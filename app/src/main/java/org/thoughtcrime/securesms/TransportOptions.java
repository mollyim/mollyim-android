package org.thoughtcrime.securesms;

import android.Manifest;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.CharacterCalculator;
import org.thoughtcrime.securesms.util.PushCharacterCalculator;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static org.thoughtcrime.securesms.TransportOption.Type;

public class TransportOptions {

  private static final String TAG = TransportOptions.class.getSimpleName();

  private final List<OnTransportChangedListener> listeners = new LinkedList<>();
  private final Context                          context;
  private final List<TransportOption>            enabledTransports;

  private Type                      defaultTransportType  = Type.TEXTSECURE;
  private Optional<Integer>         defaultSubscriptionId = Optional.absent();
  private Optional<TransportOption> selectedOption        = Optional.absent();

  public TransportOptions(Context context, boolean media) {
    this.context              = context;
    this.enabledTransports    = initializeAvailableTransports(media);
  }

  public void reset(boolean media) {
    List<TransportOption> transportOptions = initializeAvailableTransports(media);

    this.enabledTransports.clear();
    this.enabledTransports.addAll(transportOptions);

    if (selectedOption.isPresent() && !isEnabled(selectedOption.get())) {
      setSelectedTransport(null);
    } else {
      this.defaultTransportType = Type.TEXTSECURE;
      this.defaultSubscriptionId = Optional.absent();

      notifyTransportChangeListeners();
    }
  }

  public void setDefaultTransport(Type type) {
    this.defaultTransportType = type;

    if (!selectedOption.isPresent()) {
      notifyTransportChangeListeners();
    }
  }

  public void setDefaultSubscriptionId(Optional<Integer> subscriptionId) {
    if  (defaultSubscriptionId.equals(subscriptionId)) {
      return;
    }

    this.defaultSubscriptionId = subscriptionId;

    if (!selectedOption.isPresent()) {
      notifyTransportChangeListeners();
    }
  }

  public void setSelectedTransport(@Nullable  TransportOption transportOption) {
    this.selectedOption = Optional.fromNullable(transportOption);
    notifyTransportChangeListeners();
  }

  public boolean isManualSelection() {
    return this.selectedOption.isPresent();
  }

  public @NonNull TransportOption getSelectedTransport() {
    if (selectedOption.isPresent()) return selectedOption.get();

    for (TransportOption transportOption : enabledTransports) {
      if (transportOption.getType() == defaultTransportType) {
        return transportOption;
      }
    }

    throw new AssertionError("No options of default type!");
  }

  public static @NonNull TransportOption getPushTransportOption(@NonNull Context context) {
    return new TransportOption(Type.TEXTSECURE,
                               R.drawable.ic_send_lock_24,
                               context.getResources().getColor(R.color.textsecure_primary),
                               context.getString(R.string.ConversationActivity_transport_signal),
                               context.getString(R.string.conversation_activity__type_message_push),
                               new PushCharacterCalculator());

  }

  public void disableTransport(Type type) {
    TransportOption selected = selectedOption.orNull();

    Iterator<TransportOption> iterator = enabledTransports.iterator();
    while (iterator.hasNext()) {
      TransportOption option = iterator.next();

      if (option.isType(type)) {
        if (selected == option) {
          setSelectedTransport(null);
        }
        iterator.remove();
      }
    }
  }

  public List<TransportOption> getEnabledTransports() {
    return enabledTransports;
  }

  public void addOnTransportChangedListener(OnTransportChangedListener listener) {
    this.listeners.add(listener);
  }

  private List<TransportOption> initializeAvailableTransports(boolean isMediaMessage) {
    List<TransportOption> results = new LinkedList<>();

    results.add(getPushTransportOption(context));

    return results;
  }

  private void notifyTransportChangeListeners() {
    for (OnTransportChangedListener listener : listeners) {
      listener.onChange(getSelectedTransport(), selectedOption.isPresent());
    }
  }

  private boolean isEnabled(TransportOption transportOption) {
    for (TransportOption option : enabledTransports) {
      if (option.equals(transportOption)) return true;
    }

    return false;
  }

  public interface OnTransportChangedListener {
    public void onChange(TransportOption newTransport, boolean manuallySelected);
  }
}
