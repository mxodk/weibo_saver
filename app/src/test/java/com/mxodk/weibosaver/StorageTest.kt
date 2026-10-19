package com.mxodk.weibosaver

import com.mxodk.weibosaver.core.PacedInputStream
import com.mxodk.weibosaver.model.*
import com.mxodk.weibosaver.storage.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.*

// 假媒体库只验证事务顺序与恢复策略；不把它作为 Android 相册或后台证据。
class StorageTest {
    private class MemoryJournal: WriteJournal {
        val records = linkedMapOf<String,JournalEntry>()
        var removeFails = false
        override fun put(entry: JournalEntry) { records[entry.key] = entry }
        override fun entries() = records.values.toList()
        override fun remove(key: String) { if (removeFails) error("模拟清理记录失败"); records.remove(key) }
    }
    private class FakeStore(private val journal: MemoryJournal): PendingStore {
        val rows = linkedMapOf<String,Boolean>() // true 为待完成，false 为已发布。
        var publishes = 0
        var deleted = 0
        var deferred = false
        var publishAcknowledgementFails = false
        override fun insert(entry: JournalEntry, mime: String): String {
            assertTrue("必须先持久记录再插入",journal.records.containsKey(entry.key))
            assertNull(journal.records[entry.key]!!.uri)
            rows[entry.key] = true
            return "content://media/external_primary/images/media/${rows.size}"
        }
        override fun write(entry: JournalEntry, asset: SaveAsset, token: OperationToken, fail: Boolean) {
            token.check(); assertNotNull(journal.records[entry.key]!!.uri)
            if (fail) throw ProbeFailure("WRITE","合成写入失败")
        }
        override fun publish(entry: JournalEntry) {
            assertTrue(rows[entry.key] == true)
            rows[entry.key] = false; publishes++
            if (publishAcknowledgementFails) throw ProbeFailure("PUBLISH","合成发布确认丢失")
        }
        override fun recover(entry: JournalEntry): Recovery {
            if (deferred) return Recovery.DEFERRED
            return when(rows[entry.key]) {
                null -> Recovery.MISSING
                false -> Recovery.KEPT_PUBLISHED
                true -> { rows.remove(entry.key); deleted++; Recovery.REMOVED_PENDING }
            }
        }
    }
    private fun asset() = SaveAsset(File("synthetic-not-read.jpg"),"image/jpeg",3,"synthetic-hash")
    private class ProcessDeath: Error()
    private fun save(j: MemoryJournal,s: FakeStore,fault: FaultPoint = FaultPoint.NONE, token: OperationToken = OperationToken(),
                     gate: PublicationGate = PublicationGate(), checkpoint: (FaultPoint)->Unit = {}) =
        PublishTransaction(j,s).save(asset(),token,gate,fault,{},checkpoint)
    private fun cancelled(action: ()->Unit) {
        try { action(); fail("应取消") } catch (_: CancellationException) { }
    }
    @Test fun preparePrecedesInsertAndOnlyConfirmedPublishSucceeds() {
        val j=MemoryJournal(); val s=FakeStore(j)
        assertTrue(save(j,s).uri.startsWith("content://media/"))
        assertEquals(1,s.publishes); assertTrue(j.records.isEmpty()); assertEquals(listOf(false),s.rows.values.toList())
    }
    @Test fun repeatedSavesCreateIndependentNamesAndRows() {
        val j=MemoryJournal(); val s=FakeStore(j)
        val a=save(j,s); val b=save(j,s)
        assertNotEquals(a.uri,b.uri); assertEquals(2,s.rows.size); assertEquals(2,s.publishes)
    }
    @Test fun writeFailureRollsBackPendingAndKeepsEarlierPublishedFile() {
        val j=MemoryJournal(); val s=FakeStore(j); save(j,s)
        try { save(j,s,FaultPoint.WRITE_FAIL); fail("应失败") } catch (error: ProbeFailure) { assertEquals("WRITE",error.category) }
        assertEquals(1,s.rows.size); assertFalse(s.rows.values.single()); assertTrue(j.records.isEmpty())
    }
    @Test fun publishFailureNeverCountsAsCompleted() {
        val j=MemoryJournal(); val s=FakeStore(j)
        try { save(j,s,FaultPoint.PUBLISH_FAIL); fail("应失败") } catch (error: ProbeFailure) { assertEquals("PUBLISH",error.category) }
        assertEquals(0,s.publishes); assertEquals(1,s.deleted); assertTrue(j.records.isEmpty())
    }
    @Test fun processedStopPreventsCommitAfterWriting() {
        val j=MemoryJournal(); val s=FakeStore(j); val gate=PublicationGate()
        cancelled { save(j,s,FaultPoint.BEFORE_PUBLISH,gate=gate) { gate.stop() } }
        assertEquals(0,s.publishes); assertTrue(s.rows.isEmpty())
    }
    @Test fun cancellationAfterPublishRetainsSuccess() {
        val j=MemoryJournal(); val s=FakeStore(j); val token=OperationToken()
        val receipt=save(j,s,FaultPoint.AFTER_PUBLISH,token) { token.cancel(); token.check() }
        assertTrue(receipt.note.contains("保留成功")); assertEquals(0,s.deleted); assertEquals(1,s.publishes)
    }
    @Test fun alreadyCancelledOperationDoesNotInsert() {
        val j=MemoryJournal(); val s=FakeStore(j)
        cancelled { save(j,s,token=OperationToken().apply{cancel()}) }
        assertTrue(s.rows.isEmpty()); assertTrue(j.records.isEmpty())
    }
    private fun crashAndRecover(point: FaultPoint, expected: Recovery) {
        val j=MemoryJournal(); val s=FakeStore(j)
        try { save(j,s,point) { throw ProcessDeath() }; fail("应中断") } catch (_: ProcessDeath) { }
        assertEquals(1,j.records.size)
        if (point == FaultPoint.INSERT_GAP) assertNull(j.records.values.single().uri)
        val result=PublishTransaction(j,s).recover()
        assertTrue(result.single().endsWith(expected.name)); assertTrue(j.records.isEmpty())
        assertEquals(if (expected == Recovery.KEPT_PUBLISHED) 1 else 0,s.rows.size)
    }
    @Test fun crashBetweenInsertAndUriRecordIsRecoverableByUniqueName() = crashAndRecover(FaultPoint.INSERT_GAP,Recovery.REMOVED_PENDING)
    @Test fun crashBeforePublishRemovesOnlyPendingRow() = crashAndRecover(FaultPoint.BEFORE_PUBLISH,Recovery.REMOVED_PENDING)
    @Test fun crashAfterPublishKeepsPublishedRow() = crashAndRecover(FaultPoint.AFTER_PUBLISH,Recovery.KEPT_PUBLISHED)
    @Test fun uncertainOwnershipDefersCleanupAndRetainsJournal() {
        val j=MemoryJournal(); val s=FakeStore(j); s.deferred=true
        try { save(j,s,FaultPoint.WRITE_FAIL) } catch (_: ProbeFailure) { }
        assertEquals(1,j.records.size)
        assertTrue(PublishTransaction(j,s).recover().single().endsWith("DEFERRED")); assertEquals(0,s.deleted)
    }
    @Test fun missingRowsRetireTheirOwnJournalWithoutDeletingAnything() {
        val j=MemoryJournal(); val s=FakeStore(j)
        val key=UUID.randomUUID().toString(); j.put(JournalEntry(key,"weibo_p2_$key.jpg","Pictures/WeiboSaver-P2/",false))
        assertTrue(PublishTransaction(j,s).recover().single().endsWith("MISSING")); assertEquals(0,s.deleted)
    }
    @Test fun lostPublishAcknowledgementCanBeConfirmedWithoutDeletingPublishedData() {
        val j=MemoryJournal(); val s=FakeStore(j); s.publishAcknowledgementFails=true
        assertTrue(save(j,s).note.contains("保留成功")); assertEquals(0,s.deleted); assertTrue(j.records.isEmpty())
    }
    @Test fun journalRemovalFailureAfterPublishIsRecoveredWithoutDeletingSuccess() {
        val j=MemoryJournal(); val s=FakeStore(j); j.removeFails=true
        assertTrue(save(j,s).note.contains("留待下次")); assertEquals(1,j.records.size)
        j.removeFails=false
        PublishTransaction(j,s).recover(); assertTrue(j.records.isEmpty()); assertEquals(0,s.deleted)
    }
    @Test fun publicationAndStopHaveSingleOrderingPoint() {
        val pool=Executors.newFixedThreadPool(2); val gate=PublicationGate()
        val entered=CountDownLatch(1); val release=CountDownLatch(1); val stopStarted=CountDownLatch(1)
        try {
            val publishing=pool.submit { gate.commit(OperationToken()) { entered.countDown(); assertTrue(release.await(2,TimeUnit.SECONDS)) } }
            assertTrue(entered.await(2,TimeUnit.SECONDS))
            val stopping=pool.submit { stopStarted.countDown(); gate.stop() }
            assertTrue(stopStarted.await(2,TimeUnit.SECONDS)); assertFalse(stopping.isDone)
            release.countDown(); publishing.get(2,TimeUnit.SECONDS); stopping.get(2,TimeUnit.SECONDS)
            cancelled { gate.commit(OperationToken()) { fail("停止后不能提交") } }
        } finally { release.countDown(); pool.shutdownNow() }
    }
    @Test fun fileJournalPersistsPreparationAndUriAcrossInstances() {
        val directory=Files.createTempDirectory("p2-journal").toFile()
        try {
            val key=UUID.randomUUID().toString(); val entry=JournalEntry(key,"weibo_p2_$key.jpg","Pictures/微博验证/",false)
            FileWriteJournal(directory).put(entry)
            assertEquals(entry,FileWriteJournal(directory).entries().single())
            val withUri=entry.copy(uri="content://media/external_primary/images/media/7")
            FileWriteJournal(directory).put(withUri)
            assertEquals(withUri,FileWriteJournal(directory).entries().single())
            FileWriteJournal(directory).remove(key); assertTrue(FileWriteJournal(directory).entries().isEmpty())
        } finally { directory.listFiles().orEmpty().forEach{it.delete()}; directory.delete() }
    }
    @Test fun mismatchedJournalIdentityIsRetainedInsteadOfRemovingOtherEvidence() {
        val directory=Files.createTempDirectory("p2-journal-bad").toFile()
        try {
            val a=UUID.randomUUID().toString(); val b=UUID.randomUUID().toString()
            File(directory,"$a.properties").writeText("key=$b\nname=fixture\npath=fixture\nvideo=false",Charsets.UTF_8)
            try { FileWriteJournal(directory).entries(); fail("不应接受标识错配") } catch (_: IllegalStateException) { }
            assertTrue(File(directory,"$a.properties").exists())
        } finally { directory.listFiles().orEmpty().forEach{it.delete()}; directory.delete() }
    }
    @Test fun ownershipChecksRejectOtherOwnerNameAndDirectory() {
        val key=UUID.randomUUID().toString(); val e=JournalEntry(key,"weibo_p2_$key.jpg","Pictures/WeiboSaver-P2/",false)
        assertTrue(OwnershipPolicy.matches(e,e.name,e.path,"our.app","our.app"))
        assertFalse(OwnershipPolicy.matches(e,e.name,e.path,"other.app","our.app"))
        assertFalse(OwnershipPolicy.matches(e,"different.jpg",e.path,"our.app","our.app"))
        assertFalse(OwnershipPolicy.matches(e,e.name,"Pictures/Other/","our.app","our.app"))
        assertFalse(OwnershipPolicy.valid(e.copy(path="Pictures/../")))
        assertFalse(OwnershipPolicy.valid(e.copy(name="weibo_p2_$key.exe")))
    }
    @Test fun pacedNetworkReadRespondsToCancellation() {
        val token=OperationToken(); val stream=PacedInputStream(ByteArrayInputStream(ByteArray(16384)),token,8192)
        assertEquals(8192,stream.read(ByteArray(8192),0,8192))
        token.cancel(); cancelled { stream.read(ByteArray(8192),0,8192) }
    }
}
