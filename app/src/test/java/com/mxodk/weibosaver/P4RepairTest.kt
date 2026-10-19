package com.mxodk.weibosaver

import com.mxodk.weibosaver.ui.*
import com.mxodk.weibosaver.model.*
import com.mxodk.weibosaver.weibo.*
import org.junit.Assert.*
import org.junit.Test

class P4RepairTest {
    private val resource=ResourceCandidate("https://wx1.sinaimg.cn/large/synthetic.jpg","fixture")
    private fun snapshot()=SaveSnapshot(1,"post",SaveChoice.ALL,listOf(ProbeMedia("a",MediaKind.IMAGE,resource),ProbeMedia("v",MediaKind.VIDEO,resource)))
    @Test fun coverAcceptsDesktopStringAndMobileObject() {
        for(value in listOf("\"https://wx2.sinaimg.cn/large/cover.jpg\"","{\"url\":\"https://wx2.sinaimg.cn/large/cover.jpg\"}")) {
            val json="""{"idstr":"1","text_raw":"x","page_info":{"type":"video","media_info":{"media_id":"v"},"page_pic":$value}}"""
            val media=PostParser().parse(JsonPayload.parse(json),PostReference("1")).media.single()
            assertEquals("https://wx2.sinaimg.cn/large/cover.jpg",media.preview?.url)
            assertNull(media.candidate) // 封面绝不成为保存视频的候选。
        }
    }
    @Test fun coverFallsBackWithinSameVideoObjectAndRejectsPrivateAddress() {
        val json="""{"idstr":"1","page_info":{"type":"video","media_info":{"media_id":"v","big_pic_info":{"pic_middle":{"url":"https://wx1.sinaimg.cn/large/right.jpg"}}},"page_pic":"https://127.0.0.1/wrong.jpg"},"retweeted_status":{"page_info":{"page_pic":"https://wx1.sinaimg.cn/large/other-post.jpg"}}}"""
        val media=PostParser().parse(JsonPayload.parse(json),PostReference("1")).media.single()
        assertEquals("https://wx1.sinaimg.cn/large/right.jpg",media.preview?.url)
    }
    @Test fun dragVisitsEachItemOnceAndReversesStartingSelection() {
        val session=DragSelectionSession()
        session.begin(true);assertFalse(session.checked)
        assertTrue(session.visit("a"));assertTrue(session.visit("b"));assertFalse(session.visit("a"))
        session.begin(false);assertTrue(session.checked);assertTrue(session.visit("a"))
    }
    @Test fun dragSetIsIdempotentAndIgnoresUnavailableItems() {
        val media=snapshot().media+ProbeMedia("missing",MediaKind.IMAGE,null)
        val state=ReaderState().receive(ParseState(requestId=1,post=ProbePost("post",media)))
        val cleared=state.setSelected("a",false)
        assertEquals(cleared,cleared.setSelected("a",false))
        assertEquals(cleared,cleared.setSelected("missing",true))
        assertTrue(cleared.setSelected("a",true).selected.contains("a"))
    }
    @Test fun failedItemsCountAsProcessedButCancellationDoesNotReachHundredPercent() {
        val begun=DemoReducer.begin(DemoBatch(),snapshot())
        val firstDone=DemoReducer.step(begun)
        assertEquals(50,firstDone.percent)
        val stopped=firstDone.stop();assertEquals(50,stopped.percent);assertEquals(1,stopped.cancelled)
        var finished=begun;repeat(4) {finished=DemoReducer.step(finished)}
        assertEquals(100,finished.percent);assertEquals(1,finished.done);assertEquals(1,finished.failed)
        assertEquals(0,DemoReducer.retry(finished).percent)
    }
    @Test fun directoryIsValidatedAndSnapshotKeepsItsOriginalTarget() {
        assertEquals("我的微博",AlbumDirectory.validate(" 我的微博 "))
        for(input in listOf("",".","..","../other","C:\\temp","a/b","bad\nname","a".repeat(61))) {
            try {AlbumDirectory.validate(input);fail("非法相册名应拒绝")}catch(_: ProbeFailure) {}
        }
        val original=snapshot().copy(albumDirectory="旧相册")
        val active=DemoReducer.begin(DemoBatch(),original)
        val changed=original.copy(albumDirectory="新相册")
        assertEquals("旧相册",active.snapshot?.albumDirectory);assertEquals("新相册",changed.albumDirectory)
        var finished=active;repeat(4) {finished=DemoReducer.step(finished)}
        assertEquals("旧相册",DemoReducer.retry(finished).snapshot?.albumDirectory)
    }
}
