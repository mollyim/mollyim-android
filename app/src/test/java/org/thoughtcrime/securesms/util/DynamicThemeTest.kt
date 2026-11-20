/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.color.DynamicColors
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.PlainTextKeyValueStore

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class DynamicThemeTest {

  private val context: Application
    get() = ApplicationProvider.getApplicationContext()

  private lateinit var activity: Activity
  private var dynamicColorsAvailable = true

  @Before
  fun setUp() {
    PlainTextKeyValueStore.init(context)
    PlainTextKeyValueStore.isDynamicColorsEnabled = false
    dynamicColorsAvailable = true

    mockkStatic(DynamicColors::class)
    every { DynamicColors.isDynamicColorAvailable() } answers { dynamicColorsAvailable }

    activity = mockk<Activity>()
    every { activity.resources } returns context.resources
    every { activity.setTheme(any<Int>()) } just Runs
    every { activity.recreate() } just Runs
  }

  @After
  fun tearDown() {
    PlainTextKeyValueStore.isDynamicColorsEnabled = false
    unmockkStatic(DynamicColors::class)
  }

  @Test
  fun `unchanged dynamic color selection does not recreate on resume`() {
    val theme = DynamicTheme()
    theme.onCreate(activity)

    theme.onResume(activity)

    verify(exactly = 0) { activity.recreate() }
    verify { activity.setTheme(R.style.Signal_DayNight) }
  }

  @Test
  fun `dynamic color change recreates once and the replacement helper starts with the new selection`() {
    val theme = DynamicTheme()
    theme.onCreate(activity)
    PlainTextKeyValueStore.isDynamicColorsEnabled = true

    theme.onResume(activity)

    val recreatedTheme = DynamicTheme()
    recreatedTheme.onCreate(activity)
    recreatedTheme.onResume(activity)

    verify(exactly = 1) { activity.recreate() }
    verify { activity.setTheme(R.style.Theme_Molly_Dynamic) }
  }

  @Test
  fun `net-zero dynamic color change does not recreate on resume`() {
    val theme = DynamicTheme()
    theme.onCreate(activity)
    PlainTextKeyValueStore.isDynamicColorsEnabled = true
    PlainTextKeyValueStore.isDynamicColorsEnabled = false

    theme.onResume(activity)

    verify(exactly = 0) { activity.recreate() }
  }

  @Test
  fun `dynamic color availability change recreates the activity`() {
    PlainTextKeyValueStore.isDynamicColorsEnabled = true
    val theme = DynamicTheme()
    theme.onCreate(activity)
    dynamicColorsAvailable = false

    theme.onResume(activity)

    verify(exactly = 1) { activity.recreate() }
    verify { activity.setTheme(R.style.Theme_Molly_Dynamic) }
  }
}
