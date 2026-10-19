package com.mxodk.weibosaver

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element

// 用纯 JVM 检查 P2 服务与权限边界，不连接设备或真实微博。
class FoundationManifestTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"
    private fun manifest() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(File(requireNotNull(System.getProperty("app.manifest")) { "缺少本地清单路径配置" }))

    @Test
    fun launcherHasExactlyOneExplicitlyExportedActivity() {
        val document = manifest()
        val activities = document.getElementsByTagName("activity")
        assertEquals(1, activities.length)
        val activity = activities.item(0) as Element
        assertEquals(".entry.MainActivity", activity.getAttributeNS(androidNamespace, "name"))
        assertEquals("true", activity.getAttributeNS(androidNamespace, "exported"))
        assertEquals("singleTop",activity.getAttributeNS(androidNamespace,"launchMode"))
        val filters = activity.getElementsByTagName("intent-filter")
        assertEquals(1,filters.length)
        val launcher = filters.item(0) as Element
        val actions = launcher.getElementsByTagName("action")
        val categories = launcher.getElementsByTagName("category")
        assertEquals(1, actions.length)
        assertEquals(1, categories.length)
        assertEquals("android.intent.action.MAIN", (actions.item(0) as Element).getAttributeNS(androidNamespace, "name"))
        assertEquals("android.intent.category.LAUNCHER", (categories.item(0) as Element).getAttributeNS(androidNamespace, "name"))

    }

    @Test
    fun validationDeclaresOnlyRequiredPermissionsAndPrivateDataSyncService() {
        val document = manifest()
        val permissions = document.getElementsByTagName("uses-permission")
        val actual = (0 until permissions.length).map { (permissions.item(it) as Element).getAttributeNS(androidNamespace,"name") }.toSet()
        assertEquals(setOf("android.permission.INTERNET", "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC", "android.permission.POST_NOTIFICATIONS", "android.permission.WAKE_LOCK"), actual)
        val services = document.getElementsByTagName("service")
        assertEquals(1, services.length)
        val service = services.item(0) as Element
        assertEquals(".download.DownloadService",service.getAttributeNS(androidNamespace,"name"))
        assertEquals("false",service.getAttributeNS(androidNamespace,"exported"))
        assertEquals("false",service.getAttributeNS(androidNamespace,"stopWithTask"))
        assertEquals("dataSync",service.getAttributeNS(androidNamespace,"foregroundServiceType"))
        for (component in listOf("receiver", "provider")) {
            assertEquals(component, 0, document.getElementsByTagName(component).length)
        }
        val application = document.getElementsByTagName("application").item(0) as Element
        assertFalse(application.getAttributeNS(androidNamespace, "allowBackup").toBoolean())
    }
}
