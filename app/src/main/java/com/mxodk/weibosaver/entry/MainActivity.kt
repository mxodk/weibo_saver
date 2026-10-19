package com.mxodk.weibosaver.entry

import android.os.Bundle
import android.content.Intent
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mxodk.weibosaver.ui.ReaderApp

// Activity 仅负责宿主，页面状态由 ViewModel 保持。
class MainActivity : ComponentActivity() {
    private val readerViewModel: ProbeViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 配置变化复用 ViewModel，已消费的通知不反复打开面板。
        readerViewModel.initialNotification(intent.action,intent.getStringExtra(NotificationEntry.BATCH))
        setContent { ReaderApp(vm=readerViewModel) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 已有页面接收新通知，保留原输入、解析、选择和滚动状态。
        readerViewModel.notificationOpened(intent.action,intent.getStringExtra(NotificationEntry.BATCH))
    }
}
