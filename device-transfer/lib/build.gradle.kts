plugins {
  id("signal-library")
}

android {
  namespace = "org.signal.devicetransfer"
}

dependencies {
  implementation(project(":core-util"))
  implementation(libs.libsignal.android)
  api(libs.greenrobot.eventbus)

  testImplementation(testLibs.robolectric.robolectric) {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
  }
  testImplementation(testLibs.hamcrest.hamcrest)

  testImplementation(testFixtures(project(":libsignal-service")))
}
