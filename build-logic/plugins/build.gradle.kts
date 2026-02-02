plugins {
  `kotlin-dsl`
}

java {
  sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
  targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(libs.versions.kotlinJvmTarget.get()))
  }
  compilerOptions {
    suppressWarnings = true
  }
}

dependencies {
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.android.library)
  implementation(libs.android.application)

  // These allow us to reference the dependency catalog inside of our compiled plugins
  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
  implementation(files(testLibs.javaClass.superclass.protectionDomain.codeSource.location))
}
