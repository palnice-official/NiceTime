dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            library("androidx-core-ktx", "androidx.core:core-ktx:1.13.1")
            library("androidx-lifecycle-runtime-ktx", "androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
            library("androidx-lifecycle-viewmodel-compose", "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
            library("androidx-activity-compose", "androidx.activity:activity-compose:1.9.2")
            library("androidx-compose-bom", "androidx.compose:compose-bom:2024.09.02")
            library("androidx-ui", "androidx.compose.ui", "ui").withoutVersion()
            library("androidx-ui-graphics", "androidx.compose.ui", "ui-graphics").withoutVersion()
            library("androidx-ui-tooling", "androidx.compose.ui", "ui-tooling").withoutVersion()
            library("androidx-ui-tooling-preview", "androidx.compose.ui", "ui-tooling-preview").withoutVersion()
            library("androidx-material3", "androidx.compose.material3", "material3").withoutVersion()
            library("androidx-navigation-compose", "androidx.navigation:navigation-compose:2.8.1")
            library("androidx-room-runtime", "androidx.room:room-runtime:2.6.1")
            library("androidx-room-ktx", "androidx.room:room-ktx:2.6.1")
            library("androidx-room-compiler", "androidx.room:room-compiler:2.6.1")
            library("androidx-datastore-preferences", "androidx.datastore:datastore-preferences:1.1.1")
            library("androidx-media3-exoplayer", "androidx.media3:media3-exoplayer:1.4.1")
            library("androidx-media3-ui", "androidx.media3:media3-ui:1.4.1")
            library("androidx-media3-session", "androidx.media3:media3-session:1.4.1")
            library("androidx-media3-hls", "androidx.media3:media3-exoplayer-hls:1.4.1")
            library("androidx-media3-datasource-okhttp", "androidx.media3:media3-datasource-okhttp:1.4.1")
            library("kotlinx-serialization-json", "org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            library("kotlinx-coroutines-android", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
            library("kotlinx-coroutines-test", "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            library("junit", "junit:junit:4.13.2")
            library("okhttp", "com.squareup.okhttp3:okhttp:4.12.0")
            library("okhttp-mockwebserver", "com.squareup.okhttp3:mockwebserver:4.12.0")
            library("coil-compose", "io.coil-kt:coil-compose:2.7.0")
        }
    }
}
