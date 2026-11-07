package com.mxodk.weibosaver.download

import android.media.ExifInterface
import com.mxodk.weibosaver.core.MediaProbe
import com.mxodk.weibosaver.model.MediaKind
import com.mxodk.weibosaver.model.OperationToken
import com.mxodk.weibosaver.model.ProbeFailure
import com.mxodk.weibosaver.model.ProbeMedia
import com.mxodk.weibosaver.storage.SaveAsset
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

internal object LivePhotoSave {
    // 封装原型读取整份源文件，给两个并发任务留出内存余量。
    private const val MAX_STILL_BYTES = 16L * 1024 * 1024
    private const val MAX_MOTION_BYTES = 24L * 1024 * 1024

    fun prepare(media: ProbeMedia, probe: MediaProbe, token: OperationToken, progress: (String, Long) -> Unit): SaveAsset {
        val motion = media.liveMotion ?: throw ProbeFailure("LIVE_MOTION", "实况缺少动态资源，可在预览中单独保存静态图片")
        val still = probe.fetch(media, token, byteLimit = MAX_STILL_BYTES) { progress("传输中", it) }
        if (still.mime != "image/jpeg") throw ProbeFailure("LIVE_FORMAT", "实况静态资源不是可封装的 JPEG")
        val video = probe.fetch(media.copy(kind = MediaKind.VIDEO, candidate = motion), token, byteLimit = MAX_MOTION_BYTES) {
            progress("传输中", still.bytes + it)
        }
        progress("封装中", still.bytes + video.bytes)
        val folder = requireNotNull(still.file.parentFile)
        val mp4 = File.createTempFile("live-", ".mp4", folder)
        val photo = File.createTempFile("live-", ".jpg", folder)
        var complete = false
        var step = "动态封装"
        try {
            token.check()
            val sourceContainer = SourceTimingMuxer.canEmbed(video.file.toPath(), token::check)
            val embedded = if (sourceContainer) video.file else mp4.also {
                LivePhotoMuxer.remux(video.file, it, token)
            }
            val expectedMotionHash = if (sourceContainer) video.sha256 else digest(embedded, token)
            step = "封面封装"
            val orientation = ExifInterface(still.file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                .let { if (it == ExifInterface.ORIENTATION_UNDEFINED) 1 else it }
            val imageLength = OplusPhotoPacker.pack(still.file.toPath(), embedded.toPath(), photo.toPath(), orientation, token::check)
            step = "文件校验"
            val size = photo.length()
            if (size != imageLength.toLong() + embedded.length() || embedded.length() == 0L)
                throw IOException("实况容器长度无效")
            RandomAccessFile(photo, "r").use {
                it.seek(imageLength.toLong() - 2)
                if (it.readUnsignedShort() != 0xffd9) throw IOException("实况主图边界无效")
            }
            if (digest(photo, token, imageLength.toLong()) != expectedMotionHash)
                throw IOException("实况动态片段读回校验失败")
            val asset = SaveAsset(photo, "image/jpeg", size, digest(photo, token))
            complete = true
            return asset
        } catch (error: IOException) {
            val detail = (error as? LivePhotoMuxer.MotionPackingFailure)?.detail ?: error.javaClass.simpleName
            throw ProbeFailure("LIVE_PACK", "实况照片在${step}时失败（$detail），可在预览中单独保存静态图片")
        } finally {
            mp4.delete()
            video.file.delete()
            still.file.delete()
            if (!complete) photo.delete()
        }
    }

    private fun digest(file: File, token: OperationToken, offset: Long = 0): String {
        val digest = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(file, "r").use { stream ->
            stream.seek(offset)
            val buffer = ByteArray(64 * 1024)
            while (true) {
                token.check()
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
