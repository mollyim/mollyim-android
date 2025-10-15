package org.thoughtcrime.securesms.megaphone;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationManagerCompat;

import com.annimon.stream.Stream;
import com.bumptech.glide.Glide;

import org.signal.core.util.MapUtil;
import org.signal.core.util.SetUtil;
import org.signal.core.util.TranslationDetection;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.apkupdate.ApkUpdateRefreshListener;
import org.thoughtcrime.securesms.backup.v2.ui.verify.VerifyBackupKeyActivity;
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MegaphoneRecord;
import org.thoughtcrime.securesms.database.model.RemoteMegaphoneRecord;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.keyvalue.protos.LeastActiveLinkedDevice;
import org.thoughtcrime.securesms.lock.SignalPinReminderDialog;
import org.thoughtcrime.securesms.lock.SignalPinReminders;
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinActivity;
import org.thoughtcrime.securesms.lock.v2.SvrMigrationActivity;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.TurnOnNotificationsBottomSheet;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.manage.EditProfileActivity;
import org.thoughtcrime.securesms.profiles.username.NewWaysToConnectDialogFragment;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.Environment;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.VersionTracker;
import org.thoughtcrime.securesms.util.dynamiclanguage.DynamicLanguageContextWrapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Creating a new megaphone:
 * - Add an enum to {@link Event}
 * - Return a megaphone in {@link #forRecord(Context, MegaphoneRecord)}
 * - Include the event in {@link #buildDisplayOrder(Context, Map)}
 * <p>
 * Common patterns:
 * - For events that have a snooze-able recurring display schedule, use a {@link RecurringSchedule}.
 * - For events guarded by feature flags, set a {@link ForeverSchedule} with false in
 * {@link #buildDisplayOrder(Context, Map)}.
 * - For events that change, return different megaphones in {@link #forRecord(Context, MegaphoneRecord)}
 * based on whatever properties you're interested in.
 */
public final class Megaphones {

  private static final String TAG = Log.tag(Megaphones.class);

  private static final MegaphoneSchedule ALWAYS = new ForeverSchedule(true);
  private static final MegaphoneSchedule NEVER  = new ForeverSchedule(false);

  private static final Set<Event> DONATE_EVENTS                      = SetUtil.newHashSet(Event.DONATE_MOLLY);
  private static final long       MIN_TIME_BETWEEN_DONATE_MEGAPHONES = TimeUnit.DAYS.toMillis(30);

  private Megaphones() {}

  @WorkerThread
  static @Nullable Megaphone getNextMegaphone(@NonNull Context context, @NonNull Map<Event, MegaphoneRecord> records) {
    long currentTime = System.currentTimeMillis();

    List<Megaphone> megaphones = Stream.of(buildDisplayOrder(context, records))
                                       .filter(e -> {
                                         MegaphoneRecord   record   = Objects.requireNonNull(records.get(e.getKey()));
                                         MegaphoneSchedule schedule = e.getValue();

                                         return !record.isFinished() && schedule.shouldDisplay(record.getSeenCount(), record.getLastSeen(), record.getFirstVisible(), currentTime);
                                       })
                                       .map(Map.Entry::getKey)
                                       .map(records::get)
                                       .map(record -> Megaphones.forRecord(context, record))
                                       .filterNot(Objects::isNull)
                                       .toList();

    if (megaphones.size() > 0) {
      return megaphones.get(0);
    } else {
      return null;
    }
  }

  /**
   * The megaphones we want to display *in priority order*. This is a {@link LinkedHashMap}, so order is preserved.
   * We will render the first applicable megaphone in this collection.
   * <p>
   * This is also when you would hide certain megaphones based on things like {@link RemoteConfig}.
   */
  private static Map<Event, MegaphoneSchedule> buildDisplayOrder(@NonNull Context context, @NonNull Map<Event, MegaphoneRecord> records) {
    return new LinkedHashMap<>() {{
      put(Event.PINS_FOR_ALL, new PinsForAllSchedule());
      put(Event.CLIENT_DEPRECATED, SignalStore.misc().isClientDeprecated() ? ALWAYS : NEVER);
      put(Event.NEW_LINKED_DEVICE, shouldShowNewLinkedDeviceMegaphone() ? ALWAYS: NEVER);
      put(Event.NOTIFICATIONS, shouldShowNotificationsMegaphone(context) ? RecurringSchedule.every(TimeUnit.DAYS.toMillis(30)) : NEVER);
      put(Event.GRANT_FULL_SCREEN_INTENT, shouldShowGrantFullScreenIntentPermission(context) ? RecurringSchedule.every(TimeUnit.DAYS.toMillis(3)) : NEVER);
      put(Event.BACKUP_SCHEDULE_PERMISSION, shouldShowBackupSchedulePermissionMegaphone(context) ? RecurringSchedule.every(TimeUnit.DAYS.toMillis(3)) : NEVER);
      put(Event.ONBOARDING, shouldShowOnboardingMegaphone(context) ? ALWAYS : NEVER);
      put(Event.TURN_OFF_CENSORSHIP_CIRCUMVENTION, shouldShowTurnOffCircumventionMegaphone() ? RecurringSchedule.every(TimeUnit.DAYS.toMillis(7)) : NEVER);
      put(Event.ENABLE_APP_UPDATES, shouldShowEnableAppUpdatesMegaphone(context) ? ALWAYS : NEVER);
      put(Event.DONATE_MOLLY, shouldShowDonateMegaphone(context, Event.DONATE_MOLLY, records) ? ShowForDurationSchedule.showForDays(7) : NEVER);
      put(Event.REMOTE_MEGAPHONE, shouldShowRemoteMegaphone(records) ? RecurringSchedule.every(TimeUnit.DAYS.toMillis(1)) : NEVER);
      put(Event.LINKED_DEVICE_INACTIVE, shouldShowLinkedDeviceInactiveMegaphone() ? RecurringSchedule.every(TimeUnit.DAYS.toMillis(3)): NEVER);
      put(Event.PIN_REMINDER, new SignalPinReminderSchedule());
      put(Event.SET_UP_YOUR_USERNAME, shouldShowSetUpYourUsernameMegaphone(records) ? ALWAYS : NEVER);

      // Feature-introduction megaphones should *probably* be added below this divider
      put(Event.ADD_A_PROFILE_PHOTO, shouldShowAddAProfilePhotoMegaphone(context) ? ALWAYS : NEVER);
      put(Event.PNP_LAUNCH, shouldShowPnpLaunchMegaphone() ? ALWAYS : NEVER);
      put(Event.TURN_ON_SIGNAL_BACKUPS, shouldShowTurnOnBackupsMegaphone(context) ? new RecurringSchedule(TimeUnit.DAYS.toMillis(30), TimeUnit.DAYS.toMillis(90)) : NEVER);
      put(Event.VERIFY_BACKUP_KEY, new VerifyBackupKeyReminderSchedule());
    }};
  }

  private static boolean shouldShowLinkedDeviceInactiveMegaphone() {
    LeastActiveLinkedDevice device = SignalStore.misc().getLeastActiveLinkedDevice();
    if (device == null) {
      return false;
    }

    long expiringAt = device.lastActiveTimestamp + RemoteConfig.getLinkedDeviceLifespan();
    long expiringIn = Math.max(expiringAt - System.currentTimeMillis(), 0);

    return expiringIn < TimeUnit.DAYS.toMillis(7) && expiringIn > 0;
  }

  private static @Nullable Megaphone forRecord(@NonNull Context context, @NonNull MegaphoneRecord record) {
    switch (record.getEvent()) {
      case PINS_FOR_ALL:
        return buildPinsForAllMegaphone(record);
      case PIN_REMINDER:
        return buildPinReminderMegaphone(context);
      case CLIENT_DEPRECATED:
        return buildClientDeprecatedMegaphone(context);
      case ONBOARDING:
        return buildOnboardingMegaphone();
      case NOTIFICATIONS:
        return buildNotificationsMegaphone(context);
      case ADD_A_PROFILE_PHOTO:
        return buildAddAProfilePhotoMegaphone(context);
      case ENABLE_APP_UPDATES:
        return buildEnableAppUpdatesMegaphone(context);
      case DONATE_MOLLY:
        return buildDonateQ2Megaphone(context);
      case TURN_OFF_CENSORSHIP_CIRCUMVENTION:
        return buildTurnOffCircumventionMegaphone(context);
      case LINKED_DEVICE_INACTIVE:
        return buildLinkedDeviceInactiveMegaphone(context);
      case REMOTE_MEGAPHONE:
        return buildRemoteMegaphone(context);
      case BACKUP_SCHEDULE_PERMISSION:
        return buildBackupPermissionMegaphone(context);
      case SET_UP_YOUR_USERNAME:
        return buildSetUpYourUsernameMegaphone(context);
      case GRANT_FULL_SCREEN_INTENT:
        return buildGrantFullScreenIntentPermission(context);
      case PNP_LAUNCH:
        return buildPnpLaunchMegaphone();
      case NEW_LINKED_DEVICE:
        return buildNewLinkedDeviceMegaphone(context);
      case TURN_ON_SIGNAL_BACKUPS:
        return buildTurnOnSignalBackupsMegaphone();
      case VERIFY_BACKUP_KEY:
        return buildVerifyBackupKeyMegaphone();
      default:
        throw new IllegalArgumentException("Event not handled!");
    }
  }

  private static Megaphone buildLinkedDeviceInactiveMegaphone(Context context) {
    LeastActiveLinkedDevice device = SignalStore.misc().getLeastActiveLinkedDevice();
    if (device == null) {
      throw new IllegalStateException("No linked device to show");
    }

    long expiringAt   = device.lastActiveTimestamp + RemoteConfig.getLinkedDeviceLifespan();
    long expiringIn   = Math.max(expiringAt - System.currentTimeMillis(), 0);
    int  expiringDays = (int) TimeUnit.MILLISECONDS.toDays(expiringIn);

    return new Megaphone.Builder(Event.LINKED_DEVICE_INACTIVE, Megaphone.Style.BASIC)
        .setTitle(R.string.LinkedDeviceInactiveMegaphone_title)
        .setBody(context.getResources().getQuantityString(R.plurals.LinkedDeviceInactiveMegaphone_body, expiringDays, device.name, expiringDays))
        .setImage(R.drawable.symbol_linked_device)
        .setActionButton(R.string.LinkedDeviceInactiveMegaphone_got_it_button_label, (megaphone, listener) -> {
          listener.onMegaphoneSnooze(Event.LINKED_DEVICE_INACTIVE);
        })
        .setSecondaryButton(R.string.LinkedDeviceInactiveMegaphone_dont_remind_button_label, (megaphone, listener) -> {
          listener.onMegaphoneCompleted(Event.LINKED_DEVICE_INACTIVE);
        })
        .build();
  }

  private static @NonNull Megaphone buildPinsForAllMegaphone(@NonNull MegaphoneRecord record) {
    if (PinsForAllSchedule.shouldDisplayFullScreen(record.getFirstVisible(), System.currentTimeMillis())) {
      return new Megaphone.Builder(Event.PINS_FOR_ALL, Megaphone.Style.FULLSCREEN)
          .enableSnooze(null)
          .setOnVisibleListener((megaphone, listener) -> {
            if (new NetworkConstraint.Factory(AppDependencies.getApplication()).create().isMet()) {
              listener.onMegaphoneNavigationRequested(SvrMigrationActivity.createIntent(), SvrMigrationActivity.REQUEST_NEW_PIN);
            }
          })
          .build();
    } else {
      return new Megaphone.Builder(Event.PINS_FOR_ALL, Megaphone.Style.BASIC)
          .setImage(R.drawable.kbs_pin_megaphone)
          .setTitle(R.string.KbsMegaphone__create_a_pin)
          .setBody(R.string.KbsMegaphone__pins_keep_information_thats_stored_with_signal_encrytped)
          .setActionButton(R.string.KbsMegaphone__create_pin, (megaphone, listener) -> {
            Intent intent = CreateSvrPinActivity.getIntentForPinCreate(AppDependencies.getApplication());

            listener.onMegaphoneNavigationRequested(intent, CreateSvrPinActivity.REQUEST_NEW_PIN);
          })
          .build();
    }
  }

  @SuppressWarnings("CodeBlock2Expr")
  private static @NonNull Megaphone buildPinReminderMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.PIN_REMINDER, Megaphone.Style.BASIC)
        .setTitle(R.string.Megaphones_verify_your_signal_pin)
        .setBody(R.string.Megaphones_well_occasionally_ask_you_to_verify_your_pin)
        .setImage(R.drawable.kbs_pin_megaphone)
        .setActionButton(R.string.Megaphones_verify_pin, (megaphone, controller) -> {
          SignalPinReminderDialog.show(controller.getMegaphoneActivity(), controller::onMegaphoneNavigationRequested, new SignalPinReminderDialog.Callback() {
            @Override
            public void onReminderDismissed(boolean includedFailure) {
              Log.i(TAG, "[PinReminder] onReminderDismissed(" + includedFailure + ")");

              SignalStore.pin().onEntrySkip(includedFailure);
              controller.onMegaphoneSnooze(Event.PIN_REMINDER);
              controller.onMegaphoneToastRequested(controller.getMegaphoneActivity().getString(SignalPinReminders.getSkipReminderString(SignalStore.pin().getCurrentInterval())));
            }

            @Override
            public void onReminderCompleted(@NonNull String pin, boolean includedFailure) {
              Log.i(TAG, "[PinReminder] onReminderCompleted(" + includedFailure + ")");
              if (includedFailure) {
                SignalStore.pin().onEntrySuccessWithWrongGuess(pin);
              } else {
                SignalStore.pin().onEntrySuccess(pin);
              }

              controller.onMegaphoneSnooze(Event.PIN_REMINDER);
              controller.onMegaphoneToastRequested(controller.getMegaphoneActivity().getString(SignalPinReminders.getReminderString(SignalStore.pin().getCurrentInterval())));
            }
          });
        })
        .build();
  }

  private static @NonNull Megaphone buildClientDeprecatedMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.CLIENT_DEPRECATED, Megaphone.Style.FULLSCREEN)
        .disableSnooze()
        .setOnVisibleListener((megaphone, listener) -> listener.onMegaphoneNavigationRequested(new Intent(context, ClientDeprecatedActivity.class)))
        .build();
  }

  private static @NonNull Megaphone buildOnboardingMegaphone() {
    return new Megaphone.Builder(Event.ONBOARDING, Megaphone.Style.ONBOARDING)
        .build();
  }

  private static @NonNull Megaphone buildNotificationsMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.NOTIFICATIONS, Megaphone.Style.BASIC)
        .setTitle(R.string.NotificationsMegaphone_turn_on_notifications)
        .setBody(R.string.NotificationsMegaphone_never_miss_a_message)
        .setImage(R.drawable.megaphone_notifications_64)
        .setActionButton(R.string.NotificationsMegaphone_turn_on, (megaphone, controller) -> {
          if (Build.VERSION.SDK_INT >= 26) {
            controller.onMegaphoneDialogFragmentRequested(TurnOnNotificationsBottomSheet.turnOnSystemNotificationsFragment(context));
          } else {
            controller.onMegaphoneNavigationRequested(AppSettingsActivity.notifications(context));
          }
        })
        .setSecondaryButton(R.string.NotificationsMegaphone_not_now, (megaphone, controller) -> controller.onMegaphoneSnooze(Event.NOTIFICATIONS))
        .build();
  }

  private static @NonNull Megaphone buildAddAProfilePhotoMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.ADD_A_PROFILE_PHOTO, Megaphone.Style.BASIC)
        .setTitle(R.string.AddAProfilePhotoMegaphone__add_a_profile_photo)
        .setImage(R.drawable.ic_add_a_profile_megaphone_image)
        .setBody(R.string.AddAProfilePhotoMegaphone__choose_a_look_and_color)
        .setActionButton(R.string.AddAProfilePhotoMegaphone__add_photo, (megaphone, listener) -> {
          listener.onMegaphoneNavigationRequested(EditProfileActivity.getIntentForAvatarEdit(context));
          listener.onMegaphoneCompleted(Event.ADD_A_PROFILE_PHOTO);
        })
        .setSecondaryButton(R.string.AddAProfilePhotoMegaphone__not_now, (megaphone, listener) -> {
          listener.onMegaphoneCompleted(Event.ADD_A_PROFILE_PHOTO);
        })
        .build();
  }

  private static @NonNull Megaphone buildEnableAppUpdatesMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.ENABLE_APP_UPDATES, Megaphone.Style.BASIC)
        .setTitle(R.string.EnableAppUpdatesMegaphone_check_for_updates_automatically)
        .setImage(R.drawable.ic_avatar_celebration)
        .setBody(R.string.EnableAppUpdatesMegaphone_molly_can_periodically_check_for_new_releases_and_ask_you_to_install_them)
        .setActionButton(R.string.EnableAppUpdatesMegaphone_check_for_updates, (megaphone, listener) -> {
          TextSecurePreferences.setUpdateApkEnabled(context, true);
          ApkUpdateRefreshListener.scheduleIfAllowed(context);
          listener.onMegaphoneCompleted(Event.ENABLE_APP_UPDATES);
          listener.onMegaphoneToastRequested(context.getString(R.string.EnableAppUpdatesMegaphone_you_will_be_notified_when_updates_are_available));
        })
        .setSecondaryButton(R.string.CensorshipCircumventionMegaphone_no_thanks, (megaphone, listener) -> {
          listener.onMegaphoneCompleted(Event.ENABLE_APP_UPDATES);
        })
        .build();
  }

  private static @NonNull Megaphone buildDonateQ2Megaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.DONATE_MOLLY, Megaphone.Style.BASIC)
        .setTitle(R.string.DonateMegaphone_molly_is_free_software)
        .setBody(R.string.DonateMegaphone_we_maintain_molly_with_your_support_consider_donating_at_open_collective)
        .setImage(R.drawable.ic_donate_megaphone)
        .setActionButton(R.string.BecomeASustainerMegaphone__donate, (megaphone, listener) -> {
          CommunicationActions.openBrowserLink(listener.getMegaphoneActivity(), context.getString(R.string.donate_url));
          listener.onMegaphoneCompleted(megaphone.getEvent());
        })
        .setSecondaryButton(R.string.RatingManager_no_thanks, (megaphone, controller) -> controller.onMegaphoneCompleted(megaphone.getEvent()))
        .build();
  }

  private static @NonNull Megaphone buildNewLinkedDeviceMegaphone(@NonNull Context context) {
    String createdAt = DateUtils.getDateTimeString(context, Locale.getDefault(), SignalStore.misc().getNewLinkedDeviceCreatedTime());
    return new Megaphone.Builder(Event.NEW_LINKED_DEVICE, Megaphone.Style.BASIC)
        .setTitle(R.string.NewLinkedDeviceNotification__you_linked_new_device)
        .setBody(context.getString(R.string.NewLinkedDeviceMegaphone__a_new_device_was_linked, createdAt))
        .setImage(R.drawable.symbol_linked_device)
        .setActionButton(R.string.NewLinkedDeviceMegaphone__ok, (megaphone, listener) -> {
          SignalStore.misc().setNewLinkedDeviceId(0);
          SignalStore.misc().setNewLinkedDeviceCreatedTime(0);
          listener.onMegaphoneSnooze(Event.NEW_LINKED_DEVICE);
        })
        .setSecondaryButton(R.string.NewLinkedDeviceMegaphone__view_device, (megaphone, listener) -> {
          listener.onMegaphoneNavigationRequested(AppSettingsActivity.linkedDevices(context));
        })
        .build();
  }

  private static @NonNull Megaphone buildTurnOffCircumventionMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.TURN_OFF_CENSORSHIP_CIRCUMVENTION, Megaphone.Style.BASIC)
        .setTitle(R.string.CensorshipCircumventionMegaphone_turn_off_censorship_circumvention)
        .setImage(R.drawable.ic_censorship_megaphone_64)
        .setBody(R.string.CensorshipCircumventionMegaphone_you_can_now_connect_to_the_signal_service)
        .setActionButton(R.string.CensorshipCircumventionMegaphone_turn_off, (megaphone, listener) -> {
          SignalStore.settings().setCensorshipCircumventionEnabled(false);
          listener.onMegaphoneSnooze(Event.TURN_OFF_CENSORSHIP_CIRCUMVENTION);
        })
        .setSecondaryButton(R.string.CensorshipCircumventionMegaphone_no_thanks, (megaphone, listener) -> {
          listener.onMegaphoneSnooze(Event.TURN_OFF_CENSORSHIP_CIRCUMVENTION);
        })
        .build();
  }

  private static @Nullable Megaphone buildRemoteMegaphone(@NonNull Context context) {
    RemoteMegaphoneRecord record = RemoteMegaphoneRepository.getRemoteMegaphoneToShow(System.currentTimeMillis());

    if (record == null) {
      Log.w(TAG, "No remote megaphone record when told to show one!");
      return null;
    }

    Megaphone.Builder builder = new Megaphone.Builder(Event.REMOTE_MEGAPHONE, Megaphone.Style.BASIC)
        .setTitle(record.getTitle())
        .setBody(record.getBody());

    if (record.getImageUri() != null) {
      builder.setImageRequestBuilder(Glide.with(context).asDrawable().load(record.getImageUri()));
    }

    if (record.hasPrimaryAction()) {
      //noinspection ConstantConditions
      builder.setActionButton(record.getPrimaryActionText(), (megaphone, controller) -> {
        RemoteMegaphoneRepository.getAction(Objects.requireNonNull(record.getPrimaryActionId()))
                                 .run(context, controller, record);
      });
    }

    if (record.hasSecondaryAction()) {
      //noinspection ConstantConditions
      builder.setSecondaryButton(record.getSecondaryActionText(), (megaphone, controller) -> {
        RemoteMegaphoneRepository.getAction(Objects.requireNonNull(record.getSecondaryActionId()))
                                 .run(context, controller, record);
      });
    }

    builder.setOnVisibleListener((megaphone, controller) -> {
      RemoteMegaphoneRepository.markShown(record.getUuid());
    });

    return builder.build();
  }

  @SuppressLint("InlinedApi")
  private static Megaphone buildBackupPermissionMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.BACKUP_SCHEDULE_PERMISSION, Megaphone.Style.BASIC)
        .setTitle(R.string.BackupSchedulePermissionMegaphone__cant_back_up_chats)
        .setImage(R.drawable.ic_cant_backup_megaphone)
        .setBody(R.string.BackupSchedulePermissionMegaphone__your_chats_are_no_longer_being_automatically_backed_up)
        .setActionButton(R.string.BackupSchedulePermissionMegaphone__back_up_chats, (megaphone, controller) -> {
          controller.onMegaphoneDialogFragmentRequested(new ReenableBackupsDialogFragment());
        })
        .setSecondaryButton(R.string.BackupSchedulePermissionMegaphone__not_now, (megaphone, controller) -> {
          controller.onMegaphoneSnooze(Event.BACKUP_SCHEDULE_PERMISSION);
        })
        .build();
  }

  private static boolean shouldShowEnableAppUpdatesMegaphone(@NonNull Context context) {
    return BuildConfig.MANAGES_MOLLY_UPDATES &&
           !TextSecurePreferences.isUpdateApkEnabled(context) &&
           VersionTracker.getDaysSinceFirstInstalled(context) > 0;
  }

  public static @NonNull Megaphone buildSetUpYourUsernameMegaphone(@NonNull Context context) {
    return new Megaphone.Builder(Event.SET_UP_YOUR_USERNAME, Megaphone.Style.BASIC)
        .setTitle(R.string.NewWaysToConnectDialogFragment__new_ways_to_connect)
        .setBody(R.string.SetUpYourUsername__introducing_phone_number_privacy)
        .setImage(R.drawable.usernames_megaphone)
        .setActionButton(R.string.SetUpYourUsername__learn_more, (megaphone, controller) -> {
          controller.onMegaphoneDialogFragmentRequested(new NewWaysToConnectDialogFragment());
        })
        .setSecondaryButton(R.string.SetUpYourUsername__dismiss, (megaphone, controller) -> {
          controller.onMegaphoneCompleted(Event.SET_UP_YOUR_USERNAME);
        })
        .build();
  }

  public static @NonNull Megaphone buildPnpLaunchMegaphone() {
    return new Megaphone.Builder(Event.PNP_LAUNCH, Megaphone.Style.BASIC)
        .setTitle(R.string.PnpLaunchMegaphone_title)
        .setBody(R.string.PnpLaunchMegaphone_body)
        .setImage(R.drawable.usernames_megaphone)
        .setActionButton(R.string.PnpLaunchMegaphone_learn_more, (megaphone, controller) -> {
          controller.onMegaphoneDialogFragmentRequested(new NewWaysToConnectDialogFragment());
          controller.onMegaphoneCompleted(Event.PNP_LAUNCH);

          SignalStore.uiHints().setHasCompletedUsernameOnboarding(true);
          SignalDatabase.recipients().markNeedsSync(Recipient.self().getId());
          StorageSyncHelper.scheduleSyncForDataChange();
        })
        .setSecondaryButton(R.string.PnpLaunchMegaphone_dismiss, (megaphone, controller) -> {
          controller.onMegaphoneCompleted(Event.PNP_LAUNCH);

          SignalStore.uiHints().setHasCompletedUsernameOnboarding(true);
          SignalDatabase.recipients().markNeedsSync(Recipient.self().getId());
          StorageSyncHelper.scheduleSyncForDataChange();
        })
        .build();
  }

  @SuppressLint("NewApi")
  public static @NonNull Megaphone buildGrantFullScreenIntentPermission(@NonNull Context context) {
    return new Megaphone.Builder(Event.GRANT_FULL_SCREEN_INTENT, Megaphone.Style.BASIC)
        .setTitle(R.string.GrantFullScreenIntentPermission_megaphone_title)
        .setBody(R.string.GrantFullScreenIntentPermission_megaphone_body)
        .setImage(R.drawable.calling_64)
        .setActionButton(R.string.GrantFullScreenIntentPermission_megaphone_turn_on, (megaphone, controller) -> {
          controller.onMegaphoneDialogFragmentRequested(TurnOnNotificationsBottomSheet.turnOnFullScreenIntentFragment(context));
        })
        .setSecondaryButton(R.string.GrantFullScreenIntentPermission_megaphone_not_now, (megaphone, controller) -> {
          controller.onMegaphoneCompleted(Event.GRANT_FULL_SCREEN_INTENT);
        })
        .build();
  }

  public static @NonNull Megaphone buildTurnOnSignalBackupsMegaphone() {
    return new Megaphone.Builder(Event.TURN_ON_SIGNAL_BACKUPS, Megaphone.Style.BASIC)
        .setImage(R.drawable.backups_megaphone_image)
        .setTitle(R.string.preferences_chats__backups)
        .setBody(R.string.TurnOnSignalBackups__body)
        .setActionButton(R.string.BackupsSettingsFragment_set_up, (megaphone, controller) -> {
          Intent intent = AppSettingsActivity.backupsSettings(controller.getMegaphoneActivity());

          controller.onMegaphoneNavigationRequested(intent);
          controller.onMegaphoneSnooze(Event.TURN_ON_SIGNAL_BACKUPS);
        })
        .setSecondaryButton(R.string.TurnOnSignalBackups__not_now, (megaphone, controller) -> {
          controller.onMegaphoneSnooze(Event.TURN_ON_SIGNAL_BACKUPS);
        })
        .build();
  }

  public static @NonNull Megaphone buildVerifyBackupKeyMegaphone() {
    Megaphone.Builder builder = new Megaphone.Builder(Event.VERIFY_BACKUP_KEY, Megaphone.Style.BASIC)
        .setImage(R.drawable.image_signal_backups_key)
        .setTitle(R.string.VerifyBackupKey__title)
        .setBody(R.string.VerifyBackupKey__body)
        .setActionButton(R.string.VerifyBackupKey__verify, (megaphone, controller) -> {
          Intent intent = VerifyBackupKeyActivity.createIntent(controller.getMegaphoneActivity());

          controller.onMegaphoneNavigationRequested(intent, VerifyBackupKeyActivity.REQUEST_CODE);
        });

    if (!SignalStore.backup().getHasSnoozedVerified()) {
      builder.setSecondaryButton(R.string.VerifyBackupKey__not_now, (megaphone, controller) -> {
        SignalStore.backup().setHasSnoozedVerified(true);
        controller.onMegaphoneToastRequested(controller.getMegaphoneActivity().getString(R.string.VerifyBackupKey__we_will_ask_again));
        controller.onMegaphoneSnooze(Event.VERIFY_BACKUP_KEY);
      });
    }

    return builder.build();
  }

  private static boolean shouldShowDonateMegaphone(@NonNull Context context, @NonNull Event event, @NonNull Map<Event, MegaphoneRecord> records) {
    long timeSinceLastDonatePrompt = timeSinceLastDonatePrompt(event, records);

    return timeSinceLastDonatePrompt > MIN_TIME_BETWEEN_DONATE_MEGAPHONES &&
           VersionTracker.getDaysSinceFirstInstalled(context) >= 7;
  }

  private static boolean shouldShowOnboardingMegaphone(@NonNull Context context) {
    return SignalStore.onboarding().hasOnboarding(context);
  }

  private static boolean shouldShowNewLinkedDeviceMegaphone() {
    return SignalStore.misc().getNewLinkedDeviceId() > 0 && !NotificationChannels.getInstance().areNotificationsEnabled();
  }

  private static boolean shouldShowTurnOffCircumventionMegaphone() {
    return AppDependencies.getSignalServiceNetworkAccess().isCensored() &&
           SignalStore.misc().isServiceReachableWithoutCircumvention();
  }

  private static boolean shouldShowNotificationsMegaphone(@NonNull Context context) {
    boolean shouldShow = !SignalStore.settings().isMessageNotificationsEnabled() ||
                         !NotificationChannels.getInstance().isMessageChannelEnabled() ||
                         !NotificationChannels.getInstance().isMessagesChannelGroupEnabled() ||
                         !NotificationChannels.getInstance().areNotificationsEnabled();
    if (shouldShow) {
      Locale locale = DynamicLanguageContextWrapper.getUsersSelectedLocale(context);
      if (!new TranslationDetection(context, locale)
          .textExistsInUsersLanguage(R.string.NotificationsMegaphone_turn_on_notifications,
                                     R.string.NotificationsMegaphone_never_miss_a_message,
                                     R.string.NotificationsMegaphone_turn_on,
                                     R.string.NotificationsMegaphone_not_now))
      {
        Log.i(TAG, "Would show NotificationsMegaphone but is not yet translated in " + locale);
        return false;
      }
    }
    return shouldShow;
  }

  private static boolean shouldShowAddAProfilePhotoMegaphone(@NonNull Context context) {
    if (SignalStore.misc().getHasEverHadAnAvatar()) {
      return false;
    }

    boolean hasAnAvatar = AvatarHelper.hasAvatar(context, Recipient.self().getId());
    if (hasAnAvatar) {
      SignalStore.misc().setHasEverHadAnAvatar(true);
      return false;
    }

    return true;
  }

  /**
   * Prompt megaphone 3 days after turning off phone number discovery when no username is set.
   */
  private static boolean shouldShowSetUpYourUsernameMegaphone(@NonNull Map<Event, MegaphoneRecord> records) {
    boolean                        hasUsername                    = SignalStore.account().isRegistered() && SignalStore.account().getUsername() != null;
    boolean                        hasCompleted                   = MapUtil.mapOrDefault(records, Event.SET_UP_YOUR_USERNAME, MegaphoneRecord::isFinished, false);
    long                           phoneNumberDiscoveryDisabledAt = SignalStore.phoneNumberPrivacy().getPhoneNumberDiscoverabilityModeTimestamp();
    PhoneNumberDiscoverabilityMode listingMode                    = SignalStore.phoneNumberPrivacy().getPhoneNumberDiscoverabilityMode();

    return !hasUsername &&
           listingMode == PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE &&
           !hasCompleted &&
           phoneNumberDiscoveryDisabledAt > 0 &&
           (System.currentTimeMillis() - phoneNumberDiscoveryDisabledAt) >= TimeUnit.DAYS.toMillis(3);
  }

  private static boolean shouldShowPnpLaunchMegaphone() {
    return TextUtils.isEmpty(SignalStore.account().getUsername()) && !SignalStore.uiHints().hasCompletedUsernameOnboarding();
  }

  private static boolean shouldShowTurnOnBackupsMegaphone(@NonNull Context context) {
    if (!Environment.IS_STAGING) {
      return false;
    }

    if (SignalStore.backup().getLatestBackupTier() != null || SignalStore.settings().isBackupEnabled()) {
      return false;
    }

    if (!SignalStore.account().isRegistered() || TextSecurePreferences.isUnauthorizedReceived(context)) {
      return false;
    }

    return VersionTracker.getDaysSinceFirstInstalled(context) > 7;
  }

  private static boolean shouldShowGrantFullScreenIntentPermission(@NonNull Context context) {
    return Build.VERSION.SDK_INT >= 34 && !NotificationManagerCompat.from(context).canUseFullScreenIntent();
  }

  @WorkerThread
  private static boolean shouldShowRemoteMegaphone(@NonNull Map<Event, MegaphoneRecord> records) {
    boolean canShowLocalDonate = timeSinceLastDonatePrompt(Event.REMOTE_MEGAPHONE, records) > MIN_TIME_BETWEEN_DONATE_MEGAPHONES;
    return RemoteMegaphoneRepository.hasRemoteMegaphoneToShow(canShowLocalDonate);
  }

  private static boolean shouldShowBackupSchedulePermissionMegaphone(@NonNull Context context) {
    return Build.VERSION.SDK_INT >= 31 && SignalStore.settings().isBackupEnabled() && !ServiceUtil.getAlarmManager(context).canScheduleExactAlarms();
  }

  /**
   * Unfortunately lastSeen is only set today upon snoozing, which never happens to donate prompts.
   * So we use firstVisible as a proxy.
   */
  private static long timeSinceLastDonatePrompt(@NonNull Event excludeEvent, @NonNull Map<Event, MegaphoneRecord> records) {
    long lastSeenDonatePrompt = records.entrySet()
                                       .stream()
                                       .filter(e -> DONATE_EVENTS.contains(e.getKey()))
                                       .filter(e -> !e.getKey().equals(excludeEvent))
                                       .map(e -> e.getValue().getFirstVisible())
                                       .filter(t -> t > 0)
                                       .sorted()
                                       .findFirst()
                                       .orElse(0L);
    return System.currentTimeMillis() - lastSeenDonatePrompt;
  }


  public enum Event {
    PINS_FOR_ALL("pins_for_all"),
    PIN_REMINDER("pin_reminder"),
    CLIENT_DEPRECATED("client_deprecated"),
    ONBOARDING("onboarding"),
    NOTIFICATIONS("notifications"),
    ADD_A_PROFILE_PHOTO("add_a_profile_photo"),
    ENABLE_APP_UPDATES("enable_app_updates_molly"),
    DONATE_MOLLY("donate_molly"),
    TURN_OFF_CENSORSHIP_CIRCUMVENTION("turn_off_censorship_circumvention"),
    REMOTE_MEGAPHONE("remote_megaphone"),
    LINKED_DEVICE_INACTIVE("linked_device_inactive"),
    BACKUP_SCHEDULE_PERMISSION("backup_schedule_permission"),
    SET_UP_YOUR_USERNAME("set_up_your_username"),
    PNP_LAUNCH("pnp_launch"),
    GRANT_FULL_SCREEN_INTENT("grant_full_screen_intent"),
    NEW_LINKED_DEVICE("new_linked_device"),
    TURN_ON_SIGNAL_BACKUPS("turn_on_signal_backups"),
    VERIFY_BACKUP_KEY("verify_backup_key");

    private final String key;

    Event(@NonNull String key) {
      this.key = key;
    }

    public @NonNull String getKey() {
      return key;
    }

    public static Event fromKey(@NonNull String key) {
      for (Event event : values()) {
        if (event.getKey().equals(key)) {
          return event;
        }
      }
      throw new IllegalArgumentException("No event for key: " + key);
    }

    public static boolean hasKey(@NonNull String key) {
      for (Event event : values()) {
        if (event.getKey().equals(key)) {
          return true;
        }
      }
      return false;
    }
  }
}
