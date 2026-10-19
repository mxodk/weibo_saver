package com.mxodk.weibosaver.weibo

import com.mxodk.weibosaver.core.*
import com.mxodk.weibosaver.model.*
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl

class WeiboGateway(private val http: SafeHttpClient) : PostGateway {
    override fun createVisitor(token: OperationToken): Map<String, String> {
        val url = "https://visitor.passport.weibo.cn/visitor/genvisitor2"
        val (text, headers) = http.json(url, RequestPurpose.VISITOR, token, form = FormBody.Builder().add("cb", "visitor_gray_callback").build())
        val data = JsonPayload.parse(text).obj("data")
        val generated = mutableMapOf<String, String>()
        headers.values("Set-Cookie").forEach { value ->
            Cookie.parse(url.toHttpUrl(), value)?.let { if (it.name in setOf("SUB", "SUBP")) generated[it.name] = it.value }
        }
        for (name in listOf("sub", "subp")) data.str(name).takeIf { it.isNotBlank() && it != "[REDACTED]" }?.let { generated[name.uppercase()] = it }
        if (generated["SUB"].isNullOrBlank()) throw ProbeFailure("SESSION", "未取得有效临时访客会话，请稍后重试")
        return generated.toMap()
    }
    override fun desktop(reference: PostReference, session: Map<String, String>, token: OperationToken) = JsonPayload.parse(
        http.json("https://weibo.com/ajax/statuses/show".toHttpUrl().newBuilder().addQueryParameter("id", reference.key)
            .addQueryParameter("locale", "zh-CN").addQueryParameter("isGetLongText", "true").build().toString(),
            RequestPurpose.DETAIL, token, session).first)
    override fun mobile(reference: PostReference, token: OperationToken) = JsonPayload.parse(
        http.json("https://m.weibo.cn/statuses/show".toHttpUrl().newBuilder().addQueryParameter("id", reference.key).build().toString(),
            RequestPurpose.DETAIL, token).first)
}
