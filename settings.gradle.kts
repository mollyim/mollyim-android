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
    google {
      content {
        includeGroupByRegex("com\\.android(\\..*)?")
        includeGroupByRegex("com\\.google(\\..*)?")
        includeGroupByRegex("androidx?(\\..*)?")
      }
    }
    mavenLocal {
      content {
        includeGroup("im.molly")
        includeGroup("org.signal")
      }
    }
    maven {
      url = uri("https://dl.cloudsmith.io/public/mollyim/ringrtc/maven/")
      content {
        includeModule("im.molly", "ringrtc-android")
      }
    }
    maven {
      url = uri("https://dl.cloudsmith.io/public/mollyim/libsignal/maven/")
      content {
        includeModule("im.molly", "libsignal-client")
        includeModule("im.molly", "libsignal-android")
      }
    }
    maven {
      url = uri("https://raw.githubusercontent.com/signalapp/maven/master/sqlcipher/release/")
      content {
        includeModule("org.signal", "sqlcipher-android")
      }
    }
    maven {
      url = uri("https://raw.githubusercontent.com/signalapp/maven/master/aesgcmprovider/release/")
      content {
        includeModule("org.signal", "aesgcmprovider")
      }
    }
    mavenCentral()
  }
  versionCatalogs {
    // libs.versions.toml is automatically registered.
    create("testLibs") {
      from(files("gradle/test-libs.versions.toml"))
    }
    create("lintLibs") {
      from(files("gradle/lint-libs.versions.toml"))
    }
  }
}

// To build libsignal from source, set the libsignalClientPath property in gradle.properties.
val libsignalClientPath = if (extra.has("libsignalClientPath")) extra.get("libsignalClientPath") else null
if (libsignalClientPath is String) {
  includeBuild(rootDir.resolve(libsignalClientPath + "/java")) {
    name = "libsignal-client"
    dependencySubstitution {
      substitute(module("im.molly:libsignal-client")).using(project(":client"))
      substitute(module("im.molly:libsignal-android")).using(project(":android"))
    }
  }
}

// Main app
include(":app")

// Core modules
include(":core:util")
include(":core:util-jvm")
include(":core:models")
include(":core:models-jvm")
include(":core:ui")

// FOSS GMS modules
include(":core-gms:base")
include(":core-gms:cloud-messaging")
include(":core-gms:safeparcel")
include(":core-gms:safeparcel-processor")
include(":core-gms:tasks")

// Lib modules
include(":lib:libsignal-service")
include(":lib:netcipher")
include(":lib:glide-config")
include(":lib:photoview")
include(":lib:sticky-header-grid")
include(":lib:paging")
include(":lib:device-transfer")
include(":lib:donations")
include(":lib:contacts")
include(":lib:qr")
include(":lib:video")
include(":lib:image-editor")
include(":lib:debuglogs-viewer")

// Feature modules
include(":feature:registration")

// Testing/Lint modules
include(":lintchecks")

rootProject.name = "Molly"
