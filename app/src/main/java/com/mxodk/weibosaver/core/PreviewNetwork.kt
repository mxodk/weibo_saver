package com.mxodk.weibosaver.core

import okhttp3.Interceptor
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.io.IOException

// 包装响应流，Content-Length 缺失或错误时也执行读取预算。
class LimitedPreviewBody(private val delegate: ResponseBody, private val limit: Long): ResponseBody() {
    private val bounded by lazy {
        object: ForwardingSource(delegate.source()) {
            private var received = 0L
            override fun read(sink: Buffer, byteCount: Long): Long {
                val count = super.read(sink,minOf(byteCount,(limit-received+1).coerceAtLeast(1)))
                if (count > 0) received += count
                if (received > limit) throw IOException("图片预览超过读取预算")
                return count
            }
        }.buffer()
    }
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()
    override fun source() = bounded
}

class PreviewInterceptor(private val imageLimit: Long? = null): Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        var request = chain.request().newBuilder().removeHeader("Cookie").removeHeader("Authorization")
            .header("Referer","https://weibo.com/").header("User-Agent","Mozilla/5.0 Android WeiboSaver").build()
        repeat(6) { hop ->
            try { AddressPolicy.validate(request.url,RequestPurpose.MEDIA) }
            catch (_: Exception) { throw IOException("预览地址未获允许") }
            val response = chain.proceed(request)
            if (response.code in setOf(301,302,303,307,308)) {
                val next = response.header("Location")?.let(request.url::resolve)
                response.close()
                if (next == null || hop == 5) throw IOException("预览跳转无效或超出上限")
                request = request.newBuilder().url(next).build()
            } else {
                if (!response.isSuccessful) { response.close(); throw IOException("预览请求未成功") }
                if (imageLimit != null) {
                    if (response.body.contentLength() > imageLimit) { response.close(); throw IOException("图片预览超过读取预算") }
                    return response.newBuilder().body(LimitedPreviewBody(response.body,imageLimit)).build()
                }
                return response
            }
        }
        throw IOException("预览跳转未完成")
    }
}
