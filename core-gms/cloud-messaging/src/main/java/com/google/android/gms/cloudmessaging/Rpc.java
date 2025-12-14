package com.google.android.gms.cloudmessaging;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.io.IOException;

@Keep
public class Rpc {

  private static final String TAG = "Rpc";

  private static final String SERVICE_NOT_AVAILABLE = "SERVICE_NOT_AVAILABLE";

  private static final int MIN_VERSION_FOR_MESSAGE_ACK     = 233700000;
  private static final int MIN_VERSION_FOR_PROXY_RETENTION = 241100000;

  private final MessengerIpcClient ipcClient;
  private final Metadata           metadata;

  public Rpc(@NonNull Context context) {
    this.ipcClient = MessengerIpcClient.getInstance(context);
    this.metadata  = new Metadata(context);
  }

  @NonNull
  public Task<Bundle> send(@NonNull Bundle data) {
    return ipcClient
        .sendRequest(GmsOpCode.SEND, data)
        .continueWith(Runnable::run, task -> {
          if (task.isSuccessful()) {
            return task.getResult();
          } else {
            Exception e = task.getException();
            Log.d(TAG, "Error making request: " + e);
            throw new IOException(SERVICE_NOT_AVAILABLE, e);
          }
        });
  }

  @NonNull
  public Task<Void> messageHandled(@NonNull CloudMessage message) {
    if (metadata.getGmsPackageVersion() < MIN_VERSION_FOR_MESSAGE_ACK) {
      return serviceNotAvailable();
    }
    Bundle response = CloudMessageBundle.forMessage(message);
    return ipcClient.sendOneWay(GmsOpCode.RPC_ACK, response);
  }

  @NonNull
  public Task<Void> setRetainProxiedNotifications(boolean retain) {
    if (!BuildConfig.NOTIFICATION_PAYLOAD_ENABLED) {
      return serviceNotAvailable();
    }
    if (metadata.getGmsPackageVersion() < MIN_VERSION_FOR_PROXY_RETENTION) {
      return serviceNotAvailable();
    }
    Bundle bundle = new Bundle();
    bundle.putBoolean("proxy_retention", retain);
    return ipcClient.sendOneWay(GmsOpCode.PROXY_RETAIN, bundle);
  }

  @NonNull
  public Task<CloudMessage> getProxiedNotificationData() {
    if (!BuildConfig.NOTIFICATION_PAYLOAD_ENABLED) {
      return serviceNotAvailable();
    }
    if (metadata.getGmsPackageVersion() < MIN_VERSION_FOR_PROXY_RETENTION) {
      return serviceNotAvailable();
    }
    return ipcClient
        .sendRequest(GmsOpCode.PROXY_FETCH, Bundle.EMPTY)
        .continueWith(Runnable::run, task -> {
          Bundle     bundle = task.getResult();
          Parcelable intent = bundle.getParcelable("notification_data");
          if (!(intent instanceof Intent)) {
            return null;
          }
          return new CloudMessage((Intent) intent);
        });
  }

  @NonNull
  private static <T> Task<T> serviceNotAvailable() {
    return Tasks.forException(new IOException(SERVICE_NOT_AVAILABLE));
  }
}
