package com.google.android.gms.common;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;

import com.google.android.gms.tasks.Task;

public class GoogleApiAvailability {

  private static final GoogleApiAvailability INSTANCE = new GoogleApiAvailability();

  public static GoogleApiAvailability getInstance() {
    return INSTANCE;
  }

  public Dialog getErrorDialog(Context context, int serviceVersionUpdateRequired, int i) {
    return null;
  }

  public int isGooglePlayServicesAvailable(Context context) {
    return ConnectionResult.SERVICE_DISABLED;
  }

  public Task<Void> makeGooglePlayServicesAvailable(Activity activity) {
    return new Task<>();
  }
}
