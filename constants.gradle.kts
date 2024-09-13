// MOLLY: Edit Dockerfile to download the same SDK packages
val signalBuildToolsVersion by extra("34.0.0")
val signalCompileSdkVersion by extra("android-34")
val signalTargetSdkVersion by extra(34)
val signalMinSdkVersion by extra(26)
val signalNdkVersion by extra("27.0.12077973")
val signalJavaVersion by extra(JavaVersion.VERSION_17)
val signalKotlinJvmTarget by extra("17")