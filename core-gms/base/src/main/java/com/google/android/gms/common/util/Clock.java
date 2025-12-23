package com.google.android.gms.common.util;

import androidx.annotation.Keep;

@Keep
public interface Clock {
    long currentTimeMillis();

    long nanoTime();

    long currentThreadTimeMillis();

    long elapsedRealtime();
}
