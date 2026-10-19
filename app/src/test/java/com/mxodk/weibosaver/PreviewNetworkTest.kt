package com.mxodk.weibosaver

import com.mxodk.weibosaver.core.LimitedPreviewBody
import com.mxodk.weibosaver.core.PreviewInterceptor
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy

class PreviewNetworkTest {
    private fun chain(initial: Request, respond: (Request)->Response): Interceptor.Chain = Proxy.newProxyInstance(
        Interceptor.Chain::class.java.classLoader,arrayOf(Interceptor.Chain::class.java)) { _,method,args -> when(method.name) {
            "request" -> initial
            "proceed" -> respond(args!![0] as Request)
            else -> throw UnsupportedOperationException(method.name)
        } } as Interceptor.Chain
    @Test fun redirectsAreRevalidatedBeforeAnySecondRequest() {
        var count=0
        val initial=Request.Builder().url("https://wx1.sinaimg.cn/large/a.jpg").build()
        try {
            PreviewInterceptor().intercept(chain(initial) { request ->
                count++
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(302).message("fixture")
                    .header("Location","https://127.0.0.1/private").body("".toResponseBody()).build()
            });fail("应拒绝未获允许的目的地")
        }catch(_:IOException){}
        assertEquals(1,count)
    }
    @Test fun previewRequestsRemoveCredentialsButKeepVideoRangeAndReferer() {
        val initial=Request.Builder().url("https://video.weibo.com/fixture.mp4").header("Cookie","synthetic")
            .header("Authorization","synthetic").header("Range","bytes=4-").build()
        PreviewInterceptor().intercept(chain(initial) { request ->
            assertNull(request.header("Cookie"));assertNull(request.header("Authorization"))
            assertEquals("bytes=4-",request.header("Range"));assertEquals("https://weibo.com/",request.header("Referer"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(206).message("fixture").body("fixture".toResponseBody()).build()
        }).close()
    }
    @Test fun imageBodyAtLimitCanBeReadAndClosed() {
        LimitedPreviewBody("1234".toResponseBody(),4).use { assertEquals("1234",it.string()) }
    }
    @Test fun chunkedReadsCannotExceedBudgetEvenWithUnknownLength() {
        val upstream=object:okhttp3.ResponseBody() {
            override fun contentLength() = -1L
            override fun contentType():okhttp3.MediaType? = null
            override fun source()=Buffer().writeUtf8("12345")
        }
        try {LimitedPreviewBody(upstream,4).use{it.bytes()};fail("应拒绝超预算响应")}catch(_:IOException){}
    }
}
