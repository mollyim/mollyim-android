plugins {
  id("org.jetbrains.kotlin.jvm")
  id("java-library")
}

val signalJavaVersion: JavaVersion by rootProject.extra

java {
  sourceCompatibility = signalJavaVersion
  targetCompatibility = signalJavaVersion
}

dependencies {
  implementation(gradleApi())

  implementation(libs.dnsjava)
  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.mockk)
}
