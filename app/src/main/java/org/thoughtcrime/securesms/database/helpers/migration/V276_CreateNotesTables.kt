package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

@Suppress("ClassName")
object V276_CreateNotesTables : SignalDatabaseMigration {
  override fun migrate(
    context: Application,
    db: SQLiteDatabase,
    oldVersion: Int,
    newVersion: Int
  ) {
    db.execSQL(
      """
      CREATE TABLE notes (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          title TEXT NOT NULL DEFAULT '',
          content TEXT NOT NULL DEFAULT '',
          color_id INTEGER,
          created_at INTEGER NOT NULL DEFAULT (STRFTIME('%s', 'now') * 1000),
          updated_at INTEGER NOT NULL DEFAULT (STRFTIME('%s', 'now') * 1000)
      )
      """.trimIndent()
    )

    db.execSQL(
      """
      CREATE TABLE note_colors (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          name TEXT NOT NULL UNIQUE,
          hex_value TEXT NOT NULL UNIQUE
      )
      """.trimIndent()
    )

    db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_updated_at ON notes (updated_at)")
  }

  // Foreign key constraints can be enabled as these tables are new
  // and don't interact with existing tables in a way that would break constraints.
  override val enableForeignKeys: Boolean = true
}
