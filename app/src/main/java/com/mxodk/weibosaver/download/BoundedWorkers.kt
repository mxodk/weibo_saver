package com.mxodk.weibosaver.download

import kotlinx.coroutines.*

// 每个工作者串行领取；阻塞传输运行于 IO，异常由单项处理，取消不再领取。
suspend fun runBoundedWorkers(count: Int, work: suspend (worker: Int)->Unit) = coroutineScope {
    repeat(minOf(2,count)) {worker->launch(Dispatchers.IO) {work(worker)}}
}
