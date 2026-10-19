package com.mxodk.weibosaver.ui

import com.mxodk.weibosaver.model.*
import com.mxodk.weibosaver.weibo.ParseState

enum class Appearance(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }
enum class SaveChoice { ALL, SELECTED }
// URI 仅供目录授权和后续写入，不输出到诊断；快照保留原目录身份。
data class SaveFolder(val treeUri: String? = null, val displayName: String = "默认相册")
data class SaveSnapshot(val requestId: Long, val postId: String, val choice: SaveChoice, val media: List<ProbeMedia>, val albumDirectory: String = AlbumDirectory.DEFAULT, val folder: SaveFolder = SaveFolder())

fun retainedFolderUris(current: SaveFolder, snapshot: SaveSnapshot?): Set<String> =
    listOfNotNull(current.treeUri,snapshot?.folder?.treeUri).toSet()

// 选择和预览以解析请求及媒体标识绑定，不以网格位置或签名 URL 作为身份。
data class ReaderState(val parse: ParseState = ParseState(), val selected: Set<String> = emptySet(), val previewId: String? = null) {
    fun receive(next: ParseState): ReaderState {
        val post = next.post
        if (post == null) return ReaderState(next)
        val eligible = post.media.filter { it.candidate != null }.map { it.id }.toSet()
        return if (parse.requestId != next.requestId || parse.post == null) ReaderState(next,eligible)
        else copy(parse=next,selected=selected.intersect(eligible),previewId=previewId?.takeIf { id -> post.media.any { it.id == id } })
    }
    fun toggle(id: String): ReaderState {
        if (parse.post?.media?.none { it.id == id && it.candidate != null } != false) return this
        return copy(selected=if (id in selected) selected-id else selected+id)
    }
    fun setSelected(id: String, checked: Boolean): ReaderState {
        if (parse.post?.media?.none { it.id == id && it.candidate != null } != false) return this
        return copy(selected=if (checked) selected+id else selected-id)
    }
    fun selectAll() = copy(selected=parse.post?.media.orEmpty().filter { it.candidate != null }.map { it.id }.toSet())
    fun preview(id: String?) = copy(previewId=id?.takeIf { key -> parse.post?.media?.any { it.id == key } == true })
    fun snapshot(choice: SaveChoice): SaveSnapshot {
        val post = parse.post ?: throw ProbeFailure("INPUT","请先解析帖子")
        if (parse.busy) throw ProbeFailure("INPUT","请等待当前解析完成")
        val media = post.media.filter { it.candidate != null && (choice == SaveChoice.ALL || it.id in selected) }
        if (media.isEmpty()) throw ProbeFailure("INPUT","请至少选择一个可保存的媒体")
        return SaveSnapshot(parse.requestId,post.id,choice,media.toList())
    }
}

enum class DemoStatus(val label: String) { WAITING("等待演示"), RUNNING("演示处理中"), DONE("演示完成"), FAILED("演示失败"), CANCELLED("已取消演示") }
data class DemoItem(val id: String, val kind: MediaKind, val status: DemoStatus = DemoStatus.WAITING)
data class DemoBatch(val snapshot: SaveSnapshot? = null, val active: Boolean = false, val items: List<DemoItem> = emptyList(), val retrying: Boolean = false) {
    val done get() = items.count { it.status == DemoStatus.DONE }
    val failed get() = items.count { it.status == DemoStatus.FAILED }
    // 失败也是已处理项；取消不推进处理百分比，避免取消后显示全部完成。
    val processed get() = done + failed
    val progress get() = if (items.isEmpty()) 0f else processed.toFloat() / items.size
    val percent get() = (progress * 100).toInt()
    val cancelled get() = items.count { it.status == DemoStatus.CANCELLED }
    val processingText: String get() {
        if(!active) return if(cancelled>0) "演示已取消" else "演示已结束"
        // 活动批次由 reducer 保证恰有一个当前项；不展示交接占位文字。
        val index=items.indexOfFirst {it.status==DemoStatus.RUNNING}
        if(index<0) return ""
        val type=if(items[index].kind==MediaKind.VIDEO) "视频" else "图片"
        return "正在处理 ${index+1}/${items.size} 项 · $type，剩余 ${items.count {it.status==DemoStatus.WAITING}} 项"
    }
    fun stop() = copy(active=false,items=items.map { if (it.status in setOf(DemoStatus.WAITING,DemoStatus.RUNNING)) it.copy(status=DemoStatus.CANCELLED) else it })
}

// 纯面板演示不访问网络或媒体库，不能将其完成数作为实际保存数。
object DemoReducer {
    fun begin(current: DemoBatch, snapshot: SaveSnapshot): DemoBatch {
        if (current.active) throw ProbeFailure("BUSY","请等待当前批次完成，或先取消当前批次")
        require(snapshot.media.isNotEmpty())
        return DemoBatch(snapshot,true,snapshot.media.mapIndexed {index,item -> DemoItem(item.id,item.kind,if(index==0) DemoStatus.RUNNING else DemoStatus.WAITING)})
    }
    fun step(batch: DemoBatch): DemoBatch {
        if (!batch.active) return batch
        val running=batch.items.indexOfFirst {it.status==DemoStatus.RUNNING}
        val items=batch.items.mapIndexed {index,item ->
            if(index==running) item.copy(status=if(item.kind==MediaKind.VIDEO && !batch.retrying) DemoStatus.FAILED else DemoStatus.DONE)
            else item
        }.toMutableList()
        val next=items.indexOfFirst {it.status==DemoStatus.WAITING}
        // 前项结束和下一项启动一次发布，避免中间空状态引起文字消失和面板跳动。
        if(next>=0) items[next]=items[next].copy(status=DemoStatus.RUNNING)
        return batch.copy(items=items.toList(),active=next>=0)
    }
    fun retry(batch: DemoBatch): DemoBatch {
        val source=batch.snapshot ?: throw ProbeFailure("INPUT","尚无可重试的演示批次")
        val failed=batch.items.filter { it.status == DemoStatus.FAILED }.map { it.id }.toSet()
        if(failed.isEmpty()) throw ProbeFailure("INPUT","没有失败的演示项")
        return begin(batch,source.copy(choice=SaveChoice.SELECTED,media=source.media.filter { it.id in failed })).copy(retrying=true)
    }
}
