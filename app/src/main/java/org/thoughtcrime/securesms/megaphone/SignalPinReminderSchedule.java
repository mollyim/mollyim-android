package org.thoughtcrime.securesms.megaphone;

import org.thoughtcrime.securesms.keyvalue.SignalStore;

final class SignalPinReminderSchedule implements MegaphoneSchedule {

  @Override
  public boolean shouldDisplay(int seenCount, long lastSeen, long firstVisible, long currentTime) {
    if (SignalStore.svr().hasOptedOut()) {
      return false;
    }

    if (!SignalStore.svr().hasPin() || SignalStore.account().isLinkedDevice()) {
      return false;
    }

    if (SignalStore.account().isLinkedDevice()) {
      return false;
    }

    if (!SignalStore.pin().arePinRemindersEnabled()) {
      return false;
    }

    if (!SignalStore.account().isRegistered()) {
      return false;
    }

    if (SignalStore.account().isLinkedDevice()) {
      return false;
    }

    long lastReminderTime = SignalStore.pin().getLastReminderTime();
    long interval         = SignalStore.pin().getCurrentInterval();

    return currentTime - lastReminderTime >= interval;
  }
}
