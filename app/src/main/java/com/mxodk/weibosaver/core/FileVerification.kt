package com.mxodk.weibosaver.core

import com.mxodk.weibosaver.model.*
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

// P1 的预算用于控制验证流量与缓存，不作为正式产品文件上限。
const val P1_MAX_BYTES = 256L * 1024 * 1024
object FileVerification {
    fun copy(input: InputStream, partial: File, expectedBytes: Long?, token: OperationToken, progress: (Long) -> Unit = {}, maxBytes: Long = P1_MAX_BYTES, timeoutMillis: Long = 300_000): Pair<Long, String> {
        if (expectedBytes != null && expectedBytes > maxBytes) throw ProbeFailure("LIMIT", "文件过大或存储空间不足，无法保存")
        val digest = MessageDigest.getInstance("SHA-256"); var bytes = 0L; var lastReported = 0L
        val started = System.nanoTime()
        try {
            partial.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    token.check()
                    if (System.nanoTime() - started > timeoutMillis * 1_000_000) throw ProbeFailure("LIMIT", "文件传输超时，请检查网络后重试")
                    val count = input.read(buffer); if (count < 0) break
                    bytes += count
                    if (bytes > maxBytes) throw ProbeFailure("LIMIT", "文件过大或存储空间不足，无法保存")
                    output.write(buffer, 0, count); digest.update(buffer, 0, count)
                    if (bytes - lastReported >= 256 * 1024) { progress(bytes); lastReported = bytes }
                }
            }
            if (bytes == 0L || (expectedBytes != null && bytes != expectedBytes))
                throw ProbeFailure("INTEGRITY", "文件下载不完整，请重试")
            token.check()
            return bytes to digest.digest().joinToString("") { "%02x".format(it) }
        } catch (error: Exception) { partial.delete(); throw error }
    }
    fun sniff(file: File): String {
        val bytes = ByteArray(32); val count = file.inputStream().use { it.read(bytes) }
        if (count >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()) return "image/jpeg"
        if (count >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))) return "image/png"
        if (count >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP") return "image/webp"
        if (count >= 6 && String(bytes,0,6,Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")) return "image/gif"
        if (count >= 12 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp") {
            val brand = String(bytes, 8, 4, Charsets.US_ASCII)
            if (brand == "qt  ") return "video/quicktime"
            if (brand in setOf("isom", "iso2", "mp41", "mp42", "avc1", "M4V ", "dash")) return "video/mp4"
        }
        throw ProbeFailure("FORMAT", "下载内容不是受支持的图片或视频")
    }
}
