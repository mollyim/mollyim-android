package com.google.android.gms.common.util;

import androidx.annotation.Keep;

@Keep
public interface BiConsumer<T, U> {
  void accept(T t, U u);
}
