package org.thoughtcrime.securesms.crypto;

import android.text.TextUtils;

import java.util.Locale;

import me.gosimple.nbvcxz.Nbvcxz;
import me.gosimple.nbvcxz.resources.Configuration;
import me.gosimple.nbvcxz.resources.ConfigurationBuilder;
import me.gosimple.nbvcxz.scoring.Result;
import me.gosimple.nbvcxz.scoring.TimeEstimate;

public class PassphraseValidator {

  private final Nbvcxz nbvcxz;

  private static final int MAX_LENGTH = 32;

  public PassphraseValidator(Locale locale) {
    Configuration configuration = new ConfigurationBuilder()
        .setLocale(locale)
        .createConfiguration();
    this.nbvcxz = new Nbvcxz(configuration);
  }

  public Strength estimate(final char[] passphrase) {
    return new Strength(nbvcxz.estimate(getTruncatedPassphrase(passphrase)));
  }

  /**
   * Returns the truncated password based on the max length.
   *
   * It can be removed when nbvcxz supports this configuration.
   */
  private static String getTruncatedPassphrase(final char[] passphrase) {
    return new String(passphrase, 0, Math.min(passphrase.length, MAX_LENGTH));
  }

  public static class Strength {

    private final Result result;

    public Strength(final Result result) {
      this.result = result;
    }

    public boolean isValid() {
      return result.isMinimumEntropyMet();
    }

    public String getTimeToCrack() {
      // TODO: add specific estimate for argon2
      return TimeEstimate.getTimeToCrackFormatted(result, "OFFLINE_BCRYPT_10");
    }

    public String getError() {
      return result.getFeedback().getWarning();
    }

    public String getSuggestion() {
      return TextUtils.join(" ", result.getFeedback().getSuggestion());
    }
  }
}
