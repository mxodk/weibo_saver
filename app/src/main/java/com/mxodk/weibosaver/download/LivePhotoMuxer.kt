package com.mxodk.weibosaver.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.mxodk.weibosaver.model.OperationToken
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.abs

internal object LivePhotoMuxer {
    private const val TIME_TOLERANCE_US = 2_000L
    private const val SAMPLE_LIMIT = 10_000

    fun remux(source: File, target: File, token: OperationToken) {
        // 直接复制路径以源样本表、块边界和落盘摘要验收。目标相册在成品上验证播放。
        val layout = try {
            SourceTimingMuxer.remux(source.toPath(), target.toPath(), token::check)
            return
        } catch (error: SourceTimingMuxer.UnsupportedLayout) {
            error.message
        } catch (error: IOException) {
            throw MotionPackingFailure("直接复制读写校验（${error.javaClass.simpleName}）", error)
        }
        val buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024)
        val original = checked("读取源轨道", layout) { inspect(source, buffer, token) }
        if (original.any { it.reordered })
            throw MotionPackingFailure("源布局：$layout，系统封装无法保留重排时序")
        checked("系统封装", layout) { platformRemux(source, target, buffer, token) }
        val output = checked("读取系统封装结果", layout) { inspect(target, buffer, token) }
        if (output.size != original.size || original.zip(output).any { (before, after) -> !before.matches(after) })
            throw MotionPackingFailure("源布局：$layout，系统封装前后样本或时序不一致")
    }

    private inline fun <T> checked(step: String, layout: String?, action: () -> T): T = try {
        action()
    } catch (error: IOException) {
        throw MotionPackingFailure("源布局：$layout，$step（${error.javaClass.simpleName}）", error)
    }

    class MotionPackingFailure(val detail: String, cause: IOException? = null) : IOException(detail, cause)

    private class Track(val index: Int, val format: MediaFormat) {
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
        val duration = format.getLong(MediaFormat.KEY_DURATION, -1)
        val rotation = Math.floorMod(format.getInteger(MediaFormat.KEY_ROTATION, 0), 360)
        val times = mutableListOf<Long>()
        val digest = MessageDigest.getInstance("SHA-256")
        var reordered = false
            private set

        fun sample(time: Long, flags: Int, data: ByteBuffer, size: Int) {
            if (times.size >= SAMPLE_LIMIT) throw IOException("动态资源样本过多")
            if (times.lastOrNull()?.let { time < it } == true) reordered = true
            times.add(time)
            digest.update(ByteBuffer.allocate(8).putInt(size).putInt(flags).array())
            digest.update(data)
        }

        fun matches(other: Track): Boolean =
            mime == other.mime && rotation == other.rotation &&
                abs(duration - other.duration) <= TIME_TOLERANCE_US &&
                times.size == other.times.size &&
                times.zip(other.times).all { (before, after) -> abs(before - after) <= TIME_TOLERANCE_US } &&
                digest.digest().contentEquals(other.digest.digest())
    }

    private fun openTracks(extractor: MediaExtractor, source: File): List<Track> {
        extractor.setDataSource(source.path)
        val tracks = (0 until extractor.trackCount).map { Track(it, extractor.getTrackFormat(it)) }
            .filter { it.mime.startsWith("video/") || it.mime.startsWith("audio/") }
        val video = tracks.filter { it.mime.startsWith("video/") }
        val audio = tracks.filter { it.mime.startsWith("audio/") }
        if (video.size != 1 || video.single().mime !in setOf("video/avc", "video/hevc") ||
            audio.size > 1 || audio.any { it.mime != "audio/mp4a-latm" } || tracks.any { it.duration <= 0 })
            throw IOException("动态资源轨道超出支持范围")
        tracks.forEach { extractor.selectTrack(it.index) }
        return tracks.sortedBy { it.mime }
    }

    private fun samples(extractor: MediaExtractor, buffer: ByteBuffer, token: OperationToken,
                        consume: (Int, Long, Int, Int) -> Unit) {
        while (extractor.sampleTrackIndex >= 0) {
            token.check()
            val size = extractor.sampleSize
            val flags = extractor.sampleFlags
            if (size !in 1..buffer.capacity().toLong() || flags and MediaExtractor.SAMPLE_FLAG_SYNC.inv() != 0)
                throw IOException("动态资源样本大小或标记超出支持范围")
            buffer.clear()
            if (extractor.readSampleData(buffer, 0).toLong() != size) throw IOException("动态资源样本读取不完整")
            buffer.position(0).limit(size.toInt())
            consume(extractor.sampleTrackIndex, extractor.sampleTime, flags, size.toInt())
            extractor.advance()
        }
    }

    private fun inspect(file: File, buffer: ByteBuffer, token: OperationToken): List<Track> {
        val extractor = MediaExtractor()
        try {
            val tracks = openTracks(extractor, file)
            val byIndex = tracks.associateBy { it.index }
            samples(extractor, buffer, token) { index, time, flags, size ->
                byIndex.getValue(index).sample(time, flags, buffer, size)
            }
            if (tracks.any { it.times.isEmpty() }) throw IOException("动态资源存在空轨道")
            return tracks
        } finally {
            extractor.release()
        }
    }

    private fun platformRemux(source: File, target: File, buffer: ByteBuffer, token: OperationToken) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            val tracks = openTracks(extractor, source)
            val writer = MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = writer
            val outputTracks = tracks.associate { track ->
                if (track.mime.startsWith("video/")) {
                    writer.setOrientationHint(track.rotation)
                    track.format.removeKey(MediaFormat.KEY_ROTATION)
                }
                track.index to writer.addTrack(track.format)
            }
            writer.start()
            val info = MediaCodec.BufferInfo()
            samples(extractor, buffer, token) { index, time, flags, size ->
                val muxFlags = if (flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, size, time, muxFlags)
                writer.writeSampleData(outputTracks.getValue(index), buffer, info)
            }
            // 空 EOS 携带源轨道结束时间，保留末样本时长。
            tracks.forEach { track ->
                token.check()
                info.set(0, 0, track.duration, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                writer.writeSampleData(outputTracks.getValue(track.index), ByteBuffer.allocate(0), info)
            }
            writer.stop()
        } finally {
            extractor.release()
            muxer?.release()
        }
    }
}
