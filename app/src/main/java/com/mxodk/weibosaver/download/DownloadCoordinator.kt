package com.mxodk.weibosaver.download

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import com.mxodk.weibosaver.BuildConfig
import com.mxodk.weibosaver.core.*
import com.mxodk.weibosaver.model.*
import com.mxodk.weibosaver.storage.*
import com.mxodk.weibosaver.ui.*
import com.mxodk.weibosaver.weibo.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException

private data class SaveBatch(val id: String,val source: SaveSnapshot,val refresh: Boolean)

// 进程级单批次：Activity 和 ViewModel 销毁不取消已开始保存，服务负责生命周期。
class DownloadCoordinator private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: DownloadCoordinator?=null
        fun get(context: Context): DownloadCoordinator=instance ?: synchronized(this) {
            instance ?: DownloadCoordinator(context.applicationContext).also {instance=it}
        }
        const val MAX_FILE_BYTES=2L*1024*1024*1024
        const val FILE_TIMEOUT_MS=15*60*1000L
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val workLock=Mutex()
    // 保留 P2 记录的恢复入口，升级后旧半成品仍能核对。
    private val journal=FileWriteJournal(File(context.filesDir,"p2-write-journal"))
    private val transaction=PublishTransaction(journal,TargetStore(context))
    private val reportFile=AtomicFile(File(context.filesDir,"p2-last-report.txt"))
    private val mutable=MutableStateFlow(DownloadState())
    val state=mutable.asStateFlow()
    private var batch: SaveBatch?=null
    private var token: OperationToken?=null
    private var gate: PublicationGate?=null
    private var running=false
    private var recovering=false
    init {recoverAgain()}
    @Synchronized private fun change(action: (DownloadState)->DownloadState) {mutable.value=action(mutable.value)}
    private fun event(text: String) {change {it.copy(events=(it.events+text).takeLast(40))}}
    fun retainedFolderUris(): Set<String> = journal.entries().mapNotNull {it.treeUri}.toSet()+listOfNotNull(state.value.snapshot?.folder?.treeUri)
    private fun persist() {
        synchronized(reportFile) {
            var output: java.io.FileOutputStream?=null
            try {output=reportFile.startWrite();output.write(report().takeLast(64*1024).toByteArray(Charsets.UTF_8));reportFile.finishWrite(output)}
            catch(_: Exception) {output?.let {reportFile.failWrite(it)}}
        }
    }
    fun recoverAgain() {
        synchronized(this) {
            if(mutable.value.active || recovering) return
            recovering=true;change {it.copy(ready=false)}
        }
        scope.launch {workLock.withLock {
            try {
                val previous=synchronized(reportFile) {runCatching {reportFile.openRead().use {it.readBytes().toString(Charsets.UTF_8)}}.getOrDefault("")}
                val results=transaction.recover()
                listOf("p2-transfer","p5-transfer").forEach {File(context.cacheDir,it).deleteRecursively()}
                change {it.copy(ready=true,previousReport=previous,message="启动核对完成；未恢复旧任务，待后续清理 ${results.count {r->r.endsWith(Recovery.DEFERRED.name)}} 项")}
            } catch(_: Exception) {change {it.copy(ready=false,message="存储检查未完成，请重试存储检查")}}
            finally {synchronized(this@DownloadCoordinator) {recovering=false}}
        }}
    }
    @Synchronized fun reserve(source: SaveSnapshot,refresh: Boolean=false): String {
        if(!state.value.ready || state.value.active || recovering) throw ProbeFailure("BUSY","正在执行或清理，请稍后再试")
        if(source.media.isEmpty() || source.media.any {it.candidate==null} || source.media.map {it.id}.toSet().size!=source.media.size)
            throw ProbeFailure("INPUT","没有可保存的唯一媒体集合")
        val snapshot=source.copy(media=source.media.toList())
        val id=UUID.randomUUID().toString()
        batch=SaveBatch(id,snapshot,refresh);token=OperationToken();gate=PublicationGate();running=false
        mutable.value=DownloadState(ready=true,active=true,batch=id,post=snapshot.postId,snapshot=snapshot,
            message="正在启动保存…",items=snapshot.media.map {SaveItem(it.id,it.kind)},previousReport=state.value.previousReport)
        return id
    }
    @Synchronized fun abortStart(reason: String,expected: String) {
        if(running || state.value.batch!=expected) return
        token?.cancel();batch=null
        change {it.copy(active=false,message=reason,items=it.items.map {item->item.copy(status="失败",detail=reason)})}
        persist()
    }
    fun requestStop(reason: String,expectedBatch: String?=null) {
        val current=synchronized(this) {
            if(!state.value.active || (expectedBatch!=null && state.value.batch!=expectedBatch)) return
            change {it.copy(stopping=true,message=reason)}
            token to gate
        }
        current.first?.cancel()
        scope.launch {current.second?.stop()}
    }
    fun runQueued(id: String?,finished: ()->Unit) {
        val pending=synchronized(this) {
            if(running && batch?.id==id) return
            if(id==null || batch?.id!=id || running) {finished();return}
            running=true;requireNotNull(batch)
        }
        val currentToken=requireNotNull(token);val currentGate=requireNotNull(gate)
        scope.launch {workLock.withLock {
            try {
                currentToken.check()
                pending.source.folder.treeUri?.let {FolderAccess.requireLocal(context,it)}
                val source=if(pending.refresh) {
                    change {it.copy(processingHint="正在重新解析原帖子并核对失败媒体…")}
                    val fresh=PostRepository(WeiboGateway(SafeHttpClient())).fetch(PostReference(pending.source.postId),currentToken)
                    RetryPolicy.refreshed(pending.source,fresh)
                } else pending.source.media
                var next=0
                fun claim(): Int? = synchronized(this@DownloadCoordinator) {
                    currentToken.check()
                    if(next>=source.size) null else next++.also {index->
                        change {s->s.copy(items=s.items.mapIndexed {i,item->if(i==index) item.copy(status="准备中") else item})}
                    }
                }
                // 每个工作者独占临时目录，不互相清除；最多两项同时流式传输。
                runBoundedWorkers(source.size) {worker->
                        val probe=MediaProbe(context,SafeHttpClient(),"p5-transfer/$worker",MAX_FILE_BYTES,FILE_TIMEOUT_MS)
                        try {
                            var assigned=claim()
                            while(assigned!=null) {
                                val index=requireNotNull(assigned)
                                var following: Int?=null
                                val media=source[index]
                                fun update(status: String,bytes: Long?=null,uri: String?=null,detail: String?=null,evidence: String?=null) {
                                    synchronized(this@DownloadCoordinator) {
                                        if(state.value.items[index].status in DownloadState.terminal) return
                                        val terminal=status in DownloadState.terminal
                                        // 前项结果与后项领取同一次发布，不闪现准备占位文案。
                                        val successor=if(terminal && status!="取消" && runCatching {currentToken.check()}.isSuccess && next<source.size) next++ else null
                                        if(terminal) following=successor
                                        change {s->s.copy(items=s.items.mapIndexed {i,item->when(i) {
                                            index->item.copy(status=status,bytes=bytes ?: item.bytes,uri=uri ?: item.uri,
                                                detail=detail ?: item.detail,evidence=evidence ?: item.evidence)
                                            successor->item.copy(status="准备中")
                                            else->item
                                        }})}
                                    }
                                }
                                var file: File?=null
                                try {
                                    currentToken.check();probe.clear()
                                    if(media.candidate==null) throw ProbeFailure("RESOURCE_CHANGED","原媒体已移除、类型改变或质量不足，未用其他项替换")
                                    update("传输中")
                                    val evidence=probe.fetch(media,currentToken) {bytes->update("传输中",bytes)}
                                    file=evidence.file;update("核验中",bytes=evidence.bytes,evidence=evidence.report())
                                    val receipt=transaction.save(SaveAsset(evidence.file,evidence.mime,evidence.bytes,evidence.sha256),currentToken,currentGate,
                                        FaultPoint.NONE,{stage->when {
                                            stage.startsWith("已发布并确认：")->update("成功",uri=stage.substringAfter('：'))
                                            stage=="提交媒体库发布"->update("发布中")
                                            else->update("写入中")
                                        }},{},AlbumDirectory.DEFAULT,pending.source.folder.treeUri)
                                    update("成功",uri=receipt.uri)
                                } catch(cancelled: CancellationException) {
                                    if(state.value.items[index].status!="成功") update("取消")
                                    throw cancelled
                                } catch(error: Exception) {
                                    currentToken.check()
                                    update("失败",detail=failureText(error));event("第 ${index+1} 项失败：${failureText(error)}")
                                } finally {file?.delete()}
                                assigned=following
                            }
                        } finally {probe.clear()}
                }
            } catch(_: CancellationException) {event("已停止派发和在途传输")
            } catch(error: Exception) {
                change {s->s.copy(items=s.items.map {if(it.status in DownloadState.terminal) it else it.copy(status="失败",detail=failureText(error))})}
            } finally {
                synchronized(this@DownloadCoordinator) {
                    change {s->s.copy(active=false,stopping=false,message="保存结束",items=s.items.map {
                        if(it.status in DownloadState.terminal) it else it.copy(status="取消")
                    })}
                    batch=null;running=false
                }
                persist();withContext(Dispatchers.Main) {finished()}
            }
        }}
    }
    private fun failureText(error: Exception): String = when(error) {
        is ProbeFailure->error.message
        is SecurityException->"保存目录授权被拒绝，请重新选择后重试"
        is java.io.IOException->"网络或存储读写失败，请检查网络和可用空间后重试"
        else->"保存未完成，请检查目录和可用空间后重试"
    }
    fun report(): String {
        val s=state.value
        return """P5 ${BuildConfig.VERSION_NAME} / versionCode ${BuildConfig.VERSION_CODE}
设备：${Build.MODEL} Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}
构建：${Build.DISPLAY}
批次：${s.batch}；帖子：${s.post}；运行中：${s.active}
成功 ${s.success}；失败 ${s.failed}；取消 ${s.cancelled}；处理 ${s.percent}%
${s.items.joinToString("\n") {"${it.id} ${it.kind} ${it.status} ${it.bytes} bytes\n${it.detail}\n${it.evidence}"}}
${s.events.joinToString("\n")}
不含 Cookie、资源 URL、目录 URI、正文或作者；实际系统相册与后台表现以真机记录为准。"""
    }
}
