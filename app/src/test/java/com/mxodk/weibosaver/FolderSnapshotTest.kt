package com.mxodk.weibosaver

import com.mxodk.weibosaver.ui.*
import com.mxodk.weibosaver.model.*
import org.junit.Assert.*
import org.junit.Test

// 只验证目录身份隔离；系统选择器、持久授权和实际写入仍由真机验收。
class FolderSnapshotTest {
    private fun snapshot()=SaveSnapshot(1,"post",SaveChoice.ALL,listOf(ProbeMedia("v",MediaKind.VIDEO,
        ResourceCandidate("https://wx1.sinaimg.cn/synthetic.jpg","fixture"))),folder=SaveFolder("content://synthetic/old","旧目录"))
    @Test fun changingSettingsKeepsOriginalRetryDirectory() {
        var batch=DemoReducer.begin(DemoBatch(),snapshot())
        repeat(2) {batch=DemoReducer.step(batch)}
        val retry=DemoReducer.retry(batch)
        assertEquals("content://synthetic/old",retry.snapshot?.folder?.treeUri)
        assertEquals(setOf("content://synthetic/new","content://synthetic/old"),
            retainedFolderUris(SaveFolder("content://synthetic/new","新目录"),retry.snapshot))
    }
    @Test fun restoringDefaultKeepsOldGrantUntilBatchIsReplaced() {
        assertEquals(setOf("content://synthetic/old"),retainedFolderUris(SaveFolder(),snapshot()))
        assertTrue(retainedFolderUris(SaveFolder(),null).isEmpty())
    }
    @Test fun replacingBatchOnlyRetainsNewSettingAndNewSnapshot() {
        val next=snapshot().copy(folder=SaveFolder("content://synthetic/new","新目录"))
        assertEquals(setOf("content://synthetic/new"),retainedFolderUris(next.folder,next))
    }
}
