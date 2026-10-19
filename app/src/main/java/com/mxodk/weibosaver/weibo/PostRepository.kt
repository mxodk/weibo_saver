package com.mxodk.weibosaver.weibo

import com.mxodk.weibosaver.model.*
import kotlinx.serialization.json.JsonObject

// 网关可以在离线测试中替换；真实请求与模拟响应分别记录。
interface PostGateway {
    fun createVisitor(token: OperationToken): Map<String, String>
    fun desktop(reference: PostReference, session: Map<String, String>, token: OperationToken): JsonObject
    fun mobile(reference: PostReference, token: OperationToken): JsonObject
}
class PostRepository(private val gateway: PostGateway, private val parser: PostParser = PostParser()) {
    private var visitor: Map<String, String>? = null
    @Synchronized private fun session(token: OperationToken): Map<String, String> {
        token.check()
        if (visitor == null) visitor = gateway.createVisitor(token)
        return requireNotNull(visitor)
    }
    @Synchronized private fun invalidate() { visitor = null }
    fun fetch(reference: PostReference, token: OperationToken): ProbePost {
        var attempts = 0
        var payload: JsonObject? = null
        while (true) {
            token.check()
            val data = gateway.desktop(reference, session(token), token)
            when (data.str("ok")) {
                "-100" -> {
                    if (attempts++ == 0) { invalidate(); continue }
                    throw ProbeFailure("SESSION", "暂时无法访问微博，请稍后重试")
                }
                "0" -> throw ProbeFailure("ACCESS", "帖子暂时无法访问，请检查链接或稍后重试")
            }
            payload = data
            break
        }
        token.check()
        val primary = parser.parse(requireNotNull(payload), reference)
        if (!primary.needsSupplement && primary.bodyStatus !in setOf(BodyStatus.TRUNCATED,BodyStatus.MISSING)) return primary
        return try {
            token.check()
            val extra = parser.parse(gateway.mobile(reference, token), reference, "mobile")
            parser.supplement(primary, extra)
        } catch (error: ProbeFailure) {
            if (error.category == "IDENTITY") throw error
            val issue = ParseIssue(IssueScope.ACCESS,"SUPPLEMENT_${error.category}","部分内容未能获取，可稍后重新解析")
            primary.copy(warnings = primary.warnings + issue.message,issues = primary.issues + issue)
        }
    }
}
