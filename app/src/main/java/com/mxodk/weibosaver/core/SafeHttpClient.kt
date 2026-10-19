package com.mxodk.weibosaver.core

import com.mxodk.weibosaver.model.*
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class RequestPurpose { VISITOR, DETAIL, MEDIA, SHORT_LINK }
object AddressPolicy {
    private fun under(host: String, root: String) = host == root || host.endsWith(".$root")
    fun validate(url: HttpUrl, purpose: RequestPurpose) {
        if (url.scheme != "https" || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty())
            throw ProbeFailure("ADDRESS", "资源链接不受支持")
        val allowed = when (purpose) {
            RequestPurpose.SHORT_LINK -> url.host == "t.cn"
            RequestPurpose.VISITOR -> url.host == "visitor.passport.weibo.cn"
            RequestPurpose.DETAIL -> url.host in setOf("weibo.com", "m.weibo.cn")
            RequestPurpose.MEDIA -> under(url.host, "sinaimg.cn") || under(url.host, "weibocdn.com") || url.host == "video.weibo.com"
        }
        if (!allowed) throw ProbeFailure("ADDRESS", "资源来源暂不支持")
    }
    fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b = address.address
        if (b.size == 4) {
            val a = b[0].toInt() and 255; val c = b[1].toInt() and 255
            if (a == 0 || a == 127 || a >= 224 || (a == 100 && c in 64..127) || (a == 169 && c == 254)) return false
        }
        if (b.size == 16 && ((b[0].toInt() and 254) == 252)) return false
        return true
    }
}
class WireResponse(val status: Int, val headers: Headers, val body: InputStream, val host: String, private val closing: () -> Unit) : Closeable {
    override fun close() { try { body.close() } finally { closing() } }
}

// 不使用全局 CookieJar、自动重试或自动跨主机重定向。
class SafeHttpClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .dns { hostname ->
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isEmpty() || addresses.any { !AddressPolicy.publicAddress(it) })
                throw java.net.UnknownHostException("上游解析为不允许的地址")
            addresses
        }.build()
    private val mediaClient = client.newBuilder().callTimeout(15, TimeUnit.MINUTES).build()
    // 预览与保存不共享文件缓存；沿用主机、DNS、HTTPS 边界并由调用者取消。
    fun previewClient(image: Boolean): OkHttpClient = client.newBuilder()
        .callTimeout(if (image) 35 else 300, TimeUnit.SECONDS)
        .dispatcher(Dispatcher().apply { maxRequests = 6; maxRequestsPerHost = 3 })
        .addInterceptor(PreviewInterceptor(if (image) 16L * 1024 * 1024 else null)).build()
    fun open(url: String, purpose: RequestPurpose, token: OperationToken, cookies: Map<String, String> = emptyMap(), form: FormBody? = null): WireResponse {
        var current = url.toHttpUrlOrNull() ?: throw ProbeFailure("ADDRESS", "上游资源地址无效")
        repeat(6) { redirectCount ->
            token.check(); AddressPolicy.validate(current, purpose)
            val request = Request.Builder().url(current)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36")
                .header("Referer", "https://weibo.com/")
            if (purpose != RequestPurpose.MEDIA) request.header("X-Requested-With", "XMLHttpRequest")
            if (purpose == RequestPurpose.DETAIL && cookies.isNotEmpty())
                request.header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            if (form != null) request.post(form)
            val call = (if (purpose == RequestPurpose.MEDIA) mediaClient else client).newCall(request.build()); token.register(call)
            val response = try { call.execute() } catch (error: java.io.IOException) {
                token.remove(call); token.check()
                throw ProbeFailure("NETWORK", "网络连接失败或超时，请检查网络后重试")
            }
            val code = response.code
            if (code in setOf(301, 302, 303, 307, 308)) {
                val next = response.header("Location")?.let(current::resolve)
                response.close(); token.remove(call)
                if (purpose != RequestPurpose.MEDIA || next == null || redirectCount == 5)
                    throw ProbeFailure("REDIRECT", "访问受限或链接跳转不受支持")
                // 每次跳转重新验证协议与主机，DNS 在真正连接时再次核对。
                AddressPolicy.validate(next, purpose); current = next
            } else {
                if (!response.isSuccessful) {
                    response.close(); token.remove(call)
                    when (code) {
                        429 -> throw ProbeFailure("RATE_LIMIT", "请求过于频繁，请稍后重试")
                        401, 403 -> throw ProbeFailure("ACCESS", "暂时无法访问该内容（HTTP $code）")
                        else -> throw ProbeFailure("HTTP_$code", "请求失败（HTTP $code），请稍后重试")
                    }
                }
                return WireResponse(code, response.headers, response.body.byteStream(), current.host) { response.close(); token.remove(call) }
            }
        }
        throw ProbeFailure("REDIRECT", "跳转次数超出验证上限")
    }
    // 短链只读取 t.cn 的跳转头，不接收 Cookie、不执行跳转页脚本。
    fun shortLocation(url: String, token: OperationToken): String {
        val address = url.toHttpUrlOrNull() ?: throw ProbeFailure("SHORT_LINK","短链地址无效")
        AddressPolicy.validate(address,RequestPurpose.SHORT_LINK); token.check()
        val call = client.newCall(Request.Builder().url(address)
            .header("User-Agent","Mozilla/5.0 Android WeiboSaver").build())
        token.register(call)
        try {
            val response = try { call.execute() } catch (_: java.io.IOException) {
                token.check(); throw ProbeFailure("NETWORK","短链连接失败或超时")
            }
            response.use {
                token.check()
                if (it.code == 429) throw ProbeFailure("RATE_LIMIT","短链接口限流，已停止")
                if (it.code in setOf(401,403)) throw ProbeFailure("ACCESS","短链接访问受限，请尝试完整帖子链接")
                if (it.code !in setOf(301,302,303,307,308)) throw ProbeFailure("SHORT_LINK","短链接无法解析，请使用完整帖子链接")
                return it.header("Location") ?: throw ProbeFailure("SHORT_LINK","短链跳转缺少目标地址")
            }
        } finally { token.remove(call) }
    }
    fun json(url: String, purpose: RequestPurpose, token: OperationToken, cookies: Map<String, String> = emptyMap(), form: FormBody? = null): Pair<String, Headers> {
        open(url, purpose, token, cookies, form).use { response ->
            val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                token.check(); val count = response.body.read(buffer); if (count < 0) break
                if (output.size() + count > 8 * 1024 * 1024) throw ProbeFailure("LIMIT", "帖子数据过大，暂时无法解析")
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name()) to response.headers
        }
    }
}
