package org.thoughtcrime.securesms.biometric;

import android.annotation.SuppressLint;
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
import androidx.appcompat.widget.AlertDialogLayout;
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

    biometricPromptInfo = new BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(R.string.BiometricDialogFragment__biometric_verification))
        .setSubtitle(getString(R.string.BiometricDialogFragment__please_confirm_it_is_really_you_to_access_molly))
        .setNegativeButtonText(getString(android.R.string.cancel))
        .setAllowedAuthenticators(BIOMETRIC_AUTHENTICATORS_ALLOWED)
        .setConfirmationRequired(false)
        .build();
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

    if (showPrompt) {
      requestAuthentication();
    }
  }

  @Override
  public void onPause() {
    super.onPause();

    if (biometricCallback != null) {
      biometricCallback.onHostActivityPaused();
    }

    biometricPrompt   = null;
    biometricCallback = null;
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog) {
    super.onDismiss(dialog);
    hidePrompt();
  }

  public void showPrompt(@NonNull FragmentActivity activity, @NonNull Listener listener) {
    Log.d(TAG, "showPrompt: canAuthenticate returned " + getAuthenticationStatus(activity));

    biometricCallback = new AuthenticationCallback(listener);
    biometricPrompt   = new BiometricPrompt(activity, biometricCallback);

    if (!isAdded()) {
      showPrompt = true;
      return;
    }

    showPrompt = false;
    requestAuthentication();
  }

  private void requestAuthentication() {
    ThreadUtil.postToMain(() -> {
      if (biometricPrompt != null) {
        biometricPrompt.authenticate(biometricPromptInfo);
      } else {
        Log.e(TAG, "biometricPrompt was null");
      }
    });
  }

  private void hidePrompt() {
    showPrompt = false;

    if (biometricPrompt != null) {
      biometricPrompt.cancelAuthentication();
    }
  }

  private int getAuthenticationStatus(@NonNull FragmentActivity activity) {
    return BiometricManager.from(activity).canAuthenticate(BIOMETRIC_AUTHENTICATORS_ALLOWED);
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
          hidePrompt();
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
        case BiometricPrompt.ERROR_HW_NOT_PRESENT:
        case BiometricPrompt.ERROR_NO_BIOMETRICS:
          isDismissed = listener.onNotEnrolled(getErrorMessage(errString));
          break;
        default:
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
