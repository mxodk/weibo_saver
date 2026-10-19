package com.mxodk.weibosaver.model

import java.util.concurrent.CancellationException
import okhttp3.Call

// 凭据和完整资源地址只在内存对象中使用，不进入诊断报告。
class ProbeFailure(val category: String, override val message: String) : Exception(message)
data class PostReference(val key: String) {
    companion object {
        fun fromInput(input: String): PostReference =
            when (val target = com.mxodk.weibosaver.weibo.LinkResolver.identify(input)) {
                is com.mxodk.weibosaver.weibo.InputTarget.Direct -> target.reference
                is com.mxodk.weibosaver.weibo.InputTarget.Short -> throw ProbeFailure("INPUT", "短链需要先执行受控展开")
            }
    }
}
enum class MediaKind { IMAGE, VIDEO, LIVE_PHOTO }
data class ResourceCandidate(
    val url: String, val source: String, val width: Int = 0, val height: Int = 0,
    val bitrate: Long = 0, val qualityIndex: Int = 0, val declaredBytes: Long = 0,
)
enum class BodyStatus { COMPLETE, TRUNCATED, UNKNOWN, MISSING }
enum class MediaListStatus { COMPLETE, PARTIAL, UNKNOWN }
enum class MediaOrder { MIXED_SOURCE, IMAGE_IDS, MOBILE_IMAGES, UNPROVEN, NONE }
enum class IssueScope { BODY, MEDIA, ORDER, ITEM, ACCESS }
data class ParseIssue(val scope: IssueScope, val code: String, val message: String, val mediaId: String? = null)
data class PostAuthor(val id: String, val name: String)
data class ProbeMedia(val id: String, val kind: MediaKind, val candidate: ResourceCandidate?,
    val preview: ResourceCandidate? = null, val liveMotion: ResourceCandidate? = null, val durationSeconds: Long? = null)
data class ProbePost(val id: String, val media: List<ProbeMedia>, val warnings: List<String> = emptyList(),
    val needsSupplement: Boolean = false, val expectedImages: Long = 0, val text: String = "",
    val bodyStatus: BodyStatus = BodyStatus.UNKNOWN, val mediaStatus: MediaListStatus = MediaListStatus.UNKNOWN,
    val order: MediaOrder = MediaOrder.NONE, val author: PostAuthor? = null, val isRepost: Boolean = false,
    val issues: List<ParseIssue> = emptyList())

// 取消既阻止后续派发，也取消正在执行的网络调用。
class OperationToken {
    private var cancelled = false
    private val calls = mutableSetOf<Call>()
    @Synchronized fun check() { if (cancelled) throw CancellationException("用户取消") }
    @Synchronized fun register(call: Call) { check(); calls.add(call) }
    @Synchronized fun remove(call: Call) { calls.remove(call) }
    @Synchronized fun cancel() { cancelled = true; calls.forEach(Call::cancel); calls.clear() }
}
