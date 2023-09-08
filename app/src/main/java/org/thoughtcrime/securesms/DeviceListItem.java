package org.thoughtcrime.securesms;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;

import org.thoughtcrime.securesms.devicelist.Device;
import org.thoughtcrime.securesms.util.DateUtils;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Locale;

public class DeviceListItem extends LinearLayout {

  private long     deviceId;
  private TextView name;
  private View     primary;
  private View     thisDevice;
  private TextView created;
  private TextView lastActive;

  public DeviceListItem(Context context) {
    super(context);
  }

  public DeviceListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();
    this.name       = (TextView) findViewById(R.id.name);
    this.primary    = findViewById(R.id.primary);
    this.thisDevice = findViewById(R.id.this_device);
    this.created    = (TextView) findViewById(R.id.created);
    this.lastActive = (TextView) findViewById(R.id.active);
  }

  public void set(Device deviceInfo, Locale locale, long thisDeviceId) {
    if (TextUtils.isEmpty(deviceInfo.getName())) this.name.setText(getContext().getString(R.string.DeviceListItem_device_d, deviceInfo.getId()));
    else                                         this.name.setText(deviceInfo.getName());

    this.deviceId = deviceInfo.getId();

    this.created.setText(getContext().getString(this.deviceId == SignalServiceAddress.DEFAULT_DEVICE_ID ? R.string.DeviceListItem_registered_s : R.string.DeviceListItem_linked_s,
                                                DateUtils.getDayPrecisionTimeSpanString(getContext(),
                                                                                        locale,
                                                                                        deviceInfo.getCreated())));

    this.lastActive.setText(getContext().getString(R.string.DeviceListItem_last_active_s,
                                                   DateUtils.getDayPrecisionTimeSpanString(getContext(),
                                                                                           locale,
                                                                                           deviceInfo.getLastSeen())));

    this.primary.setVisibility(this.deviceId == SignalServiceAddress.DEFAULT_DEVICE_ID ? View.VISIBLE : View.GONE);
    this.thisDevice.setVisibility(this.deviceId == thisDeviceId ? View.VISIBLE : View.GONE);
  }

  public long getDeviceId() {
    return deviceId;
  }

  public String getDeviceName() {
    return name.getText().toString();
  }

}
