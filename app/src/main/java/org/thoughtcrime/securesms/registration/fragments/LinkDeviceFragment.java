package org.thoughtcrime.securesms.registration.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import org.signal.core.util.concurrent.LifecycleDisposable;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.qr.QrView;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

public final class LinkDeviceFragment extends LoggingFragment {

  private static final String TAG = Log.tag(LinkDeviceFragment.class);

  private int                   state;
  private View                  loadingSpinner;
  private QrView                qrImageView;
  private TextView              textCode;
  private View                  labelError;
  private View                  labelTimeout;
  private View                  retry;
  private RegistrationViewModel viewModel;

  private final LifecycleDisposable disposables = new LifecycleDisposable();

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_link_device, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    loadingSpinner = view.findViewById(R.id.linking_loading_spinner);
    qrImageView    = view.findViewById(R.id.linking_qr_image);
    textCode       = view.findViewById(R.id.linking_text_code);
    labelError     = view.findViewById(R.id.linking_error);
    labelTimeout   = view.findViewById(R.id.linking_timeout);
    retry          = view.findViewById(R.id.link_retry_button);

    disposables.bindTo(getViewLifecycleOwner().getLifecycle());
    viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

    TextSecurePreferences.setHasSeenNetworkConfig(requireContext(), true);
    AppDependencies.getNetworkManager().setNetworkEnabled(true);

    state = 0;
    attemptDeviceLink();

    retry.setOnClickListener(v -> attemptDeviceLink());

    textCode.setOnClickListener(v -> {
      Context context = requireContext();
      Util.copyToClipboard(context, textCode.getText());
      Toast.makeText(context, R.string.RegistrationActivity_copied_to_clipboard, Toast.LENGTH_SHORT).show();
    });
  }

  private void attemptDeviceLink() {
    if (state != 0) return;
    loadingSpinner.setVisibility(View.VISIBLE);
    labelError.setVisibility(View.GONE);
    labelTimeout.setVisibility(View.GONE);
    retry.setVisibility(View.GONE);
    state = 1;
    // This is hacky, but LifecycleDisposable doesn't expose a method to remove a single disposable,
    // and we will leak for each repeated attempt if we don't clear it before a retry
    disposables.clear();
    disposables.add(
      viewModel.requestDeviceLinkCode()
               .doOnSubscribe(unused -> SignalStore.account().setRegistered(false))
               .observeOn(AndroidSchedulers.mainThread())
               .flatMap(processor -> {
                 if (processor.hasResult()) {
                   state = 2;
                   String deviceLinkCode = processor.getResult().getDeviceLinkCode();
                   Log.d(TAG, "Displaying device link code: "+deviceLinkCode);
                   qrImageView.setQrText(deviceLinkCode);
                   textCode.setText(deviceLinkCode);
                   loadingSpinner.setVisibility(View.GONE);
                   qrImageView.setVisibility(View.VISIBLE);
                   textCode.setVisibility(View.VISIBLE);
                   return viewModel.attemptDeviceLink(processor.getResult());
                 }
                 return Single.just(processor.asNewDeviceRegistrationReturnProcessor());
               })
               .observeOn(AndroidSchedulers.mainThread())
               .subscribe(processor -> {
                 try {
                   processor.getResultOrThrow();
                   state = -1;
                   SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), LinkDeviceFragmentDirections.actionLinkDeviceComplete());
                 } catch (Throwable t) {
                   Log.w(TAG, "Failed to link device. Allowing user to retry", t);
                   loadingSpinner.setVisibility(View.GONE);
                   qrImageView.setVisibility(View.GONE);
                   textCode.setVisibility(View.GONE);
                   if (state == 2) {
                     labelTimeout.setVisibility(View.VISIBLE);
                   } else {
                     labelError.setVisibility(View.VISIBLE);
                   }
                   retry.setVisibility(View.VISIBLE);
                   state = 0;
                 }
               })
    );
  }
}
