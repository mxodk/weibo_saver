package com.mxodk.weibosaver.entry

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.mxodk.weibosaver.download.DownloadService
import com.mxodk.weibosaver.download.RetryPolicy
import android.os.Build
import android.net.Uri
import com.mxodk.weibosaver.storage.FolderAccess
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mxodk.weibosaver.BuildConfig
import com.mxodk.weibosaver.core.SafeHttpClient
import com.mxodk.weibosaver.download.DownloadCoordinator
import com.mxodk.weibosaver.model.ProbeFailure
import com.mxodk.weibosaver.ui.*
import com.mxodk.weibosaver.weibo.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// 页面解析独立于保存协调器；新输入只取消旧解析，服务批次独立存续。
class ProbeViewModel(application: Application): AndroidViewModel(application) {
    private val http = SafeHttpClient()
    private val repository = PostRepository(WeiboGateway(http))
    private val resolver = LinkResolver(ShortLinkGateway(http::shortLocation))
    private val controller = ParseController(viewModelScope) { input,token -> repository.fetch(resolver.resolve(input,token),token) }
    private val mutableReader = MutableStateFlow(ReaderState())
    val reader = mutableReader.asStateFlow()
    private val mutableNotificationEntry=MutableStateFlow(NotificationEntryState())
    val notificationEntry=mutableNotificationEntry.asStateFlow()
    fun initialNotification(action: String?,batchId: String?) {mutableNotificationEntry.update {it.initial(action,batchId)}}
    fun notificationOpened(action: String?,batchId: String?) {mutableNotificationEntry.update {it.receive(action,batchId)}}
    fun consumeNotification(sequence: Long) {mutableNotificationEntry.update {it.consume(sequence)}}
    private val preferences = application.getSharedPreferences("appearance",0)
    // 首次预览提示独立保存，不改变媒体选择和下载状态。
    private val mutablePreviewHint=MutableStateFlow(!preferences.getBoolean("preview_swipe_hint_seen",false))
    val previewSwipeHint=mutablePreviewHint.asStateFlow()
    fun dismissPreviewHint() {
        mutablePreviewHint.value=false
        preferences.edit().putBoolean("preview_swipe_hint_seen",true).apply()
    }
    private val mutableAppearance = MutableStateFlow(Appearance.entries.firstOrNull { it.name == preferences.getString("mode",null) } ?: Appearance.SYSTEM)
    val appearance = mutableAppearance.asStateFlow()
    private val mutableAlbum = MutableStateFlow(runCatching {
        AlbumDirectory.DEFAULT
    }.getOrDefault(AlbumDirectory.DEFAULT))
    val albumDirectory = mutableAlbum.asStateFlow()
    private val folderMutex=Mutex()
    private val mutableFolder=MutableStateFlow(SaveFolder(preferences.getString("folder_uri",null),
        preferences.getString("folder_name",null) ?: "默认相册"))
    val folder=mutableFolder.asStateFlow()
    private val mutableFolderBusy=MutableStateFlow(false)
    val folderBusy=mutableFolderBusy.asStateFlow()
    private val mutableFolderError=MutableStateFlow<String?>(null)
    val folderError=mutableFolderError.asStateFlow()
    val imageClient = http.previewClient(true)
    val videoClient = http.previewClient(false)
    val imageLoader = ImageLoader.Builder(application)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageClient })) }
        .memoryCache { MemoryCache.Builder().maxSizeBytes(24L * 1024 * 1024).build() }
        .diskCache(null).build()
    private val coordinator=DownloadCoordinator.get(application)
    val downloads=coordinator.state
    init {
        // 订阅现有保存任务；重建页面不恢复或重复派发。
        controller.state.onEach { next -> mutableReader.update { it.receive(next) } }.launchIn(viewModelScope)
    }
    fun editInput(input: String) = controller.edit(input)
    fun fetch() = controller.submit(controller.state.value.input)
    fun cancel() = controller.cancel()
    fun toggle(id: String) { mutableReader.update { it.toggle(id) } }
    fun setSelected(id: String, checked: Boolean) { mutableReader.update { it.setSelected(id,checked) } }
    fun setAlbumDirectory(input: String) {
        val name=AlbumDirectory.validate(input)
        mutableAlbum.value=name
        preferences.edit().putString("album",name).apply()
    }
    fun selectFolder(uri: Uri) {
        if(mutableFolderBusy.value) return
        mutableFolderBusy.value=true;mutableFolderError.value=null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    folderMutex.withLock {
                        val target=FolderAccess.adopt(getApplication(),uri)
                        preferences.edit().putString("folder_uri",target.treeUri).putString("folder_name",target.displayName).apply()
                        mutableFolder.value=target
                    }
                }
            } catch(cancelled: CancellationException) { throw cancelled
            } catch(_: Exception) {
                mutableFolderError.value="无法取得该文件夹的持久写入授权，请选择其他文件夹"
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {folderMutex.withLock {runCatching {releaseUnusedFolders()}}}
                mutableFolderBusy.value=false
            }
        }
    }
    fun restoreDefaultFolder() {
        if(mutableFolderBusy.value) return
        setAlbumDirectory(AlbumDirectory.DEFAULT)
        preferences.edit().remove("folder_uri").remove("folder_name").apply()
        mutableFolder.value=SaveFolder();mutableFolderError.value=null
        viewModelScope.launch(Dispatchers.IO) {folderMutex.withLock {runCatching {releaseUnusedFolders()}}}
    }
    private fun releaseUnusedFolders() {
        FolderAccess.releaseUnused(getApplication(),retainedFolderUris(folder.value,downloads.value.snapshot)+coordinator.retainedFolderUris())
    }
    fun selectAll() { mutableReader.update { it.selectAll() } }
    fun clearSelection() { mutableReader.update { it.copy(selected=emptySet()) } }
    fun preview(id: String?) { mutableReader.update { it.preview(id) } }
    fun setAppearance(value: Appearance) {
        mutableAppearance.value=value
        preferences.edit().putString("mode",value.name).apply()
    }
    fun save(choice: SaveChoice) {
        if(folderBusy.value) throw ProbeFailure("FOLDER","请等待文件夹授权完成")
        folder.value.treeUri?.let {if(!FolderAccess.hasWrite(getApplication(),it)) throw ProbeFailure("FOLDER","保存目录授权已失效，请重新选择")}
        val current=reader.value
        if(current.parse.requestId!=controller.state.value.requestId || controller.state.value.busy || controller.state.value.post==null)
            throw ProbeFailure("INPUT","解析结果已变化，请等待最新结果")
        startSaving(current.snapshot(choice).copy(albumDirectory=AlbumDirectory.DEFAULT,folder=folder.value),false)
    }
    fun retryFailed() {
        if(folderBusy.value) throw ProbeFailure("FOLDER","请等待文件夹授权完成")
        val snapshot=RetryPolicy.snapshot(downloads.value)
        snapshot.folder.treeUri?.let {if(!FolderAccess.hasWrite(getApplication(),it)) throw ProbeFailure("FOLDER","原批次目录授权已失效，请重新授权后重试")}
        startSaving(snapshot,true)
    }
    private fun startSaving(snapshot: SaveSnapshot,refresh: Boolean) {
        val id=coordinator.reserve(snapshot,refresh)
        try {
            ContextCompat.startForegroundService(getApplication(),Intent(getApplication(),DownloadService::class.java).putExtra("batch",id))
        } catch(_: Exception) {
            coordinator.abortStart("系统未允许启动保存，请在前台重试",id)
            throw ProbeFailure("SERVICE","系统未允许启动保存，请在前台重试")
        }
        viewModelScope.launch(Dispatchers.IO) {folderMutex.withLock {runCatching {releaseUnusedFolders()}}}
    }
    fun stopSaving() = coordinator.requestStop("用户取消批次")
    fun retryCleanup() = coordinator.recoverAgain()
    fun report(): String {
        val s = reader.value.parse; val post = s.post
        return """P5 ${BuildConfig.VERSION_NAME} / versionCode ${BuildConfig.VERSION_CODE}
设备：${Build.MODEL} Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}
构建：${Build.DISPLAY}
请求：${s.requestId}；来源：${s.source}；忙碌：${s.busy}；错误：${s.errorCode ?: "无"}
状态：${s.message}
帖子：${post?.id ?: "无"}；当前帖为转发：${post?.isRepost ?: false}
正文：${post?.bodyStatus ?: "无"}；字符数：${post?.text?.length ?: 0}
媒体列表：${post?.mediaStatus ?: "无"}；顺序：${post?.order ?: "无"}
媒体：${post?.media?.joinToString { "${it.id}:${it.kind}:封面=${it.preview != null}:静态候选=${it.candidate != null}:动态关联=${it.liveMotion != null}" } ?: "无"}
问题：${post?.issues?.joinToString { "${it.scope}:${it.code}:${it.message}" } ?: "无"}
选择：${reader.value.selected.size}；外观：${appearance.value}
保存：活动=${downloads.value.active}；成功=${downloads.value.success}；失败=${downloads.value.failed}；取消=${downloads.value.cancelled}
${coordinator.report()}
本报告不含输入原文、正文、作者、Cookie、签名 URL 或动态资源地址。"""
    }
    override fun onCleared() {
        controller.cancel(); imageLoader.shutdown()
        imageClient.dispatcher.cancelAll(); videoClient.dispatcher.cancelAll()
        super.onCleared()
    }
}
