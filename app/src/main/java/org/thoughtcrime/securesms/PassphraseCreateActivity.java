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

import androidx.core.view.WindowInsetsCompat;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.keyvalue.PlainTextKeyValueStore;
import org.thoughtcrime.securesms.util.SystemWindowInsetsSetter;

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

    setContentView(R.layout.create_passphrase_activity);

    // BaseActivity makes the window edge-to-edge, so the screen has to clear the system bars itself. The padding is
    // applied to the scroll view, so the background colour still runs behind the bars.
    SystemWindowInsetsSetter.attach(findViewById(R.id.create_passphrase_root), this, WindowInsetsCompat.Type.systemBars());

    initializeResources();
  }

  @Override
  public boolean useScreenLock() {
    return false;
  }

  private void initializeResources() {
    yesButton = findViewById(R.id.yes_button);
    noButton  = findViewById(R.id.no_button);

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
      generateSecrets(MasterSecretUtil.generateMasterSecret(this, MasterSecretUtil.getUnencryptedPassphrase()));
    }

    PlainTextKeyValueStore.setPassphraseLockEnabled(enabled);
  }

  private void generateSecrets(MasterSecret masterSecret) {
    MasterSecretUtil.generateAsymmetricMasterSecret(this, masterSecret);
    setMasterSecret(masterSecret);
    launchRoutedActivity();
  }
}
