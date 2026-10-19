package com.mxodk.weibosaver.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mxodk.weibosaver.download.DownloadState
import com.mxodk.weibosaver.entry.ProbeViewModel
import com.mxodk.weibosaver.model.ProbeFailure
import com.mxodk.weibosaver.model.ProbePost
import com.mxodk.weibosaver.model.MediaKind

@Composable
fun SaveActions(vm: ProbeViewModel, state: ReaderState, batch: DownloadState, post: ProbePost, onSave: (SaveChoice)->Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("已选 ${state.selected.size} 项",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
            TextButton(onClick=vm::selectAll) { Text("全选") }
            TextButton(onClick=vm::clearSelection) { Text("取消全选") }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={onSave(SaveChoice.ALL)},enabled=batch.ready && !batch.active && post.media.any { it.candidate != null },
                modifier=Modifier.weight(1f)) { Text("全部保存") }
            Button(onClick={onSave(SaveChoice.SELECTED)},enabled=batch.ready && !batch.active && state.selected.isNotEmpty(),
                modifier=Modifier.weight(1f)) { Text("保存所选 (${state.selected.size})") }
        }
        if(!batch.ready) TextButton(onClick=vm::retryCleanup) {Text("重试存储检查")}
        // 空闲时不重复解释保存操作，仅说明暂时不可保存的原因。
        if(batch.active || !batch.ready) Text(if(batch.active) "保存中，请等待完成或取消" else "正在检查存储，请稍候",
            style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TaskSummary(batch: DownloadState, onOpen: ()->Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick=onOpen).padding(horizontal=14.dp,vertical=8.dp),
        verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(batch.active) "保存中 · 处理 ${batch.percent}%" else if(batch.cancelled>0) "已取消 · 处理 ${batch.percent}%" else "已结束 · 处理 ${batch.percent}%",
                    style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium)
                Text("已处理 ${batch.processed}/${batch.items.size} · 成功 ${batch.done} · 失败 ${batch.failed}",style=MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick=onOpen) { Text("展开") }
        }
        LinearProgressIndicator(progress={batch.progress},modifier=Modifier.fillMaxWidth())
    }
}

@Composable
fun SettingsPanel(vm: ProbeViewModel, appearance: Appearance, album: String, onClose: ()->Unit) {
    val folder by vm.folder.collectAsStateWithLifecycle()
    val busy by vm.folderBusy.collectAsStateWithLifecycle()
    val error by vm.folderError.collectAsStateWithLifecycle()
    var menu by remember {mutableStateOf(false)}
    var pickerError by rememberSaveable {mutableStateOf<String?>(null)}
    val contract=remember {
        object: ActivityResultContracts.OpenDocumentTree() {
            override fun createIntent(context: Context, input: Uri?): Intent =
                super.createIntent(context,input).putExtra(Intent.EXTRA_LOCAL_ONLY,true)
        }
    }
    val picker=rememberLauncherForActivityResult(contract) {uri -> if(uri != null) vm.selectFolder(uri)}
    AlertDialog(onDismissRequest={if(!busy) onClose()},title={Text("设置")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("外观",fontWeight=FontWeight.SemiBold)
            Box {
                OutlinedButton(onClick={menu=true},modifier=Modifier.fillMaxWidth()) {
                    Text(appearance.label,Modifier.weight(1f));Text("▾")
                }
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    Appearance.entries.forEach {option -> DropdownMenuItem(text={Text((if(option==appearance) "✓ " else "")+option.label)},
                        onClick={vm.setAppearance(option);menu=false})}
                }
            }
            HorizontalDivider()
            Text("保存位置",fontWeight=FontWeight.SemiBold)
            Text(if(folder.treeUri==null) "默认相册\n图片：Pictures/$album/\n视频：Movies/$album/" else "当前文件夹：${folder.displayName}")
            if(busy) {LinearProgressIndicator(Modifier.fillMaxWidth());Text("正在确认文件夹授权…")}
            (pickerError ?: error)?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
            OutlinedButton(onClick={
                pickerError=null
                try {picker.launch(folder.treeUri?.let(Uri::parse))}
                catch(_: android.content.ActivityNotFoundException) {pickerError="系统文件夹选择器不可用，请使用默认相册"}
            },enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text("选择文件夹")}
            TextButton(onClick={vm.restoreDefaultFolder();pickerError=null},enabled=!busy) {Text("恢复默认位置")}
            Text("新位置仅用于新任务；重试仍保存到原位置。自选文件夹可能不显示在相册中。",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    },confirmButton={Button(onClick=onClose,enabled=!busy) {Text("完成")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(batch: DownloadState, onDismiss: ()->Unit, onStop: ()->Unit, onRetry: ()->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss) {
        // 摘要和控制独立于失败列表；长列表不会把取消按钮推到屏幕外。
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max=560.dp)) {
        val headerLimit=maxHeight * .45f
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=18.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Column(Modifier.heightIn(max=headerLimit).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("保存进度",fontSize=20.sp,fontWeight=FontWeight.SemiBold)

                Text("完成 ${batch.done} · 失败 ${batch.failed} · 取消 ${batch.cancelled}")
                Text("已处理 ${batch.processed}/${batch.items.size} · ${batch.percent}%",fontWeight=FontWeight.Medium)
                LinearProgressIndicator(progress={batch.progress},modifier=Modifier.fillMaxWidth())
                Text(batch.processingText,style=MaterialTheme.typography.bodySmall,minLines=2,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text("保存位置：${batch.snapshot?.let {if(it.folder.treeUri==null) it.albumDirectory else it.folder.displayName}}",style=MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(batch.active) OutlinedButton(onClick=onStop,modifier=Modifier.weight(1f)) {Text("取消批次")}
                if(!batch.active && batch.failed>0) OutlinedButton(onClick=onRetry,modifier=Modifier.weight(1f)) {Text("重试失败项")}
                Button(onClick=onDismiss,modifier=Modifier.weight(1f)) {Text("收起面板")}
            }
            HorizontalDivider()
            if(batch.failed>0) {
                Text("失败项 · ${batch.failed}",fontWeight=FontWeight.Medium)

                LazyColumn(Modifier.weight(1f,false).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp),contentPadding=PaddingValues(bottom=12.dp)) {
                    batch.items.forEachIndexed {index,item -> if(item.status=="失败") item(key=item.id) {
                        Surface(color=MaterialTheme.colorScheme.errorContainer,shape=MaterialTheme.shapes.small) {
                            Text("第 ${index+1} 项 · ${if(item.kind==MediaKind.VIDEO) "视频" else "图片"} · ${item.detail}",
                                Modifier.fillMaxWidth().padding(8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onErrorContainer)
                        }
                    } }
                }
            }
        }
    }
}
}
