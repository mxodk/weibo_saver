# 构建说明

## 环境

| 工具 | 版本 |
| --- | --- |
| JDK | 17 |
| Android SDK Platform | 36 |
| Android SDK Build Tools | 35.0.0 |
| Gradle | 8.13，使用仓库内 Wrapper |
| Android Gradle Plugin | 8.13.2 |
| Kotlin / Compose Compiler | 2.2.21 |

可通过 Android Studio 的 SDK Manager 或 Android 命令行工具安装 SDK，并按提示接受其条款。设置 `JAVA_HOME` 指向 JDK 17。设置 `ANDROID_HOME` 指向 SDK，或在项目根目录创建不提交的 `local.properties`：

```properties
sdk.dir=C\:/Android/Sdk
```

请替换成自己的 SDK 路径。首次构建需要联网下载 Gradle 和 Maven 依赖；构建和本地单元测试不需要访问真实微博。

## 构建、测试和静态检查

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

macOS / Linux：

```sh
chmod +x gradlew
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

依赖已完整缓存后，可在命令末尾加 `--offline`。仓库不携带 SDK、JDK 或依赖缓存，因此全新环境首次构建不能保证离线成功。

## 输出与安装

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。
- 单元测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`。
- Lint 报告：`app/build/reports/lint-results-debug.html`。

可将 APK 传到手机，允许对应来源安装后打开。应用 ID 为 `com.mxodk.weibosaver`，启动器显示名称为“微博下载器”。不同电脑的默认开发签名可能不同，不能保证覆盖安装；卸载会丢失应用设置和授权，请先确认已有安装及数据需求。

当前构建为 `1.0.0`，版本号 18，最低 API 31、目标 API 36。Debug APK 用于开发验证；正式密钥不随仓库分发。执行 `:app:assembleRelease` 可生成 `app/build/outputs/apk/release/app-release-unsigned.apk`，需使用构建者自己的密钥签名后才能安装。本地构建和单元测试通过不代表各系统的后台、媒体库或兼容性均已验收。已知限制见 [README.md](README.md)。
