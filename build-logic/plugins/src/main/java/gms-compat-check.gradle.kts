/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

val catalogs = project.extensions.getByType<VersionCatalogsExtension>()
val firebaseMessaging = catalogs.named("libs").findLibrary("firebase-messaging")
  .orElseThrow { GradleException("firebase-messaging must be declared in the main version catalog") }
  .get()
val messagingVersion = firebaseMessaging.versionConstraint.requiredVersion
val requestedDependency = "${firebaseMessaging.module.group}:${firebaseMessaging.module.name}:$messagingVersion"

require(messagingVersion.isNotBlank()) {
  "firebase-messaging must have a required version for the core-gms compatibility check"
}

// Keep this graph independent of the app's constraints and exclusions so supported-but-excluded modules
// can be compared with the baseline. Register it eagerly so Gradle's dependency verification writer can
// discover it.
val compatibilityGraph = configurations.create("gmsCompatibility") {
  isCanBeConsumed = false
  isCanBeResolved = true
}
dependencies.add(compatibilityGraph.name, requestedDependency)

val checkCompatibility = tasks.register<CheckGmsCompatibilityTask>("checkGmsCompatibility") {
  group = "verification"
  description = "Checks core-gms compatibility with Firebase Messaging."

  val gmsCompatCatalog = catalogs.named("playServices")
  val gmsGroup = "com.google.android.gms"
  val extraModules = setOf("com.google.firebase:firebase-iid-interop")

  val supported = gmsCompatCatalog.libraryAliases.associate { alias ->
    val library = gmsCompatCatalog.findLibrary(alias).get().get()
    "${library.module.group}:${library.module.name}" to library.versionConstraint.requiredVersion
  }

  val resolutionResult = compatibilityGraph.incoming.resolutionResult
  // Without app constraints, the selected version is the minimum satisfying Firebase's dependencies.
  val minimumRequired = resolutionResult.rootComponent.map {
    resolutionResult.allComponents
      .mapNotNull { component -> component.moduleVersion }
      .filter { module -> module.group == gmsGroup || "${module.group}:${module.name}" in extraModules }
      .associate { module -> "${module.group}:${module.name}" to module.version }
  }

  // Named-domain mapping keeps this lazy until the configured application is evaluated.
  val excluded = configurations.named("implementation").map {
    val messageDependency = it.dependencies.withType(ExternalModuleDependency::class.java).singleOrNull { dep ->
      dep.group == firebaseMessaging.module.group && dep.name == firebaseMessaging.module.name
    } ?: throw GradleException("Expected exactly one implementation dependency on ${firebaseMessaging.module.group}:${firebaseMessaging.module.name}")

    messageDependency.excludeRules.mapTo(mutableSetOf()) { rule ->
      "${rule.group}:${rule.module}"
    }
  }

  dependency.set(requestedDependency)
  supportedVersions.set(supported)
  minRequiredVersions.putAll(minimumRequired)
  excludedModules.set(excluded)
}

tasks.named("check") {
  dependsOn(checkCompatibility)
}
