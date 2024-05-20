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

  private static final String OSM_URL = "https://openstreetmap.org/";
  private static final String GMS_URL = "https://maps.google.com/maps";

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
    final StringBuilder description = new StringBuilder();

    if (!TextUtils.isEmpty(name)) {
      description.append(name).append("\n");
    }

    if (!TextUtils.isEmpty(address)) {
      description.append(address).append("\n");
    }

    description.append("\nOpenStreetMap: ")
               .append(Uri.parse(OSM_URL)
                          .buildUpon()
                          .encodedFragment(String.format("map=15/%s/%s", latitude, longitude))
                          .build().toString())
               .append("\n\nGoogle Maps: ")
               .append(Uri.parse(GMS_URL)
                          .buildUpon()
                          .appendQueryParameter("q", String.format("%s,%s", latitude, longitude))
                          .build().toString());

    return description.toString();
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
