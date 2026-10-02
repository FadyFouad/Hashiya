package com.etatech.hashiya.core.database.migration

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.database.model.PDF_SOURCE_DOWNLOADED
import com.etatech.hashiya.core.database.model.PdfColumns
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

    /** A version 3 library as sub-project 4 left it: one paper with a note and a status, one bare. */
    private fun createVersion3() {
        helper.createDatabase(DB_NAME, 3).use { db ->
            db.execSQL(
                "INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, " +
                    "oa_pdf_url, saved_at, reading_status) VALUES ('a', 'W1', '10.48550/arxiv.1706.03762', " +
                    "'Attention Is All You Need', 2017, 'Neural Information Processing Systems', " +
                    "'The dominant sequence transduction models', 128412, 1, NULL, 100, 'read')"
            )
            db.execSQL("INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 0, 'Ashish Vaswani', NULL)")
            db.execSQL(
                "INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, " +
                    "oa_pdf_url, saved_at, reading_status) VALUES ('b', 'W2', NULL, 'تطبيقات التَّعلُّم العميق', NULL, NULL, NULL, 0, 0, " +
                    "NULL, 200, 'to_read')"
            )
            db.execSQL(
                "INSERT INTO paper_notes (paper_id, summary, research_question, method, key_findings, limitations, thoughts, updated_at) " +
                    "VALUES ('a', 'Transformers', '', 'Ablation study', '', '', '', 5)"
            )
            db.execSQL(
                "INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes) VALUES ('a', 'attention is all you need', " +
                    "'ashish vaswani', 'the dominant sequence transduction models', 'neural information processing systems', " +
                    "'transformers ablation study')"
            )
            db.execSQL(
                "INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes) " +
                    "VALUES ('b', 'تطبيقات التعلم العميق', '', '', '', '')"
            )
        }
    }

    /**
     * A version 4 library as sub-project 5 left it: paper a with a status, a note, publication details, a cite key and a
     * collection; paper b bare and never fetched.
     */
    private fun createVersion4() {
        helper.createDatabase(DB_NAME, 4).use { db ->
            db.execSQL(
                "INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, " +
                    "oa_pdf_url, saved_at, reading_status, work_type, source_type, publisher, volume, issue, first_page, last_page, " +
                    "cite_key, details_fetched) VALUES ('a', 'W1', '10.48550/arxiv.1706.03762', 'Attention Is All You Need', 2017, " +
                    "'Neural Information Processing Systems', 'The dominant sequence transduction models', 128412, 1, " +
                    "'https://arxiv.org/pdf/1706.03762', 100, 'reading', 'article', 'conference', NULL, '30', NULL, '5998', '6008', " +
                    "'vaswani2017attention', 1)"
            )
            db.execSQL("INSERT INTO paper_authors (paper_id, position, name, open_alex_author_id) VALUES ('a', 0, 'Ashish Vaswani', NULL)")
            db.execSQL(
                "INSERT INTO papers (id, open_alex_id, doi, title, year, venue, abstract, citation_count, is_open_access, " +
                    "oa_pdf_url, saved_at, reading_status) VALUES ('b', 'W2', NULL, 'تطبيقات التَّعلُّم العميق', NULL, NULL, NULL, 0, 0, " +
                    "NULL, 200, 'to_read')"
            )
            db.execSQL(
                "INSERT INTO paper_notes (paper_id, summary, research_question, method, key_findings, limitations, thoughts, updated_at) " +
                    "VALUES ('a', 'Transformers', '', 'Ablation study', '', '', '', 5)"
            )
            db.execSQL(
                "INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes) VALUES ('a', 'attention is all you need', " +
                    "'ashish vaswani', 'the dominant sequence transduction models', 'neural information processing systems', " +
                    "'transformers ablation study')"
            )
            db.execSQL(
                "INSERT INTO paper_search (paper_id, title, authors, abstract, venue, notes) " +
                    "VALUES ('b', 'تطبيقات التعلم العميق', '', '', '', '')"
            )
            db.execSQL("INSERT INTO collections (id, name, name_key, created_at) VALUES (1, 'Thesis', 'thesis', 7)")
            db.execSQL("INSERT INTO collection_papers (collection_id, paper_id, added_at) VALUES (1, 'a', 8)")
        }
    }

    private fun openWithRoom(): HashiyaDatabase =
        Room.databaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
            suspend fun ids(match: String) = dao.observeLibrary(match, null, null).first().map { it.paper.id }

            assertEquals(listOf("b", "a"), dao.observeLibrary(null, null, null).first().map { it.paper.id })
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
            suspend fun ids(match: String) = dao.observeLibrary(match, null, null).first().map { it.paper.id }

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

    @Test
    fun migration3To4KeepsEverythingAndValidatesAgainstVersion4Schema() {
        createVersion3()

        // Validates every table and index, including collections, collection_papers and the cite_key index, against 4.json.
        helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4).use { db ->
            assertEquals(listOf("a:read", "b:to_read"), db.strings("SELECT id || ':' || reading_status FROM papers ORDER BY id"))
            assertEquals(listOf("Ablation study"), db.strings("SELECT method FROM paper_notes"))
            assertEquals(
                listOf("a:transformers ablation study", "b:"),
                db.strings("SELECT paper_id || ':' || notes FROM paper_search ORDER BY paper_id")
            )
            assertEquals(
                listOf("a:0:1:1", "b:0:1:1"),
                db.strings(
                    "SELECT id || ':' || details_fetched || ':' || (cite_key IS NULL) || ':' || (work_type IS NULL AND volume IS NULL) " +
                        "FROM papers ORDER BY id"
                )
            )
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM collections"))
            assertEquals(listOf("0"), db.strings("SELECT COUNT(*) FROM collection_papers"))
        }
    }

    @Test
    fun libraryMigratedFromVersion3IsSearchableAndTakesCollections() = runTest {
        createVersion3()
        helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4).close()

        val database = openWithRoom()
        try {
            val dao = database.paperDao()
            suspend fun ids(match: String?, collectionId: Long? = null) =
                dao.observeLibrary(match, null, collectionId).first().map { it.paper.id }

            assertEquals(listOf("a"), ids("\"ablation*\""))
            assertEquals(listOf("b"), ids("\"التعلم*\""))
            val collections = database.collectionDao()
            val id = checkNotNull(collections.insertCollection("Thesis", "thesis", createdAt = 1))
            collections.addToCollection(id, "W1", addedAt = 2)
            assertEquals(listOf("a"), ids(null, id))
        } finally {
            database.close()
        }
    }

    @Test
    fun version1LibraryMigratesAllTheWayToVersion4() {
        createVersion1()

        helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).use { db ->
            assertEquals(
                listOf("a:to_read:0", "b:to_read:0"),
                db.strings("SELECT id || ':' || reading_status || ':' || details_fetched FROM papers ORDER BY id")
            )
        }
    }

    @Test
    fun migration4To5KeepsEverythingAndValidatesAgainstVersion5Schema() {
        createVersion4()

        // Validates every table and index against 5.json, so the four pdf_ columns must match Room's entity exactly.
        helper.runMigrationsAndValidate(DB_NAME, 5, true, MIGRATION_4_5).use { db ->
            assertEquals(listOf("a:reading", "b:to_read"), db.strings("SELECT id || ':' || reading_status FROM papers ORDER BY id"))
            assertEquals(
                listOf("a:vaswani2017attention:1:30", "b:-:0:-"),
                db.strings(
                    "SELECT id || ':' || COALESCE(cite_key, '-') || ':' || details_fetched || ':' || COALESCE(volume, '-') " +
                        "FROM papers ORDER BY id"
                )
            )
            assertEquals(listOf("Ablation study"), db.strings("SELECT method FROM paper_notes"))
            assertEquals(listOf("1:a:8"), db.strings("SELECT collection_id || ':' || paper_id || ':' || added_at FROM collection_papers"))
            assertEquals(
                listOf("a:transformers ablation study", "b:"),
                db.strings("SELECT paper_id || ':' || notes FROM paper_search ORDER BY paper_id")
            )
            assertEquals(
                listOf("a:1", "b:1"),
                db.strings(
                    "SELECT id || ':' || (pdf_source IS NULL AND pdf_size IS NULL AND pdf_added_at IS NULL AND pdf_last_page IS NULL) " +
                        "FROM papers ORDER BY id"
                )
            )
        }
    }

    @Test
    fun libraryMigratedFromVersion4IsSearchableHasNoPdfsAndTakesOne() = runTest {
        createVersion4()
        helper.runMigrationsAndValidate(DB_NAME, 5, true, MIGRATION_4_5).close()

        val database = openWithRoom()
        try {
            val dao = database.paperDao()
            assertEquals(listOf("a"), dao.observeLibrary("\"ablation*\"", null, 1L).first().map { it.paper.id })
            assertEquals(listOf(null, null), dao.observeLibrary(null, null, null).first().map { it.paper.pdfSource })
            assertEquals(null, dao.observePdf("W1").first())

            dao.setPdf(checkNotNull(dao.paperIdFor("W1")), PDF_SOURCE_DOWNLOADED, size = 2_400_000, addedAt = 9)

            assertEquals(PdfColumns(PDF_SOURCE_DOWNLOADED, 2_400_000, 9, 0), dao.observePdf("W1").first())
        } finally {
            database.close()
        }
    }

    @Test
    fun version1LibraryMigratesAllTheWayToVersion5() {
        createVersion1()

        helper.runMigrationsAndValidate(DB_NAME, 5, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).use { db ->
            assertEquals(
                listOf("a:to_read:0:1", "b:to_read:0:1"),
                db.strings(
                    "SELECT id || ':' || reading_status || ':' || details_fetched || ':' || (pdf_source IS NULL) FROM papers ORDER BY id"
                )
            )
        }
    }
}
