package org.thoughtcrime.securesms;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
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

  private boolean screenLocked;
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
    initializeScreenshotSecurity();
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
    logEvent("onPostResume()");
    super.onPostResume();

    onPostResume(screenLocked);

    if (Build.VERSION.SDK_INT < 29) {
      onTopResumedActivityChanged(true);
    }
  }

  protected void onPostResume(boolean screenLocked) {}

  public boolean useScreenLock() {
    return true;
  }

  @Override
  public void onTopResumedActivityChanged(boolean isTopResumedActivity) {
    boolean isTopOfTask = isTopResumedActivity && !isFinishing();

    if (isTopOfTask && screenLocked && !biometricPromptLaunched && useScreenLock()) {
      biometricPromptLaunched = true;
      unlockScreenByBiometricPrompt();
    }
  }

  @Override
  public void onAttachedToWindow() {
    if (useScreenLock()) {
      ScreenLockController.setShowWhenLocked(getWindow(), false);
    } else {
      logEvent("Window excluded from screen lock");
      ScreenLockController.setShowWhenLocked(getWindow(), true);
    }
    if (screenLocked) {
      ScreenLockController.blankScreen();
    }
  }

  private void unlockScreenByBiometricPrompt() {
    logEvent("Prompt for biometric authentication");

    BiometricDialogFragment fragment = BiometricDialogFragment.findOrAddFragment(this, true);
    if (fragment.getDialog() == null) {
      logError("Biometric prompt has gone away");
      return;
    }

    ScreenLockController.setShowWhenLocked(fragment.requireDialog().getWindow(), true);
    fragment.authenticate(
        new BiometricDialogFragment.Listener() {
          @Override
          public boolean onResult(boolean authenticationSucceeded) {
            if (authenticationSucceeded) {
              unlockScreen();
            } else {
              moveTaskToBack(true);
            }
            biometricPromptLaunched = false;
            return true;
          }

          @Override
          public boolean onError(@NonNull CharSequence errString) {
            Toast.makeText(BaseActivity.this, errString, Toast.LENGTH_LONG).show();
            finishAndRemoveTask();
            return false;
          }

          @Override
          public boolean onNotEnrolled(@NonNull CharSequence errString) {
            Toast.makeText(BaseActivity.this, errString, Toast.LENGTH_LONG).show();
            logError("No biometrics. Screen lock will be disabled.");
            if (TextSecurePreferences.isPassphraseLockEnabled(BaseActivity.this)) {
              // PassphrasePromptActivity will disable the screen lock after authenticating
              // the user with the passphrase.
              Intent lockIntent = new Intent(BaseActivity.this, KeyCachingService.class);
              lockIntent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
              startService(lockIntent);
              return false;
            } else {
              TextSecurePreferences.setBiometricScreenLockEnabled(BaseActivity.this, false);
              unlockScreen();
              return true;
            }
          }

          private void unlockScreen() {
            screenLocked = false;
            ScreenLockController.setLockScreenAtStart(false);
            ScreenLockController.unBlankScreen();
            onPostResume(false);
          }
        }
    );
  }

  @Override
  protected void onStart() {
    logEvent("onStart()");
    if (ApplicationDependencies.isInitialized()) {
      ApplicationDependencies.getShakeToReport().registerActivity(this);
    }
    super.onStart();

    screenLocked = ScreenLockController.shouldLockScreenAtStart();
    if (screenLocked) {
      ScreenLockController.blankScreen();
    } else {
      ScreenLockController.unBlankScreen();
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

  private void initializeScreenshotSecurity() {
    if (KeyCachingService.isLocked() || TextSecurePreferences.isScreenSecurityEnabled(this)) {
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    } else {
      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
  }

  @RequiresApi(21)
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

  // MOLLY: Workaround for WebView crashes in devices API 21-25 with outdated WebView
  // https://issuetracker.google.com/issues/141351441
  // Not reproducible in Signal because RegistrationNavigationActivity does not inherit
  // this class and thus does not override the configuration.
  @Override
  public AssetManager getAssets() {
    if (Build.VERSION.SDK_INT <= 25) {
      return getResources().getAssets();  // Ignore overridden configuration
    } else {
      return super.getAssets();
    }
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
