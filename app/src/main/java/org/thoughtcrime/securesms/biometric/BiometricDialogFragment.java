package org.thoughtcrime.securesms.biometric;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import org.signal.core.util.StringUtil;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ThemeUtil;

public class BiometricDialogFragment extends DialogFragment {

  private static final String TAG = Log.tag(BiometricDialogFragment.class);

  private static final String KEY_FULL_SCREEN = "fullscreen";

  public static final int BIOMETRIC_AUTHENTICATORS_ALLOWED = BiometricManager.Authenticators.BIOMETRIC_STRONG;

  private static int    alertTitleId;
  private static String alertTitleText;

  private boolean fullScreen;
  private boolean showPrompt;

  private BiometricPrompt            biometricPrompt;
  private BiometricPrompt.PromptInfo biometricPromptInfo;
  private AuthenticationCallback     biometricCallback;

  public static void authenticate(@NonNull FragmentActivity activity, @NonNull Listener listener) {
    findOrAddFragment(activity.getSupportFragmentManager(), false).showPrompt(activity, listener);
  }

  public static void cancelAuthentication(@NonNull FragmentActivity activity) {
    BiometricDialogFragment fragment = findFragment(activity.getSupportFragmentManager());
    if (fragment != null) {
      fragment.dismissAllowingStateLoss();
    }
  }

  public static BiometricDialogFragment findOrAddFragment(@NonNull FragmentManager fragmentManager, boolean fullScreen) {
    BiometricDialogFragment fragment = findFragment((fragmentManager));
    if (fragment == null) {
      fragment = newInstance(fullScreen);
      fragment.show(fragmentManager, TAG);
    } else {
      Log.i(TAG, "Biometric dialog already being shown");
    }

    return fragment;
  }

  private static @Nullable BiometricDialogFragment findFragment(@NonNull FragmentManager fragmentManager) {
    return (BiometricDialogFragment) fragmentManager.findFragmentByTag(TAG);
  }

  public static BiometricDialogFragment newInstance(boolean fullScreen) {
    final Bundle args = new Bundle();
    args.putBoolean(KEY_FULL_SCREEN, fullScreen);

    final BiometricDialogFragment instance = new BiometricDialogFragment();
    instance.setArguments(args);
    return instance;
  }

  @SuppressLint("DiscouragedApi")
  public static boolean isDialogViewAttachedTo(@Nullable View container) {
    if (!(container instanceof ViewGroup)) {
      return false;
    }
    View layout = ((ViewGroup) container).getChildAt(0);
    if (layout != null && layout.getId() == R.id.biometric_dialog_background) {
      return true;
    } else if (isUsingFingerprintDialog()) {
      if (alertTitleId == 0) {
        alertTitleId   = container.getResources().getIdentifier("alertTitle", "id", container.getContext().getPackageName());
        alertTitleText = container.getResources().getText(R.string.BiometricDialogFragment__biometric_verification).toString();
      }
      if (!(layout instanceof ViewGroup)) {
        return false;
      }
      TextView alertTitle = layout.findViewById(alertTitleId);
      return alertTitle != null && alertTitleText.contentEquals(alertTitle.getText());
    } else {
      return false;
    }
  }

  private static boolean isUsingFingerprintDialog() {
    return Build.VERSION.SDK_INT < 28;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (getArguments() != null) {
      fullScreen = getArguments().getBoolean(KEY_FULL_SCREEN, false);
    }

    int theme = fullScreen ? R.style.Signal_DayNight_BiometricDialog_FullScreen : 0;
    setStyle(STYLE_NO_FRAME, theme);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View backgroundView = inflater.inflate(R.layout.biometric_dialog_background, container, false);

    if (fullScreen) {
      final ImageView logo = backgroundView.findViewById(R.id.logo);

      if (!ThemeUtil.isDarkTheme(this.requireContext())) {
        // Display the logo in greyscale
        final ColorMatrix greyScaleMatrix = new ColorMatrix();
        greyScaleMatrix.setSaturation(0);
        logo.setColorFilter(new ColorMatrixColorFilter(greyScaleMatrix));
      }

      if (isUsingFingerprintDialog()) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) logo.getLayoutParams();
        params.verticalBias /= 3;
        logo.setLayoutParams(params);
      }
    }

    backgroundView.setVisibility(fullScreen ? View.VISIBLE : View.GONE);

    return backgroundView;
  }

  @Override
  public void onResume() {
    super.onResume();
    if (showPrompt) requestAuthentication();
  }

  @Override
  public void onPause() {
    super.onPause();
    biometricCallback.onHostActivityPaused();
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog) {
    super.onDismiss(dialog);
    cancelAuthentication();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    biometricPromptInfo = null;
    biometricPrompt     = null;
    biometricCallback   = null;
  }

  public void showPrompt(@NonNull FragmentActivity activity, @NonNull Listener listener) {
    int authenticators = BIOMETRIC_AUTHENTICATORS_ALLOWED;
    int authStatus     = checkBiometricAvailability(activity, authenticators);

    Log.d(TAG, "canAuthenticate returned " + authStatus);

    if (authStatus == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ||
        authStatus == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)
    {
      Log.w(TAG, "Biometric hardware unavailable, allowing device credentials as fallback");
      authenticators |= BiometricManager.Authenticators.BIOMETRIC_WEAK;
      authenticators |= BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    }

    BiometricPrompt.PromptInfo.Builder promptInfoBuilder =
        new BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.BiometricDialogFragment__biometric_verification))
            .setSubtitle(activity.getString(R.string.BiometricDialogFragment__please_confirm_it_is_really_you_to_access_molly))
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false);

    if ((authenticators & BiometricManager.Authenticators.DEVICE_CREDENTIAL) == 0) {
      promptInfoBuilder.setNegativeButtonText(activity.getString(android.R.string.cancel));
    }

    biometricCallback   = new AuthenticationCallback(listener);
    biometricPrompt     = new BiometricPrompt(activity, biometricCallback);
    biometricPromptInfo = promptInfoBuilder.build();

    showPrompt = true;

    if (isAdded()) {
      requestAuthentication();
    }
  }

  private void requestAuthentication() {
    ThreadUtil.postToMain(() -> {
      if (showPrompt) {
        showPrompt = false;
        biometricPrompt.authenticate(biometricPromptInfo);
      }
    });
  }

  private void cancelAuthentication() {
    if (showPrompt) {
      showPrompt = false;
      biometricPrompt.cancelAuthentication();
    }
  }

  static public int checkBiometricAvailability(@NonNull Context context, int allowedAuthenticators) {
    return BiometricManager.from(context).canAuthenticate(allowedAuthenticators);
  }

  private class AuthenticationCallback extends BiometricPrompt.AuthenticationCallback {

    private final Listener listener;

    private boolean isDismissed;

    private AuthenticationCallback(Listener listener) {
      this.listener = listener;
    }

    @Override
    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
      Log.i(TAG, "onAuthenticationSucceeded" + " (" + getActivityName() + ")");
      if (!isDismissed) {
        dispatchSuccess();
      }
    }

    @Override
    public void onAuthenticationFailed() {
      Log.i(TAG, "onAuthenticationFailed" + " (" + getActivityName() + ")");
    }

    @Override
    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
      Log.w(TAG, "onAuthenticationError: " + errString + " (" + getActivityName() + ")");
      if (!isDismissed) {
        dispatchError(errorCode, errString);
      }
    }

    public void onHostActivityPaused() {
      Log.i(TAG, "onHostActivityPaused" + " (" + getActivityName() + ")");
      if (!isDismissed) {
        // Make sure the host activity is notified. The fingerprint dialog
        // might not call our listener back.
        isDismissed = listener.onCancel(false);
        if (isDismissed) {
          dismissAllowingStateLoss();
        } else {
          cancelAuthentication();
        }
      }
    }

    private void dispatchSuccess() {
      isDismissed = listener.onSuccess();
      if (isDismissed) {
        dismissAllowingStateLoss();
      }
    }

    private void dispatchError(int errorCode, @NonNull CharSequence errString) {
      switch (errorCode) {
        case BiometricPrompt.ERROR_NEGATIVE_BUTTON:
        case BiometricPrompt.ERROR_USER_CANCELED:
          isDismissed = listener.onCancel(true);
          break;
        case BiometricPrompt.ERROR_CANCELED:
        case BiometricPrompt.ERROR_TIMEOUT:
          isDismissed = listener.onCancel(false);
          break;
        case BiometricPrompt.ERROR_NO_BIOMETRICS:
          isDismissed = listener.onNotEnrolled(getErrorMessage(errString));
          break;
        default:  // Handles hardware or unspecified errors
          isDismissed = listener.onError(getErrorMessage(errString));
      }

      if (isDismissed) {
        dismissAllowingStateLoss();
      }
    }

    private @NonNull CharSequence getErrorMessage(@NonNull CharSequence errString) {
      return StringUtil.trimEndPeriodInSingleSentence(errString);
    }
  }

  private @NonNull String getActivityName() {
    FragmentActivity activity = getActivity();
    if (activity == null) {
      return "null";
    } else {
      return activity.getLocalClassName();
    }
  }

  public interface Listener {
    boolean onSuccess();

    default boolean onCancel(boolean fromUser) {
      return true;
    }

    boolean onError(@NonNull CharSequence errString);

    default boolean onNotEnrolled(@NonNull CharSequence errString) {
      return onError(errString);
    }
  }
}
