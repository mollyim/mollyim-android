package org.thoughtcrime.securesms.preferences;


import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.components.CustomDefaultPreference;
import org.thoughtcrime.securesms.util.SecurePreferenceManager;

import java.util.Objects;

public abstract class CorrectedPreferenceFragment extends PreferenceFragmentCompat {

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    PreferenceDataStore dataStore = new SharedPreferencesDataStore(
            SecurePreferenceManager.getSecurePreferences(getContext()));
    getPreferenceManager().setPreferenceDataStore(dataStore);
    onCreateEncryptedPreferences(savedInstanceState, rootKey);
  }

  protected abstract void onCreateEncryptedPreferences(Bundle savedInstanceState, String rootKey);

  @Override
  public void onDisplayPreferenceDialog(Preference preference) {
    DialogFragment dialogFragment = null;

    if (preference instanceof CustomDefaultPreference) {
      dialogFragment = CustomDefaultPreference.CustomDefaultPreferenceDialogFragmentCompat.newInstance(preference.getKey());
    }

    if (dialogFragment != null) {
      dialogFragment.setTargetFragment(this, 0);
      dialogFragment.show(getFragmentManager(), "android.support.v7.preference.PreferenceFragment.DIALOG");
    } else {
      super.onDisplayPreferenceDialog(preference);
    }
  }

  @Override
  @SuppressLint("RestrictedApi")
  protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
    return new PreferenceGroupAdapter(preferenceScreen) {
      @Override
      public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        Preference preference = getItem(position);
        View iconFrame = holder.itemView.findViewById(androidx.preference.R.id.icon_frame);
        if (iconFrame != null) {
          iconFrame.setVisibility(preference.getIcon() == null ? View.GONE : View.VISIBLE);
        }
      }
    };
  }

  public <T extends Preference> T requirePreference(@NonNull CharSequence key) {
    return Objects.requireNonNull(super.findPreference(key));
  }

  @Nullable
  protected ActionBar getSupportActionBar() {
    if (getActivity() instanceof AppCompatActivity) {
      return ((AppCompatActivity) getActivity()).getSupportActionBar();
    } else {
      return null;
    }
  }
}
