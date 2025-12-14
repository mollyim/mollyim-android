plugins {
  java
}

dependencies {
  implementation(libs.androidx.annotation)
  implementation(libs.jsilver)
}

tasks.jar {
  exclude("com.google.android.gms.common.internal.safeparcel/SafeParcelable.class")
}
