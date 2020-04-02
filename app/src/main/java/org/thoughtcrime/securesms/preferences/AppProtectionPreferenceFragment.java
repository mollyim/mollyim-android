package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.google.android.material.snackbar.Snackbar;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.BlockedContactsActivity;
import org.thoughtcrime.securesms.ChangePassphraseDialogFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
import org.thoughtcrime.securesms.database.Database;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.jobs.StorageSyncJob;
import org.thoughtcrime.securesms.lock.RegistrationLockDialog;
import org.thoughtcrime.securesms.lock.v2.CreateKbsPinActivity;
import org.thoughtcrime.securesms.lock.v2.PinUtil;
import org.thoughtcrime.securesms.preferences.widgets.PassphraseLockTriggerPreference;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import mobi.upod.timedurationpicker.TimeDurationPickerDialog;

public class AppProtectionPreferenceFragment extends CorrectedPreferenceFragment {

  private static final String PREFERENCE_CATEGORY_BLOCKED        = "preference_category_blocked";
  private static final String PREFERENCE_UNIDENTIFIED_LEARN_MORE = "pref_unidentified_learn_more";

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    SwitchPreferenceCompat regLock      = (SwitchPreferenceCompat) this.findPreference(TextSecurePreferences.REGISTRATION_LOCK_PREF_V1);
    Preference             kbsPinChange = this.findPreference(TextSecurePreferences.KBS_PIN_CHANGE);
    Preference             regGroup     = this.findPreference("prefs_lock_v1");
    Preference             kbsGroup     = this.findPreference("prefs_lock_v2");

    if (FeatureFlags.pinsForAll()) {
      Preference preference = this.findPreference("pref_kbs_change");
      regGroup.setVisible(false);

      if (PinUtil.userHasPin(ApplicationDependencies.getApplication())) {
        kbsPinChange.setOnPreferenceClickListener(new KbsPinUpdateListener());
        preference.setWidgetLayoutResource(R.layout.kbs_pin_change_preference);
      } else {
        kbsPinChange.setOnPreferenceClickListener(new KbsPinCreateListener());
        preference.setWidgetLayoutResource(R.layout.kbs_pin_create_preference);
      }
    } else {
      kbsGroup.setVisible(false);
      regLock.setChecked(PinUtil.userHasPin(requireContext()));
      regLock.setOnPreferenceClickListener(new AccountLockClickListener());
    }

    this.findPreference(TextSecurePreferences.PASSPHRASE_LOCK).setOnPreferenceChangeListener(new PassphraseLockListener());
    this.findPreference(TextSecurePreferences.PASSPHRASE_LOCK_TRIGGER).setOnPreferenceChangeListener(new PassphraseLockTriggerChangeListener());
    this.findPreference(TextSecurePreferences.PASSPHRASE_LOCK_TIMEOUT).setOnPreferenceClickListener(new PassphraseLockTimeoutListener());
    this.findPreference(TextSecurePreferences.CHANGE_PASSPHRASE_PREF).setOnPreferenceClickListener(new ChangePassphraseClickListener());
    this.findPreference(TextSecurePreferences.READ_RECEIPTS_PREF).setOnPreferenceChangeListener(new ReadReceiptToggleListener());
    this.findPreference(TextSecurePreferences.TYPING_INDICATORS).setOnPreferenceChangeListener(new TypingIndicatorsToggleListener());
    this.findPreference(TextSecurePreferences.LINK_PREVIEWS).setOnPreferenceChangeListener(new LinkPreviewToggleListener());
    this.findPreference(PREFERENCE_CATEGORY_BLOCKED).setOnPreferenceClickListener(new BlockedContactsClickListener());
    this.findPreference(TextSecurePreferences.SHOW_UNIDENTIFIED_DELIVERY_INDICATORS).setOnPreferenceChangeListener(new ShowUnidentifiedDeliveryIndicatorsChangedListener());
    this.findPreference(TextSecurePreferences.UNIVERSAL_UNIDENTIFIED_ACCESS).setOnPreferenceChangeListener(new UniversalUnidentifiedAccessChangedListener());
    this.findPreference(PREFERENCE_UNIDENTIFIED_LEARN_MORE).setOnPreferenceClickListener(new UnidentifiedLearnMoreClickListener());

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
  }

  private void initializePassphraseLockTriggerSummary() {
    findPreference(TextSecurePreferences.PASSPHRASE_LOCK_TRIGGER)
            .setSummary(getSummaryForPassphraseLockTrigger(TextSecurePreferences.getPassphraseLockTrigger(getContext()).getTriggers()));
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == CreateKbsPinActivity.REQUEST_NEW_PIN && resultCode == CreateKbsPinActivity.RESULT_OK) {
      Snackbar.make(requireView(), R.string.ConfirmKbsPinFragment__pin_created, Snackbar.LENGTH_LONG).show();
    }
  }

  private void initializePassphraseTimeoutSummary() {
    findPreference(TextSecurePreferences.PASSPHRASE_LOCK_TIMEOUT)
            .setSummary(getLockTimeoutSummary(TextSecurePreferences.getPassphraseLockTimeout(getContext())));
  }

  private String getLockTimeoutSummary(long timeoutSeconds) {
    if (timeoutSeconds <= 0) return getString(R.string.AppProtectionPreferenceFragment_none);

    long hours   = TimeUnit.SECONDS.toHours(timeoutSeconds);
    long minutes = TimeUnit.SECONDS.toMinutes(timeoutSeconds) - (TimeUnit.SECONDS.toHours(timeoutSeconds) * 60  );
    long seconds = TimeUnit.SECONDS.toSeconds(timeoutSeconds) - (TimeUnit.SECONDS.toMinutes(timeoutSeconds) * 60);

    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
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
            : TextUtils.join(", ", outValues);
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
        long timeoutSeconds = 0;

        if (duration > 0) {
          timeoutSeconds = Math.max(TimeUnit.MILLISECONDS.toSeconds(duration), 5);
        }

        TextSecurePreferences.setPassphraseLockTimeout(getContext(), timeoutSeconds);
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

  private class AccountLockClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      Context context = requireContext();

      if (PinUtil.userHasPin(context)) {
        RegistrationLockDialog.showRegistrationUnlockPrompt(context, (SwitchPreferenceCompat)preference);
      } else {
        RegistrationLockDialog.showRegistrationLockPrompt(context, (SwitchPreferenceCompat)preference);
      }

      return true;
    }
  }

  private class BlockedContactsClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      Intent intent = new Intent(getActivity(), BlockedContactsActivity.class);
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
                                                                                          TextSecurePreferences.isLinkPreviewsEnabled(getContext())));

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
                                                                                          TextSecurePreferences.isLinkPreviewsEnabled(getContext())));

        if (!enabled) {
          ApplicationContext.getInstance(requireContext()).getTypingStatusRepository().clear();
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
      });
      return true;
    }
  }

  public static CharSequence getSummary(Context context) {
    final   int    privacySummaryResId = FeatureFlags.pinsForAll() ? R.string.ApplicationPreferencesActivity_privacy_summary_passphrase_lock
                                                                   : R.string.ApplicationPreferencesActivity_privacy_summary_passphrase_registration_locks;
    final   String onRes               = context.getString(R.string.ApplicationPreferencesActivity_on);
    final   String offRes              = context.getString(R.string.ApplicationPreferencesActivity_off);
    boolean registrationLockEnabled    = PinUtil.userHasPin(context);

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
                                                                                          TextSecurePreferences.isLinkPreviewsEnabled(getContext())));
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
}
