package com.mxodk.weibosaver.ui

import com.mxodk.weibosaver.model.ProbeFailure

// 此处只管理媒体库中的相册目录名，不接收任意文件系统绝对路径。
object AlbumDirectory {
    const val DEFAULT = "WeiboSaver"
    fun validate(input: String): String {
        val name=input.trim()
        if (name.isEmpty() || name.length > 60 || name in setOf(".","..") || name.endsWith('.') ||
            name.any { it.code < 32 || it in "\\/:*?\"<>|" })
            throw ProbeFailure("INPUT","请输入 1～60 字的相册名称，不含路径分隔符或特殊符号")
        return name
    }
}
