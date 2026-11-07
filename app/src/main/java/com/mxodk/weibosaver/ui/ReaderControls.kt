package com.mxodk.weibosaver.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mxodk.weibosaver.BuildConfig
import com.mxodk.weibosaver.download.DownloadState
import com.mxodk.weibosaver.entry.ProbeViewModel
import com.mxodk.weibosaver.model.MediaKind
import com.mxodk.weibosaver.model.ProbePost

@Composable
fun SaveActions(vm: ProbeViewModel, state: ReaderState, batch: DownloadState, post: ProbePost, onSave: (SaveChoice)->Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("已选 ${state.selected.size} 项",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
            TextButton(onClick=vm::selectAll) { Text("全选") }
            TextButton(onClick=vm::clearSelection) { Text("取消已选") }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskSummary(batch: DownloadState, previousPost: Boolean, onDetails: () -> Unit,
                onStop: () -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal=14.dp, vertical=8.dp),
        verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(when {
                    batch.stopping -> "正在取消…"
                    batch.active && previousPost -> "上一条微博正在下载"
                    batch.active -> "正在下载 · ${batch.percent}%"
                    batch.cancelled > 0 -> "已取消"
                    batch.failed > 0 -> "部分保存失败"
                    else -> "保存完成"
                }, style=MaterialTheme.typography.bodyMedium, fontWeight=FontWeight.Medium)
            }
            if (batch.active) OutlinedButton(onClick=onStop, enabled=!batch.stopping) {
                Text(if(batch.stopping) "正在取消…" else "取消当前下载")
            } else OutlinedButton(onClick=onDismiss) { Text("关闭") }
        }
        // 限制摘要说明高度，长目录和大字体可滚动，顶部取消入口保持可见。
        Column(Modifier.heightIn(max=140.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(4.dp)) {
            if (batch.active) {
                Text(batch.processingText, style=MaterialTheme.typography.labelSmall,
                    minLines=2, maxLines=2, overflow=TextOverflow.Ellipsis)
                Text("已处理 ${batch.processed}/${batch.items.size} · ${batch.percent}% · 成功 ${batch.done} · 失败 ${batch.failed}",
                    style=MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(progress={batch.progress}, modifier=Modifier.fillMaxWidth())
            }
            if (!batch.active) Text("已保存 ${batch.done} 项 · 失败 ${batch.failed} 项 · 已取消 ${batch.cancelled} 项",
                style=MaterialTheme.typography.labelSmall)
            val stillCount = batch.items.count { it.status == "成功" && it.id in batch.snapshot?.stillOnlyIds.orEmpty() }
            if (stillCount > 0) Text("其中 $stillCount 项仅保存静态图片", style=MaterialTheme.typography.labelSmall)
            Text("保存位置：${batch.snapshot?.let { if(it.folder.treeUri==null) it.albumDirectory else it.folder.displayName }}",
                style=MaterialTheme.typography.labelSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if (batch.failed > 0) {
                    OutlinedButton(onClick=onDetails) { Text("查看失败项") }
                    if (!batch.active) OutlinedButton(onClick=onRetry) { Text("重试失败项") }
                }
            }
        }
    }
}

@Composable
fun SettingsPanel(vm: ProbeViewModel, appearance: Appearance, album: String, onClose: ()->Unit) {
    val headingStyle = MaterialTheme.typography.titleLarge.copy(
        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold
    )
    val context = LocalContext.current
    var browserError by rememberSaveable { mutableStateOf(false) }
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
            Text("外观", style = headingStyle)
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
            Text("保存位置", style = headingStyle)
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
            HorizontalDivider()
            Text("关于", style = headingStyle)
            Text("版本 ${BuildConfig.VERSION_NAME}")
            OutlinedButton(onClick = {
                browserError = false
                // 由系统选择浏览器，打开公开仓库页面。
                val browser = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
                val project = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mxodk/weibo_saver"))
                project.selector = browser
                try {
                    context.startActivity(project)
                } catch (_: ActivityNotFoundException) {
                    browserError = true
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("打开 GitHub 项目")
            }
            if (browserError) {
                Text("找不到可用的浏览器", color = MaterialTheme.colorScheme.error)
            }
        }
    },confirmButton={Button(onClick=onClose,enabled=!busy) {Text("完成")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(batch: DownloadState, onDismiss: () -> Unit, onRetry: () -> Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss) {
        // 详情只承载失败信息；取消与进度直接在主页面摘要操作。
        Column(Modifier.fillMaxWidth().heightIn(max=560.dp).navigationBarsPadding().padding(horizontal=18.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("失败项 · ${batch.failed}", style=MaterialTheme.typography.titleLarge)
            Text("保存位置：${batch.snapshot?.let { if(it.folder.treeUri==null) it.albumDirectory else it.folder.displayName }}",
                style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if (!batch.active) OutlinedButton(onClick=onRetry) { Text("重试失败项") }
                Button(onClick=onDismiss) { Text("关闭详情") }
            }
            LazyColumn(Modifier.weight(1f, false).fillMaxWidth(),
                verticalArrangement=Arrangement.spacedBy(4.dp), contentPadding=PaddingValues(bottom=12.dp)) {
                batch.items.forEachIndexed { index, item ->
                    if (item.status == "失败") item(key=item.id) {
                        Surface(color=MaterialTheme.colorScheme.errorContainer, shape=MaterialTheme.shapes.small) {
                            val kind = when(item.kind) {
                                MediaKind.VIDEO -> "视频"
                                MediaKind.LIVE_PHOTO -> "实况"
                                MediaKind.IMAGE -> "图片"
                            }
                            Text("第 ${index+1} 项 · $kind · ${item.detail}", Modifier.fillMaxWidth().padding(8.dp),
                                style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }
    }
}
