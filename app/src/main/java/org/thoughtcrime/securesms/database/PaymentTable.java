package org.thoughtcrime.securesms.database;

public final class PaymentTable {

  public static final String TABLE_NAME = "payments";

  private static final String ID           = "_id";
  private static final String PAYMENT_UUID = "uuid";
  private static final String RECIPIENT_ID = "recipient";
  private static final String ADDRESS      = "recipient_address";
  private static final String TIMESTAMP    = "timestamp";
  private static final String DIRECTION    = "direction";
  private static final String STATE        = "state";
  private static final String NOTE         = "note";
  private static final String AMOUNT       = "amount";
  private static final String FEE          = "fee";
  private static final String TRANSACTION  = "transaction_record";
  private static final String RECEIPT      = "receipt";
  private static final String PUBLIC_KEY   = "receipt_public_key";
  private static final String META_DATA    = "payment_metadata";
  private static final String FAILURE      = "failure_reason";
  private static final String BLOCK_INDEX  = "block_index";
  private static final String BLOCK_TIME   = "block_timestamp";
  private static final String SEEN         = "seen";

  public static final String CREATE_TABLE =
    "CREATE TABLE " + TABLE_NAME + "(" + ID           + " INTEGER PRIMARY KEY, " +
                                         PAYMENT_UUID + " TEXT DEFAULT NULL, " +
                                         RECIPIENT_ID + " INTEGER DEFAULT 0, " +
                                         ADDRESS      + " TEXT DEFAULT NULL, " +
                                         TIMESTAMP    + " INTEGER, " +
                                         NOTE         + " TEXT DEFAULT NULL, " +
                                         DIRECTION    + " INTEGER, " +
                                         STATE        + " INTEGER, " +
                                         FAILURE      + " INTEGER, " +
                                         AMOUNT       + " BLOB NOT NULL, " +
                                         FEE          + " BLOB NOT NULL, " +
                                         TRANSACTION  + " BLOB DEFAULT NULL, " +
                                         RECEIPT      + " BLOB DEFAULT NULL, " +
                                         META_DATA    + " BLOB DEFAULT NULL, " +
                                         PUBLIC_KEY   + " TEXT DEFAULT NULL, " +
                                         BLOCK_INDEX  + " INTEGER DEFAULT 0, " +
                                         BLOCK_TIME   + " INTEGER DEFAULT 0, " +
                                         SEEN         + " INTEGER, " +
                                         "UNIQUE(" + PAYMENT_UUID + ") ON CONFLICT ABORT)";

  public static final String[] CREATE_INDEXES = {
    "CREATE INDEX IF NOT EXISTS timestamp_direction_index ON " + TABLE_NAME + " (" + TIMESTAMP + ", " + DIRECTION + ");",
    "CREATE INDEX IF NOT EXISTS timestamp_index ON " + TABLE_NAME + " (" + TIMESTAMP + ");",
    "CREATE UNIQUE INDEX IF NOT EXISTS receipt_public_key_index ON " + TABLE_NAME + " (" + PUBLIC_KEY + ");"
  };

}
