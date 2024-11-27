buildscript {
  rootProject.extra["kotlin_version"] = "1.9.20"
  repositories {
    google()
    mavenCentral()
  }

  dependencies {
    classpath("com.android.tools.build:gradle:8.4.1")
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
    classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:1.9.20-1.0.14")
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
}

interface ConcurrencyConstraintService : BuildService<BuildServiceParameters.None>

subprojects {
  if (JavaVersion.current().isJava8Compatible) {
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
    org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class,
    com.android.build.gradle.internal.lint.AndroidLintAnalysisTask::class,
    com.android.build.gradle.internal.tasks.R8Task::class,
  )

  tasks.configureEach {
    if (expensiveTaskClasses.any { it.isInstance(this) }) {
      usesService(limiterService)
    }
  }

  // MOLLY: Add task `./gradlew allDeps` to list all dependencies for each configuration
  tasks.register<DependencyReportTask>("allDeps") { }
}

tasks.register("clean", Delete::class) {
  delete(rootProject.layout.buildDirectory)
}
