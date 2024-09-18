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
import kotlinx.coroutines.flow.Flow
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.DismissibleBannerProducer
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.PowerManagerCompat
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences

class DozeBanner(private val context: Context, val dismissed: Boolean, private val onDismiss: () -> Unit) : Banner() {
  override val enabled: Boolean = !dismissed &&
    !SignalStore.account.pushAvailable && !TextSecurePreferences.hasPromptedOptimizeDoze(context) && !ServiceUtil.getPowerManager(context).isIgnoringBatteryOptimizations(context.packageName)

  @Composable
  override fun DisplayBanner(contentPadding: PaddingValues) {
    DefaultBanner(
      title = stringResource(id = dozeTitle),
      body = stringResource(id = dozeBody),
      onDismissListener = {
        TextSecurePreferences.setPromptedOptimizeDoze(context, true)
        onDismiss()
      },
      actions = listOf(
        Action(android.R.string.ok) {
          TextSecurePreferences.setPromptedOptimizeDoze(context, true)
          PowerManagerCompat.requestIgnoreBatteryOptimizations(context)
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

  private class Producer(private val context: Context) : DismissibleBannerProducer<DozeBanner>(bannerProducer = {
    DozeBanner(context = context, dismissed = false, onDismiss = it)
  }) {
    override fun createDismissedBanner(): DozeBanner {
      return DozeBanner(context, true) {}
    }
  }

  companion object {
    @JvmStatic
    fun createFlow(context: Context): Flow<DozeBanner> {
      return Producer(context).flow
    }
  }
}
