package com.mxodk.weibosaver.storage

import com.mxodk.weibosaver.model.*

// 事务策略与 Android 提供者分离，便于离线验证重命名和崩溃窗口。
interface TreeDocuments {
    fun find(entry: JournalEntry, name: String): List<String>
    fun create(entry: JournalEntry, temporaryName: String): String
    fun writeAndVerify(uri: String, asset: SaveAsset, token: OperationToken, fail: Boolean)
    fun rename(uri: String, finalName: String): String
    fun delete(uri: String): Boolean
}

class DocumentTreeStore(private val documents: TreeDocuments): PendingStore {
    private fun temporary(entry: JournalEntry) = ".${entry.name}.pending"
    private fun validate(entry: JournalEntry) {
        if(entry.treeUri == null || !Regex("[a-f0-9-]{36}").matches(entry.key) ||
            !Regex("weibo_${entry.key}\\.(jpg|png|gif|webp|mp4|mov)").matches(entry.name))
            throw ProbeFailure("JOURNAL","目录清理记录无法确认，已保留")
    }
    override fun insert(entry: JournalEntry, mime: String): String {
        validate(entry)
        if(documents.find(entry,entry.name).isNotEmpty() || documents.find(entry,temporary(entry)).isNotEmpty())
            throw ProbeFailure("CONFLICT","文件名发生冲突，未覆盖已有文件")
        return documents.create(entry,temporary(entry))
    }
    private fun pending(entry: JournalEntry): String {
        validate(entry)
        val found=documents.find(entry,temporary(entry))
        return found.singleOrNull()?.takeIf {it==entry.uri}
            ?: throw ProbeFailure("OWNERSHIP","无法确认本次未完成文件，未写入或删除")
    }
    override fun write(entry: JournalEntry, asset: SaveAsset, token: OperationToken, fail: Boolean) {
        documents.writeAndVerify(pending(entry),asset,token,fail)
    }
    override fun publish(entry: JournalEntry) {
        val uri=pending(entry)
        if(documents.find(entry,entry.name).isNotEmpty()) throw ProbeFailure("CONFLICT","发布名称已存在，未覆盖")
        documents.rename(uri,entry.name)
        publishedUri(entry)
    }
    override fun publishedUri(entry: JournalEntry): String {
        validate(entry)
        return documents.find(entry,entry.name).singleOrNull()
            ?: throw ProbeFailure("PUBLISH","文件保存未完成，请重试")
    }
    override fun recover(entry: JournalEntry): Recovery {
        validate(entry)
        val complete=documents.find(entry,entry.name)
        val pending=documents.find(entry,temporary(entry))
        if(complete.size>1 || pending.size>1 || (complete.isNotEmpty() && pending.isNotEmpty())) return Recovery.DEFERRED
        // 正式名称只会在写入读回核验后提交；重命名改变 URI 也通过唯一名称找回。
        if(complete.size==1) return Recovery.KEPT_PUBLISHED
        if(pending.isEmpty()) return Recovery.MISSING
        if(entry.uri!=null && entry.uri!=pending.single()) return Recovery.DEFERRED
        return if(documents.delete(pending.single())) Recovery.REMOVED_PENDING else Recovery.DEFERRED
    }
}
