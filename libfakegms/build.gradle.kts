plugins {
  id("signal-library")
}

android {
  namespace = "com.google.android.gms"
}

dependencies {
  implementation(libs.androidx.fragment)
}
