package com.mxodk.weibosaver.storage

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

// 每条记录独立原子替换，失败时保留旧记录；不保存资源 URL 或访客凭据。
class FileWriteJournal(private val directory: File): WriteJournal {
    init { directory.mkdirs() }
    private fun file(key: String): File {
        require(Regex("[a-f0-9-]{36}").matches(key))
        return File(directory, "$key.properties")
    }
    @Synchronized override fun put(entry: JournalEntry) {
        val data = Properties().apply {
            setProperty("key",entry.key); setProperty("name",entry.name); setProperty("path",entry.path)
            setProperty("video",entry.video.toString()); entry.uri?.let { setProperty("uri",it) }; entry.treeUri?.let {setProperty("tree",it)}
        }
        val target = file(entry.key); val temporary = File(directory, entry.key + ".new")
        FileOutputStream(temporary).use { output ->
            val writer = java.io.OutputStreamWriter(output,Charsets.UTF_8)
            data.store(writer,null); writer.flush(); output.fd.sync()
        }
        // Android 12 及以上支持 java.nio；同目录原子替换不先删除旧证据。
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    @Synchronized override fun entries(): List<JournalEntry> = directory.listFiles().orEmpty()
        .filter { it.extension == "properties" }.map { file ->
            check(file.length() <= 16 * 1024) { "待完成记录大小异常" }
            val p = Properties().apply { file.reader(Charsets.UTF_8).use { load(it) } }
            check(p.getProperty("key") == file.nameWithoutExtension) { "待完成记录标识不一致，保留待核对" }
            JournalEntry(requireNotNull(p.getProperty("key")), requireNotNull(p.getProperty("name")),
                requireNotNull(p.getProperty("path")), p.getProperty("video").toBoolean(), p.getProperty("uri"), p.getProperty("tree"))
        }
    @Synchronized override fun remove(key: String) {
        val target = file(key)
        check(!target.exists() || target.delete()) { "待完成记录暂时无法移除" }
        File(directory, "$key.new").delete()
    }
}
