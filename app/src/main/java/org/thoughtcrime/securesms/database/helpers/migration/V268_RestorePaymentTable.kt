package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

@Suppress("ClassName")
object V268_RestorePaymentTable : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS payments(
        _id INTEGER PRIMARY KEY,
        uuid TEXT DEFAULT NULL,
        recipient INTEGER DEFAULT 0,
        recipient_address TEXT DEFAULT NULL,
        timestamp INTEGER,
        note TEXT DEFAULT NULL,
        direction INTEGER,
        state INTEGER,
        failure_reason INTEGER,
        amount BLOB NOT NULL,
        fee BLOB NOT NULL,
        transaction_record BLOB DEFAULT NULL,
        receipt BLOB DEFAULT NULL,
        payment_metadata BLOB DEFAULT NULL,
        receipt_public_key TEXT DEFAULT NULL,
        block_index INTEGER DEFAULT 0,
        block_timestamp INTEGER DEFAULT 0,
        seen INTEGER,
        UNIQUE(uuid) ON CONFLICT ABORT
      )
      """.trimIndent()
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS timestamp_direction_index ON payments (timestamp, direction)")
    db.execSQL("CREATE INDEX IF NOT EXISTS timestamp_index ON payments (timestamp)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS receipt_public_key_index ON payments (receipt_public_key)")
  }
}
