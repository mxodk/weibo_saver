package com.mxodk.weibosaver

import com.mxodk.weibosaver.download.*
import com.mxodk.weibosaver.model.*
import com.mxodk.weibosaver.storage.*
import com.mxodk.weibosaver.ui.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

// 仅合成事务与队列证据，不替代提供者、相册及前台服务的设备验收。
class P5IntegrationTest {
    private class Journal: WriteJournal {
        val records=linkedMapOf<String,JournalEntry>()
        override fun put(entry: JournalEntry) {records[entry.key]=entry}
        override fun entries()=records.values.toList()
        override fun remove(key: String) {records.remove(key)}
    }
    private class Documents: TreeDocuments {
        val names=linkedMapOf<String,String>()
        var writes=0;var deletes=0;var renameFails=false;var loseAck=false;var denyDelete=false
        override fun find(entry: JournalEntry,name: String)=names.filterValues {it==name}.keys.toList()
        override fun create(entry: JournalEntry,temporaryName: String): String {
            assertTrue(temporaryName.startsWith("."));assertTrue(temporaryName.endsWith(".pending"))
            val uri="content://local/${UUID.randomUUID()}";names[uri]=temporaryName;return uri
        }
        override fun writeAndVerify(uri: String,asset: SaveAsset,token: OperationToken,fail: Boolean) {
            token.check();writes++;if(fail) throw ProbeFailure("WRITE","合成写入失败")
        }
        override fun rename(uri: String,finalName: String): String {
            if(renameFails) throw ProbeFailure("PUBLISH","合成重命名失败")
            assertNotNull(names.remove(uri));val finalUri="$uri-renamed";names[finalUri]=finalName
            if(loseAck) throw ProbeFailure("PUBLISH","合成发布确认丢失")
            return finalUri
        }
        override fun delete(uri: String): Boolean {
            if(denyDelete) return false
            deletes++;return names.remove(uri)!=null
        }
    }
    private val asset=SaveAsset(File("synthetic.jpg"),"image/jpeg",3,"synthetic")
    private fun save(j: Journal,d: Documents,fault: FaultPoint=FaultPoint.NONE,token: OperationToken=OperationToken(),
        gate: PublicationGate=PublicationGate(),checkpoint: (FaultPoint)->Unit={}) =
        PublishTransaction(j,DocumentTreeStore(d)).save(asset,token,gate,fault,{},checkpoint,"WeiboSaver","content://tree/local")
    @Test fun treePublishReturnsRenamedUriAndNeverKeepsTemporaryName() {
        val j=Journal();val d=Documents();val result=save(j,d)
        assertTrue(result.uri.endsWith("-renamed"));assertEquals(1,d.writes);assertEquals(1,d.names.size)
        assertTrue(d.names.values.single().endsWith(".jpg"));assertTrue(j.records.isEmpty())
    }
    @Test fun treeFailureCleansOnlyOwnPendingAndPreservesUnrelatedFile() {
        val j=Journal();val d=Documents();d.names["content://unrelated"]="existing.jpg"
        try {save(j,d,FaultPoint.WRITE_FAIL);fail()} catch(_: ProbeFailure) {}
        assertEquals(mapOf("content://unrelated" to "existing.jpg"),d.names);assertTrue(j.records.isEmpty())
    }
    @Test fun renameFailureDoesNotReportSuccess() {
        val j=Journal();val d=Documents();d.renameFails=true
        try {save(j,d);fail()} catch(_: ProbeFailure) {}
        assertTrue(d.names.isEmpty());assertEquals(1,d.deletes)
    }
    @Test fun stopBeforeRenameRemovesPending() {
        val j=Journal();val d=Documents();val gate=PublicationGate()
        try {save(j,d,FaultPoint.BEFORE_PUBLISH,gate=gate) {gate.stop()};fail()} catch(_: java.util.concurrent.CancellationException) {}
        assertTrue(d.names.isEmpty());assertTrue(j.records.isEmpty())
    }
    @Test fun cancellationAfterRenameKeepsPublishedDocumentAndUsesNewUri() {
        val j=Journal();val d=Documents();val token=OperationToken()
        val result=save(j,d,FaultPoint.AFTER_PUBLISH,token) {token.cancel();token.check()}
        assertTrue(result.uri.endsWith("-renamed"));assertEquals(0,d.deletes)
    }
    private class Crash: Error()
    private fun crash(point: FaultPoint,expected: Recovery) {
        val j=Journal();val d=Documents()
        try {save(j,d,point) {throw Crash()};fail()} catch(_: Crash) {}
        assertEquals(1,j.records.size)
        val results=PublishTransaction(j,DocumentTreeStore(d)).recover()
        assertTrue(results.single().endsWith(expected.name));assertTrue(j.records.isEmpty())
        assertEquals(if(expected==Recovery.KEPT_PUBLISHED) 1 else 0,d.names.size)
    }
    @Test fun treeInsertGapRecoversByExactUniqueName()=crash(FaultPoint.INSERT_GAP,Recovery.REMOVED_PENDING)
    @Test fun treeCrashBeforeRenameRemovesPending()=crash(FaultPoint.BEFORE_PUBLISH,Recovery.REMOVED_PENDING)
    @Test fun treeCrashAfterRenameKeepsCompleteEvenThoughUriChanged()=crash(FaultPoint.AFTER_PUBLISH,Recovery.KEPT_PUBLISHED)
    @Test fun lostRenameAcknowledgementCanBeRecoveredAsPublished() {
        val j=Journal();val d=Documents();d.loseAck=true
        assertTrue(save(j,d).uri.endsWith("-renamed"));assertTrue(j.records.isEmpty());assertEquals(0,d.deletes)
    }
    @Test fun uncertainDeleteRetainsJournalForLaterRecovery() {
        val j=Journal();val d=Documents();d.denyDelete=true
        try {save(j,d,FaultPoint.WRITE_FAIL);fail()} catch(_: ProbeFailure) {}
        assertEquals(1,j.records.size)
        assertTrue(PublishTransaction(j,DocumentTreeStore(d)).recover().single().endsWith("DEFERRED"))
        d.denyDelete=false;PublishTransaction(j,DocumentTreeStore(d)).recover();assertTrue(j.records.isEmpty())
    }
    @Test fun repeatedTreeSavesAreIndependent() {
        val j=Journal();val d=Documents();val a=save(j,d);val b=save(j,d)
        assertNotEquals(a.uri,b.uri);assertEquals(2,d.names.size);assertEquals(2,d.names.values.toSet().size)
    }
    @Test fun treeJournalRetainsTargetAcrossRestart() {
        val dir=Files.createTempDirectory("p5-journal").toFile()
        try {
            val key=UUID.randomUUID().toString();val e=JournalEntry(key,"weibo_$key.jpg","Pictures/WeiboSaver/",false,treeUri="content://tree/original")
            FileWriteJournal(dir).put(e);assertEquals(e,FileWriteJournal(dir).entries().single())
        } finally {dir.deleteRecursively()}
    }
    private fun media(id: String,quality: Int=5)=ProbeMedia(id,MediaKind.IMAGE,ResourceCandidate("https://wx1.sinaimg.cn/large/$id.jpg","large",1000,1000,qualityIndex=quality))
    private fun snapshot()=SaveSnapshot(3,"post",SaveChoice.ALL,listOf(media("a"),media("b")),folder=SaveFolder("original","old"))
    @Test fun retryOnlyIncludesFailedOriginalIdsAndKeepsDirectory() {
        val s=snapshot();val state=DownloadState(snapshot=s,items=listOf(SaveItem("a",MediaKind.IMAGE,"成功"),SaveItem("b",MediaKind.IMAGE,"失败")))
        val retry=RetryPolicy.snapshot(state)
        assertEquals(listOf("b"),retry.media.map {it.id});assertEquals(s.folder,retry.folder)
    }
    @Test fun reorderedFreshResourcesMatchIdsInsteadOfPositions() {
        val s=snapshot();val resources=RetryPolicy.refreshed(s,ProbePost("post",listOf(media("b"),media("a"))))
        assertEquals(listOf("a","b"),resources.map {it.id})
    }
    @Test fun disappearedChangedAndLowerQualityMediaAreNotReplaced() {
        val s=snapshot();val resources=RetryPolicy.refreshed(s,ProbePost("post",listOf(media("other"),media("b",1))))
        assertTrue(resources.all {it.candidate==null});assertEquals(listOf("a","b"),resources.map {it.id})
    }
    @Test fun refreshCannotUseAnotherPost() {
        try {RetryPolicy.refreshed(snapshot(),ProbePost("other",listOf(media("a"))));fail()} catch(e: ProbeFailure) {assertEquals("IDENTITY",e.category)}
    }
    @Test fun activeBatchCannotRetryOrReplaceSnapshot() {
        try {RetryPolicy.snapshot(DownloadState(active=true,snapshot=snapshot()));fail()} catch(e: ProbeFailure) {assertEquals("BUSY",e.category)}
    }
    @Test fun cancellationsDoNotIncreaseProgressAndFailedFilesAreNotSuccess() {
        val state=DownloadState(items=listOf(SaveItem("a",MediaKind.IMAGE,"成功"),SaveItem("b",MediaKind.IMAGE,"失败"),SaveItem("c",MediaKind.IMAGE,"取消")))
        assertEquals(1,state.success);assertEquals(2,state.processed);assertEquals(66,state.percent)
    }
    @Test fun activeHandoffRetainsHintAndTerminalResultsReplaceIt() {
        val s=DownloadState(active=true,processingHint="正在核验第 1 项…",items=listOf(SaveItem("a",MediaKind.IMAGE,"成功")))
        assertEquals("批次已结束",s.processingText)
        assertEquals("正在核验第 1 项…",s.copy(items=listOf(SaveItem("a",MediaKind.IMAGE))).processingText)
        assertEquals("批次已结束",s.copy(active=false).processingText)
    }
    @Test fun workersStayBoundedAndSupportMoreThanThreeItems()=runBlocking {
        val next=AtomicInteger();val active=AtomicInteger();val maximum=AtomicInteger();val done=AtomicInteger()
        runBoundedWorkers(18) {
            while(next.getAndIncrement()<18) {
                val current=active.incrementAndGet();maximum.updateAndGet {maxOf(it,current)}
                delay(5);done.incrementAndGet();active.decrementAndGet()
            }
        }
        assertEquals(18,done.get());assertTrue(maximum.get()<=2);assertEquals(2,maximum.get())
    }
    @Test fun defaultMediaNamesPassStrictOwnershipAndNeverUseP2Directory() {
        val j=Journal();var captured: JournalEntry?=null
        val store=object: PendingStore {
            override fun insert(entry: JournalEntry,mime: String): String {captured=entry;return "content://media/1"}
            override fun write(entry: JournalEntry,asset: SaveAsset,token: OperationToken,fail: Boolean) {}
            override fun publish(entry: JournalEntry) {}
            override fun recover(entry: JournalEntry)=Recovery.KEPT_PUBLISHED
        }
        PublishTransaction(j,store).save(asset,OperationToken(),PublicationGate(),FaultPoint.NONE,{},{},"WeiboSaver")
        val e=requireNotNull(captured);assertEquals("Pictures/WeiboSaver/",e.path);assertTrue(OwnershipPolicy.valid(e))
    }
    @Test fun existingNameConflictDoesNotDeleteOrReportAnotherFileAsSuccess() {
        val j=Journal();var recoverCalls=0
        val store=object: PendingStore {
            override fun insert(entry: JournalEntry,mime: String): String {throw ProbeFailure("CONFLICT","existing")}
            override fun write(entry: JournalEntry,asset: SaveAsset,token: OperationToken,fail: Boolean) {fail("不应写入")}
            override fun publish(entry: JournalEntry) {fail("不应发布")}
            override fun recover(entry: JournalEntry): Recovery {recoverCalls++;return Recovery.KEPT_PUBLISHED}
        }
        try {PublishTransaction(j,store).save(asset,OperationToken(),PublicationGate(),FaultPoint.NONE,{},{},"WeiboSaver");fail()}
        catch(e: ProbeFailure) {assertEquals("CONFLICT",e.category)}
        assertEquals(0,recoverCalls);assertTrue(j.records.isEmpty())
    }
}
