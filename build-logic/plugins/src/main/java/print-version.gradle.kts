/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import com.android.build.api.dsl.ApplicationExtension
import groovy.json.JsonOutput

val android = extensions.getByType<ApplicationExtension>()

val versionCodeProvider = provider {
  android.defaultConfig.versionCode
}
val versionNameProvider = provider {
  android.defaultConfig.versionName
}

tasks.register("printVersion") {
  group = "help"
  description = "Prints app version information as JSON."

  doLast {
    val versionInfo = mapOf(
      "android" to mapOf(
        "versionCode" to versionCodeProvider.orNull,
        "versionName" to versionNameProvider.orNull,
      ),
    )

    println(JsonOutput.prettyPrint(JsonOutput.toJson(versionInfo)))
  }
}
