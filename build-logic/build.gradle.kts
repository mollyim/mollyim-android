plugins {
  alias(libs.plugins.jetbrains.kotlin.jvm) apply false
}

buildscript {
  repositories {
    maven("https://maven-central.storage.googleapis.com/maven2")
    mavenCentral()
  }
}
