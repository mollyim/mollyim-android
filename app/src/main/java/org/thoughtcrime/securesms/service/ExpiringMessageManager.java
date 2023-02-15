package org.thoughtcrime.securesms.service;

import android.content.Context;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;

import java.util.Comparator;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExpiringMessageManager {

  private static final String TAG = Log.tag(ExpiringMessageManager.class);

  private final TreeSet<ExpiringMessageReference> expiringMessageReferences = new TreeSet<>(new ExpiringMessageComparator());
  private final ExecutorService                   executor                  = Executors.newSingleThreadExecutor();

  private final MessageTable messageTable;
  private final Context      context;

  public ExpiringMessageManager(Context context) {
    this.context      = context.getApplicationContext();
    this.messageTable = SignalDatabase.messages();

    executor.execute(new LoadTask());
    executor.execute(new ProcessTask());
  }

  public void quit() {
    executor.shutdownNow();
  }

  public void scheduleDeletion(long id, long expiresInMillis) {
    scheduleDeletion(id, System.currentTimeMillis(), expiresInMillis);
  }

  public void scheduleDeletion(long id, long startedAtTimestamp, long expiresInMillis) {
    long expiresAtMillis = startedAtTimestamp + expiresInMillis;

    synchronized (expiringMessageReferences) {
      expiringMessageReferences.add(new ExpiringMessageReference(id, expiresAtMillis));
      expiringMessageReferences.notifyAll();
    }
  }

  public void checkSchedule() {
    synchronized (expiringMessageReferences) {
      expiringMessageReferences.notifyAll();
    }
  }

  private class LoadTask implements Runnable {
    public void run() {
      MessageTable.MmsReader mmsReader = MessageTable.mmsReaderFor(messageTable.getExpirationStartedMessages());

      MessageRecord messageRecord;

      while ((messageRecord = mmsReader.getNext()) != null) {
        expiringMessageReferences.add(new ExpiringMessageReference(messageRecord.getId(),
                                                                   messageRecord.getExpireStarted() + messageRecord.getExpiresIn()));
      }

      mmsReader.close();
    }
  }

  private class ProcessTask implements Runnable {
    public void run() {
      while (true) {
        ExpiringMessageReference expiredMessage = null;

        synchronized (expiringMessageReferences) {
          try {
            while (expiringMessageReferences.isEmpty()) expiringMessageReferences.wait();

            ExpiringMessageReference nextReference = expiringMessageReferences.first();
            long                     waitTime      = nextReference.expiresAtMillis - System.currentTimeMillis();

            if (waitTime > 0) {
              ExpirationListener.setAlarm(context, waitTime);
              expiringMessageReferences.wait(waitTime);
            } else {
              expiredMessage = nextReference;
              expiringMessageReferences.remove(nextReference);
            }

          } catch (InterruptedException e) {
            Log.i(TAG, "Interrupted.");
            ExpirationListener.cancelAlarm(context);
            break;
          }
        }

        if (expiredMessage != null) {
          messageTable.deleteExpiringMessage(expiredMessage.id);
        }
      }
    }
  }

  private static class ExpiringMessageReference {
    private final long    id;
    private final long    expiresAtMillis;

    private ExpiringMessageReference(long id, long expiresAtMillis) {
      this.id = id;
      this.expiresAtMillis = expiresAtMillis;
    }

    @Override
    public boolean equals(Object other) {
      if (other == null) return false;
      if (!(other instanceof ExpiringMessageReference)) return false;

      ExpiringMessageReference that = (ExpiringMessageReference)other;
      return this.id == that.id && this.expiresAtMillis == that.expiresAtMillis;
    }

    @Override
    public int hashCode() {
      return (int)this.id ^ (int)expiresAtMillis;
    }
  }

  private static class ExpiringMessageComparator implements Comparator<ExpiringMessageReference> {
    @Override
    public int compare(ExpiringMessageReference lhs, ExpiringMessageReference rhs) {
      if      (lhs.expiresAtMillis < rhs.expiresAtMillis) return -1;
      else if (lhs.expiresAtMillis > rhs.expiresAtMillis) return 1;
      else if (lhs.id < rhs.id)                           return -1;
      else if (lhs.id > rhs.id)                           return 1;
      else                                                return 0;
    }
  }

}
