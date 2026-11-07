package com.mxodk.weibosaver.ui

import android.view.LayoutInflater
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.mxodk.weibosaver.R
import com.mxodk.weibosaver.entry.ProbeViewModel
import com.mxodk.weibosaver.model.*

@Composable
private fun lifecycleStarted(): Boolean {
    val owner=LocalLifecycleOwner.current
    var active by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,_ -> active=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return active
}

@Composable
fun NetworkImage(url: String?, vm: ProbeViewModel, full: Boolean, modifier: Modifier = Modifier,
                 onImageSize: (Int, Int) -> Unit = { _, _ -> }) {
    var attempt by remember(url) { mutableIntStateOf(0) }
    val context = LocalContext.current
    if(!lifecycleStarted()) { Box(modifier);return }
    if (url == null) Box(modifier,contentAlignment=Alignment.Center) { Text("暂无预览",style=MaterialTheme.typography.labelSmall) }
    else key(url,attempt) {
        SubcomposeAsyncImage(model=ImageRequest.Builder(context).data(url).size(if(full) 2048 else 384).build(),
            onSuccess={ onImageSize(it.result.image.width, it.result.image.height) },
            imageLoader=vm.imageLoader,contentDescription=if(full) "当前图片大图" else "媒体缩略图",
            contentScale=if(full) ContentScale.Fit else ContentScale.Crop,modifier=modifier,
            loading={ Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } },
            error={ Column(Modifier.fillMaxSize().padding(4.dp),horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.Center) {
                Text("图片加载失败",color=MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.labelSmall)
                FilledTonalButton(onClick={attempt++}) { Text("重新加载") }
            } })
    }
}

@Composable
fun PreviewDialog(vm: ProbeViewModel, state: ReaderState, saveStill: (String) -> Unit) {
    val post = state.parse.post ?: return
    val index = post.media.indexOfFirst { it.id == state.previewId }
    if(index < 0) return
    val pager=rememberPagerState(initialPage=index,pageCount={post.media.size})
    var zoomed by remember {mutableStateOf(false)}
    val media=post.media[pager.currentPage]
    var toolsVisible by remember { mutableStateOf(true) }
    var resetZoom by remember { mutableIntStateOf(0) }
    val showSwipeHint by vm.previewSwipeHint.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { if(post.media.size>1) vm.dismissPreviewHint() } }
    LaunchedEffect(pager.currentPage) {
        zoomed=false
        toolsVisible=true
        vm.preview(post.media[pager.currentPage].id)
    }
    Dialog(onDismissRequest={vm.preview(null)},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val view=LocalView.current
        val light=MaterialTheme.colorScheme.background.luminance() > .5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window,view).apply {
                    isAppearanceLightStatusBars=light;isAppearanceLightNavigationBars=light
                }
            }
        }
        Surface(color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground) {
            BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
                val toolbarLimit = maxHeight * .45f
                val density = LocalDensity.current
                var topToolbarHeight by remember { mutableIntStateOf(0) }
                var bottomToolbarHeight by remember { mutableIntStateOf(0) }
                val navigationBottom = WindowInsets.navigationBars.getBottom(density)
                val hasBottomToolbar = media.kind==MediaKind.LIVE_PHOTO || zoomed || (post.media.size>1 && showSwipeHint)
                // 图片占用完整视口；工具栏单独绘制，缩放不会改变其位置和尺寸。
                HorizontalPager(state=pager, userScrollEnabled=!zoomed, modifier=Modifier.fillMaxSize(),
                    key={post.media[it].id}) { page ->
                    val item=post.media[page]
                    if(item.kind==MediaKind.VIDEO) VideoPreview(item,vm,Modifier.fillMaxSize()
                        .padding(top=with(density) {topToolbarHeight.toDp()},
                            bottom=with(density) {if(hasBottomToolbar) bottomToolbarHeight.toDp() else navigationBottom.toDp()})
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                        visible=page==pager.currentPage && !pager.isScrollInProgress)
                    else ZoomableImage(item, vm, current=page==pager.currentPage, reset=resetZoom,
                        onTap={ toolsVisible=!toolsVisible },
                        onZoomChanged={ if(page==pager.currentPage) zoomed=it })
                }
                if(toolsVisible || media.kind==MediaKind.VIDEO) {
                    Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged {topToolbarHeight=it.height}, color=MaterialTheme.colorScheme.surface) {
                        Row(Modifier.statusBarsPadding().padding(horizontal=12.dp, vertical=4.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            FilledTonalButton(onClick={vm.preview(null)}) {
                                Text("关闭")
                            }
                            Text("${pager.currentPage+1} / ${post.media.size}", Modifier.padding(start=16.dp),
                                color=MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if(hasBottomToolbar) {
                        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged {bottomToolbarHeight=it.height}, color=MaterialTheme.colorScheme.surface) {
                            Column(Modifier.heightIn(max=toolbarLimit).verticalScroll(rememberScrollState())
                                .navigationBarsPadding().padding(12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                if(media.kind==MediaKind.LIVE_PHOTO) {
                                    Text("实况照片 · 当前显示静态画面", color=MaterialTheme.colorScheme.onSurfaceVariant,
                                        style=MaterialTheme.typography.bodySmall)
                                }
                                PreviewActions(zoomed=zoomed, live=media.kind==MediaKind.LIVE_PHOTO,
                                    canSave=media.candidate!=null, onReset={resetZoom++}, onSave={saveStill(media.id)})
                                if(post.media.size>1 && (zoomed || showSwipeHint)) {
                                    Text(if(zoomed) "还原缩放后可切换" else "左右滑动切换",
                                        style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewActions(zoomed: Boolean, live: Boolean, canSave: Boolean, onReset: () -> Unit, onSave: () -> Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(live) Button(onClick=onSave, enabled=canSave) { Text("仅保存静态图片") }
        if(zoomed) OutlinedButton(onClick=onReset) { Text("还原缩放") }
    }
}

@Composable
private fun ZoomableImage(media: ProbeMedia, vm: ProbeViewModel, current: Boolean, reset: Int,
                          onTap: () -> Unit, onZoomChanged: (Boolean) -> Unit) {
    var zoom by remember(media.id) { mutableFloatStateOf(1f) }
    var offset by remember(media.id) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember(media.id) { mutableStateOf(IntSize.Zero) }
    val notify by rememberUpdatedState(onZoomChanged)
    val tap by rememberUpdatedState(onTap)
    fun constrain(value: Offset, scale: Float): Offset {
        val bounds = imagePanBounds(viewport.width, viewport.height, imageSize.width, imageSize.height, scale)
        return Offset(value.x.coerceIn(-bounds.first, bounds.first), value.y.coerceIn(-bounds.second, bounds.second))
    }
    LaunchedEffect(current, reset) {
        zoom=1f
        offset=Offset.Zero
        if(current) notify(false)
    }
    LaunchedEffect(viewport, imageSize) { offset=constrain(offset, zoom) }
    val gestures=rememberTransformableState { change, pan, _ ->
        if(imageSize.width>0 && imageSize.height>0) {
            zoom=(zoom*change).coerceIn(1f,5f)
            if(zoom<=1.01f) zoom=1f
            offset=constrain(offset+pan, zoom)
            notify(zoom>1f)
        }
    }
    // 原始比例由 Pager 处理横滑，放大后由图片处理平移。双击以点击位置为中心放大。
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewport=it }
        .pointerInput(media.id) {
            detectTapGestures(onTap={tap()}, onDoubleTap={ position ->
                if(imageSize.width>0 && imageSize.height>0) {
                    val next=if(zoom>1f) 1f else 2.5f
                    val center=Offset(viewport.width/2f, viewport.height/2f)
                    offset=if(next==1f) Offset.Zero else constrain((center-position)*(next-1f),next)
                    zoom=next
                    notify(zoom>1f)
                }
            })
        }.transformable(gestures, canPan={zoom>1f}), contentAlignment=Alignment.Center) {
        NetworkImage(media.candidate?.url ?: media.preview?.url, vm, true,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX=zoom; scaleY=zoom; translationX=offset.x; translationY=offset.y
            }, onImageSize={width,height -> imageSize=IntSize(width,height)})
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPreview(media: ProbeMedia, vm: ProbeViewModel, modifier: Modifier, visible: Boolean) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    var active by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var position by remember(media.id) { mutableLongStateOf(0L) }
    var attempt by remember(media.id) { mutableIntStateOf(0) }
    var failed by remember(media.id,attempt) { mutableStateOf(false) }
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,_ -> active=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Box(modifier,contentAlignment=Alignment.Center) {
        val url=media.candidate?.url
        if(url == null) Text("当前没有可播放的视频资源")
        else if(failed) Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Text("视频加载失败",color=MaterialTheme.colorScheme.onBackground)
            Button(onClick={attempt++}) { Text("重新加载") }
        } else if(active && visible) key(media.id,attempt) {
            // 只有可见的当前视频持有播放器；切后台、关闭和切项均释放连接与解码器。
            val player=remember {
                ExoPlayer.Builder(context).build().apply {
                    val source=ProgressiveMediaSource.Factory(OkHttpDataSource.Factory(vm.videoClient))
                        .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(0))
                        .createMediaSource(MediaItem.fromUri(url))
                    setMediaSource(source);seekTo(position);playWhenReady=false
                    setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT,true)
                    addListener(object:Player.Listener {
                        override fun onPlayerError(error: PlaybackException) { failed=true }
                    })
                    prepare()
                }
            }
            DisposableEffect(player) { onDispose { position=player.currentPosition;player.release() } }
            AndroidView(factory={ LayoutInflater.from(it).inflate(R.layout.video_player,null) as PlayerView },
                update={it.player=player},modifier=Modifier.fillMaxSize(),onRelease={it.player=null})
        } else if(!visible) Box(Modifier.fillMaxSize())
        else Text("返回应用后可继续预览")
    }
}
