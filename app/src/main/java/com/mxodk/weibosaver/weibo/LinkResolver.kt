package com.mxodk.weibosaver.weibo

import com.mxodk.weibosaver.model.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface InputTarget {
    data class Direct(val reference: PostReference): InputTarget
    data class Short(val url: String): InputTarget
}
fun interface ShortLinkGateway { fun location(url: String, token: OperationToken): String }

// 只识别输入中的受支持微博目标；不访问正文中的任意外部地址。
class LinkResolver(private val gateway: ShortLinkGateway) {
    companion object {
        private val id = Regex("[A-Za-z0-9]{9,25}")
        private val urls = Regex("https?://[^\\s<>\"'，。！？；、（）【】]+",RegexOption.IGNORE_CASE)
        private val hosts = setOf("weibo.com","www.weibo.com","m.weibo.cn","weibo.cn","www.weibo.cn","t.cn")
        fun normalized(raw: String): HttpUrl {
            val parsed = raw.toHttpUrlOrNull() ?: throw ProbeFailure("INPUT","链接格式不正确")
            if (parsed.host !in hosts || parsed.username.isNotEmpty() || parsed.password.isNotEmpty() ||
                parsed.port != (if (parsed.scheme == "https") 443 else 80))
                throw ProbeFailure("INPUT","请输入受支持的微博帖子链接")
            // 粘贴的旧链接可能含 HTTP，仅在解析输入时升级；实际请求始终 HTTPS。
            return parsed.newBuilder().scheme("https").port(443).fragment(null).build()
        }
        private fun target(raw: String): InputTarget {
            val url = normalized(raw); val segments = url.pathSegments.filter { it.isNotEmpty() }
            if (url.host == "t.cn") {
                if (segments.size != 1 || !Regex("[A-Za-z0-9]{3,32}").matches(segments[0])) throw ProbeFailure("INPUT","短链路径不受支持")
                return InputTarget.Short(url.toString())
            }
            val key = when {
                segments.size == 2 && segments[0] in setOf("status","detail") -> segments[1]
                url.host in setOf("weibo.cn","www.weibo.cn") && segments.size == 2 && segments[0] == "comment" -> segments[1]
                url.host in setOf("weibo.com","www.weibo.com") && segments.size == 2 && segments[0].all(Char::isDigit) -> segments[1]
                else -> ""
            }
            if (!id.matches(key)) throw ProbeFailure("INPUT","不是受支持的单个微博帖子链接")
            return InputTarget.Direct(PostReference(key))
        }
        fun identify(input: String): InputTarget {
            val value = input.trim()
            if (value.isEmpty()) throw ProbeFailure("INPUT","请输入一个微博帖子链接")
            if (value.length > 16 * 1024) throw ProbeFailure("INPUT","输入过长，请仅保留一个帖子链接")
            if (id.matches(value)) return InputTarget.Direct(PostReference(value))
            val candidates = urls.findAll(value).map { it.value.trimEnd('.',',',';',')',']','}') }.toList()
            val targets = candidates.mapNotNull { raw ->
                val host = raw.toHttpUrlOrNull()?.host
                if (host in hosts) target(raw) else null
            }.distinct()
            if (targets.size > 1) throw ProbeFailure("MULTIPLE_LINKS","检测到多个帖子链接，请只保留一个")
            return targets.singleOrNull() ?: throw ProbeFailure("INPUT","没有识别到受支持的微博帖子链接")
        }
    }
    fun resolve(input: String, token: OperationToken): PostReference {
        var current = identify(input); val visited = mutableSetOf<String>()
        repeat(6) { hop ->
            token.check()
            when (val target = current) {
                is InputTarget.Direct -> return target.reference
                is InputTarget.Short -> {
                    if (hop == 5 || !visited.add(target.url)) throw ProbeFailure("SHORT_LINK","短链跳转过多或循环，已停止")
                    val location = gateway.location(target.url,token)
                    val next = normalized(target.url).resolve(location) ?: throw ProbeFailure("SHORT_LINK","短链跳转地址无效")
                    current = Companion.target(next.toString())
                }
            }
        }
        throw ProbeFailure("SHORT_LINK","短链未能展开为帖子")
    }
}
