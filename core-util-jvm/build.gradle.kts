/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

val signalJavaVersion: JavaVersion by rootProject.extra

plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
}

java {
  sourceCompatibility = signalJavaVersion
  targetCompatibility = signalJavaVersion
}

dependencies {
  testImplementation(libs.kotlin.reflect.tests)

  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.assertj.core)
}
