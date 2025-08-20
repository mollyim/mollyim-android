val signalBuildToolsVersion by extra("35.0.0")      // MOLLY: Dockerfile must match this version
val signalCompileSdkVersion by extra("android-35")  // MOLLY: Dockerfile must match this version
val signalTargetSdkVersion by extra(35)
val signalMinSdkVersion by extra(27)
val signalNdkVersion by extra("28.0.13004108")      // MOLLY: Dockerfile must match this version
val signalJavaVersion by extra(JavaVersion.VERSION_17)
val signalKotlinJvmTarget by extra("17")
