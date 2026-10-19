package com.mxodk.weibosaver.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.app.Activity
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mxodk.weibosaver.BuildConfig
import com.mxodk.weibosaver.entry.ProbeViewModel
import com.mxodk.weibosaver.entry.matches
import com.mxodk.weibosaver.model.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderApp(vm: ProbeViewModel = viewModel()) {
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val state by vm.reader.collectAsStateWithLifecycle()
    val batch by vm.downloads.collectAsStateWithLifecycle()
    val notificationEntry by vm.notificationEntry.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val album by vm.albumDirectory.collectAsStateWithLifecycle()
    val focusManager=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val listState=rememberLazyListState()
    var inputFocused by remember {mutableStateOf(false)}
    var inputBounds by remember {mutableStateOf<Rect?>(null)}
    val drag=rememberDragSelection(state.parse.requestId,state.selected,listState,vm::setSelected)
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    val snack=remember { SnackbarHostState() }
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    val notifyPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->
        if(!granted) scope.launch {snack.showSnackbar("通知权限未开启，系统可能限制通知展示；请在面板查看保存结果")}
    }
    LaunchedEffect(notificationEntry.request?.sequence,batch.batch,batch.ready,batch.snapshot!=null) {
        val request=notificationEntry.request ?: return@LaunchedEffect
        if(!batch.ready) return@LaunchedEffect
        if(request.matches(batch.batch,batch.snapshot!=null)) {
            showSettings=false;showReport=false
            vm.preview(null);focusManager.clearFocus(force=true);keyboard?.hide()
            showSheet=true
        } else snack.showSnackbar("该批次记录已不可用，可查看当前保存状态")
        vm.consumeNotification(request.sequence)
    }
    fun copyText(value: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("微博正文",value))
        scope.launch { snack.showSnackbar("已复制") }
    }
    fun parse() {
        focusManager.clearFocus(force=true)
        keyboard?.hide()
        vm.fetch()
    }
    fun retry() {
        try {vm.retryFailed()} catch(error: ProbeFailure) {scope.launch {snack.showSnackbar(error.message)}}
    }
    fun save(choice: SaveChoice) {
        try {
            vm.save(choice);showSheet=false
            if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        catch(error: ProbeFailure) { scope.launch { snack.showSnackbar(error.message) } }
    }
    ReaderTheme(appearance) {
        val view=LocalView.current
        val density=LocalDensity.current
        val ime=WindowInsets.ime
        LaunchedEffect(view,inputFocused,density) {
            if(!inputFocused) return@LaunchedEffect
            // 只在键盘高度或文本框高度变化时检查；不追逐用户手动滚动的位置。
            snapshotFlow {ime.getBottom(density) to (inputBounds?.height ?: 0f)}.collectLatest { (keyboardHeight,_) ->
                if(keyboardHeight<=0) return@collectLatest
                // 等尾部滚动余量完成本帧布局，再读取输入框位置。
                withFrameNanos { }
                val bounds=inputBounds ?: return@collectLatest
                val overlap=bounds.bottom+with(density) {8.dp.toPx()}-(view.height-keyboardHeight)
                if(overlap>1f && !listState.isScrollInProgress) listState.scrollBy(overlap)
            }
        }
        val light=MaterialTheme.colorScheme.background.luminance() > .5f
        SideEffect {
            (context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window,view).apply {
                    isAppearanceLightStatusBars=light;isAppearanceLightNavigationBars=light
                }
            }
        }
        Scaffold(contentWindowInsets=WindowInsets.systemBars,containerColor=MaterialTheme.colorScheme.background,snackbarHost={SnackbarHost(snack)},
            bottomBar={
                Surface(color=MaterialTheme.colorScheme.surface,shadowElevation=6.dp) {
                    Column(Modifier.navigationBarsPadding()) {
                        if(batch.snapshot != null) TaskSummary(batch,onOpen={showSheet=true})
                        state.parse.post?.takeIf { it.media.isNotEmpty() }?.let { post ->
                            SaveActions(vm,state,batch,post,onSave=::save)
                        }
                    }
                }
            }) { padding ->
            // 列表视口不随 IME 缩小，键盘始终覆盖已完整绘制的内容，避免收起时露出裁切色带。
            LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).then(drag.modifier),state=listState,
                contentPadding=PaddingValues(horizontal=14.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("微博下载器",fontSize=24.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                        TextButton(onClick={showSettings=true}) { Text("设置") }
                    }
                }
                item { InputCard(vm,state,onParse=::parse,onInputFocus={inputFocused=it},onInputBounds={inputBounds=it}) }
                if(state.parse.busy) item {
                    AppCard { LinearProgressIndicator(Modifier.fillMaxWidth());Text("正在解析当前帖子…") }
                } else if(state.parse.post == null) item {
                    if(state.parse.errorCode != null) AppCard {
                        Text("解析未完成",fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.error)
                        Text(state.parse.message)
                        TextButton(onClick=::parse) { Text("重试解析") }
                    } else Column(Modifier.fillMaxWidth().padding(vertical=48.dp),horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("↗",fontSize=42.sp,color=MaterialTheme.colorScheme.primary)
                        Text("从一条微博开始",fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                        Text("粘贴帖子链接，然后点击解析",color=MaterialTheme.colorScheme.onSurfaceVariant)

                        if(state.parse.requestId > 0) Text(state.parse.message + if(batch.active && state.parse.message=="已取消解析") "，下载继续" else "",style=MaterialTheme.typography.bodySmall)
                    }
                }
                state.parse.post?.let { post -> item(key="result-${state.parse.requestId}") {
                    AppCard {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) {
                                Text(post.author?.name?.take(1)?.ifEmpty { "微" } ?: "微",fontWeight=FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(post.author?.name?.ifEmpty { "当前帖子" } ?: "当前帖子",fontWeight=FontWeight.SemiBold)
                                if(post.isRepost) Text("转发帖",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick={copyText(post.text)},enabled=post.text.isNotEmpty()) { Text("复制正文") }
                        }
                        var expanded by rememberSaveable(state.parse.requestId) { mutableStateOf(false) }
                        var overflowing by remember(state.parse.requestId) { mutableStateOf(false) }
                        SelectionContainer { Text(post.text.ifEmpty { "未获取到正文" },fontSize=14.sp,lineHeight=23.sp,
                            maxLines=if(expanded) Int.MAX_VALUE else 10,overflow=TextOverflow.Ellipsis,
                            onTextLayout={if(!expanded) overflowing=it.hasVisualOverflow}) }
                        if(expanded || overflowing) TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "收起正文" else "展开正文") }
                        if(post.bodyStatus != BodyStatus.COMPLETE && post.text.isNotEmpty()) Notice(when(post.bodyStatus) {
                            BodyStatus.TRUNCATED -> "正文可能不完整"
                            BodyStatus.MISSING -> "未获取到正文"
                            else -> "正文可能不完整"
                        })
                        if(post.mediaStatus != MediaListStatus.COMPLETE) Notice("部分媒体未能获取")
                        if(post.order == MediaOrder.UNPROVEN) Notice("媒体顺序可能与原帖不同")
                        post.issues.filter { it.scope in setOf(IssueScope.ITEM,IssueScope.ACCESS) && it.code != "STATIC_ONLY" }
                            .distinctBy { it.message }.forEach { Notice(it.message) }
                        if(post.media.isEmpty()) Text(if(post.mediaStatus == MediaListStatus.COMPLETE) "此帖没有图片或视频" else "未能获取媒体列表",
                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        else {
                            HorizontalDivider()
                            val images=post.media.count { it.kind != MediaKind.VIDEO };val videos=post.media.size-images
                            Text("媒体 · $images 张图片 / $videos 个视频",fontWeight=FontWeight.SemiBold)
                            Text("长按后滑动可多选",style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                            post.media.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                row.forEach { media -> MediaTile(vm,media,media.id in state.selected,Modifier.weight(1f),drag.bounds) }
                                repeat(3-row.size) { Spacer(Modifier.weight(1f)) }
                            } }


                        }
                    }
                } }
                item {
                    Column {
                        // 开发诊断入口仅供 Debug 使用，正式包不显示。
                        if(BuildConfig.DEBUG) {
                            TextButton(onClick={showReport=true}) { Text("开发验证版 ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.labelSmall) }
                        }
                        // 只增加列表末尾的可滚动余量，不改变视口或裁切边界；已消费固定底栏边距。
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime))
                    }
                }
            }
        }
        if(showSettings) SettingsPanel(vm,appearance,album,onClose={showSettings=false})
        if(!batch.ready) LaunchedEffect(batch.message) {snack.showSnackbar(batch.message)}
        if(BuildConfig.DEBUG && showReport) AlertDialog(onDismissRequest={showReport=false},title={Text("验证信息")},text={Text("解析、预览和保存均使用真实资源。保存结果以发布完成为准；可复制脱敏诊断反馈问题。")},
            confirmButton={TextButton(onClick={copyText(vm.report());showReport=false}) {Text("复制诊断")}},dismissButton={TextButton(onClick={showReport=false}) {Text("关闭")}})
        if(showSheet && batch.snapshot != null) DownloadSheet(batch,onDismiss={showSheet=false},onStop=vm::stopSaving,onRetry=::retry)
        if(state.previewId != null) PreviewDialog(vm,state)
    }
}

@Composable
private fun AppCard(content: @Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}

@Composable
private fun InputCard(vm: ProbeViewModel, state: ReaderState, onParse: ()->Unit, onInputFocus: (Boolean)->Unit, onInputBounds: (Rect)->Unit) {
    var focused by remember { mutableStateOf(false) }
    AppCard {
        OutlinedTextField(value=state.parse.input,onValueChange=vm::editInput,modifier=Modifier.fillMaxWidth()
            .onFocusChanged {focused=it.isFocused;onInputFocus(it.isFocused)}
            .onGloballyPositioned {val position=it.positionInRoot();onInputBounds(Rect(position.x,position.y,position.x+it.size.width,position.y+it.size.height))},
            placeholder={if(!focused) Text("请粘贴帖子链接")},minLines=2,maxLines=4,shape=RoundedCornerShape(12.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            if(state.parse.busy) OutlinedButton(onClick=vm::cancel) {Text("取消解析")}
            Spacer(Modifier.weight(1f))
            Button(onClick=onParse,enabled=!state.parse.busy || state.parse.input != state.parse.requestInput) {Text("解析")}
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MediaTile(vm: ProbeViewModel, media: ProbeMedia, selected: Boolean, modifier: Modifier, bounds: (String,Rect?)->Unit) {
    DisposableEffect(media.id) { onDispose { bounds(media.id,null) } }
    Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .onGloballyPositioned { if(media.candidate != null) bounds(media.id,it.boundsInRoot()) }.clickable {vm.preview(media.id)}) {
            NetworkImage(media.preview?.url ?: media.candidate?.takeIf {media.kind != MediaKind.VIDEO}?.url,vm,false,Modifier.fillMaxSize())
            if(media.kind == MediaKind.VIDEO) Text("▶",fontSize=26.sp,color=Color.White,modifier=Modifier.align(Alignment.Center).background(Color.Black.copy(alpha=.45f),CircleShape).padding(8.dp))
            // 视觉标记缩小，点击区域仍保留 48dp，避免大圆遮挡缩略图。
            Box(Modifier.align(Alignment.TopEnd).size(48.dp).toggleable(value=selected,enabled=media.candidate != null,
                role=Role.Checkbox,onValueChange={vm.setSelected(media.id,it)}),contentAlignment=Alignment.Center) {
                Box(Modifier.align(Alignment.TopEnd).offset((-4).dp,4.dp).size(24.dp).clip(RoundedCornerShape(5.dp))
                    .background(if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha=.9f))
                    .border(1.dp,MaterialTheme.colorScheme.outline,RoundedCornerShape(5.dp)),contentAlignment=Alignment.Center) {
                    val checkColor=MaterialTheme.colorScheme.onPrimary
                    if(selected) Canvas(Modifier.size(14.dp)) {
                        drawLine(checkColor,Offset(size.width*.1f,size.height*.5f),Offset(size.width*.4f,size.height*.8f),strokeWidth=2.dp.toPx())
                        drawLine(checkColor,Offset(size.width*.4f,size.height*.8f),Offset(size.width*.9f,size.height*.2f),strokeWidth=2.dp.toPx())
                    }
                }
            }
        }
        Text(when(media.kind) {MediaKind.IMAGE -> "图片";MediaKind.VIDEO -> "视频";MediaKind.LIVE_PHOTO -> "实况 · 仅静态"},
            fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        media.durationSeconds?.let { seconds -> Text("${seconds / 60}:${(seconds % 60).toString().padStart(2,'0')}",fontSize=11.sp) }
        if(media.candidate == null) Text("暂不可保存",fontSize=11.sp,color=MaterialTheme.colorScheme.error)
    }
}

