package com.mxodk.weibosaver.download

import com.mxodk.weibosaver.model.*
import com.mxodk.weibosaver.ui.SaveSnapshot

// 页面、面板、通知只订阅同一份真实批次结果；不以预览 URL 或网格位置识别媒体。
data class SaveItem(val id: String,val kind: MediaKind,val status: String="排队",val bytes: Long=0,
    val uri: String?=null,val detail: String="",val evidence: String="")
data class DownloadState(val ready: Boolean=false,val active: Boolean=false,val batch: String="",val post: String="",
    val message: String="正在核对上次残留（不恢复下载）…",val items: List<SaveItem> = emptyList(),
    val snapshot: SaveSnapshot?=null,val stopping: Boolean=false,val processingHint: String="准备保存…",
    val events: List<String> = emptyList(),val previousReport: String="") {
    val success get()=items.count {it.status=="成功"}
    val done get()=success
    val failed get()=items.count {it.status=="失败"}
    val cancelled get()=items.count {it.status=="取消"}
    val processed get()=success+failed
    val progress get()=if(items.isEmpty()) 0f else processed.toFloat()/items.size
    val percent get()=(progress*100).toInt()
    val processingText: String get() {
        if(!active) return if(cancelled>0) "批次已取消" else "批次已结束"
        if(stopping) return "正在取消并清理未完成项…"
        val current=items.withIndex().filter {it.value.status !in terminal && it.value.status!="排队"}
        if(current.isEmpty()) return if(items.isNotEmpty() && items.all {it.status in terminal}) {
            if(cancelled>0) "批次已取消" else "批次已结束"
        } else processingHint
        val stages=current.joinToString("；") {(i,item)->
            "第 ${i+1}/${items.size} 项 · ${if(item.kind==MediaKind.VIDEO) "视频" else "图片"} · ${item.status}"+
                if(item.status=="传输中" && item.bytes>0) " ${item.bytes/1024} KiB" else ""
        }
        return "$stages；等待 ${items.count {it.status=="排队"}} 项"
    }
    companion object {val terminal=setOf("成功","失败","取消")}
}

object RetryPolicy {
    fun snapshot(state: DownloadState): SaveSnapshot {
        if(state.active) throw ProbeFailure("BUSY","请等待当前批次完成或取消")
        val source=state.snapshot ?: throw ProbeFailure("INPUT","没有可重试的批次")
        val failed=state.items.filter {it.status=="失败"}.map {it.id}.toSet()
        if(failed.isEmpty()) throw ProbeFailure("INPUT","没有失败项")
        return source.copy(media=source.media.filter {it.id in failed}.toList())
    }
    fun refreshed(source: SaveSnapshot, fresh: ProbePost): List<ProbeMedia> {
        if(fresh.id!=source.postId) throw ProbeFailure("IDENTITY","帖子身份已变化，未替换原批次")
        // 每项独立匹配；缺失或类型变化保留为空候选，不取相同数组下标替代。
        return source.media.map {old ->
            val next=fresh.media.singleOrNull {it.id==old.id && it.kind==old.kind}
            val a=old.candidate;val c=next?.candidate
            val lower=a!=null && c!=null && (c.qualityIndex<a.qualityIndex ||
                (a.width>0 && a.height>0 && c.width.toLong()*c.height<a.width.toLong()*a.height) ||
                (a.bitrate>0 && c.bitrate>0 && c.bitrate<a.bitrate))
            if(next==null || c==null || lower) old.copy(candidate=null) else next
        }
    }
}
