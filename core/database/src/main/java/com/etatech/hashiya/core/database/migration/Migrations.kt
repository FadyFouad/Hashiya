package com.etatech.hashiya.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.etatech.hashiya.core.database.model.searchEntityFor

/** Adds the reading status (every existing paper becomes "to_read") and the search index, filled for every existing paper. */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE papers ADD COLUMN reading_status TEXT NOT NULL DEFAULT 'to_read'")
        // Exactly the statement Room generates for PaperSearchEntity (schemas/…/2.json), so the schema validates.
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `paper_search` USING FTS4(`paper_id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`authors` TEXT NOT NULL, `abstract` TEXT NOT NULL, `venue` TEXT NOT NULL, tokenize=unicode61, notindexed=`paper_id`)"
        )
        val authorNames = mutableMapOf<String, MutableList<String>>()
        db.query("SELECT paper_id, name FROM paper_authors ORDER BY paper_id, position").use { cursor ->
            while (cursor.moveToNext()) {
                authorNames.getOrPut(cursor.getString(0)) { mutableListOf() } += cursor.getString(1)
            }
        }
        db.query("SELECT id, title, abstract, venue FROM papers").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val row = searchEntityFor(
                    paperId = id,
                    title = cursor.getString(1),
                    authorNames = authorNames[id].orEmpty(),
                    abstract = if (cursor.isNull(2)) null else cursor.getString(2),
                    venue = if (cursor.isNull(3)) null else cursor.getString(3)
                )
                db.execSQL(
                    "INSERT INTO paper_search (paper_id, title, authors, abstract, venue) VALUES (?, ?, ?, ?, ?)",
                    arrayOf(row.paperId, row.title, row.authors, row.abstract, row.venue)
                )
            }
        }
    }
}

/**
 * Adds the notes table and a notes column to the search index. An FTS table can't gain a column, so the existing
 * search rows are copied out through a plain temporary table and back into the rebuilt one, unchanged and with no notes.
 */
internal val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Both CREATE statements are exactly what Room generates (schemas/…/3.json), so the schema validates.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `paper_notes` (`paper_id` TEXT NOT NULL, `summary` TEXT NOT NULL, " +
                "`research_question` TEXT NOT NULL, `method` TEXT NOT NULL, `key_findings` TEXT NOT NULL, " +
                "`limitations` TEXT NOT NULL, `thoughts` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`paper_id`), " +
                "FOREIGN KEY(`paper_id`) REFERENCES `papers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE TEMP TABLE paper_search_copy AS SELECT paper_id, title, authors, abstract, venue FROM paper_search")
        db.execSQL("DROP TABLE paper_search")
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `paper_search` USING FTS4(`paper_id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`authors` TEXT NOT NULL, `abstract` TEXT NOT NULL, `venue` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                "tokenize=unicode61, notindexed=`paper_id`)"
        )
        db.execSQL(
            "INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes) " +
                "SELECT paper_id, title, authors, abstract, venue, '' FROM paper_search_copy"
        )
        db.execSQL("DROP TABLE paper_search_copy")
    }
}
