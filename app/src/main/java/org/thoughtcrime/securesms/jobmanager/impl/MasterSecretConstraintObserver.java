package org.thoughtcrime.securesms.jobmanager.impl;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.jobmanager.ConstraintObserver;
import org.thoughtcrime.securesms.service.KeyCachingService;

public class MasterSecretConstraintObserver implements ConstraintObserver {

  private static final String REASON = MasterSecretConstraintObserver.class.getSimpleName();

  private final Application application;

  public MasterSecretConstraintObserver(@NonNull Application application) {
    this.application = application;
  }

  @Override
  public void register(@NonNull Notifier notifier) {
    IntentFilter filter = new IntentFilter();

    filter.addAction(KeyCachingService.CLEAR_KEY_EVENT);

    ContextCompat.registerReceiver(application, new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        notifier.onConstraintMet(REASON);
      }
    }, filter, KeyCachingService.KEY_PERMISSION, null, ContextCompat.RECEIVER_NOT_EXPORTED);
  }
}