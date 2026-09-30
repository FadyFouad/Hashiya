package com.etatech.hashiya.core.database.migration

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.model.PaperNotes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val DB_NAME = "migration-test.db"

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HashiyaDatabase::class.java)

    /**
     * A version 1 library as sub-project 2 left it: one paper with authors, one with no authors and no abstract or venue.
     * The second author is inserted first, so the backfilled authors are in order only if the migration sorts them.
     */
    private fun createVersion1() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, " +
                    "oa_pdf_url, saved_at) VALUES ('a', 'W1', '10.48550/arxiv.1706.03762', 'Attention Is All You Need', 2017, " +
                    "'Neural Information Processing Systems', 'The dominant sequence transduction models', 128412, 1, NULL, 100)"
            )
            db.execSQL("INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 1, 'Noam Shazeer', NULL)")
            db.execSQL("INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 0, 'Ashish Vaswani', NULL)")
            db.execSQL(
                "INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, " +
                    "oa_pdf_url, saved_at) VALUES ('b', 'W2', NULL, 'تطبيقات التَّعلُّم العميق', NULL, NULL, NULL, 0, 0, NULL, 200)"
            )
        }
    }

    /** A version 2 library as sub-project 3 left it: statuses mixed, search rows already normalized. */
    private fun createVersion2() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            db.execSQL(
                "INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, " +
                    "oa_pdf_url, saved_at, reading_status) VALUES ('a', 'W1', NULL, 'Attention Is All You Need', 2017, " +
                    "'Neural Information Processing Systems', 'The dominant sequence transduction models', 128412, 1, NULL, 100, 'reading')"
            )
            db.execSQL("INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 0, 'Ashish Vaswani', NULL)")
            db.execSQL("INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 1, 'Noam Shazeer', NULL)")
            db.execSQL(
                "INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, " +
                    "oa_pdf_url, saved_at, reading_status) VALUES ('b', 'W2', NULL, 'تطبيقات التَّعلُّم العميق', NULL, NULL, NULL, 0, 0, " +
                    "NULL, 200, 'to_read')"
            )
            db.execSQL(
                "INSERT INTO paper_search (paper_id, title, authors, abstract, venue) VALUES ('a', 'attention is all you need', " +
                    "'ashish vaswani noam shazeer', 'the dominant sequence transduction models', 'neural information processing systems')"
            )
            db.execSQL(
                "INSERT INTO paper_search (paper_id, title, authors, abstract, venue) VALUES ('b', 'تطبيقات التعلم العميق', '', '', '')"
            )
        }
    }

    private fun openWithRoom(): HashiyaDatabase =
        Room.databaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

    private fun SupportSQLiteDatabase.strings(sql: String): List<String> = query(sql).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    @Test
    fun migrationKeepsEveryPaperAsToReadAndValidatesAgainstVersion2Schema() {
        createVersion1()

        // Validates every table, including paper_search, against schemas/…/2.json.
        helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2).use { db ->
            assertEquals(listOf("a:to_read", "b:to_read"), db.strings("SELECT id || ':' || reading_status FROM papers ORDER BY id"))
            assertEquals(listOf("Ashish Vaswani", "Noam Shazeer"), db.strings("SELECT name FROM paper_authors ORDER BY position"))
            assertEquals(
                listOf("a:attention is all you need:ashish vaswani noam shazeer", "b:تطبيقات التعلم العميق:"),
                db.strings("SELECT paper_id || ':' || title || ':' || authors FROM paper_search ORDER BY paper_id")
            )
        }
    }

    @Test
    fun migratedLibraryOpensWithRoomAndIsSearchable() = runTest {
        createVersion1()
        helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2).close()

        val database = openWithRoom()
        try {
            val dao = database.paperDao()
            suspend fun ids(match: String) = dao.observeLibrary(match, null).first().map { it.paper.id }

            assertEquals(listOf("b", "a"), dao.observeLibrary(null, null).first().map { it.paper.id })
            assertEquals(listOf("a"), ids("\"attention*\""))
            assertEquals(listOf("a"), ids("\"shazeer*\""))
            assertEquals(listOf("a"), ids("\"transduction*\""))
            assertEquals(listOf("a"), ids("\"neural*\" \"processing*\""))
            assertEquals(listOf("b"), ids("\"التعلم*\""))
        } finally {
            database.close()
        }
    }

    @Test
    fun migration2To3KeepsPapersStatusesAndSearchRowsAndValidatesAgainstVersion3Schema() {
        createVersion2()

        // Validates every table, including paper_notes and the rebuilt paper_search, against schemas/…/3.json.
        helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3).use { db ->
            assertEquals(listOf("a:reading", "b:to_read"), db.strings("SELECT id || ':' || reading_status FROM papers ORDER BY id"))
            assertEquals(
                listOf("a:attention is all you need:ashish vaswani noam shazeer:", "b:تطبيقات التعلم العميق::"),
                db.strings("SELECT paper_id || ':' || title || ':' || authors || ':' || notes FROM paper_search ORDER BY paper_id")
            )
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM paper_notes"))
        }
    }

    @Test
    fun libraryMigratedFromVersion2FindsTheSamePapersAndTakesNotes() = runTest {
        createVersion2()
        helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3).close()

        val database = openWithRoom()
        try {
            val dao = database.paperDao()
            suspend fun ids(match: String) = dao.observeLibrary(match, null).first().map { it.paper.id }

            assertEquals(listOf("a"), ids("\"shazeer*\""))
            assertEquals(listOf("a"), ids("\"transduction*\""))
            assertEquals(listOf("b"), ids("\"التعلم*\""))
            assertTrue(dao.saveNotes("W1", PaperNotes(method = "Ablation study"), updatedAt = 1))
            assertEquals(listOf("a"), ids("\"ablation*\""))
        } finally {
            database.close()
        }
    }

    @Test
    fun version1LibraryMigratesAllTheWayToVersion3() {
        createVersion1()

        helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_1_2, MIGRATION_2_3).use { db ->
            assertEquals(listOf("a:to_read", "b:to_read"), db.strings("SELECT id || ':' || reading_status FROM papers ORDER BY id"))
            assertEquals(
                listOf("a:attention is all you need:"),
                db.strings("SELECT paper_id || ':' || title || ':' || notes FROM paper_search WHERE paper_id = 'a'")
            )
        }
    }
}
