/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.lang.module.ModuleDescriptor

/** Checks that core-gms supports the interface versions required by Firebase Messaging. */
abstract class CheckGmsCompatibilityTask : DefaultTask() {
  @get:Input
  abstract val dependency: Property<String>

  @get:Input
  abstract val supportedVersions: MapProperty<String, String>

  @get:Input
  abstract val minRequiredVersions: MapProperty<String, String>

  @get:Input
  abstract val excludedModules: SetProperty<String>

  @TaskAction
  fun checkCompatibility() {
    val dep = dependency.get()
    val supported = supportedVersions.get()
    val required = minRequiredVersions.get()
    val exclusions = excludedModules.get()
    val failures = mutableListOf<String>()

    for ((module, requiredVersion) in required) {
      if (module !in exclusions) continue // Bundled externally; Licensee checks its license.
      val supportedVersion = supported[module] ?: continue // Not a core-gms baseline module.

      val comparison = compareVersions(requiredVersion, supportedVersion)
      if (comparison == null) {
        failures += "$module: cannot compare required $requiredVersion with supported $supportedVersion"
      } else if (comparison > 0) {
        failures += "$module: Firebase requires $requiredVersion, but core-gms supports $supportedVersion"
      }
    }

    if (failures.isNotEmpty()) {
      throw GradleException(
        "core-gms compatibility checks failed for $dep. Review the modules below, then update " +
          "core-gms/play-services.versions.toml as needed.\n" +
          failures.joinToString("\n"),
      )
    }

    logger.lifecycle("core-gms supports the interfaces required by $dep.")
  }

  /** Returns null when either version cannot be parsed. */
  private fun compareVersions(required: String, supported: String): Int? =
    runCatching {
      ModuleDescriptor.Version.parse(required).compareTo(ModuleDescriptor.Version.parse(supported))
    }.getOrNull()
}
