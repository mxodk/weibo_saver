package com.mxodk.weibosaver.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.mxodk.weibosaver.model.ProbeFailure
import com.mxodk.weibosaver.ui.SaveFolder

// 只检查用户选择目录的元数据和持久授权；P4 不创建文件、不扫描目录内容。
object FolderAccess {
    fun adopt(context: Context, uri: Uri): SaveFolder {
        if(uri.scheme != "content" || uri.authority!="com.android.externalstorage.documents" || !DocumentsContract.isTreeUri(uri))
            throw ProbeFailure("FOLDER","请选择系统内部存储或存储卡的本地文件夹")
        val resolver=context.contentResolver
        val document=DocumentsContract.buildDocumentUriUsingTree(uri,DocumentsContract.getTreeDocumentId(uri))
        val columns=arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_FLAGS)
        val label=resolver.query(document,columns,null,null,null)?.use { cursor ->
            if(!cursor.moveToFirst()) throw ProbeFailure("FOLDER","无法读取该文件夹，请重新选择")
            val flags=cursor.getInt(1)
            if(flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE == 0)
                throw ProbeFailure("FOLDER","该文件夹不支持写入，请选择其他文件夹")
            cursor.getString(0)?.takeIf {it.isNotBlank()} ?: "所选文件夹"
        } ?: throw ProbeFailure("FOLDER","无法读取该文件夹，请重新选择")
        resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if(!hasWrite(context,uri.toString())) throw ProbeFailure("FOLDER","未取得可保留的写入授权，请重新选择")
        // 本地存储提供者可显示清晰位置，其他提供者保留其名称，不推测文件系统路径。
        val treeId=DocumentsContract.getTreeDocumentId(uri)
        val readable=if(uri.authority=="com.android.externalstorage.documents" && ':' in treeId) {
            val volume=treeId.substringBefore(':')
            val path=treeId.substringAfter(':')
            (if(volume=="primary") "内部存储" else "存储卷 $volume")+if(path.isEmpty()) "" else "/$path"
        } else label
        return SaveFolder(uri.toString(),readable)
    }
    // 系统本地提供者使用隐藏临时文件和最终重命名；其他提供者未验证隔离能力。
    fun requireLocal(context: Context, raw: String) {
        val uri=Uri.parse(raw)
        if(uri.scheme!="content" || uri.authority!="com.android.externalstorage.documents" || !DocumentsContract.isTreeUri(uri))
            throw ProbeFailure("FOLDER","此提供者暂不支持安全保存，请选择系统内部存储或存储卡的本地文件夹")
        if(!hasWrite(context,raw)) throw ProbeFailure("FOLDER","原保存目录授权已失效，请在设置中重新选择")
        val parent=DocumentsContract.buildDocumentUriUsingTree(uri,DocumentsContract.getTreeDocumentId(uri))
        context.contentResolver.query(parent,arrayOf(DocumentsContract.Document.COLUMN_FLAGS),null,null,null)?.use { c ->
            if(!c.moveToFirst() || c.getInt(0) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE==0)
                throw ProbeFailure("FOLDER","目录不存在或不能写入，请重新选择")
        } ?: throw ProbeFailure("FOLDER","无法核对保存目录，请重新选择")
    }
    fun hasWrite(context: Context, uri: String) = context.contentResolver.persistedUriPermissions.any {
        it.uri.toString()==uri && it.isWritePermission
    }
    fun releaseUnused(context: Context, retained: Set<String>) {
        context.contentResolver.persistedUriPermissions.filter {it.uri.toString() !in retained}.forEach { grant ->
            // 保留当前设置和当前批次的目录；重试仍需原授权。
            val flags=(if(grant.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                (if(grant.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            runCatching {context.contentResolver.releasePersistableUriPermission(grant.uri,flags)}
        }
    }
}
