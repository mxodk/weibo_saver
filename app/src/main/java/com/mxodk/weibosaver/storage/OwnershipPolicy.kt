package com.mxodk.weibosaver.storage

// 将删除前的归属规则集中为纯函数，离线测试不能替代实际 MediaStore 查询。
object OwnershipPolicy {
    fun valid(entry: JournalEntry): Boolean = Regex("[a-f0-9-]{36}").matches(entry.key) &&
        entry.name.substringAfterLast('.') in setOf("jpg","png","gif","webp","mp4","mov") &&
        entry.treeUri == null && (
            (entry.name == "weibo_p2_${entry.key}." + entry.name.substringAfterLast('.') &&
             entry.path == (if(entry.video) "Movies/" else "Pictures/")+"WeiboSaver-P2/") ||
            (entry.name == "weibo_${entry.key}." + entry.name.substringAfterLast('.') &&
             entry.path == (if(entry.video) "Movies/" else "Pictures/")+"WeiboSaver/")
        )
    fun matches(entry: JournalEntry, name: String?, path: String?, owner: String?, packageName: String): Boolean =
        valid(entry) && entry.name == name && entry.path == path && owner == packageName
}
