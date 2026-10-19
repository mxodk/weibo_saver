package com.mxodk.weibosaver

import com.mxodk.weibosaver.core.*
import com.mxodk.weibosaver.model.*
import com.mxodk.weibosaver.weibo.*
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.net.InetAddress

// 全部是合成数据，离线运行不访问微博；不包含用户帖子正文或签名资源。
class ProbeTest {
    private val ref = PostReference("9007199254740993")
    private fun json(value: String) = JsonPayload.parse(value)
    private fun failure(category: String, block: () -> Unit) {
        try { block(); fail("应拒绝：$category") } catch (error: ProbeFailure) { assertEquals(category, error.category) }
    }
    @Test fun postReferencePreservesCaseAndLongId() {
        assertEquals("AbC123xyz", PostReference.fromInput("https://weibo.com/123/AbC123xyz").key)
        assertEquals(ref, PostReference.fromInput(ref.key))
        failure("INPUT") { PostReference.fromInput("https://weibo.com.evil.test/123/AbC123xyz") }
    }
    @Test fun parserRejectsIdentityMismatchWithoutFloatingPointRounding() {
        val status = json("""{"id":9007199254740992,"text":"fixture"}""")
        failure("IDENTITY") { PostParser().parse(status, ref) }
        assertEquals(ref.key, PostParser().parse(json("""{"id":9007199254740993,"text":"fixture"}"""), ref).id)
    }
    @Test fun mixedOrderingStableIdsAndVideoMimeArePreserved() {
        val status = json("""{"idstr":"9007199254740993","mix_media_info":{"items":[
          {"id":"p1","type":"pic","data":{"pic_id":"p1","type":"livephoto","large":{"url":"https://wx1.sinaimg.cn/mw2000/p1.jpg?test=1","width":200,"height":100}}},
          {"id":"v1","type":"video","data":{"object_id":"v1","media_info":{"playback_list":[
            {"meta":{"quality_index":999},"play_info":{"mime":"image/jpeg","url":"https://wx1.sinaimg.cn/large/cover.jpg"}},
            {"meta":{"quality_index":1},"play_info":{"mime":"video/mp4","url":"https://f.video.weibocdn.com/low.mp4"}},
            {"meta":{"quality_index":2},"play_info":{"mime":"video/mp4","url":"https://f.video.weibocdn.com/high.mp4"}}
          ]}}}
        ]},"retweeted_status":{"pics":[{"pid":"wrong"}]}}""")
        val result = PostParser().parse(status, ref)
        assertEquals(listOf("p1", "v1"), result.media.map { it.id })
        assertEquals(MediaKind.LIVE_PHOTO, result.media[0].kind)
        assertEquals("https://wx1.sinaimg.cn/large/p1.jpg?test=1", result.media[0].candidate?.url)
        assertTrue(result.media[1].candidate!!.url.endsWith("high.mp4"))
    }
    @Test fun picturesFollowDeclaredIdsNotInfoMapOrder() {
        val result = PostParser().parse(json("""{"idstr":"9007199254740993","pic_ids":["b","a"],"pic_infos":{"a":{"large":{"url":"https://wx1.sinaimg.cn/large/a.jpg"}},"b":{"large":{"url":"https://wx1.sinaimg.cn/large/b.jpg"}}}}"""), ref)
        assertEquals(listOf("b", "a"), result.media.map { it.id })
        assertFalse(result.needsSupplement)
    }
    @Test fun standaloneVideoIsNotItsCover() {
        val result = PostParser().parse(json("""{"idstr":"9007199254740993","page_info":{"type":"video","object_id":"v1","media_info":{"playback_list":[{"play_info":{"mime":"video/mp4","url":"https://f.video.weibocdn.com/a.mp4"}}]}}}"""), ref)
        assertEquals(1, result.media.size)
        assertEquals(MediaKind.VIDEO, result.media.single().kind)
    }
    @Test fun supplementUsesIdInsteadOfArrayPosition() {
        val original = ProbePost(ref.key, listOf(ProbeMedia("a", MediaKind.IMAGE, null), ProbeMedia("b", MediaKind.IMAGE, null)), needsSupplement = true)
        val a = ResourceCandidate("https://wx1.sinaimg.cn/large/a.jpg", "test")
        val b = ResourceCandidate("https://wx1.sinaimg.cn/large/b.jpg", "test")
        val extra = ProbePost(ref.key, listOf(ProbeMedia("b", MediaKind.IMAGE, b), ProbeMedia("a", MediaKind.IMAGE, a)))
        val merged = PostParser().supplement(original, extra)
        assertEquals(listOf(a,b), merged.media.map { it.candidate })
    }
    private class FakeGateway(val response: () -> JsonObject): PostGateway {
        var visitors = 0; var requests = 0; var mobileRequests = 0
        override fun createVisitor(token: OperationToken): Map<String,String> { visitors++; return mapOf("SUB" to "synthetic") }
        override fun desktop(reference: PostReference, session: Map<String,String>, token: OperationToken): JsonObject { requests++; return response() }
        override fun mobile(reference: PostReference, token: OperationToken): JsonObject { mobileRequests++; error("不应访问") }
    }
    @Test fun sessionRefreshStopsAfterOneRetry() {
        val gateway = FakeGateway { json("""{"ok":-100}""") }
        failure("SESSION") { PostRepository(gateway).fetch(ref, OperationToken()) }
        assertEquals(2, gateway.visitors); assertEquals(2, gateway.requests); assertEquals(0, gateway.mobileRequests)
    }
    @Test fun refreshedSessionCanSucceed() {
        var responses = 0
        val gateway = FakeGateway { if (responses++ == 0) json("""{"ok":-100}""") else json("""{"idstr":"9007199254740993","text":"fixture"}""") }
        assertEquals(ref.key, PostRepository(gateway).fetch(ref, OperationToken()).id)
        assertEquals(2, gateway.requests)
    }
    @Test fun rateLimitAndAccessDoNotBecomeEmptySuccess() {
        val limited = FakeGateway { throw ProbeFailure("RATE_LIMIT", "429") }
        failure("RATE_LIMIT") { PostRepository(limited).fetch(ref, OperationToken()) }
        assertEquals(1, limited.requests); assertEquals(1, limited.visitors)
        val restricted = FakeGateway { json("""{"ok":0}""") }
        failure("ACCESS") { PostRepository(restricted).fetch(ref, OperationToken()) }
        assertEquals(1, restricted.requests)
    }
    @Test fun addressPolicyBlocksUntrustedRedirectAndPrivateDns() {
        for (url in listOf("http://wx1.sinaimg.cn/a", "https://wx1.sinaimg.cn.evil.test/a", "https://127.0.0.1/a", "https://user:password@wx1.sinaimg.cn/a"))
            failure("ADDRESS") { AddressPolicy.validate(url.toHttpUrl(), RequestPurpose.MEDIA) }
        for (ip in listOf("127.0.0.1", "192.168.1.1", "100.64.0.1", "::1", "fd00::1"))
            assertFalse(ip, AddressPolicy.publicAddress(InetAddress.getByName(ip)))
        assertTrue(AddressPolicy.publicAddress(InetAddress.getByName("8.8.8.8")))
    }
    @Test fun truncatedBodyIsDeletedAndHtmlIsNotMedia() {
        val partial = File.createTempFile("probe", ".partial")
        try {
            failure("INTEGRITY") { FileVerification.copy(ByteArrayInputStream(byteArrayOf(1,2)), partial, 4L, OperationToken()) }
            assertFalse(partial.exists())
            partial.writeText("<html>challenge</html>", Charsets.UTF_8)
            failure("FORMAT") { FileVerification.sniff(partial) }
        } finally { partial.delete() }
    }
    @Test fun copyHashAndCancelAreDeterministic() {
        val file = File.createTempFile("probe", ".partial")
        try {
            val body = "abc".toByteArray()
            val copied = FileVerification.copy(ByteArrayInputStream(body), file, 3, OperationToken())
            assertEquals(3L, copied.first)
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", copied.second)
            val token = OperationToken().apply { cancel() }
            try { FileVerification.copy(ByteArrayInputStream(body), file, 3, token); fail("应取消") }
            catch (_: java.util.concurrent.CancellationException) { assertFalse(file.exists()) }
        } finally { file.delete() }
    }
    @Test fun quickTimeContainerIsNotReportedAsMp4() {
        val file = File.createTempFile("probe", ".partial")
        try {
            val header = ByteArray(32)
            "ftypqt  ".toByteArray(Charsets.US_ASCII).copyInto(header, 4)
            file.writeBytes(header)
            assertEquals("video/quicktime", FileVerification.sniff(file))
        } finally { file.delete() }
    }
    @Test fun visitorJsonpExtractsDataWithoutExecutingScript() {
        assertEquals(json("""{"ok":1}"""), JsonPayload.parse("visitor_gray_callback({\"ok\":1});"))
        failure("RESPONSE") { JsonPayload.parse("<html>visitor_gray_callback({})</html>") }
    }
}
