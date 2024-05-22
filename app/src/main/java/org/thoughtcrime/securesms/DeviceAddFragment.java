package org.thoughtcrime.securesms;

import android.Manifest;
import android.animation.Animator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import org.signal.qr.QrScannerView;
import org.signal.qr.kitkat.ScanListener;
import org.thoughtcrime.securesms.mediasend.camerax.CameraXModelBlocklist;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.signal.core.util.concurrent.LifecycleDisposable;
import org.thoughtcrime.securesms.util.ViewUtil;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public class DeviceAddFragment extends LoggingFragment {

  private final LifecycleDisposable lifecycleDisposable = new LifecycleDisposable();

  private ImageView     devicesImage;
  private ScanListener  scanListener;
  private QrScannerView scannerView;
  private boolean       scannerStarted;

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
    ViewGroup container = ViewUtil.inflate(inflater, viewGroup, R.layout.device_add_fragment);

    // MOLLY: Set listener for button "Link without scanning"
    container.findViewById(R.id.device_add_enter_link).setOnClickListener(v -> {
      if (scanListener != null) {
        scanListener.onNoScan();
      }
    });

    this.scannerView = container.findViewById(R.id.scanner);
    this.devicesImage = container.findViewById(R.id.devices);
    ViewCompat.setTransitionName(devicesImage, "devices");

    container.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
      @Override
      public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                 int oldLeft, int oldTop, int oldRight, int oldBottom)
      {
        v.removeOnLayoutChangeListener(this);

        Animator reveal = ViewAnimationUtils.createCircularReveal(v, right, bottom, 0, (int) Math.hypot(right, bottom));
        reveal.setInterpolator(new DecelerateInterpolator(2f));
        reveal.setDuration(800);
        reveal.start();
      }
    });

    return container;
  }

  void startScanner() {
    if (scannerStarted) {
      return;
    }

    scannerStarted = true;
    scannerView.start(getViewLifecycleOwner(), CameraXModelBlocklist.isBlocklisted());

    lifecycleDisposable.bindTo(getViewLifecycleOwner());

    Disposable qrDisposable = scannerView
        .getQrData()
        .distinctUntilChanged()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(qrData -> {
          if (scanListener != null) {
            scanListener.onQrDataFound(qrData);
          }
        });

    lifecycleDisposable.add(qrDisposable);
  }

  @Override
  public void onStart() {
    super.onStart();
    Permissions.with(requireActivity())
               .request(Manifest.permission.CAMERA)
               .ifNecessary()
               .withRationaleDialog(getString(R.string.CameraXFragment_allow_access_camera), getString(R.string.CameraXFragment_to_scan_qr_code_allow_camera), R.drawable.symbol_camera_24)
               .withPermanentDenialDialog(getString(R.string.DeviceActivity_signal_needs_the_camera_permission_in_order_to_scan_a_qr_code), null, R.string.CameraXFragment_allow_access_camera, R.string.CameraXFragment_to_scan_qr_codes, getParentFragmentManager())
               .onAllGranted(this::startScanner)
               .onAnyDenied(() -> Toast.makeText(requireContext(), R.string.CameraXFragment_signal_needs_camera_access_scan_qr_code, Toast.LENGTH_LONG).show())
               .execute();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    MenuItem switchCamera = ((DeviceActivity) requireActivity()).getCameraSwitchItem();

    if (switchCamera != null) {
      switchCamera.setVisible(true);
      switchCamera.setOnMenuItemClickListener(v -> {
        scannerView.toggleCamera();
        return true;
      });
    }
  }

  public ImageView getDevicesImage() {
    return devicesImage;
  }

  public void setScanListener(ScanListener scanListener) {
    this.scanListener = scanListener;
  }
}
