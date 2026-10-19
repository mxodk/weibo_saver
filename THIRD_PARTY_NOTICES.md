# 第三方组件与声明

清单依据当前 Gradle 解析图、本地发布 POM（含父 POM 继承）和 JAR/AAR 自带声明生成。
包含运行、测试、BOM 与变体组件，不代表表中每项都会打入 APK。构建工具另见文末。

| 组件 | 发布元数据声明的许可证 | 范围 | 随归档声明 |
| --- | --- | --- | --- |
| `androidx.activity:activity-compose:1.11.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.activity:activity-ktx:1.11.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.activity:activity:1.11.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.annotation:annotation-experimental:1.4.1` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.annotation:annotation-jvm:1.9.1` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.annotation:annotation:1.9.1` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.appcompat:appcompat-resources:1.7.1` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.arch.core:core-common:2.2.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.arch.core:core-runtime:2.2.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.autofill:autofill:1.0.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.collection:collection-jvm:1.5.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.collection:collection-ktx:1.5.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.collection:collection:1.5.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.compose.animation:animation-android:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.compose.animation:animation-core-android:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.compose.animation:animation-core:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.compose.animation:animation:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.compose.foundation:foundation-android:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.compose.foundation:foundation-layout-android:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.compose.foundation:foundation-layout:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.compose.foundation:foundation:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.compose.material3:material3-android:1.4.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.compose.material3:material3:1.4.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.compose.material:material-ripple-android:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.compose.material:material-ripple:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.compose.runtime:runtime-android:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.compose.runtime:runtime-annotation-android:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.compose.runtime:runtime-annotation:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.compose.runtime:runtime-saveable-android:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.compose.runtime:runtime-saveable:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.compose.runtime:runtime:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.compose.ui:ui-android:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.compose.ui:ui-geometry-android:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.compose.ui:ui-geometry:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.compose.ui:ui-graphics-android:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.compose.ui:ui-graphics:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.compose.ui:ui-text-android:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.compose.ui:ui-text:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.compose.ui:ui-unit-android:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.compose.ui:ui-unit:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.compose.ui:ui-util-android:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.compose.ui:ui-util:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.compose.ui:ui:1.9.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.compose:compose-bom:2025.10.00` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.concurrent:concurrent-futures:1.1.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.core:core-ktx:1.15.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.core:core-viewtree:1.0.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.core:core:1.15.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.customview:customview-poolingcontainer:1.0.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.customview:customview:1.0.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.emoji2:emoji2:1.4.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.exifinterface:exifinterface:1.4.1` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.graphics:graphics-path:1.0.1` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.interpolator:interpolator:1.0.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.lifecycle:lifecycle-common-java8:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.lifecycle:lifecycle-common-jvm:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.lifecycle:lifecycle-common:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.lifecycle:lifecycle-livedata-core:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.lifecycle:lifecycle-process:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.lifecycle:lifecycle-runtime-android:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.lifecycle:lifecycle-runtime-compose-android:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.lifecycle:lifecycle-runtime-compose:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.lifecycle:lifecycle-runtime-ktx-android:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.lifecycle:lifecycle-runtime-ktx:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.lifecycle:lifecycle-runtime:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.lifecycle:lifecycle-viewmodel-android:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.lifecycle:lifecycle-viewmodel-compose-android:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.lifecycle:lifecycle-viewmodel:2.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.media3:media3-common:1.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.media3:media3-container:1.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.media3:media3-database:1.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.media3:media3-datasource-okhttp:1.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.media3:media3-datasource:1.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.media3:media3-decoder:1.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.media3:media3-exoplayer:1.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.media3:media3-extractor:1.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.media3:media3-ui:1.9.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.profileinstaller:profileinstaller:1.4.1` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.recyclerview:recyclerview:1.3.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.savedstate:savedstate-android:1.3.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.savedstate:savedstate-compose-android:1.3.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.savedstate:savedstate-compose:1.3.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.savedstate:savedstate-ktx:1.3.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.savedstate:savedstate:1.3.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `androidx.startup:startup-runtime:1.2.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | [声明 1](third_party/notices/809fa1ed21450f59827d1e9aec720bbc4b687434fa22283c6cb5dd82a47ab9c0.txt) |
| `androidx.tracing:tracing:1.2.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.vectordrawable:vectordrawable-animated:1.1.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.vectordrawable:vectordrawable:1.1.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `androidx.versionedparcelable:versionedparcelable:1.1.1` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `com.google.accompanist:accompanist-drawablepainter:0.37.3` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `com.google.guava:failureaccess:1.0.2` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `com.google.guava:guava:33.3.1-android` | [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `com.squareup.okhttp3:okhttp-android:5.3.2` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `com.squareup.okhttp3:okhttp:5.3.2` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `com.squareup.okio:okio-jvm:3.16.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `com.squareup.okio:okio:3.16.4` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `io.coil-kt.coil3:coil-android:3.3.0` | [The Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `io.coil-kt.coil3:coil-compose-android:3.3.0` | [The Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `io.coil-kt.coil3:coil-compose-core-android:3.3.0` | [The Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `io.coil-kt.coil3:coil-compose-core:3.3.0` | [The Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `io.coil-kt.coil3:coil-compose:3.3.0` | [The Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `io.coil-kt.coil3:coil-core-android:3.3.0` | [The Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `io.coil-kt.coil3:coil-core:3.3.0` | [The Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `io.coil-kt.coil3:coil-network-core-android:3.3.0` | [The Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `io.coil-kt.coil3:coil-network-core:3.3.0` | [The Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `io.coil-kt.coil3:coil-network-okhttp-jvm:3.3.0` | [The Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `io.coil-kt.coil3:coil-network-okhttp:3.3.0` | [The Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `io.coil-kt.coil3:coil:3.3.0` | [The Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `junit:junit:4.13.2` | [Eclipse Public License 1.0](http://www.eclipse.org/legal/epl-v10.html) | 仅测试 | [声明 1](third_party/notices/9648bb2891b9813970bddb68d4be8a5e6ec8280d0180a53dfb29236b579c55bb.txt) |
| `org.hamcrest:hamcrest-core:1.3` | [New BSD License](http://www.opensource.org/licenses/bsd-license.php) | 仅测试 | [声明 1](third_party/notices/1cde867cab5c8e842929da5391cef98b4017314822270e934e8e2eef3767c666.txt) |
| `org.jetbrains.androidx.lifecycle:lifecycle-common:2.8.4` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.8.4` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.compose.animation:animation-core:1.8.2` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.compose.animation:animation:1.8.2` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.compose.annotation-internal:annotation:1.8.2` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.compose.collection-internal:collection:1.8.2` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.compose.foundation:foundation-layout:1.8.2` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.compose.foundation:foundation:1.8.2` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.compose.runtime:runtime-saveable:1.8.2` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.compose.runtime:runtime:1.8.2` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.compose.ui:ui-geometry:1.8.2` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.compose.ui:ui-graphics:1.8.2` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.compose.ui:ui-text:1.8.2` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.compose.ui:ui-unit:1.8.2` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.compose.ui:ui-util:1.8.2` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.compose.ui:ui:1.8.2` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.kotlin:kotlin-bom:1.8.0` | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.kotlin:kotlin-stdlib:2.2.21` | [Apache-2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2` | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.2` | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2` | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2` | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom:1.9.0` | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.9.0` | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0` | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0` | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0` | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行（平台／元数据） | 使用发布许可证正文 |
| `org.jetbrains:annotations:23.0.0` | [The Apache Software License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |
| `org.jsoup:jsoup:1.22.2` | [The MIT License](https://jsoup.org/license) | 运行 | [声明 1](third_party/notices/f5d724c5818010c61bff2e177f5b6452434bc054807522bf241940bca8b1d6b1.txt) |
| `org.jspecify:jspecify:1.0.0` | [The Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt) | 运行 | 使用发布许可证正文 |

机器可读版本、POM 摘要和归档内声明位置见 [dependencies.json](third_party/dependencies.json)。
公共许可证正文见 [third_party/licenses/](third_party/licenses/)。运行所需声明与本项目 MIT 正文同时放入 APK 的 `assets/open_source_notices.txt`。

构建工具：Gradle 8.13／Wrapper、Android Gradle Plugin 8.13.2、Kotlin／Compose Compiler 2.2.21 使用 Apache-2.0；
JDK、Android SDK 由构建者单独安装，不随仓库或 APK 分发，保留各自工具包原有许可。

技术研究参考（不作为 APK 依赖）：
- [weibo-image-downloader 固定版本](https://github.com/gbandszxc/weibo-image-downloader/tree/cc41dc1279ebe0c1c55cdf0ac323075a13de562b)：参考媒体字段和获取路径。完整固定版本归档无根 LICENSE／COPYING；但 [发布脚本](https://github.com/gbandszxc/weibo-image-downloader/blob/cc41dc1279ebe0c1c55cdf0ac323075a13de562b/dist/weibo-image-downloader.user.js) 与 [构建元数据](https://github.com/gbandszxc/weibo-image-downloader/blob/cc41dc1279ebe0c1c55cdf0ac323075a13de562b/scripts/build.mjs) 明确标记 @license MIT，不能将“根 LICENSE 缺失”写成“没有任何许可证声明”。发布物内另一份 MIT 正文属于 gifenc，不能冒充项目源码许可证。当前项目没有纳入该发布物或 gifenc。
- [HIKARI_BOT_NEO 固定版本](https://github.com/higashitaniyume/HIKARI_BOT_NEO/tree/5b30566e9af5f01bb6b9f348979c8481a37ec073)：参考访客接口和详情字段原理；该项目 LICENSE 为 AGPL-3.0。当前应用没有使用其 Python 示例的主页 XSRF 补全、正则清理 HTML、页面 HTML 抓取或媒体 URL 汇总实现。
- 本次固定版本技术对照未识别出直接复制或逐段翻译的实现片段：共同内容为接口地址、回调名及响应字段；本项目自行实现会话边界、身份校验、候选排序、错误状态和稳定 ID 补充。保留研究来源致谢，本项目 MIT 不变，不将阅读参考资料等同于引入上游源码。此为注明范围的技术核对，不是对所有著作权问题的法律担保；后续若复制、修改或翻译上游代码，应重新核对授权和声明。
