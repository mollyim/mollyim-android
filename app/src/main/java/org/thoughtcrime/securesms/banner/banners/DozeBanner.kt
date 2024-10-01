/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.PowerManagerCompat
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences

class DozeBanner(private val context: Context) : Banner<Unit>() {

  override val enabled: Boolean
    get() = !SignalStore.account.pushAvailable && !TextSecurePreferences.hasPromptedOptimizeDoze(context) && !ServiceUtil.getPowerManager(context).isIgnoringBatteryOptimizations(context.packageName)

  override val dataFlow: Flow<Unit>
    get() = flowOf(Unit)

  @Composable
  override fun DisplayBanner(model: Unit, contentPadding: PaddingValues) {
    Banner(
      contentPadding = contentPadding,
      onDismissListener = {
        TextSecurePreferences.setPromptedOptimizeDoze(context, true)
      },
      onOkListener = {
        TextSecurePreferences.setPromptedOptimizeDoze(context, true)
        PowerManagerCompat.requestIgnoreBatteryOptimizations(context)
      }
    )
  }
}

@Composable
private fun Banner(contentPadding: PaddingValues, onDismissListener: () -> Unit = {}, onOkListener: () -> Unit = {}) {
  DefaultBanner(
    title = stringResource(id = dozeTitle),
    body = stringResource(id = dozeBody),
    onDismissListener = onDismissListener,
    actions = listOf(
      Action(android.R.string.ok) {
        onOkListener()
      }
    ),
    paddingValues = contentPadding
  )
}

@StringRes
private val dozeTitle: Int = if (BuildConfig.USE_PLAY_SERVICES) {
  R.string.DozeReminder_optimize_for_missing_play_services
} else {
  R.string.DozeReminder_optimize_for_timely_notifications
}

@StringRes
private val dozeBody: Int = if (BuildConfig.USE_PLAY_SERVICES) {
  R.string.DozeReminder_this_device_does_not_support_play_services_tap_to_disable_system_battery
} else {
  R.string.DozeReminder_tap_to_allow_molly_to_retrieve_messages_while_the_device_is_in_standby
}

@SignalPreview
@Composable
private fun BannerPreview() {
  Previews.Preview {
    Banner(contentPadding = PaddingValues(0.dp))
  }
}
