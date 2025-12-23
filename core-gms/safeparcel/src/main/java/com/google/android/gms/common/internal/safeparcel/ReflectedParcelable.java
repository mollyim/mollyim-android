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

import android.os.Parcelable;

/**
 * Interface for Parcelables that have the class name reflectively read as part of serialization.
 * This happens when when put into an Intent or Bundle, or in some Parcel write methods.
 *
 * <p>This interface is needed because the errorprone checker has some limitations on detecting
 * annotations (like {@code @KeepName}), where detecting inheritance is easier.
 *
 * @hide
 */
public interface ReflectedParcelable extends Parcelable {}
