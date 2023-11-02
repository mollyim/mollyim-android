/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
  id("signal-library")
  kotlin("kapt")
}

android {
  namespace = "org.signal.glide.webp"

  defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
}

dependencies {
  implementation(project(":core-util"))

  implementation(libs.glide.glide)
  implementation(libs.molly.glide.webp.decoder)
  kapt(libs.glide.compiler)

  androidTestImplementation(testLibs.androidx.test.core)
  androidTestImplementation(testLibs.androidx.test.core.ktx)
  androidTestImplementation(testLibs.androidx.test.ext.junit)
  androidTestImplementation(testLibs.androidx.test.ext.junit.ktx)
  androidTestImplementation(testLibs.androidx.test.monitor)
  androidTestImplementation(testLibs.androidx.test.runner)
}
