pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("(com\\.(android|google)|androidx?)(\\..*)?")
      }
    }
    mavenCentral()
  }
  includeBuild("build-logic")
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google {
      content {
        includeGroupByRegex("(com\\.(android|google)|androidx?)(\\..*)?")
      }
    }
    // MOLLY: Prebuilt libraries added via the deps-overlay stage in the Dockerfile
    maven {
      url = uri(rootDir.resolve("prebuilts/ringrtc"))
      content {
        includeModule("im.molly", "ringrtc-android")
      }
    }
    maven {
      url = uri(rootDir.resolve("prebuilts/libsignal"))
      content {
        includeModule("im.molly", "libsignal-client")
        includeModule("im.molly", "libsignal-android")
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
include(":core:network")
include(":core:ui")
include(":core:serialization")
include(":core:database")

// FOSS GMS modules
include(":core-gms:base")
include(":core-gms:cloud-messaging")
include(":core-gms:safeparcel")
include(":core-gms:safeparcel-processor")
include(":core-gms:tasks")

// Lib modules
include(":lib:libsignal-service")
include(":lib:netcipher")
include(":lib:network")
include(":lib:glide")
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
include(":lib:blurhash")
include(":lib:apng")
include(":lib:emoji")
include(":lib:archive")
include(":lib:ui-components")
include(":lib:signal-login")
include(":lib:password-manager")

// Feature modules
include(":feature:app-settings")
include(":feature:registration")
include(":feature:camera")
include(":feature:media-send")
include(":feature:chat-settings")
include(":feature:media-keyboard")
include(":feature:media-viewer")

// Testing/Lint modules
include(":lintchecks")
include(":fast-lint")

rootProject.name = "Molly"
