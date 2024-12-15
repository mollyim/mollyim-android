package org.thoughtcrime.securesms.preferences;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinActivity;
import org.thoughtcrime.securesms.pin.PinOptOutDialog;

public class AdvancedPinPreferenceFragment extends ListSummaryPreferenceFragment {

  private static final String PREF_ENABLE  = "pref_pin_enable";
  private static final String PREF_DISABLE = "pref_pin_disable";

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
  }

  @Override
  public void onCreateEncryptedPreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_advanced_pin);
  }

  @Override
  public void onResume() {
    super.onResume();
    updatePreferenceState();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == CreateSvrPinActivity.REQUEST_NEW_PIN && resultCode == CreateSvrPinActivity.RESULT_OK) {
      Snackbar.make(requireView(), R.string.ApplicationPreferencesActivity_pin_created, Snackbar.LENGTH_LONG).show();
    }
  }

  private void updatePreferenceState() {
    Preference enable = this.findPreference(PREF_ENABLE);
    Preference disable = this.findPreference(PREF_DISABLE);

    if (SignalStore.svr().hasOptedOut()) {
      enable.setVisible(true);
      disable.setVisible(false);

      enable.setOnPreferenceClickListener(preference -> {
        onPreferenceChanged(true);
        return true;
      });
    } else {
      enable.setVisible(false);
      disable.setVisible(true);

      disable.setOnPreferenceClickListener(preference -> {
        onPreferenceChanged(false);
        return true;
      });
    }
  }

  private void onPreferenceChanged(boolean enabled) {
    boolean hasRegistrationLock = SignalStore.svr().isRegistrationLockEnabled();

    if (!enabled && hasRegistrationLock) {
      new MaterialAlertDialogBuilder(requireContext())
                     .setMessage(R.string.ApplicationPreferencesActivity_pins_are_required_for_registration_lock)
                     .setCancelable(true)
                     .setPositiveButton(android.R.string.ok, (d, which) -> d.dismiss())
                     .show();
    } else if (!enabled) {
      PinOptOutDialog.show(requireContext(),
                           () -> {
                             updatePreferenceState();
                             Snackbar.make(requireView(), R.string.ApplicationPreferencesActivity_pin_disabled, Snackbar.LENGTH_SHORT).show();
                           });
    } else {
      startActivityForResult(CreateSvrPinActivity.getIntentForPinCreate(requireContext()), CreateSvrPinActivity.REQUEST_NEW_PIN);
    }
  }
}
