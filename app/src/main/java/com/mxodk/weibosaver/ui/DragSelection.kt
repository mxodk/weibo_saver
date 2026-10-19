package com.mxodk.weibosaver.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.max

class DragSelectionSession {
    var checked = false
        private set
    private val visited = mutableSetOf<String>()
    fun begin(wasSelected: Boolean) { checked=!wasSelected; visited.clear() }
    fun visit(id: String) = visited.add(id)
}

class DragSelectionBinding(val modifier: Modifier, val bounds: (String,Rect?)->Unit)

// 长按才进入划选，普通滑动仍滚动页面；可见区域边缘有界自动滚动。
@Composable
fun rememberDragSelection(requestId: Long, selected: Set<String>, list: LazyListState,
                          onSelect: (String,Boolean)->Unit): DragSelectionBinding {
    val rectangles=remember(requestId) { mutableMapOf<String,Rect>() }
    val session=remember(requestId) { DragSelectionSession() }
    val currentSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pointer by remember(requestId) { mutableStateOf<Offset?>(null) }
    val edge=with(LocalDensity.current) { 64.dp.toPx() }
    fun hit(point: Offset) {
        val c=coordinates?.takeIf {it.isAttached} ?: return
        val local=point-c.localToRoot(Offset.Zero)
        if(local.x !in 0f..c.size.width.toFloat()) return
        val visible=c.localToRoot(Offset(local.x,local.y.coerceIn(1f,(c.size.height-1).coerceAtLeast(1).toFloat())))
        rectangles.entries.firstOrNull { it.value.contains(visible) }?.key?.let {
            if(session.visit(it)) select(it,session.checked)
        }
    }
    LaunchedEffect(requestId,pointer != null) {
        while(pointer != null) {
            val c=coordinates
            val p=pointer
            if(c != null && c.isAttached && p != null) {
                val local=p-c.localToRoot(Offset.Zero)
                val delta=when {
                    local.y < edge -> -18f * ((edge-local.y)/edge).coerceIn(0f,1f)
                    local.y > c.size.height-edge -> 18f * ((local.y-c.size.height+edge)/edge).coerceIn(0f,1f)
                    else -> 0f
                }
                if(delta != 0f) { list.scrollBy(delta); hit(p) }
            }
            delay(16)
        }
    }
    val modifier=Modifier.onGloballyPositioned { coordinates=it }.pointerInput(requestId) {
        detectDragGesturesAfterLongPress(
            onDragStart={ position ->
                val root=coordinates?.takeIf { it.isAttached }?.localToRoot(position)
                val id=root?.let { p -> rectangles.entries.firstOrNull { it.value.contains(p) }?.key }
                if(id != null) { session.begin(id in currentSelected);pointer=root;hit(root) }
            },
            onDragEnd={pointer=null},onDragCancel={pointer=null},
            onDrag={ change,_ ->
                val previous=pointer
                val next=coordinates?.takeIf { it.isAttached }?.localToRoot(change.position)
                if(previous != null && next != null) {
                    change.consume()
                    // 快速横跨多个格子也按路径命中，不只检查最后一个点。
                    val steps=max(1,ceil((next-previous).getDistance()/8f).toInt())
                    for(i in 1..steps) hit(previous+(next-previous)*(i.toFloat()/steps))
                    pointer=next
                }
            })
    }
    return DragSelectionBinding(modifier) { id,rect -> if(rect == null) rectangles.remove(id) else rectangles[id]=rect }
}
