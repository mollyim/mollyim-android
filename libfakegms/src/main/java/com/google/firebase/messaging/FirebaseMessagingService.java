package com.google.firebase.messaging;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

public abstract class FirebaseMessagingService extends Service {
  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  public abstract void onMessageReceived(RemoteMessage remoteMessage);

  public abstract void onDeletedMessages();

  public abstract void onNewToken(String token);
}
