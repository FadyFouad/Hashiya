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
