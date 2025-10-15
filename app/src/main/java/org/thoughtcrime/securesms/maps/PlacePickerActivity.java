package org.thoughtcrime.securesms.maps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;

import org.signal.core.util.concurrent.ListenableFuture;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.location.SignalMapView;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * Allows selection of an address from a google map.
 * <p>
 * Based on https://github.com/suchoX/PlacePicker
 */
public final class PlacePickerActivity extends AppCompatActivity {

  private static final String TAG = Log.tag(PlacePickerActivity.class);

  // If it cannot load location for any reason, it defaults to the prime meridian.
  private static final LatLng PRIME_MERIDIAN = new LatLng(51.4779, -0.0015);
  private static final String ADDRESS_INTENT = "ADDRESS";
  private static final float  ZOOM           = 17.0f;

  private static final int                   ANIMATION_DURATION     = 250;
  private static final OvershootInterpolator OVERSHOOT_INTERPOLATOR = new OvershootInterpolator();
  public  static final String                KEY_CHAT_COLOR         = "chat_color";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  private SingleAddressBottomSheet bottomSheet;
  private Address                  currentAddress;
  private LatLng                   initialLocation;
  private LatLng                   currentLocation = new LatLng(0, 0);
  private AddressLookup            addressLookup;
  private GoogleMap                googleMap;

  public static void startActivityForResultAtCurrentLocation(@NonNull Fragment fragment, int requestCode, @ColorInt int chatColor) {
    fragment.startActivityForResult(new Intent(fragment.requireActivity(), PlacePickerActivity.class).putExtra(KEY_CHAT_COLOR, chatColor), requestCode);
  }

  public static AddressData addressFromData(@NonNull Intent data) {
    return data.getParcelableExtra(ADDRESS_INTENT);
  }

  @SuppressLint("MissingInflatedId")
  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    dynamicTheme.onCreate(this);

    setContentView(R.layout.activity_place_picker);

    bottomSheet      = findViewById(R.id.bottom_sheet);
    View markerImage = findViewById(R.id.marker_image_view);
    View fab         = findViewById(R.id.place_chosen_button);

    if (BuildConfig.USE_OSM) {
      findViewById(R.id.map_type_overlay).setVisibility(View.GONE);
    } else {
      findViewById(R.id.btnMapTypeNormal).setOnClickListener(v -> handleMapType("normal"));
      findViewById(R.id.btnMapTypeSatellite).setOnClickListener(v -> handleMapType("satellite"));
      findViewById(R.id.btnMapTypeTerrain).setOnClickListener(v -> handleMapType("terrain"));
    }

    ViewCompat.setBackgroundTintList(fab, ColorStateList.valueOf(getIntent().getIntExtra(KEY_CHAT_COLOR, Color.RED)));
    fab.setOnClickListener(v -> finishWithAddress());

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    {
      new LocationRetriever(this, this, location -> {
        setInitialLocation(new LatLng(location.getLatitude(), location.getLongitude()));
      }, () -> {
        Log.w(TAG, "Failed to get location.");
        setInitialLocation(PRIME_MERIDIAN);
      });
    } else {
      Log.w(TAG, "No location permissions");
      setInitialLocation(PRIME_MERIDIAN);
    }

    SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
    if (mapFragment == null) throw new AssertionError("No map fragment");

    mapFragment.getMapAsync(googleMap -> {
      setMap(googleMap);
      if (DynamicTheme.isDarkTheme(this)) {
        try {
          boolean success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));

          if (!success) {
            Log.e(TAG, "Style parsing failed.");
          }
        } catch (Resources.NotFoundException e) {
          Log.e(TAG, "Can't find style. Error: ", e);
        }
      }

      enableMyLocationButtonIfHaveThePermission(googleMap);

      googleMap.setOnCameraMoveStartedListener(i -> {
        markerImage.animate()
                   .translationY(-75f)
                   .setInterpolator(OVERSHOOT_INTERPOLATOR)
                   .setDuration(ANIMATION_DURATION)
                   .start();

        bottomSheet.hide();
      });

      googleMap.setOnCameraIdleListener(() -> {
        markerImage.animate()
                   .translationY(0f)
                   .setInterpolator(OVERSHOOT_INTERPOLATOR)
                   .setDuration(ANIMATION_DURATION)
                   .start();

        setCurrentLocation(googleMap.getCameraPosition().target);
      });
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  private void setInitialLocation(@NonNull LatLng latLng) {
    initialLocation = latLng;

    moveMapToInitialIfPossible();
  }

  private void setMap(GoogleMap googleMap) {
    this.googleMap = googleMap;

    setMapType(TextSecurePreferences.getGoogleMapType(this));
    moveMapToInitialIfPossible();
  }

  private void handleMapType(final String mapType) {
    setMapType(mapType);
    TextSecurePreferences.setGoogleMapType(this, mapType);
  }

  private void setMapType(final String mapType) {
    if (googleMap == null) {
      return;
    }
    switch (mapType) {
      case "hybrid":    googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);    break;
      case "satellite": googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE); break;
      case "terrain":   googleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);   break;
      case "none":      googleMap.setMapType(GoogleMap.MAP_TYPE_NONE);      break;
      default:          googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);    break;
    }
  }

  private void moveMapToInitialIfPossible() {
    if (initialLocation != null && googleMap != null) {
      Log.d(TAG, "Moving map to initial location");
      googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLocation, ZOOM));
      setCurrentLocation(initialLocation);
    }
  }

  private void setCurrentLocation(LatLng location) {
    currentLocation = location;
    bottomSheet.showLoading();
    lookupAddress(location);
  }

  private void finishWithAddress() {
    Intent      returnIntent = new Intent();
    String      address      = currentAddress != null && currentAddress.getAddressLine(0) != null ? currentAddress.getAddressLine(0) : "";
    AddressData addressData  = new AddressData(currentLocation.latitude, currentLocation.longitude, address);

    SimpleProgressDialog.DismissibleDialog dismissibleDialog = SimpleProgressDialog.showDelayed(this);
    MapView mapView = findViewById(R.id.map_view);
    SignalMapView.snapshot(currentLocation, mapView).addListener(new ListenableFuture.Listener<>() {
      @Override
      public void onSuccess(Bitmap result) {
        dismissibleDialog.dismiss();
        byte[] blob = BitmapUtil.toByteArray(result);
        Uri uri = BlobProvider.getInstance()
                              .forData(blob)
                              .withMimeType(MediaUtil.IMAGE_JPEG)
                              .createForSingleSessionInMemory();
        returnIntent.putExtra(ADDRESS_INTENT, addressData);
        returnIntent.setData(uri);
        setResult(RESULT_OK, returnIntent);
        finish();
      }

      @Override
      public void onFailure(ExecutionException e) {
        dismissibleDialog.dismiss();
        Log.e(TAG, "Failed to generate snapshot", e);
      }
    });
  }

  private void enableMyLocationButtonIfHaveThePermission(GoogleMap googleMap) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    {
      googleMap.setMyLocationEnabled(true);
    }
  }

  private void lookupAddress(@Nullable LatLng target) {
    if (addressLookup != null) {
      addressLookup.cancel(true);
    }
    addressLookup = new AddressLookup();
    addressLookup.execute(target);
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (addressLookup != null) {
      addressLookup.cancel(true);
    }
  }

  @SuppressLint("StaticFieldLeak")
  private class AddressLookup extends AsyncTask<LatLng, Void, Address> {

    private final String TAG = Log.tag(AddressLookup.class);
    private final Geocoder geocoder;

    AddressLookup() {
      geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
    }

    @Override
    protected Address doInBackground(LatLng... latLngs) {
      if (latLngs.length == 0) return null;
      LatLng latLng = latLngs[0];
      if (latLng == null) return null;
      try {
        List<Address> result = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
        return !result.isEmpty() ? result.get(0) : null;
      } catch (IOException e) {
        Log.w(TAG, "Failed to get address from location", e);
        return null;
      }
    }

    @Override
    protected void onPostExecute(@Nullable Address address) {
      currentAddress = address;
      if (address != null) {
        bottomSheet.showResult(address.getLatitude(), address.getLongitude(), addressToShortString(address), addressToString(address));
      } else {
        bottomSheet.hide();
      }
    }
  }

  private static @NonNull String addressToString(@Nullable Address address) {
    return address != null ? address.getAddressLine(0) : "";
  }

  private static @NonNull String addressToShortString(@Nullable Address address) {
    if (address == null) return "";

    String   addressLine = Objects.requireNonNullElse(address.getAddressLine(0), "");
    String[] split       = addressLine.split(",");

    if (split.length >= 3) {
      return split[1].trim() + ", " + split[2].trim();
    } else if (split.length == 2) {
      return split[1].trim();
    } else return split[0].trim();
  }
}
