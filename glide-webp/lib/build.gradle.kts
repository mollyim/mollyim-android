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
}

dependencies {
  implementation(project(":core-util"))

  implementation(libs.glide.glide)
  implementation(libs.molly.glide.webp.decoder)
  kapt(libs.glide.compiler)
}
