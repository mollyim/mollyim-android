package com.google.android.gms.maps.model;

public final class CameraPosition {
  public final LatLng target;
  public final float  zoom;

  public CameraPosition(LatLng target, float zoom) {
    this.target = target;
    this.zoom   = zoom;
  }
}
