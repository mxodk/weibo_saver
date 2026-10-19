package com.mxodk.weibosaver.storage

import com.mxodk.weibosaver.model.*
import java.io.File
import java.util.UUID

// 发布与停止在同一门闩排序：已提交的成功项不受之后的停止影响。
class PublicationGate {
    private var stopped = false
    @Synchronized fun stop() { stopped = true }
    @Synchronized fun <T> commit(token: OperationToken, action: () -> T): T {
        token.check()
        if (stopped) throw java.util.concurrent.CancellationException("停止已处理")
        return action()
    }
}
enum class FaultPoint(val label: String) {
    NONE("正常保存"), WRITE_FAIL("模拟写入失败"), PUBLISH_FAIL("模拟发布失败"),
    INSERT_GAP("插入后暂停（URI 尚未记入）"), BEFORE_PUBLISH("写完后暂停（尚未发布）"),
    AFTER_PUBLISH("发布后暂停（清理记录尚在）"),
}
data class JournalEntry(val key: String, val name: String, val path: String, val video: Boolean, val uri: String? = null, val treeUri: String? = null)
data class SaveAsset(val file: File, val mime: String, val bytes: Long, val sha256: String)
enum class Recovery { REMOVED_PENDING, KEPT_PUBLISHED, MISSING, DEFERRED }
interface WriteJournal {
    fun put(entry: JournalEntry)
    fun entries(): List<JournalEntry>
    fun remove(key: String)
}
interface PendingStore {
    fun insert(entry: JournalEntry, mime: String): String
    fun write(entry: JournalEntry, asset: SaveAsset, token: OperationToken, fail: Boolean)
    fun publish(entry: JournalEntry)
    // 文档重命名可能改变 URI；默认媒体库仍返回原 URI。
    fun publishedUri(entry: JournalEntry): String = requireNotNull(entry.uri)
    fun recover(entry: JournalEntry): Recovery
}
data class SaveReceipt(val uri: String, val note: String = "已发布并确认")

class PublishTransaction(private val journal: WriteJournal, private val store: PendingStore) {
    fun recover(): List<String> = journal.entries().map { entry ->
        val result = runCatching { store.recover(entry) }.getOrDefault(Recovery.DEFERRED)
        if (result != Recovery.DEFERRED) journal.remove(entry.key)
        "${entry.key}:$result"
    }
    fun save(asset: SaveAsset, token: OperationToken, gate: PublicationGate, fault: FaultPoint,
             stage: (String) -> Unit, checkpoint: (FaultPoint) -> Unit,
             album: String = "WeiboSaver-P2", treeUri: String? = null): SaveReceipt {
        token.check()
        val key = UUID.randomUUID().toString()
        val extension = when (asset.mime) {
            "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/gif" -> "gif"; "image/webp" -> "webp"
            "video/mp4" -> "mp4"; "video/quicktime" -> "mov"
            else -> throw ProbeFailure("FORMAT", "无法为此文件确定保存格式")
        }
        require(com.mxodk.weibosaver.ui.AlbumDirectory.validate(album) == album)
        val prefix=if(album=="WeiboSaver-P2" && treeUri==null) "weibo_p2_" else "weibo_"
        var entry = JournalEntry(key, "$prefix${key}.$extension",
            (if (asset.mime.startsWith("video/")) "Movies/" else "Pictures/") + "$album/", asset.mime.startsWith("video/"), treeUri=treeUri)
        // 准备记录先于插入落盘，覆盖插入成功但 URI 未记录的崩溃窗口。
        journal.put(entry)
        try {
            token.check(); stage("创建待完成媒体")
            val uri = store.insert(entry, asset.mime)
            if (fault == FaultPoint.INSERT_GAP) checkpoint(fault)
            entry = entry.copy(uri = uri); journal.put(entry)
            token.check(); stage("写入并核对媒体库文件")
            store.write(entry, asset, token, fault == FaultPoint.WRITE_FAIL)
            if (fault == FaultPoint.BEFORE_PUBLISH) checkpoint(fault)
            gate.commit(token) {
                stage("提交媒体库发布")
                if (fault == FaultPoint.PUBLISH_FAIL) throw ProbeFailure("PUBLISH", "模拟发布失败，未提交发布")
                store.publish(entry)
            }
            // 到这里已确认发布；后续中断只能清理记录，不能删除成功文件。
            val published=store.publishedUri(entry)
            stage("已发布并确认：$published")
            if (fault == FaultPoint.AFTER_PUBLISH) checkpoint(fault)
            return try { journal.remove(entry.key); SaveReceipt(published) }
                catch (_: Exception) { SaveReceipt(published, "发布成功，清理记录留待下次核对") }
        } catch (error: Exception) {
            // 已检测到插入前名称冲突时不认领或清理已有文件。
            if(entry.uri==null && error is ProbeFailure && error.category=="CONFLICT") {
                runCatching {journal.remove(entry.key)};throw error
            }
            val recovery = runCatching { store.recover(entry) }.getOrDefault(Recovery.DEFERRED)
            if (recovery != Recovery.DEFERRED) runCatching { journal.remove(entry.key) }
            if (recovery == Recovery.KEPT_PUBLISHED) return SaveReceipt(store.publishedUri(entry), "停止／异常前已发布，保留成功文件")
            throw error
        }
    }
}
