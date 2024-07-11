package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.internal.push.SyncMessage.Request;

import java.io.IOException;

public class AllDataSyncRequestJob extends BaseJob {

  public static final String KEY = "AllDataSyncRequestJob";

  private static final String TAG = Log.tag(AllDataSyncRequestJob.class);

  public AllDataSyncRequestJob()
  {
    this(new Job.Parameters.Builder()
                           .setQueue("AllDataSyncRequestJob")
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxAttempts(10)
                           .build());
  }

  private AllDataSyncRequestJob(@NonNull Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull byte[] serialize() {
    return null;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    SignalServiceMessageSender signalServiceMessageSender = AppDependencies.getSignalServiceMessageSender();
    signalServiceMessageSender.sendSyncMessage(SignalServiceSyncMessage.forRequest(RequestMessage.forType(Request.Type.CONTACTS)));
    signalServiceMessageSender.sendSyncMessage(SignalServiceSyncMessage.forRequest(RequestMessage.forType(Request.Type.BLOCKED)));
    signalServiceMessageSender.sendSyncMessage(SignalServiceSyncMessage.forRequest(RequestMessage.forType(Request.Type.CONFIGURATION)));
    signalServiceMessageSender.sendSyncMessage(SignalServiceSyncMessage.forRequest(RequestMessage.forType(Request.Type.KEYS)));
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {}

  public static final class Factory implements Job.Factory<AllDataSyncRequestJob> {
    @Override
    public @NonNull AllDataSyncRequestJob create(@NonNull Parameters parameters, @NonNull byte[] serializedData) {
      return new AllDataSyncRequestJob(parameters);
    }
  }
}
