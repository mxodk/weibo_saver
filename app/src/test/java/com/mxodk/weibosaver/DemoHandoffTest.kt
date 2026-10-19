package com.mxodk.weibosaver

import com.mxodk.weibosaver.ui.*
import com.mxodk.weibosaver.model.*
import org.junit.Assert.*
import org.junit.Test

// 确认交接始终有真实当前项，既不插入假提示，也不改变失败和取消语义。
class DemoHandoffTest {
    private fun start(): DemoBatch {
        val candidate=ResourceCandidate("https://wx1.sinaimg.cn/synthetic.jpg","fixture")
        val media=listOf(ProbeMedia("a",MediaKind.IMAGE,candidate),ProbeMedia("v",MediaKind.VIDEO,candidate),ProbeMedia("b",MediaKind.IMAGE,candidate))
        return DemoReducer.begin(DemoBatch(),SaveSnapshot(1,"post",SaveChoice.ALL,media))
    }
    @Test fun everyActiveStateHasExactlyOneCurrentItemAndContinuousHint() {
        var batch=start()
        for(index in 0..2) {
            assertTrue(batch.active)
            assertEquals(1,batch.items.count {it.status==DemoStatus.RUNNING})
            assertTrue(batch.processingText.contains("${index+1}/3"))
            assertTrue(batch.processingText.contains("剩余 ${2-index} 项"))
            assertEquals(index,batch.processed)
            batch=DemoReducer.step(batch)
        }
        assertFalse(batch.active);assertEquals(2,batch.done);assertEquals(1,batch.failed)
        assertEquals("演示已结束",batch.processingText)
        assertEquals(batch,DemoReducer.step(batch))
    }
    @Test fun cancellationAndRetryPreserveCountsWithoutAnEmptyActiveHint() {
        val afterFirst=DemoReducer.step(start())
        val stopped=afterFirst.stop()
        assertEquals(1,stopped.done);assertEquals(2,stopped.cancelled)
        assertEquals("演示已取消",stopped.processingText);assertEquals(33,stopped.percent)
        var finished=start();repeat(3) {finished=DemoReducer.step(finished)}
        val retry=DemoReducer.retry(finished)
        assertTrue(retry.processingText.contains("1/1"));assertEquals(listOf("v"),retry.items.map {it.id})
        assertEquals(1,DemoReducer.step(retry).done)
    }
}
