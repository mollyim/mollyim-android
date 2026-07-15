plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.jetbrains.kotlin.android) apply false
  alias(libs.plugins.jetbrains.kotlin.jvm) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.licensee) apply false
  id("dependency-verification")
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
    classpath("com.squareup.wire:wire-gradle-plugin:6.4.0") {
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
  tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
  }
}

abstract class ConcurrencyConstraintService : BuildService<BuildServiceParameters.None>

fun limiter(name: String, max: Int) =
  gradle.sharedServices.registerIfAbsent(name, ConcurrencyConstraintService::class.java) {
    maxParallelUsages.set(max)
  }

// MOLLY: Limit concurrency of high-RAM tasks to avoid OOMs (especially in CI)
val kLimiter = limiter("CC-kotlin", max = 3)
val lintLimiter = limiter("CC-lint", max = 1)
val r8Limiter = limiter("CC-r8", max = 1)

gradle.projectsEvaluated {
  val kotlinCompiles = allprojects
    .flatMap { it.tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() }

  kotlinCompiles.forEach { task ->
    task.usesService(kLimiter)
  }

  allprojects {
    tasks.named { it.startsWith("lint") && it.contains("Analyze") }.configureEach {
      usesService(lintLimiter)
      mustRunAfter(kotlinCompiles)
    }

    tasks.named { it.startsWith("minify") }.configureEach {
      usesService(r8Limiter)
    }
  }
}

// MOLLY: Add task `./gradlew allDeps` to list all dependencies for each configuration
allprojects {
  tasks.register<DependencyReportTask>("allDeps")
}

tasks.register("clean", Delete::class) {
  delete(rootProject.layout.buildDirectory)
}
