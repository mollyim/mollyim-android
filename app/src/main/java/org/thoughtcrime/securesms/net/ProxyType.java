package org.thoughtcrime.securesms.net;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;

public enum ProxyType {
  NONE  ("none",  R.string.arrays__none),
  ORBOT ("orbot", R.string.arrays__tor_via_orbot),
  SOCKS5("socks5", R.string.arrays__socks5);

  private final String code;

  private final int stringResource;

  ProxyType(String code, @StringRes int stringResource) {
    this.code           = code;
    this.stringResource = stringResource;
  }

  public String getCode() {
    return code;
  }

  public @StringRes int getStringResource() {
    return stringResource;
  }

  public static ProxyType fromCode(@Nullable String code) {
    for (ProxyType type : ProxyType.values()) {
      if (type.code.equals(code)) {
        return type;
      }
    }

    return NONE;
  }
}
