plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.jetbrains.kotlin.android) apply false
  alias(libs.plugins.jetbrains.kotlin.jvm) apply false
  alias(libs.plugins.compose.compiler) apply false
}

buildscript {
  repositories {
    google()
    mavenCentral()
  }

  dependencies {
    classpath(libs.gradle)
    classpath(libs.androidx.navigation.safe.args.gradle.plugin)
    classpath(libs.protobuf.gradle.plugin)
    classpath("com.squareup.wire:wire-gradle-plugin:4.4.3") {
      exclude(group = "com.squareup.wire", module = "wire-swift-generator")
      exclude(group = "com.squareup.wire", module = "wire-grpc-client")
      exclude(group = "com.squareup.wire", module = "wire-grpc-jvm")
      exclude(group = "com.squareup.wire", module = "wire-grpc-server-generator")
      exclude(group = "io.outfoxx", module = "swiftpoet")
    }
    classpath(libs.androidx.benchmark.gradle.plugin)
    classpath(files("$rootDir/wire-handler/wire-handler-1.0.0.jar"))
    classpath(libs.com.google.devtools.ksp.gradle.plugin)
  }
}

tasks.withType<Wrapper> {
  distributionType = Wrapper.DistributionType.ALL
}

apply(from = "$rootDir/constants.gradle.kts")

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
