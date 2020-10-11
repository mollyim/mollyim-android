package com.google.firebase.messaging;

public abstract class FirebaseMessagingService {
  public abstract void onMessageReceived(RemoteMessage remoteMessage);

  public abstract void onNewToken(String token);
}
