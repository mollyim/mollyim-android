package com.google.android.gms.cloudmessaging;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;

import com.google.android.gms.common.internal.safeparcel.AbstractSafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelable.Class;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

@Keep
@Class(creator = "CloudMessageCreator")
public final class CloudMessage extends AbstractSafeParcelable {

  private static final String TAG = "CloudMessage";

  public static final int PRIORITY_UNKNOWN = 0;
  public static final int PRIORITY_HIGH    = 1;
  public static final int PRIORITY_NORMAL  = 2;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {
      PRIORITY_UNKNOWN,
      PRIORITY_HIGH,
      PRIORITY_NORMAL,
  })
  public @interface MessagePriority {
  }

  public static final Parcelable.Creator<CloudMessage> CREATOR = new CloudMessageCreator();

  @Field(id = 1) final Intent intent;

  private Map<String, String> dataCache;

  @Constructor
  public CloudMessage(@NonNull @Param(id = 1) Intent intent) {
    this.intent = intent;
  }

  @Nullable
  public String getCollapseKey() {
    return intent.getStringExtra("collapse_key");
  }

  @NonNull
  public synchronized Map<String, String> getData() {
    if (dataCache == null) {
      dataCache = parseExtrasToMap(intent.getExtras());
    }
    return dataCache;
  }

  @Nullable
  public String getFrom() {
    return intent.getStringExtra("from");
  }

  @NonNull
  public Intent getIntent() {
    return intent;
  }

  @Nullable
  public String getMessageId() {
    String msgId = intent.getStringExtra("google.message_id");
    return msgId == null ? intent.getStringExtra("message_id") : msgId;
  }

  @Nullable
  public String getMessageType() {
    return intent.getStringExtra("message_type");
  }

  @MessagePriority
  public int getOriginalPriority() {
    String p = intent.getStringExtra("google.original_priority");
    if (p == null) {
      p = intent.getStringExtra("google.priority");
    }
    return parsePriority(p);
  }

  @MessagePriority
  public int getPriority() {
    String p = intent.getStringExtra("google.delivered_priority");
    if (p == null) {
      if ("1".equals(intent.getStringExtra("google.priority_reduced"))) {
        return PRIORITY_NORMAL;
      }
      p = intent.getStringExtra("google.priority");
    }
    return parsePriority(p);
  }

  @Nullable
  public byte[] getRawData() {
    return intent.getByteArrayExtra("rawData");
  }

  @Nullable
  public String getSenderId() {
    return intent.getStringExtra("google.c.sender.id");
  }

  public long getSentTime() {
    Bundle extras = intent.getExtras();
    if (extras == null) {
      return 0;
    }
    Object raw = extras.get("google.sent_time");
    if (raw instanceof Long) {
      return (Long) raw;
    }
    if (raw instanceof String) {
      try {
        return Long.parseLong((String) raw);
      } catch (NumberFormatException e) {
        Log.w(TAG, "Invalid sent time: " + raw);
      }
    }
    return 0;
  }

  @Nullable
  public String getTo() {
    return intent.getStringExtra("google.to");
  }

  public int getTtl() {
    Bundle extras = intent.getExtras();
    if (extras == null) {
      return 0;
    }
    Object raw = extras.get("google.ttl");
    if (raw instanceof Integer) {
      return (Integer) raw;
    }
    if (raw instanceof String) {
      try {
        return Integer.parseInt((String) raw);
      } catch (NumberFormatException e) {
        Log.w(TAG, "Invalid TTL: " + raw);
      }
    }
    return 0;
  }

  @Nullable
  Integer getProductId() {
    if (!intent.hasExtra("google.product_id")) {
      return null;
    }
    return intent.getIntExtra("google.product_id", 0);
  }

  @Override
  public void writeToParcel(@NonNull Parcel dest, int flags) {
    CloudMessageCreator.writeToParcel(this, dest, flags);
  }

  @MessagePriority
  private static int parsePriority(@Nullable String p) {
    if ("high".equals(p)) {
      return PRIORITY_HIGH;
    } else if ("normal".equals(p)) {
      return PRIORITY_NORMAL;
    } else {
      return PRIORITY_UNKNOWN;
    }
  }

  @NonNull
  private static Map<String, String> parseExtrasToMap(@Nullable Bundle extras) {
    ArrayMap<String, String> map = new ArrayMap<>();
    if (extras == null) {
      return map;
    }
    for (String key : extras.keySet()) {
      Object value = extras.get(key);
      if (!(value instanceof String)
          || key.startsWith("google.")
          || key.equals("from")
          || key.equals("message_type")
          || key.equals("collapse_key"))
      {
        continue;
      }
      map.put(key, (String) value);
    }
    return map;
  }
}
