package com.google.android.gms.maps;

import com.google.android.gms.maps.model.LatLng;

public class CameraUpdateFactory {
  public static CameraUpdate newLatLngZoom(LatLng latLng, float zoom) {
    return new CameraUpdate(latLng, zoom);
  }
}
