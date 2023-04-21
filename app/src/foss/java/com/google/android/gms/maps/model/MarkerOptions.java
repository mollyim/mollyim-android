package com.google.android.gms.maps.model;

public final class MarkerOptions {
  private LatLng latLng;

  public LatLng getPosition() {
    return latLng;
  }

  public MarkerOptions position(LatLng latlng) {
    this.latLng = latlng;
    return this;
  }
}
