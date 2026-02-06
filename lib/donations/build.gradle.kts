plugins {
  id("signal-library")
  id("kotlin-parcelize")
}

android {
  namespace = "org.signal.donations"

  buildFeatures {
    buildConfig = true
  }
}

dependencies {
  implementation(project(":core:util"))

  implementation(libs.kotlin.reflect)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.jackson.core)

  testImplementation(testLibs.robolectric.robolectric) {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
  }

  api(libs.square.okhttp3)
}
