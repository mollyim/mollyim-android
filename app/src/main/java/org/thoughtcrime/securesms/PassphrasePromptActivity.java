/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;

import com.dd.CircularProgressButton;
import com.google.android.material.textfield.TextInputLayout;

import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.biometric.BiometricDialogFragment;
import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.UnrecoverableKeyException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.SupportEmailUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.Arrays;

import static com.google.android.material.textfield.TextInputLayout.END_ICON_NONE;
import static com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE;

/**
 * Activity that prompts for a user's passphrase.
 *
 * @author Moxie Marlinspike
 */
public class PassphrasePromptActivity extends PassphraseActivity {

  private static final String TAG = PassphrasePromptActivity.class.getSimpleName();

  private View                   passphraseAuthContainer;
  private View                   headerText;
  private TextInputLayout        passphraseLayout;
  private EditText               passphraseInput;
  private CircularProgressButton okButton;
  private View                   successView;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.i(TAG, "onCreate()");
    super.onCreate(savedInstanceState);

    requireSupportActionBar().setTitle("");

    setContentView(R.layout.prompt_passphrase_activity);
    initializeResources();

    setExcludeFromRecents(true);
  }

  @Override
  public void onResume() {
    super.onResume();
    passphraseInput.requestFocus();

    // Manually lock the screen since the app lifecycle observer is not running yet
    ScreenLockController.setLockScreenAtStart(ScreenLockController.getAutoLock());
  }

  @Override
  protected void onPause() {
    super.onPause();
  }

  @Override
  public boolean useScreenLock() {
    return false;
  }

  @Override
  public void onStop() {
    super.onStop();
    passphraseInput.setText("");
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.passphrase_prompt, menu);

    super.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    if (item.getItemId() == R.id.menu_submit_debug_logs) {
      handleLogSubmit();
      return true;
    } else if (item.getItemId() == R.id.menu_contact_support) {
      sendEmailToSupport();
      return true;
    }

    return false;
  }

  private void handleLogSubmit() {
    final Intent intent = new Intent(this, SubmitDebugLogActivity.class);
    startActivity(chainIntents(intent, getIntent().getParcelableExtra("next_intent")));
  }

  private static Intent chainIntents(@NonNull Intent sourceIntent, @Nullable Intent nextIntent) {
    if (nextIntent != null) {
      sourceIntent.putExtra("next_intent", nextIntent);
    }
    return sourceIntent;
  }

  private void onOkClicked(View view) {
    char[] passphrase = getEnteredPassphrase(passphraseInput);
    if (passphrase.length > 0) {
      setInputEnabled(false);
      if (ScreenLockController.getLockScreenAtStart()) {
        BiometricDialogFragment.authenticate(
            this, new BiometricDialogFragment.Listener() {
              @Override
              public boolean onResult(boolean authenticationSucceeded) {
                if (authenticationSucceeded) {
                  ScreenLockController.setLockScreenAtStart(false);
                  handlePassphrase(passphrase);
                } else {
                  setInputEnabled(true);
                  showFailure(false);
                }
                return true;
              }

              @Override
              public boolean onError(@NonNull CharSequence errString) {
                setInputEnabled(true);
                showFailure(false);
                Toast.makeText(PassphrasePromptActivity.this, errString, Toast.LENGTH_LONG).show();
                return true;
              }

              @Override
              public boolean onNotEnrolled(@NonNull CharSequence errString) {
                Toast.makeText(PassphrasePromptActivity.this, errString, Toast.LENGTH_LONG).show();
                TextSecurePreferences.setBiometricScreenLockEnabled(PassphrasePromptActivity.this, false);
                ScreenLockController.enableAutoLock(false);
                handlePassphrase(passphrase);
                return true;
              }
            }
        );
      } else {
        handlePassphrase(passphrase);
      }
    } else {
      showFailure(true);
    }
  }

  private void handlePassphrase(char[] passphrase) {
    SetMasterSecretTask task = new SetMasterSecretTask(passphrase);
    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private char[] getEnteredPassphrase(final EditText editText) {
    int len = editText.length();

    char[] passphrase = new char[len];
    if (editText.getText() != null) {
      editText.getText().getChars(0, len, passphrase, 0);
    }
    return passphrase;
  }

  private void initializeResources() {
    passphraseAuthContainer = findViewById(R.id.password_auth_container);
    headerText              = findViewById(R.id.header_text);
    passphraseLayout        = findViewById(R.id.passphrase_layout);
    passphraseInput         = findViewById(R.id.passphrase_input);
    okButton                = findViewById(R.id.ok_button);
    successView             = findViewById(R.id.success);

    SpannableString hint = new SpannableString(getString(R.string.PassphrasePromptActivity_enter_passphrase));
    hint.setSpan(new RelativeSizeSpan(0.9f), 0, hint.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
    hint.setSpan(new TypefaceSpan("sans-serif-light"), 0, hint.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    passphraseInput.setHint(hint);
    passphraseInput.setOnEditorActionListener(new PassphraseActionListener());

    okButton.setOnClickListener(this::onOkClicked);
  }

  private void sendEmailToSupport() {
    String body = SupportEmailUtil.generateSupportEmailBody(this,
                                                            R.string.PassphrasePromptActivity_signal_android_lock_screen,
                                                            null,
                                                            null);
    CommunicationActions.openEmail(this,
                                   SupportEmailUtil.getSupportEmailAddress(this),
                                   getString(R.string.PassphrasePromptActivity_signal_android_lock_screen),
                                   body);
  }

  private class PassphraseActionListener implements TextView.OnEditorActionListener {
    @Override
    public boolean onEditorAction(TextView exampleView, int actionId, KeyEvent keyEvent) {
      if ((keyEvent == null && actionId == EditorInfo.IME_ACTION_DONE) ||
          (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN &&
           (actionId == EditorInfo.IME_NULL)))
      {
        if (okButton.isClickable()) {
          okButton.performClick();
        }
        return true;
      }
      return keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_UP
             && actionId == EditorInfo.IME_NULL;
    }
  }

  private void setInputEnabled(boolean enabled) {
    if (enabled) {
      passphraseInput.selectAll();
    } else {
      passphraseInput.clearFocus();
    }
    passphraseLayout.setEnabled(enabled);
    passphraseLayout.setEndIconMode(enabled ? END_ICON_PASSWORD_TOGGLE : END_ICON_NONE);
    okButton.setClickable(enabled);
    headerText.setEnabled(enabled);
  }

  private void showProgress(float x) {
    double y = 1 + Math.pow(1 - x, 1.5) * -1;
    okButton.setProgress((int) (y * 99));
  }

  private void showFailure(boolean focusOnInput) {
    if (focusOnInput && passphraseInput.requestFocus()) {
      InputMethodManager imm = ServiceUtil.getInputMethodManager(this);
      imm.showSoftInput(passphraseInput, InputMethodManager.SHOW_IMPLICIT);
    }

    TranslateAnimation shake = new TranslateAnimation(0, 30, 0, 0);
    shake.setDuration(50);
    shake.setRepeatCount(7);
    passphraseAuthContainer.startAnimation(shake);
  }

  private void onSuccessfulPassphrase(MasterSecret masterSecret) {
    int shortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);

    successView.setAlpha(0f);
    successView.setVisibility(View.VISIBLE);
    successView.animate().alpha(1f).setDuration(shortAnimationDuration).setListener(null);

    okButton.animate().alpha(0f).setDuration(shortAnimationDuration).setListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        ThreadUtil.postToMain(() -> {
          setMasterSecret(masterSecret);
          launchRoutedActivity();
        });
      }
    });

    setExcludeFromRecents(false);
  }

  @SuppressLint("StaticFieldLeak")
  private class SetMasterSecretTask extends AsyncTask<Void, Float, MasterSecret> {

    private final char[] passphrase;

    private CountDownTimer progressTimer;

    SetMasterSecretTask(char[] passphrase) {
      this.passphrase = passphrase;
    }

    @Override
    protected void onPreExecute() {
      initializeProgressTimer();
    }

    @Override
    protected MasterSecret doInBackground(Void... voids) {
      progressTimer.start();

      MasterSecret masterSecret = null;
      try {
        masterSecret = MasterSecretUtil.getMasterSecret(getApplicationContext(), passphrase);
      } catch (InvalidPassphraseException | UnrecoverableKeyException e) {
        Log.d(TAG, e);
      }

      Arrays.fill(passphrase, (char) 0);
      progressTimer.cancel();

      return masterSecret;
    }

    @Override
    protected void onProgressUpdate(Float... values) {
      showProgress(values[0]);
    }

    @Override
    protected void onPostExecute(MasterSecret masterSecret) {
      if (masterSecret != null) {
        onSuccessfulPassphrase(masterSecret);
      } else {
        setInputEnabled(true);
        showProgress(0f);
        showFailure(true);
      }
    }

    private void initializeProgressTimer() {
      long countdown = MasterSecretUtil.getKdfElapsedTimeMillis(getApplicationContext()) + 250;

      progressTimer = new CountDownTimer(countdown, 100) {
        @Override
        public void onTick(long millisUntilFinished) {
          publishProgress(1 - (millisUntilFinished / (float) countdown));
        }

        @Override
        public void onFinish() {
          publishProgress(1f);
        }
      };
    }
  }
}
