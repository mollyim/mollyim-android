package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RunWith(AndroidJUnit4.class)
public class EncryptedPreferencesTest {

  private Context context;

  private static final String PREFS_FILE = "test_shared_prefs";

  @Before
  public void setup() {
    context = InstrumentationRegistry.getInstrumentation().getContext();
  }

  @Test
  public void testEditor() {
    SharedPreferences sharedPreferences = EncryptedPreferences.create(context, PREFS_FILE);

    SharedPreferences.Editor editor = sharedPreferences.edit();

    // String Test
    final String stringTestKey = "StringTest";
    final String stringTestValue = "THIS IS A TEST STRING";
    editor.putString(stringTestKey, stringTestValue);

    final SharedPreferences.OnSharedPreferenceChangeListener listener =
        (sharedPreferences1, key) -> Assert.assertEquals(stringTestValue,
                                                         sharedPreferences1.getString(stringTestKey, null));

    sharedPreferences.registerOnSharedPreferenceChangeListener(listener);
    editor.commit();
    sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener);

    // String Set Test
    String           stringSetTestKey = "StringSetTest";
    ArraySet<String> stringSetValue   = new ArraySet<>();
    stringSetValue.add("Test1");
    stringSetValue.add("Test2");
    editor.putStringSet(stringSetTestKey, stringSetValue);

    // Int Test
    String intTestKey = "IntTest";
    int intTestValue = 1000;
    editor.putInt(intTestKey, intTestValue);

    // Long Test
    String longTestKey = "LongTest";
    long longTestValue = 500L;
    editor.putLong(longTestKey, longTestValue);

    // Boolean Test
    String booleanTestKey = "BooleanTest";
    boolean booleanTestValue = true;
    editor.putBoolean(booleanTestKey, booleanTestValue);

    // Float Test
    String floatTestKey = "FloatTest";
    float floatTestValue = 250.5f;
    editor.putFloat(floatTestKey, floatTestValue);

    boolean exceptionThrown = false;

    // Try Null Key Test
    try {
      String nullKey = null;
      String nullStringValue = "NULL_VALUE";
      editor.putString(nullKey, nullStringValue);
    } catch (IllegalArgumentException e) {
      exceptionThrown = true;
    }

    Assert.assertTrue("Null keys should not be allowed.",
                      exceptionThrown);
    exceptionThrown = false;

    editor.commit();

    // String Test Assertion
    Assert.assertEquals(stringTestKey + " has the wrong value",
                        stringTestValue,
                        sharedPreferences.getString(stringTestKey, null));

    // StringSet Test Assertion
    Set<String> stringSetPrefsValue = sharedPreferences.getStringSet(stringSetTestKey, null);
    String      stringSetTestValue  = null;
    if (!stringSetPrefsValue.isEmpty()) {
      stringSetTestValue = stringSetPrefsValue.iterator().next();
    }
    Assert.assertEquals(stringSetTestKey + " has the wrong value",
                        stringSetValue.valueAt(0),
                        stringSetTestValue);

    // Int Test Assertion
    Assert.assertEquals(intTestKey + " has the wrong value",
                        intTestValue,
                        sharedPreferences.getInt(intTestKey, 0));

    // Long Test Assertion
    Assert.assertEquals(longTestKey + " has the wrong value",
                        longTestValue,
                        sharedPreferences.getLong(longTestKey, 0L));

    // Boolean Test Assertion
    Assert.assertEquals(booleanTestKey + " has the wrong value",
                        booleanTestValue,
                        sharedPreferences.getBoolean(booleanTestKey, false));

    // Float Test Assertion
    Assert.assertEquals(floatTestValue,
                        sharedPreferences.getFloat(floatTestKey, 0.0f),
                        0.0f);

    // Test Remove
    editor.remove(intTestKey);
    editor.apply();

    Assert.assertEquals(intTestKey + " should have been removed.", sharedPreferences.getInt(intTestKey, 0), 0);

    Assert.assertFalse(intTestKey + " should not exist",
                       sharedPreferences.contains(intTestKey));

    // Null String Key and value Test Assertion
    String nullKey = "NullTest";
    editor.putString(nullKey, null);
    editor.putStringSet(nullKey, null);
    editor.commit();
    Assert.assertNull(nullKey + " should not have a value", sharedPreferences.getString(nullKey, null));

    // Null StringSet Key and value Test Assertion
    Assert.assertNull(nullKey + " should not have a value", sharedPreferences.getStringSet(nullKey, null));

    // Test overwriting keys
    String twiceKey = "KeyTwice";
    String twiceVal1 = "FirstVal";
    String twiceVal2 = "SecondVal";
    editor.putString(twiceKey, twiceVal1);
    editor.commit();

    Assert.assertEquals(twiceVal1 + " should be the value",
                        twiceVal1,
                        sharedPreferences.getString(twiceKey, null));

    editor.putString(twiceKey, twiceVal2);
    editor.commit();

    Assert.assertEquals(twiceVal2 + " should be the value",
                        twiceVal2,
                        sharedPreferences.getString(twiceKey, null));

    // Test getAll
    Map<String, ?> all = sharedPreferences.getAll();

    Assert.assertTrue("Get all should have supplied " + twiceKey,
                      all.containsKey(twiceKey));

    System.out.println("All entries " + all);
  }

  @Test
  public void testReentrantCallbackCalls() {
    SharedPreferences sharedPreferences = EncryptedPreferences.create(context, PREFS_FILE);

    sharedPreferences.registerOnSharedPreferenceChangeListener(
        new SharedPreferences.OnSharedPreferenceChangeListener() {
          @Override
          public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                                String key) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
          }
        });

    sharedPreferences.registerOnSharedPreferenceChangeListener(
        (prefs, key) -> {
          // No-op
        });

    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putString("someKey", "someValue");
    editor.apply();
  }

  @Test
  public void testWriteReadWithConcurrency() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(10);

    SharedPreferences sharedPreferences = EncryptedPreferences.create(context, PREFS_FILE);

    Callable<Object> job = () -> {
      SharedPreferences.Editor editor = sharedPreferences.edit();

      String randomValue = String.valueOf(Math.random());
      editor.putString("someKey", randomValue);
      editor.apply();

      return sharedPreferences.getString("someKey", null);
    };

    List<Callable<Object>> todo = new ArrayList<>(Collections.nCopies(100, job));

    List<Future<Object>> answers = executor.invokeAll(todo);

    for (Future<Object> answer : answers) {
      Assert.assertNotNull(answer.get());
    }
  }
}
