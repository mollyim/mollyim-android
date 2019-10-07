package org.thoughtcrime.securesms.crypto;

import android.text.TextUtils;
import me.gosimple.nbvcxz.Nbvcxz;
import me.gosimple.nbvcxz.scoring.Result;
import me.gosimple.nbvcxz.scoring.TimeEstimate;

public class PassphraseValidator {

  private final Nbvcxz nbvcxz;

  // TODO: ban "unencrypted" word
  // TODO: configure LANG
  public PassphraseValidator() {
    this.nbvcxz = new Nbvcxz();
  }

  public Strength estimate(final String passphrase) {
    return new Strength(nbvcxz.estimate(passphrase));
  }

  public class Strength {

    private final Result result;

    public Strength(final Result result) {
      this.result = result;
    }

    public boolean isValid() {
      return result.isMinimumEntropyMet();
    }

    public String getTimeToCrack() {
      return TimeEstimate.getTimeToCrackFormatted(result, "OFFLINE_BCRYPT_12");
    }

    public String getError() {
      return result.getFeedback().getWarning();
    }

    public String getSuggestion() {
      return TextUtils.join(" ", result.getFeedback().getSuggestion());
    }
  }
}
