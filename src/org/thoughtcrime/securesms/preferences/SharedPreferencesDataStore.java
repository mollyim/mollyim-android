package org.thoughtcrime.securesms.preferences;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;

import java.util.Set;

public class SharedPreferencesDataStore extends PreferenceDataStore {

  private final SharedPreferences sharedPreferences;

  public SharedPreferencesDataStore(SharedPreferences sharedPreferences) {
    this.sharedPreferences = sharedPreferences;
  }

  @Override
  public void putString(String key, @Nullable String value) {
    sharedPreferences.edit().putString(key, value).apply();
  }

  @Override
  public void putStringSet(String key, @Nullable Set<String> values) {
    sharedPreferences.edit().putStringSet(key, values).apply();
  }

  @Override
  public void putInt(String key, int value) {
    sharedPreferences.edit().putInt(key, value).apply();
  }

  @Override
  public void putLong(String key, long value) {
    sharedPreferences.edit().putLong(key, value).apply();
  }

  @Override
  public void putFloat(String key, float value) {
    sharedPreferences.edit().putFloat(key, value).apply();
  }

  @Override
  public void putBoolean(String key, boolean value) {
    sharedPreferences.edit().putBoolean(key, value).apply();
  }

  @Nullable
  @Override
  public String getString(String key, @Nullable String defValue) {
    return sharedPreferences.getString(key, defValue);
  }

  @Nullable
  @Override
  public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
    return sharedPreferences.getStringSet(key, defValues);
  }

  @Override
  public int getInt(String key, int defValue) {
    return sharedPreferences.getInt(key, defValue);
  }

  @Override
  public long getLong(String key, long defValue) {
    return sharedPreferences.getLong(key, defValue);
  }

  @Override
  public float getFloat(String key, float defValue) {
    return sharedPreferences.getFloat(key, defValue);
  }

  @Override
  public boolean getBoolean(String key, boolean defValue) {
    return sharedPreferences.getBoolean(key, defValue);
  }
}
