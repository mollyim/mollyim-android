plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.jetbrains.kotlin.android) apply false
  alias(libs.plugins.jetbrains.kotlin.jvm) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.licensee) apply false
  id("mollyify")
}

buildscript {
  repositories {
    google {
      content {
        includeGroupByRegex("(com\\.(android|google)|androidx?)(\\..*)?")
      }
    }
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
    // MOLLY: wire-handler factory now in buildSrc, no manual classpath needed
    classpath(libs.com.google.devtools.ksp.gradle.plugin)
  }
}

tasks.withType<Wrapper> {
  distributionType = Wrapper.DistributionType.ALL
}

allprojects {
  tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
  }
}

if (JavaVersion.current().isJava8Compatible) {
  allprojects {
    tasks.withType<Javadoc> {
      (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }
  }
}

interface ConcurrencyConstraintService : BuildService<BuildServiceParameters.None>

subprojects {
  // MOLLY: Limit concurrency of high-RAM tasks to avoid OOMs (especially in CI).
  fun limiter(name: String, max: Int) =
    gradle.sharedServices.registerIfAbsent(name, ConcurrencyConstraintService::class.java) {
      maxParallelUsages.set(max)
    }

  val lintLimiter = limiter("CC-lint", max = 1)
  val r8Limiter = limiter("CC-r8", max = 1)
  val kLimiter = limiter("CC-kotlin", max = 3)

  tasks.configureEach {
    val service = when (this) {
      is com.android.build.gradle.internal.lint.AndroidLintAnalysisTask -> lintLimiter
      is com.android.build.gradle.internal.tasks.R8Task -> r8Limiter
      is org.jetbrains.kotlin.gradle.tasks.KotlinCompile -> kLimiter
      else -> null
    }
    service?.let(::usesService)
  }

  // MOLLY: Add task `./gradlew allDeps` to list all dependencies for each configuration
  tasks.register<DependencyReportTask>("allDeps")
}

tasks.register("clean", Delete::class) {
  delete(rootProject.layout.buildDirectory)
}
