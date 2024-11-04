package org.thoughtcrime.securesms;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.biometric.BiometricDialogFragment;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.AppStartup;
import org.thoughtcrime.securesms.util.ConfigurationUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.WindowUtil;
import org.thoughtcrime.securesms.util.dynamiclanguage.DynamicLanguageContextWrapper;

import java.util.List;
import java.util.Objects;

/**
 * Base class for all activities. The vast majority of activities shouldn't extend this directly.
 * Instead, they should extend {@link PassphraseRequiredActivity} so they're protected by
 * screen lock.
 */
public abstract class BaseActivity extends AppCompatActivity {
  private static final String TAG = Log.tag(BaseActivity.class);

  private boolean lockScreenState;
  private boolean biometricPromptLaunched;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    AppStartup.getInstance().onCriticalRenderEventStart();
    logEvent("onCreate()");
    super.onCreate(savedInstanceState);
    AppStartup.getInstance().onCriticalRenderEventEnd();
  }

  @Override
  protected void onResume() {
    super.onResume();
    WindowUtil.initializeScreenshotSecurity(this, getWindow());
  }

  @Override
  protected void onPause() {
    super.onPause();

    if (getWindow() != null && ScreenLockController.getAutoLock()) {
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
  }

  @Override
  protected void onPostResume() {
    super.onPostResume();

    boolean screenLocked = ScreenLockController.getLockScreenAtStart();

    if (!screenLocked && lockScreenState) {
      logEvent("onPostResume: screen no longer locked");
      BiometricDialogFragment.cancelAuthentication(this);
      onPostResume(false);
    } else {
      onPostResume(screenLocked);
    }

    if (Build.VERSION.SDK_INT < 29) {
      onTopResumedActivityChanged(true);
    }
  }

  @Override
  public void onTopResumedActivityChanged(boolean isTopResumedActivity) {
    boolean isTopOfTask = isTopResumedActivity && !isFinishing();
    if (isTopOfTask && lockScreenState && useScreenLock()) {
      showBiometricPromptForAuthentication(!hasShowWhenLockedWindow());
    }
  }

  protected void onPostResume(boolean screenLocked) {
    this.lockScreenState = screenLocked;

    if (!screenLocked) {
      ScreenLockController.unBlankScreen();
      if (getWindow() != null) {
        onWindowAttributesChanged(getWindow().getAttributes());
      }
    }
  }

  protected void onAuthenticationCancel() {
    if (!moveTaskToBack(true)) {
      logError("Failed to move to the back of the activity stack");
      finishAndRemoveTask();
    }
  }

  public boolean useScreenLock() {
    return true;
  }

  @Override
  public void onWindowAttributesChanged(WindowManager.LayoutParams params) {
    int noInputFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    if (lockScreenState && useScreenLock()) {
      params.flags |= noInputFlags;
    } else {
      params.flags &= ~noInputFlags;
    }
    super.onWindowAttributesChanged(params);
  }

  @Override
  public void onAttachedToWindow() {
    boolean alwaysVisible = !useScreenLock() || hasShowWhenLockedWindow();
    if (alwaysVisible) {
      logEvent("onAttachedToWindow: window excluded from screen lock");
      ScreenLockController.showWhenLocked(getWindow());
    } else {
      ScreenLockController.hideWhenLocked(getWindow());
    }

    if (lockScreenState) {
      ScreenLockController.blankScreen();
    }
  }

  private void showBiometricPromptForAuthentication(boolean fullScreen) {
    if (biometricPromptLaunched) {
      return;
    }

    logEvent("Prompting for biometrics: fullScreen=" + fullScreen);

    final BiometricDialogFragment dialog = BiometricDialogFragment.findOrAddFragment(
        getSupportFragmentManager(), fullScreen);

    dialog.setCancelable(false);
    dialog.showPrompt(
        this,
        new BiometricDialogFragment.Listener() {
          @Override
          public boolean onSuccess() {
            biometricPromptLaunched = false;
            ScreenLockController.setLockScreenAtStart(false);
            onPostResume(false);
            return true;
          }

          @Override
          public boolean onCancel(boolean fromUser) {
            biometricPromptLaunched = false;
            if (!ScreenLockController.getLockScreenAtStart()) {
              logEvent("Screen already unlocked by another activity. Ignore the cancel event.");
              return onSuccess();
            }
            if (fromUser) {
              logEvent("Authentication canceled from user");
              onAuthenticationCancel();
            }
            return false;
          }

          @Override
          public boolean onError(@NonNull CharSequence errString) {
            biometricPromptLaunched = false;
            Toast.makeText(BaseActivity.this, errString, Toast.LENGTH_LONG).show();
            finishAndRemoveTask();
            return false;
          }

          @Override
          public boolean onNotEnrolled(@NonNull CharSequence errString) {
            biometricPromptLaunched = false;
            logError("No biometrics. Screen lock will be disabled.");
            Toast.makeText(BaseActivity.this, errString, Toast.LENGTH_LONG).show();
            // If passphrase is available, PassphrasePromptActivity will disable the screen lock
            // after authenticating the user with the passphrase. Otherwise it cannot be helped.
            if (TextSecurePreferences.isPassphraseLockEnabled(BaseActivity.this)) {
              Intent lockIntent = new Intent(BaseActivity.this, KeyCachingService.class);
              lockIntent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
              startService(lockIntent);
            } else {
              TextSecurePreferences.setBiometricScreenLockEnabled(BaseActivity.this, false);
              ScreenLockController.enableAutoLock(false);
              onSuccess();
              recreate();
            }
            return false;
          }
        }
    );

    biometricPromptLaunched = true;
  }

  @Override
  protected void onStart() {
    logEvent("onStart()");
    super.onStart();

    if (ScreenLockController.shouldLockScreenAtStart()) {
      logEvent("onStart: screen locked");
      ScreenLockController.blankScreen();
    }
  }

  @Override
  protected void onStop() {
    logEvent("onStop()");
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    logEvent("onDestroy()");
    super.onDestroy();
  }

  private boolean hasShowWhenLockedWindow() {
    return (getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED) != 0;
  }

  protected void setExcludeFromRecents(boolean exclude) {
    ActivityManager.AppTask task = getTask();
    if (task != null) {
      task.setExcludeFromRecents(exclude);
    }
  }

  private @Nullable ActivityManager.AppTask getTask() {
    int taskId = getTaskId();

    List<ActivityManager.AppTask> tasks = ServiceUtil.getActivityManager(this).getAppTasks();
    for (ActivityManager.AppTask task : tasks) {
      if (task.getTaskInfo().id == taskId) {
        return task;
      }
    }
    return null;
  }

  @Override
  protected void attachBaseContext(@NonNull Context newBase) {
    super.attachBaseContext(newBase);

    Configuration configuration      = new Configuration(newBase.getResources().getConfiguration());
    int           appCompatNightMode = getDelegate().getLocalNightMode() != AppCompatDelegate.MODE_NIGHT_UNSPECIFIED ? getDelegate().getLocalNightMode()
                                                                                                                     : AppCompatDelegate.getDefaultNightMode();

    configuration.uiMode      = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | mapNightModeToConfigurationUiMode(newBase, appCompatNightMode);
    configuration.orientation = Configuration.ORIENTATION_UNDEFINED;

    applyOverrideConfiguration(configuration);
  }

  @Override
  public void applyOverrideConfiguration(@NonNull Configuration overrideConfiguration) {
    DynamicLanguageContextWrapper.prepareOverrideConfiguration(this, overrideConfiguration);
    super.applyOverrideConfiguration(overrideConfiguration);
  }

  private void logEvent(@NonNull String event) {
    Log.d(TAG, "[" + Log.tag(getClass()) + "] " + event);
  }

  private void logError(@NonNull String event) {
    Log.e(TAG, "[" + Log.tag(getClass()) + "] " + event);
  }

  public final @NonNull ActionBar requireSupportActionBar() {
    return Objects.requireNonNull(getSupportActionBar());
  }

  private static int mapNightModeToConfigurationUiMode(@NonNull Context context, @AppCompatDelegate.NightMode int appCompatNightMode) {
    if (appCompatNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
      return Configuration.UI_MODE_NIGHT_YES;
    } else if (appCompatNightMode == AppCompatDelegate.MODE_NIGHT_NO) {
      return Configuration.UI_MODE_NIGHT_NO;
    }
    return ConfigurationUtil.getNightModeConfiguration(context.getApplicationContext());
  }
}
