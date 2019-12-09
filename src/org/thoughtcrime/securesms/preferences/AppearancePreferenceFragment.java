package org.thoughtcrime.securesms.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.Arrays;

public class AppearancePreferenceFragment extends ListSummaryPreferenceFragment
    implements Preference.OnPreferenceChangeListener
{
  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    ListPreference themePref = (ListPreference) findPreference(TextSecurePreferences.THEME_PREF);
    themePref.setOnPreferenceChangeListener(this);
    initializeListSummary(themePref);

    ListPreference languagePref = (ListPreference) findPreference(TextSecurePreferences.LANGUAGE_PREF);
    languagePref.setOnPreferenceChangeListener(this);
    initializeListSummary(languagePref);
  }

  @Override
  public void onCreateEncryptedPreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_appearance);
  }

  @Override
  public boolean onPreferenceChange(Preference preference, Object newValue) {
    final Activity activity = getActivity();
    if (activity == null) return false;

    activity.recreate();

    if (TextSecurePreferences.LANGUAGE_PREF.equals(preference.getKey())) {
      Intent intent = new Intent(activity, KeyCachingService.class);
      intent.setAction(KeyCachingService.LOCALE_CHANGE_EVENT);
      activity.startService(intent);
    }

    return new ListSummaryListener().onPreferenceChange(preference, newValue);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__appearance);
  }

  public static CharSequence getSummary(Context context) {
    String[] languageEntries     = context.getResources().getStringArray(R.array.language_entries);
    String[] languageEntryValues = context.getResources().getStringArray(R.array.language_values);
    String[] themeEntries        = context.getResources().getStringArray(R.array.pref_theme_entries);
    String[] themeEntryValues    = context.getResources().getStringArray(R.array.pref_theme_values);

    int langIndex  = Arrays.asList(languageEntryValues).indexOf(TextSecurePreferences.getLanguage(context));
    int themeIndex = Arrays.asList(themeEntryValues).indexOf(TextSecurePreferences.getTheme(context));

    if (langIndex == -1)  langIndex = 0;
    if (themeIndex == -1) themeIndex = 0;

    return context.getString(R.string.ApplicationPreferencesActivity_appearance_summary,
                             themeEntries[themeIndex],
                             languageEntries[langIndex]);
  }
}
