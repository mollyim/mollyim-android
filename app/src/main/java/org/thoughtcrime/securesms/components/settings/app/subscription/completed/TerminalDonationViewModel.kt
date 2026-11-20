/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.completed

import androidx.annotation.StringRes
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.signal.core.models.badges.Badge
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.databaseprotos.TerminalDonationQueue
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient

class TerminalDonationViewModel(
  donationCompleted: TerminalDonationQueue.TerminalDonation,
  repository: TerminalDonationRepository = TerminalDonationRepository()
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(TerminalDonationViewModel::class.java)
  }

  private val internalBadge = mutableStateOf<Badge?>(null)
  private val internalToggleChecked = mutableStateOf(false)
  private val internalToggleType = mutableStateOf(ToggleType.NONE)

  val badge: State<Badge?> = internalBadge
  val isToggleChecked: State<Boolean> = internalToggleChecked
  val toggleType: State<ToggleType> = internalToggleType

  init {
    viewModelScope.launch {
      val result = repository.getBadge(donationCompleted)
      val badge = result.successOrNull()
      if (badge == null) {
        Log.w(TAG, "Failed to load badge.", result.getCause())
        return@launch
      }

      val hasOtherBadges = Recipient.self().badges.filterNot { it.id == badge.id }.isNotEmpty()
      val isDisplayingBadges = SignalStore.inAppPayments.getDisplayBadgesOnProfile()

      internalBadge.value = badge
      internalToggleChecked.value = !hasOtherBadges
      internalToggleType.value = when {
        hasOtherBadges && isDisplayingBadges -> ToggleType.MAKE_FEATURED_BADGE
        else -> ToggleType.DISPLAY_ON_PROFILE
      }
    }
  }

  fun onToggleCheckChanged(isChecked: Boolean) {
    internalToggleChecked.value = isChecked
  }

  fun commitToggleState() {
    // MOLLY: No-op
  }

  enum class ToggleType(@StringRes val copyId: Int) {
    NONE(-1),
    MAKE_FEATURED_BADGE(R.string.SubscribeThanksForYourSupportBottomSheetDialogFragment__make_featured_badge),
    DISPLAY_ON_PROFILE(R.string.SubscribeThanksForYourSupportBottomSheetDialogFragment__display_on_profile)
  }
}
