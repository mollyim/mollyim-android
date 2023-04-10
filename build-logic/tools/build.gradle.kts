plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(libs.dnsjava)
    testImplementation(testLibs.junit.junit)
    testImplementation(testLibs.mockk)
}
