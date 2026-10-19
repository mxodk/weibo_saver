package com.mxodk.weibosaver.weibo

import com.mxodk.weibosaver.model.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.mxodk.weibosaver.core.AddressPolicy
import com.mxodk.weibosaver.core.RequestPurpose

internal fun JsonObject.obj(key: String) = this[key] as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.arr(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())
internal fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.number(key: String) = str(key).toLongOrNull() ?: 0L

object JsonPayload {
    fun parse(text: String): JsonObject {
        val trimmed = text.trim()
        val json = if (trimmed.startsWith("{")) trimmed else {
            // 只提取访客接口 JSONP 的 JSON 数据，绝不执行 JavaScript。
            if (trimmed.startsWith("<") || !trimmed.contains("visitor_gray_callback"))
                throw ProbeFailure("RESPONSE", "上游返回非预期页面或数据")
            val start = trimmed.indexOf('{'); val end = trimmed.lastIndexOf('}')
            if (start < 0 || end < start) throw ProbeFailure("RESPONSE", "访客响应结构不完整")
            trimmed.substring(start, end + 1)
        }
        return runCatching { Json.parseToJsonElement(json) as JsonObject }.getOrElse {
            throw ProbeFailure("RESPONSE", "上游 JSON 数据无法解析")
        }
    }
}

class PostParser {
    private fun flag(status: JsonObject, key: String) = status.str(key) in setOf("true","1")
    fun parse(payload: JsonObject, reference: PostReference, origin: String = "desktop"): ProbePost {
        if (payload.str("ok") == "0") throw ProbeFailure("ACCESS","帖子暂时无法访问，请稍后重试")
        if (payload.str("ok") == "-100") throw ProbeFailure("SESSION","访客会话受限")
        val status = if (payload["data"] is JsonObject) payload.obj("data") else payload
        val id = status.str("idstr").ifBlank { status.str("id") }
        val bid = status.str("mblogid").ifBlank { status.str("bid") }
        if (id.isBlank() || reference.key !in setOf(id,bid)) throw ProbeFailure("IDENTITY","响应帖子身份与输入不一致，已停止")
        val issues = mutableListOf<ParseIssue>()
        fun issue(scope: IssueScope, code: String, message: String, mediaId: String? = null) { issues += ParseIssue(scope,code,message,mediaId) }
        val long = status.obj("longText")
        val longRaw = long.str("longTextContent_raw")
        val longHtml = long.str("longTextContent")
        val raw = (status["text_raw"] as? JsonPrimitive)?.contentOrNull
        val html = (status["text"] as? JsonPrimitive)?.contentOrNull
        val convertedLong = if (longHtml.isNotBlank()) BodyText.fromHtml(longHtml) else ""
        val inlineLong = longRaw.isNotBlank() || convertedLong.isNotBlank()
        val text = when {
            longRaw.isNotBlank() -> longRaw.replace("\r\n","\n").trim()
            convertedLong.isNotBlank() -> convertedLong
            raw != null -> raw.replace("\r\n","\n").trim()
            html != null -> BodyText.fromHtml(html)
            else -> ""
        }
        val navigation = Regex("<a\\b[^>]*>\\s*(?:展开全文|查看全文|全文)\\s*</a>",RegexOption.IGNORE_CASE).containsMatchIn(html.orEmpty())
        val bodyStatus = when {
            inlineLong -> BodyStatus.COMPLETE
            flag(status,"isLongText") || flag(status,"is_long_text") || navigation -> BodyStatus.TRUNCATED
            raw != null || html != null -> BodyStatus.COMPLETE
            else -> BodyStatus.MISSING
        }
        if (bodyStatus == BodyStatus.TRUNCATED) issue(IssueScope.BODY,"BODY_TRUNCATED","正文可能截断，尚未取得可确认的全文字段")
        if (bodyStatus == BodyStatus.MISSING) issue(IssueScope.BODY,"BODY_MISSING","当前帖正文缺字段，未借用被转发帖正文")
        val result = linkedMapOf<String,ProbeMedia>()
        fun resource(rawUrl: String, source: String, width: Int = 0, height: Int = 0): ResourceCandidate? {
            val url = rawUrl.toHttpUrlOrNull() ?: return null
            if (runCatching { AddressPolicy.validate(url,RequestPurpose.MEDIA) }.isFailure) {
                issue(IssueScope.ITEM,"ADDRESS_UNSUPPORTED","部分媒体的资源链接暂不支持")
                return null
            }
            return ResourceCandidate(url.toString(),source,width.coerceAtLeast(0),height.coerceAtLeast(0))
        }
        fun put(media: ProbeMedia) {
            if (result.putIfAbsent(media.id,media) != null)
                issue(IssueScope.MEDIA,"DUPLICATE_ID","媒体稳定标识重复，已去重但不能认定列表完整",media.id)
        }
        fun addImage(data: JsonObject, fallbackId: String, path: String) {
            val explicitId = data.str("pic_id").ifBlank { data.str("pid") }
            val mediaId = explicitId.ifBlank { fallbackId }
            if (mediaId.isBlank()) { issue(IssueScope.MEDIA,"MISSING_ID","图片缺稳定标识，未按数组位置推测"); return }
            if (fallbackId.isNotBlank() && explicitId.isNotBlank() && fallbackId != explicitId) {
                issue(IssueScope.ITEM,"MEDIA_IDENTITY","图片标识与来源键不符，未选择该资源",fallbackId)
                put(ProbeMedia(fallbackId,MediaKind.IMAGE,null)); return
            }
            val live = data.str("type").equals("livephoto",true) || data.str("videoSrc").isNotBlank() ||
                (data.str("video").contains("livephoto=") || data.str("video").substringBefore('?').endsWith(".mov",true))
            val candidates = listOf("largest","original","large").mapNotNull { name ->
                val value = data.obj(name); val parsed = value.str("url").toHttpUrlOrNull() ?: return@mapNotNull null
                val url = if (parsed.encodedPath.startsWith("/mw2000/")) parsed.newBuilder()
                    .encodedPath(parsed.encodedPath.replaceFirst("/mw2000/","/large/")).build().toString() else parsed.toString()
                resource(url,"$origin.$path.$name",value.number("width").toInt(),value.number("height").toInt())
            }
            val best = candidates.maxByOrNull { it.width.toLong() * it.height }
            if (best == null) issue(IssueScope.ITEM,"CANDIDATE_MISSING","部分图片无法获取高清资源",mediaId)
            val preview = listOf("thumbnail","bmiddle").firstNotNullOfOrNull { key ->
                resource(data.obj(key).str("url"),"$origin.$path.$key")
            }
            val motion = if (live) listOf("video_hd","video","videoSrc").firstNotNullOfOrNull { key ->
                resource(data.str(key),"$origin.$path.$key")
            } else null
            if (live) issue(IssueScope.ITEM,"STATIC_ONLY","Live Photo $mediaId 仅支持静态保存，动态部分不另计视频",mediaId)
            put(ProbeMedia(mediaId,if (live) MediaKind.LIVE_PHOTO else MediaKind.IMAGE,best,preview,motion))
        }
        fun addVideo(data: JsonObject, fallbackId: String, path: String) {
            val info = data.obj("media_info")
            val mediaId = data.str("object_id").ifBlank { info.str("media_id") }.ifBlank { fallbackId }
            if (mediaId.isBlank()) { issue(IssueScope.MEDIA,"MISSING_ID","视频缺稳定标识，未按位置推测"); return }
            val candidates = info.arr("playback_list").mapNotNull { element ->
                val entry = element as? JsonObject ?: return@mapNotNull null; val play = entry.obj("play_info")
                if (!play.str("mime").startsWith("video/")) return@mapNotNull null
                resource(play.str("url"),"$origin.$path.media_info.playback_list",play.number("width").toInt(),play.number("height").toInt())
                    ?.copy(bitrate=play.number("bitrate"),qualityIndex=entry.obj("meta").number("quality_index").toInt(),declaredBytes=play.number("size"))
            }
            val best = candidates.maxWithOrNull(compareBy<ResourceCandidate>{it.qualityIndex}.thenBy{it.width.toLong()*it.height}.thenBy{it.bitrate})
            if (best == null) issue(IssueScope.ITEM,"CANDIDATE_MISSING","部分视频无法获取下载资源",mediaId)
            // 桌面 page_pic 可为字符串，移动端也可能为对象；仅使用本视频对象中的封面。
            val covers = listOf(
                "page_pic" to (data.str("page_pic").ifBlank { data.obj("page_pic").str("url") }),
                "pic_info.pic_middle" to data.obj("pic_info").obj("pic_middle").str("url"),
                "pic_info.pic_big" to data.obj("pic_info").obj("pic_big").str("url"),
                "media_info.big_pic_info.pic_middle" to info.obj("big_pic_info").obj("pic_middle").str("url"),
                "media_info.big_pic_info.pic_big" to info.obj("big_pic_info").obj("pic_big").str("url")
            )
            val preview = covers.firstNotNullOfOrNull { (field,url) ->
                if (url.isBlank()) null else resource(url,"$origin.$path.$field")
            }
            val duration = info.str("duration").toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }?.toLong()
            put(ProbeMedia(mediaId,MediaKind.VIDEO,best,preview,durationSeconds=duration))
        }
        val mixed = status.obj("mix_media_info").arr("items")
        var order = MediaOrder.NONE
        if (mixed.isNotEmpty()) {
            order = MediaOrder.MIXED_SOURCE
            mixed.forEachIndexed { index, element ->
                val item = element as? JsonObject
                if (item == null) issue(IssueScope.MEDIA,"MALFORMED_ITEM","混合列表含无法解析项")
                else when(item.str("type")) {
                    "pic" -> addImage(item.obj("data"),item.str("id"),"mix_media_info.items[$index].data")
                    "video" -> addVideo(item.obj("data"),item.str("id"),"mix_media_info.items[$index].data")
                    else -> issue(IssueScope.MEDIA,"UNSUPPORTED_ITEM","混合列表含未支持类型，不能报告完整列表")
                }
            }
        } else {
            val ids = status.arr("pic_ids"); val infos = status.obj("pic_infos")
            if (ids.isNotEmpty()) {
                order = MediaOrder.IMAGE_IDS
                ids.forEach { value ->
                    val key = (value as? JsonPrimitive)?.contentOrNull.orEmpty()
                    addImage(infos[key] as? JsonObject ?: JsonObject(emptyMap()),key,"pic_infos[$key]")
                }
            } else {
                val pics = status.arr("pics")
                if (pics.isNotEmpty()) order = MediaOrder.MOBILE_IMAGES
                pics.forEachIndexed { index,value ->
                    if (value is JsonObject) addImage(value,value.str("pid"),"pics[$index]")
                    else issue(IssueScope.MEDIA,"MALFORMED_ITEM","图片列表含无法解析项")
                }
            }
            val page = status.obj("page_info")
            if (page.str("type") in setOf("video","11") || page.str("object_type") == "video") addVideo(page,page.str("page_id"),"page_info")
            if (result.values.any { it.kind == MediaKind.VIDEO } && result.values.any { it.kind != MediaKind.VIDEO }) {
                order = MediaOrder.UNPROVEN
                issue(IssueScope.ORDER,"ORDER_UNPROVEN","分离字段未证明图视频相对顺序，未声称还原原帖顺序")
            }
        }
        val expected = maxOf(status.number("pic_num"),status.arr("pic_ids").size.toLong())
        val found = result.values.count { it.kind != MediaKind.VIDEO }
        if (expected > found) issue(IssueScope.MEDIA,"COUNT_MISMATCH","图片列表不完整：声明 $expected，识别 $found")
        val lacksEvidence = result.isEmpty() && expected == 0L && bodyStatus == BodyStatus.MISSING
        if (lacksEvidence) issue(IssueScope.MEDIA,"MEDIA_UNKNOWN","缺少足够正文／媒体证据，不能认定为空帖")
        val mediaStatus = when {
            lacksEvidence -> MediaListStatus.UNKNOWN
            issues.any { it.scope == IssueScope.MEDIA } -> MediaListStatus.PARTIAL
            else -> MediaListStatus.COMPLETE
        }
        val user = status.obj("user")
        val author = if (user.isNotEmpty()) PostAuthor(user.str("idstr").ifBlank { user.str("id") },user.str("screen_name")) else null
        // 仅读当前对象；retweeted_status 的正文、媒体、用户和长文都不读取。
        return ProbePost(id,result.values.toList(),issues.map { it.message }.distinct(),
            mediaStatus != MediaListStatus.COMPLETE || result.values.any { it.candidate == null },expected,text,
            bodyStatus,mediaStatus,order,author,status["retweeted_status"] is JsonObject,issues)
    }

    fun supplement(primary: ProbePost, extra: ProbePost): ProbePost {
        if (primary.id != extra.id) throw ProbeFailure("IDENTITY","补充响应来自其他帖子，已停止")
        val extras = extra.media.associateBy { it.id }
        val merged = primary.media.map { old ->
            val other = extras[old.id]
            val compatible = other != null && (other.kind == old.kind || setOf(other.kind,old.kind) == setOf(MediaKind.IMAGE,MediaKind.LIVE_PHOTO))
            if (!compatible) old else old.copy(candidate=old.candidate ?: other!!.candidate,
                preview=old.preview ?: other!!.preview,liveMotion=old.liveMotion ?: other!!.liveMotion,
                durationSeconds=old.durationSeconds ?: other!!.durationSeconds,
                kind=if (old.kind == MediaKind.LIVE_PHOTO || other!!.kind == MediaKind.LIVE_PHOTO) MediaKind.LIVE_PHOTO else old.kind)
        }
        val added = extra.media.filter { e -> primary.media.none { it.id == e.id } }
        val all = merged + added
        val expected = maxOf(primary.expectedImages,extra.expectedImages)
        val useExtraBody = primary.bodyStatus != BodyStatus.COMPLETE && extra.bodyStatus == BodyStatus.COMPLETE && (primary.bodyStatus == BodyStatus.MISSING || extra.text.length > primary.text.length)
        val body = if (useExtraBody) extra else primary
        val issues = (primary.issues + extra.issues).distinct().filterNot { issue ->
            (issue.scope == IssueScope.BODY && body.bodyStatus == BodyStatus.COMPLETE) ||
            (issue.code == "CANDIDATE_MISSING" && all.any { it.id == issue.mediaId && it.candidate != null }) ||
            (issue.code == "COUNT_MISMATCH" && all.count { it.kind != MediaKind.VIDEO } >= expected) ||
            (issue.code == "MEDIA_UNKNOWN" && extra.mediaStatus != MediaListStatus.UNKNOWN)
        }.toMutableList()
        if (added.isNotEmpty()) issues += ParseIssue(IssueScope.ORDER,"ORDER_UNPROVEN","补充了经当前帖身份核对的媒体；跨来源相对顺序无法证明")
        primary.media.filter { old -> extras[old.id]?.let { e -> e.kind != old.kind && setOf(e.kind,old.kind) != setOf(MediaKind.IMAGE,MediaKind.LIVE_PHOTO) } == true }
            .forEach { issues += ParseIssue(IssueScope.ITEM,"TYPE_CONFLICT","部分媒体类型无法确认",it.id) }
        val mediaStatus = when {
            all.isEmpty() && primary.mediaStatus == MediaListStatus.UNKNOWN && extra.mediaStatus == MediaListStatus.UNKNOWN -> MediaListStatus.UNKNOWN
            issues.any { it.scope == IssueScope.MEDIA } -> MediaListStatus.PARTIAL
            else -> MediaListStatus.COMPLETE
        }
        return primary.copy(media=all,text=body.text,bodyStatus=body.bodyStatus,mediaStatus=mediaStatus,
            order=if (added.isNotEmpty()) MediaOrder.UNPROVEN else primary.order,
            expectedImages=expected,issues=issues.distinct(),warnings=issues.map{it.message}.distinct(),
            needsSupplement=mediaStatus != MediaListStatus.COMPLETE || all.any { it.candidate == null })
    }
}
