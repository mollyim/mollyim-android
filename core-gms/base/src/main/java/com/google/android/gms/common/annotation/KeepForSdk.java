package com.google.android.gms.common.annotation;

import androidx.annotation.Keep;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Keep
@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR })
@Documented
public @interface KeepForSdk {
}
