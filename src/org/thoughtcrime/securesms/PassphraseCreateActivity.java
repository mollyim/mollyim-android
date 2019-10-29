/**
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

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;

import android.view.View;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.VersionTracker;
import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * Activity for creating a user's local encryption passphrase.
 *
 * @author Moxie Marlinspike
 */

public class PassphraseCreateActivity extends PassphraseActivity {

  private static final String TAG = Log.tag(PassphraseCreateActivity.class);

  public PassphraseCreateActivity() { }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setStatusBarColor(getResources().getColor(R.color.signal_primary_dark));

    setContentView(R.layout.create_passphrase_activity);

    initializeResources();
  }

  private void initializeResources() {
    View yesButton = ViewUtil.findById(this,R.id.yes_button);
    View noButton  = ViewUtil.findById(this,R.id.no_button);

    yesButton.setOnClickListener(v -> onButtonClicked(true));
    noButton.setOnClickListener(v -> onButtonClicked(false));
  }

  private void onButtonClicked(boolean passphraseEnabled) {
    if (MasterSecretUtil.isPassphraseInitialized(this)) {
      Log.w(TAG, "Passphrase already initialized!");
      return;
    }

    if (passphraseEnabled) {
      ChangePassphraseDialogFragment dialog = ChangePassphraseDialogFragment.newInstance(ChangePassphraseDialogFragment.MODE_ENABLE);

      dialog.setMasterSecretChangedListener(masterSecret -> {
        TextSecurePreferences.setPassphraseLockEnabled(this, true);
        new SecretGenerator(masterSecret).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
      });

      dialog.show(getSupportFragmentManager(), "ChangePassphraseDialogFragment");
    } else {
      MasterSecret masterSecret = MasterSecretUtil.generateMasterSecret(this, MasterSecretUtil.UNENCRYPTED_PASSPHRASE);

      new SecretGenerator(masterSecret).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  @SuppressLint("StaticFieldLeak")
  private class SecretGenerator extends AsyncTask<Void, Void, Void> {
    private final MasterSecret masterSecret;

    SecretGenerator(MasterSecret masterSecret) {
      this.masterSecret = masterSecret;
    }

    @Override
    protected void onPreExecute() {
    }

    @Override
    protected Void doInBackground(Void... params) {
      MasterSecretUtil.generateAsymmetricMasterSecret(PassphraseCreateActivity.this, masterSecret);
      IdentityKeyUtil.generateIdentityKeys(PassphraseCreateActivity.this, masterSecret);
      VersionTracker.updateLastSeenVersion(PassphraseCreateActivity.this);

      TextSecurePreferences.setLastExperienceVersionCode(PassphraseCreateActivity.this, Util.getCanonicalVersionCode());
      TextSecurePreferences.setReadReceiptsEnabled(PassphraseCreateActivity.this, true);
      TextSecurePreferences.setTypingIndicatorsEnabled(PassphraseCreateActivity.this, true);
      TextSecurePreferences.setHasSeenWelcomeScreen(PassphraseCreateActivity.this, false);

      return null;
    }

    @Override
    protected void onPostExecute(Void param) {
      setMasterSecret(masterSecret);
    }
  }
}
