package org.thoughtcrime.securesms.components.settings.conversation

import android.Manifest
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.DimensionUnit
import org.signal.core.util.Result
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.addTo
import org.signal.core.util.getParcelableArrayListExtraCompat
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.AvatarPreviewActivity
import org.thoughtcrime.securesms.BlockUnblockDialog
import org.thoughtcrime.securesms.MuteDialog
import org.thoughtcrime.securesms.PushContactSelectionActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.Badges.displayBadges
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.recyclerview.OnScrollAnimationHelper
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.NO_TINT
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.CheckoutFlowActivity
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.components.settings.conversation.preferences.AvatarPreference
import org.thoughtcrime.securesms.components.settings.conversation.preferences.BioTextPreference
import org.thoughtcrime.securesms.components.settings.conversation.preferences.ButtonStripPreference
import org.thoughtcrime.securesms.components.settings.conversation.preferences.CallPreference
import org.thoughtcrime.securesms.components.settings.conversation.preferences.GroupDescriptionPreference
import org.thoughtcrime.securesms.components.settings.conversation.preferences.InternalPreference
import org.thoughtcrime.securesms.components.settings.conversation.preferences.LargeIconClickPreference
import org.thoughtcrime.securesms.components.settings.conversation.preferences.LegacyGroupPreference
import org.thoughtcrime.securesms.components.settings.conversation.preferences.RecipientPreference
import org.thoughtcrime.securesms.components.settings.conversation.preferences.SharedMediaPreference
import org.thoughtcrime.securesms.components.settings.conversation.preferences.Utils.formatMutedUntil
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.ui.GroupErrors
import org.thoughtcrime.securesms.groups.ui.GroupLimitDialog
import org.thoughtcrime.securesms.groups.ui.LeaveGroupDialog
import org.thoughtcrime.securesms.groups.ui.addmembers.AddMembersActivity
import org.thoughtcrime.securesms.groups.ui.addtogroup.AddToGroupsActivity
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.ManagePendingAndRequestingMembersActivity
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupDescriptionDialog
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupInviteSentDialog
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupsLearnMoreBottomSheetDialogFragment
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory
import org.thoughtcrime.securesms.mediasend.camerax.CameraXUtil
import org.thoughtcrime.securesms.messagerequests.MessageRequestRepository
import org.thoughtcrime.securesms.nicknames.NicknameActivity
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.profiles.edit.CreateProfileActivity
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientExporter
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.recipients.ui.about.AboutSheet
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.stories.StoryViewerArgs
import org.thoughtcrime.securesms.stories.dialogs.StoryDialogs
import org.thoughtcrime.securesms.stories.viewer.AddToGroupStoryDelegate
import org.thoughtcrime.securesms.stories.viewer.StoryViewerActivity
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.ContextUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.ExpirationUtil
import org.thoughtcrime.securesms.util.Material3OnScrollHelper
import org.thoughtcrime.securesms.util.ThemeUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog
import org.thoughtcrime.securesms.verify.VerifyIdentityActivity
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperActivity
import java.util.Locale

private const val REQUEST_CODE_VIEW_CONTACT = 1
private const val REQUEST_CODE_ADD_CONTACT = 2
private const val REQUEST_CODE_ADD_MEMBERS_TO_GROUP = 3
private const val REQUEST_CODE_RETURN_FROM_MEDIA = 4

class ConversationSettingsFragment : DSLSettingsFragment(
  layoutId = R.layout.conversation_settings_fragment,
  menuId = R.menu.conversation_settings
) {

  private val args: ConversationSettingsFragmentArgs by navArgs()
  private val alertTint by lazy { ContextCompat.getColor(requireContext(), R.color.signal_alert_primary) }
  private val alertDisabledTint by lazy { ContextCompat.getColor(requireContext(), R.color.signal_alert_primary_50) }
  private val blockIcon by lazy {
    ContextUtil.requireDrawable(requireContext(), R.drawable.symbol_block_24).apply {
      colorFilter = PorterDuffColorFilter(alertTint, PorterDuff.Mode.SRC_IN)
    }
  }

  private val leaveIcon by lazy {
    ContextUtil.requireDrawable(requireContext(), R.drawable.symbol_leave_24).apply {
      colorFilter = PorterDuffColorFilter(alertTint, PorterDuff.Mode.SRC_IN)
    }
  }

  private val viewModel by viewModels<ConversationSettingsViewModel>(
    factoryProducer = {
      val groupId = args.groupId as? GroupId

      ConversationSettingsViewModel.Factory(
        recipientId = args.recipientId,
        groupId = groupId,
        callMessageIds = args.callMessageIds ?: longArrayOf(),
        repository = ConversationSettingsRepository(requireContext()),
        messageRequestRepository = MessageRequestRepository(requireContext())
      )
    }
  )

  private lateinit var callback: Callback

  private lateinit var toolbar: Toolbar
  private lateinit var toolbarAvatarContainer: FrameLayout
  private lateinit var toolbarAvatar: AvatarImageView
  private lateinit var toolbarBadge: BadgeImageView
  private lateinit var toolbarTitle: TextView
  private lateinit var toolbarBackground: View
  private lateinit var addToGroupStoryDelegate: AddToGroupStoryDelegate
  private lateinit var nicknameLauncher: ActivityResultLauncher<NicknameActivity.Args>

  private val navController get() = Navigation.findNavController(requireView())
  private val lifecycleDisposable = LifecycleDisposable()

  override fun onAttach(context: Context) {
    super.onAttach(context)

    callback = context as Callback
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    toolbar = view.findViewById(R.id.toolbar)
    toolbarAvatarContainer = view.findViewById(R.id.toolbar_avatar_container)
    toolbarAvatar = view.findViewById(R.id.toolbar_avatar)
    toolbarBadge = view.findViewById(R.id.toolbar_badge)
    toolbarTitle = view.findViewById(R.id.toolbar_title)
    toolbarBackground = view.findViewById(R.id.toolbar_background)

    val args: ConversationSettingsFragmentArgs = ConversationSettingsFragmentArgs.fromBundle(requireArguments())
    if (args.recipientId != null) {
      layoutManagerProducer = Badges::createLayoutManagerForGridWithBadges
    }

    super.onViewCreated(view, savedInstanceState)

    recyclerView?.addOnScrollListener(ConversationSettingsOnUserScrolledAnimationHelper(toolbarAvatarContainer, toolbarTitle, toolbarBackground))
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (requestCode) {
      REQUEST_CODE_ADD_MEMBERS_TO_GROUP -> if (data != null) {
        val selected: List<RecipientId> = requireNotNull(data.getParcelableArrayListExtraCompat(PushContactSelectionActivity.KEY_SELECTED_RECIPIENTS, RecipientId::class.java))
        val progress: SimpleProgressDialog.DismissibleDialog = SimpleProgressDialog.showDelayed(requireContext())

        viewModel.onAddToGroupComplete(selected) {
          progress.dismiss()
        }
      }

      REQUEST_CODE_RETURN_FROM_MEDIA -> viewModel.refreshSharedMedia()
      REQUEST_CODE_ADD_CONTACT -> viewModel.refreshRecipient()
      REQUEST_CODE_VIEW_CONTACT -> viewModel.refreshRecipient()
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return if (item.itemId == R.id.action_edit) {
      val args = ConversationSettingsFragmentArgs.fromBundle(requireArguments())
      val groupId = args.groupId as GroupId

      startActivity(CreateProfileActivity.getIntentForGroupProfile(requireActivity(), requireNotNull(groupId)))
      true
    } else {
      super.onOptionsItemSelected(item)
    }
  }

  override fun getMaterial3OnScrollHelper(toolbar: Toolbar?): Material3OnScrollHelper {
    return object : Material3OnScrollHelper(activity = requireActivity(), views = listOf(toolbar!!), lifecycleOwner = viewLifecycleOwner) {
      override val inactiveColorSet = ColorSet.from(requireContext(),
        toolbarColorRes = R.color.signal_colorBackground_0,
        statusBarColorRes = com.google.android.material.R.attr.colorSurface
      )
    }
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    nicknameLauncher = registerForActivityResult(NicknameActivity.Contract()) {
      // Intentionally left blank
    }

    val args = ConversationSettingsFragmentArgs.fromBundle(requireArguments())

    BioTextPreference.register(adapter)
    AvatarPreference.register(adapter)
    ButtonStripPreference.register(adapter)
    LargeIconClickPreference.register(adapter)
    SharedMediaPreference.register(adapter)
    RecipientPreference.register(adapter)
    InternalPreference.register(adapter)
    GroupDescriptionPreference.register(adapter)
    LegacyGroupPreference.register(adapter)
    CallPreference.register(adapter)

    val recipientId = args.recipientId
    if (recipientId != null) {
      Badge.register(adapter) { _, _, _ ->
      }
    }

    addToGroupStoryDelegate = AddToGroupStoryDelegate(this)
    viewModel.state.observe(viewLifecycleOwner) { state ->

      if (state.recipient != Recipient.UNKNOWN) {
        toolbarAvatar.buildOptions()
          .withQuickContactEnabled(false)
          .withUseSelfProfileAvatar(false)
          .withFixedSize(ViewUtil.dpToPx(80))
          .load(state.recipient)

        state.withRecipientSettingsState {
          toolbarTitle.text = if (state.recipient.isSelf) getString(R.string.note_to_self) else state.recipient.getDisplayName(requireContext())
        }

        state.withGroupSettingsState {
          toolbarTitle.text = it.groupTitle
          toolbar.menu.findItem(R.id.action_edit).isVisible = it.canEditGroupAttributes
        }
      }

      adapter.submitList(getConfiguration(state).toMappingModelList()) {
        if (state.isLoaded) {
          (view?.parent as? ViewGroup)?.doOnPreDraw {
            callback.onContentWillRender()
          }
        }
      }
    }

    lifecycleDisposable.bindTo(viewLifecycleOwner)
    lifecycleDisposable += viewModel.events.subscribe { event ->
      when (event) {
        is ConversationSettingsEvent.AddToAGroup -> handleAddToAGroup(event)
        is ConversationSettingsEvent.AddMembersToGroup -> handleAddMembersToGroup(event)
        ConversationSettingsEvent.ShowGroupHardLimitDialog -> showGroupHardLimitDialog()
        is ConversationSettingsEvent.ShowAddMembersToGroupError -> showAddMembersToGroupError(event)
        is ConversationSettingsEvent.ShowBlockGroupError -> showBlockGroupError(event)
        is ConversationSettingsEvent.ShowGroupInvitesSentDialog -> showGroupInvitesSentDialog(event)
        is ConversationSettingsEvent.ShowMembersAdded -> showMembersAdded(event)
      }
    }
  }

  private fun getConfiguration(state: ConversationSettingsState): DSLConfiguration {
    return configure {
      if (state.recipient == Recipient.UNKNOWN) {
        return@configure
      }

      customPref(
        AvatarPreference.Model(
          recipient = state.recipient,
          storyViewState = state.storyViewState,
          onAvatarClick = { avatar ->
            val viewAvatarIntent = AvatarPreviewActivity.intentFromRecipientId(requireContext(), state.recipient.id)
            val viewAvatarTransitionBundle = AvatarPreviewActivity.createTransitionBundle(requireActivity(), avatar)

            if (Stories.isFeatureEnabled() && avatar.hasStory()) {
              val viewStoryIntent = StoryViewerActivity.createIntent(
                requireContext(),
                StoryViewerArgs(
                  recipientId = state.recipient.id,
                  isInHiddenStoryMode = state.recipient.shouldHideStory,
                  isFromQuote = true
                )
              )
              StoryDialogs.displayStoryOrProfileImage(
                context = requireContext(),
                onViewStory = { startActivity(viewStoryIntent) },
                onViewAvatar = { startActivity(viewAvatarIntent, viewAvatarTransitionBundle) }
              )
            } else if (!state.recipient.isSelf) {
              startActivity(viewAvatarIntent, viewAvatarTransitionBundle)
            }
          },
          onBadgeClick = { _ ->
          }
        )
      )

      state.withRecipientSettingsState {
        customPref(
          BioTextPreference.RecipientModel(
            recipient = state.recipient,
            linkedDevices = RecipientUtil.getSubDeviceCount(requireContext(), state.recipient).orElse(null),
            onHeadlineClickListener = if (state.recipient.isSelf || !state.recipient.isIndividual) {
              null
            } else{
              { AboutSheet.create(state.recipient).show(parentFragmentManager, null) }
            }
          )
        )
      }

      state.withGroupSettingsState { groupState ->

        val groupMembershipDescription = if (groupState.groupId.isV1) {
          String.format("%s · %s", groupState.membershipCountDescription, getString(R.string.ManageGroupActivity_legacy_group))
        } else if (!groupState.canEditGroupAttributes && groupState.groupDescription.isNullOrEmpty()) {
          groupState.membershipCountDescription
        } else {
          null
        }

        customPref(
          BioTextPreference.GroupModel(
            groupTitle = groupState.groupTitle,
            groupMembershipDescription = groupMembershipDescription
          )
        )

        if (groupState.groupId.isV2) {
          customPref(
            GroupDescriptionPreference.Model(
              groupId = groupState.groupId,
              groupDescription = groupState.groupDescription,
              descriptionShouldLinkify = groupState.groupDescriptionShouldLinkify,
              canEditGroupAttributes = groupState.canEditGroupAttributes,
              onEditGroupDescription = {
                startActivity(CreateProfileActivity.getIntentForGroupProfileWithFocusedDescription(requireActivity(), groupState.groupId))
              },
              onViewGroupDescription = {
                GroupDescriptionDialog.show(childFragmentManager, groupState.groupId, null, groupState.groupDescriptionShouldLinkify)
              }
            )
          )
        } else if (groupState.legacyGroupState != LegacyGroupPreference.State.NONE) {
          customPref(
            LegacyGroupPreference.Model(
              state = groupState.legacyGroupState,
              onLearnMoreClick = { GroupsLearnMoreBottomSheetDialogFragment.show(parentFragmentManager) },
              onMmsWarningClick = { startActivity(AppSettingsActivity.invite(requireContext())) }
            )
          )
        }
      }

      if (state.displayInternalRecipientDetails) {
        customPref(
          InternalPreference.Model(
            recipient = state.recipient,
            onInternalDetailsClicked = {
              val action = ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToInternalDetailsSettingsFragment(state.recipient.id)
              navController.safeNavigate(action)
            }
          )
        )
      }

      customPref(
        ButtonStripPreference.Model(
          state = state.buttonStripState,
          enabled = !state.isDeprecatedOrUnregistered,
          onMessageClick = {
            val intent = ConversationIntents
              .createBuilderSync(requireContext(), state.recipient.id, state.threadId)
              .build()

            startActivity(intent)
          },
          onAddToStoryClick = {
            if (state.recipient.isPushV2Group && state.requireGroupSettingsState().isAnnouncementGroup && !state.requireGroupSettingsState().isSelfAdmin) {
              MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ConversationSettingsFragment__cant_add_to_group_story)
                .setMessage(R.string.ConversationSettingsFragment__only_admins_of_this_group_can_add_to_its_story)
                .setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
                .show()
            } else if (CameraXUtil.isSupported()) {
              addToGroupStoryDelegate.addToStory(state.recipient.id)
            } else {
              Permissions.with(this@ConversationSettingsFragment)
                .request(Manifest.permission.CAMERA)
                .ifNecessary()
                .withRationaleDialog(getString(R.string.CameraXFragment_allow_access_camera), getString(R.string.CameraXFragment_to_capture_photos_and_video_allow_camera), R.drawable.symbol_camera_24)
                .withPermanentDenialDialog(getString(R.string.CameraXFragment_signal_needs_camera_access_capture_photos), null, R.string.CameraXFragment_allow_access_camera, R.string.CameraXFragment_to_capture_photos_videos, getParentFragmentManager())
                .onAllGranted { addToGroupStoryDelegate.addToStory(state.recipient.id) }
                .onAnyDenied { Toast.makeText(requireContext(), R.string.CameraXFragment_signal_needs_camera_access_capture_photos, Toast.LENGTH_LONG).show() }
                .execute()
            }
          },
          onVideoClick = {
            if (state.recipient.isPushV2Group && state.requireGroupSettingsState().isAnnouncementGroup && !state.requireGroupSettingsState().isSelfAdmin) {
              MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ConversationActivity_cant_start_group_call)
                .setMessage(R.string.ConversationActivity_only_admins_of_this_group_can_start_a_call)
                .setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
                .show()
            } else {
              CommunicationActions.startVideoCall(requireActivity(), state.recipient) {
                YouAreAlreadyInACallSnackbar.show(requireView())
              }
            }
          },
          onAudioClick = {
            CommunicationActions.startVoiceCall(requireActivity(), state.recipient) {
              YouAreAlreadyInACallSnackbar.show(requireView())
            }
          },
          onMuteClick = {
            if (!state.buttonStripState.isMuted) {
              MuteDialog.show(requireContext(), viewModel::setMuteUntil)
            } else {
              MaterialAlertDialogBuilder(requireContext())
                .setMessage(state.recipient.muteUntil.formatMutedUntil(requireContext()))
                .setPositiveButton(R.string.ConversationSettingsFragment__unmute) { dialog, _ ->
                  viewModel.unmute()
                  dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                .show()
            }
          },
          onSearchClick = {
            lifecycleDisposable += ConversationIntents.createBuilder(requireContext(), state.recipient.id, state.threadId)
              .subscribeBy { builder ->
                val intent = builder
                  .withSearchOpen(true)
                  .build()

                startActivity(intent)
                requireActivity().finish()
              }
          }
        )
      )

      dividerPref()

      if (state.calls.isNotEmpty()) {
        val firstCall = state.calls.first()
        sectionHeaderPref(DSLSettingsText.from(DateUtils.formatDate(Locale.getDefault(), firstCall.record.timestamp)))

        for (call in state.calls) {
          customPref(call)
        }

        dividerPref()
      }

      if (state.recipient.isReleaseNotes) {
        textPref(
          icon = DSLSettingsIcon.from(R.drawable.symbol_official_20),
          title = DSLSettingsText.from(R.string.ReleaseNotes__this_is_official_chat)
        )
        textPref(
          icon = DSLSettingsIcon.from(R.drawable.symbol_bell_20),
          title = DSLSettingsText.from(R.string.ReleaseNotes__keep_up_to_date)
        )
        dividerPref()
      }

      val summary = DSLSettingsText.from(formatDisappearingMessagesLifespan(state.disappearingMessagesLifespan))
      val icon = if (state.disappearingMessagesLifespan <= 0 || state.recipient.isBlocked) {
        R.drawable.symbol_timer_slash_24
      } else {
        R.drawable.symbol_timer_24
      }

      var enabled = !state.recipient.isBlocked
      state.withGroupSettingsState {
        enabled = it.canEditGroupAttributes && !state.recipient.isBlocked
      }

      if (!state.recipient.isReleaseNotes && !state.recipient.isBlocked) {
        clickPref(
          title = DSLSettingsText.from(R.string.ConversationSettingsFragment__disappearing_messages),
          summary = summary,
          icon = DSLSettingsIcon.from(icon),
          isEnabled = enabled && !state.isDeprecatedOrUnregistered,
          onClick = {
            val action = ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToAppSettingsExpireTimer()
              .setInitialValue(state.disappearingMessagesLifespan)
              .setRecipientId(state.recipient.id)
              .setForResultMode(false)

            navController.safeNavigate(action)
          }
        )
      }

      if (state.recipient.isIndividual && !state.recipient.isSelf) {
        clickPref(
          title = DSLSettingsText.from(R.string.NicknameActivity__nickname),
          icon = DSLSettingsIcon.from(R.drawable.symbol_edit_24),
          onClick = {
            nicknameLauncher.launch(
              NicknameActivity.Args(
                state.recipient.id,
                false
              )
            )
          }
        )
      }

      if (!state.recipient.isReleaseNotes) {
        clickPref(
          title = DSLSettingsText.from(R.string.preferences__chat_color_and_wallpaper),
          icon = DSLSettingsIcon.from(R.drawable.symbol_color_24),
          onClick = {
            startActivity(ChatWallpaperActivity.createIntent(requireContext(), state.recipient.id))
          }
        )
      }

      if (!state.recipient.isSelf) {
        clickPref(
          title = DSLSettingsText.from(R.string.ConversationSettingsFragment__sounds_and_notifications),
          icon = DSLSettingsIcon.from(R.drawable.symbol_speaker_24),
          isEnabled = !state.isDeprecatedOrUnregistered,
          onClick = {
            val action = ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToSoundsAndNotificationsSettingsFragment(state.recipient.id)

            navController.safeNavigate(action)
          }
        )
      }

      state.withRecipientSettingsState { recipientState ->
        when (recipientState.contactLinkState) {
          ContactLinkState.OPEN -> {
            @Suppress("DEPRECATION")
            clickPref(
              title = DSLSettingsText.from(R.string.ConversationSettingsFragment__contact_details),
              icon = DSLSettingsIcon.from(R.drawable.ic_profile_circle_24),
              onClick = {
                startActivityForResult(Intent(Intent.ACTION_VIEW, state.recipient.contactUri), REQUEST_CODE_VIEW_CONTACT)
              }
            )
          }

          ContactLinkState.ADD -> {
            @Suppress("DEPRECATION")
            clickPref(
              title = DSLSettingsText.from(R.string.ConversationSettingsFragment__add_to_your_phones_contacts),
              icon = DSLSettingsIcon.from(R.drawable.ic_plus_24),
              onClick = {
                try {
                  startActivityForResult(RecipientExporter.export(state.recipient).asAddContactIntent(), REQUEST_CODE_ADD_CONTACT)
                } catch (e: ActivityNotFoundException) {
                  Toast.makeText(context, R.string.ConversationSettingsFragment__contacts_app_not_found, Toast.LENGTH_SHORT).show()
                }
              }
            )
          }

          ContactLinkState.NONE -> {
          }
        }

        if (!state.recipient.isReleaseNotes && !state.recipient.isSelf) {
          clickPref(
            title = DSLSettingsText.from(R.string.ConversationSettingsFragment__view_safety_number),
            icon = DSLSettingsIcon.from(R.drawable.symbol_safety_number_24),
            isEnabled = !state.isDeprecatedOrUnregistered,
            onClick = {
              VerifyIdentityActivity.startOrShowExchangeMessagesDialog(requireActivity(), recipientState.identityRecord)
            }
          )
        }
      }

      if (state.sharedMedia.isNotEmpty()) {
        dividerPref()

        sectionHeaderPref(R.string.recipient_preference_activity__shared_media)

        @Suppress("DEPRECATION")
        customPref(
          SharedMediaPreference.Model(
            mediaRecords = state.sharedMedia,
            mediaIds = state.sharedMediaIds,
            onMediaRecordClick = { view, mediaRecord, isLtr ->
              if (mediaRecord.attachment?.transferState != AttachmentTable.TRANSFER_PROGRESS_DONE) {
                Toast.makeText(context, R.string.ConversationSettingsFragment__this_media_is_not_sent_yet, Toast.LENGTH_LONG).show()
                return@Model
              }
              view.transitionName = "thumb"
              val options = ActivityOptions.makeSceneTransitionAnimation(requireActivity(), view, "thumb")
              startActivityForResult(
                MediaIntentFactory.intentFromMediaRecord(requireContext(), mediaRecord, isLtr, allMediaInRail = true),
                REQUEST_CODE_RETURN_FROM_MEDIA,
                options.toBundle()
              )
            }
          )
        )

        clickPref(
          title = DSLSettingsText.from(R.string.ConversationSettingsFragment__see_all),
          onClick = {
            startActivity(MediaOverviewActivity.forThread(requireContext(), state.threadId))
          }
        )
      }

      if (state.recipient.isReleaseNotes) {
        dividerPref()
        sectionHeaderPref(R.string.preferences__help)

        externalLinkPref(
          icon = DSLSettingsIcon.from(R.drawable.symbol_help_24),
          title = DSLSettingsText.from(R.string.HelpSettingsFragment__support_center),
          linkId = R.string.support_center_url
        )
        clickPref(
          icon = DSLSettingsIcon.from(R.drawable.symbol_invite_24),
          title = DSLSettingsText.from(R.string.HelpSettingsFragment__contact_us),
          onClick = { startActivity(AppSettingsActivity.help(requireContext())) }
        )
        clickPref(
          icon = DSLSettingsIcon.from(R.drawable.symbol_heart_24),
          title = DSLSettingsText.from(R.string.preferences__donate_to_signal),
          onClick = { startActivity(CheckoutFlowActivity.createIntent(requireContext(), InAppPaymentType.ONE_TIME_DONATION)) }
        )
      }

      state.withRecipientSettingsState { recipientSettingsState ->
        if (state.recipient.badges.isNotEmpty() && !state.recipient.isSelf) {
          dividerPref()

          sectionHeaderPref(R.string.ManageProfileFragment_badges)

          displayBadges(requireContext(), state.recipient.badges)

          textPref(
            summary = DSLSettingsText.from(
              R.string.ConversationSettingsFragment__get_badges
            )
          )
        }

        if (recipientSettingsState.selfHasGroups && !state.recipient.isReleaseNotes) {
          dividerPref()

          val groupsInCommonCount = recipientSettingsState.allGroupsInCommon.size
          sectionHeaderPref(
            DSLSettingsText.from(
              if (groupsInCommonCount == 0) {
                getString(R.string.ManageRecipientActivity_no_groups_in_common)
              } else {
                resources.getQuantityString(
                  R.plurals.ManageRecipientActivity_d_groups_in_common,
                  groupsInCommonCount,
                  groupsInCommonCount
                )
              }
            )
          )

          if (!state.recipient.isBlocked) {
            customPref(
              LargeIconClickPreference.Model(
                title = DSLSettingsText.from(R.string.ConversationSettingsFragment__add_to_a_group),
                icon = DSLSettingsIcon.from(R.drawable.add_to_a_group, NO_TINT),
                isEnabled = !state.isDeprecatedOrUnregistered,
                onClick = {
                  viewModel.onAddToGroup()
                }
              )
            )
          }

          for (group in recipientSettingsState.groupsInCommon) {
            customPref(
              RecipientPreference.Model(
                recipient = group,
                onClick = {
                  CommunicationActions.startConversation(requireActivity(), group, null)
                  requireActivity().finish()
                }
              )
            )
          }

          if (recipientSettingsState.canShowMoreGroupsInCommon) {
            customPref(
              LargeIconClickPreference.Model(
                title = DSLSettingsText.from(R.string.ConversationSettingsFragment__see_all),
                icon = DSLSettingsIcon.from(R.drawable.show_more, NO_TINT),
                onClick = {
                  viewModel.revealAllMembers()
                }
              )
            )
          }
        }
      }

      state.withGroupSettingsState { groupState ->
        val memberCount = groupState.allMembers.size

        if (groupState.canAddToGroup || memberCount > 0) {
          dividerPref()

          sectionHeaderPref(DSLSettingsText.from(resources.getQuantityString(R.plurals.ContactSelectionListFragment_d_members, memberCount, memberCount)))
        }

        if (groupState.canAddToGroup && !state.isDeprecatedOrUnregistered) {
          customPref(
            LargeIconClickPreference.Model(
              title = DSLSettingsText.from(R.string.ConversationSettingsFragment__add_members),
              icon = DSLSettingsIcon.from(R.drawable.add_to_a_group, NO_TINT),
              onClick = {
                viewModel.onAddToGroup()
              }
            )
          )
        }

        for (member in groupState.members) {
          customPref(
            RecipientPreference.Model(
              recipient = member.member,
              isAdmin = member.isAdmin,
              lifecycleOwner = viewLifecycleOwner,
              onClick = {
                RecipientBottomSheetDialogFragment.show(parentFragmentManager, member.member.id, groupState.groupId)
              }
            )
          )
        }

        if (groupState.canShowMoreGroupMembers) {
          customPref(
            LargeIconClickPreference.Model(
              title = DSLSettingsText.from(R.string.ConversationSettingsFragment__see_all),
              icon = DSLSettingsIcon.from(R.drawable.show_more, NO_TINT),
              onClick = {
                viewModel.revealAllMembers()
              }
            )
          )
        }

        if (state.recipient.isPushV2Group) {
          dividerPref()

          clickPref(
            title = DSLSettingsText.from(R.string.ConversationSettingsFragment__group_link),
            summary = DSLSettingsText.from(if (groupState.groupLinkEnabled) R.string.preferences_on else R.string.preferences_off),
            icon = DSLSettingsIcon.from(R.drawable.ic_link_16),
            isEnabled = !state.isDeprecatedOrUnregistered,
            onClick = {
              navController.safeNavigate(ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToShareableGroupLinkFragment(groupState.groupId.requireV2().toString()))
            }
          )

          clickPref(
            title = DSLSettingsText.from(R.string.ConversationSettingsFragment__requests_and_invites),
            icon = DSLSettingsIcon.from(R.drawable.ic_update_group_add_16),
            isEnabled = !state.isDeprecatedOrUnregistered,
            onClick = {
              startActivity(ManagePendingAndRequestingMembersActivity.newIntent(requireContext(), groupState.groupId.requireV2()))
            }
          )

          if (groupState.isSelfAdmin) {
            clickPref(
              title = DSLSettingsText.from(R.string.ConversationSettingsFragment__permissions),
              icon = DSLSettingsIcon.from(R.drawable.ic_lock_24),
              isEnabled = !state.isDeprecatedOrUnregistered,
              onClick = {
                val action = ConversationSettingsFragmentDirections.actionConversationSettingsFragmentToPermissionsSettingsFragment(groupState.groupId)
                navController.safeNavigate(action)
              }
            )
          }
        }

        if (groupState.canLeave) {
          dividerPref()

          clickPref(
            title = DSLSettingsText.from(R.string.conversation__menu_leave_group, if (state.isDeprecatedOrUnregistered) alertDisabledTint else alertTint),
            icon = DSLSettingsIcon.from(leaveIcon),
            isEnabled = !state.isDeprecatedOrUnregistered,
            onClick = {
              LeaveGroupDialog.handleLeavePushGroup(requireActivity(), groupState.groupId.requirePush(), null)
            }
          )
        }
      }

      if (state.canModifyBlockedState) {
        state.withRecipientSettingsState {
          dividerPref()
        }

        state.withGroupSettingsState {
          if (!it.canLeave) {
            dividerPref()
          }
        }

        val isBlocked = state.recipient.isBlocked
        val isGroup = state.recipient.isPushGroup

        val title = when {
          isBlocked && isGroup -> R.string.ConversationSettingsFragment__unblock_group
          isBlocked -> R.string.ConversationSettingsFragment__unblock
          isGroup -> R.string.ConversationSettingsFragment__block_group
          else -> R.string.ConversationSettingsFragment__block
        }

        val titleTint = if (isBlocked) {
          null
        } else if (state.isDeprecatedOrUnregistered) {
          alertDisabledTint
        } else {
          alertTint
        }

        clickPref(
          title = if (titleTint != null) DSLSettingsText.from(title, titleTint) else DSLSettingsText.from(title),
          icon = if (isBlocked) DSLSettingsIcon.from(R.drawable.symbol_block_24) else DSLSettingsIcon.from(blockIcon),
          isEnabled = !state.isDeprecatedOrUnregistered,
          onClick = {
            if (state.recipient.isBlocked) {
              BlockUnblockDialog.showUnblockFor(requireContext(), viewLifecycleOwner.lifecycle, state.recipient) {
                viewModel.unblock()
              }
            } else {
              BlockUnblockDialog.showBlockFor(requireContext(), viewLifecycleOwner.lifecycle, state.recipient) {
                viewModel.block()
              }
            }
          }
        )

        if (!state.recipient.isReleaseNotes) {
          val reportSpamTint = if (state.isDeprecatedOrUnregistered) R.color.signal_alert_primary_50 else R.color.signal_alert_primary
          clickPref(
            title = DSLSettingsText.from(R.string.ConversationFragment_report_spam, ThemeUtil.getThemedColor(requireContext(), reportSpamTint)),
            icon = DSLSettingsIcon.from(R.drawable.symbol_spam_24, reportSpamTint),
            isEnabled = !state.isDeprecatedOrUnregistered,
            onClick = {
              BlockUnblockDialog.showReportSpamFor(
                requireContext(),
                viewLifecycleOwner.lifecycle,
                state.recipient,
                {
                  viewModel
                    .onReportSpam()
                    .subscribeBy {
                      Toast.makeText(requireContext(), R.string.ConversationFragment_reported_as_spam, Toast.LENGTH_SHORT).show()
                      onToolbarNavigationClicked()
                    }
                    .addTo(lifecycleDisposable)
                },
                if (state.recipient.isBlocked) {
                  null
                } else {
                  Runnable {
                    viewModel
                      .onBlockAndReportSpam()
                      .subscribeBy { result ->
                        when (result) {
                          is Result.Success -> {
                            Toast.makeText(requireContext(), R.string.ConversationFragment_reported_as_spam_and_blocked, Toast.LENGTH_SHORT).show()
                            onToolbarNavigationClicked()
                          }

                          is Result.Failure -> {
                            Toast.makeText(requireContext(), GroupErrors.getUserDisplayMessage(result.failure), Toast.LENGTH_SHORT).show()
                          }
                        }
                      }
                      .addTo(lifecycleDisposable)
                  }
                }
              )
            }
          )
        }
      }
    }
  }

  private fun formatDisappearingMessagesLifespan(disappearingMessagesLifespan: Int): String {
    return if (disappearingMessagesLifespan <= 0) {
      getString(R.string.preferences_off)
    } else {
      ExpirationUtil.getExpirationDisplayValue(requireContext(), disappearingMessagesLifespan)
    }
  }

  private fun handleAddToAGroup(addToAGroup: ConversationSettingsEvent.AddToAGroup) {
    startActivity(AddToGroupsActivity.newIntent(requireContext(), addToAGroup.recipientId, addToAGroup.groupMembership))
  }

  @Suppress("DEPRECATION")
  private fun handleAddMembersToGroup(addMembersToGroup: ConversationSettingsEvent.AddMembersToGroup) {
    startActivityForResult(
      AddMembersActivity.createIntent(
        requireContext(),
        addMembersToGroup.groupId,
        ContactSelectionDisplayMode.FLAG_PUSH,
        addMembersToGroup.selectionWarning,
        addMembersToGroup.selectionLimit,
        addMembersToGroup.isAnnouncementGroup,
        addMembersToGroup.groupMembersWithoutSelf
      ),
      REQUEST_CODE_ADD_MEMBERS_TO_GROUP
    )
  }

  private fun showGroupHardLimitDialog() {
    GroupLimitDialog.showHardLimitMessage(requireContext())
  }

  private fun showAddMembersToGroupError(showAddMembersToGroupError: ConversationSettingsEvent.ShowAddMembersToGroupError) {
    Toast.makeText(requireContext(), GroupErrors.getUserDisplayMessage(showAddMembersToGroupError.failureReason), Toast.LENGTH_LONG).show()
  }

  private fun showBlockGroupError(showBlockGroupError: ConversationSettingsEvent.ShowBlockGroupError) {
    Toast.makeText(requireContext(), GroupErrors.getUserDisplayMessage(showBlockGroupError.failureReason), Toast.LENGTH_LONG).show()
  }

  private fun showGroupInvitesSentDialog(showGroupInvitesSentDialog: ConversationSettingsEvent.ShowGroupInvitesSentDialog) {
    GroupInviteSentDialog.showInvitesSent(requireContext(), viewLifecycleOwner, showGroupInvitesSentDialog.invitesSentTo)
  }

  private fun showMembersAdded(showMembersAdded: ConversationSettingsEvent.ShowMembersAdded) {
    val string = resources.getQuantityString(
      R.plurals.ManageGroupActivity_added,
      showMembersAdded.membersAddedCount,
      showMembersAdded.membersAddedCount
    )

    Snackbar.make(requireView(), string, Snackbar.LENGTH_SHORT).show()
  }

  private class ConversationSettingsOnUserScrolledAnimationHelper(
    private val toolbarAvatar: View,
    private val toolbarTitle: View,
    private val toolbarBackground: View
  ) : OnScrollAnimationHelper() {

    override val duration: Long = 200L

    private val actionBarSize = DimensionUnit.DP.toPixels(64f)
    private val rect = Rect()

    override fun getAnimationState(recyclerView: RecyclerView): AnimationState {
      val layoutManager = recyclerView.layoutManager!!
      val firstVisibleItemPosition = if (layoutManager is FlexboxLayoutManager) {
        layoutManager.findFirstVisibleItemPosition()
      } else {
        (layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
      }

      return if (firstVisibleItemPosition == 0) {
        val firstChild = requireNotNull(layoutManager.getChildAt(0))
        firstChild.getLocalVisibleRect(rect)

        if (rect.height() <= actionBarSize) {
          AnimationState.SHOW
        } else {
          AnimationState.HIDE
        }
      } else {
        AnimationState.SHOW
      }
    }

    override fun show(duration: Long) {
      toolbarAvatar
        .animate()
        .setDuration(duration)
        .translationY(0f)
        .alpha(1f)

      toolbarTitle
        .animate()
        .setDuration(duration)
        .translationY(0f)
        .alpha(1f)

      toolbarBackground
        .animate()
        .setDuration(duration)
        .alpha(1f)
    }

    override fun hide(duration: Long) {
      toolbarAvatar
        .animate()
        .setDuration(duration)
        .translationY(ViewUtil.dpToPx(56).toFloat())
        .alpha(0f)

      toolbarTitle
        .animate()
        .setDuration(duration)
        .translationY(ViewUtil.dpToPx(56).toFloat())
        .alpha(0f)

      toolbarBackground
        .animate()
        .setDuration(duration)
        .alpha(0f)
    }
  }

  interface Callback {
    fun onContentWillRender()
  }
}
