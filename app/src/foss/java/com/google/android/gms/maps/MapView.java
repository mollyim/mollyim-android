package com.google.android.gms.maps;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;

import androidx.core.content.res.ResourcesCompat;

import com.google.android.gms.maps.GoogleMap.OnMapLoadedCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.tileprovider.TileStates;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsDisplay;
import org.osmdroid.views.overlay.Marker;

import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.net.Networking;
import org.thoughtcrime.securesms.osm.MapTileProvider;

import java.net.Proxy;

public class MapView extends org.osmdroid.views.MapView {
  private Marker               marker;
  private boolean isMyLocationEnabled = false;
  private MyLocationNewOverlay myLocationNewOverlay;
  private OnMapLoadedCallback  onMapLoadedCallback;
  private boolean              isMapRenderWatcherRunning = false;

  static private MapTileProviderBase createTileProvider(Context context) {
    return new MapTileProvider(context, TileSourceFactory.DEFAULT_TILE_SOURCE);
  }

  public MapView(Context context) {
    super(context, createTileProvider(context));
  }

  public MapView(Context context, AttributeSet attrs) {
    super(context, createTileProvider(context), null, attrs);
  }

  public MapView(Context context, AttributeSet attrs, int defStyle) {
    super(context, createTileProvider(context), null, attrs);
  }

  public void getMapAsync(OnMapReadyCallback callback) {
    setDefaultConfiguration(getContext());
    this.setTilesScaledToDpi(true);

    GoogleMap googleMap = new GoogleMap(this);
    callback.onMapReady(googleMap);
  }

  private void setDefaultConfiguration(Context context) {
    final String userAgent = context.getPackageName();
    final Proxy proxy = Networking.getProxy();
    final IConfigurationProvider config = Configuration.getInstance();
    config.setDebugMode(BuildConfig.DEBUG);
    config.setUserAgentValue(userAgent);
    if (proxy != Proxy.NO_PROXY) {
      config.setHttpProxy(proxy);
    }

    getZoomController().getDisplay().setPositions(
        false,
        CustomZoomButtonsDisplay.HorizontalPosition.RIGHT,
        CustomZoomButtonsDisplay.VerticalPosition.TOP);
    setMultiTouchControls(true);
  }

  void addMarker(MarkerOptions markerOptions) {
    removeExistingMarker();

    marker = new Marker(this);
    marker.setPosition(toPoint(markerOptions.getPosition()));
    Drawable icon = ResourcesCompat.getDrawable(
        getContext().getResources(),
        R.drawable.ic_map_marker,
        getContext().getTheme());
    marker.setIcon(icon);
    getOverlays().add(marker);
  }

  private void removeExistingMarker() {
    if (marker == null) return;
    getOverlays().remove(marker);
    marker = null;
  }

  void setMapPosition(CameraPosition position) {
    IMapController mapController = getController();
    mapController.setCenter(toPoint(position.target));
    mapController.setZoom(position.zoom);
  }

  Bitmap snapshot() {
    Bitmap bitmap = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(), Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    layout(getLeft(), getTop(), getRight(), getBottom());
    draw(canvas);
    return bitmap;
  }

  private void waitMapRender() {
    if(isMapRenderWatcherRunning) return;
    isMapRenderWatcherRunning = true;
    postDelayed(new Runnable() {
      @Override
      public void run() {
        TileStates tileStates = getOverlayManager().getTilesOverlay().getTileStates();
        if (tileStates.getTotal() > 0 && (tileStates.getTotal() == tileStates.getUpToDate())) {
          if (onMapLoadedCallback != null) onMapLoadedCallback.onMapLoaded();
          isMapRenderWatcherRunning = false;
        } else {
          // We are not done yet. wait for a few cycles and check again
          postDelayed(this, 72);
        }
      }
    }, 72);
  }

  void setOnMapLoadedCallback(OnMapLoadedCallback onMapLoadedCallback) {
    this.onMapLoadedCallback = onMapLoadedCallback;
    waitMapRender();
  }

  void setMyLocationEnabled(boolean isMyLocationEnabled) {
    // TODO this code does not add the "jump to my location" button on the map atm.
    this.isMyLocationEnabled = isMyLocationEnabled;
    if (isMyLocationEnabled) {
      if (myLocationNewOverlay == null) {
        myLocationNewOverlay = new MyLocationNewOverlay(this);
        myLocationNewOverlay.enableMyLocation();
      }
      getOverlays().add(myLocationNewOverlay);
    } else if (myLocationNewOverlay != null) {
      getOverlays().remove(myLocationNewOverlay);
      myLocationNewOverlay.disableMyLocation();
    }
  }

  public final void onCreate(Bundle savedInstanceState) { }

  public final void onStart() { }

  public final void onResume() {
    super.onResume();
    if (myLocationNewOverlay != null && isMyLocationEnabled) {
      myLocationNewOverlay.enableMyLocation();
    }
  }

  public final void onPause() {
    super.onPause();
    if (myLocationNewOverlay != null) {
      myLocationNewOverlay.disableMyLocation();
    }
  }

  public final void onStop() { }

  public final void onDestroy() { }

  private GeoPoint toPoint(LatLng latLng) {
    return new GeoPoint(latLng.latitude, latLng.longitude);
  }
}
