/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.AppUtil
import org.signal.core.util.ThreadUtil
import org.signal.core.util.bytes
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.RestoreV2Event
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsTypeFeature
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsTypeFeatureRow
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportCallbacks
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportDialog
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportViewModel
import org.thoughtcrime.securesms.components.contactsupport.SendSupportEmailEffect
import org.thoughtcrime.securesms.conversation.v2.registerForLifecycle
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreenTitleSubtitle
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.PlayStoreUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.viewModel
import java.util.Locale
import kotlin.time.Duration

/**
 * Restore backup from remote source.
 */
@SuppressLint("BaseActivitySubclass")
class RemoteRestoreActivity : BaseActivity() {
  companion object {

    private const val KEY_ONLY_OPTION = "ONLY_OPTION"

    fun getIntent(context: Context, isOnlyOption: Boolean = false): Intent {
      return Intent(context, RemoteRestoreActivity::class.java).apply {
        putExtra(KEY_ONLY_OPTION, isOnlyOption)
      }
    }
  }

  private val viewModel: RemoteRestoreViewModel by viewModel {
    RemoteRestoreViewModel(intent.getBooleanExtra(KEY_ONLY_OPTION, false))
  }

  private val contactSupportViewModel: ContactSupportViewModel<ContactSupportReason> by viewModels()

  private lateinit var wakeLock: RemoteRestoreWakeLock

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    wakeLock = RemoteRestoreWakeLock(this)

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        val restored = viewModel
          .state
          .map { it.importState }
          .filterIsInstance<RemoteRestoreViewModel.ImportState.Restored>()
          .firstOrNull()

        if (restored != null) {
          RegistrationUtil.maybeMarkRegistrationComplete()
          startActivity(MainActivity.clearTop(this@RemoteRestoreActivity))
          finishAffinity()
        }
      }
    }

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        viewModel
          .state
          .map { it.importState }
          .collect {
            when (it) {
              RemoteRestoreViewModel.ImportState.InProgress -> {
                wakeLock.acquire()
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
              }

              else -> {
                wakeLock.release()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
              }
            }
          }
      }
    }

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while (isActive) {
          if (TextSecurePreferences.isUnauthorizedReceived(this@RemoteRestoreActivity)) {
            ThreadUtil.runOnMain { showUnregisteredDialog() }
            break
          }
          delay(1000)
        }
      }
    }

    setContent {
      val state: RemoteRestoreViewModel.ScreenState by viewModel.state.collectAsStateWithLifecycle()
      val contactSupportState: ContactSupportViewModel.ContactSupportState<ContactSupportReason> by contactSupportViewModel.state.collectAsStateWithLifecycle()
      var showSkipRestoreWarning by remember { mutableStateOf(false) }

      SendSupportEmailEffect(
        contactSupportState = contactSupportState,
        subjectRes = { reason ->
          when (reason) {
            ContactSupportReason.SvrBFailure -> R.string.EnterBackupKey_permanent_failure_support_email
            else -> R.string.EnterBackupKey_network_failure_support_email
          }
        },
        filterRes = { reason ->
          when (reason) {
            ContactSupportReason.SvrBFailure -> R.string.EnterBackupKey_permanent_failure_support_email_filter
            else -> R.string.EnterBackupKey_network_failure_support_email_filter
          }
        }
      ) {
        contactSupportViewModel.hideContactSupport()
      }

      SignalTheme {
        Surface {
          RestoreFromBackupContent(
            state = state,
            contactSupportState = contactSupportState,
            onRestoreBackupClick = { viewModel.restore() },
            onRetryRestoreTier = { viewModel.reload() },
            onContactSupport = { contactSupportViewModel.showContactSupport() },
            onCancelClick = {
              if (state.isRemoteRestoreOnlyOption) {
                showSkipRestoreWarning = true
              } else {
                finish()
              }
            },
            onImportErrorDialogDismiss = { viewModel.clearError() },
            onUpdateSignal = {
              PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(this)
            },
            contactSupportCallbacks = contactSupportViewModel
          )

          if (showSkipRestoreWarning) {
            Dialogs.SimpleAlertDialog(
              title = stringResource(R.string.SelectRestoreMethodFragment__skip_restore_title),
              body = stringResource(R.string.SelectRestoreMethodFragment__skip_restore_warning),
              confirm = stringResource(R.string.SelectRestoreMethodFragment__skip_restore),
              dismiss = stringResource(android.R.string.cancel),
              onConfirm = {
                lifecycleScope.launch {
                  viewModel.skipRestore()
                  viewModel.performStorageServiceAccountRestoreIfNeeded()
                  startActivity(MainActivity.clearTop(this@RemoteRestoreActivity))
                  finish()
                }
              },
              onDismiss = { showSkipRestoreWarning = false },
              confirmColor = MaterialTheme.colorScheme.error
            )
          }
        }
      }
    }

    EventBus.getDefault().registerForLifecycle(subscriber = this, lifecycleOwner = this)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onEvent(restoreEvent: RestoreV2Event) {
    viewModel.updateRestoreProgress(restoreEvent)
  }

  private fun showUnregisteredDialog() {
    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.RestoreActivity__no_longer_registered_title)
      .setMessage(R.string.RestoreActivity__no_longer_registered_message)
      .setCancelable(false)
      .setPositiveButton(android.R.string.ok) { _, _ -> AppUtil.clearData(this) }
      .show()
  }

  enum class ContactSupportReason {
    NetworkError, SvrBFailure
  }
}

@Composable
private fun RestoreFromBackupContent(
  state: RemoteRestoreViewModel.ScreenState,
  contactSupportState: ContactSupportViewModel.ContactSupportState<RemoteRestoreActivity.ContactSupportReason> = ContactSupportViewModel.ContactSupportState(),
  onRestoreBackupClick: () -> Unit = {},
  onRetryRestoreTier: () -> Unit = {},
  onContactSupport: () -> Unit = {},
  onCancelClick: () -> Unit = {},
  onImportErrorDialogDismiss: () -> Unit = {},
  onUpdateSignal: () -> Unit = {},
  contactSupportCallbacks: ContactSupportCallbacks = ContactSupportCallbacks.Empty
) {
  when (state.loadState) {
    RemoteRestoreViewModel.ScreenState.LoadState.LOADING -> {
      Dialogs.IndeterminateProgressDialog(
        message = stringResource(R.string.RemoteRestoreActivity__fetching_backup_details)
      )
    }

    RemoteRestoreViewModel.ScreenState.LoadState.LOADED -> {
      BackupAvailableContent(
        state = state,
        onRestoreBackupClick = onRestoreBackupClick,
        onCancelClick = onCancelClick,
        onImportErrorDialogDismiss = onImportErrorDialogDismiss,
        onUpdateSignal = onUpdateSignal,
        onContactSupport = onContactSupport
      )
    }

    RemoteRestoreViewModel.ScreenState.LoadState.NOT_FOUND -> {
      BackupNotFoundDialog(onDismiss = onCancelClick)
    }

    RemoteRestoreViewModel.ScreenState.LoadState.FAILURE -> {
      if (contactSupportState.show) {
        ContactSupportDialog(
          showInProgress = contactSupportState.showAsProgress,
          callbacks = contactSupportCallbacks
        )
      } else {
        TierRestoreFailedDialog(
          loadAttempts = state.loadAttempts,
          onRetryRestore = onRetryRestoreTier,
          onContactSupport = onContactSupport,
          onCancel = onCancelClick
        )
      }
    }

    RemoteRestoreViewModel.ScreenState.LoadState.STORAGE_SERVICE_RESTORE -> {
      Dialogs.IndeterminateProgressDialog()
    }
  }
}

@Composable
private fun BackupAvailableContent(
  state: RemoteRestoreViewModel.ScreenState,
  onRestoreBackupClick: () -> Unit,
  onCancelClick: () -> Unit,
  onImportErrorDialogDismiss: () -> Unit,
  onUpdateSignal: () -> Unit,
  onContactSupport: () -> Unit
) {
  val subtitle = if (state.backupSize.bytes > 0) {
    stringResource(
      id = R.string.RemoteRestoreActivity__backup_created_at_with_size,
      DateUtils.formatDateWithoutDayOfWeek(Locale.getDefault(), state.backupTime),
      DateUtils.getOnlyTimeString(LocalContext.current, state.backupTime),
      state.backupSize.toUnitString()
    )
  } else {
    stringResource(
      id = R.string.RemoteRestoreActivity__backup_created_at,
      DateUtils.formatDateWithoutDayOfWeek(Locale.getDefault(), state.backupTime),
      DateUtils.getOnlyTimeString(LocalContext.current, state.backupTime)
    )
  }

  RegistrationScreen(
    menu = null,
    topContent = {
      if (state.backupTier != null) {
        RegistrationScreenTitleSubtitle(
          title = stringResource(id = R.string.RemoteRestoreActivity__restore_from_backup),
          subtitle = AnnotatedString(subtitle)
        )
      } else {
        Icon(
          imageVector = ImageVector.vectorResource(id = R.drawable.symbol_backup_24),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier
            .size(64.dp)
            .background(color = SignalTheme.colors.colorSurface2, shape = CircleShape)
            .padding(12.dp)
            .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.size(16.dp))

        Text(
          text = stringResource(id = R.string.RemoteRestoreActivity__restore_from_backup),
          style = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
          modifier = Modifier.fillMaxWidth()
        )
      }
    },

    bottomContent = {
      Column {
        if (state.isLoaded()) {
          Buttons.LargeTonal(
            onClick = onRestoreBackupClick,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(text = stringResource(id = R.string.RemoteRestoreActivity__restore_backup))
          }
        }

        TextButton(
          onClick = onCancelClick,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(text = stringResource(id = if (state.isRemoteRestoreOnlyOption) R.string.RemoteRestoreActivity__skip_restore else android.R.string.cancel))
        }
      }
    }
  ) {
    if (state.backupTier != null) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(color = SignalTheme.colors.colorSurface2, shape = RoundedCornerShape(18.dp))
          .padding(horizontal = 20.dp)
          .padding(top = 20.dp, bottom = 18.dp)
      ) {
        Text(
          text = stringResource(id = R.string.RemoteRestoreActivity__your_backup_includes),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(bottom = 6.dp)
        )

        getFeatures(state.backupTier, state.backupMediaTTL).forEach {
          MessageBackupsTypeFeatureRow(
            messageBackupsTypeFeature = it,
            iconTint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 6.dp)
          )
        }
      }

      Text(
        text = stringResource(R.string.RemoteRestoreActivity__your_media_will_restore_in_the_background),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 16.dp)
      )
    } else {
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 20.dp)
      )

      Text(
        text = stringResource(R.string.RemoteRestoreActivity__your_media_will_restore_in_the_background),
        style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    when (state.importState) {
      RemoteRestoreViewModel.ImportState.None -> Unit
      RemoteRestoreViewModel.ImportState.InProgress -> RestoreProgressDialog(state.restoreProgress)
      RemoteRestoreViewModel.ImportState.Restored -> Unit
      RemoteRestoreViewModel.ImportState.NetworkFailure -> RestoreNetworkFailedDialog(onDismiss = onImportErrorDialogDismiss)
      RemoteRestoreViewModel.ImportState.Failed -> {
        if (SignalStore.backup.hasInvalidBackupVersion) {
          InvalidBackupVersionDialog(onUpdateSignal = onUpdateSignal, onDismiss = onImportErrorDialogDismiss)
        } else {
          RestoreFailedDialog(onDismiss = onImportErrorDialogDismiss)
        }
      }

      RemoteRestoreViewModel.ImportState.FailureWithLogPrompt -> {
        RestoreFailedWithLogPromptDialog(onDismiss = onImportErrorDialogDismiss, onContactSupport = onContactSupport)
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun RestoreFromBackupContentPreview() {
  Previews.Preview {
    RestoreFromBackupContent(
      state = RemoteRestoreViewModel.ScreenState(
        backupTier = MessageBackupTier.PAID,
        backupTime = System.currentTimeMillis(),
        backupSize = 1234567.bytes,
        importState = RemoteRestoreViewModel.ImportState.None,
        restoreProgress = null
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun RestoreFromBackupUnknownTierPreview() {
  Previews.Preview {
    RestoreFromBackupContent(
      state = RemoteRestoreViewModel.ScreenState(
        loadState = RemoteRestoreViewModel.ScreenState.LoadState.LOADED,
        backupTier = null,
        backupTime = System.currentTimeMillis(),
        backupSize = 0.bytes,
        importState = RemoteRestoreViewModel.ImportState.Restored,
        restoreProgress = null
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun RestoreFromBackupContentLoadingPreview() {
  Previews.Preview {
    RestoreFromBackupContent(
      state = RemoteRestoreViewModel.ScreenState(
        importState = RemoteRestoreViewModel.ImportState.None,
        restoreProgress = null
      )
    )
  }
}

@Composable
private fun getFeatures(tier: MessageBackupTier?, mediaTTL: Duration): ImmutableList<MessageBackupsTypeFeature> {
  return when (tier) {
    null -> persistentListOf()
    MessageBackupTier.PAID -> {
      persistentListOf(
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_thread_compact_bold_16,
          label = stringResource(id = R.string.RemoteRestoreActivity__all_of_your_media)
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_recent_compact_bold_16,
          label = stringResource(id = R.string.RemoteRestoreActivity__all_of_your_messages)
        )
      )
    }

    MessageBackupTier.FREE -> {
      persistentListOf(
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_thread_compact_bold_16,
          label = stringResource(id = R.string.RemoteRestoreActivity__your_last_d_days_of_media, mediaTTL.inWholeDays)
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_recent_compact_bold_16,
          label = stringResource(id = R.string.RemoteRestoreActivity__all_of_your_messages)
        )
      )
    }
  }
}

/**
 * A dialog that *just* shows a spinner. Useful for short actions where you need to
 * let the user know that some action is completing.
 */
@Composable
private fun RestoreProgressDialog(restoreProgress: RestoreV2Event?) {
  AlertDialog(
    onDismissRequest = {},
    confirmButton = {},
    dismissButton = {},
    text = {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .fillMaxWidth()
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.wrapContentSize()
        ) {
          if (restoreProgress == null || restoreProgress.type == RestoreV2Event.Type.PROGRESS_FINALIZING) {
            CircularProgressIndicator(
              modifier = Modifier
                .padding(top = 55.dp, bottom = 16.dp)
                .width(48.dp)
                .height(48.dp)
            )
          } else {
            CircularProgressIndicator(
              progress = { restoreProgress.getProgress() },
              modifier = Modifier
                .padding(top = 55.dp, bottom = 16.dp)
                .width(48.dp)
                .height(48.dp)
            )
          }

          val progressText = when (restoreProgress?.type) {
            RestoreV2Event.Type.PROGRESS_DOWNLOAD -> stringResource(id = R.string.RemoteRestoreActivity__downloading_backup)
            RestoreV2Event.Type.PROGRESS_RESTORE -> stringResource(id = R.string.RemoteRestoreActivity__restoring_messages)
            RestoreV2Event.Type.PROGRESS_FINALIZING -> stringResource(id = R.string.RemoteRestoreActivity__finishing_restore)
            else -> stringResource(id = R.string.RemoteRestoreActivity__restoring)
          }

          Text(
            text = progressText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp)
          )

          if (restoreProgress != null && restoreProgress.type != RestoreV2Event.Type.PROGRESS_FINALIZING) {
            val progressBytes = restoreProgress.count.toUnitString()
            val totalBytes = restoreProgress.estimatedTotalCount.toUnitString()
            Text(
              text = stringResource(id = R.string.RemoteRestoreActivity__s_of_s_s, progressBytes, totalBytes, "%.2f%%".format(restoreProgress.getProgress() * 100)),
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(bottom = 12.dp)
            )
          }
        }
      }
    },
    modifier = Modifier.width(212.dp)
  )
}

@DayNightPreviews
@Composable
private fun ProgressDialogPreview() {
  Previews.Preview {
    RestoreProgressDialog(
      RestoreV2Event(
        type = RestoreV2Event.Type.PROGRESS_RESTORE,
        count = 1234.bytes,
        estimatedTotalCount = 10240.bytes
      )
    )
  }
}

@Composable
fun BackupNotFoundDialog(
  onDismiss: () -> Unit = {}
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.EnterBackupKey_backup_not_found),
    body = stringResource(R.string.EnterBackupKey_backup_key_you_entered_is_correct_but_no_backup),
    confirm = stringResource(android.R.string.ok),
    onConfirm = onDismiss,
    onDismiss = onDismiss
  )
}

@Composable
fun RestoreFailedDialog(
  onDismiss: () -> Unit = {}
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.RemoteRestoreActivity__couldnt_transfer),
    body = stringResource(R.string.RemoteRestoreActivity__error_occurred),
    confirm = stringResource(android.R.string.ok),
    onConfirm = onDismiss,
    onDismiss = onDismiss
  )
}

@Composable
fun RestoreFailedWithLogPromptDialog(
  onDismiss: () -> Unit = {},
  onContactSupport: () -> Unit = {}
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.RemoteRestoreActivity__failure_with_log_prompt_title),
    body = stringResource(R.string.RemoteRestoreActivity__failure_with_log_prompt_body),
    confirm = stringResource(R.string.RemoteRestoreActivity__failure_with_log_prompt_contact_button),
    dismiss = stringResource(android.R.string.ok),
    onConfirm = onContactSupport,
    onDismiss = onDismiss
  )
}

@Composable
fun RestoreNetworkFailedDialog(
  onDismiss: () -> Unit = {}
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.RemoteRestoreActivity__couldnt_transfer),
    body = stringResource(R.string.RegistrationActivity_error_connecting_to_service),
    confirm = stringResource(android.R.string.ok),
    onConfirm = onDismiss,
    onDismiss = onDismiss
  )
}

@Composable
fun TierRestoreFailedDialog(
  loadAttempts: Int = 0,
  onRetryRestore: () -> Unit = {},
  onContactSupport: () -> Unit = {},
  onCancel: () -> Unit = {}
) {
  if (loadAttempts > 2) {
    Dialogs.AdvancedAlertDialog(
      title = stringResource(R.string.EnterBackupKey_cant_restore_backup),
      body = stringResource(R.string.EnterBackupKey_your_backup_cant_be_restored_right_now),
      positive = stringResource(R.string.EnterBackupKey_try_again),
      neutral = stringResource(R.string.EnterBackupKey_contact_support),
      negative = stringResource(android.R.string.cancel),
      onPositive = onRetryRestore,
      onNeutral = onContactSupport,
      onNegative = onCancel
    )
  } else {
    Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.EnterBackupKey_cant_restore_backup),
      body = stringResource(R.string.EnterBackupKey_your_backup_cant_be_restored_right_now),
      confirm = stringResource(R.string.EnterBackupKey_try_again),
      dismiss = stringResource(android.R.string.cancel),
      onConfirm = onRetryRestore,
      onDeny = onCancel,
      onDismissRequest = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun RestoreFailedDialogPreview() {
  Previews.Preview {
    RestoreFailedDialog()
  }
}

@DayNightPreviews
@Composable
private fun RestoreFailedWithLogPromptDialogPreview() {
  Previews.Preview {
    RestoreFailedWithLogPromptDialog()
  }
}

@Composable
fun InvalidBackupVersionDialog(
  onUpdateSignal: () -> Unit = {},
  onDismiss: () -> Unit = {}
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.RemoteRestoreActivity__couldnt_restore),
    body = stringResource(R.string.RemoteRestoreActivity__update_latest),
    confirm = stringResource(R.string.RemoteRestoreActivity__update_signal),
    onConfirm = onUpdateSignal,
    dismiss = stringResource(R.string.RemoteRestoreActivity__not_now),
    onDismiss = onDismiss
  )
}

@DayNightPreviews
@Composable
private fun InvalidBackupVersionDialogPreview() {
  Previews.Preview {
    InvalidBackupVersionDialog()
  }
}
