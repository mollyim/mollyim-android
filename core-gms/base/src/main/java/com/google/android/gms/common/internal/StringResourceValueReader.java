package com.google.android.gms.common.internal;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.R;

@Keep
public class StringResourceValueReader {

  private final Resources resources;
  private final String    namespace;

  public StringResourceValueReader(@NonNull Context context) {
    resources = context.getResources();
    namespace = resources.getResourcePackageName(R.integer.google_play_services_version);
  }

  @SuppressLint("DiscouragedApi")
  @Nullable
  public String getString(@NonNull String name) {
    int resId = resources.getIdentifier(name, "string", namespace);
    return resId == 0 ? null : resources.getString(resId);
  }
}
