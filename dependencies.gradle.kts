// IMPORTANT: After changing a dependency, please run:
// ./gradlew --write-verification-metadata sha256 qa --rerun-tasks

dependencyResolutionManagement {
  versionCatalogs {
    create("libs") {
      version("androidx-appcompat", "1.6.1")
      version("androidx-activity", "1.9.2")
      version("androidx-camera", "1.3.4")
      version("androidx-fragment", "1.8.3")
      version("androidx-lifecycle", "2.8.5")
      version("androidx-media3", "1.3.1")
      version("androidx-navigation", "2.8.0")
      version("androidx-window", "1.3.0")
      version("exoplayer", "2.19.0")
      version("glide", "4.15.1")
      version("kotlin", "1.9.20")
      version("libsignal-client", "0.58.2")
      version("mp4parser", "1.9.39")
      version("android-gradle-plugin", "8.4.0")
      version("accompanist", "0.28.0")
      version("nanohttpd", "2.3.1")

      // Android Plugins
      library("android-library", "com.android.library", "com.android.library.gradle.plugin").versionRef("android-gradle-plugin")
      library("android-application", "com.android.application", "com.android.application.gradle.plugin").versionRef("android-gradle-plugin")

      // Compose
      library("androidx-compose-bom", "androidx.compose:compose-bom:2024.09.00")
      library("androidx-compose-material3", "androidx.compose.material3", "material3").withoutVersion()
      library("androidx-compose-ui-tooling-preview", "androidx.compose.ui", "ui-tooling-preview").withoutVersion()
      library("androidx-compose-ui-tooling-core", "androidx.compose.ui", "ui-tooling").withoutVersion()
      library("androidx-compose-ui-test-manifest", "androidx.compose.ui", "ui-test-manifest").withoutVersion()
      library("androidx-compose-runtime-livedata", "androidx.compose.runtime", "runtime-livedata").withoutVersion()
      library("androidx-compose-rxjava3", "androidx.compose.runtime:runtime-rxjava3:1.4.2")

      // Accompanist
      library("accompanist-permissions", "com.google.accompanist", "accompanist-permissions").versionRef("accompanist")

      // Desugaring
      library("android-tools-desugar", "com.android.tools:desugar_jdk_libs:1.1.6")

      // Kotlin
      library("kotlin-stdlib-jdk8", "org.jetbrains.kotlin", "kotlin-stdlib-jdk8").versionRef("kotlin")
      library("kotlin-reflect", "org.jetbrains.kotlin", "kotlin-reflect").versionRef("kotlin")
      library("kotlin-gradle-plugin", "org.jetbrains.kotlin", "kotlin-gradle-plugin").versionRef("kotlin")
      library("kotlinx-coroutines-core", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
      library("kotlinx-coroutines-core-jvm", "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0")
      library("kotlinx-coroutines-play-services", "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
      library("kotlinx-coroutines-rx3", "org.jetbrains.kotlinx:kotlinx-coroutines-rx3:1.3.9")

      // Android X
      library("androidx-activity-ktx", "androidx.activity", "activity-ktx").versionRef("androidx-activity")
      library("androidx-activity-compose", "androidx.activity", "activity-compose").versionRef("androidx-activity")
      library("androidx-appcompat", "androidx.appcompat", "appcompat").versionRef("androidx-appcompat")
      library("androidx-core-ktx", "androidx.core:core-ktx:1.12.0")
      library("androidx-fragment", "androidx.fragment", "fragment").versionRef("androidx-fragment")
      library("androidx-fragment-ktx", "androidx.fragment", "fragment-ktx").versionRef("androidx-fragment")
      library("androidx-fragment-testing", "androidx.fragment", "fragment-testing").versionRef("androidx-fragment")
      library("androidx-annotation", "androidx.annotation:annotation:1.4.0")
      library("androidx-constraintlayout", "androidx.constraintlayout:constraintlayout:2.1.4")
      library("androidx-window-window", "androidx.window", "window").versionRef("androidx-window")
      library("androidx-window-java", "androidx.window", "window-java").versionRef("androidx-window")
      library("androidx-recyclerview", "androidx.recyclerview:recyclerview:1.3.1")
      library("androidx-legacy-support", "androidx.legacy:legacy-support-v13:1.0.0")
      library("androidx-legacy-preference", "androidx.legacy:legacy-preference-v14:1.0.0")
      library("androidx-preference", "androidx.preference:preference:1.1.1")
      library("androidx-gridlayout", "androidx.gridlayout:gridlayout:1.0.0")
      library("androidx-exifinterface", "androidx.exifinterface:exifinterface:1.3.3")
      library("androidx-media3-exoplayer", "androidx.media3", "media3-exoplayer").versionRef("androidx-media3")
      library("androidx-media3-session", "androidx.media3", "media3-session").versionRef("androidx-media3")
      library("androidx-media3-ui", "androidx.media3", "media3-ui").versionRef("androidx-media3")
      library("androidx-media3-decoder", "androidx.media3", "media3-decoder").versionRef("androidx-media3")
      library("androidx-media3-common", "androidx.media3", "media3-common").versionRef("androidx-media3")
      library("androidx-navigation-fragment-ktx", "androidx.navigation", "navigation-fragment-ktx").versionRef("androidx-navigation")
      library("androidx-navigation-ui-ktx", "androidx.navigation", "navigation-ui-ktx").versionRef("androidx-navigation")
      library("androidx-navigation-compose", "androidx.navigation", "navigation-compose").versionRef("androidx-navigation")
      library("androidx-lifecycle-viewmodel-ktx", "androidx.lifecycle", "lifecycle-viewmodel-ktx").versionRef("androidx-lifecycle")
      library("androidx-lifecycle-livedata-core", "androidx.lifecycle", "lifecycle-livedata").versionRef("androidx-lifecycle")
      library("androidx-lifecycle-livedata-ktx", "androidx.lifecycle", "lifecycle-livedata-ktx").versionRef("androidx-lifecycle")
      library("androidx-lifecycle-process", "androidx.lifecycle", "lifecycle-process").versionRef("androidx-lifecycle")
      library("androidx-lifecycle-viewmodel-savedstate", "androidx.lifecycle", "lifecycle-viewmodel-savedstate").versionRef("androidx-lifecycle")
      library("androidx-lifecycle-common-java8", "androidx.lifecycle", "lifecycle-common-java8").versionRef("androidx-lifecycle")
      library("androidx-lifecycle-reactivestreams-ktx", "androidx.lifecycle", "lifecycle-reactivestreams-ktx").versionRef("androidx-lifecycle")
      library("androidx-lifecycle-runtime-compose", "androidx.lifecycle", "lifecycle-runtime-compose").versionRef("androidx-lifecycle")
      library("androidx-camera-core", "androidx.camera", "camera-core").versionRef("androidx-camera")
      library("androidx-camera-camera2", "androidx.camera", "camera-camera2").versionRef("androidx-camera")
      library("androidx-camera-extensions", "androidx.camera", "camera-extensions").versionRef("androidx-camera")
      library("androidx-camera-lifecycle", "androidx.camera", "camera-lifecycle").versionRef("androidx-camera")
      library("androidx-camera-view", "androidx.camera", "camera-view").versionRef("androidx-camera")
      library("androidx-concurrent-futures", "androidx.concurrent:concurrent-futures:1.0.0")
      library("androidx-autofill", "androidx.autofill:autofill:1.0.0")
      library("androidx-biometric", "androidx.biometric:biometric:1.1.0")
      library("androidx-sharetarget", "androidx.sharetarget:sharetarget:1.2.0-rc02")
      library("androidx-sqlite", "androidx.sqlite:sqlite:2.1.0")
      library("androidx-core-role", "androidx.core:core-role:1.0.0")
      library("androidx-profileinstaller", "androidx.profileinstaller:profileinstaller:1.2.2")
      library("androidx-asynclayoutinflater", "androidx.asynclayoutinflater:asynclayoutinflater:1.1.0-alpha01")
      library("androidx-asynclayoutinflater-appcompat", "androidx.asynclayoutinflater:asynclayoutinflater-appcompat:1.1.0-alpha01")
      library("androidx-emoji2", "androidx.emoji2:emoji2:1.4.0")
      library("androidx-documentfile", "androidx.documentfile:documentfile:1.0.0")
      library("androidx-webkit", "androidx.webkit:webkit:1.4.0")
      library("android-billing", "com.android.billingclient:billing-ktx:7.0.0")

      // Material
      library("material-material", "com.google.android.material:material:1.8.0")

      // Google
      library("google-protobuf-javalite", "com.google.protobuf:protobuf-javalite:3.11.4")
      library("google-libphonenumber", "com.googlecode.libphonenumber:libphonenumber:8.13.40")
      library("google-play-services-maps", "com.google.android.gms:play-services-maps:18.2.0")
      library("google-play-services-auth", "com.google.android.gms:play-services-auth:21.0.0")
      library("google-play-services-wallet", "com.google.android.gms:play-services-wallet:19.2.1")
      library("google-zxing-android-integration", "com.google.zxing:android-integration:3.3.0")
      library("google-zxing-core", "com.google.zxing:core:3.4.1")
      library("google-ez-vcard", "com.googlecode.ez-vcard:ez-vcard:0.9.11")
      library("google-jsr305", "com.google.code.findbugs:jsr305:3.0.2")
      library("google-guava-android", "com.google.guava:guava:30.0-android")
      library("google-flexbox", "com.google.android.flexbox:flexbox:3.0.0")

      bundle("media3", listOf("androidx-media3-exoplayer", "androidx-media3-session", "androidx-media3-ui"))

      // Firebase
      library("firebase-messaging", "com.google.firebase:firebase-messaging:23.1.2")

      // 1st Party
      library("libsignal-client", "org.signal", "libsignal-client").versionRef("libsignal-client")
      library("libsignal-android", "org.signal", "libsignal-android").versionRef("libsignal-client")
      library("signal-aesgcmprovider", "org.signal:aesgcmprovider:0.0.3")
      library("molly-ringrtc", "im.molly:ringrtc-android:2.48.1-1")
      library("signal-android-database-sqlcipher", "org.signal:sqlcipher-android:4.6.0-S1")

      // MOLLY
      library("gosimple-nbvcxz", "me.gosimple:nbvcxz:1.5.0")
      library("molly-native-utils", "im.molly:native-utils:1.0.0")
      library("molly-argon2", "im.molly:argon2:13.1-1")
      library("molly-glide-webp-decoder", "im.molly:glide-webp-decoder:1.3.2-2")

      // UnifiedPush
      library("unifiedpush-connector", "org.unifiedpush.android:connector:3.0.0-rc2")
      library("unifiedpush-connector-ui", "org.unifiedpush.android:connector-ui:1.1.0-rc3")

      // Third Party
      library("greenrobot-eventbus", "org.greenrobot:eventbus:3.0.0")
      library("jackson-core", "com.fasterxml.jackson.core:jackson-databind:2.17.2")
      library("jackson-module-kotlin", "com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
      library("square-okhttp3", "com.squareup.okhttp3:okhttp:4.12.0")
      library("square-okhttp3-dnsoverhttps", "com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
      library("square-okio", "com.squareup.okio:okio:3.6.0")
      library("square-leakcanary", "com.squareup.leakcanary:leakcanary-android:2.7")
      library("rxjava3-rxjava", "io.reactivex.rxjava3:rxjava:3.0.13")
      library("rxjava3-rxandroid", "io.reactivex.rxjava3:rxandroid:3.0.0")
      library("rxjava3-rxkotlin", "io.reactivex.rxjava3:rxkotlin:3.0.1")
      library("rxdogtag", "com.uber.rxdogtag2:rxdogtag:2.0.1")
      library("conscrypt-android", "org.conscrypt:conscrypt-android:2.5.2")
      library("mobilecoin", "com.mobilecoin:android-sdk:6.0.1")
      library("leolin-shortcutbadger", "me.leolin:ShortcutBadger:1.1.22")
      library("emilsjolander-stickylistheaders", "se.emilsjolander:stickylistheaders:2.7.0")
      library("apache-httpclient-android", "org.apache.httpcomponents:httpclient-android:4.3.5")
      library("glide-glide", "com.github.bumptech.glide", "glide").versionRef("glide")
      library("glide-ksp", "com.github.bumptech.glide", "ksp").versionRef("glide")
      library("roundedimageview", "com.makeramen:roundedimageview:2.1.0")
      library("materialish-progress", "com.pnikosis:materialish-progress:1.7")
      library("subsampling-scale-image-view", "com.davemorrissey.labs:subsampling-scale-image-view-androidx:3.10.0")
      library("android-tooltips", "com.tomergoldst.android:tooltips:1.0.6")
      library("stream", "com.annimon:stream:1.1.8")
      library("lottie", "com.airbnb.android:lottie:5.2.0")
      library("lottie-compose", "com.airbnb.android:lottie-compose:6.4.0")
      library("dnsjava", "dnsjava:dnsjava:2.1.9")
      library("nanohttpd-webserver", "org.nanohttpd", "nanohttpd-webserver").versionRef("nanohttpd")
      library("nanohttpd-websocket", "org.nanohttpd", "nanohttpd-websocket").versionRef("nanohttpd")
      library("kotlinx-collections-immutable", "org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")

      // Can"t use the newest version because it hits some weird NoClassDefFoundException
      library("jknack-handlebars", "com.github.jknack:handlebars:4.0.7")

      // Mp4Parser
      library("mp4parser-isoparser", "org.mp4parser", "isoparser").versionRef("mp4parser")
      library("mp4parser-streaming", "org.mp4parser", "streaming").versionRef("mp4parser")
      library("mp4parser-muxer", "org.mp4parser", "muxer").versionRef("mp4parser")
      bundle("mp4parser", listOf("mp4parser-isoparser", "mp4parser-streaming", "mp4parser-muxer"))
    }

    create("benchmarkLibs") {
      version("androidx-test-ext-junit", "1.1.3")

      // Macrobench/Baseline profiles
      library("androidx-test-ext-junit", "androidx.test.ext", "junit").versionRef("androidx-test-ext-junit")
      library("espresso-core", "androidx.test.espresso:espresso-core:3.4.0")
      library("uiautomator", "androidx.test.uiautomator:uiautomator:2.2.0")
      library("androidx-benchmark-macro", "androidx.benchmark:benchmark-macro-junit4:1.1.1")
      library("androidx-benchmark-micro", "androidx.benchmark:benchmark-junit4:1.1.0-beta04")
    }

    create("testLibs") {
      version("androidx-test", "1.5.0")
      version("androidx-test-ext-junit", "1.1.5")
      version("robolectric", "4.10.3")
      version("espresso", "3.4.0")

      library("junit-junit", "junit:junit:4.13.2")
      library("androidx-test-core", "androidx.test", "core").versionRef("androidx-test")
      library("androidx-test-core-ktx", "androidx.test", "core-ktx").versionRef("androidx-test")
      library("androidx-test-ext-junit", "androidx.test.ext", "junit").versionRef("androidx-test-ext-junit")
      library("androidx-test-ext-junit-ktx", "androidx.test.ext", "junit-ktx").versionRef("androidx-test-ext-junit")
      library("androidx-test-monitor", "androidx.test:monitor:1.6.1")
      library("androidx-test-orchestrator", "androidx.test:orchestrator:1.4.1")
      library("androidx-test-runner", "androidx.test", "runner").versionRef("androidx-test")
      library("espresso-core", "androidx.test.espresso", "espresso-core").versionRef("espresso")
      library("kotlinx-coroutines-test", "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
      library("mockito-core", "org.mockito:mockito-inline:4.6.1")
      library("mockito-kotlin", "org.mockito.kotlin:mockito-kotlin:4.0.0")
      library("mockito-android", "org.mockito:mockito-android:4.6.1")
      library("robolectric-robolectric", "org.robolectric", "robolectric").versionRef("robolectric")
      library("bouncycastle-bcprov-jdk15on", "org.bouncycastle:bcprov-jdk15on:1.70")
      library("bouncycastle-bcpkix-jdk15on", "org.bouncycastle:bcpkix-jdk15on:1.70")
      library("hamcrest-hamcrest", "org.hamcrest:hamcrest:2.2")
      library("assertj-core", "org.assertj:assertj-core:3.11.1")
      library("square-okhttp-mockserver", "com.squareup.okhttp3:mockwebserver:4.12.0")
      library("mockk", "io.mockk:mockk:1.13.2")
      library("mockk-android", "io.mockk:mockk-android:1.13.2")

      library("conscrypt-openjdk-uber", "org.conscrypt:conscrypt-openjdk-uber:2.5.2")
      library("diff-utils", "io.github.java-diff-utils:java-diff-utils:4.12")
    }

    create("lintLibs") {
      version("lint", "31.4.0") // Lint version is AGP version + 23.0.0

      library("lint-api", "com.android.tools.lint", "lint-api").versionRef("lint")
      library("lint-checks", "com.android.tools.lint", "lint-checks").versionRef("lint")
      library("lint-tests", "com.android.tools.lint", "lint-tests").versionRef("lint")
    }
  }
}
