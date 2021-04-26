package com.google.android.gms.maps;

import com.google.android.gms.maps.model.LatLng;

public class CameraUpdate {
  final LatLng latLng;
  final float  zoom;

  CameraUpdate(LatLng latLng, float zoom) {
    this.latLng = latLng;
    this.zoom   = zoom;
  }
}
