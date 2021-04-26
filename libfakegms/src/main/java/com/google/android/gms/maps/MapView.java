package com.google.android.gms.maps;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;

public class MapView extends View {
  public MapView(Context context) {
    super(context);
  }

  public MapView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public MapView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void getMapAsync(OnMapReadyCallback cb) {
  }

  public void onCreate(Bundle savedInstanceState) {
  }

  public void onResume() {
  }

  @Override
  public void setVisibility(int visible) {
  }

  public void onPause() {
  }

  public void onDestroy() {
  }
}
