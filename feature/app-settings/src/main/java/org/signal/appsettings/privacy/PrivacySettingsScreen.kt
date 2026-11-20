/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.privacy

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.util.ExpirationUtil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object PrivacySettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_PHONE_NUMBER = "row-phone-number"
  const val ROW_BLOCKED = "row-blocked"
  const val ROW_BLOCK_UNKNOWN = "row-block-unknown"
  const val ROW_READ_RECEIPTS = "row-read-receipts"
  const val ROW_TYPING_INDICATORS = "row-typing-indicators"
  const val ROW_DEFAULT_TIMER = "row-default-timer"
  const val ROW_PASSPHRASE_LOCK = "row-passphrase-lock"
  const val ROW_CHANGE_PASSPHRASE = "row-change-passphrase"
  const val ROW_AUTOMATIC_LOCKDOWN = "row-automatic-lockdown"
  const val ROW_PASSPHRASE_LOCK_TIMEOUT = "row-passphrase-lock-timeout"
  const val ROW_BIOMETRIC_SCREEN_LOCK = "row-biometric-screen-lock"
  const val ROW_SCREEN_SECURITY = "row-screen-security"
  const val ROW_INCOGNITO_KEYBOARD = "row-incognito-keyboard"
  const val TEXT_INCOGNITO_KEYBOARD_DISCLAIMER = "text-incognito-keyboard-disclaimer"
  const val ROW_ADVANCED = "row-advanced"
}

/**
 * The top level privacy settings: who can see the user's number, blocked contacts, messaging privacy, disappearing
 * message defaults and app security.
 */
@Composable
fun PrivacySettingsScreen(
  state: PrivacySettingsState,
  onEvent: (PrivacySettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  LifecycleResumeEffect(Unit) {
    onEvent(PrivacySettingsEvent.ScreenResumed)
    onPauseOrDispose {}
  }

  val listState = rememberLazyListState()

  Scaffolds.Settings(
    title = stringResource(R.string.PrivacySettingsScreen__privacy),
    onNavigationClick = { onEvent(PrivacySettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    snackbarHost = {
      Snackbars.Host(
        message = state.snackbar.message(),
        onDismissed = { onEvent(PrivacySettingsEvent.SnackbarDismissed) }
      )
    },
    modifier = modifier,
    collapseTopBarInSplitPane = true
  ) { paddingValues ->
    AppSettingsLazyColumn(
      scaffoldPadding = paddingValues,
      state = listState,
      modifier = Modifier.testTag(PrivacySettingsTestTags.SCROLLER)
    ) {
      if (state.hasPhoneNumber) {
        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences_app_protection__phone_number),
            label = stringResource(R.string.preferences_app_protection__choose_who_can_see),
            onClick = { onEvent(PrivacySettingsEvent.PhoneNumberClicked) },
            modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_PHONE_NUMBER)
          )
        }

        item {
          Dividers.Default()
        }
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.PrivacySettingsFragment__blocked),
          label = pluralStringResource(R.plurals.PrivacySettingsFragment__d_contacts, state.blockedCount, state.blockedCount),
          onClick = { onEvent(PrivacySettingsEvent.BlockedClicked) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_BLOCKED)
        )
      }

      item {
        Rows.ToggleRow(
          checked = state.blockUnknown,
          text = stringResource(R.string.preferences__block_unknown),
          label = stringResource(R.string.preferences__block_users_youve_never_been_in_contact_with_and_who_are_not_saved_in_your_contacts),
          onCheckChanged = { onEvent(PrivacySettingsEvent.BlockUnknownToggled(it)) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_BLOCK_UNKNOWN)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(text = stringResource(R.string.PrivacySettingsFragment__messaging))
      }

      item {
        Rows.ToggleRow(
          checked = state.readReceipts,
          text = stringResource(R.string.preferences__read_receipts),
          label = stringResource(R.string.preferences__if_read_receipts_are_disabled_you_wont_be_able_to_see_read_receipts),
          onCheckChanged = { onEvent(PrivacySettingsEvent.ReadReceiptsToggled(it)) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_READ_RECEIPTS)
        )
      }

      item {
        Rows.ToggleRow(
          checked = state.typingIndicators,
          text = stringResource(R.string.preferences__typing_indicators),
          label = stringResource(R.string.preferences__if_typing_indicators_are_disabled_you_wont_be_able_to_see_typing_indicators),
          onCheckChanged = { onEvent(PrivacySettingsEvent.TypingIndicatorsToggled(it)) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_TYPING_INDICATORS)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(text = stringResource(R.string.PrivacySettingsFragment_data_at_rest))
      }

      item {
        Rows.ToggleRow(
          checked = state.passphraseLock,
          text = stringResource(R.string.preferences__passphrase_lock),
          label = stringResource(R.string.preferences__protect_molly_database_with_a_passphrase),
          onCheckChanged = { onEvent(PrivacySettingsEvent.PassphraseLockToggled(it)) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_PASSPHRASE_LOCK)
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.preferences__change_passphrase),
          enabled = state.passphraseLock,
          onClick = { onEvent(PrivacySettingsEvent.ChangePassphraseClicked) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_CHANGE_PASSPHRASE)
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.preferences__automatic_lockdown),
          label = automaticLockdownSummary(state),
          enabled = state.passphraseLock,
          onClick = { onEvent(PrivacySettingsEvent.AutomaticLockdownClicked) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_AUTOMATIC_LOCKDOWN)
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.preferences__device_lock_timeout),
          label = passphraseLockTimeoutSummary(state.passphraseLockTimeout),
          enabled = state.passphraseLock && state.isPassphraseLockTimeoutEnabled,
          onClick = { onEvent(PrivacySettingsEvent.PassphraseLockTimeoutClicked) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_PASSPHRASE_LOCK_TIMEOUT)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(text = stringResource(R.string.PrivacySettingsScreen__disappearing_messages))
      }

      item {
        DefaultTimerRow(
          timer = state.universalExpireTimer,
          onClick = { onEvent(PrivacySettingsEvent.DefaultTimerClicked) }
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(text = stringResource(R.string.PrivacySettingsFragment__app_security))
      }

      item {
        Rows.ToggleRow(
          checked = state.biometricScreenLock,
          text = stringResource(R.string.preferences_app_protection__screen_lock),
          label = stringResource(R.string.PrivacySettingsFragment__lock_molly_access_with_fingerprint_or_face_recognition),
          onCheckChanged = { onEvent(PrivacySettingsEvent.BiometricScreenLockToggled(it)) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_BIOMETRIC_SCREEN_LOCK)
        )
      }

      item {
        Rows.ToggleRow(
          checked = state.isScreenSecurityEnabled,
          enabled = !state.isScreenSecurityForced,
          text = stringResource(R.string.preferences__screen_security),
          label = stringResource(R.string.PrivacySettingsFragment__block_screenshots_in_the_recents_list_and_inside_the_app),
          onCheckChanged = { onEvent(PrivacySettingsEvent.ScreenSecurityToggled(it)) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_SCREEN_SECURITY)
        )
      }

      item {
        Rows.ToggleRow(
          checked = state.incognitoKeyboard,
          text = stringResource(R.string.preferences__incognito_keyboard),
          label = stringResource(R.string.preferences__request_keyboard_to_disable),
          onCheckChanged = { onEvent(PrivacySettingsEvent.IncognitoKeyboardToggled(it)) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_INCOGNITO_KEYBOARD)
        )
      }

      item {
        IncognitoKeyboardDisclaimer(onEvent = onEvent)
      }

      item {
        Dividers.Default()
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.PrivacySettingsScreen__advanced),
          label = stringResource(R.string.PrivacySettingsFragment__signal_message_and_calls),
          onClick = { onEvent(PrivacySettingsEvent.AdvancedClicked) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_ADVANCED)
        )
      }
    }

    when (state.dialog) {
      PrivacySettingsState.Dialog.None -> Unit
      PrivacySettingsState.Dialog.AutomaticLockdown -> {
        Dialogs.MultiSelectListDialog(
          title = stringResource(R.string.preferences__automatic_lockdown),
          labels = state.passphraseLockTriggerOptions.map { it.label }.toTypedArray(),
          values = state.passphraseLockTriggerOptions.map { it.key }.toTypedArray(),
          selection = state.passphraseLockTriggerValues.toTypedArray(),
          onSelectionChanged = { onEvent(PrivacySettingsEvent.AutomaticLockdownSelected(it.toSet())) },
          onDismissRequest = { onEvent(PrivacySettingsEvent.DialogDismissed) }
        )
      }
    }
  }
}

@Composable
private fun automaticLockdownSummary(state: PrivacySettingsState): String {
  if (state.passphraseLockTriggerValues.isEmpty()) {
    return stringResource(CoreUiR.string.preferences__none)
  }

  val labels = state.passphraseLockTriggerOptions.associate { it.key to it.label }
  return state.passphraseLockTriggerValues.joinToString(", ") { labels[it] ?: it }
}

@Composable
private fun passphraseLockTimeoutSummary(timeout: Duration): String {
  return if (timeout <= Duration.ZERO) {
    stringResource(R.string.AppProtectionPreferenceFragment_instant)
  } else {
    ExpirationUtil.getExpirationDisplayValue(LocalContext.current, timeout.inWholeSeconds.toInt())
  }
}

@Composable
private fun DefaultTimerRow(
  timer: Duration,
  onClick: () -> Unit
) {
  val context = LocalContext.current
  val seconds = timer.inWholeSeconds.toInt()
  val value = ExpirationUtil.getExpirationAbbreviatedDisplayValue(context, seconds)
  val valueDescription = ExpirationUtil.getExpirationDisplayValue(context, seconds)

  Rows.TextRow(
    text = {
      with(Rows) {
        TextAndLabel(
          text = stringResource(R.string.PrivacySettingsFragment__default_timer_for_new_changes),
          label = stringResource(R.string.PrivacySettingsFragment__set_a_default_disappearing_message_timer_for_all_new_chats_started_by_you)
        )
      }

      Spacer(Modifier.width(16.dp))

      Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = valueDescription }
      )
    },
    onClick = onClick,
    modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_DEFAULT_TIMER)
  )
}

@Composable
private fun IncognitoKeyboardDisclaimer(onEvent: (PrivacySettingsEvent) -> Unit) {
  Text(
    text = buildAnnotatedString {
      append(stringResource(R.string.preferences__this_setting_is_not_a_guarantee))
      append(" ")

      withLink(
        LinkAnnotation.Clickable(
          tag = "learn-more",
          styles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.onSurface)),
          linkInteractionListener = { onEvent(PrivacySettingsEvent.IncognitoKeyboardLearnMoreClicked) }
        )
      ) {
        append(stringResource(R.string.PrivacySettingsScreen__learn_more))
      }
    },
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
      .padding(horizontal = dimensionResource(CoreUiR.dimen.gutter), vertical = 16.dp)
      .testTag(PrivacySettingsTestTags.TEXT_INCOGNITO_KEYBOARD_DISCLAIMER)
  )
}

@Composable
private fun PrivacySettingsState.Snackbar.message(): String? {
  return when (this) {
    PrivacySettingsState.Snackbar.None -> null
    PrivacySettingsState.Snackbar.BiometricsUnavailable -> stringResource(R.string.PrivacySettingsFragment__no_biometric_features_available_on_this_device)
    PrivacySettingsState.Snackbar.BiometricEnrollmentRequired -> stringResource(R.string.PrivacySettingsFragment__please_first_setup_your_biometrics_in_android_settings)
    PrivacySettingsState.Snackbar.DeviceSecuritySettingsUnavailable -> stringResource(R.string.PrivacySettingsFragment__failed_to_navigate_to_system_settings)
  }
}

private val previewState = PrivacySettingsState(
  blockedCount = 3,
  readReceipts = true,
  typingIndicators = true,
  universalExpireTimer = 1.days,
  passphraseLock = true,
  passphraseLockTriggerValues = setOf("timeout"),
  passphraseLockTriggerOptions = listOf(PassphraseLockTriggerOption("timeout", "On inactivity timeout")),
  isPassphraseLockTimeoutEnabled = true,
  passphraseLockTimeout = 5.minutes,
  biometricScreenLock = true,
  screenSecurity = true
)

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PrivacySettingsScreenPreview() {
  PrivacySettingsScreen(state = previewState, onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PrivacySettingsScreenDefaultsPreview() {
  PrivacySettingsScreen(state = PrivacySettingsState(), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PrivacySettingsScreenInsecureDevicePreview() {
  PrivacySettingsScreen(state = previewState.copy(hasPhoneNumber = false), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PrivacySettingsScreenPassphrasePreview() {
  PrivacySettingsScreen(
    state = previewState.copy(passphraseLockTimeout = 2.hours),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PrivacySettingsScreenAutomaticLockdownDialogPreview() {
  PrivacySettingsScreen(
    state = previewState.copy(dialog = PrivacySettingsState.Dialog.AutomaticLockdown),
    onEvent = {}
  )
}
