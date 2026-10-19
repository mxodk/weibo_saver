package com.mxodk.weibosaver.core

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import com.mxodk.weibosaver.model.*
import java.io.File
import java.util.UUID

// 按接口高清资源口径记录来源与文件事实，不要求上传前原文件对照。
data class FileEvidence(
    val file: File, val bytes: Long, val sha256: String, val mime: String,
    val width: Int, val height: Int, val durationUs: Long?, val host: String,
    val lengthEvidence: String, val source: String,
) {
    fun report(): String = """来源字段：$source
主机：$host
格式：$mime；尺寸：${width} × $height
时长（微秒）：${durationUs ?: "不适用"}
字节数：$bytes
SHA-256：$sha256
完整性：$lengthEvidence
可用性：图片抽样解码／视频轨道可读取；未执行完整逐帧解码
图片口径：微博接口提供的高清图片，不要求与上传前原文件相同；视频保留接口所选资源，不转码"""
}

class MediaProbe(context: Context, private val http: SafeHttpClient, cacheSubdir: String = "p1-validation", private val maxBytes: Long = P1_MAX_BYTES, private val timeoutMillis: Long = 300_000) {
    private val directory = File(context.cacheDir, cacheSubdir).apply { mkdirs() }
    // 仅清理本验证入口独占的临时文件，不触及相册或其他目录。
    fun clear() { directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() } }
    fun fetch(media: ProbeMedia, token: OperationToken, slow: Boolean = false, progress: (Long) -> Unit): FileEvidence {
        val candidate = media.candidate ?: throw ProbeFailure("RESOURCE", "此媒体暂无可用下载资源")
        val partial = File(directory, "${UUID.randomUUID()}.partial")
        try {
            http.open(candidate.url, RequestPurpose.MEDIA, token).use { response ->
                val headerLength = response.headers["Content-Length"]?.toLongOrNull()?.takeIf { it >= 0 }
                val expected = headerLength ?: candidate.declaredBytes.takeIf { it > 0 }
                val (size, hash) = FileVerification.copy(if (slow) PacedInputStream(response.body, token) else response.body, partial, expected, token, progress, minOf(maxBytes,(directory.usableSpace / 3).coerceAtLeast(1)),timeoutMillis)
                if (candidate.declaredBytes > 0 && candidate.declaredBytes != size)
                    throw ProbeFailure("INTEGRITY", "文件下载不完整，请重试")
                val mime = FileVerification.sniff(partial)
                if ((media.kind == MediaKind.VIDEO) != mime.startsWith("video/"))
                    throw ProbeFailure("FORMAT", "下载文件类型不符，请重新解析后重试")
                val declaredMime = response.headers["Content-Type"].orEmpty().substringBefore(';').trim().lowercase()
                if (declaredMime.isNotEmpty() && declaredMime != "application/octet-stream" &&
                    declaredMime != mime && !(mime == "video/mp4" && declaredMime == "video/quicktime"))
                    throw ProbeFailure("FORMAT", "下载文件格式异常，请重试")
                val dimensions: Pair<Int, Int>
                var duration: Long? = null
                if (mime.startsWith("image/")) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(partial.path, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ProbeFailure("DECODE", "图片尺寸无法读取")
                    dimensions = bounds.outWidth to bounds.outHeight
                    var sample = 1
                    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
                    val bitmap = BitmapFactory.decodeFile(partial.path, BitmapFactory.Options().apply { inSampleSize = sample })
                        ?: throw ProbeFailure("DECODE", "图片抽样解码失败")
                    bitmap.recycle()
                } else {
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(partial.path)
                        val format = (0 until extractor.trackCount).map(extractor::getTrackFormat)
                            .firstOrNull { it.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/") }
                            ?: throw ProbeFailure("DECODE", "视频容器内没有可读取的视频轨道")
                        dimensions = format.getInteger(MediaFormat.KEY_WIDTH) to format.getInteger(MediaFormat.KEY_HEIGHT)
                        duration = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else null
                        if (dimensions.first <= 0 || dimensions.second <= 0) throw ProbeFailure("DECODE", "视频尺寸无效")
                    } finally { extractor.release() }
                }
                token.check()
                val complete = File(directory, partial.nameWithoutExtension + when (mime) { "video/quicktime" -> ".mov"; "video/mp4" -> ".mp4"; else -> ".image" })
                if (!partial.renameTo(complete)) throw ProbeFailure("STORAGE", "临时文件确认失败")
                return FileEvidence(complete, size, hash, mime, dimensions.first, dimensions.second, duration, response.host,
                    if (expected != null) "实际字节数与声明相符；不代表逐帧内容全部正确" else "接收到流结束；无长度声明，完整性仍待对照",
                    candidate.source)
            }
        } catch (error: Exception) { partial.delete(); throw error }
    }
}
