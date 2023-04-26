package org.thoughtcrime.securesms.components.location;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.android.gms.maps.model.LatLng;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.maps.AddressData;
import org.thoughtcrime.securesms.util.JsonUtils;

import java.io.IOException;

public class SignalPlace {

  private static final String URL = "https://maps.google.com/maps";

  private static final String TAG = Log.tag(SignalPlace.class);

  @JsonProperty
  private CharSequence name;

  @JsonProperty
  private CharSequence address;

  @JsonProperty
  private double latitude;

  @JsonProperty
  private double longitude;

  public SignalPlace(@NonNull AddressData place) {
    this.name      = "";
    this.address   = place.getAddress();
    this.latitude  = place.getLatitude();
    this.longitude = place.getLongitude();
  }

  @JsonCreator
  @SuppressWarnings("unused")
  public SignalPlace() {}

  @JsonIgnore
  public LatLng getLatLong() {
    return new LatLng(latitude, longitude);
  }

  @JsonIgnore
  public String getDescription() {
    String description = "";

    if (!TextUtils.isEmpty(name)) {
      description += (name + "\n");
    }

    if (!TextUtils.isEmpty(address)) {
      description += (address + "\n");
    }

    description += Uri.parse(URL)
                      .buildUpon()
                      .appendQueryParameter("q", String.format("%s,%s", latitude, longitude))
                      .build().toString();

    return description;
  }

  public @Nullable String serialize() {
    try {
      return JsonUtils.toJson(this);
    } catch (IOException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  public static SignalPlace deserialize(@NonNull  String serialized) throws IOException {
    return JsonUtils.fromJson(serialized, SignalPlace.class);
  }
}
