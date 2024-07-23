plugins {
  id("signal-library")
  id("kotlin-parcelize")
}

android {
  namespace = "org.signal.donations"

  flavorDimensions += "license"

  productFlavors {
    create("gms") {
      dimension = "license"
      isDefault = true
    }

    create("foss") {
      dimension = "license"
    }
  }
}

dependencies {
  implementation(project(":core-util"))

  implementation(libs.kotlin.reflect)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.jackson.core)

  testImplementation(testLibs.robolectric.robolectric) {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
  }

  "gmsApi"(libs.google.play.services.wallet)
  "fossApi"(project(":libfakegms"))
  api(libs.square.okhttp3)
}
