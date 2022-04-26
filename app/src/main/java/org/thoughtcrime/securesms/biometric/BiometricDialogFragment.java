package org.thoughtcrime.securesms.biometric;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import org.signal.core.util.StringUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.whispersystems.signalservice.api.util.Preconditions;

import static androidx.biometric.BiometricPrompt.ERROR_CANCELED;
import static androidx.biometric.BiometricPrompt.ERROR_HW_NOT_PRESENT;
import static androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON;
import static androidx.biometric.BiometricPrompt.ERROR_NO_BIOMETRICS;
import static androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED;

public class BiometricDialogFragment extends DialogFragment {

  private static final String TAG = Log.tag(BiometricDialogFragment.class);

  private static final String KEY_FULL_SCREEN = "fullscreen";

  public static final int BIOMETRIC_AUTHENTICATORS_ALLOWED = BiometricManager.Authenticators.BIOMETRIC_WEAK;

  public static final String AUTH_REQUEST       = "im.molly.biometric.AUTH_REQUEST";
  public static final String AUTH_SUCCEEDED     = "im.molly.biometric.AUTH_SUCCEEDED";
  public static final String AUTH_ERROR_CODE    = "im.molly.biometric.AUTH_ERROR_CODE";
  public static final String AUTH_ERROR_MESSAGE = "im.molly.biometric.AUTH_ERROR_MESSAGE";

  private boolean fullScreen;

  private Listener        listener;
  private BiometricPrompt biometricPrompt;

  private boolean activityReady;

  public static void authenticate(@NonNull FragmentActivity activity, @NonNull Listener listener) {
    findOrAddFragment(activity, false).authenticate(listener);
  }

  public static BiometricDialogFragment findOrAddFragment(@NonNull FragmentActivity activity, boolean fullScreen) {
    final FragmentManager fragmentManager = activity.getSupportFragmentManager();

    BiometricDialogFragment fragment = (BiometricDialogFragment) fragmentManager.findFragmentByTag(TAG);
    if (fragment == null) {
      fragment = newInstance(fullScreen);
      fragment.showNow(fragmentManager, TAG);
    } else {
      Log.i(TAG, "Biometric dialog already being shown");
      Preconditions.checkArgument(fullScreen == fragment.getFullScreen());
    }

    return fragment;
  }

  public static BiometricDialogFragment newInstance(boolean fullScreen) {
    final Bundle args = new Bundle();
    args.putBoolean(KEY_FULL_SCREEN, fullScreen);

    final BiometricDialogFragment instance = new BiometricDialogFragment();
    instance.setArguments(args);
    return instance;
  }

  public boolean getFullScreen() {
    return fullScreen;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (getArguments() != null) {
      fullScreen = getArguments().getBoolean(KEY_FULL_SCREEN, false);
    }

    int theme = fullScreen ? R.style.Signal_DayNight_Dialog_FullScreen : 0;
    setStyle(STYLE_NO_INPUT, theme);
    setCancelable(!fullScreen);
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    if (fullScreen) {
      requireDialog().getWindow().setWindowAnimations(R.style.ScreenLockAnimation);
    }

    return null;
  }

  @Override
  public void onStart() {
    Log.v(TAG, "onStart()");
    super.onStart();

    activityReady = true;

    if (listener != null) {
      showBiometricPrompt();
    }
  }

  @Override
  public void onStop() {
    Log.v(TAG, "onStop()");
    super.onStop();

    activityReady = false;

    listener = null;
    if (biometricPrompt != null) {
      biometricPrompt.cancelAuthentication();
    }

    dismissAllowingStateLoss();
  }

  public void authenticate(@NonNull Listener listener) {
    this.listener = listener;

    if (!activityReady) {
      // Method called before the fragment was started
      return;
    }

    showBiometricPrompt();
  }

  private void showBiometricPrompt() {
    final BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(R.string.BiometricDialogFragment__biometric_verification))
        .setSubtitle(getString(R.string.BiometricDialogFragment__please_confirm_its_really_you_to_access_molly))
        .setNegativeButtonText(getString(android.R.string.cancel))
        .setAllowedAuthenticators(BIOMETRIC_AUTHENTICATORS_ALLOWED)
        .setConfirmationRequired(false)
        .build();

    getParentFragmentManager().setFragmentResultListener(
        AUTH_REQUEST, this,
        (key, result) -> {
          biometricPrompt = null;
          dispatchResult(result);
        });

    biometricPrompt = new BiometricPrompt(this, new BiometricPromptListener());
    biometricPrompt.authenticate(promptInfo);
  }

  private class BiometricPromptListener extends BiometricPrompt.AuthenticationCallback {
    @Override
    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
      Log.i(TAG, "onAuthenticationSucceeded");
      Bundle bundle = new Bundle();
      bundle.putBoolean(AUTH_SUCCEEDED, true);
      getParentFragmentManager().setFragmentResult(AUTH_REQUEST, bundle);
    }

    @Override
    public void onAuthenticationFailed() {
      Log.i(TAG, "onAuthenticationFailed");
      Bundle bundle = new Bundle();
      bundle.putBoolean(AUTH_SUCCEEDED, false);
      getParentFragmentManager().setFragmentResult(AUTH_REQUEST, bundle);
    }

    @Override
    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
      Log.w(TAG, "onAuthenticationError: " + errString);
      Bundle bundle = new Bundle();
      bundle.putBoolean(AUTH_SUCCEEDED, false);
      bundle.putInt(AUTH_ERROR_CODE, errorCode);
      bundle.putCharSequence(AUTH_ERROR_MESSAGE, errString);
      getParentFragmentManager().setFragmentResult(AUTH_REQUEST, bundle);
    }
  }

  private void dispatchResult(@NonNull Bundle result) {
    if (!activityReady) {
      Log.w(TAG, "Canceled!");
      return;
    }

    Activity activity = getActivity();
    if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
      Log.e(TAG, "No longer attached to a running activity. Listener not called.");
      dismissAllowingStateLoss();
      return;
    }

    boolean dismissible;

    if (result.getBoolean(AUTH_SUCCEEDED)) {
      dismissible = listener.onResult(true);
    } else if (!result.containsKey(AUTH_ERROR_CODE)) {
      dismissible = listener.onResult(false);
    } else {
      CharSequence err = result.getCharSequence(AUTH_ERROR_MESSAGE);
      if (err != null) {
        err = StringUtil.trimEndPeriodInSingleSentence(err);
      } else {
        err = "Unknown error";
      }
      switch (result.getInt(AUTH_ERROR_CODE)) {
        case ERROR_NEGATIVE_BUTTON:
        case ERROR_USER_CANCELED:
        case ERROR_CANCELED:
          dismissible = listener.onResult(false);
          break;
        case ERROR_HW_NOT_PRESENT:
        case ERROR_NO_BIOMETRICS:
          dismissible = listener.onNotEnrolled(err);
          break;
        default:
          dismissible = listener.onError(err);
      }
    }

    if (dismissible || !fullScreen) {
      dismissAllowingStateLoss();
    }
  }

  public interface Listener {
    boolean onResult(boolean authenticationSucceeded);

    boolean onError(@NonNull CharSequence errString);

    default boolean onNotEnrolled(@NonNull CharSequence errString) {
      return onError(errString);
    }
  }
}
