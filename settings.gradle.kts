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

includeProject("app")
includeProject("libsignal-service")
includeProject("core-gms:base", "core-gms/base")
includeProject("core-gms:cloud-messaging", "core-gms/cloud-messaging")
includeProject("core-gms:safeparcel", "core-gms/safeparcel")
includeProject("core-gms:safeparcel-processor", "core-gms/safeparcel-processor")
includeProject("core-gms:tasks", "core-gms/tasks")
includeProject("libnetcipher")
includeProject("lintchecks")
includeProject("paging", "paging/lib")
includeProject("core-util")
includeProject("core-util-jvm")
includeProject("core-models")
includeProject("glide-config")
includeProject("device-transfer", "device-transfer/lib")
includeProject("image-editor", "image-editor/lib")
includeProject("donations", "donations/lib")
includeProject("debuglogs-viewer", "debuglogs-viewer/lib")
includeProject("contacts", "contacts/lib")
includeProject("qr", "qr/lib")
includeProject("sticky-header-grid")
includeProject("photoview")
includeProject("core-ui")
includeProject("video", "video/lib")
includeProject("registration", "registration/lib")

rootProject.name = "Molly"

fun includeProject(projectName: String, projectRoot: String = projectName) {
  val projectId = ":$projectName"
  include(projectId)
  project(projectId).projectDir = file(projectRoot)
}
