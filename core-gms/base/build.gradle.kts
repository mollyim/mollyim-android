plugins {
  id("signal-library")
}

val gmsVersionCode = 12451000

android {
  namespace = "com.google.android.gms"

  buildFeatures {
    buildConfig = true
  }

  defaultConfig {
    buildConfigField("int", "GMS_VERSION_CODE", gmsVersionCode.toString())
    resValue("integer", "google_play_services_version", gmsVersionCode.toString())
  }

  lint {
    disable += setOf("LogNotSignal")
  }
}
