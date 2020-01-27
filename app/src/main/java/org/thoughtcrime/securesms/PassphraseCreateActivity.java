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

import android.os.Bundle;

import android.view.View;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * Activity for creating a user's local encryption passphrase.
 *
 * @author Moxie Marlinspike
 */

public class PassphraseCreateActivity extends PassphraseActivity {

  private static final String TAG = Log.tag(PassphraseCreateActivity.class);

  private View yesButton;
  private View noButton;

  public PassphraseCreateActivity() { }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setStatusBarColor(getResources().getColor(R.color.signal_primary_dark));
    setTheme(R.style.TextSecure_LightIntroTheme);

    setContentView(R.layout.create_passphrase_activity);

    initializeResources();
  }

  private void initializeResources() {
    yesButton = ViewUtil.findById(this,R.id.yes_button);
    noButton  = ViewUtil.findById(this,R.id.no_button);

    yesButton.setOnClickListener(v -> onButtonClicked(true));
    noButton.setOnClickListener(v -> onButtonClicked(false));
  }

  private void onButtonClicked(boolean enabled) {
    if (MasterSecretUtil.isPassphraseInitialized(this)) {
      Log.w(TAG, "Passphrase already initialized!");
      return;
    }

    if (enabled) {
      ChangePassphraseDialogFragment dialog = ChangePassphraseDialogFragment.newInstance(ChangePassphraseDialogFragment.MODE_ENABLE);
      dialog.setMasterSecretChangedListener(this::generateSecrets);
      dialog.show(getSupportFragmentManager(), "ChangePassphraseDialogFragment");
    } else {
      generateSecrets(MasterSecretUtil.generateMasterSecret(this, MasterSecretUtil.UNENCRYPTED_PASSPHRASE));
    }

    TextSecurePreferences.setPassphraseLockEnabled(this, enabled);
  }

  private void generateSecrets(MasterSecret masterSecret) {
    MasterSecretUtil.generateAsymmetricMasterSecret(PassphraseCreateActivity.this, masterSecret);
    setMasterSecret(masterSecret);
  }
}
