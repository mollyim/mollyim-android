package com.google.firebase.iid.internal;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;

import java.io.IOException;

@Keep
public interface FirebaseInstanceIdInternal {

  void deleteToken(@NonNull String senderId, @NonNull String scope) throws IOException;

  String getId();

  @Nullable
  String getToken();

  @NonNull
  Task<String> getTokenTask();

  void addNewTokenListener(NewTokenListener listener);

  interface NewTokenListener {
    void onNewToken(String token);
  }
}
