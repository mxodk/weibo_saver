package com.mxodk.weibosaver.download

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import com.mxodk.weibosaver.entry.NotificationEntry
import android.os.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

// 仅由可见页面主动开始；不注册开机事件，不自动恢复旧下载。
class DownloadService: Service() {
    companion object {const val CHANNEL="p5-download";const val NOTIFICATION=2002;const val STOP="save.stop"}
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var coordinator: DownloadCoordinator
    private var ownedBatch: String?=null
    private var wakeLock: PowerManager.WakeLock?=null
    private val handler=Handler(Looper.getMainLooper())
    private val budget=Runnable {coordinator.requestStop("批次运行超过安全时限",ownedBatch);stopSelf()}
    override fun onCreate() {
        super.onCreate();coordinator=DownloadCoordinator.get(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"微博媒体保存",NotificationManager.IMPORTANCE_LOW))
        scope.launch {
            var last=0L
            coordinator.state.collect {state->
                if(state.batch==ownedBatch && state.active && SystemClock.elapsedRealtime()-last>=500) {
                    last=SystemClock.elapsedRealtime()
                    runCatching {getSystemService(NotificationManager::class.java).notify(NOTIFICATION,notification())}
                }
            }
        }
    }
    private fun notification(): Notification {
        val state=coordinator.state.value
        val openIntent=Intent(this,com.mxodk.weibosaver.entry.MainActivity::class.java)
            .setAction(NotificationEntry.OPEN).putExtra(NotificationEntry.BATCH,state.batch)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open=PendingIntent.getActivity(this,0,openIntent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // 每条停止操作绑定批次，旧通知不能停止新批次。
        val stop=PendingIntent.getService(this,1,Intent(this,DownloadService::class.java).setAction(STOP)
            .setData(Uri.parse("weibosaver://batch/${state.batch}")).putExtra("batch",state.batch),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("微博下载器 · 处理 ${state.percent}%")
            .setContentText("成功 ${state.success} · 失败 ${state.failed} · 取消 ${state.cancelled}")
            .setStyle(Notification.BigTextStyle().bigText(state.processingText))
            .setProgress(state.items.size.coerceAtLeast(1),state.processed,false)
            .setOngoing(state.active).setOnlyAlertOnce(true).setContentIntent(open)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {if(state.active) addAction(Notification.Action.Builder(null,"取消未完成任务",stop).build())}.build()
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        val requested=intent?.getStringExtra("batch")
        if(intent?.action==STOP) {
            if(requested!=null) coordinator.requestStop("通知取消按钮",requested)
            if(!coordinator.state.value.active || ownedBatch==null) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val state=coordinator.state.value
        if(requested==null || requested!=state.batch || !state.active) {stopSelfResult(startId);return START_NOT_STICKY}
        ownedBatch=requested
        try {
            startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            // 有界 CPU 锁只覆盖用户主动发起的保存，结束和销毁均释放。
            if(wakeLock?.isHeld!=true) wakeLock=getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"weibosaver:save").apply {acquire(60*60*1000L)}
            handler.removeCallbacks(budget);handler.postDelayed(budget,60*60*1000L)
            coordinator.runQueued(requested) {
                if(coordinator.state.value.batch==requested) {
                    runCatching {getSystemService(NotificationManager::class.java).notify(NOTIFICATION,notification())}
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                } else stopSelfResult(startId)
            }
        } catch(_: Exception) {
            coordinator.requestStop("前台服务启动失败",requested)
            coordinator.abortStart("无法启动保存，请返回应用后重试",requested)
            stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) {coordinator.requestStop("最近任务划掉",ownedBatch);stopSelf();super.onTaskRemoved(rootIntent)}
    override fun onTimeout(startId: Int,fgsType: Int) {coordinator.requestStop("系统前台服务超时",ownedBatch);stopSelf()}
    override fun onDestroy() {
        ownedBatch?.let {coordinator.requestStop("服务销毁",it)};handler.removeCallbacks(budget)
        wakeLock?.let {if(it.isHeld) it.release()};wakeLock=null
        scope.cancel();super.onDestroy()
    }
    override fun onBind(intent: Intent?)=null
}
