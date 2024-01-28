import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.io.ByteArrayOutputStream

plugins {
  id("com.android.application")
  id("kotlin-android")
  id("androidx.navigation.safeargs")
  id("org.jetbrains.kotlin.android")
  id("app.cash.exhaustive")
  id("kotlin-parcelize")
  id("com.squareup.wire")
  id("translations")
  id("licenses")
}

// Sort baseline.profm for reproducible builds
// See issue: https://issuetracker.google.com/issues/231837768
apply {
  from("fix-profm.gradle")
}

val canonicalVersionCode = 1377
val canonicalVersionName = "6.44.3"
val mollyRevision = 1

val postFixSize = 100

val selectableVariants = listOf(
  "prodFossWebsiteDebug",
  "prodFossWebsiteRelease",
  "prodFossStoreDebug",
  "prodFossStoreRelease",
  "prodGmsWebsiteDebug",
  "prodGmsWebsiteRelease",
  "prodGmsWebsiteCanary",
  "prodGmsWebsiteInstrumentation",
  "prodGmsWebsiteSpinner",
  "stagingFossWebsiteDebug",
  "stagingFossWebsiteRelease",
  "stagingGmsWebsiteDebug",
  "stagingGmsWebsiteRelease",
)

val signalBuildToolsVersion: String by rootProject.extra
val signalCompileSdkVersion: String by rootProject.extra
val signalTargetSdkVersion: Int by rootProject.extra
val signalMinSdkVersion: Int by rootProject.extra
val signalJavaVersion: JavaVersion by rootProject.extra
val signalKotlinJvmTarget: String by rootProject.extra

// Override build config via env vars when project property 'CI' is set
val ciEnabled = project.hasProperty("CI")

val baseAppTitle = getCiEnv("CI_APP_TITLE") ?: properties["baseAppTitle"] as String
val baseAppFileName = getCiEnv("CI_APP_FILENAME") ?: properties["baseAppFileName"] as String
val basePackageId = getCiEnv("CI_PACKAGE_ID") ?: properties["basePackageId"] as String
val buildVariants = getCiEnv("CI_BUILD_VARIANTS") ?: properties["buildVariants"] as String
val forceInternalUserFlag = getCiEnv("CI_FORCE_INTERNAL_USER_FLAG") ?: properties["forceInternalUserFlag"] as String
val mapsApiKey = getCiEnv("CI_MAPS_API_KEY") ?: properties["mapsApiKey"] as String

fun getCiEnv(name: String) = if (ciEnabled) System.getenv(name).takeUnless { it.isNullOrBlank() } else null

wire {
  kotlin {
    javaInterop = true
  }

  sourcePath {
    srcDir("src/main/protowire")
  }

  protoPath {
    srcDir("${project.rootDir}/libsignal-service/src/main/protowire")
  }
}

android {
  namespace = "org.thoughtcrime.securesms"

  buildToolsVersion = signalBuildToolsVersion
  compileSdkVersion = signalCompileSdkVersion

  flavorDimensions += listOf("environment", "license", "distribution")
  useLibrary("org.apache.http.legacy")
  testBuildType = "instrumentation"

  kotlinOptions {
    jvmTarget = signalKotlinJvmTarget
    freeCompilerArgs = listOf("-Xallow-result-return-type")
  }

  signingConfigs {
    System.getenv("CI_KEYSTORE_PATH")?.let { path ->
      create("ci") {
        println("Signing release build with keystore: '$path'")
        storeFile = file(path)
        storePassword = System.getenv("CI_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("CI_KEYSTORE_ALIAS")
        keyPassword = System.getenv("CI_KEYSTORE_PASSWORD")
        enableV4Signing = false
      }
    }
  }

  testOptions {
    execution = "ANDROIDX_TEST_ORCHESTRATOR"

    unitTests {
      isIncludeAndroidResources = true
    }
  }

  sourceSets {
    getByName("test") {
      java.srcDir("$projectDir/src/testShared")
    }

    getByName("androidTest") {
      java.srcDir("$projectDir/src/testShared")
    }
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = signalJavaVersion
    targetCompatibility = signalJavaVersion
  }

  packagingOptions {
    resources {
      excludes += setOf("LICENSE.txt", "LICENSE", "NOTICE", "asm-license.txt", "META-INF/LICENSE", "META-INF/LICENSE.md", "META-INF/NOTICE", "META-INF/LICENSE-notice.md", "META-INF/proguard/androidx-annotations.pro", "libsignal_jni.dylib", "signal_jni.dll")
    }
    jniLibs {
      // MOLLY: Compress native libs by default as APK is not split on ABIs
      useLegacyPackaging = true
    }
  }

  buildFeatures {
    viewBinding = true
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = "1.4.4"
  }

  if (mollyRevision < 0 || mollyRevision >= postFixSize) {
    throw GradleException("Molly revision $mollyRevision out of range")
  }

  defaultConfig {
    versionCode = canonicalVersionCode * postFixSize + mollyRevision
    versionName = if (ciEnabled) getCommitTag() else canonicalVersionName

    minSdkVersion(signalMinSdkVersion)
    targetSdkVersion(signalTargetSdkVersion)

    applicationId = basePackageId

    multiDexEnabled = true

    buildConfigField("String", "SIGNAL_PACKAGE_NAME", "\"org.thoughtcrime.securesms\"")
    buildConfigField("String", "SIGNAL_CANONICAL_VERSION_NAME", "\"$canonicalVersionName\"")
    buildConfigField("int", "SIGNAL_CANONICAL_VERSION_CODE", "$canonicalVersionCode")
    buildConfigField("String", "BACKUP_FILENAME", "\"${baseAppFileName.lowercase()}\"")
    buildConfigField("boolean", "FORCE_INTERNAL_USER_FLAG", forceInternalUserFlag)

    vectorDrawables.useSupportLibrary = true

    // MOLLY: Ensure to add any new URLs to SignalServiceNetworkAccess.HOSTNAMES list
    buildConfigField("long", "BUILD_TIMESTAMP", getLastCommitTimestamp() + "L")
    buildConfigField("String", "GIT_HASH", "\"${getGitHash()}\"")
    buildConfigField("String", "SIGNAL_URL", "\"https://chat.signal.org\"")
    buildConfigField("String", "STORAGE_URL", "\"https://storage.signal.org\"")
    buildConfigField("String", "SIGNAL_CDN_URL", "\"https://cdn.signal.org\"")
    buildConfigField("String", "SIGNAL_CDN2_URL", "\"https://cdn2.signal.org\"")
    buildConfigField("String", "SIGNAL_CDN3_URL", "\"https://cdn3.signal.org\"")
    buildConfigField("String", "SIGNAL_CDSI_URL", "\"https://cdsi.signal.org\"")
    buildConfigField("String", "SIGNAL_SERVICE_STATUS_URL", "\"uptime.signal.org\"")
    buildConfigField("String", "SIGNAL_KEY_BACKUP_URL", "\"https://api.backup.signal.org\"")
    buildConfigField("String", "SIGNAL_SVR2_URL", "\"https://svr2.signal.org\"")
    buildConfigField("String", "SIGNAL_SFU_URL", "\"https://sfu.voip.signal.org\"")
    buildConfigField("String", "SIGNAL_STAGING_SFU_URL", "\"https://sfu.staging.voip.signal.org\"")
    buildConfigField("String[]", "SIGNAL_SFU_INTERNAL_NAMES", "new String[]{\"Test\", \"Staging\", \"Development\"}")
    buildConfigField("String[]", "SIGNAL_SFU_INTERNAL_URLS", "new String[]{\"https://sfu.test.voip.signal.org\", \"https://sfu.staging.voip.signal.org\", \"https://sfu.staging.test.voip.signal.org\"}")
    buildConfigField("String", "CONTENT_PROXY_HOST", "\"contentproxy.signal.org\"")
    buildConfigField("int", "CONTENT_PROXY_PORT", "443")
    buildConfigField("String", "SIGNAL_AGENT", "\"OWA\"")
    buildConfigField("String", "CDSI_MRENCLAVE", "\"0f6fd79cdfdaa5b2e6337f534d3baf999318b0c462a7ac1f41297a3e4b424a57\"")
    buildConfigField("String", "SVR2_MRENCLAVE_DEPRECATED", "\"6ee1042f9e20f880326686dd4ba50c25359f01e9f733eeba4382bca001d45094\"")
    buildConfigField("String", "SVR2_MRENCLAVE", "\"a6622ad4656e1abcd0bc0ff17c229477747d2ded0495c4ebee7ed35c1789fa97\"")
    buildConfigField("String", "UNIDENTIFIED_SENDER_TRUST_ROOT", "\"BXu6QIKVz5MA8gstzfOgRQGqyLqOwNKHL6INkv3IHWMF\"")
    buildConfigField("String", "ZKGROUP_SERVER_PUBLIC_PARAMS", "\"AMhf5ywVwITZMsff/eCyudZx9JDmkkkbV6PInzG4p8x3VqVJSFiMvnvlEKWuRob/1eaIetR31IYeAbm0NdOuHH8Qi+Rexi1wLlpzIo1gstHWBfZzy1+qHRV5A4TqPp15YzBPm0WSggW6PbSn+F4lf57VCnHF7p8SvzAA2ZZJPYJURt8X7bbg+H3i+PEjH9DXItNEqs2sNcug37xZQDLm7X36nOoGPs54XsEGzPdEV+itQNGUFEjY6X9Uv+Acuks7NpyGvCoKxGwgKgE5XyJ+nNKlyHHOLb6N1NuHyBrZrgtY/JYJHRooo5CEqYKBqdFnmbTVGEkCvJKxLnjwKWf+fEPoWeQFj5ObDjcKMZf2Jm2Ae69x+ikU5gBXsRmoF94GXTLfN0/vLt98KDPnxwAQL9j5V1jGOY8jQl6MLxEs56cwXN0dqCnImzVH3TZT1cJ8SW1BRX6qIVxEzjsSGx3yxF3suAilPMqGRp4ffyopjMD1JXiKR2RwLKzizUe5e8XyGOy9fplzhw3jVzTRyUZTRSZKkMLWcQ/gv0E4aONNqs4P+NameAZYOD12qRkxosQQP5uux6B2nRyZ7sAV54DgFyLiRcq1FvwKw2EPQdk4HDoePrO/RNUbyNddnM/mMgj4FW65xCoT1LmjrIjsv/Ggdlx46ueczhMgtBunx1/w8k8V+l8LVZ8gAT6wkU5J+DPQalQguMg12Jzug3q4TbdHiGCmD9EunCwOmsLuLJkz6EcSYXtrlDEnAM+hicw7iergYLLlMXpfTdGxJCWJmP4zqUFeTTmsmhsjGBt7NiEB/9pFFEB3pSbf4iiUukw63Eo8Aqnf4iwob6X1QviCWuc8t0I=\"")
    buildConfigField("String", "GENERIC_SERVER_PUBLIC_PARAMS", "\"AByD873dTilmOSG0TjKrvpeaKEsUmIO8Vx9BeMmftwUs9v7ikPwM8P3OHyT0+X3EUMZrSe9VUp26Wai51Q9I8mdk0hX/yo7CeFGJyzoOqn8e/i4Ygbn5HoAyXJx5eXfIbqpc0bIxzju4H/HOQeOpt6h742qii5u/cbwOhFZCsMIbElZTaeU+BWMBQiZHIGHT5IE0qCordQKZ5iPZom0HeFa8Yq0ShuEyAl0WINBiY6xE3H/9WnvzXBbMuuk//eRxXgzO8ieCeK8FwQNxbfXqZm6Ro1cMhCOF3u7xoX83QhpN\"")
    buildConfigField("String", "BACKUP_SERVER_PUBLIC_PARAMS", "\"AJwNSU55fsFCbgaxGRD11wO1juAs8Yr5GF8FPlGzzvdJJIKH5/4CC7ZJSOe3yL2vturVaRU2Cx0n751Vt8wkj1bozK3CBV1UokxV09GWf+hdVImLGjXGYLLhnI1J2TWEe7iWHyb553EEnRb5oxr9n3lUbNAJuRmFM7hrr0Al0F0wrDD4S8lo2mGaXe0MJCOM166F8oYRQqpFeEHfiLnxA1O8ZLh7vMdv4g9jI5phpRBTsJ5IjiJrWeP0zdIGHEssUeprDZ9OUJ14m0v61eYJMKsf59Bn+mAT2a7YfB+Don9O\"")
    buildConfigField("String[]", "LANGUAGES", "new String[]{ ${languageList().map { "\"$it\"" }.joinToString(separator = ", ")} }")
    buildConfigField("String", "DEFAULT_CURRENCIES", "\"EUR,AUD,GBP,CAD,CNY\"")
    buildConfigField("String", "GIPHY_API_KEY", "\"3o6ZsYH6U6Eri53TXy\"")
    buildConfigField("String", "SIGNAL_CAPTCHA_URL", "\"https://signalcaptchas.org/registration/generate.html\"")
    buildConfigField("String", "RECAPTCHA_PROOF_URL", "\"https://signalcaptchas.org/challenge/generate.html\"")

    // MOLLY: Rely on the built-in variables FLAVOR and BUILD_TYPE instead of BUILD_*_TYPE
    buildConfigField("String", "BADGE_STATIC_ROOT", "\"https://updates2.signal.org/static/badges/\"")
    buildConfigField("boolean", "TRACING_ENABLED", "false")

    ndk {
      //noinspection ChromeOsAbiSupport
      abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
    }

    resourceConfigurations += listOf()

    bundle {
      language {
        enableSplit = false
      }
    }

    testInstrumentationRunner = "org.thoughtcrime.securesms.testing.SignalTestRunner"
    testInstrumentationRunnerArguments["clearPackageData"] = "true"
  }

  buildTypes {
    getByName("debug") {
      isDefault = true
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android.txt"),
        "proguard/proguard-firebase-messaging.pro",
        "proguard/proguard-google-play-services.pro",
        "proguard/proguard-jackson.pro",
        "proguard/proguard-sqlite.pro",
        "proguard/proguard-appcompat-v7.pro",
        "proguard/proguard-square-okhttp.pro",
        "proguard/proguard-square-okio.pro",
        "proguard/proguard-rounded-image-view.pro",
        "proguard/proguard-glide.pro",
        "proguard/proguard-shortcutbadger.pro",
        "proguard/proguard-retrofit.pro",
        "proguard/proguard-webrtc.pro",
        "proguard/proguard-klinker.pro",
        "proguard/proguard-mobilecoin.pro",
        "proguard/proguard-retrolambda.pro",
        "proguard/proguard-okhttp.pro",
        "proguard/proguard-ez-vcard.pro",
        "proguard/proguard.cfg"
      )
      testProguardFiles(
        "proguard/proguard-automation.pro",
        "proguard/proguard.cfg"
      )
    }

    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.findByName("ci")
      proguardFiles(*buildTypes["debug"].proguardFiles.toTypedArray())
    }

    create("instrumentation") {
      initWith(getByName("debug"))
      isDefault = false
      isMinifyEnabled = false
      matchingFallbacks += "debug"
      applicationIdSuffix = ".instrumentation"
    }

    create("spinner") {
      initWith(getByName("debug"))
      isDefault = false
      isMinifyEnabled = false
      matchingFallbacks += "debug"
    }

    create("canary") {
      initWith(getByName("debug"))
      isDefault = false
      isMinifyEnabled = false
      matchingFallbacks += "debug"
    }
  }

  productFlavors {
    create("store") {
      dimension = "distribution"
      buildConfigField("boolean", "MANAGES_MOLLY_UPDATES", "false")
    }

    create("website") {
      dimension = "distribution"
      isDefault = true
      buildConfigField("boolean", "MANAGES_MOLLY_UPDATES", "true")
    }

    create("gms") {
      dimension = "license"
      isDefault = true
      manifestPlaceholders["mapsApiKey"] = mapsApiKey
      buildConfigField("boolean", "USE_PLAY_SERVICES", "true")
      buildConfigField("boolean", "USE_OSM", "false")
      buildConfigField("String", "FDROID_UPDATE_URL", "\"https://molly.im/fdroid/repo\"")
    }

    create("foss") {
      dimension = "license"
      versionNameSuffix = "-FOSS"
      buildConfigField("boolean", "USE_PLAY_SERVICES", "false")
      buildConfigField("boolean", "USE_OSM", "true")
      buildConfigField("String", "FDROID_UPDATE_URL", "\"https://molly.im/fdroid/foss/repo\"")
    }

    create("prod") {
      dimension = "environment"

      isDefault = true

      buildConfigField("String", "MOBILE_COIN_ENVIRONMENT", "\"mainnet\"")
    }

    create("staging") {
      dimension = "environment"

      applicationIdSuffix = ".staging"

      buildConfigField("String", "SIGNAL_PACKAGE_NAME", "\"org.thoughtcrime.securesms.staging\"")

      buildConfigField("String", "SIGNAL_URL", "\"https://chat.staging.signal.org\"")
      buildConfigField("String", "STORAGE_URL", "\"https://storage-staging.signal.org\"")
      buildConfigField("String", "SIGNAL_CDN_URL", "\"https://cdn-staging.signal.org\"")
      buildConfigField("String", "SIGNAL_CDN2_URL", "\"https://cdn2-staging.signal.org\"")
      buildConfigField("String", "SIGNAL_CDN3_URL", "\"https://cdn3-staging.signal.org\"")
      buildConfigField("String", "SIGNAL_CDSI_URL", "\"https://cdsi.staging.signal.org\"")
      buildConfigField("String", "SIGNAL_KEY_BACKUP_URL", "\"https://api-staging.backup.signal.org\"")
      buildConfigField("String", "SIGNAL_SVR2_URL", "\"https://svr2.staging.signal.org\"")
      buildConfigField("String", "SVR2_MRENCLAVE_DEPRECATED", "\"a8a261420a6bb9b61aa25bf8a79e8bd20d7652531feb3381cbffd446d270be95\"")
      buildConfigField("String", "SVR2_MRENCLAVE", "\"acb1973aa0bbbd14b3b4e06f145497d948fd4a98efc500fcce363b3b743ec482\"")
      buildConfigField("String", "UNIDENTIFIED_SENDER_TRUST_ROOT", "\"BbqY1DzohE4NUZoVF+L18oUPrK3kILllLEJh2UnPSsEx\"")
      buildConfigField("String", "ZKGROUP_SERVER_PUBLIC_PARAMS", "\"ABSY21VckQcbSXVNCGRYJcfWHiAMZmpTtTELcDmxgdFbtp/bWsSxZdMKzfCp8rvIs8ocCU3B37fT3r4Mi5qAemeGeR2X+/YmOGR5ofui7tD5mDQfstAI9i+4WpMtIe8KC3wU5w3Inq3uNWVmoGtpKndsNfwJrCg0Hd9zmObhypUnSkfYn2ooMOOnBpfdanRtrvetZUayDMSC5iSRcXKpdlukrpzzsCIvEwjwQlJYVPOQPj4V0F4UXXBdHSLK05uoPBCQG8G9rYIGedYsClJXnbrgGYG3eMTG5hnx4X4ntARBgELuMWWUEEfSK0mjXg+/2lPmWcTZWR9nkqgQQP0tbzuiPm74H2wMO4u1Wafe+UwyIlIT9L7KLS19Aw8r4sPrXZSSsOZ6s7M1+rTJN0bI5CKY2PX29y5Ok3jSWufIKcgKOnWoP67d5b2du2ZVJjpjfibNIHbT/cegy/sBLoFwtHogVYUewANUAXIaMPyCLRArsKhfJ5wBtTminG/PAvuBdJ70Z/bXVPf8TVsR292zQ65xwvWTejROW6AZX6aqucUjlENAErBme1YHmOSpU6tr6doJ66dPzVAWIanmO/5mgjNEDeK7DDqQdB1xd03HT2Qs2TxY3kCK8aAb/0iM0HQiXjxZ9HIgYhbtvGEnDKW5ILSUydqH/KBhW4Pb0jZWnqN/YgbWDKeJxnDbYcUob5ZY5Lt5ZCMKuaGUvCJRrCtuugSMaqjowCGRempsDdJEt+cMaalhZ6gczklJB/IbdwENW9KeVFPoFNFzhxWUIS5ML9riVYhAtE6JE5jX0xiHNVIIPthb458cfA8daR0nYfYAUKogQArm0iBezOO+mPk5vCM=\"")
      buildConfigField("String", "GENERIC_SERVER_PUBLIC_PARAMS", "\"AHILOIrFPXX9laLbalbA9+L1CXpSbM/bTJXZGZiuyK1JaI6dK5FHHWL6tWxmHKYAZTSYmElmJ5z2A5YcirjO/yfoemE03FItyaf8W1fE4p14hzb5qnrmfXUSiAIVrhaXVwIwSzH6RL/+EO8jFIjJ/YfExfJ8aBl48CKHgu1+A6kWynhttonvWWx6h7924mIzW0Czj2ROuh4LwQyZypex4GuOPW8sgIT21KNZaafgg+KbV7XM1x1tF3XA17B4uGUaDbDw2O+nR1+U5p6qHPzmJ7ggFjSN6Utu+35dS1sS0P9N\"")
      buildConfigField("String", "BACKUP_SERVER_PUBLIC_PARAMS", "\"AHYrGb9IfugAAJiPKp+mdXUx+OL9zBolPYHYQz6GI1gWjpEu5me3zVNSvmYY4zWboZHif+HG1sDHSuvwFd0QszSwuSF4X4kRP3fJREdTZ5MCR0n55zUppTwfHRW2S4sdQ0JGz7YDQIJCufYSKh0pGNEHL6hv79Agrdnr4momr3oXdnkpVBIp3HWAQ6IbXQVSG18X36GaicI1vdT0UFmTwU2KTneluC2eyL9c5ff8PcmiS+YcLzh0OKYQXB5ZfQ06d6DiINvDQLy75zcfUOniLAj0lGJiHxGczin/RXisKSR8\"")
      buildConfigField("String", "MOBILE_COIN_ENVIRONMENT", "\"testnet\"")
      buildConfigField("String", "SIGNAL_CAPTCHA_URL", "\"https://signalcaptchas.org/staging/registration/generate.html\"")
      buildConfigField("String", "RECAPTCHA_PROOF_URL", "\"https://signalcaptchas.org/staging/challenge/generate.html\"")

      buildConfigField("String", "BUILD_ENVIRONMENT_TYPE", "\"Staging\"")
      buildConfigField("String", "STRIPE_PUBLISHABLE_KEY", "\"pk_test_sngOd8FnXNkpce9nPXawKrJD00kIDngZkD\"")
    }
  }

  lint {
    abortOnError = true
    baseline = file("lint-baseline.xml")
    disable += "LintError"
  }

  applicationVariants.all {
    val isStaging = productFlavors.any { it.name == "staging" }

    resValue("string", "app_name", baseAppTitle + if (isStaging) " Staging" else "")
    resValue("string", "package_name", applicationId)

    outputs
      .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
      .forEach { output ->
        val flavors = "-$baseName"
          .replace("-prod", "")
          .replace(Regex("-(foss|gms)"), "")
          .replace("-website", "")
          .replace("-release", "")
        val unsigned = if (isSigningReady) "" else "-unsigned"

        output.outputFileName = "${baseAppFileName}${flavors}${unsigned}-${versionName}.apk"
      }
  }

  android.buildTypes.forEach {
    val path: String = if (it.name == "release") {
      "$projectDir/src/release/java"
    } else {
      "$projectDir/src/debug/java"
    }

    sourceSets.findByName(it.name)!!.java.srcDir(path)
  }
}

androidComponents {
  beforeVariants { variantBuilder ->
    val name = variantBuilder.name
    val selected = selectableVariants.contains(name)
    if (!(selected && buildVariants.toRegex().containsMatchIn(name))) {
      variantBuilder.enable = false
    }
  }
}

dependencies {
  lintChecks(project(":lintchecks"))
  coreLibraryDesugaring(libs.android.tools.desugar)

  implementation(project(":libsignal-service"))
  implementation(project(":paging"))
  implementation(project(":core-util"))
  implementation(project(":glide-config"))
  implementation(project(":video"))
  implementation(project(":device-transfer"))
  implementation(project(":image-editor"))
  implementation(project(":contacts"))
  implementation(project(":qr"))
  implementation(project(":sticky-header-grid"))
  implementation(project(":photoview"))
  implementation(project(":glide-webp"))
  implementation(project(":core-ui"))

  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.appcompat) {
    version {
      strictly("1.6.1")
    }
  }
  implementation(libs.androidx.window.window)
  implementation(libs.androidx.window.java)
  implementation(libs.androidx.recyclerview)
  implementation(libs.material.material)
  implementation(libs.androidx.legacy.support)
  implementation(libs.androidx.preference)
  implementation(libs.androidx.legacy.preference)
  implementation(libs.androidx.gridlayout)
  implementation(libs.androidx.exifinterface)
  implementation(libs.androidx.compose.rxjava3)
  implementation(libs.androidx.compose.runtime.livedata)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.multidex)
  implementation(libs.androidx.navigation.fragment.ktx)
  implementation(libs.androidx.navigation.ui.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.livedata.ktx)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.lifecycle.viewmodel.savedstate)
  implementation(libs.androidx.lifecycle.common.java8)
  implementation(libs.androidx.lifecycle.reactivestreams.ktx)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.concurrent.futures)
  implementation(libs.androidx.autofill)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.sharetarget)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.asynclayoutinflater)
  implementation(libs.androidx.asynclayoutinflater.appcompat)
  implementation(libs.androidx.webkit)
  "gmsImplementation"(libs.firebase.messaging) {
    exclude(group = "com.google.firebase", module = "firebase-core")
    exclude(group = "com.google.firebase", module = "firebase-analytics")
    exclude(group = "com.google.firebase", module = "firebase-measurement-connector")
  }
  "gmsImplementation"(libs.google.play.services.maps)
  "gmsImplementation"(libs.google.play.services.auth)
  implementation(libs.bundles.media3)
  implementation(libs.conscrypt.android)
  implementation(libs.signal.aesgcmprovider)
  implementation(libs.libsignal.android)
  implementation(libs.mobilecoin)
  implementation(libs.molly.ringrtc)
  implementation(libs.leolin.shortcutbadger)
  implementation(libs.emilsjolander.stickylistheaders)
  implementation(libs.apache.httpclient.android)
  implementation(libs.glide.glide)
  implementation(libs.roundedimageview)
  implementation(libs.materialish.progress)
  implementation(libs.greenrobot.eventbus)
  implementation(libs.google.zxing.android.integration)
  implementation(libs.google.zxing.core)
  implementation(libs.google.flexbox)
  implementation(libs.subsampling.scale.image.view) {
    exclude(group = "com.android.support", module = "support-annotations")
  }
  implementation(libs.android.tooltips) {
    exclude(group = "com.android.support", module = "appcompat-v7")
  }
  implementation(libs.stream)
  implementation(libs.lottie)
  implementation(libs.signal.android.database.sqlcipher)
  implementation(libs.androidx.sqlite)
  implementation(libs.google.ez.vcard) {
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "org.freemarker")
  }
  implementation(libs.dnsjava)
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.accompanist.permissions)
  implementation(libs.kotlin.stdlib.jdk8)
  implementation(libs.rxjava3.rxandroid)
  implementation(libs.rxjava3.rxkotlin)
  implementation(libs.rxdogtag)

  implementation(project(":libnetcipher"))
  implementation(libs.molly.argon2) { artifact { type = "aar" } }
  implementation(libs.molly.native.utils)
  implementation(libs.molly.glide.webp.decoder)
  implementation(libs.gosimple.nbvcxz)
  "fossImplementation"("org.osmdroid:osmdroid-android:6.1.16")
  "fossImplementation"(project(":libfakegms"))

  "spinnerImplementation"(project(":spinner"))

  "canaryImplementation"(libs.square.leakcanary)

  "instrumentationImplementation"(libs.androidx.fragment.testing) {
    exclude(group = "androidx.test", module = "core")
  }

  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.assertj.core)
  testImplementation(testLibs.mockito.core)
  testImplementation(testLibs.mockito.kotlin)
  testImplementation(testLibs.androidx.test.core)
  testImplementation(testLibs.robolectric.robolectric) {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
  }
  testImplementation(testLibs.robolectric.shadows.multidex)
  testImplementation(testLibs.bouncycastle.bcprov.jdk15on) {
    version {
      strictly("1.70")
    }
  }
  testImplementation(testLibs.bouncycastle.bcpkix.jdk15on) {
    version {
      strictly("1.70")
    }
  }
  testImplementation(testLibs.conscrypt.openjdk.uber)
  testImplementation(testLibs.hamcrest.hamcrest)
  testImplementation(testLibs.mockk)
  testImplementation(testFixtures(project(":libsignal-service")))
  testImplementation(testLibs.espresso.core)

  androidTestImplementation(testLibs.androidx.test.ext.junit)
  androidTestImplementation(testLibs.espresso.core)
  androidTestImplementation(testLibs.androidx.test.core)
  androidTestImplementation(testLibs.androidx.test.core.ktx)
  androidTestImplementation(testLibs.androidx.test.ext.junit.ktx)
  androidTestImplementation(testLibs.mockito.android)
  androidTestImplementation(testLibs.mockito.kotlin)
  androidTestImplementation(testLibs.mockk.android)
  androidTestImplementation(testLibs.square.okhttp.mockserver)

  androidTestUtil(testLibs.androidx.test.orchestrator)
}

fun assertIsGitRepo() {
  if (!file("${project.rootDir}/.git").exists()) {
    throw IllegalStateException("Must be a git repository to guarantee reproducible builds! (git hash is part of APK)")
  }
}

fun getLastCommitTimestamp(): String {
  assertIsGitRepo()

  ByteArrayOutputStream().use { stdout ->
    exec {
      commandLine = listOf("git", "log", "-1", "--pretty=format:%ct000")
      standardOutput = stdout
    }

    return stdout.toString().trim()
  }
}

fun getGitHash(): String {
  assertIsGitRepo()

  ByteArrayOutputStream().use { stdout ->
    exec {
      commandLine = listOf("git", "rev-parse", "--short=12", "HEAD")
      standardOutput = stdout
    }

    return stdout.toString().trim()
  }
}

fun getCommitTag(): String {
  assertIsGitRepo()

  ByteArrayOutputStream().use { stdout ->
    exec {
      commandLine = listOf("git", "describe", "--tags", "--exact-match")
      standardOutput = stdout
    }

    return stdout.toString().trim().takeIf { it.isNotEmpty() } ?: "untagged"
  }
}

tasks.withType<Test>().configureEach {
  testLogging {
    events("failed")
    exceptionFormat = TestExceptionFormat.FULL
    showCauses = true
    showExceptions = true
    showStackTraces = true
  }
}

fun Project.languageList(): List<String> {
  return fileTree("src/main/res") { include("**/strings.xml") }
    .map { stringFile -> stringFile.parentFile.name }
    .map { valuesFolderName -> valuesFolderName.replace("values-", "") }
    .filter { valuesFolderName -> valuesFolderName != "values" }
    .map { languageCode -> languageCode.replace("-r", "_") }
    .distinct() + "en"
}

fun String.capitalize(): String {
  return this.replaceFirstChar { it.uppercase() }
}
