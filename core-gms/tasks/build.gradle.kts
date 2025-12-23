plugins {
  id("signal-library")
}

android {
  namespace = "com.google.android.gms.tasks"
}

dependencies {
  implementation(project(":core-gms:base"))
}
