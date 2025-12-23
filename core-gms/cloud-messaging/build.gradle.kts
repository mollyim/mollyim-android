plugins {
  id("signal-library")
}

android {
  namespace = "com.google.android.gms.cloudmessaging"

  buildFeatures {
    buildConfig = true
  }

  defaultConfig {
    buildConfigField("boolean", "NOTIFICATION_PAYLOAD_ENABLED", "false")
  }

  lint {
    disable += setOf("LogNotSignal")
  }
}

dependencies {
  api(project(":core-gms:base"))
  api(project(":core-gms:safeparcel"))
  api(project(":core-gms:tasks"))
  annotationProcessor(project(":core-gms:safeparcel-processor"))
}
