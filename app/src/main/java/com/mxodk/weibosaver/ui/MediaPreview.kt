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
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
fun NetworkImage(url: String?, vm: ProbeViewModel, full: Boolean, modifier: Modifier = Modifier) {
    var attempt by remember(url) { mutableIntStateOf(0) }
    val context = LocalContext.current
    if(!lifecycleStarted()) { Box(modifier);return }
    if (url == null) Box(modifier,contentAlignment=Alignment.Center) { Text("暂无预览",style=MaterialTheme.typography.labelSmall) }
    else key(url,attempt) {
        SubcomposeAsyncImage(model=ImageRequest.Builder(context).data(url).size(if(full) 2048 else 384).build(),
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
fun PreviewDialog(vm: ProbeViewModel, state: ReaderState) {
    val post = state.parse.post ?: return
    val index = post.media.indexOfFirst { it.id == state.previewId }
    if(index < 0) return
    val pager=rememberPagerState(initialPage=index,pageCount={post.media.size})
    var zoomed by remember {mutableStateOf(false)}
    val media=post.media[pager.currentPage]
    val showSwipeHint by vm.previewSwipeHint.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { if(post.media.size>1) vm.dismissPreviewHint() } }
    LaunchedEffect(pager.currentPage) {
        zoomed=false
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
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(12.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    // 左内边距移到右侧，让关闭文字与媒体视口左边缘对齐，保留点击范围及序号位置。
                    TextButton(onClick={vm.preview(null)},modifier=Modifier.heightIn(min=56.dp),contentPadding=PaddingValues(start=0.dp,end=36.dp,top=10.dp,bottom=10.dp)) {
                        Text("关闭",fontSize=20.sp)
                    }
                    Text("${pager.currentPage+1} / ${post.media.size}",Modifier.weight(1f),color=MaterialTheme.colorScheme.onBackground)
                }
                HorizontalPager(state=pager,userScrollEnabled=!zoomed,modifier=Modifier.weight(1f).fillMaxWidth(),
                    key={post.media[it].id}) { page ->
                    val item=post.media[page]
                    if(item.kind==MediaKind.VIDEO) VideoPreview(item,vm,Modifier.fillMaxSize(),visible=page==pager.currentPage && !pager.isScrollInProgress)
                    else ZoomableImage(item,vm,current=page==pager.currentPage,onZoomChanged={if(page==pager.currentPage) zoomed=it})
                }
                if(media.kind == MediaKind.LIVE_PHOTO) Text("Live Photo · 仅静态图片",color=MaterialTheme.colorScheme.primary)
                if(post.media.size>1 && (zoomed || showSwipeHint)) Text(if(zoomed) "还原缩放后可切换" else "左右滑动切换",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ZoomableImage(media: ProbeMedia, vm: ProbeViewModel, current: Boolean, onZoomChanged: (Boolean)->Unit) {
    var zoom by remember {mutableFloatStateOf(1f)}
    var offsetX by remember {mutableFloatStateOf(0f)}
    var offsetY by remember {mutableFloatStateOf(0f)}
    val notify by rememberUpdatedState(onZoomChanged)
    LaunchedEffect(current) {if(!current) {zoom=1f;offsetX=0f;offsetY=0f}}
    val gestures=rememberTransformableState {change,pan,_ ->
        zoom=(zoom*change).coerceIn(1f,5f)
        if(zoom<=1.01f) {zoom=1f;offsetX=0f;offsetY=0f}
        else {offsetX=(offsetX+pan.x).coerceIn(-1600f,1600f);offsetY=(offsetY+pan.y).coerceIn(-1600f,1600f)}
        notify(zoom>1f)
    }
    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally) {
        // 原尺寸时把单指横滑交给 Pager，双指缩放和平移在图片组件处理。
        Box(Modifier.weight(1f).fillMaxWidth().transformable(gestures,canPan={zoom>1f}),contentAlignment=Alignment.Center) {
            NetworkImage(media.candidate?.url ?: media.preview?.url,vm,true,Modifier.fillMaxSize().graphicsLayer {
                scaleX=zoom;scaleY=zoom;translationX=offsetX;translationY=offsetY
            })
        }
        TextButton(onClick={zoom=1f;offsetX=0f;offsetY=0f;notify(false)}) {Text("还原缩放")}
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
