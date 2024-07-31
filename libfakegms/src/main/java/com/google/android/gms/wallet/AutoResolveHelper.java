package com.google.android.gms.wallet;

import android.app.Activity;
import android.content.Intent;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;

public class AutoResolveHelper {
  public static final int RESULT_ERROR = 1;

  public static <T> void resolveTask(Task<T> task, Activity activity, int requestCode) {
  }

  public static Status getStatusFromIntent (Intent data) {
    return new Status();
  }
}
