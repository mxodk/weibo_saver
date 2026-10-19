plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mxodk.weibosaver"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mxodk.weibosaver"
        // Android 12 是最低支持目标，不能随编译 SDK 升级而提高。
        minSdk = 31
        targetSdk = 36
        versionCode = 18
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions {
        unitTests.all { test ->
            // JVM 测试只读取本地清单，不访问设备或真实微博。
            test.systemProperty("app.manifest", file("src/main/AndroidManifest.xml").absolutePath)
        }
    }
}
kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("androidx.media3:media3-exoplayer:1.9.4")
    implementation("androidx.media3:media3-ui:1.9.4")
    implementation("androidx.media3:media3-datasource-okhttp:1.9.4")
    testImplementation("junit:junit:4.13.2")
}
