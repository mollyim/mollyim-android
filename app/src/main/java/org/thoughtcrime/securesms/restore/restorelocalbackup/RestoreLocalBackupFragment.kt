/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.restorelocalbackup

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.BackupEvent
import org.thoughtcrime.securesms.backup.BackupPassphrase
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRestoreLocalBackupBinding
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import org.thoughtcrime.securesms.restore.RestoreActivity
import org.thoughtcrime.securesms.restore.RestoreRepository
import org.thoughtcrime.securesms.restore.RestoreViewModel
import org.thoughtcrime.securesms.service.LocalBackupListener
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.ViewModelFactory
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible
import java.util.Locale

/**
 * This fragment is used to monitor and manage an in-progress backup restore.
 */
class RestoreLocalBackupFragment : LoggingFragment(R.layout.fragment_restore_local_backup) {
  private val sharedViewModel: RestoreViewModel by activityViewModels()
  private val restoreLocalBackupViewModel: RestoreLocalBackupViewModel by viewModels(
    factoryProducer = ViewModelFactory.factoryProducer {
      val fileBackupUri = sharedViewModel.getBackupFileUri()!!
      RestoreLocalBackupViewModel(fileBackupUri)
    }
  )

  private val selectBackupDirectory = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    when (val resultCode = result.resultCode) {
      Activity.RESULT_OK -> {
        val backupDirectoryUri: Uri? = result.data?.data

        if (backupDirectoryUri != null) {
          Log.i(TAG, "Re-enabling backups with new directory")
          val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

          SignalStore.settings.setSignalBackupDirectory(backupDirectoryUri)
          requireContext().contentResolver.takePersistableUriPermission(backupDirectoryUri, takeFlags)

          enableLocalBackups(requireContext())
          resumeRegistrationAfterLocalBackupRestore()
        } else {
          Log.w(TAG, "Backup directory URI is null, reshowing dialog to re-enable")
          onLocalBackupRestoreCompletedSuccessfully()
        }
      }
      Activity.RESULT_CANCELED -> {
        Log.w(TAG, "Backup directory selection canceled, reshowing dialog to re-enable")
        onLocalBackupRestoreCompletedSuccessfully()
      }
      else -> Log.w(TAG, "Backup directory selection activity ended with unknown result code: $resultCode")
    }
  }

  private val binding: FragmentRestoreLocalBackupBinding by ViewBinderDelegate(FragmentRestoreLocalBackupBinding::bind)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setDebugLogSubmitMultiTapView(binding.verifyHeader)
    Log.i(TAG, "Backup restore.")

    if (sharedViewModel.getBackupFileUri() == null) {
      Log.i(TAG, "No backup URI found, must navigate back to choose one.")
      findNavController().navigateUp()
      return
    }

    binding.restoreButton.setOnClickListener { presentBackupPassPhrasePromptDialog() }

    binding.cancelLocalRestoreButton.setOnClickListener {
      Log.i(TAG, "Cancel clicked.")
      findNavController().navigateUp()
    }

    if (SignalStore.settings.isBackupEnabled) {
      Log.i(TAG, "Backups enabled, so a backup must have been previously restored.")
      onLocalBackupRestoreCompletedSuccessfully()
      return
    }

    restoreLocalBackupViewModel.backupReadError.observe(viewLifecycleOwner) { fileState ->
      fileState?.let {
        restoreLocalBackupViewModel.clearBackupFileStateError()
        handleBackupFileStateError(it)
      }
    }

    restoreLocalBackupViewModel.uiState.observe(viewLifecycleOwner) { fragmentState ->
      fragmentState.backupInfo?.let {
        presentBackupFileInfo(backupSize = it.size, backupTimestamp = it.timestamp)
      }

      if (fragmentState.restoreInProgress) {
        presentRestoreProgress(fragmentState.backupProgressCount)
      } else {
        presentProgressEnded()
      }
    }

    restoreLocalBackupViewModel.importResult.observe(viewLifecycleOwner) { importResult ->
      when (importResult) {
        null -> Unit
        RestoreRepository.BackupImportResult.SUCCESS -> onLocalBackupRestoreCompletedSuccessfully()
        else -> {
          handleBackupImportError(importResult)
          restoreLocalBackupViewModel.backupImportErrorShown()
        }
      }
    }

    restoreLocalBackupViewModel.prepareRestore(requireContext())
  }

  private fun onLocalBackupRestoreCompletedSuccessfully() {
    Log.d(TAG, "onBackupCompletedSuccessfully()")
    if (BackupUtil.isUserSelectionRequired(requireContext()) && !BackupUtil.canUserAccessBackupDirectory(requireContext())) {
      displayConfirmationDialog(requireContext())
    } else {
      enableLocalBackups(requireContext())
      resumeRegistrationAfterLocalBackupRestore()
    }
  }

  private fun resumeRegistrationAfterLocalBackupRestore() {
    val activity = requireActivity() as RestoreActivity
    activity.onBackupCompletedSuccessfully()
  }

  @RequiresApi(29)
  private fun displayConfirmationDialog(context: Context) {
    Log.d(TAG, "Showing continue using local backups dialog")
    MaterialAlertDialogBuilder(context)
      .setTitle(R.string.RestoreBackupFragment__restore_complete)
      .setMessage(R.string.RestoreBackupFragment__to_continue_using_backups_please_choose_a_folder)
      .setPositiveButton(R.string.RestoreBackupFragment__choose_folder) { _, _ ->
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
          Log.d(TAG, "Showing external file picker for new backup directory")
          selectBackupDirectory.launch(intent)
        } catch (_: ActivityNotFoundException) {
          Log.w(TAG, "No external activity found")
          Toast.makeText(context, R.string.BackupDialog_no_file_picker_available, Toast.LENGTH_LONG).show()
          BackupPassphrase.set(context, null)
          resumeRegistrationAfterLocalBackupRestore()
        }
      }
      .setNegativeButton(R.string.RestoreBackupFragment__not_now) { _, _ ->
        BackupPassphrase.set(context, null)
        resumeRegistrationAfterLocalBackupRestore()
      }
      .setCancelable(false)
      .show()
  }

  private fun enableLocalBackups(context: Context) {
    if (BackupUtil.canUserAccessBackupDirectory(context)) {
      LocalBackupListener.setNextBackupTimeToIntervalFromNow(context)
      SignalStore.settings.isBackupEnabled = true
      LocalBackupListener.schedule(context)
    }
  }

  override fun onStart() {
    super.onStart()
    EventBus.getDefault().register(this)
  }

  override fun onStop() {
    super.onStop()
    EventBus.getDefault().unregister(this)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onEvent(event: BackupEvent) {
    restoreLocalBackupViewModel.onBackupProgressUpdate(event)
  }

  private fun handleBackupFileStateError(fileState: BackupUtil.BackupFileState) {
    @StringRes
    val errorResId: Int = when (fileState) {
      BackupUtil.BackupFileState.READABLE -> throw AssertionError("Unexpected error state.")
      BackupUtil.BackupFileState.NOT_FOUND -> R.string.RestoreBackupFragment__backup_not_found
      BackupUtil.BackupFileState.NOT_READABLE -> R.string.RestoreBackupFragment__backup_has_a_bad_extension
      BackupUtil.BackupFileState.UNSUPPORTED_FILE_EXTENSION -> R.string.RestoreBackupFragment__backup_could_not_be_read
    }

    Toast.makeText(requireContext(), errorResId, Toast.LENGTH_LONG).show()
  }

  private fun handleBackupImportError(importResult: RestoreRepository.BackupImportResult) {
    when (importResult) {
      RestoreRepository.BackupImportResult.FAILURE_VERSION_DOWNGRADE -> {
        Log.i(TAG, "Notifying user of restore failure due to version downgrade.")
        Toast.makeText(requireContext(), R.string.RegistrationActivity_backup_failure_downgrade, Toast.LENGTH_LONG).show()
      }

      RestoreRepository.BackupImportResult.FAILURE_FOREIGN_KEY -> {
        Log.i(TAG, "Notifying user of restore failure due to foreign key.")
        Toast.makeText(requireContext(), R.string.RegistrationActivity_backup_failure_foreign_key, Toast.LENGTH_LONG).show()
      }

      RestoreRepository.BackupImportResult.FAILURE_UNKNOWN -> {
        Log.i(TAG, "Notifying user of restore failure due to incorrect passphrase.")
        Toast.makeText(requireContext(), R.string.RegistrationActivity_incorrect_backup_passphrase, Toast.LENGTH_LONG).show()
      }

      RestoreRepository.BackupImportResult.SUCCESS -> {
        Log.w(TAG, "Successful backup import should not be handled in this function.", IllegalStateException())
      }
    }
  }

  private fun presentProgressEnded() {
    binding.restoreButton.cancelSpinning()
    binding.cancelLocalRestoreButton.visible = true
    binding.backupProgressText.text = null
  }

  private fun presentRestoreProgress(backupProgressCount: Long) {
    binding.restoreButton.setSpinning()
    binding.cancelLocalRestoreButton.visibility = View.INVISIBLE
    if (backupProgressCount > 0L) {
      binding.backupProgressText.text = getString(R.string.RegistrationActivity_d_messages_so_far, backupProgressCount)
    } else {
      binding.backupProgressText.setText(R.string.RegistrationActivity_checking)
    }
  }

  private fun presentBackupPassPhrasePromptDialog() {
    val view = LayoutInflater.from(requireContext()).inflate(R.layout.enter_backup_passphrase_dialog, null)
    val prompt = view.findViewById<EditText>(R.id.restore_passphrase_input)

    prompt.addTextChangedListener(PassphraseAsYouTypeFormatter())

    val alertDialog = MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.RegistrationActivity_enter_backup_passphrase)
      .setView(view)
      .setPositiveButton(R.string.RegistrationActivity_restore) { _, _ ->
        // Do nothing, we'll handle this in the setOnShowListener method below.
      }
      .setNegativeButton(android.R.string.cancel, null)
      .create()

    alertDialog.setOnShowListener { dialog ->
      val positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
      positiveButton.isEnabled = false

      prompt.doOnTextChanged { text, _, _, _ ->
        val input = text.toString()
        positiveButton.isEnabled = input.isNotBlank()
      }

      positiveButton.setOnClickListener {
        ViewUtil.hideKeyboard(requireContext(), prompt)
        restoreLocalBackupViewModel.confirmPassphraseAndBeginRestore(requireContext(), prompt.text.toString())
        dialog.dismiss()
      }
    }
    alertDialog.show()

    Log.i(TAG, "Prompt for backup passphrase shown to user.")
  }

  private fun presentBackupFileInfo(backupSize: Long, backupTimestamp: Long) {
    if (backupSize > 0) {
      binding.backupSizeText.text = getString(R.string.RegistrationActivity_backup_size_s, backupSize.bytes.toUnitString())
    }

    if (backupTimestamp > 0) {
      binding.backupCreatedText.text = getString(R.string.RegistrationActivity_backup_timestamp_s, DateUtils.getExtendedRelativeTimeSpanString(requireContext(), Locale.getDefault(), backupTimestamp))
    }
  }

  companion object {
    private val TAG = Log.tag(RestoreLocalBackupFragment::class.java)
  }
}
