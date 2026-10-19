package com.mxodk.weibosaver

import com.mxodk.weibosaver.entry.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

// 事件交接验证不替代 Android 实际任务栈；旧点击不能打开或取消另一批次。
class NotificationEntryTest {
    private val batch=UUID.randomUUID().toString()
    @Test fun coldNotificationProducesOneRequestAndDoesNotResumeDownloads() {
        val entry=NotificationEntryState().initial(NotificationEntry.OPEN,batch)
        assertTrue(requireNotNull(entry.request).matches(batch,true))
        assertFalse(requireNotNull(entry.request).matches(batch,false))
        assertEquals(entry,entry.initial(NotificationEntry.OPEN,batch))
    }
    @Test fun normalLauncherDoesNotOpenPanelButLaterNotificationDoes() {
        val entry=NotificationEntryState().initial("android.intent.action.MAIN",null)
        assertNull(entry.request)
        assertNotNull(entry.receive(NotificationEntry.OPEN,batch).request)
    }
    @Test fun consumedNotificationDoesNotReopenOnConfigurationChange() {
        val first=NotificationEntryState().initial(NotificationEntry.OPEN,batch)
        val consumed=first.consume(requireNotNull(first.request).sequence)
        assertNull(consumed.initial(NotificationEntry.OPEN,batch).request)
    }
    @Test fun repeatedTapOnSameBatchReopensClosedPanel() {
        val first=NotificationEntryState().initial(NotificationEntry.OPEN,batch)
        val consumed=first.consume(requireNotNull(first.request).sequence)
        val next=consumed.receive(NotificationEntry.OPEN,batch)
        assertTrue(requireNotNull(next.request).sequence>requireNotNull(first.request).sequence)
    }
    @Test fun oldConsumptionCannotEatANewerTap() {
        val first=NotificationEntryState().receive(NotificationEntry.OPEN,batch)
        val next=first.receive(NotificationEntry.OPEN,batch)
        assertEquals(next,next.consume(requireNotNull(first.request).sequence))
    }
    @Test fun staleBatchAndMalformedEntryNeverMatchCurrentBatch() {
        val entry=NotificationEntryState().receive(NotificationEntry.OPEN,batch)
        assertFalse(requireNotNull(entry.request).matches(UUID.randomUUID().toString(),true))
        assertNull(NotificationEntryState().receive(NotificationEntry.OPEN,"not-a-batch").request)
        assertNull(NotificationEntryState().receive("other",batch).request)
    }
}
