package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;

import com.google.android.material.snackbar.Snackbar;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.ChangePassphraseDialogFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.blocked.BlockedUsersActivity;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.keyvalue.KbsValues;
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues;
import org.thoughtcrime.securesms.keyvalue.PinValues;
import org.thoughtcrime.securesms.keyvalue.SettingsValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.CreateKbsPinActivity;
import org.thoughtcrime.securesms.lock.v2.RegistrationLockUtil;
import org.thoughtcrime.securesms.megaphone.Megaphones;
import org.thoughtcrime.securesms.pin.RegistrationLockV2Dialog;
import org.thoughtcrime.securesms.preferences.widgets.PassphraseLockTriggerPreference;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;


import mobi.upod.timedurationpicker.TimeDurationPickerDialog;

public class AppProtectionPreferenceFragment extends CorrectedPreferenceFragment {

  private static final String TAG = Log.tag(AppProtectionPreferenceFragment.class);

  private static final String PREFERENCE_CATEGORY_BLOCKED             = "preference_category_blocked";
  private static final String PREFERENCE_UNIDENTIFIED_LEARN_MORE      = "pref_unidentified_learn_more";
  private static final String PREFERENCE_WHO_CAN_SEE_PHONE_NUMBER     = "pref_who_can_see_phone_number";
  private static final String PREFERENCE_WHO_CAN_FIND_BY_PHONE_NUMBER = "pref_who_can_find_by_phone_number";

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    this.findPreference(TextSecurePreferences.PASSPHRASE_LOCK).setOnPreferenceChangeListener(new PassphraseLockListener());
    this.findPreference(TextSecurePreferences.PASSPHRASE_LOCK_TRIGGER).setOnPreferenceChangeListener(new PassphraseLockTriggerChangeListener());
    this.findPreference(TextSecurePreferences.PASSPHRASE_LOCK_TIMEOUT).setOnPreferenceClickListener(new PassphraseLockTimeoutListener());

    this.findPreference(KbsValues.V2_LOCK_ENABLED).setPreferenceDataStore(SignalStore.getPreferenceDataStore());
    ((SwitchPreferenceCompat) this.findPreference(KbsValues.V2_LOCK_ENABLED)).setChecked(SignalStore.kbsValues().isV2RegistrationLockEnabled());
    this.findPreference(KbsValues.V2_LOCK_ENABLED).setOnPreferenceChangeListener(new RegistrationLockV2ChangedListener());

    this.findPreference(PinValues.PIN_REMINDERS_ENABLED).setPreferenceDataStore(SignalStore.getPreferenceDataStore());
    ((SwitchPreferenceCompat) this.findPreference(PinValues.PIN_REMINDERS_ENABLED)).setChecked(SignalStore.pinValues().arePinRemindersEnabled());

    this.findPreference(TextSecurePreferences.CHANGE_PASSPHRASE_PREF).setOnPreferenceClickListener(new ChangePassphraseClickListener());
    this.findPreference(TextSecurePreferences.READ_RECEIPTS_PREF).setOnPreferenceChangeListener(new ReadReceiptToggleListener());
    this.findPreference(TextSecurePreferences.TYPING_INDICATORS).setOnPreferenceChangeListener(new TypingIndicatorsToggleListener());
    this.findPreference(PREFERENCE_CATEGORY_BLOCKED).setOnPreferenceClickListener(new BlockedContactsClickListener());
    this.findPreference(TextSecurePreferences.SHOW_UNIDENTIFIED_DELIVERY_INDICATORS).setOnPreferenceChangeListener(new ShowUnidentifiedDeliveryIndicatorsChangedListener());
    this.findPreference(TextSecurePreferences.UNIVERSAL_UNIDENTIFIED_ACCESS).setOnPreferenceChangeListener(new UniversalUnidentifiedAccessChangedListener());
    this.findPreference(PREFERENCE_UNIDENTIFIED_LEARN_MORE).setOnPreferenceClickListener(new UnidentifiedLearnMoreClickListener());

    if (FeatureFlags.phoneNumberPrivacy()) {
      Preference whoCanSeePhoneNumber    = this.findPreference(PREFERENCE_WHO_CAN_SEE_PHONE_NUMBER);
      Preference whoCanFindByPhoneNumber = this.findPreference(PREFERENCE_WHO_CAN_FIND_BY_PHONE_NUMBER);

      whoCanSeePhoneNumber.setPreferenceDataStore(null);
      whoCanSeePhoneNumber.setOnPreferenceClickListener(new PhoneNumberPrivacyWhoCanSeeClickListener());

      whoCanFindByPhoneNumber.setPreferenceDataStore(null);
      whoCanFindByPhoneNumber.setOnPreferenceClickListener(new PhoneNumberPrivacyWhoCanFindClickListener());
    } else {
      this.findPreference("category_phone_number_privacy").setVisible(false);
    }

    SwitchPreferenceCompat linkPreviewPref = (SwitchPreferenceCompat) this.findPreference(SettingsValues.LINK_PREVIEWS);
    linkPreviewPref.setChecked(SignalStore.settings().isLinkPreviewsEnabled());
    linkPreviewPref.setPreferenceDataStore(SignalStore.getPreferenceDataStore());
    linkPreviewPref.setOnPreferenceChangeListener(new LinkPreviewToggleListener());

    initializeVisibility();
  }

  @Override
  public void onCreateEncryptedPreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_app_protection);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__privacy);

    initializePassphraseLockTriggerSummary();
    initializePassphraseTimeoutSummary();

    Preference             signalPinCreateChange   = this.findPreference(TextSecurePreferences.SIGNAL_PIN_CHANGE);
    SwitchPreferenceCompat signalPinReminders      = (SwitchPreferenceCompat) this.findPreference(PinValues.PIN_REMINDERS_ENABLED);
    SwitchPreferenceCompat registrationLockV2      = (SwitchPreferenceCompat) this.findPreference(KbsValues.V2_LOCK_ENABLED);

    if (SignalStore.kbsValues().hasPin() && !SignalStore.kbsValues().hasOptedOut()) {
      signalPinCreateChange.setOnPreferenceClickListener(new KbsPinUpdateListener());
      signalPinCreateChange.setTitle(R.string.preferences_app_protection__change_your_pin);
      signalPinReminders.setEnabled(true);
      registrationLockV2.setEnabled(true);
    } else {
      signalPinCreateChange.setOnPreferenceClickListener(new KbsPinCreateListener());
      signalPinCreateChange.setTitle(R.string.preferences_app_protection__create_a_pin);
      signalPinReminders.setEnabled(false);
      registrationLockV2.setEnabled(false);
    }

    initializePhoneNumberPrivacyWhoCanSeeSummary();
    initializePhoneNumberPrivacyWhoCanFindSummary();
  }

  private void initializePassphraseLockTriggerSummary() {
    findPreference(TextSecurePreferences.PASSPHRASE_LOCK_TRIGGER)
            .setSummary(getSummaryForPassphraseLockTrigger(TextSecurePreferences.getPassphraseLockTrigger(getContext()).getTriggers()));
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == CreateKbsPinActivity.REQUEST_NEW_PIN && resultCode == CreateKbsPinActivity.RESULT_OK) {
      Snackbar.make(requireView(), R.string.ConfirmKbsPinFragment__pin_created, Snackbar.LENGTH_LONG).setTextColor(Color.WHITE).show();
    }
  }

  private void initializePassphraseTimeoutSummary() {
    findPreference(TextSecurePreferences.PASSPHRASE_LOCK_TIMEOUT)
            .setSummary(getLockTimeoutSummary(TextSecurePreferences.getPassphraseLockTimeout(getContext())));
  }

  private String getLockTimeoutSummary(long timeoutSeconds) {
    if (timeoutSeconds <= 0) return getString(R.string.AppProtectionPreferenceFragment_instant);

    long hours   = TimeUnit.SECONDS.toHours(timeoutSeconds);
    long minutes = TimeUnit.SECONDS.toMinutes(timeoutSeconds) - (TimeUnit.SECONDS.toHours(timeoutSeconds) * 60  );
    long seconds = TimeUnit.SECONDS.toSeconds(timeoutSeconds) - (TimeUnit.SECONDS.toMinutes(timeoutSeconds) * 60);

    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
  }

  private void initializePhoneNumberPrivacyWhoCanSeeSummary() {
    Preference preference = findPreference(PREFERENCE_WHO_CAN_SEE_PHONE_NUMBER);

    switch (SignalStore.phoneNumberPrivacy().getPhoneNumberSharingMode()) {
      case EVERYONE: preference.setSummary(R.string.PhoneNumberPrivacy_everyone);    break;
      case CONTACTS: preference.setSummary(R.string.PhoneNumberPrivacy_my_contacts); break;
      case NOBODY  : preference.setSummary(R.string.PhoneNumberPrivacy_nobody);      break;
      default      : throw new AssertionError();
    }
  }

  private void initializePhoneNumberPrivacyWhoCanFindSummary() {
    Preference preference = findPreference(PREFERENCE_WHO_CAN_FIND_BY_PHONE_NUMBER);

    switch (SignalStore.phoneNumberPrivacy().getPhoneNumberListingMode()) {
      case LISTED  : preference.setSummary(R.string.PhoneNumberPrivacy_everyone); break;
      case UNLISTED: preference.setSummary(R.string.PhoneNumberPrivacy_nobody);   break;
      default      : throw new AssertionError();
    }
  }

  private void initializeVisibility() {
    findPreference(TextSecurePreferences.PASSPHRASE_LOCK_TIMEOUT)
            .setEnabled(TextSecurePreferences.getPassphraseLockTrigger(requireContext()).isTimeoutEnabled());
  }

  private CharSequence getSummaryForPassphraseLockTrigger(Set<String> triggers) {
    String[]     keys      = getResources().getStringArray(R.array.pref_passphrase_lock_trigger_entries);
    String[]     values    = getResources().getStringArray(R.array.pref_passphrase_lock_trigger_values);
    List<String> outValues = new ArrayList<>(triggers.size());

    for (int i=0; i < keys.length; i++) {
      if (triggers.contains(keys[i])) outValues.add(values[i]);
    }

    return outValues.isEmpty() ? getResources().getString(R.string.preferences__none)
            : TextUtils.join(". ", outValues);
  }

  private class PassphraseLockListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      boolean enabled = (Boolean)newValue;

      int mode = enabled ? ChangePassphraseDialogFragment.MODE_ENABLE : ChangePassphraseDialogFragment.MODE_DISABLE;

      ChangePassphraseDialogFragment dialog = ChangePassphraseDialogFragment.newInstance(mode);

      dialog.setMasterSecretChangedListener(masterSecret -> {
        ((SwitchPreferenceCompat) preference).setChecked(enabled);
        ((ApplicationPreferencesActivity) requireContext()).setMasterSecret(masterSecret);
      });
      dialog.show(requireFragmentManager(), "ChangePassphraseDialogFragment");

      return false;
    }
  }

  private class PassphraseLockTimeoutListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      new TimeDurationPickerDialog(requireContext(), (view, duration) -> {
        long timeoutSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
        TextSecurePreferences.setPassphraseLockTimeout(requireContext(), timeoutSeconds);
        preference.setSummary(getLockTimeoutSummary(timeoutSeconds));
      }, 0).show();

      return true;
    }
  }

  private class ChangePassphraseClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      if (TextSecurePreferences.isPassphraseLockEnabled(getContext())) {
        ChangePassphraseDialogFragment dialog = ChangePassphraseDialogFragment.newInstance();

        dialog.setMasterSecretChangedListener(masterSecret -> {
          Toast.makeText(getActivity(),
                         R.string.preferences__passphrase_changed,
                         Toast.LENGTH_LONG).show();
          masterSecret.close();
        });
        dialog.show(requireFragmentManager(), "ChangePassphraseDialogFragment");
      } else {
        Toast.makeText(getActivity(),
                       R.string.ApplicationPreferenceActivity_you_havent_set_a_passphrase_yet,
                       Toast.LENGTH_LONG).show();
      }

      return true;
    }
  }

  private class KbsPinUpdateListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      startActivityForResult(CreateKbsPinActivity.getIntentForPinChangeFromSettings(requireContext()), CreateKbsPinActivity.REQUEST_NEW_PIN);
      return true;
    }
  }

  private class PassphraseLockTriggerChangeListener implements Preference.OnPreferenceChangeListener {
    @SuppressWarnings("unchecked")
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      PassphraseLockTriggerPreference trigger = new PassphraseLockTriggerPreference((Set<String>)newValue);

      preference.setSummary(getSummaryForPassphraseLockTrigger(trigger.getTriggers()));
      findPreference(TextSecurePreferences.PASSPHRASE_LOCK_TIMEOUT).setEnabled(trigger.isTimeoutEnabled());

      return true;
    }
  }
  private class KbsPinCreateListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      startActivityForResult(CreateKbsPinActivity.getIntentForPinCreate(requireContext()), CreateKbsPinActivity.REQUEST_NEW_PIN);
      return true;
    }
  }

  private class BlockedContactsClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      Intent intent = new Intent(getActivity(), BlockedUsersActivity.class);
      startActivity(intent);
      return true;
    }
  }

  private class ReadReceiptToggleListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      SignalExecutors.BOUNDED.execute(() -> {
        boolean enabled = (boolean)newValue;
        DatabaseFactory.getRecipientDatabase(getContext()).markNeedsSync(Recipient.self().getId());
        StorageSyncHelper.scheduleSyncForDataChange();
        ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(enabled,
                                                                                          TextSecurePreferences.isTypingIndicatorsEnabled(requireContext()),
                                                                                          TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(getContext()),
                                                                                          SignalStore.settings().isLinkPreviewsEnabled()));

      });
      return true;
    }
  }

  private class TypingIndicatorsToggleListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      SignalExecutors.BOUNDED.execute(() -> {
        boolean enabled = (boolean)newValue;
        DatabaseFactory.getRecipientDatabase(getContext()).markNeedsSync(Recipient.self().getId());
        StorageSyncHelper.scheduleSyncForDataChange();
        ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(requireContext()),
                                                                                          enabled,
                                                                                          TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(getContext()),
                                                                                          SignalStore.settings().isLinkPreviewsEnabled()));

        if (!enabled) {
          ApplicationDependencies.getTypingStatusRepository().clear();
        }
      });
      return true;
    }
  }

  private class LinkPreviewToggleListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      SignalExecutors.BOUNDED.execute(() -> {
        boolean enabled = (boolean)newValue;
        DatabaseFactory.getRecipientDatabase(getContext()).markNeedsSync(Recipient.self().getId());
        StorageSyncHelper.scheduleSyncForDataChange();
        ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(requireContext()),
                                                                                          TextSecurePreferences.isTypingIndicatorsEnabled(requireContext()),
                                                                                          TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(requireContext()),
                                                                                          enabled));
        if (enabled) {
          ApplicationDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.LINK_PREVIEWS);
        }
      });
      return true;
    }
  }

  public static CharSequence getSummary(Context context) {
    final   int    privacySummaryResId = R.string.ApplicationPreferencesActivity_privacy_summary_passphrase_registration_locks;
    final   String onRes               = context.getString(R.string.ApplicationPreferencesActivity_on);
    final   String offRes              = context.getString(R.string.ApplicationPreferencesActivity_off);
    boolean registrationLockEnabled    = RegistrationLockUtil.userHasRegistrationLock(context);

    if (!TextSecurePreferences.isPassphraseLockEnabled(context)) {
      if (registrationLockEnabled) {
        return context.getString(privacySummaryResId, offRes, onRes);
      } else {
        return context.getString(privacySummaryResId, offRes, offRes);
      }
    } else {
      if (registrationLockEnabled) {
        return context.getString(privacySummaryResId, onRes, onRes);
      } else {
        return context.getString(privacySummaryResId, onRes, offRes);
      }
    }
  }

  private class ShowUnidentifiedDeliveryIndicatorsChangedListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      boolean enabled = (boolean) newValue;
      SignalExecutors.BOUNDED.execute(() -> {
        DatabaseFactory.getRecipientDatabase(getContext()).markNeedsSync(Recipient.self().getId());
        StorageSyncHelper.scheduleSyncForDataChange();
        ApplicationDependencies.getJobManager().add(new MultiDeviceConfigurationUpdateJob(TextSecurePreferences.isReadReceiptsEnabled(getContext()),
                                                                                          TextSecurePreferences.isTypingIndicatorsEnabled(getContext()),
                                                                                          enabled,
                                                                                          SignalStore.settings().isLinkPreviewsEnabled()));
      });

      return true;
    }
  }

  private class UniversalUnidentifiedAccessChangedListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
      ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
      return true;
    }
  }

  private class UnidentifiedLearnMoreClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      CommunicationActions.openBrowserLink(preference.getContext(), "https://signal.org/blog/sealed-sender/");
      return true;
    }
  }

  private class RegistrationLockV2ChangedListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      boolean     value   = (boolean) newValue;

      Log.i(TAG, "Getting ready to change registration lock setting to: " + value);

      if (value) {
        RegistrationLockV2Dialog.showEnableDialog(requireContext(), () -> ((CheckBoxPreference) preference).setChecked(true));
      } else {
        RegistrationLockV2Dialog.showDisableDialog(requireContext(), () -> ((CheckBoxPreference) preference).setChecked(false));
      }

      return false;
    }
  }

  private final class PhoneNumberPrivacyWhoCanSeeClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      PhoneNumberPrivacyValues phoneNumberPrivacyValues = SignalStore.phoneNumberPrivacy();

      final PhoneNumberPrivacyValues.PhoneNumberSharingMode[] value = { phoneNumberPrivacyValues.getPhoneNumberSharingMode() };

      Map<PhoneNumberPrivacyValues.PhoneNumberSharingMode, CharSequence> items        = items(requireContext());
      List<PhoneNumberPrivacyValues.PhoneNumberSharingMode>              modes        = new ArrayList<>(items.keySet());
      CharSequence[]                                                     modeStrings  = items.values().toArray(new CharSequence[0]);
      int                                                                selectedMode = modes.indexOf(value[0]);

      new AlertDialog.Builder(requireActivity())
                     .setTitle(R.string.preferences_app_protection__see_my_phone_number)
                     .setCancelable(true)
                     .setSingleChoiceItems(modeStrings, selectedMode, (dialog, which) -> value[0] = modes.get(which))
                     .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                       PhoneNumberPrivacyValues.PhoneNumberSharingMode phoneNumberSharingMode = value[0];
                       phoneNumberPrivacyValues.setPhoneNumberSharingMode(phoneNumberSharingMode);
                       Log.i(TAG, String.format("PhoneNumberSharingMode changed to %s. Scheduling storage value sync", phoneNumberSharingMode));
                       StorageSyncHelper.scheduleSyncForDataChange();
                       initializePhoneNumberPrivacyWhoCanSeeSummary();
                     })
                     .setNegativeButton(android.R.string.cancel, null)
                     .show();

      return true;
    }

    private Map<PhoneNumberPrivacyValues.PhoneNumberSharingMode, CharSequence> items(Context context) {
      Map<PhoneNumberPrivacyValues.PhoneNumberSharingMode, CharSequence> map = new LinkedHashMap<>();

      map.put(PhoneNumberPrivacyValues.PhoneNumberSharingMode.EVERYONE, titleAndDescription(context, context.getString(R.string.PhoneNumberPrivacy_everyone), context.getString(R.string.PhoneNumberPrivacy_everyone_see_description)));
      map.put(PhoneNumberPrivacyValues.PhoneNumberSharingMode.NOBODY, context.getString(R.string.PhoneNumberPrivacy_nobody));

      return map;
    }
  }

  private final class PhoneNumberPrivacyWhoCanFindClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      PhoneNumberPrivacyValues phoneNumberPrivacyValues = SignalStore.phoneNumberPrivacy();

      final PhoneNumberPrivacyValues.PhoneNumberListingMode[] value = { phoneNumberPrivacyValues.getPhoneNumberListingMode() };

      new AlertDialog.Builder(requireActivity())
                     .setTitle(R.string.preferences_app_protection__find_me_by_phone_number)
                     .setCancelable(true)
                     .setSingleChoiceItems(items(requireContext()),
                                           value[0].ordinal(),
                                           (dialog, which) -> value[0] = PhoneNumberPrivacyValues.PhoneNumberListingMode.values()[which])
                     .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                       PhoneNumberPrivacyValues.PhoneNumberListingMode phoneNumberListingMode = value[0];
                       phoneNumberPrivacyValues.setPhoneNumberListingMode(phoneNumberListingMode);
                       Log.i(TAG, String.format("PhoneNumberListingMode changed to %s. Scheduling storage value sync", phoneNumberListingMode));
                       StorageSyncHelper.scheduleSyncForDataChange();
                       ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
                       initializePhoneNumberPrivacyWhoCanFindSummary();
                     })
                     .setNegativeButton(android.R.string.cancel, null)
                     .show();

      return true;
    }

    private CharSequence[] items(Context context) {
      return new CharSequence[]{
        titleAndDescription(context, context.getString(R.string.PhoneNumberPrivacy_everyone), context.getString(R.string.PhoneNumberPrivacy_everyone_find_description)),
        context.getString(R.string.PhoneNumberPrivacy_nobody) };
    }
  }

  /** Adds a detail row for radio group descriptions. */
  private static CharSequence titleAndDescription(@NonNull Context context, @NonNull String header, @NonNull String description) {
    SpannableStringBuilder builder = new SpannableStringBuilder();

    builder.append("\n");
    builder.append(header);
    builder.append("\n");

    builder.setSpan(new TextAppearanceSpan(context, android.R.style.TextAppearance_Small), builder.length(), builder.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

    builder.append(description);
    builder.append("\n");

    return builder;
  }
}
