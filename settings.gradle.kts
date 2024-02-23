pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
  includeBuild("build-logic")
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    mavenLocal()
    maven {
      url = uri("https://raw.githubusercontent.com/mollyim/maven/master/argon2/releases/")
      content {
        includeModule("im.molly", "argon2")
      }
    }
    maven {
      url = uri("https://raw.githubusercontent.com/mollyim/maven/master/ringrtc/releases/")
      content {
        includeModule("im.molly", "ringrtc-android")
      }
    }
    maven {
      url = uri("https://raw.githubusercontent.com/mollyim/maven/master/native-utils/releases/")
      content {
        includeModule("im.molly", "native-utils")
      }
    }
    maven {
      url = uri("https://raw.githubusercontent.com/mollyim/maven/master/glide-webp-decoder/releases/")
      content {
        includeModule("im.molly", "glide-webp-decoder")
      }
    }
    maven {
      url = uri("https://raw.githubusercontent.com/signalapp/maven/master/sqlcipher/release/")
      content {
        includeModule("org.signal", "sqlcipher-android")
      }
    }
    maven {
      url = uri("https://dl.cloudsmith.io/qxAgwaeEE1vN8aLU/mobilecoin/mobilecoin/maven/")
      content {
        includeModule("com.mobilecoin", "android-bindings")
        includeModule("com.mobilecoin", "android-sdk")
      }
    }
  }
}

include(":app")
include(":libsignal-service")
include(":libfakegms")
include(":libnetcipher")
include(":lintchecks")
include(":paging")
//include(":paging-app")
include(":core-util")
include(":core-util-jvm")
include(":glide-config")
include(":device-transfer")
//include(":device-transfer-app")
include(":image-editor")
//include(":image-editor-app")
include(":spinner")
//include(":spinner-app")
include(":contacts")
//include(":contacts-app")
include(":qr")
//include(":qr-app")
include(":sticky-header-grid")
include(":photoview")
include(":core-ui")
include(":video")
include(":video-app")

project(":paging").projectDir = file("paging/lib")
//project(":paging-app").projectDir = file("paging/app")

project(":device-transfer").projectDir = file("device-transfer/lib")
//project(":device-transfer-app").projectDir = file("device-transfer/app")

project(":image-editor").projectDir = file("image-editor/lib")
//project(":image-editor-app").projectDir = file("image-editor/app")

project(":spinner").projectDir = file("spinner/lib")
//project(":spinner-app").projectDir = file("spinner/app")

project(":contacts").projectDir = file("contacts/lib")
//project(":contacts-app").projectDir = file("contacts/app")

project(":qr").projectDir = file("qr/lib")
//project(":qr-app").projectDir = file("qr/app")

project(":video").projectDir = file("video/lib")
//project(":video-app").projectDir = file("video/app")

rootProject.name = "Molly"

apply(from = "dependencies.gradle.kts")
