package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.Base64;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.LRUCache;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.nio.charset.StandardCharsets.UTF_8;

public class EncryptedPreferences implements SharedPreferences {

  private static final String NULL_VALUE = "__NULL__";

  private static final int MAX_CACHE_SIZE = 50;
  private static final LRUCache<String, Object> cache = new LRUCache<>(MAX_CACHE_SIZE);

  private final String fileName;

  private final SharedPreferences sharedPreferences;

  private EncryptPredicate encryptionFilter;

  private MasterSecret masterSecret;
  private MasterCipher masterCipher;

  private final CopyOnWriteArrayList<OnSharedPreferenceChangeListener> listeners;

  private EncryptedPreferences(@NonNull String fileName,
                               @NonNull SharedPreferences sharedPreferences) {
    this.fileName          = fileName;
    this.sharedPreferences = sharedPreferences;
    this.listeners         = new CopyOnWriteArrayList<>();
  }

  @NonNull
  public static EncryptedPreferences create(@NonNull Context context,
                                            @NonNull String fileName) {
    return new EncryptedPreferences(fileName,
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE));
  }

  public interface EncryptPredicate {
    boolean test(String key);
  }

  public void setEncryptionFilter(EncryptPredicate filter) {
    this.encryptionFilter = filter;
  }

  @Override
  protected void finalize() throws Throwable {
    if (masterSecret != null) {
      masterSecret.destroy();
    }
    super.finalize();
  }

  public static final class Editor implements SharedPreferences.Editor {

    private final EncryptedPreferences     preferences;
    private final SharedPreferences.Editor editor;
    private final List<String>             keysChanged;

    Editor(EncryptedPreferences encryptedPreferences, SharedPreferences.Editor editor) {
      this.preferences = encryptedPreferences;
      this.editor      = editor;
      this.keysChanged = new CopyOnWriteArrayList<>();
    }

    @Override
    public SharedPreferences.Editor putString(String key, @Nullable String value) {
      if (preferences.shouldEncrypt(key)) {
        if (value == null) {
          value = NULL_VALUE;
        }
        byte[] stringBytes = value.getBytes(UTF_8);
        int paddedLength = getPaddedLength(4 + 4 + stringBytes.length);
        ByteBuffer buffer = ByteBuffer.allocate(paddedLength);
        buffer.putInt(EncryptedType.STRING.getId());
        buffer.putInt(stringBytes.length);
        buffer.put(stringBytes);
        putEncryptedObject(key, buffer.array());
      } else {
        editor.putString(key, value);
      }
      return this;
    }

    @Override
    public SharedPreferences.Editor putStringSet(String key, @Nullable Set<String> values) {
      if (preferences.shouldEncrypt(key)) {
        if (values == null) {
          values = new HashSet<>(1);
          values.add(NULL_VALUE);
        }
        List<byte[]> byteValues = new ArrayList<>(values.size());
        int totalBytes = 4 + values.size() * 4;
        for (String strValue : values) {
          byte[] byteValue = strValue.getBytes(UTF_8);
          byteValues.add(byteValue);
          totalBytes += byteValue.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(totalBytes);
        buffer.putInt(EncryptedType.STRING_SET.getId());
        for (byte[] bytes : byteValues) {
          buffer.putInt(bytes.length);
          buffer.put(bytes);
        }
        putEncryptedObject(key, buffer.array());
      } else {
        editor.putStringSet(key, values);
      }
      return this;
    }

    @Override
    public SharedPreferences.Editor putInt(String key, int value) {
      if (preferences.shouldEncrypt(key)) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4);
        buffer.putInt(EncryptedType.INT.getId());
        buffer.putInt(value);
        putEncryptedObject(key, buffer.array());
      } else {
        editor.putInt(key, value);
      }
      return this;
    }

    @Override
    public SharedPreferences.Editor putLong(String key, long value) {
      if (preferences.shouldEncrypt(key)) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 8);
        buffer.putInt(EncryptedType.LONG.getId());
        buffer.putLong(value);
        putEncryptedObject(key, buffer.array());
      } else {
        editor.putLong(key, value);
      }
      return this;
    }

    @Override
    public SharedPreferences.Editor putFloat(String key, float value) {
      if (preferences.shouldEncrypt(key)) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4);
        buffer.putInt(EncryptedType.FLOAT.getId());
        buffer.putFloat(value);
        putEncryptedObject(key, buffer.array());
      } else {
        editor.putFloat(key, value);
      }
      return this;
    }

    @Override
    public SharedPreferences.Editor putBoolean(String key, boolean value) {
      if (preferences.shouldEncrypt(key)) {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 1);
        buffer.putInt(EncryptedType.BOOLEAN.getId());
        buffer.put(value ? (byte) 1 : (byte) 0);
        putEncryptedObject(key, buffer.array());
      } else {
        editor.putBoolean(key, value);
      }
      return this;
    }

    @Override
    public SharedPreferences.Editor remove(String key) {
      editor.remove(key);
      keysChanged.add(key);
      return this;
    }

    @Override
    public SharedPreferences.Editor clear() {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean commit() {
        try {
          synchronized (cache) {
            invalidateCache();
            return editor.commit();
          }
        } finally {
          notifyListeners();
          keysChanged.clear();
        }
    }

    @Override
    public void apply() {
      synchronized (cache) {
        invalidateCache();
        editor.apply();
      }
      notifyListeners();
    }

    private void putEncryptedObject(String key, byte[] value) {
      keysChanged.add(key);
      editor.putString(key, encrypt(key, value, preferences.getCipher()));
    }

    private void invalidateCache() {
      for (String key : keysChanged) {
        cache.remove(preferences.getCacheKey(key));
      }
    }

    private void notifyListeners() {
      for (OnSharedPreferenceChangeListener listener : preferences.listeners) {
        for (String key : keysChanged) {
          listener.onSharedPreferenceChanged(preferences, key);
        }
      }
    }

    private static int getPaddedLength(int length) {
      return Math.max(32, (Integer.highestOneBit(length) << 1)) - 1;
    }
  }

  @Override
  public Map<String, ?> getAll() {
    return sharedPreferences.getAll();
  }

  @Nullable
  @Override
  public String getString(String key, @Nullable String defValue) {
    if (shouldEncrypt(key)) {
      Object value = getDecryptedObject(key);
      return (value instanceof String) ? (String) value : defValue;
    } else {
      return sharedPreferences.getString(key, defValue);
    }
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
    if (shouldEncrypt(key)) {
      Object values = getDecryptedObject(key);
      return (values instanceof Set) ? (Set<String>) values : defValues;
    } else {
      return sharedPreferences.getStringSet(key, defValues);
    }
  }

  @Override
  public int getInt(String key, int defValue) {
    if (shouldEncrypt(key)) {
      Object value = getDecryptedObject(key);
      return (value instanceof Integer) ? (Integer) value : defValue;
    } else {
      return sharedPreferences.getInt(key, defValue);
    }
  }

  @Override
  public long getLong(String key, long defValue) {
    if (shouldEncrypt(key)) {
      Object value = getDecryptedObject(key);
      return (value instanceof Long) ? (Long) value : defValue;
    } else {
      return sharedPreferences.getLong(key, defValue);
    }
  }

  @Override
  public float getFloat(String key, float defValue) {
    if (shouldEncrypt(key)) {
      Object value = getDecryptedObject(key);
      return (value instanceof Float) ? (Float) value : defValue;
    } else {
      return sharedPreferences.getFloat(key, defValue);
    }
  }

  @Override
  public boolean getBoolean(String key, boolean defValue) {
    if (shouldEncrypt(key)) {
      Object value = getDecryptedObject(key);
      return (value instanceof Boolean) ? (Boolean) value : defValue;
    } else {
      return sharedPreferences.getBoolean(key, defValue);
    }
  }

  @Override
  public boolean contains(String key) {
    return sharedPreferences.contains(key);
  }

  @Override
  public Editor edit() {
    return new Editor(this, sharedPreferences.edit());
  }

  @Override
  public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    listeners.add(listener);
  }

  @Override
  public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    listeners.remove(listener);
  }

  private enum EncryptedType {
    STRING(0),
    STRING_SET(1),
    INT(2),
    LONG(3),
    FLOAT(4),
    BOOLEAN(5);

    int id;

    EncryptedType(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }

    public static EncryptedType fromId(int id) {
      switch (id) {
        case 0: return STRING;
        case 1: return STRING_SET;
        case 2: return INT;
        case 3: return LONG;
        case 4: return FLOAT;
        case 5: return BOOLEAN;
      }
      return null;
    }
  }

  private Object getDecryptedObject(String key) {
    String cacheKey = getCacheKey(key);
    synchronized (cache) {
      Object cachedValue = cache.get(cacheKey);
      if (cachedValue != null || cache.containsKey(cacheKey)) {
        return cachedValue;
      }
    }
    Object value = null;
    MasterCipher masterCipher = getCipher();
    synchronized (cache) {
      String encryptedValue = sharedPreferences.getString(key, null);
      if (encryptedValue != null) {
        byte[] bytes = decrypt(key, encryptedValue, masterCipher);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.position(0);
        int typeId = buffer.getInt();
        EncryptedType type = EncryptedType.fromId(typeId);
        switch (Objects.requireNonNull(type)) {
          case STRING:
            int stringLength = buffer.getInt();
            ByteBuffer stringSlice = buffer.slice();
            stringSlice.limit(stringLength);
            String stringValue = UTF_8.decode(stringSlice).toString();
            if (stringValue.equals(NULL_VALUE)) {
              value = null;
            } else {
              value = stringValue;
            }
            break;
          case INT:
            value = buffer.getInt();
            break;
          case LONG:
            value = buffer.getLong();
            break;
          case FLOAT:
            value = buffer.getFloat();
            break;
          case BOOLEAN:
            value = buffer.get() != (byte) 0;
            break;
          case STRING_SET:
            HashSet<String> stringSet = new HashSet<>();
            while (buffer.hasRemaining()) {
              int subStringLength = buffer.getInt();
              ByteBuffer subStringSlice = buffer.slice();
              subStringSlice.limit(subStringLength);
              buffer.position(buffer.position() + subStringLength);
              stringSet.add(UTF_8.decode(subStringSlice).toString());
            }
            if (stringSet.size() == 1 && NULL_VALUE.equals(stringSet.iterator().next())) {
              value = null;
            } else {
              value = stringSet;
            }
            break;
        }
      }
      cache.put(cacheKey, value);
    }
    return value;
  }

  private boolean shouldEncrypt(String key) {
    if (key == null) {
      throw new IllegalArgumentException("Preference name cannot be null");
    }
    return encryptionFilter == null || encryptionFilter.test(key);
  }

  private String getCacheKey(String key) {
    return String.format("%s:%s", key, fileName);
  }

  private MasterCipher getCipher() {
    if (masterCipher == null) {
      masterSecret = KeyCachingService.getMasterSecret();
      masterCipher = new MasterCipher(masterSecret);
    }
    return masterCipher;
  }

  static private String encrypt(String key, byte[] value, MasterCipher masterCipher) {
    return Base64.encodeWithPadding(masterCipher.encrypt(value, key.getBytes(UTF_8)));
  }

  static private byte[] decrypt(String key, String encryptedValue, MasterCipher masterCipher) {
    try {
      return masterCipher.decrypt(Base64.decode(encryptedValue), key.getBytes(UTF_8));
    } catch (GeneralSecurityException | IOException e) {
      throw new SecurityException("Could not decrypt '" + key + "' value. " + e.getMessage(), e);
    }
  }
}
