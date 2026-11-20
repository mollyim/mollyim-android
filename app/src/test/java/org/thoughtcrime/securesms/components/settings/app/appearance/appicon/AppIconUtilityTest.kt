/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance.appicon

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.appicon.AppIconPreset
import org.signal.appsettings.appicon.AppIconUtility
import org.thoughtcrime.securesms.R

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppIconUtilityTest {

  private val context = ApplicationProvider.getApplicationContext<Application>()

  @Test
  fun `picker icons are the exact icons declared by the launcher manifest`() {
    val expectedIcons = mapOf(
      AppIconPreset.DEFAULT to R.mipmap.ic_launcher,
      AppIconPreset.WHITE to R.mipmap.ic_launcher_alt_light,
      AppIconPreset.COLOR to R.mipmap.ic_launcher_alt_signal,
      AppIconPreset.DARK to R.mipmap.ic_launcher_alt_colorful,
      AppIconPreset.DARK_VARIANT to R.mipmap.ic_launcher_alt_gold,
      AppIconPreset.CHAT to R.mipmap.ic_launcher_alt_neon,
      AppIconPreset.BUBBLES to R.mipmap.ic_launcher_alt_xmas,
      AppIconPreset.YELLOW to R.mipmap.ic_launcher_alt_heart,
      AppIconPreset.NEWS to R.mipmap.ic_launcher_alt_zen,
      AppIconPreset.NOTES to R.mipmap.ic_launcher_alt_notes,
      AppIconPreset.WEATHER to R.mipmap.ic_launcher_alt_moon,
      AppIconPreset.WAVES to R.mipmap.ic_launcher_alt_music
    )

    val metadata = AppIconUtility(context).appIconMetadata

    assertThat(metadata.keys).containsExactlyInAnyOrder(*expectedIcons.keys.toTypedArray())
    expectedIcons.forEach { (preset, expectedIcon) ->
      assertThat(metadata.getValue(preset).iconResId).isEqualTo(expectedIcon)
    }
  }

  @Test
  fun `picker labels are the localized labels declared for the Molly aliases`() {
    val metadata = AppIconUtility(context).appIconMetadata
    val customLabels = mapOf(
      AppIconPreset.NEWS to R.string.app_icon_label_zen,
      AppIconPreset.NOTES to org.signal.appsettings.R.string.app_icon_label_notes,
      AppIconPreset.WEATHER to R.string.app_icon_label_moon,
      AppIconPreset.WAVES to R.string.app_icon_label_music
    )
    val label = context.applicationInfo.loadLabel(context.packageManager).toString()

    AppIconPreset.entries.forEach { preset ->
      val expectedLabel = customLabels[preset]?.let(context::getString) ?: label
      assertThat(metadata.getValue(preset).label).isEqualTo(expectedLabel)
    }
  }
}
