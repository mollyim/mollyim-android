/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gms.common.internal.safeparcel;

/**
 * This is here for the SafeParcelProcessor to link against and is intentionally not implementing a
 * Parcelable, so that it is not necessary to link in the Android framework to compile this.
 *
 * @hide
 */
public interface SafeParcelable {
    String NULL = "SAFE_PARCELABLE_NULL_STRING";

    @interface Class {
        String creator();

        boolean creatorIsFinal() default true;

        boolean validate() default false;

        boolean doNotParcelTypeDefaultValues() default false;
    }

    @interface Field {
        int id();

        String getter() default NULL;

        String type() default NULL;

        String defaultValue() default NULL;

        String defaultValueUnchecked() default NULL;
    }

    @interface VersionField {
        int id();

        String getter() default NULL;

        String type() default NULL;
    }

    @interface Indicator {
        String getter() default NULL;
    }

    @interface Constructor {}

    @interface Param {
        int id();
    }

    @interface RemovedParam {
        int id();

        String defaultValue() default NULL;

        String defaultValueUnchecked() default NULL;
    }

    @interface Reserved {
        int[] value();
    }
}
