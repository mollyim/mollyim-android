import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val signalKotlinJvmTarget: String by rootProject.extra

buildscript {
  rootProject.extra["kotlin_version"] = "1.8.10"
  repositories {
    google()
    mavenCentral()
  }

  dependencies {
    classpath("com.android.tools.build:gradle:8.0.2")
    classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.5.3")
    classpath("com.google.protobuf:protobuf-gradle-plugin:0.9.0")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${rootProject.extra["kotlin_version"] as String}")
    classpath("app.cash.exhaustive:exhaustive-gradle:0.1.1")
    classpath("com.squareup.wire:wire-gradle-plugin:4.4.3") {
      exclude(group = "com.squareup.wire", module = "wire-swift-generator")
      exclude(group = "com.squareup.wire", module = "wire-grpc-client")
      exclude(group = "com.squareup.wire", module = "wire-grpc-jvm")
      exclude(group = "com.squareup.wire", module = "wire-grpc-server-generator")
      exclude(group = "io.outfoxx", module = "swiftpoet")
    }
    classpath("androidx.benchmark:benchmark-gradle-plugin:1.1.0-beta04")
    classpath(files("$rootDir/wire-handler/wire-handler-1.0.0.jar"))
  }
}

tasks.withType<Wrapper> {
  distributionType = Wrapper.DistributionType.ALL
}

apply(from = "${rootDir}/constants.gradle.kts")

allprojects {
  tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
  }

  // Needed because otherwise the kapt task defaults to jvmTarget 17, which "poisons the well" and requires us to bump up too
  tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
      jvmTarget = signalKotlinJvmTarget
    }
  }
}

interface ConcurrencyConstraintService : BuildService<BuildServiceParameters.None>

subprojects {
  if (JavaVersion.current().isJava8Compatible()) {
    allprojects {
      tasks.withType<Javadoc> {
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
      }
    }
  }

  val limiterService = gradle.sharedServices.registerIfAbsent("concurrencyConstraint", ConcurrencyConstraintService::class.java) {
    maxParallelUsages.set(1)
  }

  val expensiveTaskClasses = listOf(
    KotlinCompile::class,
    com.android.build.gradle.internal.lint.AndroidLintAnalysisTask::class,
    com.android.build.gradle.internal.tasks.R8Task::class,
  )

  tasks.configureEach {
    if (expensiveTaskClasses.any { it.isInstance(this) }) {
      usesService(limiterService)
    }
  }
}

tasks.register("clean", Delete::class) {
  delete(rootProject.buildDir)
}
