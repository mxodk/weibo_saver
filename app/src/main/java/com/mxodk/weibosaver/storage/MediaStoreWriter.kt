package com.mxodk.weibosaver.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.content.ContentResolver
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.mxodk.weibosaver.model.*
import java.security.MessageDigest

// 删除前逐项核对 App 所有者、唯一名称、路径和待完成状态，绝不按目录批量删除。
class MediaStoreWriter(private val context: Context): PendingStore {
    private val resolver = context.contentResolver
    private val columns = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.OWNER_PACKAGE_NAME, MediaStore.MediaColumns.IS_PENDING)
    private fun collection(entry: JournalEntry): Uri = if (entry.video)
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private fun validate(entry: JournalEntry) {
        if (!OwnershipPolicy.valid(entry))
            throw ProbeFailure("JOURNAL", "清理记录无法确认归属，保留待核对")
        entry.uri?.let { raw ->
            val uri = Uri.parse(raw)
            if (uri.scheme != "content" || uri.authority != "media" || uri.query != null || uri.fragment != null ||
                uri.lastPathSegment?.toLongOrNull() == null ||
                uri.toString().substringBeforeLast('/') != collection(entry).toString())
                throw ProbeFailure("JOURNAL", "媒体 URI 不属于预期集合，保留待核对")
        }
    }
    private data class Row(val uri: Uri, val pending: Boolean)
    private fun rows(entry: JournalEntry): List<Row>? {
        validate(entry)
        val uri = entry.uri?.let(Uri::parse) ?: collection(entry)
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING,MediaStore.MATCH_INCLUDE)
            if (entry.uri == null) {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION,"${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?")
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,arrayOf(entry.name,entry.path))
            }
        }
        val cursor = resolver.query(uri, columns, args, null)
        if (cursor == null) return null
        return cursor.use { c ->
            val found = mutableListOf<Row>()
            while (c.moveToNext()) {
                if (!OwnershipPolicy.matches(entry,c.getString(1),c.getString(2),c.getString(3),context.packageName)) return null
                found += Row(ContentUris.withAppendedId(collection(entry), c.getLong(0)), c.getInt(4) == 1)
            }
            found
        }
    }
    override fun insert(entry: JournalEntry, mime: String): String {
        validate(entry)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, entry.name); put(MediaStore.MediaColumns.RELATIVE_PATH, entry.path)
            put(MediaStore.MediaColumns.MIME_TYPE, mime); put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return resolver.insert(collection(entry), values)?.toString() ?: throw ProbeFailure("INSERT", "媒体库未创建文件")
    }
    override fun write(entry: JournalEntry, asset: SaveAsset, token: OperationToken, fail: Boolean) {
        if (rows(entry)?.singleOrNull()?.pending != true) throw ProbeFailure("OWNERSHIP", "待写入媒体的归属或状态不符")
        val uri = Uri.parse(requireNotNull(entry.uri))
        val descriptor = resolver.openFileDescriptor(uri, "w") ?: throw ProbeFailure("WRITE", "无法打开媒体库写入")
        ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
            asset.file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024); var count: Int
                while (true) {
                    token.check(); count = input.read(buffer); if (count < 0) break
                    output.write(buffer, 0, count)
                    if (fail) throw ProbeFailure("WRITE", "模拟写入中途失败")
                }
            }
            output.flush(); descriptor.fileDescriptor.sync()
        }
        // 从媒体库读回实际字节，避免把仅写入调用成功当作文件已确认。
        val digest = MessageDigest.getInstance("SHA-256"); var bytes = 0L
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                token.check(); val count = input.read(buffer); if (count < 0) break
                bytes += count
                if (bytes > asset.bytes) throw ProbeFailure("INTEGRITY", "媒体库实际大小超出预期")
                digest.update(buffer, 0, count)
            }
        } ?: throw ProbeFailure("VERIFY", "无法读回媒体库文件")
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (bytes != asset.bytes || hash != asset.sha256) throw ProbeFailure("INTEGRITY", "保存文件校验失败，请重试")
    }
    override fun publish(entry: JournalEntry) {
        if (rows(entry)?.singleOrNull()?.pending != true) throw ProbeFailure("OWNERSHIP", "发布前无法确认待完成媒体")
        val count = resolver.update(Uri.parse(requireNotNull(entry.uri)), ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) }, null,null)
        if (count != 1 || rows(entry)?.singleOrNull()?.pending != false)
            throw ProbeFailure("PUBLISH", "文件保存未完成，请重试")
    }
    override fun recover(entry: JournalEntry): Recovery {
        val found = rows(entry) ?: return Recovery.DEFERRED
        if (found.isEmpty()) return Recovery.MISSING
        if (found.size != 1) return Recovery.DEFERRED
        val row = found.single()
        if (!row.pending) return Recovery.KEPT_PUBLISHED
        if (resolver.delete(row.uri, "${MediaStore.MediaColumns.IS_PENDING} = ?", arrayOf("1")) != 1) return Recovery.DEFERRED
        return Recovery.REMOVED_PENDING
    }
}
