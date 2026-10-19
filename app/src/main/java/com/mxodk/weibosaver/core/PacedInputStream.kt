package com.mxodk.weibosaver.core

import com.mxodk.weibosaver.model.OperationToken
import java.io.FilterInputStream
import java.io.InputStream

// 仅 P2 人工验证限速，等待期间持续响应取消，不伪造传输字节或成功进度。
class PacedInputStream(input: InputStream, private val token: OperationToken, private val bytesPerSecond: Long = 64 * 1024): FilterInputStream(input) {
    private val started = System.nanoTime()
    private var received = 0L
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        while (System.nanoTime() - started < received * 1_000_000_000L / bytesPerSecond) {
            token.check(); Thread.sleep(50)
        }
        token.check()
        val count = super.read(buffer, offset, minOf(length, 8192))
        if (count > 0) received += count
        return count
    }
}
