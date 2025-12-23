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
import java.util.ArrayList;
import java.util.List;

/**
 * Functions to read in a safe parcel.
 *
 * @hide
 */
public class SafeParcelReader {
    /** class to parse the exception. */
    public static class ParseException extends RuntimeException {
        public ParseException(@NonNull String message, @NonNull Parcel p) {
            super(message + " Parcel: pos=" + p.dataPosition() + " size=" + p.dataSize());
        }
    }

    private SafeParcelReader() {}

    /** Reads the header. */
    public static int readHeader(@NonNull Parcel p) {
        return p.readInt();
    }

    /** Gets the id for the field. */
    public static int getFieldId(int header) {
        return header & 0x0000ffff;
    }

    /** Reads the size. */
    public static int readSize(@NonNull Parcel p, int header) {
        if ((header & 0xffff0000) != 0xffff0000) {
            return (header >> 16) & 0x0000ffff;
        } else {
            return p.readInt();
        }
    }

    /** Skips the unknown field. */
    public static void skipUnknownField(@NonNull Parcel p, int header) {
        int size = readSize(p, header);
        p.setDataPosition(p.dataPosition() + size);
    }

    private static void readAndEnforceSize(@NonNull Parcel p, int header, int required) {
        final int size = readSize(p, header);
        if (size != required) {
            throw new ParseException(
                    "Expected size "
                            + required
                            + " got "
                            + size
                            + " (0x"
                            + Integer.toHexString(size)
                            + ")",
                    p);
        }
    }

    private static void enforceSize(@NonNull Parcel p, int header, int size, int required) {
        if (size != required) {
            throw new ParseException(
                    "Expected size "
                            + required
                            + " got "
                            + size
                            + " (0x"
                            + Integer.toHexString(size)
                            + ")",
                    p);
        }
    }

    /** Returns the end position of the object in the parcel. */
    public static int validateObjectHeader(@NonNull Parcel p) {
        final int header = readHeader(p);
        final int size = readSize(p, header);
        final int start = p.dataPosition();
        if (getFieldId(header) != SafeParcelWriter.OBJECT_HEADER) {
            throw new ParseException(
                    "Expected object header. Got 0x" + Integer.toHexString(header), p);
        }
        final int end = start + size;
        if (end < start || end > p.dataSize()) {
            throw new ParseException("Size read is invalid start=" + start + " end=" + end, p);
        }
        return end;
    }

    /** Reads a boolean. */
    public static boolean readBoolean(@NonNull Parcel p, int header) {
        readAndEnforceSize(p, header, 4);
        return p.readInt() != 0;
    }

    /** Reads a {@link Boolean} object. */
    @Nullable
    public static Boolean readBooleanObject(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        if (size == 0) {
            return null;
        } else {
            enforceSize(p, header, size, 4);
            return p.readInt() != 0;
        }
    }

    /** Reads a byte. */
    public static byte readByte(@NonNull Parcel p, int header) {
        readAndEnforceSize(p, header, 4);
        return (byte) p.readInt();
    }

    /** Reads a char. */
    public static char readChar(@NonNull Parcel p, int header) {
        readAndEnforceSize(p, header, 4);
        return (char) p.readInt();
    }

    /** Reads a short. */
    public static short readShort(@NonNull Parcel p, int header) {
        readAndEnforceSize(p, header, 4);
        return (short) p.readInt();
    }

    /** Reads an int. */
    public static int readInt(@NonNull Parcel p, int header) {
        readAndEnforceSize(p, header, 4);
        return p.readInt();
    }

    /** Reads an {@link Integer} object. */
    @Nullable
    public static Integer readIntegerObject(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        if (size == 0) {
            return null;
        } else {
            enforceSize(p, header, size, 4);
            return p.readInt();
        }
    }

    /** Reads a long. */
    public static long readLong(@NonNull Parcel p, int header) {
        readAndEnforceSize(p, header, 8);
        return p.readLong();
    }

    /** Reads a {@link Long} object. */
    @Nullable
    public static Long readLongObject(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        if (size == 0) {
            return null;
        } else {
            enforceSize(p, header, size, 8);
            return p.readLong();
        }
    }

    /** Creates a {@link BigInteger}. */
    @Nullable
    public static BigInteger createBigInteger(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final byte[] val = p.createByteArray();
        p.setDataPosition(pos + size);
        return new BigInteger(val);
    }

    /** Reads a float. */
    public static float readFloat(@NonNull Parcel p, int header) {
        readAndEnforceSize(p, header, 4);
        return p.readFloat();
    }

    /** Reads a {@link Float}. */
    @Nullable
    public static Float readFloatObject(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        if (size == 0) {
            return null;
        } else {
            enforceSize(p, header, size, 4);
            return p.readFloat();
        }
    }

    /** Reads a double. */
    public static double readDouble(@NonNull Parcel p, int header) {
        readAndEnforceSize(p, header, 8);
        return p.readDouble();
    }

    /** Reads a {@link Double}. */
    @Nullable
    public static Double readDoubleObject(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        if (size == 0) {
            return null;
        } else {
            enforceSize(p, header, size, 8);
            return p.readDouble();
        }
    }

    /** Creates a {@link BigDecimal}. */
    @Nullable
    public static BigDecimal createBigDecimal(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final byte[] unscaledValue = p.createByteArray();
        final int scale = p.readInt();
        p.setDataPosition(pos + size);
        return new BigDecimal(new BigInteger(unscaledValue), scale);
    }

    /** Creates a {@link String}. */
    @Nullable
    public static String createString(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final String result = p.readString();
        p.setDataPosition(pos + size);
        return result;
    }

    /** Reads an {@link IBinder}. */
    @Nullable
    public static IBinder readIBinder(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final IBinder result = p.readStrongBinder();
        p.setDataPosition(pos + size);
        return result;
    }

    /** Reads a {@link PendingIntent}. */
    @Nullable
    public static PendingIntent readPendingIntent(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final PendingIntent result = PendingIntent.readPendingIntentOrNullFromParcel(p);
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a {@link Parcelable}. */
    @Nullable
    public static <T extends Parcelable> T createParcelable(
            @NonNull Parcel p, int header, @NonNull Parcelable.Creator<T> creator) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final T result = creator.createFromParcel(p);
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a {@link Bundle}. */
    @Nullable
    public static Bundle createBundle(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final Bundle result = p.readBundle();
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a byte array. */
    @Nullable
    public static byte[] createByteArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final byte[] result = p.createByteArray();
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a byte array array. */
    @Nullable
    public static byte[][] createByteArrayArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final int length = p.readInt();
        final byte[][] result = new byte[length][];
        for (int i = 0; i < length; i++) {
            result[i] = p.createByteArray();
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a boolean array array. */
    @Nullable
    public static boolean[] createBooleanArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final boolean[] result = p.createBooleanArray();
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a char array. */
    @Nullable
    public static char[] createCharArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final char[] result = p.createCharArray();
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates an int array. */
    @Nullable
    public static int[] createIntArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final int[] result = p.createIntArray();
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a long array. */
    @Nullable
    public static long[] createLongArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final long[] result = p.createLongArray();
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a {@link BigInteger} array. */
    @Nullable
    public static BigInteger[] createBigIntegerArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final int length = p.readInt();
        final BigInteger[] result = new BigInteger[length];
        for (int i = 0; i < length; i++) {
            result[i] = new BigInteger(p.createByteArray());
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a float array. */
    @Nullable
    public static float[] createFloatArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final float[] result = p.createFloatArray();
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a double array. */
    @Nullable
    public static double[] createDoubleArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final double[] result = p.createDoubleArray();
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a {@link BigDecimal} array. */
    @Nullable
    public static BigDecimal[] createBigDecimalArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final int length = p.readInt();
        final BigDecimal[] result = new BigDecimal[length];
        for (int i = 0; i < length; i++) {
            byte[] unscaledValue = p.createByteArray();
            int scale = p.readInt();
            result[i] = new BigDecimal(new BigInteger(unscaledValue), scale);
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a {@link String} array. */
    @Nullable
    public static String[] createStringArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final String[] result = p.createStringArray();
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a {@link IBinder} array. */
    @Nullable
    public static IBinder[] createIBinderArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final IBinder[] result = p.createBinderArray();
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a {@link Boolean} list. */
    @Nullable
    public static ArrayList<Boolean> createBooleanList(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final ArrayList<Boolean> result = new ArrayList<Boolean>();
        final int count = p.readInt();
        for (int i = 0; i < count; i++) {
            result.add(p.readInt() != 0 ? true : false);
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a {@link Integer} list. */
    @Nullable
    public static ArrayList<Integer> createIntegerList(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final ArrayList<Integer> result = new ArrayList<Integer>();
        final int count = p.readInt();
        for (int i = 0; i < count; i++) {
            result.add(p.readInt());
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a {@link SparseBooleanArray}. */
    @Nullable
    public static SparseBooleanArray createSparseBooleanArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        SparseBooleanArray result = p.readSparseBooleanArray();
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a {@link SparseIntArray}. */
    @Nullable
    public static SparseIntArray createSparseIntArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final SparseIntArray result = new SparseIntArray();
        final int count = p.readInt();
        for (int i = 0; i < count; i++) {
            int key = p.readInt();
            int value = p.readInt();
            result.append(key, value);
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a {@link Float} {@link SparseArray}. */
    @Nullable
    public static SparseArray<Float> createFloatSparseArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final SparseArray<Float> result = new SparseArray<Float>();
        final int count = p.readInt();
        for (int i = 0; i < count; i++) {
            int key = p.readInt();
            float value = p.readFloat();
            result.append(key, value);
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a {@link Double} {@link SparseArray}. */
    @Nullable
    public static SparseArray<Double> createDoubleSparseArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final SparseArray<Double> result = new SparseArray<Double>();
        final int count = p.readInt();
        for (int i = 0; i < count; i++) {
            int key = p.readInt();
            double value = p.readDouble();
            result.append(key, value);
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a {@link SparseLongArray}. */
    @Nullable
    public static SparseLongArray createSparseLongArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final SparseLongArray result = new SparseLongArray();
        final int count = p.readInt();
        for (int i = 0; i < count; i++) {
            int key = p.readInt();
            long value = p.readLong();
            result.append(key, value);
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a {@link String} {@link SparseArray}. */
    @Nullable
    public static SparseArray<String> createStringSparseArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final SparseArray<String> result = new SparseArray<String>();
        final int count = p.readInt();
        for (int i = 0; i < count; i++) {
            int key = p.readInt();
            String value = p.readString();
            result.append(key, value);
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates a {@link Parcel} {@link SparseArray}. */
    @Nullable
    public static SparseArray<Parcel> createParcelSparseArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final int count = p.readInt();
        final SparseArray<Parcel> result = new SparseArray<Parcel>();
        for (int i = 0; i < count; i++) {
            int key = p.readInt();
            // read in the flag of whether this element is null
            int parcelSize = p.readInt();
            if (parcelSize != 0) {
                // non-null
                int currentDataPosition = p.dataPosition();
                Parcel item = Parcel.obtain();
                item.appendFrom(p, currentDataPosition, parcelSize);
                result.append(key, item);

                // move p's data position
                p.setDataPosition(currentDataPosition + parcelSize);
            } else {
                // is null
                result.append(key, null);
            }
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates typed {@link SparseArray}. */
    @Nullable
    public static <T> SparseArray<T> createTypedSparseArray(
            @NonNull Parcel p, int header, @NonNull Parcelable.Creator<T> c) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final int count = p.readInt();
        final SparseArray<T> result = new SparseArray<>();
        for (int i = 0; i < count; i++) {
            int key = p.readInt();
            T value;
            if (p.readInt() != 0) {
                value = c.createFromParcel(p);
            } else {
                value = null;
            }
            result.append(key, value);
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates {@link IBinder} {@link SparseArray}. */
    @Nullable
    public static SparseArray<IBinder> createIBinderSparseArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final int count = p.readInt();
        final SparseArray<IBinder> result = new SparseArray<>(count);
        for (int i = 0; i < count; i++) {
            int key = p.readInt();
            IBinder value = p.readStrongBinder();
            result.append(key, value);
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates byte array {@link SparseArray}. */
    @Nullable
    public static SparseArray<byte[]> createByteArraySparseArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);

        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final int count = p.readInt();
        final SparseArray<byte[]> result = new SparseArray<byte[]>(count);
        for (int i = 0; i < count; i++) {
            int key = p.readInt();
            byte[] value = p.createByteArray();
            result.append(key, value);
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates {@link Long} {@link ArrayList}. */
    @Nullable
    public static ArrayList<Long> createLongList(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final ArrayList<Long> result = new ArrayList<Long>();
        final int count = p.readInt();
        for (int i = 0; i < count; i++) {
            result.add(p.readLong());
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates {@link Float} {@link ArrayList}. */
    @Nullable
    public static ArrayList<Float> createFloatList(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final ArrayList<Float> result = new ArrayList<Float>();
        final int count = p.readInt();
        for (int i = 0; i < count; i++) {
            result.add(p.readFloat());
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates {@link Double} {@link ArrayList}. */
    @Nullable
    public static ArrayList<Double> createDoubleList(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final ArrayList<Double> result = new ArrayList<Double>();
        final int count = p.readInt();
        for (int i = 0; i < count; i++) {
            result.add(p.readDouble());
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates {@link String} {@link ArrayList}. */
    @Nullable
    public static ArrayList<String> createStringList(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final ArrayList<String> result = p.createStringArrayList();
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates {@link IBinder} {@link ArrayList}. */
    @Nullable
    public static ArrayList<IBinder> createIBinderList(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final ArrayList<IBinder> result = p.createBinderArrayList();
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates typed array. */
    @Nullable
    public static <T> T[] createTypedArray(
            @NonNull Parcel p, int header, @NonNull Parcelable.Creator<T> c) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final T[] result = p.createTypedArray(c);
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates typed {@link ArrayList}. */
    @Nullable
    public static <T> ArrayList<T> createTypedList(
            @NonNull Parcel p, int header, @NonNull Parcelable.Creator<T> c) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final ArrayList<T> result = p.createTypedArrayList(c);
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates {@link Parcel}. */
    @Nullable
    public static Parcel createParcel(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final Parcel result = Parcel.obtain();
        result.appendFrom(p, pos, size);
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates {@link Parcel} array. */
    @Nullable
    public static Parcel[] createParcelArray(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final int length = p.readInt();
        final Parcel[] result = new Parcel[length];
        for (int i = 0; i < length; i++) {
            int parcelSize = p.readInt();
            if (parcelSize != 0) {
                int currentDataPosition = p.dataPosition();
                Parcel item = Parcel.obtain();
                item.appendFrom(p, currentDataPosition, parcelSize);
                result[i] = item;

                // move p's data position
                p.setDataPosition(currentDataPosition + parcelSize);
            } else {
                result[i] = null;
            }
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Creates {@link Parcel} {@link ArrayList}. */
    @Nullable
    public static ArrayList<Parcel> createParcelList(@NonNull Parcel p, int header) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return null;
        }
        final int length = p.readInt();
        final ArrayList<Parcel> result = new ArrayList<Parcel>();
        for (int i = 0; i < length; i++) {
            // read in the flag of whether this element is null
            int parcelSize = p.readInt();
            if (parcelSize != 0) {
                // non-null
                int currentDataPosition = p.dataPosition();
                Parcel item = Parcel.obtain();
                item.appendFrom(p, currentDataPosition, parcelSize);
                result.add(item);

                // move p's data position
                p.setDataPosition(currentDataPosition + parcelSize);
            } else {
                // is null
                result.add(null);
            }
        }
        p.setDataPosition(pos + size);
        return result;
    }

    /** Reads the list. */
    public static void readList(
            @NonNull Parcel p,
            int header,
            @SuppressWarnings("rawtypes") @NonNull List list,
            @Nullable ClassLoader loader) {
        final int size = readSize(p, header);
        final int pos = p.dataPosition();
        if (size == 0) {
            return;
        }
        p.readList(list, loader);
        p.setDataPosition(pos + size);
    }

    /** Ensures at end. */
    public static void ensureAtEnd(@NonNull Parcel parcel, int end) {
        if (parcel.dataPosition() != end) {
            throw new ParseException("Overread allowed size end=" + end, parcel);
        }
    }
}
