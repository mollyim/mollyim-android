@file:Suppress("DEPRECATION")

package org.thoughtcrime.securesms.profiles.manage

import org.thoughtcrime.securesms.profiles.EditMode // Import EditMode
import org.signal.core.util.logging.Log // For logging

import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.airbnb.lottie.SimpleColorFilter
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.AvatarPreviewActivity
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.Avatars.getForegroundColor
import org.thoughtcrime.securesms.avatar.Avatars.getTextSizeForLength
import org.thoughtcrime.securesms.avatar.picker.AvatarPickerFragment
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.emoji.EmojiUtil
import org.thoughtcrime.securesms.databinding.EditProfileFragmentBinding
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.profiles.manage.EditProfileViewModel.AvatarState
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameDeleteResult
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.ui.RegistrationActivity
import org.thoughtcrime.securesms.util.NameUtil.getAbbreviation
import org.thoughtcrime.securesms.util.PlayStoreUtil
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog
import java.util.Arrays
import java.util.Optional

/**
 * Fragment for editing your profile after you're already registered.
 */
class EditProfileFragment : LoggingFragment() {

  private var avatarProgress: AlertDialog? = null

  private lateinit var viewModel: EditProfileViewModel
  private lateinit var binding: EditProfileFragmentBinding
  private lateinit var disposables: LifecycleDisposable

  private lateinit var currentEditMode: EditMode
  private var currentNoteId: Long? = null

  companion object {
    private const val DISABLED_ALPHA = 0.4f
    private const val TAG = "EditProfileFragment" // For logging

    // Argument keys (public if EditProfileActivity needs them, though NavController handles passing)
    const val ARG_EDIT_MODE = "edit_mode"  // Corresponds to EditProfileActivity.EXTRA_EDIT_MODE
    const val ARG_NOTE_ID = "note_id"      // Corresponds to EditProfileActivity.EXTRA_NOTE_ID

    @JvmStatic
    fun newInstance(editMode: EditMode, noteId: Long?): EditProfileFragment {
      // This method might not be directly called if NavController handles instantiation,
      // but it's good for defining arg keys and for potential manual instantiation.
      return EditProfileFragment().apply {
        arguments = Bundle().apply {
          putString(ARG_EDIT_MODE, editMode.name)
          noteId?.let { putLong(ARG_NOTE_ID, it) }
        }
      }
    }
  }

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem

// ... other imports

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true) // Important for fragment to handle options menu

    val editModeName = arguments?.getString(ARG_EDIT_MODE)
    currentEditMode = if (editModeName != null) {
      try {
        EditMode.valueOf(editModeName)
      } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Invalid EditMode received: $editModeName", e)
        EditMode.EDIT_SELF_PROFILE // Default to self profile on error
      }
    } else {
      Log.w(TAG, "No EditMode received, defaulting to self profile.")
      EditMode.EDIT_SELF_PROFILE // Default if no argument found
    }

    currentNoteId = if (arguments?.containsKey(ARG_NOTE_ID) == true) {
      arguments?.getLong(ARG_NOTE_ID)
    } else {
      null
    }

    Log.i(TAG, "Mode: $currentEditMode, Note ID: $currentNoteId")
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    binding = EditProfileFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

import androidx.appcompat.app.AppCompatActivity // Ensure this is imported

// ... other imports might be needed depending on what setup methods do

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    disposables = LifecycleDisposable()
    disposables.bindTo(viewLifecycleOwner)

    if (currentEditMode == EditMode.EDIT_NOTE) {
      setupNoteEditingUI()
    } else {
      setupProfileEditingUI()
    }

    UsernameEditFragment.ResultContract().registerForResult(parentFragmentManager, viewLifecycleOwner) {
      Snackbar.make(view, R.string.ManageProfileFragment__username_created, Snackbar.LENGTH_SHORT).show()
    }

    UsernameShareBottomSheet.ResultContract.registerForResult(parentFragmentManager, viewLifecycleOwner) {
      Snackbar.make(view, R.string.ManageProfileFragment__username_copied, Snackbar.LENGTH_SHORT).show()
    }

    initializeViewModel()

    binding.toolbar.setNavigationOnClickListener { requireActivity().finish() }

    if (currentEditMode != EditMode.EDIT_NOTE) {
      binding.manageProfileEditPhoto.setOnClickListener {
        if (!viewModel.isRegisteredAndUpToDate) {
          onClickWhenUnregisteredOrDeprecated()
        } else {
          onEditAvatarClicked()
        }
      }
    } else {
      // Ensure no listener if it's a note, or view is GONE
      binding.manageProfileEditPhoto.setOnClickListener(null)
    }

    binding.manageProfileNameContainer.setOnClickListener { v: View ->
      if (!viewModel.isRegisteredAndUpToDate) {
        onClickWhenUnregisteredOrDeprecated()
      } else {
        findNavController(v).safeNavigate(EditProfileFragmentDirections.actionManageProfileName())
      }
    }

    binding.manageProfileUsernameContainer.setOnClickListener { v: View ->
      if (!viewModel.isRegisteredAndUpToDate) {
        onClickWhenUnregisteredOrDeprecated()
      } else if (SignalStore.account.username != null) {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_Signal_MaterialAlertDialog_List)
          .setItems(R.array.username_edit_entries) { _: DialogInterface?, w: Int ->
            when (w) {
              0 -> findNavController(v).safeNavigate(EditProfileFragmentDirections.actionManageUsername())
              1 -> displayConfirmUsernameDeletionDialog()
              else -> throw IllegalStateException()
            }
          }
          .show()
      } else {
        findNavController(v).safeNavigate(EditProfileFragmentDirections.actionManageUsername())
      }
    }

    binding.manageProfileAboutContainer.setOnClickListener { v: View ->
      if (!viewModel.isRegisteredAndUpToDate) {
        onClickWhenUnregisteredOrDeprecated()
      } else {
        findNavController(v).safeNavigate(EditProfileFragmentDirections.actionManageAbout())
      }
    }

    if (currentEditMode != EditMode.EDIT_NOTE) {
      parentFragmentManager.setFragmentResultListener(AvatarPickerFragment.REQUEST_KEY_SELECT_AVATAR, viewLifecycleOwner) { _: String?, bundle: Bundle ->
        if (!viewModel.isRegisteredAndUpToDate) {
          onClickWhenUnregisteredOrDeprecated()
        } else if (bundle.getBoolean(AvatarPickerFragment.SELECT_AVATAR_CLEAR)) {
          viewModel.onAvatarSelected(requireContext(), null)
        } else {
          val result = bundle.getParcelableCompat(AvatarPickerFragment.SELECT_AVATAR_MEDIA, Media::class.java)
          viewModel.onAvatarSelected(requireContext(), result)
        }
      }
    }

    val avatarInitials = binding.manageProfileAvatarInitials
    avatarInitials.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
      if (avatarInitials.length() > 0) {
        updateInitials(avatarInitials.text.toString())
      }
    }

    binding.manageProfileBadgesContainer.visibility = View.GONE // This is the original line, will be handled by setupUI methods.

    if (currentEditMode != EditMode.EDIT_NOTE) {
      binding.manageProfileAvatar.setOnClickListener {
        if (!viewModel.isRegisteredAndUpToDate) {
          onClickWhenUnregisteredOrDeprecated()
        } else {
          startActivity(
            AvatarPreviewActivity.intentFromRecipientId(requireContext(), Recipient.self().id),
            AvatarPreviewActivity.createTransitionBundle(requireActivity(), binding.manageProfileAvatar)
          )
        }
      }
    } else {
      binding.manageProfileAvatar.setOnClickListener(null)
    }
  }

  private fun initializeViewModel() {
    val factory = EditProfileViewModel.Factory(
      requireActivity().application,
      currentEditMode,
      currentNoteId
    )
    viewModel = ViewModelProvider(this, factory).get(EditProfileViewModel::class.java)

    if (currentEditMode == EditMode.EDIT_NOTE) {
      viewModel.currentNote.observe(viewLifecycleOwner) { noteEntity ->
        if (noteEntity != null) {
          binding.manageProfileName.setText(noteEntity.title)
          binding.editNoteContent.setText(noteEntity.content)
          // TODO: Handle colorId if UI for color selection is added
        }
      }
      // Observe saving state if needed for UI updates (e.g., show progress)
      // viewModel.isSaving.observe(viewLifecycleOwner) { saving -> /* ... */ }
    } else {
      // Existing observers for profile mode
      LiveDataUtil
        .distinctUntilChanged(viewModel.avatar) { b1, b2 -> Arrays.equals(b1.avatar, b2.avatar) }
        .map { avatarState -> Optional.ofNullable(avatarState.avatar) }
        .observe(viewLifecycleOwner) { avatarData -> presentAvatarImage(avatarData) }

      viewModel.avatar.observe(viewLifecycleOwner) { presentAvatarPlaceholder(it) }
      viewModel.profileName.observe(viewLifecycleOwner) { presentProfileName(it) }
      viewModel.events.observe(viewLifecycleOwner) { presentEvent(it) }
      viewModel.about.observe(viewLifecycleOwner) { presentAbout(it) }
      viewModel.aboutEmoji.observe(viewLifecycleOwner) { presentAboutEmoji(it) }
      viewModel.badge.observe(viewLifecycleOwner) { presentBadge(it) }
      viewModel.username.observe(viewLifecycleOwner) { presentUsername(it) }
    }
  }

  private fun presentAvatarImage(avatarData: Optional<ByteArray>) {
    if (avatarData.isPresent) {
      Glide.with(this)
        .load(avatarData.get())
        .circleCrop()
        .into(binding.manageProfileAvatar)
    } else {
      Glide.with(this).load(null as Drawable?).into(binding.manageProfileAvatar)
    }

    binding.manageProfileAvatar.alpha = if (viewModel.isRegisteredAndUpToDate) 1.0f else DISABLED_ALPHA
    binding.manageProfileAvatarInitials.alpha = if (viewModel.isRegisteredAndUpToDate) 1.0f else DISABLED_ALPHA
    binding.manageProfileEditPhoto.isEnabled = viewModel.isRegisteredAndUpToDate
  }

  private fun presentAvatarPlaceholder(avatarState: AvatarState) {
    if (avatarState.avatar == null) {
      val initials: CharSequence? = getAbbreviation(avatarState.self.getDisplayName(requireContext()))
      val foregroundColor = getForegroundColor(avatarState.self.avatarColor)

      binding.manageProfileAvatarBackground.colorFilter = SimpleColorFilter(avatarState.self.avatarColor.colorInt())
      binding.manageProfileAvatarPlaceholder.colorFilter = SimpleColorFilter(foregroundColor.colorInt)
      binding.manageProfileAvatarInitials.setTextColor(foregroundColor.colorInt)

      if (TextUtils.isEmpty(initials)) {
        binding.manageProfileAvatarPlaceholder.visibility = View.VISIBLE
        binding.manageProfileAvatarInitials.visibility = View.GONE
      } else {
        updateInitials(initials.toString())
        binding.manageProfileAvatarPlaceholder.visibility = View.GONE
        binding.manageProfileAvatarInitials.visibility = View.VISIBLE
      }
    } else {
      binding.manageProfileAvatarPlaceholder.visibility = View.GONE
      binding.manageProfileAvatarInitials.visibility = View.GONE
    }

    if (avatarProgress == null && avatarState.loadingState == EditProfileViewModel.LoadingState.LOADING) {
      avatarProgress = SimpleProgressDialog.show(requireContext())
    } else if (avatarProgress != null && avatarState.loadingState == EditProfileViewModel.LoadingState.LOADED) {
      avatarProgress!!.dismiss()
    }
  }

  private fun updateInitials(initials: String) {
    binding.manageProfileAvatarInitials.setTextSize(
      TypedValue.COMPLEX_UNIT_PX,
      getTextSizeForLength(
        context = requireContext(),
        text = initials,
        maxWidth = binding.manageProfileAvatarInitials.measuredWidth * 0.8f,
        maxSize = binding.manageProfileAvatarInitials.measuredWidth * 0.45f
      )
    )

    binding.manageProfileAvatarInitials.text = initials
  }

  private fun presentProfileName(profileName: ProfileName?) {
    if (profileName == null || profileName.isEmpty) {
      binding.manageProfileName.setText(R.string.ManageProfileFragment_profile_name)
    } else {
      binding.manageProfileName.text = profileName.toString()
      if (binding.manageProfileAvatarPlaceholder.isVisible || binding.manageProfileAvatarInitials.isVisible) {
        val initials = getAbbreviation(profileName.toString())
        if (TextUtils.isEmpty(initials)) {
          binding.manageProfileAvatarPlaceholder.visibility = View.VISIBLE
          binding.manageProfileAvatarInitials.visibility = View.GONE
        } else {
          updateInitials(initials.toString())
          binding.manageProfileAvatarPlaceholder.visibility = View.GONE
          binding.manageProfileAvatarInitials.visibility = View.VISIBLE
        }
      }
    }

    binding.manageProfileName.isEnabled = viewModel.isRegisteredAndUpToDate
    binding.manageProfileNameIcon.alpha = if (viewModel.isRegisteredAndUpToDate) 1.0f else DISABLED_ALPHA
  }

  private fun presentUsername(username: String?) {
    if (username.isNullOrEmpty()) {
      binding.manageProfileUsername.setText(R.string.ManageProfileFragment_username)
    } else {
      binding.manageProfileUsername.text = username
    }

    if (SignalStore.account.usernameSyncState == AccountValues.UsernameSyncState.USERNAME_AND_LINK_CORRUPTED) {
      binding.usernameErrorIndicator.visibility = View.VISIBLE
    } else {
      binding.usernameErrorIndicator.visibility = View.GONE
    }

    if (SignalStore.account.username != null && SignalStore.account.usernameSyncState != AccountValues.UsernameSyncState.USERNAME_AND_LINK_CORRUPTED) {
      binding.usernameLinkContainer.setOnClickListener {
        findNavController().safeNavigate(EditProfileFragmentDirections.actionManageProfileFragmentToUsernameLinkFragment())
      }

      if (SignalStore.account.usernameSyncState == AccountValues.UsernameSyncState.LINK_CORRUPTED) {
        binding.linkErrorIndicator.visibility = View.VISIBLE
      } else {
        binding.linkErrorIndicator.visibility = View.GONE
      }

      if (SignalStore.tooltips.showProfileSettingsQrCodeTooltop()) {
        binding.usernameLinkTooltip.visibility = View.VISIBLE
        binding.linkTooltipCloseButton.setOnClickListener {
          binding.usernameLinkTooltip.visibility = View.GONE
          SignalStore.tooltips.markProfileSettingsQrCodeTooltipSeen()
        }
      }

      binding.usernameInfoText.setText(R.string.ManageProfileFragment__your_username)
    } else {
      binding.usernameLinkContainer.visibility = View.GONE
      binding.usernameInfoText.setText(R.string.ManageProfileFragment__username_footer_no_username)
    }

    binding.manageProfileUsername.isEnabled = viewModel.isRegisteredAndUpToDate
    binding.manageProfileUsernameIcon.alpha = if (viewModel.isRegisteredAndUpToDate) 1.0f else DISABLED_ALPHA
  }

  private fun presentAbout(about: String?) {
    if (about.isNullOrEmpty()) {
      binding.manageProfileAbout.setText(R.string.ManageProfileFragment_about)
    } else {
      binding.manageProfileAbout.text = about
    }

    binding.manageProfileAbout.isEnabled = viewModel.isRegisteredAndUpToDate
    binding.manageProfileAboutIcon.alpha = if (viewModel.isRegisteredAndUpToDate) 1.0f else DISABLED_ALPHA
  }

  private fun presentAboutEmoji(aboutEmoji: String?) {
    if (aboutEmoji.isNullOrEmpty()) {
      binding.manageProfileAboutIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.symbol_edit_24, null))
    } else {
      val emoji = EmojiUtil.convertToDrawable(requireContext(), aboutEmoji)
      if (emoji != null) {
        binding.manageProfileAboutIcon.setImageDrawable(emoji)
      } else {
        binding.manageProfileAboutIcon.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.symbol_edit_24, null))
      }
    }
  }

  private fun presentBadge(badge: Optional<Badge>) {
    if (badge.isPresent && badge.get().visible && !badge.get().isExpired()) {
      binding.manageProfileBadge.setBadge(badge.orElse(null))
    } else {
      binding.manageProfileBadge.setBadge(null)
    }

    binding.manageProfileBadges.isEnabled = viewModel.isRegisteredAndUpToDate
    binding.manageProfileBadge.alpha = if (viewModel.isRegisteredAndUpToDate) 1.0f else DISABLED_ALPHA
    binding.manageProfileBadgesIcon.alpha = if (viewModel.isRegisteredAndUpToDate) 1.0f else DISABLED_ALPHA

    if (!viewModel.isRegisteredAndUpToDate) {
      binding.manageProfileBadge.setOnClickListener { onClickWhenUnregisteredOrDeprecated() }
    }
  }

  private fun presentEvent(event: EditProfileViewModel.Event) {
    when (event) {
      EditProfileViewModel.Event.AVATAR_DISK_FAILURE -> Toast.makeText(requireContext(), R.string.ManageProfileFragment_failed_to_set_avatar, Toast.LENGTH_LONG).show()
      EditProfileViewModel.Event.AVATAR_NETWORK_FAILURE -> Toast.makeText(requireContext(), R.string.EditProfileNameFragment_failed_to_save_due_to_network_issues_try_again_later, Toast.LENGTH_LONG).show()
    }
  }

  private fun onEditAvatarClicked() {
    findNavController(requireView()).safeNavigate(EditProfileFragmentDirections.actionManageProfileFragmentToAvatarPicker(null, null))
  }

  private fun displayConfirmUsernameDeletionDialog() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.ManageProfileFragment__delete_username_dialog_title)
      .setMessage(requireContext().getString(R.string.ManageProfileFragment__delete_username_dialog_body, SignalStore.account.username))
      .setPositiveButton(R.string.delete) { _, _ -> onUserConfirmedUsernameDeletion() }
      .setNegativeButton(android.R.string.cancel) { d: DialogInterface?, w: Int -> }
      .show()
  }

  private fun onUserConfirmedUsernameDeletion() {
    binding.progressCard.visibility = View.VISIBLE

    disposables += viewModel
      .deleteUsername()
      .subscribe { result: UsernameDeleteResult ->
        binding.progressCard.visibility = View.GONE
        handleUsernameDeletionResult(result)
      }
  }

  private fun handleUsernameDeletionResult(usernameDeleteResult: UsernameDeleteResult) {
    when (usernameDeleteResult) {
      UsernameDeleteResult.SUCCESS -> {
        Snackbar.make(requireView(), R.string.ManageProfileFragment__username_deleted, Snackbar.LENGTH_SHORT).show()
        binding.usernameLinkContainer.visibility = View.GONE
      }

      UsernameDeleteResult.NETWORK_ERROR -> Snackbar.make(requireView(), R.string.ManageProfileFragment__couldnt_delete_username, Snackbar.LENGTH_SHORT).show()
    }
  }

  private fun onClickWhenUnregisteredOrDeprecated() {
    if (viewModel.isDeprecated) {
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.EditProfileFragment_deprecated_dialog_title)
        .setMessage(R.string.EditProfileFragment_deprecated_dialog_body)
        .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
        .setPositiveButton(R.string.EditProfileFragment_deprecated_dialog_update_button) { d, _ ->
          PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext())
          d.dismiss()
        }
        .show()
    } else {
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.EditProfileFragment_unregistered_dialog_title)
        .setMessage(R.string.EditProfileFragment_unregistered_dialog_body)
        .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
        .setPositiveButton(R.string.EditProfileFragment_unregistered_dialog_reregister_button) { d, _ ->
          startActivity(RegistrationActivity.newIntentForReRegistration(requireContext()))
          d.dismiss()
        }
        .show()
    }
  }

  private fun setupNoteEditingUI() {
    binding.toolbar.title = if (currentNoteId == null) getString(R.string.edit_profile_fragment_title_new_note)
                            else getString(R.string.edit_profile_fragment_title_edit_note)

    // Hide profile-specific elements
    binding.manageProfileAvatarBackground.visibility = View.GONE
    binding.manageProfileAvatarPlaceholder.visibility = View.GONE
    binding.manageProfileAvatarInitials.visibility = View.GONE
    binding.manageProfileAvatar.visibility = View.GONE
    binding.manageProfileBadge.visibility = View.GONE
    binding.manageProfileEditPhoto.visibility = View.GONE

    // Repurpose name field for Note Title
    (binding.manageProfileName as? android.widget.TextView)?.hint = getString(R.string.edit_note_title_hint)
    // binding.manageProfileNameIcon.setImageResource(R.drawable.ic_title_placeholder) // Optional: new icon

    binding.manageProfileUsernameContainer.visibility = View.GONE
    binding.usernameLinkContainer.visibility = View.GONE
    binding.usernameInfoText.visibility = View.GONE
    binding.usernameLinkTooltip.visibility = View.GONE
    binding.manageProfileAboutContainer.visibility = View.GONE // Hide original about section
    binding.manageProfileBadgesContainer.visibility = View.GONE
    binding.manageProfileDivider.visibility = View.GONE
    binding.groupDescriptionText.visibility = View.GONE

    // Show note content field
    binding.editNoteContent.visibility = View.VISIBLE

    // Data population will be handled by observing ViewModel
    if (currentNoteId == null) { // New note
        binding.manageProfileName.setText("")
        binding.editNoteContent.setText("")
    } else {
        // Placeholder for actual data loading via ViewModel
        // viewModel.loadNoteData(currentNoteId)
        // observe viewModel.noteDetails and populate fields
    }
  }

  private fun setupProfileEditingUI() {
    binding.toolbar.title = getString(R.string.CreateProfileActivity__profile)

    // Ensure profile-specific elements are visible (or their default state)
    binding.manageProfileAvatarBackground.visibility = View.VISIBLE
    // manage_profile_avatar_placeholder and manage_profile_avatar_initials visibility is handled by presentAvatarPlaceholder/presentProfileName
    binding.manageProfileAvatar.visibility = View.VISIBLE
    binding.manageProfileBadge.visibility = View.VISIBLE // Visibility also handled by presentBadge
    binding.manageProfileEditPhoto.visibility = View.VISIBLE

    (binding.manageProfileName as? android.widget.TextView)?.hint = null // Clear hint or set to original
    // binding.manageProfileNameIcon.setImageResource(R.drawable.symbol_person_24) // Restore original icon

    binding.manageProfileUsernameContainer.visibility = View.VISIBLE
    binding.usernameLinkContainer.visibility = if (SignalStore.account().username != null) View.VISIBLE else View.GONE
    binding.usernameInfoText.visibility = View.VISIBLE
    binding.manageProfileAboutContainer.visibility = View.VISIBLE
    binding.manageProfileBadgesContainer.visibility = View.VISIBLE // Visibility also handled by presentBadge
    binding.manageProfileDivider.visibility = View.VISIBLE
    binding.groupDescriptionText.visibility = View.VISIBLE

    // Hide note content field
    binding.editNoteContent.visibility = View.GONE
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    super.onCreateOptionsMenu(menu, inflater)
    inflater.inflate(R.menu.edit_profile_menu, menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == R.id.action_save_profile_note) {
      handleSave()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  private fun handleSave() {
    if (currentEditMode == EditMode.EDIT_NOTE) {
      saveNoteData()
    } else {
      // TODO: Implement existing profile save logic here
      // For example: viewModel.saveProfile(binding.manageProfileName.text.toString(), ...etc)
      // For now, just show a toast for profile save.
      Toast.makeText(requireContext(), "Profile save action (TODO)", Toast.LENGTH_SHORT).show()
      // Potentially finish activity after save: requireActivity().finish()
    }
  }

  private fun saveNoteData() {
    val title = binding.manageProfileName.text.toString()
    val content = binding.editNoteContent.text.toString()
    // val colorId: Long? = null // Future feature

    viewModel.saveCurrentNote(title, content, null /* colorId */)
    // TODO: Observe save success from ViewModel to finish activity or show confirmation
    // For now, finish directly after triggering save. This might be too soon if save is slow.
    Toast.makeText(requireContext(), "Note saved (actual save is async)", Toast.LENGTH_SHORT).show() // Placeholder
    requireActivity().finish()
  }
}
