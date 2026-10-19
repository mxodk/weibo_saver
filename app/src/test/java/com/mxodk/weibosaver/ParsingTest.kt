package com.mxodk.weibosaver

import com.mxodk.weibosaver.core.*
import com.mxodk.weibosaver.model.*
import com.mxodk.weibosaver.weibo.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

// 全部是合成夹具；真实输入、正文和计数仍需用户在手机上对照。
class ParsingTest {
    @Test fun declaredVideoDurationIsMetadataAndDoesNotInventMissingDuration() {
        val withDuration=PostParser().parse(JsonPayload.parse("""{"idstr":"9007199254740993","text_raw":"x","page_info":{"type":"video","media_info":{"media_id":"v","duration":65.5}}}"""),PostReference("9007199254740993"))
        assertEquals(65L,withDuration.media.single().durationSeconds)
        val without=PostParser().parse(JsonPayload.parse("""{"idstr":"9007199254740993","text_raw":"x","page_info":{"type":"video","media_info":{"media_id":"v"}}}"""),PostReference("9007199254740993"))
        assertNull(without.media.single().durationSeconds)
    }
    private val ref=PostReference("9007199254740993")
    private fun fixture(name: String)=JsonPayload.parse(requireNotNull(javaClass.getResource("/p3/$name.json")).readText(Charsets.UTF_8))
    private fun json(value: String)=JsonPayload.parse(value)
    private fun failure(code: String, action: ()->Unit) {
        try { action(); fail("应拒绝 $code") } catch(error: ProbeFailure) { assertEquals(code,error.category) }
    }
    @Test fun pastedTextContainsOnePostAndDuplicateTrackingLinksAreDeduplicated() {
        val input="说明文字 https://weibo.com/123/AbC123xyz?from=a，详情 https://weibo.com/123/AbC123xyz?from=b。"
        assertEquals(InputTarget.Direct(PostReference("AbC123xyz")),LinkResolver.identify(input))
    }
    @Test fun multiplePostTargetsAreRejectedBeforeShortRequest() {
        var requests=0
        val resolver=LinkResolver { _,_-> requests++; "https://weibo.com/123/AbC123xyz" }
        failure("MULTIPLE_LINKS") { resolver.resolve("https://t.cn/AbC123xyz https://m.weibo.cn/detail/9007199254740993",OperationToken()) }
        assertEquals(0,requests)
    }
    @Test fun emptyInvalidAndOversizedInputDoNotResolve() {
        for(input in listOf("", "普通无链接文字", "https://weibo.com.evil.test/123/AbC123xyz", "https://weibo.com/tv/show/1034:123", "a".repeat(16385)))
            failure("INPUT") { LinkResolver.identify(input) }
    }
    @Test fun httpInputIsUpgradedWithoutMakingAnHttpRequest() {
        val requested=mutableListOf<String>()
        val resolver=LinkResolver { url,_-> requested+=url; "http://weibo.com/123/AbC123xyz" }
        assertEquals(PostReference("AbC123xyz"),resolver.resolve("http://t.cn/AbC123xyz?sig=synthetic",OperationToken()))
        assertEquals(listOf("https://t.cn/AbC123xyz?sig=synthetic"),requested)
    }
    @Test fun shortRedirectRejectsPrivateAndUntrustedDestinations() {
        for(location in listOf("https://127.0.0.1/test", "https://weibo.com.evil.test/123/AbC123xyz", "https://user:pass@weibo.com/123/AbC123xyz")) {
            var count=0
            val resolver=LinkResolver { _,_-> count++; location }
            failure("INPUT") { resolver.resolve("https://t.cn/AbC123xyz",OperationToken()) }
            assertEquals(1,count)
        }
    }
    @Test fun shortRedirectLoopAndHopCountAreBounded() {
        var loop=0
        failure("SHORT_LINK") { LinkResolver { _,_-> loop++; "/AbC123xyz" }.resolve("https://t.cn/AbC123xyz",OperationToken()) }
        assertEquals(1,loop)
        var hops=0
        failure("SHORT_LINK") { LinkResolver { _,_-> "https://t.cn/Next${++hops}" }.resolve("https://t.cn/Start123",OperationToken()) }
        assertEquals(5,hops)
    }
    @Test fun shortRateLimitStopsWithoutRetryAndCancelStopsNextHop() {
        var calls=0
        failure("RATE_LIMIT") { LinkResolver { _,_-> calls++; throw ProbeFailure("RATE_LIMIT","synthetic") }.resolve("https://t.cn/AbC123xyz",OperationToken()) }
        assertEquals(1,calls)
        val token=OperationToken()
        try { LinkResolver { _,_-> token.cancel(); "https://weibo.com/123/AbC123xyz" }.resolve("https://t.cn/AbC123xyz",token); fail("应取消") }
        catch (_: CancellationException) { }
    }
    @Test fun mobileAndLegacyCommentLinksKeepCaseAndLongIds() {
        assertEquals(InputTarget.Direct(ref),LinkResolver.identify("https://m.weibo.cn/detail/${ref.key}"))
        assertEquals(InputTarget.Direct(PostReference("AbC123xyz")),LinkResolver.identify("https://weibo.cn/comment/AbC123xyz"))
    }
    @Test fun htmlTextPreservesParagraphsEntitiesEmojiWithoutExecutingScripts() {
        val text=BodyText.fromHtml("<p>A&amp;B<br>换行<img alt='[笑]' src='https://bad.test'></p><script>evil()</script><p>&lt;tag&gt;</p>")
        assertEquals("A&B\n换行[笑]\n<tag>",text)
        assertFalse(text.contains("evil"))
    }
    @Test fun plainTextAndZeroMediaAreKnownInsteadOfError() {
        val post=PostParser().parse(fixture("text-only"),ref)
        assertEquals("第一段 & 内容\n第二行[笑]\n末段",post.text)
        assertEquals(BodyStatus.COMPLETE,post.bodyStatus); assertEquals(MediaListStatus.COMPLETE,post.mediaStatus)
        assertTrue(post.media.isEmpty()); assertFalse(post.needsSupplement)
        assertEquals("合成用户",post.author?.name)
    }
    @Test fun rawTextIsNotInterpretedAsHtml() {
        val post=PostParser().parse(json("""{"idstr":"9007199254740993","text_raw":"<b>用户原文</b> &amp;","pic_num":0}"""),ref)
        assertEquals("<b>用户原文</b> &amp;",post.text)
    }
    @Test fun inlineLongBodyReplacesSummaryButMissingLongBodyStaysTruncated() {
        val full=PostParser().parse(fixture("long-inline"),ref)
        assertEquals(BodyStatus.COMPLETE,full.bodyStatus); assertTrue(full.text.endsWith("结尾核对标记 <不会执行>")); assertFalse(full.text.contains("摘要"))
        val partial=PostParser().parse(fixture("long-truncated"),ref)
        assertEquals(BodyStatus.TRUNCATED,partial.bodyStatus); assertTrue(partial.issues.any{it.scope==IssueScope.BODY})
    }
    @Test fun emptyConvertedLongFieldCannotProveFullBody() {
        val post=PostParser().parse(json("""{"idstr":"9007199254740993","isLongText":true,"text_raw":"保留摘要","longText":{"longTextContent":"<script>ignored()</script>"}}"""),ref)
        assertEquals("保留摘要",post.text); assertEquals(BodyStatus.TRUNCATED,post.bodyStatus)
    }
    @Test fun repostWithoutOwnMediaNeverBorrowsOriginalBodyOrPictures() {
        val post=PostParser().parse(fixture("repost-no-media"),ref)
        assertTrue(post.isRepost); assertEquals("我自己的转发评论",post.text); assertTrue(post.media.isEmpty())
        assertEquals(MediaListStatus.COMPLETE,post.mediaStatus)
    }
    @Test fun repostWithOwnMediaReturnsOnlyItsOwnStableIds() {
        val post=PostParser().parse(fixture("repost-own-media"),ref)
        assertEquals(listOf("own-photo"),post.media.map{it.id}); assertEquals("自己的评论",post.text)
    }
    @Test fun unknownMixedTypesAndMissingIdsCannotBecomeCompleteEmptyPosts() {
        val unknown=PostParser().parse(fixture("mixed-unknown"),ref)
        assertEquals(MediaListStatus.PARTIAL,unknown.mediaStatus); assertTrue(unknown.needsSupplement)
        val noEvidence=PostParser().parse(json("""{"idstr":"9007199254740993"}"""),ref)
        assertEquals(MediaListStatus.UNKNOWN,noEvidence.mediaStatus); assertEquals(BodyStatus.MISSING,noEvidence.bodyStatus)
    }
    @Test fun picturePlaceholdersPermitIdBasedRepairWhenInfosAreMissing() {
        val parser=PostParser()
        val missing=parser.parse(json("""{"idstr":"9007199254740993","text_raw":"x","pic_ids":["b","a"]}"""),ref)
        assertEquals(listOf("b","a"),missing.media.map{it.id}); assertTrue(missing.media.all{it.candidate==null})
        val extra=parser.parse(json("""{"idstr":"9007199254740993","text_raw":"x","pics":[{"pid":"a","large":{"url":"https://wx1.sinaimg.cn/large/a.jpg"}},{"pid":"b","large":{"url":"https://wx1.sinaimg.cn/large/b.jpg"}}]}"""),ref,"mobile")
        val merged=parser.supplement(missing,extra)
        assertEquals(listOf("b","a"),merged.media.map{it.id}); assertTrue(merged.media.all{it.candidate!=null}); assertFalse(merged.needsSupplement)
    }
    @Test fun supplementCanAddVerifiedOwnMediaButCannotInventCrossSourceOrder() {
        val parser=PostParser(); val primary=parser.parse(json("""{"idstr":"9007199254740993","text_raw":"x","pic_num":1}"""),ref)
        val extra=parser.parse(json("""{"idstr":"9007199254740993","text_raw":"x","pics":[{"pid":"a","large":{"url":"https://wx1.sinaimg.cn/large/a.jpg"}}]}"""),ref)
        val merged=parser.supplement(primary,extra)
        assertEquals(1,merged.media.size); assertEquals(MediaOrder.UNPROVEN,merged.order); assertEquals(MediaListStatus.COMPLETE,merged.mediaStatus)
    }
    @Test fun identicalUnflaggedSupplementCannotProveLongBodyComplete() {
        val parser=PostParser(); val primary=parser.parse(fixture("long-truncated"),ref)
        val extra=parser.parse(json("""{"idstr":"9007199254740993","text_raw":"未取得完整正文的摘要"}"""),ref)
        assertEquals(BodyStatus.TRUNCATED,parser.supplement(primary,extra).bodyStatus)
    }
    @Test fun livePhotoMotionIsAssociatedWithSameItemAndNotCountedAsVideo() {
        val post=PostParser().parse(json("""{"idstr":"9007199254740993","text_raw":"x","pics":[{"pid":"live","type":"livephoto","large":{"url":"https://wx1.sinaimg.cn/large/live.jpg"},"videoSrc":"https://video.weibo.com/media/play?livephoto=synthetic&sig=keep"}]}"""),ref)
        assertEquals(1,post.media.size); assertEquals(MediaKind.LIVE_PHOTO,post.media.single().kind)
        assertTrue(post.media.single().liveMotion!!.url.endsWith("&sig=keep")); assertEquals(0,post.media.count{it.kind==MediaKind.VIDEO})
    }
    @Test fun conflictingMediaTypeDoesNotReplaceByArrayPosition() {
        val parser=PostParser(); val a=ProbePost(ref.key,listOf(ProbeMedia("same",MediaKind.VIDEO,null)))
        val b=ProbePost(ref.key,listOf(ProbeMedia("same",MediaKind.IMAGE,ResourceCandidate("https://wx1.sinaimg.cn/large/x.jpg","fixture"))))
        val merged=parser.supplement(a,b)
        assertNull(merged.media.single().candidate); assertTrue(merged.issues.any{it.code=="TYPE_CONFLICT"})
    }
    @Test fun untrustedImageCandidateIsNotDownloadableAndDoesNotFetchPreview() {
        val post=PostParser().parse(json("""{"idstr":"9007199254740993","text_raw":"x","pics":[{"pid":"a","large":{"url":"https://other.test/a.jpg"}}]}"""),ref)
        assertNull(post.media.single().candidate); assertTrue(post.issues.any{it.code=="ADDRESS_UNSUPPORTED"})
    }
    @Test fun mediaIdentityMismatchKeepsPlaceholderInsteadOfWrongPhoto() {
        val post=PostParser().parse(json("""{"idstr":"9007199254740993","text_raw":"x","pic_ids":["expected"],"pic_infos":{"expected":{"pic_id":"wrong","large":{"url":"https://wx1.sinaimg.cn/large/wrong.jpg"}}}}"""),ref)
        assertEquals("expected",post.media.single().id); assertNull(post.media.single().candidate)
    }
    @Test fun supplementalRateLimitRetainsPrimaryContentWithoutFurtherRequests() {
        var mobiles=0
        val gateway=object:PostGateway {
            override fun createVisitor(token: OperationToken)=mapOf("SUB" to "synthetic")
            override fun desktop(reference: PostReference,session:Map<String,String>,token:OperationToken)=fixture("long-truncated")
            override fun mobile(reference: PostReference,token:OperationToken):kotlinx.serialization.json.JsonObject { mobiles++; throw ProbeFailure("RATE_LIMIT","synthetic") }
        }
        val post=PostRepository(gateway).fetch(ref,OperationToken())
        assertEquals(1,mobiles); assertEquals(BodyStatus.TRUNCATED,post.bodyStatus); assertTrue(post.text.isNotBlank())
        assertTrue(post.issues.any{it.code=="SUPPLEMENT_RATE_LIMIT"})
    }
}
