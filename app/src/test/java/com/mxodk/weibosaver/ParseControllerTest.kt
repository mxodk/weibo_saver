package com.mxodk.weibosaver

import com.mxodk.weibosaver.model.*
import com.mxodk.weibosaver.weibo.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// 让合成加载器忽略协程取消并迟到返回，验证真实控制器不会接纳旧回调。
class ParseControllerTest {
    private fun await(condition:()->Boolean) {
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4)
        while(!condition()) { if(System.nanoTime()>deadline) fail("受控回调未按时完成"); Thread.sleep(5) }
    }
    private fun post(id:String)=ProbePost(id,emptyList(),bodyStatus=BodyStatus.COMPLETE,mediaStatus=MediaListStatus.COMPLETE)
    private fun race(oldFails:Boolean,newFinishesFirst:Boolean) {
        val dispatcher=Executors.newFixedThreadPool(3).asCoroutineDispatcher()
        val root=SupervisorJob(); val scope=CoroutineScope(root+dispatcher)
        val oldEntered=CountDownLatch(1); val oldRelease=CountDownLatch(1)
        val newEntered=CountDownLatch(1); val newRelease=CountDownLatch(1)
        try {
            val controller=ParseController(scope,dispatcher) { input,_->
                if(input=="old") {
                    oldEntered.countDown(); check(oldRelease.await(4,TimeUnit.SECONDS))
                    if(oldFails) throw ProbeFailure("OLD_ERROR","不得覆盖新请求")
                    post("old-post")
                } else { newEntered.countDown(); check(newRelease.await(4,TimeUnit.SECONDS)); post("new-post") }
            }
            controller.submit("old"); assertTrue(oldEntered.await(4,TimeUnit.SECONDS))
            controller.submit("new"); assertTrue(newEntered.await(4,TimeUnit.SECONDS))
            if(newFinishesFirst) {
                newRelease.countDown(); await { controller.state.value.post?.id=="new-post" && !controller.state.value.busy }
            }
            oldRelease.countDown()
            await { root.children.count() == if(newFinishesFirst) 0 else 1 }
            assertNull(controller.state.value.errorCode)
            if(!newFinishesFirst) {
                assertTrue("旧请求收尾不能清除新请求的加载状态",controller.state.value.busy)
                assertNull(controller.state.value.post)
                newRelease.countDown(); await { controller.state.value.post?.id=="new-post" && !controller.state.value.busy }
            }
            assertEquals("new-post",controller.state.value.post?.id); assertEquals(2L,controller.state.value.requestId)
            controller.cancel()
        } finally { oldRelease.countDown(); newRelease.countDown(); scope.cancel(); dispatcher.close() }
    }
    @Test fun staleSuccessAndFinallyCannotFinishNewLoading()=race(false,false)
    @Test fun staleFailureAndFinallyCannotFinishNewLoading()=race(true,false)
    @Test fun staleFailureAfterNewSuccessCannotReplaceResult()=race(true,true)
    @Test fun cancelPropagatesToRegisteredHttpCallAndDoesNotPublishLateResult() {
        val dispatcher=Executors.newFixedThreadPool(2).asCoroutineDispatcher(); val root=SupervisorJob()
        val scope=CoroutineScope(root+dispatcher); val registered=CountDownLatch(1); val release=CountDownLatch(1)
        val call=OkHttpClient().newCall(Request.Builder().url("https://weibo.com/").build()) // 不 execute，无网络。
        try {
            val controller=ParseController(scope,dispatcher) { _,token->
                token.register(call); registered.countDown(); check(release.await(4,TimeUnit.SECONDS)); post("late")
            }
            controller.submit("test"); assertTrue(registered.await(4,TimeUnit.SECONDS)); controller.cancel()
            assertTrue(call.isCanceled()); assertFalse(controller.state.value.busy)
            release.countDown(); await { root.children.none() }; assertNull(controller.state.value.post)
        } finally { release.countDown(); scope.cancel(); dispatcher.close() }
    }
    @Test fun invalidNewInputClearsOldResultAndDoesNotChooseOneOfMultiplePosts() {
        val dispatcher=Executors.newFixedThreadPool(2).asCoroutineDispatcher(); val scope=CoroutineScope(SupervisorJob()+dispatcher)
        try {
            val controller=ParseController(scope,dispatcher) { input,_->
                val target=LinkResolver.identify(input) as InputTarget.Direct
                post(target.reference.key)
            }
            controller.submit("9007199254740993"); await { controller.state.value.post!=null && !controller.state.value.busy }
            controller.submit("https://weibo.com/123/AbC123xyz https://weibo.com/123/Def456uvw")
            await { !controller.state.value.busy }
            assertNull(controller.state.value.post); assertEquals("MULTIPLE_LINKS",controller.state.value.errorCode)
        } finally { scope.cancel(); dispatcher.close() }
    }
}
