package org.thoughtcrime.securesms;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.util.PushCharacterCalculator;

import java.util.LinkedList;
import java.util.List;

import static org.thoughtcrime.securesms.TransportOption.Type;

public class TransportOptions {

  private static final String TAG = TransportOptions.class.getSimpleName();

  private final List<OnTransportChangedListener> listeners = new LinkedList<>();

  private final TransportOption pushTransport;

  public TransportOptions(Context context, boolean media) {
    this.pushTransport = getPushTransportOption(context);
  }

  public void reset(boolean media) {
    notifyTransportChangeListeners();
  }

  public void setSelectedTransport(@Nullable  TransportOption transportOption) {
    notifyTransportChangeListeners();
  }

  public boolean isManualSelection() {
    return false;
  }

  public @NonNull TransportOption getSelectedTransport() {
    return pushTransport;
  }

  public static @NonNull TransportOption getPushTransportOption(@NonNull Context context) {
    return new TransportOption(Type.TEXTSECURE,
                               R.drawable.ic_send_lock_24,
                               context.getResources().getColor(R.color.core_ultramarine),
                               context.getString(R.string.ConversationActivity_transport_signal),
                               context.getString(R.string.conversation_activity__type_message_push),
                               new PushCharacterCalculator());
  }

  public void addOnTransportChangedListener(OnTransportChangedListener listener) {
    this.listeners.add(listener);
  }

  private void notifyTransportChangeListeners() {
    for (OnTransportChangedListener listener : listeners) {
      listener.onChange(getSelectedTransport(), false);
    }
  }

  public interface OnTransportChangedListener {
    public void onChange(TransportOption newTransport, boolean manuallySelected);
  }
}
