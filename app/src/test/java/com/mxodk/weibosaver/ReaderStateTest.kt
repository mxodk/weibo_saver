package com.mxodk.weibosaver

import com.mxodk.weibosaver.model.*
import com.mxodk.weibosaver.ui.*
import com.mxodk.weibosaver.weibo.ParseState
import org.junit.Assert.*
import org.junit.Test

// 验证集合与身份语义，不以假 UI 或面板演示替代手机上的媒体预览。
class ReaderStateTest {
    private fun image(id: String, available: Boolean = true) = ProbeMedia(id,MediaKind.IMAGE,
        if(available) ResourceCandidate("https://wx1.sinaimg.cn/large/$id.jpg","fixture") else null)
    private fun result(id: Long = 1, post: ProbePost = ProbePost("post-a",listOf(image("a"),image("b"),image("missing",false)))) = ParseState(post=post,requestId=id)
    @Test fun defaultsSelectOnlyUsableCandidates() {
        val state=ReaderState().receive(result())
        assertEquals(setOf("a","b"),state.selected)
        assertEquals(state,state.toggle("missing"));assertEquals(state,state.toggle("not-present"))
    }
    @Test fun saveAllIgnoresSelectionButSelectedUsesCurrentChoiceAndSourceOrder() {
        val state=ReaderState().receive(result()).toggle("a")
        assertEquals(listOf("a","b"),state.snapshot(SaveChoice.ALL).media.map { it.id })
        assertEquals(listOf("b"),state.snapshot(SaveChoice.SELECTED).media.map { it.id })
    }
    @Test fun zeroSelectionCannotProduceSelectedBatchButCanStillSaveAll() {
        val state=ReaderState().receive(result()).copy(selected=emptySet())
        try {state.snapshot(SaveChoice.SELECTED);fail("零勾选应拒绝")}catch(e:ProbeFailure){assertEquals("INPUT",e.category)}
        assertEquals(2,state.snapshot(SaveChoice.ALL).media.size)
    }
    @Test fun previewAndSameRequestUpdatesKeepChoices() {
        val initial=ReaderState().receive(result()).toggle("a").preview("a")
        assertEquals(setOf("b"),initial.selected)
        val updated=initial.receive(result().copy(input="new editable input"))
        assertEquals(setOf("b"),updated.selected);assertEquals("a",updated.previewId)
        assertEquals(setOf("b"),updated.preview(null).selected)
    }
    @Test fun newRequestClearsSelectionAndPreviewEvenWhenPostIdIsSame() {
        val initial=ReaderState().receive(result()).toggle("a").preview("a")
        val loading=initial.receive(ParseState(requestId=2,busy=true))
        assertTrue(loading.selected.isEmpty());assertNull(loading.previewId)
        assertEquals(setOf("a","b"),loading.receive(result(id=2)).selected)
    }
    @Test fun failedNewRequestNeverLeavesAnOldSaveablePost() {
        val next=ReaderState().receive(result()).receive(ParseState(requestId=2,errorCode="INPUT"))
        assertNull(next.parse.post)
        try {next.snapshot(SaveChoice.ALL);fail("失败后不能保存旧帖")}catch(_:ProbeFailure){}
    }
    @Test fun snapshotCannotChangeWhenPageParsesAnotherPostOrSelectionChanges() {
        val state=ReaderState().receive(result())
        val snapshot=state.snapshot(SaveChoice.SELECTED)
        state.toggle("a").receive(result(2,ProbePost("post-b",listOf(image("new")))))
        assertEquals("post-a",snapshot.postId);assertEquals(listOf("a","b"),snapshot.media.map { it.id })
    }
    @Test fun pureTextAndMissingCandidatesCannotProduceEmptyBatches() {
        for(post in listOf(ProbePost("text",emptyList()),ProbePost("missing",listOf(image("bad",false))))) {
            val state=ReaderState().receive(result(post=post))
            assertTrue(state.selected.isEmpty())
            try{state.snapshot(SaveChoice.ALL);fail("没有可保存项")}catch(_:ProbeFailure){}
        }
    }
    @Test fun livePhotoIsOneStaticChoiceAndNotAnAdditionalVideo() {
        val live=image("live").copy(kind=MediaKind.LIVE_PHOTO,liveMotion=ResourceCandidate("https://video.weibo.com/media/play?livephoto=synthetic","fixture"))
        val state=ReaderState().receive(result(post=ProbePost("live-post",listOf(live))))
        assertEquals(1,state.selected.size);assertEquals(listOf(live),state.snapshot(SaveChoice.ALL).media)
    }
    @Test fun activeDemoRejectsSecondBatchUntilStoppedOrFinished() {
        val snapshot=ReaderState().receive(result()).snapshot(SaveChoice.ALL)
        val active=DemoReducer.begin(DemoBatch(),snapshot)
        try{DemoReducer.begin(active,snapshot);fail("不能并行批次")}catch(e:ProbeFailure){assertEquals("BUSY",e.category)}
        val stopped=active.stop();assertFalse(stopped.active);assertEquals(2,stopped.cancelled)
        assertTrue(DemoReducer.begin(stopped,snapshot).active)
    }
    @Test fun stopKeepsCompletedItemsAndPreventsFurtherDemoProgress() {
        val snapshot=ReaderState().receive(result()).snapshot(SaveChoice.ALL)
        val initial=DemoReducer.begin(DemoBatch(),snapshot)
        val firstDone=DemoReducer.step(initial)
        val stopped=firstDone.stop()
        assertEquals(1,stopped.done);assertEquals(1,stopped.cancelled)
        assertEquals(stopped,DemoReducer.step(stopped))
    }
    @Test fun demoEndCountsOnlyDemonstrationAndKeepsSourceSnapshot() {
        val snapshot=ReaderState().receive(result()).snapshot(SaveChoice.ALL)
        var batch=DemoReducer.begin(DemoBatch(),snapshot)
        repeat(4) {batch=DemoReducer.step(batch)}
        assertFalse(batch.active);assertEquals(2,batch.done);assertEquals(snapshot,batch.snapshot)
    }
    @Test fun demoRetryUsesFailedOriginalSnapshotEvenAfterAnotherPostWasParsed() {
        val original=ReaderState().receive(result(post=ProbePost("first",listOf(image("pic"),image("video").copy(kind=MediaKind.VIDEO)))))
        var batch=DemoReducer.begin(DemoBatch(),original.snapshot(SaveChoice.ALL))
        repeat(4) {batch=DemoReducer.step(batch)}
        assertEquals(1,batch.done);assertEquals(1,batch.failed)
        original.receive(result(2,ProbePost("second",listOf(image("new")))))
        val retry=DemoReducer.retry(batch)
        assertEquals("first",retry.snapshot!!.postId);assertEquals(listOf("video"),retry.items.map { it.id })
        assertEquals(1,DemoReducer.step(DemoReducer.step(retry)).done)
    }
}
