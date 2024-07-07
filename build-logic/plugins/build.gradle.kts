import org.gradle.kotlin.dsl.extra

plugins {
  `kotlin-dsl`
}

val signalJavaVersion: JavaVersion by rootProject.extra
val signalKotlinJvmTarget: String by rootProject.extra

java {
  sourceCompatibility = signalJavaVersion
  targetCompatibility = signalJavaVersion
}

kotlinDslPluginOptions {
  jvmTarget.set(signalKotlinJvmTarget)
}

dependencies {
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.android.library)
  implementation(libs.android.application)

  // These allow us to reference the dependency catalog inside of our compiled plugins
  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
  implementation(files(testLibs.javaClass.superclass.protectionDomain.codeSource.location))
}
