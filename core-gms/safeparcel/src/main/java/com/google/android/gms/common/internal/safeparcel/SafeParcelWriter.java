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

import android.app.PendingIntent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.util.SparseLongArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * Functions to write a safe parcel. A safe parcel consists of a sequence of header/payload bytes.
 *
 * <p>The header is 16 bits of size and 16 bits of field id. If the size in the header is 0xffff,
 * the next 4 bytes are the size field instead.
 *
 * @hide
 */
public class SafeParcelWriter {

    static final int OBJECT_HEADER = 0x00004f45;

    private SafeParcelWriter() {}

    private static void writeHeader(Parcel p, int id, int size) {
        if (size >= 0x0000ffff) {
            p.writeInt(0xffff0000 | id);
            p.writeInt(size);
        } else {
            p.writeInt((size << 16) | id);
        }
    }

    /** Returns the cookie that should be passed to endVariableData. */
    private static int beginVariableData(Parcel p, int id) {
        // Since we don't know the size yet, assume it might be big and always use the
        // size overflow.
        p.writeInt(0xffff0000 | id);
        p.writeInt(0);
        return p.dataPosition();
    }

    /**
     * @param start The result of the paired beginVariableData.
     */
    private static void finishVariableData(Parcel p, int start) {
        int end = p.dataPosition();
        int size = end - start;
        // The size is one int before start.
        p.setDataPosition(start - 4);
        p.writeInt(size);
        p.setDataPosition(end);
    }

    /** Begins the objects header. */
    public static int beginObjectHeader(@NonNull Parcel p) {
        return beginVariableData(p, OBJECT_HEADER);
    }

    /** Finishes the objects header. */
    public static void finishObjectHeader(@NonNull Parcel p, int start) {
        finishVariableData(p, start);
    }

    /** Writes a boolean. */
    public static void writeBoolean(@NonNull Parcel p, int id, boolean val) {
        writeHeader(p, id, 4);
        p.writeInt(val ? 1 : 0);
    }

    /** Writes a {@link Boolean} object. */
    public static void writeBooleanObject(
            @NonNull Parcel p, int id, @Nullable Boolean val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }

        writeHeader(p, id, 4);
        p.writeInt(val ? 1 : 0);
    }

    /** Writes a byte. */
    public static void writeByte(@NonNull Parcel p, int id, byte val) {
        writeHeader(p, id, 4);
        p.writeInt(val);
    }

    /** Writes a char. */
    public static void writeChar(@NonNull Parcel p, int id, char val) {
        writeHeader(p, id, 4);
        p.writeInt(val);
    }

    /** Writes a short. */
    public static void writeShort(@NonNull Parcel p, int id, short val) {
        writeHeader(p, id, 4);
        p.writeInt(val);
    }

    /** Writes an int. */
    public static void writeInt(@NonNull Parcel p, int id, int val) {
        writeHeader(p, id, 4);
        p.writeInt(val);
    }

    /** Writes an {@link Integer}. */
    public static void writeIntegerObject(
            @NonNull Parcel p, int id, @Nullable Integer val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        writeHeader(p, id, 4);
        p.writeInt(val);
    }

    /** Writes a long. */
    public static void writeLong(@NonNull Parcel p, int id, long val) {
        writeHeader(p, id, 8);
        p.writeLong(val);
    }

    /** Writes a {@link Long}. */
    public static void writeLongObject(
            @NonNull Parcel p, int id, @Nullable Long val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        writeHeader(p, id, 8);
        p.writeLong(val);
    }

    /** Writes a {@link BigInteger}. */
    public static void writeBigInteger(
            @NonNull Parcel p, int id, @Nullable BigInteger val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeByteArray(val.toByteArray());
        finishVariableData(p, start);
    }

    /** Writes a float. */
    public static void writeFloat(@NonNull Parcel p, int id, float val) {
        writeHeader(p, id, 4);
        p.writeFloat(val);
    }

    /** Writes a {@link Float}. */
    public static void writeFloatObject(
            @NonNull Parcel p, int id, @Nullable Float val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        writeHeader(p, id, 4);
        p.writeFloat(val);
    }

    /** Writes a double. */
    public static void writeDouble(@NonNull Parcel p, int id, double val) {
        writeHeader(p, id, 8);
        p.writeDouble(val);
    }

    /** Writes a {@link Double} object. */
    public static void writeDoubleObject(
            @NonNull Parcel p, int id, @Nullable Double val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        writeHeader(p, id, 8);
        p.writeDouble(val);
    }

    /** Writes a {@link BigDecimal}. */
    public static void writeBigDecimal(
            @NonNull Parcel p, int id, @Nullable BigDecimal val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeByteArray(val.unscaledValue().toByteArray());
        p.writeInt(val.scale());
        finishVariableData(p, start);
    }

    /** Writes a {@link String}. */
    public static void writeString(
        @NonNull Parcel p, int id, @Nullable String val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeString(val);
        finishVariableData(p, start);
    }

    /** Writes a {@link IBinder}. */
    public static void writeIBinder(
            @NonNull Parcel p, int id, @Nullable IBinder val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        // The size of the flat_binder_object in Parcel.cpp is not actually variable
        // but is not part of the CDD, so treat it as variable.  It almost certainly
        // won't change between processes on a given device.
        int start = beginVariableData(p, id);
        p.writeStrongBinder(val);
        finishVariableData(p, start);
    }

    /** Writes a {@link Parcelable}. */
    public static void writeParcelable(
            @NonNull Parcel p,
            int id,
            @Nullable Parcelable val,
            int parcelableFlags,
            boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        val.writeToParcel(p, parcelableFlags);
        finishVariableData(p, start);
    }

    /** Writes a {@link Bundle}. */
    public static void writeBundle(
            @NonNull Parcel p, int id, @Nullable Bundle val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeBundle(val);
        finishVariableData(p, start);
    }

    /** Writes a byte array. */
    public static void writeByteArray(
            @NonNull Parcel p, int id, @Nullable byte[] buf, boolean writeNull) {
        if (buf == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeByteArray(buf);
        finishVariableData(p, start);
    }

    /** Writes a byte array array. */
    public static void writeByteArrayArray(
            @NonNull Parcel p, int id, @Nullable byte[][] buf, boolean writeNull) {
        if (buf == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int length = buf.length;
        p.writeInt(length);
        for (int i = 0; i < length; i++) {
            p.writeByteArray(buf[i]);
        }
        finishVariableData(p, start);
    }

    /** Writes a boolean array. */
    public static void writeBooleanArray(
            @NonNull Parcel p, int id, @Nullable boolean[] val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeBooleanArray(val);
        finishVariableData(p, start);
    }

    /** Writes a char array. */
    public static void writeCharArray(
            @NonNull Parcel p, int id, @Nullable char[] val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeCharArray(val);
        finishVariableData(p, start);
    }

    /** Writes an int array. */
    public static void writeIntArray(
            @NonNull Parcel p, int id, @Nullable int[] val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeIntArray(val);
        finishVariableData(p, start);
    }

    /** Writes a long array. */
    public static void writeLongArray(
            @NonNull Parcel p, int id, @Nullable long[] val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeLongArray(val);
        finishVariableData(p, start);
    }

    /** Writes a {@link BigInteger} array. */
    public static void writeBigIntegerArray(
            @NonNull Parcel p, int id, @Nullable BigInteger[] val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int length = val.length;
        p.writeInt(length);
        for (int i = 0; i < length; i++) {
            p.writeByteArray(val[i].toByteArray());
        }
        finishVariableData(p, start);
    }

    /** Writes a float array. */
    public static void writeFloatArray(
            @NonNull Parcel p, int id, @Nullable float[] val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeFloatArray(val);
        finishVariableData(p, start);
    }

    /** Writes a double array. */
    public static void writeDoubleArray(
            @NonNull Parcel p, int id, @Nullable double[] val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeDoubleArray(val);
        finishVariableData(p, start);
    }

    /** Writes a {@link BigDecimal} array. */
    public static void writeBigDecimalArray(
            @NonNull Parcel p, int id, @Nullable BigDecimal[] val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int length = val.length;
        p.writeInt(length);
        for (int i = 0; i < length; i++) {
            p.writeByteArray(val[i].unscaledValue().toByteArray());
            p.writeInt(val[i].scale());
        }
        finishVariableData(p, start);
    }

    /** Writes a {@link String} array. */
    public static void writeStringArray(
            @NonNull Parcel p, int id, @Nullable String[] val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeStringArray(val);
        finishVariableData(p, start);
    }

    /** Writes a {@link IBinder} array. */
    public static void writeIBinderArray(
            @NonNull Parcel p, int id, @Nullable IBinder[] val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeBinderArray(val);
        finishVariableData(p, start);
    }

    /** Writes a boolean list. */
    public static void writeBooleanList(
            @NonNull Parcel p, int id, @Nullable List<Boolean> val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int size = val.size();
        p.writeInt(size);
        for (int i = 0; i < size; i++) {
            p.writeInt(val.get(i) ? 1 : 0);
        }
        finishVariableData(p, start);
    }

    /** Writes an {@link Integer} list. */
    public static void writeIntegerList(
            @NonNull Parcel p, int id, @Nullable List<Integer> val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int size = val.size();
        p.writeInt(size);
        for (int i = 0; i < size; i++) {
            p.writeInt(val.get(i));
        }
        finishVariableData(p, start);
    }

    /** Writes a {@link Long} list. */
    public static void writeLongList(
            @NonNull Parcel p, int id, @Nullable List<Long> val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int size = val.size();
        p.writeInt(size);
        for (int i = 0; i < size; i++) {
            p.writeLong(val.get(i));
        }
        finishVariableData(p, start);
    }

    /** Writes a {@link Float} list. */
    public static void writeFloatList(
            @NonNull Parcel p, int id, @Nullable List<Float> val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int size = val.size();
        p.writeInt(size);
        for (int i = 0; i < size; i++) {
            p.writeFloat(val.get(i));
        }
        finishVariableData(p, start);
    }

    /** Writes a {@link Double} list. */
    public static void writeDoubleList(
            @NonNull Parcel p, int id, @Nullable List<Double> val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int size = val.size();
        p.writeInt(size);
        for (int i = 0; i < size; i++) {
            p.writeDouble(val.get(i));
        }
        finishVariableData(p, start);
    }

    /** Writes a {@link String} list. */
    public static void writeStringList(
            @NonNull Parcel p, int id, @Nullable List<String> val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeStringList(val);
        finishVariableData(p, start);
    }

    /** Writes a {@link IBinder} list. */
    public static void writeIBinderList(
            @NonNull Parcel p, int id, @Nullable List<IBinder> val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeBinderList(val);
        finishVariableData(p, start);
    }

    /** Writes a typed array. */
    public static <T extends Parcelable> void writeTypedArray(
            @NonNull Parcel p, int id, @Nullable T[] val, int parcelableFlags, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        // We need to customize the built-in Parcel.writeTypedArray() because we need to write
        // the sizes for each individual SafeParcelable objects since they can vary in size due
        // to supporting missing fields.
        final int length = val.length;
        p.writeInt(length);
        for (int i = 0; i < length; i++) {
            T item = val[i];
            if (item == null) {
                p.writeInt(0);
            } else {
                writeTypedItemWithSize(p, item, parcelableFlags);
            }
        }
        finishVariableData(p, start);
    }

    /** Writes a typed list. */
    public static <T extends Parcelable> void writeTypedList(
            @NonNull Parcel p, int id, @Nullable List<T> val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        // We need to customize the built-in Parcel.writeTypedList() because we need to write
        // the sizes for each individual SafeParcelable objects since they can vary in size due
        // supporting missing fields.
        final int length = val.size();
        p.writeInt(length);
        for (int i = 0; i < length; i++) {
            T item = val.get(i);
            if (item == null) {
                p.writeInt(0);
            } else {
                writeTypedItemWithSize(p, item, 0);
            }
        }
        finishVariableData(p, start);
    }

    /** Writes a typed item with size. */
    private static <T extends Parcelable> void writeTypedItemWithSize(
            Parcel p, T item, int parcelableFlags) {
        // Just write a 1 as a placeholder since we don't know the exact size of item
        // yet, and save the data position in Parcel p.
        final int itemSizeDataPosition = p.dataPosition();
        p.writeInt(1);
        final int itemStartPosition = p.dataPosition();
        item.writeToParcel(p, parcelableFlags);
        final int currentDataPosition = p.dataPosition();

        // go back and write the length in bytes
        p.setDataPosition(itemSizeDataPosition);
        p.writeInt(currentDataPosition - itemStartPosition);

        // set the parcel data position to where it was before
        p.setDataPosition(currentDataPosition);
    }

    /** Writes a parcel. */
    public static void writeParcel(
            @NonNull Parcel p, int id, @Nullable Parcel val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.appendFrom(val, 0, val.dataSize());
        finishVariableData(p, start);
    }

    /**
     * This is made to be compatible with writeTypedArray. See implementation of
     * Parcel.writeTypedArray(T[] val, parcelableFlags);
     */
    public static void writeParcelArray(
            @NonNull Parcel p, int id, @Nullable Parcel[] val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int length = val.length;
        p.writeInt(length);
        for (int i = 0; i < length; i++) {
            Parcel item = val[i];
            if (item != null) {
                p.writeInt(item.dataSize());
                // custom part
                p.appendFrom(item, 0, item.dataSize());
            } else {
                p.writeInt(0);
            }
        }
        finishVariableData(p, start);
    }

    /**
     * This is made to be compatible with writeTypedList. See implementation of
     * Parce.writeTypedList(List<T> val).
     */
    public static void writeParcelList(
            @NonNull Parcel p, int id, @Nullable List<Parcel> val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int size = val.size();
        p.writeInt(size);
        for (int i = 0; i < size; i++) {
            Parcel item = val.get(i);
            if (item != null) {
                p.writeInt(item.dataSize());
                // custom part
                p.appendFrom(item, 0, item.dataSize());
            } else {
                p.writeInt(0);
            }
        }
        finishVariableData(p, start);
    }

    /** Writes a {@link PendingIntent}. */
    public static void writePendingIntent(
            @NonNull Parcel p, int id, @Nullable PendingIntent val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        PendingIntent.writePendingIntentOrNullToParcel(val, p);
        finishVariableData(p, start);
    }

    /** Writes a list. */
    public static void writeList(
            @NonNull Parcel p,
            int id,
            @SuppressWarnings("rawtypes") @Nullable List list,
            boolean writeNull) {
        if (list == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeList(list);
        finishVariableData(p, start);
    }

    /** Writes a {@link SparseBooleanArray}. */
    public static void writeSparseBooleanArray(
            @NonNull Parcel p, int id, @Nullable SparseBooleanArray val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        p.writeSparseBooleanArray(val);
        finishVariableData(p, start);
    }

    /** Writes a {@link Double} {@link SparseArray}. */
    public static void writeDoubleSparseArray(
            @NonNull Parcel p, int id, @Nullable SparseArray<Double> val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int size = val.size();
        p.writeInt(size);
        for (int i = 0; i < size; i++) {
            p.writeInt(val.keyAt(i));
            p.writeDouble(val.valueAt(i));
        }
        finishVariableData(p, start);
    }

    /** Writes a {@link Float} {@link SparseArray}. */
    public static void writeFloatSparseArray(
            @NonNull Parcel p, int id, @Nullable SparseArray<Float> val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int size = val.size();
        p.writeInt(size);
        for (int i = 0; i < size; i++) {
            p.writeInt(val.keyAt(i));
            p.writeFloat(val.valueAt(i));
        }
        finishVariableData(p, start);
    }

    /** Writes a {@link SparseIntArray}. */
    public static void writeSparseIntArray(
            @NonNull Parcel p, int id, @Nullable SparseIntArray val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int size = val.size();
        p.writeInt(size);
        for (int i = 0; i < size; i++) {
            p.writeInt(val.keyAt(i));
            p.writeInt(val.valueAt(i));
        }
        finishVariableData(p, start);
    }

    /** Writes a {@link SparseLongArray}. */
    public static void writeSparseLongArray(
            @NonNull Parcel p, int id, @Nullable SparseLongArray val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int size = val.size();
        p.writeInt(size);
        for (int i = 0; i < size; i++) {
            p.writeInt(val.keyAt(i));
            p.writeLong(val.valueAt(i));
        }
        finishVariableData(p, start);
    }

    /** Writes a {@link String} {@link SparseArray}. */
    public static void writeStringSparseArray(
            @NonNull Parcel p, int id, @Nullable SparseArray<String> val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int size = val.size();
        p.writeInt(size);
        for (int i = 0; i < size; i++) {
            p.writeInt(val.keyAt(i));
            p.writeString(val.valueAt(i));
        }
        finishVariableData(p, start);
    }

    /** Writes a {@link Parcel} {@link SparseArray}. */
    public static void writeParcelSparseArray(
            @NonNull Parcel p, int id, @Nullable SparseArray<Parcel> val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int size = val.size();
        p.writeInt(size);
        for (int i = 0; i < size; i++) {
            p.writeInt(val.keyAt(i));
            Parcel item = val.valueAt(i);
            if (item != null) {
                p.writeInt(item.dataSize());
                // custom part
                p.appendFrom(item, 0, item.dataSize());
            } else {
                p.writeInt(0);
            }
        }
        finishVariableData(p, start);
    }

    /** Writes typed {@link SparseArray}. */
    public static <T extends Parcelable> void writeTypedSparseArray(
            @NonNull Parcel p, int id, @Nullable SparseArray<T> val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        // We follow the same approach as writeTypedList().
        final int size = val.size();
        p.writeInt(size);
        for (int i = 0; i < size; i++) {
            p.writeInt(val.keyAt(i));
            T item = val.valueAt(i);
            if (item == null) {
                p.writeInt(0);
            } else {
                writeTypedItemWithSize(p, item, 0);
            }
        }
        finishVariableData(p, start);
    }

    /** Writes {@link IBinder} {@link SparseArray}. */
    public static void writeIBinderSparseArray(
            @NonNull Parcel p, int id, @Nullable SparseArray<IBinder> val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int size = val.size();
        p.writeInt(size);
        for (int i = 0; i < size; i++) {
            p.writeInt(val.keyAt(i));
            p.writeStrongBinder(val.valueAt(i));
        }
        finishVariableData(p, start);
    }

    /** Writes byte array {@link SparseArray}. */
    public static void writeByteArraySparseArray(
            @NonNull Parcel p, int id, @Nullable SparseArray<byte[]> val, boolean writeNull) {
        if (val == null) {
            if (writeNull) {
                writeHeader(p, id, 0);
            }
            return;
        }
        int start = beginVariableData(p, id);
        final int size = val.size();
        p.writeInt(size);
        for (int i = 0; i < size; i++) {
            p.writeInt(val.keyAt(i));
            p.writeByteArray(val.valueAt(i));
        }
        finishVariableData(p, start);
    }
}
