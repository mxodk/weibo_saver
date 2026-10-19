package com.mxodk.weibosaver.storage

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract as DC
import com.mxodk.weibosaver.model.*
import java.security.MessageDigest

// 仅操作系统本地存储提供者；不猜测文件路径，不向未知提供者写半成品。
class AndroidTreeDocuments(private val context: Context): TreeDocuments {
    private val resolver=context.contentResolver
    private fun tree(entry: JournalEntry): Uri {
        val uri=Uri.parse(requireNotNull(entry.treeUri))
        FolderAccess.requireLocal(context,uri.toString())
        return uri
    }
    private fun parent(entry: JournalEntry): Uri {
        val uri=tree(entry)
        return DC.buildDocumentUriUsingTree(uri,DC.getTreeDocumentId(uri))
    }
    override fun find(entry: JournalEntry, name: String): List<String> {
        val uri=tree(entry)
        val children=DC.buildChildDocumentsUriUsingTree(uri,DC.getTreeDocumentId(uri))
        return (resolver.query(children,arrayOf(DC.Document.COLUMN_DOCUMENT_ID,DC.Document.COLUMN_DISPLAY_NAME),null,null,null)
            ?: throw ProbeFailure("FOLDER","无法读取原保存目录，请重新选择或授权")).use { cursor ->
            val found=mutableListOf<String>()
            while(cursor.moveToNext()) if(cursor.getString(1)==name)
                found+=DC.buildDocumentUriUsingTree(uri,cursor.getString(0)).toString()
            found
        }
    }
    override fun create(entry: JournalEntry, temporaryName: String): String {
        val uri=DC.createDocument(resolver,parent(entry),"application/octet-stream",temporaryName)
            ?: throw ProbeFailure("INSERT","系统未创建待完成文件")
        try {
            resolver.query(uri,arrayOf(DC.Document.COLUMN_DISPLAY_NAME,DC.Document.COLUMN_FLAGS),null,null,null)?.use { c ->
                if(!c.moveToFirst() || c.getString(0)!=temporaryName) throw ProbeFailure("INSERT","系统更改了临时名称，未继续写入")
                val needed=DC.Document.FLAG_SUPPORTS_WRITE or DC.Document.FLAG_SUPPORTS_RENAME or DC.Document.FLAG_SUPPORTS_DELETE
                if(c.getInt(1) and needed != needed) throw ProbeFailure("FOLDER","此目录不支持安全写入、重命名和清理，请选择其他目录")
            } ?: throw ProbeFailure("FOLDER","无法核对新文件的写入能力")
        } catch(error: Exception) {runCatching {DC.deleteDocument(resolver,uri)};throw error}
        return uri.toString()
    }
    override fun writeAndVerify(uri: String, asset: SaveAsset, token: OperationToken, fail: Boolean) {
        val target=Uri.parse(uri)
        val fd=resolver.openFileDescriptor(target,"wt") ?: throw ProbeFailure("WRITE","无法写入所选文件夹")
        ParcelFileDescriptor.AutoCloseOutputStream(fd).use { output ->
            asset.file.inputStream().use { input ->
                val buffer=ByteArray(64*1024)
                while(true) {
                    token.check();val count=input.read(buffer);if(count<0) break
                    output.write(buffer,0,count)
                    if(fail) throw ProbeFailure("WRITE","模拟写入失败")
                }
            }
            output.flush();fd.fileDescriptor.sync()
        }
        val digest=MessageDigest.getInstance("SHA-256");var bytes=0L
        resolver.openInputStream(target)?.use { input ->
            val buffer=ByteArray(64*1024)
            while(true) {
                token.check();val count=input.read(buffer);if(count<0) break
                bytes+=count;if(bytes>asset.bytes) throw ProbeFailure("INTEGRITY","目录文件长度超出预期")
                digest.update(buffer,0,count)
            }
        } ?: throw ProbeFailure("VERIFY","无法读回所选目录的文件")
        if(bytes!=asset.bytes || digest.digest().joinToString("") {"%02x".format(it)}!=asset.sha256)
            throw ProbeFailure("INTEGRITY","保存文件校验失败，请重试")
    }
    override fun rename(uri: String, finalName: String): String =
        DC.renameDocument(resolver,Uri.parse(uri),finalName)?.toString()
            ?: throw ProbeFailure("PUBLISH","文件保存未完成，请重试")
    override fun delete(uri: String) = DC.deleteDocument(resolver,Uri.parse(uri))
}

// 两种存储共用发布门闩，恢复按记录自身的目标路由。
class TargetStore(context: Context): PendingStore {
    private val media=MediaStoreWriter(context)
    private val tree=DocumentTreeStore(AndroidTreeDocuments(context))
    private fun store(entry: JournalEntry): PendingStore = if(entry.treeUri==null) media else tree
    override fun insert(entry: JournalEntry,mime: String)=store(entry).insert(entry,mime)
    override fun write(entry: JournalEntry,asset: SaveAsset,token: OperationToken,fail: Boolean)=store(entry).write(entry,asset,token,fail)
    override fun publish(entry: JournalEntry)=store(entry).publish(entry)
    override fun publishedUri(entry: JournalEntry)=store(entry).publishedUri(entry)
    override fun recover(entry: JournalEntry)=store(entry).recover(entry)
}
