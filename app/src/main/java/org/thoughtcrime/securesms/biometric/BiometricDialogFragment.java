package org.thoughtcrime.securesms.biometric;

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
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.whispersystems.signalservice.api.util.Preconditions;

public class BiometricDialogFragment extends DialogFragment implements View.OnAttachStateChangeListener {

  private static final String TAG = Log.tag(BiometricDialogFragment.class);

  private static final String KEY_FULL_SCREEN = "fullscreen";

  public static final int BIOMETRIC_AUTHENTICATORS_ALLOWED = BiometricManager.Authenticators.BIOMETRIC_WEAK;

  private static int    alertTitleId;
  private static String alertTitleText;

  private boolean fullScreen;
  private boolean showPrompt;
  private boolean isWaitingResult;

  private BiometricPrompt            biometricPrompt;
  private BiometricPrompt.PromptInfo biometricPromptInfo;

  private Listener listener;

  public static void authenticate(@NonNull FragmentActivity activity, @NonNull Listener listener) {
    findOrAddFragment(activity, false).authenticate(listener);
  }

  public static void authenticate(@NonNull FragmentActivity activity, boolean fullscreen, @NonNull Listener listener) {
    findOrAddFragment(activity, fullscreen).authenticate(listener);
  }

  public static void cancelFullScreenAuthentication(@NonNull FragmentActivity activity) {
    BiometricDialogFragment fragment = findFragment((activity.getSupportFragmentManager()));
    if (fragment != null && fragment.fullScreen) {
      fragment.dismissAllowingStateLoss();
    }
  }

  private static BiometricDialogFragment findOrAddFragment(@NonNull FragmentActivity activity, boolean fullScreen) {
    final FragmentManager fragmentManager = activity.getSupportFragmentManager();

    BiometricDialogFragment fragment = findFragment((fragmentManager));
    if (fragment == null) {
      fragment = newInstance(fullScreen);
      fragment.show(fragmentManager, TAG);
    } else {
      Log.i(TAG, "Biometric dialog already being shown");
      Preconditions.checkArgument(fullScreen == fragment.fullScreen);
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

  public static boolean isDialogAttachedTo(@Nullable View container) {
    if (!(container instanceof ViewGroup)) {
      return false;
    }
    View layout = ((ViewGroup) container).getChildAt(0);
    if (layout != null && layout.getId() == R.id.biometric_dialog_background) {
      return true;
    } else if (isUsingFingerprintDialog() && (layout instanceof AlertDialogLayout)) {
      if (alertTitleId == 0) {
        alertTitleId   = container.getResources().getIdentifier("alertTitle", "id", container.getContext().getPackageName());
        alertTitleText = container.getResources().getText(R.string.BiometricDialogFragment__biometric_verification).toString();
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

    int theme = fullScreen ? R.style.Signal_DayNight_Dialog_FullScreen : 0;
    setStyle(STYLE_NO_INPUT, theme);
    setCancelable(!fullScreen);

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
    View view = inflater.inflate(R.layout.biometric_dialog_background, container, false);

    final ImageView logo = view.findViewById(R.id.logo);

    if (fullScreen) {
      view.setVisibility(View.VISIBLE);
      requireDialog().getWindow().setWindowAnimations(R.style.ScreenLockAnimation);

      // Display the logo dimmed and in greyscale
      final ColorMatrix greyScaleMatrix = new ColorMatrix();
      greyScaleMatrix.setSaturation(0);
      logo.setAlpha(0.8f);
      logo.setColorFilter(new ColorMatrixColorFilter(greyScaleMatrix));

      if (isUsingFingerprintDialog()) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) logo.getLayoutParams();
        params.verticalBias /= 2;
        logo.setLayoutParams(params);
      }
    }

    view.addOnAttachStateChangeListener(this);

    return view;
  }

  @Override
  public void onDestroyView() {
    if (getView() != null) {
      getView().removeOnAttachStateChangeListener(this);
    }
    super.onDestroyView();
  }

  @Override
  public void onViewAttachedToWindow(View v) {
    if (showPrompt) {
      showPrompt = false;
      showBiometricPrompt();
    }
  }

  @Override
  public void onViewDetachedFromWindow(View v) {
  }

  @Override
  public void onPause() {
    super.onPause();

    if (isWaitingResult) {
      Log.i(TAG, "onPause(): aborting" + " (" + getActivityName() + ")");

      isWaitingResult = false;
      biometricPrompt.cancelAuthentication();
      biometricPrompt = null;

      // Make sure the host activity is notified. The fingerprint dialog
      // might not call back our listener.
      listener.onFailure(false);
      listener = null;
    }
  }

  public void authenticate(@NonNull Listener listener) {
    this.listener = listener;

    if (getView() == null || !getView().isAttachedToWindow()) {
      showPrompt = true;
      return;
    }

    showPrompt = false;
    showBiometricPrompt();
  }

  private void showBiometricPrompt() {
    isWaitingResult = true;
    biometricPrompt = new BiometricPrompt(this, new BiometricPromptListener(listener));
    biometricPrompt.authenticate(biometricPromptInfo);
  }

  private class BiometricPromptListener extends BiometricPrompt.AuthenticationCallback {

    private final Listener listener;

    private BiometricPromptListener(Listener listener) {
      this.listener = listener;
    }

    @Override
    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
      Log.i(TAG, "BiometricPrompt succeeded" + " (" + getActivityName() + ")");
      if (isWaitingResult) {
        dispatchResult(true);
      }
    }

    @Override
    public void onAuthenticationFailed() {
      Log.i(TAG, "BiometricPrompt failed" + " (" + getActivityName() + ")");
      if (isWaitingResult) {
        dispatchResult(false);
      }
    }

    @Override
    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
      Log.w(TAG, "BiometricPrompt error: " + errString + " (" + getActivityName() + ")");
      if (isWaitingResult) {
        dispatchError(errorCode, errString);
      }
    }

    private void dispatchResult(boolean succeeded) {
      boolean dismissible = succeeded ?
                            listener.onSuccess() :
                            listener.onFailure(false);

      if (dismissible) {
        isWaitingResult = false;
        dismissAllowingStateLoss();
      }
    }

    private void dispatchError(int errorCode, @NonNull CharSequence errString) {
      boolean dismissible;

      switch (errorCode) {
        case BiometricPrompt.ERROR_NEGATIVE_BUTTON:
        case BiometricPrompt.ERROR_USER_CANCELED:
          dismissible = listener.onFailure(true);
          break;
        case BiometricPrompt.ERROR_CANCELED:
          dismissible = listener.onFailure(false);
          break;
        case BiometricPrompt.ERROR_HW_NOT_PRESENT:
        case BiometricPrompt.ERROR_NO_BIOMETRICS:
          dismissible = listener.onNotEnrolled(getErrorMessage(errString));
          break;
        default:
          dismissible = listener.onError(getErrorMessage(errString));
      }

      if (dismissible) {
        isWaitingResult = false;
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
      return "Activity is null";
    } else {
      return activity.getLocalClassName();
    }
  }

  public interface Listener {
    boolean onSuccess();

    boolean onFailure(boolean canceledFromUser);

    boolean onError(@NonNull CharSequence errString);

    default boolean onNotEnrolled(@NonNull CharSequence errString) {
      return onError(errString);
    }
  }
}
