package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;

import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.TransportOptions;
import org.thoughtcrime.securesms.TransportOptions.OnTransportChangedListener;
import org.thoughtcrime.securesms.util.ViewUtil;

public class SendButton extends AppCompatImageButton
    implements TransportOptions.OnTransportChangedListener
{

  private final TransportOptions transportOptions;

  @SuppressWarnings("unused")
  public SendButton(Context context) {
    super(context);
    this.transportOptions = initializeTransportOptions(false);
    ViewUtil.mirrorIfRtl(this, getContext());
  }

  @SuppressWarnings("unused")
  public SendButton(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.transportOptions = initializeTransportOptions(false);
    ViewUtil.mirrorIfRtl(this, getContext());
  }

  @SuppressWarnings("unused")
  public SendButton(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    this.transportOptions = initializeTransportOptions(false);
    ViewUtil.mirrorIfRtl(this, getContext());
  }

  private TransportOptions initializeTransportOptions(boolean media) {
    if (isInEditMode()) return null;

    TransportOptions transportOptions = new TransportOptions(getContext(), media);
    transportOptions.addOnTransportChangedListener(this);

    return transportOptions;
  }

  public boolean isManualSelection() {
    return transportOptions.isManualSelection();
  }

  public void addOnTransportChangedListener(OnTransportChangedListener listener) {
    transportOptions.addOnTransportChangedListener(listener);
  }

  public TransportOption getSelectedTransport() {
    return transportOptions.getSelectedTransport();
  }

  public void resetAvailableTransports(boolean isMediaMessage) {
    transportOptions.reset(isMediaMessage);
  }

  public void setTransport(@NonNull TransportOption option) {
    transportOptions.setSelectedTransport(option);
  }

  @Override
  public void onChange(TransportOption newTransport, boolean isManualSelection) {
    setImageResource(newTransport.getDrawable());
    setContentDescription(newTransport.getDescription());
  }
}
