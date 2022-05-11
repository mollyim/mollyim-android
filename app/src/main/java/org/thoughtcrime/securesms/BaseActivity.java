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
    super.onPostResume();

    boolean screenLocked = ScreenLockController.getLockScreenAtStart();

    if (!screenLocked) {
      BiometricDialogFragment.cancelFullScreenAuthentication(this);
      ScreenLockController.unBlankScreen();
    }

    logEvent("onPostResume(" + screenLocked + ")");
    onPostResume(screenLocked);

    if (screenLocked && useScreenLock() && !isFinishing()) {
      showBiometricPromptForAuthentication();
    }
  }

  protected void onPostResume(boolean screenLocked) {}

  public boolean useScreenLock() {
    return true;
  }

  @Override
  public void onAttachedToWindow() {
    boolean alwaysVisible = !useScreenLock();
    ScreenLockController.setShowWhenLocked(getWindow(), alwaysVisible);
    if (alwaysVisible) {
      logEvent("Window excluded from screen lock");
    }

    if (ScreenLockController.getLockScreenAtStart()) {
      logEvent("Screen locked");
      ScreenLockController.blankScreen();
    }
  }

  private void showBiometricPromptForAuthentication() {
    logEvent("Prompt for biometric authentication");

    BiometricDialogFragment.authenticate(
        this, true,
        new BiometricDialogFragment.Listener() {
          @Override
          public boolean onSuccess() {
            ScreenLockController.setLockScreenAtStart(false);
            ScreenLockController.unBlankScreen();
            onPostResume(false);
            return true;
          }

          @Override
          public boolean onFailure(boolean canceledFromUser) {
            if (!ScreenLockController.getLockScreenAtStart()) {
              logEvent("Screen already unlocked by another activity. Ignore the canceled event.");
              return true;
            }
            if (canceledFromUser) {
              logEvent("Authentication canceled from user");
              moveTaskToBack(true);
              return true;
            }
            return false;
          }

          @Override
          public boolean onError(@NonNull CharSequence errString) {
            Toast.makeText(BaseActivity.this, errString, Toast.LENGTH_LONG).show();
            finishAndRemoveTask();
            return false;
          }

          @Override
          public boolean onNotEnrolled(@NonNull CharSequence errString) {
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
  }

  @Override
  protected void onStart() {
    logEvent("onStart()");
    super.onStart();

    if (ScreenLockController.shouldLockScreenAtStart()) {
      logEvent("Screen locked");
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

  public void initializeScreenshotSecurity() {
    boolean forceFlag = ScreenLockController.getAlwaysSetSecureFlagOnResume();
    if (forceFlag || KeyCachingService.isLocked() || TextSecurePreferences.isScreenSecurityEnabled(this)) {
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
