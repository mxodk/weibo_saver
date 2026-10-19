package com.mxodk.weibosaver.weibo

import com.mxodk.weibosaver.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CancellationException

data class ParseState(val input: String = "", val requestInput: String = "", val busy: Boolean = false,
    val post: ProbePost? = null, val message: String = "请输入一个帖子链接或带链接的文字", val requestId: Long = 0,
    val source: String = "手动", val errorCode: String? = null)

// 与 Android 解耦；每个成功、失败及收尾写入均在同一锁内核对序号。
class ParseController(private val scope: CoroutineScope, private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
                      private val loader: (String,OperationToken)->ProbePost) {
    private val mutable = MutableStateFlow(ParseState())
    val state = mutable.asStateFlow()
    private var sequence = 0L
    private var job: Job? = null
    private var token: OperationToken? = null
    @Synchronized fun edit(input: String) { mutable.value = mutable.value.copy(input = input) }
    @Synchronized private fun write(current: Long, change: (ParseState)->ParseState) {
        if (current == sequence) mutable.value = change(mutable.value)
    }
    @Synchronized fun submit(input: String, source: String = "手动") {
        token?.cancel(); job?.cancel()
        val current = ++sequence; val currentToken = OperationToken(); token = currentToken
        mutable.value = ParseState(input,input,true,message="正在识别输入并解析当前帖…",requestId=current,source=source)
        job = scope.launch {
            try {
                val post = withContext(dispatcher) { loader(input,currentToken) }
                currentToken.check()
                write(current) { it.copy(post=post,message=if (post.bodyStatus == BodyStatus.COMPLETE && post.mediaStatus == MediaListStatus.COMPLETE && !post.needsSupplement)
                    "当前帖解析完成；文件保存仍需单独操作" else "已返回部分内容，请查看各项完整性说明") }
            } catch (_: CancellationException) { /* 取消不转换成网络失败。 */ }
            catch (error: Exception) {
                write(current) { it.copy(post=null,errorCode=if(error is ProbeFailure) error.category else "NETWORK_OR_RESPONSE",
                    message=if(error is ProbeFailure) error.message else "请求未完成，可能是网络或上游结构变化；可修改输入或重试") }
            } finally { write(current) { it.copy(busy=false) } }
        }
    }
    @Synchronized fun cancel() {
        ++sequence; token?.cancel(); job?.cancel()
        mutable.value = mutable.value.copy(busy=false,post=null,message="已取消解析",requestId=sequence,errorCode=null)
    }
}
