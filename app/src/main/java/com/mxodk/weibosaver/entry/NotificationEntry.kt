package com.mxodk.weibosaver.entry

// 只传批次身份和打开面板事件；不从通知重新解析、创建或恢复下载。
object NotificationEntry {
    const val OPEN="com.mxodk.weibosaver.OPEN_DOWNLOADS"
    const val BATCH="notification_batch"
}
data class DownloadPanelRequest(val sequence: Long,val batchId: String)
data class NotificationEntryState(val initialHandled: Boolean=false,val sequence: Long=0,val request: DownloadPanelRequest?=null) {
    fun initial(action: String?,batchId: String?): NotificationEntryState =
        if(initialHandled) this else copy(initialHandled=true).receive(action,batchId)
    fun receive(action: String?,batchId: String?): NotificationEntryState {
        if(action!=NotificationEntry.OPEN || batchId==null || !Regex("[a-f0-9-]{36}").matches(batchId)) return this
        return copy(sequence=sequence+1,request=DownloadPanelRequest(sequence+1,batchId))
    }
    fun consume(expected: Long): NotificationEntryState = if(request?.sequence==expected) copy(request=null) else this
}
fun DownloadPanelRequest.matches(currentBatch: String,hasSnapshot: Boolean)=hasSnapshot && batchId==currentBatch
