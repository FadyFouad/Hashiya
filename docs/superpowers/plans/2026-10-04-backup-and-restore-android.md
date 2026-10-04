# Backup and Restore (Android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Export the library to a `.hashiya` file (optionally with PDFs), restore one by merging it into the library, and fix Android Auto Backup so it keeps the database and settings without PDFs.

**Architecture:** A `LibraryBackup` unit in `core/data` writes and reads the archive (`java.util.zip` + kotlinx.serialization models kept apart from the Room entities). A new `BackupDao` in `core/database` reads a consistent snapshot and applies the merge in one Room transaction. PDFs are staged as `.part` files in the PDF folder before the transaction and renamed into place after it, all under a `PdfStoreGate` shared with `RoomPdfRepository` so the startup sweep can't delete them. Settings gets a Backup section and export dialog; a new Restore screen in `feature/settings` is reached from Settings or by opening a `.hashiya` file.

**Tech Stack:** Kotlin, Room 2.8, kotlinx.serialization 1.11, `java.util.zip`, Jetpack Compose + Material 3, Hilt, Navigation Compose (type-safe routes), Robolectric + Roborazzi tests.

**Spec:** `docs/superpowers/specs/2026-10-04-backup-and-restore-design.md`

## Global Constraints

- minSdk 24 with no core library desugaring: no `java.time`, no `InputStream.readNBytes`/`readAllBytes` (API 33). Use `SimpleDateFormat` and manual read loops.
- Format number `1`. A reader rejects a higher `format`; unknown JSON fields are ignored.
- Archive entries: `manifest.json`, `library.json`, `pdfs/<ref>.pdf`. Suggested file name `Hashiya-library-YYYY-MM-DD.hashiya`. Media type `application/zip`.
- `library.json` is capped at 50 MB (`50 * 1024 * 1024` bytes).
- Restore merges; the device wins every conflict. No Replace mode.
- The export file never contains settings or the API key.
- Matching: same `openAlexId` when the backup paper has one; only when it has none, the same normalized DOI (`normalizeDoi` from `core:model`); otherwise new. (DOIs are deliberately not unique in this app — preprint and published version can both be saved — so a backup paper with an OpenAlex id never merges by DOI.)
- No local database ids in the file; papers carry a file-local `ref`.
- All new strings in English (`values/strings.xml`) and Arabic (`values-ar/strings.xml`), Arabic plurals with all six quantities (`zero one two few many other`), as the existing files do.
- Commits authored as `Fady <fady.fouad.a@gmail.com>`; no AI attribution anywhere.
- Run Gradle from `android/`. Unit tests: `./gradlew :<module>:testDebugUnitTest --tests '<class>'`. Screenshots are recorded on CI (Linux); locally run `verifyRoborazziDebug` only if baselines exist, otherwise leave recording to CI as earlier features did.

## Review Focus

1. **A `.hashiya` from Drive arrives as `application/octet-stream` or with a mangled name** — opening it from Files must still reach the restore screen when its type is zip, and the in-app picker must accept octet-stream; a non-backup zip must say "This isn't a Hashiya backup", never crash. (Task 9 picker MIME list + Task 6 `notAZip`/`zipWithoutManifest` tests.)
2. **Restoring the same backup twice** adds nothing the second time and doesn't duplicate collection links or notes. (Task 7 `restoringTwiceAddsNothing`.)
3. **The app is killed between the merge transaction and the PDF renames** — rows point at files that aren't there; the next startup sweep must clear those rows rather than leave a broken "Read PDF". (Task 2 sweep test + Task 7 staged files are `.part`, which the sweep removes.)
4. **A backup paper whose `pdf.file` is not exactly `pdfs/<its ref>.pdf`** (e.g. `../../databases/hashiya.db` or another paper's entry) must be treated as missing, never read. (Task 7 `pdfEntryNameMustMatchRef`.)
5. **Rotating or leaving the screen mid-export/restore** — the ViewModel owns the work, so it continues and the temp file is still cleaned up; leaving Restore before applying deletes the copied archive. (Task 8 `dismissDiscardsExportedFile`, Task 9 `cancelDiscards`.)

---

## File Structure

**core/model** — unchanged (uses `normalizeDoi`, `collectionNameKey`).

**core/database**
- Create `model/BackupRows.kt` — `LibrarySnapshot`, `IncomingPaper`, `IncomingCollection`, `MergeOutcome`, `PdfTotals`.
- Create `dao/BackupDao.kt` — snapshot, counts, matching, merge transaction.
- Modify `HashiyaDatabase.kt` (add `backupDao()`), `di/DatabaseModule.kt` (provide it), `dao/PaperDao.kt` (nothing; sweep uses existing `clearPdf`).
- Test `dao/BackupDaoTest.kt`.

**core/data**
- Modify `build.gradle.kts` — serialization plugin, `testdata` system property.
- Create `backup/BackupFormat.kt` — serializable archive models, constants, `backupJson`.
- Create `backup/BackupArchive.kt` — pure write/read of the zip (no Android, no DB).
- Create `backup/LibraryBackup.kt` — public interface and result types.
- Create `backup/ArchiveLibraryBackup.kt` — the implementation.
- Create `di/BackupModule.kt` — Hilt binding and the cache dir / version providers.
- Create `pdf/PdfStoreGate.kt`; modify `pdf/PdfFileStore.kt` (stage/commit), `repository/RoomPdfRepository.kt` (gate, sweep clears missing rows).
- Tests: `backup/BackupFormatTest.kt`, `backup/BackupArchiveTest.kt`, `backup/ArchiveLibraryBackupTest.kt`, additions to `pdf/PdfFileStoreTest.kt` and `repository/RoomPdfRepositoryTest.kt`.

**core/testing**
- Create `FakeLibraryBackup.kt`.

**feature/settings**
- Modify `SettingsViewModel.kt`, `SettingsScreen.kt`, `navigation/SettingsNavigation.kt`, strings (en/ar).
- Create `BackupSection.kt` (section + export dialog), `restore/RestoreViewModel.kt`, `restore/RestoreScreen.kt`, `navigation/RestoreNavigation.kt`.
- Tests: `SettingsBackupViewModelTest.kt`, `restore/RestoreViewModelTest.kt`, screenshot additions, `SettingsContentTest` addition.

**app**
- Modify `AndroidManifest.xml` (VIEW filter), `MainActivity.kt` (pending restore), `navigation/HashiyaApp.kt` (routes), `res/xml/data_extraction_rules.xml`, `res/xml/backup_rules.xml`.
- Tests: `share/BackupIntentFilterTest.kt`, `BackupRulesTest.kt`.

**repo root**
- Create `testdata/backup/format-1/{manifest.json,library.json,pdfs/1.pdf}`, `testdata/backup/format-1.hashiya`, `scripts/make-backup-fixture.sh`.

---

### Task 1: Archive format models and the shared fixture

**Files:**
- Modify: `android/core/data/build.gradle.kts`
- Create: `android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/BackupFormat.kt`
- Create: `testdata/backup/format-1/manifest.json`, `testdata/backup/format-1/library.json`, `testdata/backup/format-1/pdfs/1.pdf`
- Create: `scripts/make-backup-fixture.sh`, generated `testdata/backup/format-1.hashiya`
- Test: `android/core/data/src/test/java/com/etatech/hashiya/core/data/backup/BackupFormatTest.kt`

**Interfaces:**
- Produces: `BACKUP_FORMAT`, `MANIFEST_ENTRY`, `LIBRARY_ENTRY`, `MAX_LIBRARY_BYTES`, `pdfEntryName(ref: Int): String`, `BackupManifest`, `BackupLibrary`, `BackupPaper`, `BackupAuthor`, `BackupNotes`, `BackupPdf`, `BackupCollection`, `backupJson: Json` (all `internal`, package `com.etatech.hashiya.core.data.backup`). Tests find the fixture through system property `hashiya.testdata`.

- [ ] **Step 1: Add the serialization plugin and the fixture path to core/data**

In `android/core/data/build.gradle.kts`:

```kotlin
plugins {
    id("hashiya.android.library")
    id("hashiya.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.etatech.hashiya.core.data"
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.paging.common)
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:bibtex"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(libs.kotlinx.serialization.json)

    // (existing test dependencies unchanged)
}

// The backup fixture shared with iOS lives at the repository root.
tasks.withType<Test>().configureEach {
    systemProperty("hashiya.testdata", rootProject.file("../testdata").absolutePath)
}
```

Keep the existing `testImplementation`/`androidTestImplementation` lines as they are.

- [ ] **Step 2: Write the fixture files**

`testdata/backup/format-1/manifest.json`:

```json
{ "format": 1, "app": "0.3.0 (Android)", "exportedAt": "2026-10-04T14:05:00Z", "papers": 3, "collections": 2, "includesPdfs": true }
```

`testdata/backup/format-1/library.json`:

```json
{
  "papers": [
    {
      "ref": 1,
      "openAlexId": "W2741809807",
      "doi": "10.1038/nature14539",
      "title": "Deep learning",
      "year": 2015,
      "venue": "Nature",
      "abstract": "Deep learning allows computational models…",
      "citationCount": 54000,
      "isOpenAccess": true,
      "oaPdfUrl": "https://example.org/deep.pdf",
      "savedAt": 1790000000000,
      "readingStatus": "reading",
      "workType": "article",
      "sourceType": "journal",
      "publisher": "Springer Nature",
      "volume": "521",
      "issue": "7553",
      "firstPage": "436",
      "lastPage": "444",
      "citeKey": "lecun2015deep",
      "detailsFetched": true,
      "authors": [
        { "name": "Yann LeCun", "openAlexAuthorId": "A1" },
        { "name": "Yoshua Bengio", "openAlexAuthorId": "A2" },
        { "name": "Geoffrey Hinton", "openAlexAuthorId": null }
      ],
      "notes": {
        "summary": "Review of deep learning.",
        "researchQuestion": "",
        "method": "Survey",
        "keyFindings": "",
        "limitations": "",
        "thoughts": "Cite in chapter 2",
        "updatedAt": 1790000100000
      },
      "pdf": { "source": "downloaded", "addedAt": 1790000200000, "lastPage": 4, "file": "pdfs/1.pdf" },
      "someFutureField": "ignored"
    },
    {
      "ref": 2,
      "title": "التعلم العميق في معالجة اللغة العربية",
      "savedAt": 1790000300000,
      "readingStatus": "to_read",
      "authors": [ { "name": "فاطمة الزهراء" } ],
      "notes": null,
      "pdf": { "source": "attached", "addedAt": 1790000400000, "lastPage": 0, "file": null }
    },
    {
      "ref": 3,
      "openAlexId": "W3",
      "doi": "10.1000/xyz",
      "title": "Attention is all you need",
      "year": 2017,
      "citationCount": 10,
      "isOpenAccess": false,
      "savedAt": 1790000500000,
      "readingStatus": "read",
      "authors": [],
      "pdf": null
    }
  ],
  "collections": [
    { "name": "Thesis", "createdAt": 1790000600000, "papers": [1, 2] },
    { "name": "مراجعة", "createdAt": 1790000700000, "papers": [3] }
  ]
}
```

`testdata/backup/format-1/pdfs/1.pdf` — a minimal PDF (exact bytes, ends with a newline):

```
%PDF-1.4
1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
2 0 obj << /Type /Pages /Kids [] /Count 0 >> endobj
trailer << /Root 1 0 R >>
%%EOF
```

- [ ] **Step 3: Write the fixture script and generate the archive**

`scripts/make-backup-fixture.sh`:

```bash
#!/usr/bin/env bash
# Zips testdata/backup/format-1/ into testdata/backup/format-1.hashiya, the backup both platforms' tests decode.
set -euo pipefail
cd "$(dirname "$0")/../testdata/backup/format-1"
rm -f ../format-1.hashiya
zip -X -q -r ../format-1.hashiya manifest.json library.json pdfs
echo "Wrote testdata/backup/format-1.hashiya"
```

Run: `chmod +x scripts/make-backup-fixture.sh && scripts/make-backup-fixture.sh`
Expected: `Wrote testdata/backup/format-1.hashiya`

- [ ] **Step 4: Write the failing test**

`android/core/data/src/test/java/com/etatech/hashiya/core/data/backup/BackupFormatTest.kt`:

```kotlin
package com.etatech.hashiya.core.data.backup

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {
    private val fixture = File(System.getProperty("hashiya.testdata"), "backup/format-1.hashiya")

    private fun ZipFile.text(name: String) = getInputStream(getEntry(name)).use { it.reader(Charsets.UTF_8).readText() }

    @Test
    fun decodesTheSharedFixture() {
        ZipFile(fixture).use { zip ->
            val manifest = backupJson.decodeFromString(BackupManifest.serializer(), zip.text(MANIFEST_ENTRY))
            assertEquals(1, manifest.format)
            assertTrue(manifest.includesPdfs)

            val library = backupJson.decodeFromString(BackupLibrary.serializer(), zip.text(LIBRARY_ENTRY))
            assertEquals(listOf(1, 2, 3), library.papers.map { it.ref })

            val first = library.papers[0]
            assertEquals("W2741809807", first.openAlexId)
            assertEquals("lecun2015deep", first.citeKey)
            assertEquals(listOf("Yann LeCun", "Yoshua Bengio", "Geoffrey Hinton"), first.authors.map { it.name })
            assertNull(first.authors[2].openAlexAuthorId)
            assertEquals("Cite in chapter 2", first.notes?.thoughts)
            assertEquals(BackupPdf("downloaded", 1790000200000, 4, "pdfs/1.pdf"), first.pdf)

            val second = library.papers[1]
            assertNull(second.openAlexId)
            assertNull(second.doi)
            assertEquals("التعلم العميق في معالجة اللغة العربية", second.title)
            assertEquals(0, second.citationCount)
            assertNull(second.pdf?.file)

            assertEquals("read", library.papers[2].readingStatus)
            assertEquals(listOf(BackupCollection("Thesis", 1790000600000, listOf(1, 2)), BackupCollection("مراجعة", 1790000700000, listOf(3))), library.collections)
            assertTrue(zip.getEntry(pdfEntryName(1)) != null)
        }
    }

    @Test
    fun roundTripsAPaper() {
        val paper = BackupPaper(ref = 7, title = "T", savedAt = 5, notes = BackupNotes(summary = "s", updatedAt = 6))
        val text = backupJson.encodeToString(BackupLibrary.serializer(), BackupLibrary(papers = listOf(paper)))
        assertEquals(paper, backupJson.decodeFromString(BackupLibrary.serializer(), text).papers.single())
    }
}
```

- [ ] **Step 5: Run it to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests 'com.etatech.hashiya.core.data.backup.BackupFormatTest'`
Expected: compilation FAILS — `BackupManifest`, `backupJson` unresolved.

- [ ] **Step 6: Write the models**

`android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/BackupFormat.kt`:

```kotlin
package com.etatech.hashiya.core.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The `.hashiya` archive, format 1 (docs/superpowers/specs/2026-10-04-backup-and-restore-design.md §3). These types are the
 * file format: they are kept apart from the Room entities so a schema change never silently changes what a backup holds.
 */
internal const val BACKUP_FORMAT = 1
internal const val MANIFEST_ENTRY = "manifest.json"
internal const val LIBRARY_ENTRY = "library.json"
internal const val MAX_LIBRARY_BYTES = 50 * 1024 * 1024
internal const val MAX_MANIFEST_BYTES = 64 * 1024

/** The only entry a paper's PDF may be read from; any other `pdf.file` counts as missing. */
internal fun pdfEntryName(ref: Int): String = "pdfs/$ref.pdf"

@Serializable
internal data class BackupManifest(
    val format: Int,
    val app: String = "",
    /** ISO 8601 in UTC, e.g. `2026-10-04T14:05:00Z`. */
    val exportedAt: String = "",
    val papers: Int = 0,
    val collections: Int = 0,
    val includesPdfs: Boolean = false
)

@Serializable
internal data class BackupLibrary(
    val papers: List<BackupPaper> = emptyList(),
    val collections: List<BackupCollection> = emptyList()
)

@Serializable
internal data class BackupPaper(
    /** Unique within the file; collections and the PDF entry refer to it. */
    val ref: Int,
    val openAlexId: String? = null,
    val doi: String? = null,
    val title: String,
    val year: Int? = null,
    val venue: String? = null,
    val abstract: String? = null,
    val citationCount: Int = 0,
    val isOpenAccess: Boolean = false,
    val oaPdfUrl: String? = null,
    val savedAt: Long,
    /** `to_read`, `reading` or `read`; anything else restores as `to_read`. */
    val readingStatus: String = "to_read",
    val workType: String? = null,
    val sourceType: String? = null,
    val publisher: String? = null,
    val volume: String? = null,
    val issue: String? = null,
    val firstPage: String? = null,
    val lastPage: String? = null,
    val citeKey: String? = null,
    val detailsFetched: Boolean = false,
    /** In position order. */
    val authors: List<BackupAuthor> = emptyList(),
    val notes: BackupNotes? = null,
    val pdf: BackupPdf? = null
)

@Serializable
internal data class BackupAuthor(val name: String, val openAlexAuthorId: String? = null)

@Serializable
internal data class BackupNotes(
    val summary: String = "",
    val researchQuestion: String = "",
    val method: String = "",
    val keyFindings: String = "",
    val limitations: String = "",
    val thoughts: String = "",
    val updatedAt: Long = 0
)

/** A stored PDF. [file] is null when the PDF isn't in the archive (PDFs left out, or missing at export). */
@Serializable
internal data class BackupPdf(val source: String, val addedAt: Long, val lastPage: Int = 0, val file: String? = null)

@Serializable
internal data class BackupCollection(val name: String, val createdAt: Long, val papers: List<Int> = emptyList())

internal val backupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew :core:data:testDebugUnitTest --tests 'com.etatech.hashiya.core.data.backup.BackupFormatTest'`
Expected: PASS (2 tests).

- [ ] **Step 8: Commit**

```bash
git add android/core/data/build.gradle.kts android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/BackupFormat.kt \
  android/core/data/src/test/java/com/etatech/hashiya/core/data/backup/BackupFormatTest.kt testdata/backup scripts/make-backup-fixture.sh
git commit -m "feat(android): backup archive format and shared fixture"
```

---

### Task 2: The startup sweep clears rows whose PDF file is gone

**Files:**
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/repository/RoomPdfRepository.kt` (`sweepOrphans`)
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/repository/PdfRepository.kt` (doc comment)
- Test: `android/core/data/src/test/java/com/etatech/hashiya/core/data/repository/RoomPdfRepositoryTest.kt`

**Interfaces:**
- Consumes: `PaperDao.pdfPaperIds()`, `PaperDao.clearPdf(paperId)`, `PdfFileStore.file(paperId)`, `PdfFileStore.sweep(keep)`.
- Produces: `sweepOrphans()` now also clears the four `pdf_*` columns of rows whose file is missing.

- [ ] **Step 1: Write the failing test**

Add to `RoomPdfRepositoryTest` (next to `sweepOrphansDeletesFilesWithoutARow`):

```kotlin
@Test
fun sweepOrphansClearsRowsWhoseFileIsGone() = runTest {
    library.save(paper("W1"))
    library.save(paper("W2"))
    // W1's row says a PDF is stored, but its file is gone (an OS restore without PDFs, or a lost file).
    db.paperDao().setPdf("local-1", "downloaded", 10, 1)
    db.paperDao().setPdf("local-2", "attached", 10, 1)
    dir.mkdirs()
    File(dir, "local-2.pdf").writeText("%PDF-1.4")

    repository.sweepOrphans()

    assertNull(repository.observePdf("W1").first())
    assertEquals(PdfSource.Attached, repository.observePdf("W2").first()?.source)
    assertTrue(File(dir, "local-2.pdf").exists())
}
```

Add `import org.junit.Assert.assertTrue` if missing.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*RoomPdfRepositoryTest.sweepOrphansClearsRowsWhoseFileIsGone'`
Expected: FAIL — `observePdf("W1")` is not null.

- [ ] **Step 3: Implement**

Replace `sweepOrphans` in `RoomPdfRepository`:

```kotlin
override suspend fun sweepOrphans() {
    // A download or attach running alongside would lose its temporary file, or its new file before its row is set.
    sweepLock.withLock {
        activeStores.first { it == 0 }
        val stored = paperDao.pdfPaperIds().toSet()
        // Rows whose file is gone (an OS restore leaves PDFs out) go back to "no PDF", so Details offers to download it again.
        val missing = withContext(io) { stored.filterNot { fileStore.file(it).exists() } }
        missing.forEach { paperDao.clearPdf(it) }
        withContext(io) { fileStore.sweep(stored - missing.toSet()) }
    }
}
```

Update the doc comment in `PdfRepository`:

```kotlin
/**
 * Deletes files no paper points at and leftover temporary files, and clears the PDF of papers whose file is gone. Called once at
 * startup.
 */
suspend fun sweepOrphans()
```

- [ ] **Step 4: Run the repository tests**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*RoomPdfRepositoryTest'`
Expected: PASS (all, including the existing sweep test).

- [ ] **Step 5: Commit**

```bash
git add android/core/data/src/main/java/com/etatech/hashiya/core/data/repository android/core/data/src/test/java/com/etatech/hashiya/core/data/repository/RoomPdfRepositoryTest.kt
git commit -m "feat(android): startup sweep clears PDFs whose file is gone"
```

---

### Task 3: A shared store gate and staged PDF writes

**Files:**
- Create: `android/core/data/src/main/java/com/etatech/hashiya/core/data/pdf/PdfStoreGate.kt`
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/pdf/PdfFileStore.kt`
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/repository/RoomPdfRepository.kt`
- Test: `android/core/data/src/test/java/com/etatech/hashiya/core/data/pdf/PdfFileStoreTest.kt`, `PdfStoreGateTest.kt`

**Interfaces:**
- Produces:
  - `internal class PdfStoreGate @Inject constructor()` (`@Singleton`) with `suspend fun <T> storing(block: suspend () -> T): T` and `suspend fun <T> sweeping(block: suspend () -> T): T`.
  - `PdfFileStore.stage(prefix: String, input: InputStream, maxBytes: Long, onProgress: (Long) -> Unit): StageResult`
  - `internal sealed interface StageResult { data class Staged(val file: File, val size: Long); data object NotPdf; data object TooLarge }`
  - `PdfFileStore.commit(staged: File, paperId: String)` — throws `PdfWriteException`.
  - `PdfFileStore.usableSpace(): Long`
  - `RoomPdfRepository` takes a `gate: PdfStoreGate` (last parameter of the internal constructor, default `PdfStoreGate()`; required in the `@Inject` constructor).

- [ ] **Step 1: Write the failing tests**

Add to `PdfFileStoreTest` (it already has a `TemporaryFolder` and a `store` under test; adapt the field names to the file's existing ones):

```kotlin
@Test
fun stageKeepsAPartFileAndCommitMovesItIntoPlace() {
    val store = PdfFileStore(dir)
    val staged = store.stage("restore", "%PDF-1.4 hello".byteInputStream(), maxBytes = 1024) {} as StageResult.Staged
    assertTrue(staged.file.name.endsWith(".part"))
    assertFalse(store.file("p1").exists())

    store.commit(staged.file, "p1")

    assertFalse(staged.file.exists())
    assertEquals("%PDF-1.4 hello", store.file("p1").readText())
    assertEquals(14L, staged.size)
}

@Test
fun stageRejectsANonPdfAndLeavesNothing() {
    val store = PdfFileStore(dir)
    assertEquals(StageResult.NotPdf, store.stage("restore", "<html>".byteInputStream(), maxBytes = 1024) {})
    assertTrue(dir.listFiles().orEmpty().isEmpty())
}

@Test
fun sweepDeletesUncommittedStagedFiles() {
    val store = PdfFileStore(dir)
    store.stage("restore", "%PDF-1.4".byteInputStream(), maxBytes = 1024) {}
    store.sweep(keep = emptySet())
    assertTrue(dir.listFiles().orEmpty().isEmpty())
}
```

`android/core/data/src/test/java/com/etatech/hashiya/core/data/pdf/PdfStoreGateTest.kt`:

```kotlin
package com.etatech.hashiya.core.data.pdf

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfStoreGateTest {
    @Test
    fun sweepWaitsForRunningStores() = runTest {
        val gate = PdfStoreGate()
        val events = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val store = launch { gate.storing { events += "store start"; release.await(); events += "store end" } }
        yield()
        val sweep = async { gate.sweeping { events += "sweep" } }
        yield()
        release.complete(Unit)
        store.join()
        sweep.await()
        assertEquals(listOf("store start", "store end", "sweep"), events)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*PdfFileStoreTest' --tests '*PdfStoreGateTest'`
Expected: compilation FAILS — `stage`, `StageResult`, `PdfStoreGate` unresolved.

- [ ] **Step 3: Write the gate**

`android/core/data/src/main/java/com/etatech/hashiya/core/data/pdf/PdfStoreGate.kt`:

```kotlin
package com.etatech.hashiya.core.data.pdf

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the startup sweep away from files being written. Anything that writes into the PDF folder and records it (a download,
 * an attach, a restore) runs inside [storing]; the sweep runs inside [sweeping], which waits until no store is running and keeps
 * new ones from starting until it is done.
 */
@Singleton
internal class PdfStoreGate @Inject constructor() {
    private val sweepLock = Mutex()
    private val activeStores = MutableStateFlow(0)

    suspend fun <T> storing(block: suspend () -> T): T {
        sweepLock.withLock { activeStores.update { it + 1 } }
        try {
            return block()
        } finally {
            activeStores.update { it - 1 }
        }
    }

    suspend fun <T> sweeping(block: suspend () -> T): T = sweepLock.withLock {
        activeStores.first { it == 0 }
        block()
    }
}
```

- [ ] **Step 4: Split `PdfFileStore.store` into stage and commit**

In `PdfFileStore.kt`, add the result type after `StoreResult`:

```kotlin
internal sealed interface StageResult {
    /** A checked PDF in a `.part` file in the store's folder; [PdfFileStore.commit] moves it into place, or the caller deletes it. */
    data class Staged(val file: File, val size: Long) : StageResult

    data object NotPdf : StageResult

    data object TooLarge : StageResult
}
```

Replace `store` with `stage`, `commit` and a `store` built from them:

```kotlin
/**
 * Copies [input] to a `.part` file named after [prefix] and checks it is a PDF of at most [maxBytes]. A rejected or failed copy
 * leaves no file. Read failures from [input] are thrown as they are; write failures throw [PdfWriteException]. A staged file that
 * is never committed or deleted is removed by the next [sweep].
 */
fun stage(prefix: String, input: InputStream, maxBytes: Long, onProgress: (Long) -> Unit): StageResult {
    val temp = try {
        dir.mkdirs()
        File.createTempFile("$prefix-", TEMP_SUFFIX, dir)
    } catch (e: IOException) {
        throw PdfWriteException(e)
    }
    var staged = false
    try {
        val head = ByteArray(HEADER_WINDOW)
        var headSize = 0
        var total = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        val output = try {
            temp.outputStream()
        } catch (e: IOException) {
            throw PdfWriteException(e)
        }
        output.use { out ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) return StageResult.TooLarge
                if (headSize < HEADER_WINDOW) {
                    val take = minOf(read, HEADER_WINDOW - headSize)
                    buffer.copyInto(head, destinationOffset = headSize, startIndex = 0, endIndex = take)
                    headSize += take
                    // A web page is usually small, but a large non-PDF shouldn't be read to the end before it is rejected.
                    if (headSize == HEADER_WINDOW && !head.containsPdfHeader(headSize)) return StageResult.NotPdf
                }
                try {
                    out.write(buffer, 0, read)
                } catch (e: IOException) {
                    throw PdfWriteException(e)
                }
                onProgress(total)
            }
            // Without this, a power loss just after the rename can leave an empty or partial `<paperId>.pdf`.
            try {
                out.fd.sync()
            } catch (e: IOException) {
                throw PdfWriteException(e)
            }
        }
        if (!head.containsPdfHeader(headSize)) return StageResult.NotPdf
        staged = true
        return StageResult.Staged(temp, total)
    } finally {
        if (!staged) temp.delete()
    }
}

/** Moves a staged file over `<paperId>.pdf`; rename(2) replaces the target atomically within one folder. */
fun commit(staged: File, paperId: String) {
    if (!staged.renameTo(file(paperId))) throw PdfWriteException(IOException("Couldn't move the PDF into place"))
}

/**
 * Stages [input] and moves it over `<paperId>.pdf`. A rejected or failed store leaves the current file, if any, as it was, and no
 * temporary file.
 */
fun store(paperId: String, input: InputStream, maxBytes: Long, onProgress: (Long) -> Unit): StoreResult =
    when (val result = stage(paperId, input, maxBytes, onProgress)) {
        is StageResult.Staged -> {
            try {
                commit(result.file, paperId)
            } catch (e: PdfWriteException) {
                result.file.delete()
                throw e
            }
            StoreResult.Stored(result.size)
        }

        StageResult.NotPdf -> StoreResult.NotPdf

        StageResult.TooLarge -> StoreResult.TooLarge
    }

/** Bytes free where the PDFs are kept. */
fun usableSpace(): Long {
    dir.mkdirs()
    return dir.usableSpace
}
```

- [ ] **Step 5: Make `RoomPdfRepository` use the gate**

In `RoomPdfRepository`:
1. Add `private val gate: PdfStoreGate = PdfStoreGate()` as the **last** parameter of the primary (internal) constructor, after `io`.
2. Add `gate: PdfStoreGate` to the `@Inject` secondary constructor and pass it: `this(paperDao, fileStore, downloader, pdfLinks, contentResolver, scope, System::currentTimeMillis, gate = gate)`.
3. Delete the `sweepLock` and `activeStores` fields and the private `storing` function; replace every `storing {` call with `gate.storing {`.
4. Rewrite `sweepOrphans` (keeping Task 2's behavior):

```kotlin
override suspend fun sweepOrphans() {
    // A download, attach or restore running alongside would lose its temporary file, or its new file before its row is set.
    gate.sweeping {
        val stored = paperDao.pdfPaperIds().toSet()
        // Rows whose file is gone (an OS restore leaves PDFs out) go back to "no PDF", so Details offers to download it again.
        val missing = withContext(io) { stored.filterNot { fileStore.file(it).exists() } }
        missing.forEach { paperDao.clearPdf(it) }
        withContext(io) { fileStore.sweep(stored - missing.toSet()) }
    }
}
```

Remove the now-unused imports (`Mutex`, `withLock`, `MutableStateFlow` only if no longer used — `downloads` still uses `MutableStateFlow`; keep `update`).

- [ ] **Step 6: Run the PDF tests**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*PdfFileStoreTest' --tests '*PdfStoreGateTest' --tests '*RoomPdfRepositoryTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add android/core/data/src/main/java/com/etatech/hashiya/core/data/pdf android/core/data/src/main/java/com/etatech/hashiya/core/data/repository/RoomPdfRepository.kt android/core/data/src/test/java/com/etatech/hashiya/core/data/pdf
git commit -m "refactor(android): shared PDF store gate and staged PDF writes"
```

---

### Task 4: BackupDao — snapshot, matching and the merge transaction

**Files:**
- Create: `android/core/database/src/main/java/com/etatech/hashiya/core/database/model/BackupRows.kt`
- Create: `android/core/database/src/main/java/com/etatech/hashiya/core/database/dao/BackupDao.kt`
- Modify: `android/core/database/src/main/java/com/etatech/hashiya/core/database/HashiyaDatabase.kt`, `di/DatabaseModule.kt`
- Test: `android/core/database/src/test/java/com/etatech/hashiya/core/database/dao/BackupDaoTest.kt`

**Interfaces:**
- Consumes: entities `PaperEntity`, `PaperAuthorEntity`, `PaperNotesEntity`, `CollectionEntity`, `CollectionPaperEntity`, `PaperWithAuthors`; `searchEntityFor`, `notesSearchText`, `asPaperNotes` (all in `core.database.model`).
- Produces (package `com.etatech.hashiya.core.database`):
  - `data class LibrarySnapshot(papers: List<PaperWithAuthors>, notes: List<PaperNotesEntity>, collections: List<CollectionEntity>, links: List<CollectionPaperEntity>)`
  - `data class PdfTotals(count: Int, bytes: Long)`
  - `data class IncomingPaper(ref: Int, paper: PaperEntity, authors: List<PaperAuthorEntity>, notes: PaperNotesEntity?)` — `paper.id` is a fresh local id; its `pdf_*` columns are set only when a staged file goes with it.
  - `data class IncomingCollection(name: String, nameKey: String, createdAt: Long, refs: List<Int>)`
  - `data class MergeOutcome(added: Int, matched: Int, notesAdded: Int, collectionsCreated: Int, pdfTargets: Map<Int, String>)` — `pdfTargets`: ref → local id whose PDF columns now point at that ref's staged file.
  - `BackupDao`: `suspend fun snapshot(): LibrarySnapshot`, `suspend fun paperCount(): Int`, `suspend fun collectionCount(): Int`, `suspend fun pdfTotals(): PdfTotals`, `suspend fun matchFor(openAlexId: String?, doi: String?): String?`, `suspend fun merge(papers: List<IncomingPaper>, collections: List<IncomingCollection>, now: Long): MergeOutcome`.
  - `HashiyaDatabase.backupDao()`; Hilt provides `BackupDao`.

- [ ] **Step 1: Write the failing tests**

`android/core/database/src/test/java/com/etatech/hashiya/core/database/dao/BackupDaoTest.kt`:

```kotlin
package com.etatech.hashiya.core.database.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.database.model.IncomingCollection
import com.etatech.hashiya.core.database.model.IncomingPaper
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperNotesEntity
import com.etatech.hashiya.core.database.model.searchEntityFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupDaoTest {
    private lateinit var db: HashiyaDatabase
    private lateinit var dao: BackupDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.backupDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entity(id: String, openAlexId: String? = null, doi: String? = null, title: String = "Paper $id", citeKey: String? = null) =
        PaperEntity(
            id = id, openAlexId = openAlexId, doi = doi, title = title, year = 2020, venue = null, abstract = null,
            citationCount = 0, isOpenAccess = false, oaPdfUrl = null, savedAt = 1, readingStatus = "to_read", citeKey = citeKey
        )

    private fun notes(paperId: String, summary: String) = PaperNotesEntity(paperId, summary, "", "", "", "", "", updatedAt = 1)

    /** Saves a paper the way the app does, so the device side of a merge looks real. */
    private suspend fun saved(entity: PaperEntity, notes: PaperNotesEntity? = null) {
        db.paperDao().insertPaperWithAuthors(
            entity,
            listOf(PaperAuthorEntity(entity.id, 0, "Device Author", null)),
            searchEntityFor(entity.id, entity.title, listOf("Device Author"), null, null, notes?.let { com.etatech.hashiya.core.database.model.run { it.asPaperNotes() } }),
            notes
        )
    }

    private fun incoming(ref: Int, entity: PaperEntity, notes: PaperNotesEntity? = null, authors: List<String> = listOf("A")) =
        IncomingPaper(ref, entity, authors.mapIndexed { i, name -> PaperAuthorEntity(entity.id, i, name, null) }, notes)

    @Test
    fun addsNewPapersWithAuthorsNotesAndSearchRow() = runTest {
        val outcome = dao.merge(listOf(incoming(1, entity("n1", "W1"), notes("n1", "Backup summary"), listOf("Ada", "Grace"))), emptyList(), now = 9)

        assertEquals(1, outcome.added)
        val paper = db.paperDao().getByOpenAlexId("W1")!!
        assertEquals(listOf("Ada", "Grace"), paper.authors.sortedBy { it.position }.map { it.name })
        assertEquals("Backup summary", db.paperDao().observeNotes("W1").first()?.summary)
        assertEquals(1, db.paperDao().observeLibrary("summary*", null, null).first().size)
    }

    @Test
    fun matchesByOpenAlexIdAndKeepsTheDevicePaper() = runTest {
        saved(entity("d1", "W1", title = "Device title").copy(readingStatus = "read"), notes("d1", "Device notes"))

        val outcome = dao.merge(
            listOf(incoming(1, entity("n1", "W1", title = "Backup title"), notes("n1", "Backup notes"))),
            emptyList(),
            now = 9
        )

        assertEquals(0, outcome.added)
        assertEquals(1, outcome.matched)
        assertEquals(0, outcome.notesAdded)
        val paper = db.paperDao().getByOpenAlexId("W1")!!.paper
        assertEquals("Device title", paper.title)
        assertEquals("read", paper.readingStatus)
        assertEquals("Device notes", db.paperDao().observeNotes("W1").first()?.summary)
    }

    @Test
    fun backupNotesFillAMatchedPaperWithoutNotes() = runTest {
        saved(entity("d1", "W1"))
        val outcome = dao.merge(listOf(incoming(1, entity("n1", "W1"), notes("n1", "Backup notes"))), emptyList(), now = 9)
        assertEquals(1, outcome.notesAdded)
        assertEquals("Backup notes", db.paperDao().observeNotes("W1").first()?.summary)
        assertEquals(1, db.paperDao().observeLibrary("backup*", null, null).first().size)
    }

    @Test
    fun matchesByDoiOnlyWhenTheBackupPaperHasNoOpenAlexId() = runTest {
        saved(entity("d1", "W1", doi = "10.1/x"))

        val byDoi = dao.merge(listOf(incoming(1, entity("n1", openAlexId = null, doi = "10.1/x"))), emptyList(), now = 9)
        assertEquals(1, byDoi.matched)

        // Another work with the same DOI (a preprint and its published version) is a separate paper.
        val otherWork = dao.merge(listOf(incoming(1, entity("n2", openAlexId = "W2", doi = "10.1/x"))), emptyList(), now = 9)
        assertEquals(1, otherWork.added)
    }

    @Test
    fun aPaperWithNeitherIdIsAlwaysNew() = runTest {
        val first = dao.merge(listOf(incoming(1, entity("n1"))), emptyList(), now = 9)
        val second = dao.merge(listOf(incoming(1, entity("n2"))), emptyList(), now = 9)
        assertEquals(1, first.added)
        assertEquals(1, second.added)
        assertEquals(2, dao.paperCount())
    }

    @Test
    fun aTakenCiteKeyIsDropped() = runTest {
        saved(entity("d1", "W1", citeKey = "smith2020"))
        dao.merge(listOf(incoming(1, entity("n1", "W2", citeKey = "smith2020"))), emptyList(), now = 9)
        assertNull(db.paperDao().getByOpenAlexId("W2")!!.paper.citeKey)
    }

    @Test
    fun pdfColumnsAreSetOnlyWhenTheMatchedPaperHasNone() = runTest {
        saved(entity("d1", "W1"))
        saved(entity("d2", "W2"))
        db.paperDao().setPdf("d2", "attached", 5, 1)
        val withPdf = { id: String, oa: String -> entity(id, oa).copy(pdfSource = "downloaded", pdfSize = 7, pdfAddedAt = 3, pdfLastPage = 2) }

        val outcome = dao.merge(listOf(incoming(1, withPdf("n1", "W1")), incoming(2, withPdf("n2", "W2")), incoming(3, withPdf("n3", "W3"))), emptyList(), now = 9)

        assertEquals(mapOf(1 to "d1", 3 to "n3"), outcome.pdfTargets)
        assertEquals(2, db.paperDao().getByOpenAlexId("W1")!!.paper.pdfLastPage)
        assertEquals("attached", db.paperDao().getByOpenAlexId("W2")!!.paper.pdfSource)
    }

    @Test
    fun collectionsMergeByNameKeyAndLinkNewAndMatchedPapers() = runTest {
        saved(entity("d1", "W1"))
        val existing = db.collectionDao().insertCollection("Thesis", "thesis", 1)!!

        val outcome = dao.merge(
            listOf(incoming(1, entity("n1", "W1")), incoming(2, entity("n2", "W2"))),
            listOf(IncomingCollection("THESIS ", "thesis", 5, listOf(1, 2)), IncomingCollection("Review", "review", 6, listOf(2, 99))),
            now = 9
        )

        assertEquals(1, outcome.collectionsCreated)
        val counts = db.collectionDao().observeCollections().first().associate { it.name to it.paperCount }
        assertEquals(mapOf("Review" to 1, "Thesis" to 2), counts)
        assertTrue(db.collectionDao().observeCollectionIdsForPaper("W1").first().contains(existing))
    }

    @Test
    fun aFailureRollsBackTheWholeMerge() = runTest {
        val broken = IncomingPaper(2, entity("n2", "W2"), listOf(PaperAuthorEntity("no-such-paper", 0, "X", null)), null)
        try {
            dao.merge(listOf(incoming(1, entity("n1", "W1")), broken), emptyList(), now = 9)
            fail("Expected the foreign key to fail")
        } catch (e: SQLiteConstraintException) {
            // expected
        }
        assertEquals(0, dao.paperCount())
    }

    @Test
    fun snapshotReadsEverything() = runTest {
        saved(entity("d1", "W1"), notes("d1", "N"))
        val id = db.collectionDao().insertCollection("C", "c", 1)!!
        db.collectionDao().addToCollection(id, "W1", 2)

        val snapshot = dao.snapshot()

        assertEquals(listOf("d1"), snapshot.papers.map { it.paper.id })
        assertEquals(listOf("N"), snapshot.notes.map { it.summary })
        assertEquals(listOf("C"), snapshot.collections.map { it.name })
        assertEquals(listOf(id to "d1"), snapshot.links.map { it.collectionId to it.paperId })
    }
}
```

In `saved`, replace the awkward `notes?.let { … }` with a plain call if `asPaperNotes` is imported: add `import com.etatech.hashiya.core.database.model.asPaperNotes` and write `notes?.asPaperNotes()`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:database:testDebugUnitTest --tests '*BackupDaoTest'`
Expected: compilation FAILS — `backupDao`, `IncomingPaper` unresolved.

- [ ] **Step 3: Write the row types**

`android/core/database/src/main/java/com/etatech/hashiya/core/database/model/BackupRows.kt`:

```kotlin
package com.etatech.hashiya.core.database.model

/** Everything an export writes, read in one transaction so it is consistent. */
data class LibrarySnapshot(
    val papers: List<PaperWithAuthors>,
    val notes: List<PaperNotesEntity>,
    val collections: List<CollectionEntity>,
    val links: List<CollectionPaperEntity>
)

data class PdfTotals(val count: Int, val bytes: Long)

/**
 * A backup paper ready to merge. [paper] has a fresh local id, and its `pdf_*` columns are set only when a staged PDF goes with
 * it; [authors] and [notes] belong to that id.
 */
data class IncomingPaper(
    val ref: Int,
    val paper: PaperEntity,
    val authors: List<PaperAuthorEntity>,
    val notes: PaperNotesEntity?
)

/** [refs] are backup refs; ones that name no paper in the merge are skipped. */
data class IncomingCollection(val name: String, val nameKey: String, val createdAt: Long, val refs: List<Int>)

/** [pdfTargets]: backup ref → the local id whose PDF columns now point at that ref's staged file. */
data class MergeOutcome(
    val added: Int,
    val matched: Int,
    val notesAdded: Int,
    val collectionsCreated: Int,
    val pdfTargets: Map<Int, String>
)
```

- [ ] **Step 4: Write the DAO**

`android/core/database/src/main/java/com/etatech/hashiya/core/database/dao/BackupDao.kt`:

```kotlin
package com.etatech.hashiya.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.etatech.hashiya.core.database.model.CollectionEntity
import com.etatech.hashiya.core.database.model.CollectionPaperEntity
import com.etatech.hashiya.core.database.model.IncomingCollection
import com.etatech.hashiya.core.database.model.IncomingPaper
import com.etatech.hashiya.core.database.model.LibrarySnapshot
import com.etatech.hashiya.core.database.model.MergeOutcome
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperNotesEntity
import com.etatech.hashiya.core.database.model.PaperSearchEntity
import com.etatech.hashiya.core.database.model.PaperWithAuthors
import com.etatech.hashiya.core.database.model.PdfTotals
import com.etatech.hashiya.core.database.model.asPaperNotes
import com.etatech.hashiya.core.database.model.notesSearchText
import com.etatech.hashiya.core.database.model.searchEntityFor

/** Reads the library for an export and merges a backup into it. The device wins every conflict. */
@Dao
abstract class BackupDao {
    @Transaction
    open suspend fun snapshot(): LibrarySnapshot = LibrarySnapshot(allPapers(), allNotes(), allCollections(), allLinks())

    @Query("SELECT COUNT(*) FROM papers")
    abstract suspend fun paperCount(): Int

    @Query("SELECT COUNT(*) FROM collections")
    abstract suspend fun collectionCount(): Int

    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(pdf_size), 0) AS bytes FROM papers WHERE pdf_source IS NOT NULL")
    abstract suspend fun pdfTotals(): PdfTotals

    /**
     * The local id of the saved paper a backup paper matches: by [openAlexId] when it has one, otherwise by [doi] (already
     * normalized). DOIs aren't unique here (a preprint and its published version can both be saved), so a paper with an OpenAlex
     * id never matches by DOI.
     */
    open suspend fun matchFor(openAlexId: String?, doi: String?): String? = when {
        openAlexId != null -> idForOpenAlexId(openAlexId)
        doi != null -> idForDoi(doi)
        else -> null
    }

    /** Adds what the library lacks and keeps everything it has, atomically. See the spec's merge rules. */
    @Transaction
    open suspend fun merge(papers: List<IncomingPaper>, collections: List<IncomingCollection>, now: Long): MergeOutcome {
        val localIds = mutableMapOf<Int, String>()
        val pdfTargets = mutableMapOf<Int, String>()
        var added = 0
        var matched = 0
        var notesAdded = 0
        var collectionsCreated = 0
        for (incoming in papers) {
            val paper = incoming.paper
            val existing = matchFor(paper.openAlexId, paper.doi)
            if (existing == null) {
                val key = paper.citeKey
                insertPaper(if (key != null && citeKeyTaken(key)) paper.copy(citeKey = null) else paper)
                insertAuthors(incoming.authors)
                insertSearch(
                    searchEntityFor(
                        paper.id,
                        paper.title,
                        incoming.authors.sortedBy { it.position }.map { it.name },
                        paper.abstract,
                        paper.venue,
                        incoming.notes?.asPaperNotes()
                    )
                )
                incoming.notes?.let { upsertNotes(it) }
                localIds[incoming.ref] = paper.id
                if (paper.pdfSource != null) pdfTargets[incoming.ref] = paper.id
                added++
            } else {
                matched++
                localIds[incoming.ref] = existing
                val notes = incoming.notes
                if (notes != null && !hasNotes(existing)) {
                    upsertNotes(notes.copy(paperId = existing))
                    setSearchNotes(existing, notesSearchText(notes.asPaperNotes()))
                    notesAdded++
                }
                val source = paper.pdfSource
                if (source != null &&
                    setPdfIfNone(existing, source, paper.pdfSize ?: 0, paper.pdfAddedAt ?: now, paper.pdfLastPage ?: 0) > 0
                ) {
                    pdfTargets[incoming.ref] = existing
                }
            }
        }
        for (collection in collections) {
            val id = idForNameKey(collection.nameKey)
                ?: insertCollection(CollectionEntity(name = collection.name, nameKey = collection.nameKey, createdAt = collection.createdAt))
                    .also { collectionsCreated++ }
            collection.refs.mapNotNull(localIds::get).distinct().forEach { link(id, it, now) }
        }
        return MergeOutcome(added, matched, notesAdded, collectionsCreated, pdfTargets)
    }

    @Transaction
    @Query("SELECT * FROM papers ORDER BY saved_at")
    protected abstract suspend fun allPapers(): List<PaperWithAuthors>

    @Query("SELECT * FROM paper_notes")
    protected abstract suspend fun allNotes(): List<PaperNotesEntity>

    @Query("SELECT * FROM collections ORDER BY name_key")
    protected abstract suspend fun allCollections(): List<CollectionEntity>

    @Query("SELECT * FROM collection_papers ORDER BY added_at")
    protected abstract suspend fun allLinks(): List<CollectionPaperEntity>

    @Query("SELECT id FROM papers WHERE open_alex_id = :openAlexId")
    protected abstract suspend fun idForOpenAlexId(openAlexId: String): String?

    @Query("SELECT id FROM papers WHERE doi = :doi ORDER BY saved_at LIMIT 1")
    protected abstract suspend fun idForDoi(doi: String): String?

    @Query("SELECT EXISTS(SELECT 1 FROM paper_notes WHERE paper_id = :paperId)")
    protected abstract suspend fun hasNotes(paperId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM papers WHERE cite_key = :citeKey)")
    protected abstract suspend fun citeKeyTaken(citeKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPaper(paper: PaperEntity)

    @Insert
    protected abstract suspend fun insertAuthors(authors: List<PaperAuthorEntity>)

    @Insert
    protected abstract suspend fun insertSearch(search: PaperSearchEntity)

    @Upsert
    protected abstract suspend fun upsertNotes(notes: PaperNotesEntity)

    @Query("UPDATE paper_search SET notes = :text WHERE paper_id = :paperId")
    protected abstract suspend fun setSearchNotes(paperId: String, text: String)

    @Query(
        """
        UPDATE papers SET pdf_source = :source, pdf_size = :size, pdf_added_at = :addedAt, pdf_last_page = :lastPage
        WHERE id = :paperId AND pdf_source IS NULL
        """
    )
    protected abstract suspend fun setPdfIfNone(paperId: String, source: String, size: Long, addedAt: Long, lastPage: Int): Int

    @Query("SELECT id FROM collections WHERE name_key = :nameKey")
    protected abstract suspend fun idForNameKey(nameKey: String): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertCollection(collection: CollectionEntity): Long

    @Query("INSERT OR IGNORE INTO collection_papers (collection_id, paper_id, added_at) VALUES (:collectionId, :paperId, :addedAt)")
    protected abstract suspend fun link(collectionId: Long, paperId: String, addedAt: Long)
}
```

- [ ] **Step 5: Register the DAO**

In `HashiyaDatabase` add `abstract fun backupDao(): BackupDao` (and its import). In `DatabaseModule` add:

```kotlin
@Provides
fun provideBackupDao(database: HashiyaDatabase): BackupDao = database.backupDao()
```

No schema change: the database version stays 5.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :core:database:testDebugUnitTest`
Expected: PASS (`BackupDaoTest` plus the existing DAO and migration tests).

- [ ] **Step 7: Commit**

```bash
git add android/core/database/src
git commit -m "feat(android): BackupDao reads a library snapshot and merges a backup"
```

---

### Task 5: Writing the archive and the export operation

**Files:**
- Create: `android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/BackupArchive.kt`
- Create: `android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/LibraryBackup.kt`
- Create: `android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/ArchiveLibraryBackup.kt`
- Create: `android/core/data/src/main/java/com/etatech/hashiya/core/data/di/BackupModule.kt`
- Test: `android/core/data/src/test/java/com/etatech/hashiya/core/data/backup/ArchiveLibraryBackupTest.kt`

**Interfaces:**
- Consumes: `BackupDao.snapshot()`, `.paperCount()`, `.collectionCount()`, `.pdfTotals()`; `PdfFileStore.file(paperId)`; `PdfStoreGate.storing`; Task 1 models.
- Produces (public, package `com.etatech.hashiya.core.data.backup`):

```kotlin
interface LibraryBackup {
    suspend fun summary(): BackupSummary
    suspend fun export(includePdfs: Boolean, onProgress: (Float) -> Unit = {}): ExportedFile
    suspend fun save(exported: ExportedFile, destination: Uri)
    fun discard(exported: ExportedFile)
    suspend fun open(source: Uri): OpenResult           // Task 6
    suspend fun apply(backup: PreparedBackup, onProgress: (Float) -> Unit = {}): RestoreResult   // Task 7
    fun discard(backup: PreparedBackup)                  // Task 6
}
data class BackupSummary(val papers: Int, val collections: Int, val pdfCount: Int, val pdfBytes: Long)
class ExportedFile internal constructor(internal val file: File, val fileName: String, val missingPdfs: Int)
class BackupException(val failure: BackupFailure, cause: Throwable? = null) : Exception(cause)
enum class BackupFailure { NoSpace, WriteFailed, Unreadable }
```

This is the final shape, for reference. Task 5 declares the interface with only `summary`, `export`, `save` and `discard(ExportedFile)`; Task 6 adds `open` and `discard(PreparedBackup)`, Task 7 adds `apply`. No member is ever stubbed with `TODO()`.

- [ ] **Step 1: Write the failing tests**

`android/core/data/src/test/java/com/etatech/hashiya/core/data/backup/ArchiveLibraryBackupTest.kt`:

```kotlin
package com.etatech.hashiya.core.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.data.pdf.PdfFileStore
import com.etatech.hashiya.core.data.pdf.PdfStoreGate
import com.etatech.hashiya.core.data.repository.RoomLibraryRepository
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperNotes
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArchiveLibraryBackupTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: HashiyaDatabase
    private lateinit var library: RoomLibraryRepository
    private lateinit var pdfDir: File
    private lateinit var backup: ArchiveLibraryBackup
    private var ids = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, HashiyaDatabase::class.java).allowMainThreadQueries().build()
        library = RoomLibraryRepository(db.paperDao(), now = { 1_000L }, newId = { "local-${++ids}" })
        pdfDir = File(tmp.root, "pdfs")
        backup = backup(db)
    }

    @After
    fun tearDown() = db.close()

    private fun backup(database: HashiyaDatabase, pdfs: File = pdfDir) = ArchiveLibraryBackup(
        backupDao = database.backupDao(),
        fileStore = PdfFileStore(pdfs),
        gate = PdfStoreGate(),
        contentResolver = context.contentResolver,
        workDir = File(tmp.root, "work"),
        appVersion = "0.3.0 (Android)",
        now = { 1_790_000_000_000L },
        newId = { "restored-${++ids}" },
        io = Dispatchers.Unconfined
    )

    private fun paper(id: String, title: String = "Paper $id") =
        Paper(id, "10.1/$id", title, listOf(Author("Jane Doe", "A1"), Author("Omar", null)), 2020, "Nature", "Abstract", 3, true, "https://x/$id.pdf")

    private fun storePdf(localId: String, text: String = "%PDF-1.4 $localId") {
        pdfDir.mkdirs()
        File(pdfDir, "$localId.pdf").writeText(text)
        kotlinx.coroutines.runBlocking { db.paperDao().setPdf(localId, "downloaded", text.length.toLong(), 5) }
    }

    private fun ZipFile.text(name: String) = getInputStream(getEntry(name)).use { it.reader().readText() }

    @Test
    fun summaryCountsPapersCollectionsAndPdfs() = runTest {
        library.save(paper("W1"))
        library.save(paper("W2"))
        storePdf("local-1")
        db.collectionDao().insertCollection("C", "c", 1)

        assertEquals(BackupSummary(papers = 2, collections = 1, pdfCount = 1, pdfBytes = 16), backup.summary())
    }

    @Test
    fun exportWithoutPdfsWritesTheLibraryAndNoPdfEntries() = runTest {
        library.save(paper("W1"))
        library.saveNotes("W1", PaperNotes(summary = "My summary"))
        storePdf("local-1")
        val collection = db.collectionDao().insertCollection("Thesis", "thesis", 7)!!
        db.collectionDao().addToCollection(collection, "W1", 8)

        val exported = backup.export(includePdfs = false)

        assertEquals("Hashiya-library-2026-09-21.hashiya", exported.fileName)
        assertEquals(0, exported.missingPdfs)
        ZipFile(exported.file).use { zip ->
            assertNull(zip.getEntry("pdfs/1.pdf"))
            val manifest = backupJson.decodeFromString(BackupManifest.serializer(), zip.text(MANIFEST_ENTRY))
            assertEquals(BackupManifest(1, "0.3.0 (Android)", "2026-09-21T13:46:40Z", papers = 1, collections = 1, includesPdfs = false), manifest)
            val written = backupJson.decodeFromString(BackupLibrary.serializer(), zip.text(LIBRARY_ENTRY))
            val paper = written.papers.single()
            assertEquals(1, paper.ref)
            assertEquals("W1", paper.openAlexId)
            assertEquals(listOf(BackupAuthor("Jane Doe", "A1"), BackupAuthor("Omar", null)), paper.authors)
            assertEquals("My summary", paper.notes?.summary)
            assertEquals(BackupPdf("downloaded", 5, 0, null), paper.pdf)
            assertEquals(listOf(BackupCollection("Thesis", 7, listOf(1))), written.collections)
        }
    }

    @Test
    fun exportWithPdfsIncludesThemAndCountsMissingOnes() = runTest {
        library.save(paper("W1"))
        library.save(paper("W2"))
        storePdf("local-1")
        storePdf("local-2")
        File(pdfDir, "local-2.pdf").delete()

        val exported = backup.export(includePdfs = true)

        assertEquals(1, exported.missingPdfs)
        ZipFile(exported.file).use { zip ->
            assertEquals("%PDF-1.4 local-1", zip.text("pdfs/1.pdf"))
            val papers = backupJson.decodeFromString(BackupLibrary.serializer(), zip.text(LIBRARY_ENTRY)).papers
            assertEquals("pdfs/1.pdf", papers[0].pdf?.file)
            assertNull(papers[1].pdf?.file)
            assertTrue(backupJson.decodeFromString(BackupManifest.serializer(), zip.text(MANIFEST_ENTRY)).includesPdfs)
        }
    }

    @Test
    fun saveCopiesToTheDestinationAndDiscardDeletesTheTempFile() = runTest {
        library.save(paper("W1"))
        val exported = backup.export(includePdfs = false)
        val destination = File(tmp.root, "out.hashiya")

        backup.save(exported, Uri.fromFile(destination))
        backup.discard(exported)

        assertTrue(destination.length() > 0)
        assertFalse(exported.file.exists())
    }
}
```

Note the timestamp: `1_790_000_000_000` ms is `2026-09-21T13:46:40Z`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*ArchiveLibraryBackupTest'`
Expected: compilation FAILS — `ArchiveLibraryBackup` unresolved.

- [ ] **Step 3: Write the archive writer**

`android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/BackupArchive.kt`:

```kotlin
package com.etatech.hashiya.core.data.backup

import com.etatech.hashiya.core.database.model.LibrarySnapshot
import com.etatech.hashiya.core.database.model.PDF_SOURCE_ATTACHED
import com.etatech.hashiya.core.database.model.PDF_SOURCE_DOWNLOADED
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** What [writeArchive] wrote. */
internal data class WrittenArchive(val papers: Int, val collections: Int, val missingPdfs: Int)

/**
 * Writes [snapshot] as a `.hashiya` archive to [out]. With [includePdfs], each stored PDF that [pdfFile] finds is copied in; one
 * that can't be opened is written with `"file": null` and counted as missing. PDFs go first and the JSON last, so `library.json`
 * only names entries that were really written.
 */
internal fun writeArchive(
    snapshot: LibrarySnapshot,
    includePdfs: Boolean,
    pdfFile: (paperId: String) -> File,
    manifest: (papers: Int, collections: Int) -> BackupManifest,
    out: OutputStream,
    onProgress: (Float) -> Unit
): WrittenArchive {
    val refs = snapshot.papers.mapIndexed { index, row -> row.paper.id to index + 1 }.toMap()
    val notesByPaper = snapshot.notes.associateBy { it.paperId }
    val withPdf = snapshot.papers.filter { it.paper.pdfSource != null }
    val included = mutableSetOf<String>()
    var missing = 0
    ZipOutputStream(out.buffered()).use { zip ->
        if (includePdfs) {
            withPdf.forEachIndexed { index, row ->
                val input = runCatching { pdfFile(row.paper.id).inputStream() }.getOrNull()
                if (input == null) {
                    missing++
                } else {
                    input.use {
                        zip.putNextEntry(ZipEntry(pdfEntryName(refs.getValue(row.paper.id))))
                        it.copyTo(zip)
                        zip.closeEntry()
                    }
                    included += row.paper.id
                }
                onProgress((index + 1).toFloat() / (withPdf.size + 1))
            }
        }
        val papers = snapshot.papers.map { row ->
            val paper = row.paper
            val ref = refs.getValue(paper.id)
            BackupPaper(
                ref = ref,
                openAlexId = paper.openAlexId,
                doi = paper.doi,
                title = paper.title,
                year = paper.year,
                venue = paper.venue,
                abstract = paper.abstract,
                citationCount = paper.citationCount,
                isOpenAccess = paper.isOpenAccess,
                oaPdfUrl = paper.oaPdfUrl,
                savedAt = paper.savedAt,
                readingStatus = paper.readingStatus,
                workType = paper.workType,
                sourceType = paper.sourceType,
                publisher = paper.publisher,
                volume = paper.volume,
                issue = paper.issue,
                firstPage = paper.firstPage,
                lastPage = paper.lastPage,
                citeKey = paper.citeKey,
                detailsFetched = paper.detailsFetched,
                authors = row.authors.sortedBy { it.position }.map { BackupAuthor(it.name, it.openAlexAuthorId) },
                notes = notesByPaper[paper.id]?.let {
                    BackupNotes(it.summary, it.researchQuestion, it.method, it.keyFindings, it.limitations, it.thoughts, it.updatedAt)
                },
                pdf = paper.pdfSource?.let { source ->
                    BackupPdf(
                        source = if (source == PDF_SOURCE_DOWNLOADED) PDF_SOURCE_DOWNLOADED else PDF_SOURCE_ATTACHED,
                        addedAt = paper.pdfAddedAt ?: 0,
                        lastPage = paper.pdfLastPage ?: 0,
                        file = if (paper.id in included) pdfEntryName(ref) else null
                    )
                }
            )
        }
        val collections = snapshot.collections.map { collection ->
            BackupCollection(
                name = collection.name,
                createdAt = collection.createdAt,
                papers = snapshot.links.filter { it.collectionId == collection.id }.mapNotNull { refs[it.paperId] }
            )
        }
        zip.putNextEntry(ZipEntry(LIBRARY_ENTRY))
        zip.write(backupJson.encodeToString(BackupLibrary.serializer(), BackupLibrary(papers, collections)).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
        zip.write(backupJson.encodeToString(BackupManifest.serializer(), manifest(papers.size, collections.size)).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
    onProgress(1f)
    return WrittenArchive(snapshot.papers.size, snapshot.collections.size, missing)
}

/** `2026-10-04T14:05:00Z`. java.time needs API 26; minSdk is 24. */
internal fun isoUtc(millis: Long): String = utcFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(Date(millis))

/** `Hashiya-library-2026-10-04.hashiya`, dated in UTC like the manifest. */
internal fun backupFileName(millis: Long): String = "Hashiya-library-${utcFormat("yyyy-MM-dd").format(Date(millis))}.hashiya"

/** Millis for an [isoUtc] string; null when it doesn't parse. */
internal fun parseIsoUtc(text: String): Long? =
    runCatching { utcFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").apply { isLenient = false }.parse(text)?.time }.getOrNull()

private fun utcFormat(pattern: String) = SimpleDateFormat(pattern, Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
```

- [ ] **Step 4: Write the interface (export members) and the implementation**

`android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/LibraryBackup.kt`:

```kotlin
package com.etatech.hashiya.core.data.backup

import android.net.Uri
import java.io.File

/** Exports the library to a `.hashiya` archive and merges one back in. */
interface LibraryBackup {
    suspend fun summary(): BackupSummary

    /** Builds the archive in a temporary file. Throws [BackupException]. */
    suspend fun export(includePdfs: Boolean, onProgress: (Float) -> Unit = {}): ExportedFile

    /** Copies [exported] to [destination] (from the system save dialog). Throws [BackupException]; a failed copy is deleted. */
    suspend fun save(exported: ExportedFile, destination: Uri)

    /** Deletes the temporary file. Safe to call more than once. */
    fun discard(exported: ExportedFile)
}

data class BackupSummary(val papers: Int, val collections: Int, val pdfCount: Int, val pdfBytes: Long)

class ExportedFile internal constructor(internal val file: File, val fileName: String, val missingPdfs: Int)

enum class BackupFailure { NoSpace, WriteFailed, Unreadable }

class BackupException(val failure: BackupFailure, cause: Throwable? = null) : Exception(failure.name, cause)
```

`android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/ArchiveLibraryBackup.kt`:

```kotlin
package com.etatech.hashiya.core.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.etatech.hashiya.core.data.pdf.PdfFileStore
import com.etatech.hashiya.core.data.pdf.PdfStoreGate
import com.etatech.hashiya.core.database.dao.BackupDao
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class ArchiveLibraryBackup(
    private val backupDao: BackupDao,
    private val fileStore: PdfFileStore,
    private val gate: PdfStoreGate,
    private val contentResolver: ContentResolver,
    /** A private folder for archives being built or read; cleared of leftovers on first use. */
    private val workDir: File,
    private val appVersion: String,
    private val now: () -> Long,
    private val newId: () -> String,
    private val io: CoroutineDispatcher
) : LibraryBackup {
    override suspend fun summary(): BackupSummary {
        val pdfs = backupDao.pdfTotals()
        return BackupSummary(backupDao.paperCount(), backupDao.collectionCount(), pdfs.count, pdfs.bytes)
    }

    override suspend fun export(includePdfs: Boolean, onProgress: (Float) -> Unit): ExportedFile = gate.storing {
        // Inside the gate so the startup sweep can't delete a PDF mid-copy.
        val snapshot = backupDao.snapshot()
        withContext(io) {
            val time = now()
            val file = File(workDir.apply { mkdirs() }, "export-${newId()}.hashiya")
            try {
                val written = file.outputStream().use { out ->
                    writeArchive(
                        snapshot = snapshot,
                        includePdfs = includePdfs,
                        pdfFile = fileStore::file,
                        manifest = { papers, collections ->
                            BackupManifest(BACKUP_FORMAT, appVersion, isoUtc(time), papers, collections, includePdfs)
                        },
                        out = out,
                        onProgress = onProgress
                    )
                }
                ExportedFile(file, backupFileName(time), written.missingPdfs)
            } catch (e: IOException) {
                file.delete()
                throw BackupException(if (workDir.usableSpace < MIN_FREE_BYTES) BackupFailure.NoSpace else BackupFailure.WriteFailed, e)
            }
        }
    }

    override suspend fun save(exported: ExportedFile, destination: Uri) = withContext(io) {
        try {
            val out = contentResolver.openOutputStream(destination, "wt") ?: throw IOException("No output stream")
            out.use { exported.file.inputStream().use { input -> input.copyTo(it) } }
        } catch (e: Exception) {
            if (e !is IOException && e !is SecurityException) throw e
            // A half-written file at the destination is worse than none.
            runCatching { DocumentsContract.deleteDocument(contentResolver, destination) }
            throw BackupException(BackupFailure.WriteFailed, e)
        }
    }

    override fun discard(exported: ExportedFile) {
        exported.file.delete()
    }

    private companion object {
        /** Below this, a failed write is reported as "out of space". */
        const val MIN_FREE_BYTES = 10L * 1024 * 1024
    }
}
```

`android/core/data/src/main/java/com/etatech/hashiya/core/data/di/BackupModule.kt`:

```kotlin
package com.etatech.hashiya.core.data.di

import android.content.ContentResolver
import android.content.Context
import com.etatech.hashiya.core.data.backup.ArchiveLibraryBackup
import com.etatech.hashiya.core.data.backup.LibraryBackup
import com.etatech.hashiya.core.data.pdf.PdfFileStore
import com.etatech.hashiya.core.data.pdf.PdfStoreGate
import com.etatech.hashiya.core.database.dao.BackupDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.UUID
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
internal object BackupModule {
    @Provides
    @Singleton
    fun provideLibraryBackup(
        @ApplicationContext context: Context,
        backupDao: BackupDao,
        fileStore: PdfFileStore,
        gate: PdfStoreGate,
        contentResolver: ContentResolver
    ): LibraryBackup = ArchiveLibraryBackup(
        backupDao = backupDao,
        fileStore = fileStore,
        gate = gate,
        contentResolver = contentResolver,
        workDir = File(context.cacheDir, "backup"),
        appVersion = "${context.packageManager.getPackageInfo(context.packageName, 0).versionName} (Android)",
        now = System::currentTimeMillis,
        newId = { UUID.randomUUID().toString() },
        io = Dispatchers.IO
    )
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*ArchiveLibraryBackupTest'`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add android/core/data/src
git commit -m "feat(android): export the library to a .hashiya archive"
```

---

### Task 6: Opening and validating a backup, with a preview

**Files:**
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/BackupArchive.kt` (reader)
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/LibraryBackup.kt` (restore types, `open`, `discard(PreparedBackup)`)
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/ArchiveLibraryBackup.kt`
- Test: `android/core/data/src/test/java/com/etatech/hashiya/core/data/backup/BackupArchiveTest.kt`, additions to `ArchiveLibraryBackupTest.kt`

**Interfaces:**
- Consumes: Task 1 models; `BackupDao.matchFor`; `normalizeDoi` (`core.model`).
- Produces:

```kotlin
// LibraryBackup additions
suspend fun open(source: Uri): OpenResult
fun discard(backup: PreparedBackup)

sealed interface OpenResult {
    data class Ready(val backup: PreparedBackup, val preview: RestorePreview) : OpenResult
    data class Failed(val reason: OpenFailure) : OpenResult
}
enum class OpenFailure { NotABackup, NewerFormat, Damaged, Unreadable }
/** [exportedAt] is null when the manifest's date doesn't parse. */
data class RestorePreview(val exportedAt: Long?, val papers: Int, val collections: Int, val pdfs: Int, val newPapers: Int, val existingPapers: Int)
class PreparedBackup internal constructor(internal val file: File, internal val library: BackupLibrary)

// BackupArchive.kt additions (internal)
sealed interface ArchiveRead {
    data class Valid(val manifest: BackupManifest, val library: BackupLibrary) : ArchiveRead
    data class Invalid(val reason: OpenFailure) : ArchiveRead
}
fun readArchive(file: File): ArchiveRead
```

- [ ] **Step 1: Write the failing archive-reader tests**

`android/core/data/src/test/java/com/etatech/hashiya/core/data/backup/BackupArchiveTest.kt`:

```kotlin
package com.etatech.hashiya.core.data.backup

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val manifest = """{"format":1,"exportedAt":"2026-10-04T14:05:00Z"}"""
    private val library = """{"papers":[{"ref":1,"title":"T","savedAt":1}],"collections":[{"name":"C","createdAt":1,"papers":[1]}]}"""

    private fun zip(vararg entries: Pair<String, ByteArray>): File = tmp.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (name, bytes) ->
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
    }

    private fun zipText(vararg entries: Pair<String, String>) = zip(*entries.map { it.first to it.second.toByteArray() }.toTypedArray())

    private fun invalid(file: File) = (readArchive(file) as ArchiveRead.Invalid).reason

    @Test
    fun readsTheSharedFixture() {
        val read = readArchive(File(System.getProperty("hashiya.testdata"), "backup/format-1.hashiya")) as ArchiveRead.Valid
        assertEquals(3, read.library.papers.size)
    }

    @Test
    fun readsAValidArchive() {
        assertTrue(readArchive(zipText(MANIFEST_ENTRY to manifest, LIBRARY_ENTRY to library)) is ArchiveRead.Valid)
    }

    @Test
    fun notAZip() = assertEquals(OpenFailure.NotABackup, invalid(tmp.newFile().apply { writeText("hello") }))

    @Test
    fun zipWithoutManifest() = assertEquals(OpenFailure.NotABackup, invalid(zipText("other.txt" to "x")))

    @Test
    fun manifestThatIsNotJson() = assertEquals(OpenFailure.NotABackup, invalid(zipText(MANIFEST_ENTRY to "<xml/>", LIBRARY_ENTRY to library)))

    @Test
    fun newerFormat() = assertEquals(OpenFailure.NewerFormat, invalid(zipText(MANIFEST_ENTRY to """{"format":2}""", LIBRARY_ENTRY to library)))

    @Test
    fun missingLibrary() = assertEquals(OpenFailure.Damaged, invalid(zipText(MANIFEST_ENTRY to manifest)))

    @Test
    fun malformedLibrary() = assertEquals(OpenFailure.Damaged, invalid(zipText(MANIFEST_ENTRY to manifest, LIBRARY_ENTRY to """{"papers":[{"ref":1}]}""")))

    @Test
    fun duplicateRefs() = assertEquals(
        OpenFailure.Damaged,
        invalid(zipText(MANIFEST_ENTRY to manifest, LIBRARY_ENTRY to """{"papers":[{"ref":1,"title":"A","savedAt":1},{"ref":1,"title":"B","savedAt":1}]}"""))
    )

    @Test
    fun collectionPointingAtAnUnknownRef() = assertEquals(
        OpenFailure.Damaged,
        invalid(zipText(MANIFEST_ENTRY to manifest, LIBRARY_ENTRY to """{"papers":[],"collections":[{"name":"C","createdAt":1,"papers":[5]}]}"""))
    )

    @Test
    fun oversizedLibrary() {
        val huge = ByteArray(MAX_LIBRARY_BYTES + 1) { ' '.code.toByte() }
        assertEquals(OpenFailure.Damaged, invalid(zip(MANIFEST_ENTRY to manifest.toByteArray(), LIBRARY_ENTRY to huge)))
    }
}
```

Spec note: the spec lists "collections pointing at unknown refs" as damaged; `IncomingCollection` still skips unknown refs defensively (Task 4 test), which never triggers for a validated archive.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*BackupArchiveTest'`
Expected: compilation FAILS — `readArchive`, `ArchiveRead`, `OpenFailure` unresolved.

- [ ] **Step 3: Write the reader and the restore types**

Append to `LibraryBackup.kt` (types) and add the two members to the interface:

```kotlin
    /** Copies [source] into the app and checks it. Nothing is written to the library. */
    suspend fun open(source: Uri): OpenResult

    /** Deletes the copied archive. Safe to call more than once. */
    fun discard(backup: PreparedBackup)
```

```kotlin
sealed interface OpenResult {
    data class Ready(val backup: PreparedBackup, val preview: RestorePreview) : OpenResult

    data class Failed(val reason: OpenFailure) : OpenResult
}

enum class OpenFailure { NotABackup, NewerFormat, Damaged, Unreadable }

/** [exportedAt] is null when the manifest's date doesn't parse. [pdfs] counts PDFs in the archive. */
data class RestorePreview(
    val exportedAt: Long?,
    val papers: Int,
    val collections: Int,
    val pdfs: Int,
    val newPapers: Int,
    val existingPapers: Int
)

class PreparedBackup internal constructor(internal val file: File, internal val library: BackupLibrary)
```

Append to `BackupArchive.kt`:

```kotlin
internal sealed interface ArchiveRead {
    data class Valid(val manifest: BackupManifest, val library: BackupLibrary) : ArchiveRead

    data class Invalid(val reason: OpenFailure) : ArchiveRead
}

/** Reads and checks [file]'s manifest and library. Only those two entries are read; PDFs are read by name at restore. */
internal fun readArchive(file: File): ArchiveRead {
    val zip = try {
        ZipFile(file)
    } catch (e: IOException) {
        return ArchiveRead.Invalid(OpenFailure.NotABackup)
    }
    zip.use {
        val manifestEntry = zip.getEntry(MANIFEST_ENTRY) ?: return ArchiveRead.Invalid(OpenFailure.NotABackup)
        val manifestText = zip.readText(manifestEntry, MAX_MANIFEST_BYTES) ?: return ArchiveRead.Invalid(OpenFailure.NotABackup)
        val manifest = try {
            backupJson.decodeFromString(BackupManifest.serializer(), manifestText)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            return ArchiveRead.Invalid(OpenFailure.NotABackup)
        }
        if (manifest.format > BACKUP_FORMAT) return ArchiveRead.Invalid(OpenFailure.NewerFormat)
        if (manifest.format < 1) return ArchiveRead.Invalid(OpenFailure.Damaged)
        val libraryEntry = zip.getEntry(LIBRARY_ENTRY) ?: return ArchiveRead.Invalid(OpenFailure.Damaged)
        val libraryText = zip.readText(libraryEntry, MAX_LIBRARY_BYTES) ?: return ArchiveRead.Invalid(OpenFailure.Damaged)
        val library = try {
            backupJson.decodeFromString(BackupLibrary.serializer(), libraryText)
        } catch (e: IllegalArgumentException) {
            return ArchiveRead.Invalid(OpenFailure.Damaged)
        }
        val refs = library.papers.map { it.ref }
        if (refs.size != refs.toSet().size) return ArchiveRead.Invalid(OpenFailure.Damaged)
        val known = refs.toSet()
        if (library.collections.any { collection -> collection.papers.any { it !in known } }) {
            return ArchiveRead.Invalid(OpenFailure.Damaged)
        }
        return ArchiveRead.Valid(manifest, library)
    }
}

/** The entry as UTF-8, or null when it is longer than [maxBytes] or can't be read. Never trusts the entry's declared size. */
private fun ZipFile.readText(entry: ZipEntry, maxBytes: Int): String? = try {
    getInputStream(entry).use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) return null
            out.write(buffer, 0, read)
        }
        out.toString(Charsets.UTF_8.name())
    }
} catch (e: IOException) {
    null
}
```

Add imports to `BackupArchive.kt`: `java.io.ByteArrayOutputStream`, `java.io.IOException`, `java.util.zip.ZipFile`.

- [ ] **Step 4: Run the reader tests**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*BackupArchiveTest'`
Expected: PASS (11 tests).

- [ ] **Step 5: Write the failing `open` tests**

Add to `ArchiveLibraryBackupTest`:

```kotlin
private val fixture = File(System.getProperty("hashiya.testdata"), "backup/format-1.hashiya")

@Test
fun openPreviewsTheFixtureAgainstTheLibrary() = runTest {
    library.save(paper("W3"))

    val ready = backup.open(Uri.fromFile(fixture)) as OpenResult.Ready

    assertEquals(RestorePreview(exportedAt = 1_791_259_500_000L, papers = 3, collections = 2, pdfs = 1, newPapers = 2, existingPapers = 1), ready.preview)
    backup.discard(ready.backup)
    assertFalse(ready.backup.file.exists())
}

@Test
fun openRejectsANonBackupAndKeepsNoCopy() = runTest {
    val text = tmp.newFile("notes.txt").apply { writeText("hello") }

    assertEquals(OpenResult.Failed(OpenFailure.NotABackup), backup.open(Uri.fromFile(text)))
    assertTrue(File(tmp.root, "work").listFiles().orEmpty().isEmpty())
}

@Test
fun openReportsAnUnreadableSource() = runTest {
    assertEquals(OpenResult.Failed(OpenFailure.Unreadable), backup.open(Uri.fromFile(File(tmp.root, "missing.hashiya"))))
}
```

(`2026-10-04T14:05:00Z` is `1_791_259_500_000` ms; `W3` in the fixture matches the saved paper, so 1 existing and 2 new.)

- [ ] **Step 6: Run them to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*ArchiveLibraryBackupTest'`
Expected: compilation FAILS — `open` not implemented.

- [ ] **Step 7: Implement `open` and `discard(PreparedBackup)`**

Add to `ArchiveLibraryBackup`:

```kotlin
override suspend fun open(source: Uri): OpenResult {
    val file = File(workDir.apply { mkdirs() }, "restore-${newId()}.hashiya")
    val read = withContext(io) {
        try {
            val input = contentResolver.openInputStream(source) ?: throw IOException("No input stream")
            input.use { from -> file.outputStream().use { from.copyTo(it) } }
            readArchive(file)
        } catch (e: Exception) {
            if (e !is IOException && e !is SecurityException) throw e
            ArchiveRead.Invalid(OpenFailure.Unreadable)
        }
    }
    if (read !is ArchiveRead.Valid) {
        file.delete()
        return OpenResult.Failed((read as ArchiveRead.Invalid).reason)
    }
    val papers = read.library.papers
    val existing = papers.count { backupDao.matchFor(it.openAlexId?.trim()?.ifEmpty { null }, it.doi?.let(::normalizeDoi)) != null }
    val preview = RestorePreview(
        exportedAt = parseIsoUtc(read.manifest.exportedAt),
        papers = papers.size,
        collections = read.library.collections.size,
        pdfs = papers.count { it.pdf?.file != null },
        newPapers = papers.size - existing,
        existingPapers = existing
    )
    return OpenResult.Ready(PreparedBackup(file, read.library), preview)
}

override fun discard(backup: PreparedBackup) {
    backup.file.delete()
}
```

Add `import com.etatech.hashiya.core.model.normalizeDoi`. Also make `FakeLibraryBackup` compile later (Task 8) — nothing to do here.

- [ ] **Step 8: Run the tests**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*ArchiveLibraryBackupTest' --tests '*BackupArchiveTest'`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add android/core/data/src
git commit -m "feat(android): open and validate a backup with a restore preview"
```

---

### Task 7: Applying a backup — staged PDFs, merge, commit

**Files:**
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/LibraryBackup.kt` (`apply`, `RestoreResult`)
- Modify: `android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/ArchiveLibraryBackup.kt`
- Create: `android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/IncomingMapping.kt`
- Test: additions to `ArchiveLibraryBackupTest.kt`

**Interfaces:**
- Consumes: `PreparedBackup`, `BackupDao.merge`, `PdfFileStore.stage/commit/usableSpace`, `PdfStoreGate.storing`, `collectionNameKey` (`core.model`), `normalizeDoi`.
- Produces:

```kotlin
suspend fun apply(backup: PreparedBackup, onProgress: (Float) -> Unit = {}): RestoreResult   // throws BackupException(NoSpace | Unreadable | WriteFailed)
data class RestoreResult(val papersAdded: Int, val notesAdded: Int, val collectionsCreated: Int, val pdfsAdded: Int, val pdfsMissing: Int)
internal fun BackupPaper.toIncoming(localId: String, staged: StageResult.Staged?): IncomingPaper
```

`ArchiveLibraryBackup` gains a constructor parameter `merge: suspend (List<IncomingPaper>, List<IncomingCollection>, Long) -> MergeOutcome = backupDao::merge` placed after `io`, so tests can make the merge fail. `BackupModule` keeps the default.

- [ ] **Step 1: Write the failing tests**

Add to `ArchiveLibraryBackupTest`:

```kotlin
private suspend fun ready(file: File, using: ArchiveLibraryBackup = backup) = using.open(Uri.fromFile(file)) as OpenResult.Ready

@Test
fun applyingTheFixtureRestoresEverything() = runTest {
    val result = backup.apply(ready(fixture).backup)

    assertEquals(RestoreResult(papersAdded = 3, notesAdded = 0, collectionsCreated = 2, pdfsAdded = 1, pdfsMissing = 0), result)
    val deep = db.paperDao().getByOpenAlexId("W2741809807")!!
    assertEquals("reading", deep.paper.readingStatus)
    assertEquals("lecun2015deep", deep.paper.citeKey)
    assertEquals(listOf("Yann LeCun", "Yoshua Bengio", "Geoffrey Hinton"), deep.authors.sortedBy { it.position }.map { it.name })
    assertEquals(4, deep.paper.pdfLastPage)
    assertTrue(File(pdfDir, "${deep.paper.id}.pdf").readText().startsWith("%PDF-1.4"))
    assertEquals(1, db.paperDao().observeLibrary("chapter*", null, null).first().size)
    assertEquals(1, db.paperDao().observeLibrary("العربية*", null, null).first().size)
    // No staged file is left behind.
    assertTrue(pdfDir.listFiles().orEmpty().none { it.name.endsWith(".part") })
}

@Test
fun roundTripIntoAnEmptyLibrary() = runTest {
    library.save(paper("W1"))
    library.save(paper("W2", title = "Second"))
    library.saveNotes("W1", PaperNotes(summary = "S", thoughts = "T"))
    library.setStatus("W2", com.etatech.hashiya.core.model.ReadingStatus.Read)
    storePdf("local-1")
    val c = db.collectionDao().insertCollection("Thesis", "thesis", 7)!!
    db.collectionDao().addToCollection(c, "W2", 8)
    val exported = backup.export(includePdfs = true)

    val other = Room.inMemoryDatabaseBuilder(context, HashiyaDatabase::class.java).allowMainThreadQueries().build()
    val otherPdfs = File(tmp.root, "other-pdfs")
    val target = backup(other, otherPdfs)
    val result = target.apply(ready(exported.file, target).backup)

    assertEquals(2, result.papersAdded)
    assertEquals(1, result.pdfsAdded)
    val w1 = other.paperDao().getByOpenAlexId("W1")!!
    assertEquals("S", other.paperDao().observeNotes("W1").first()?.summary)
    assertEquals("read", other.paperDao().getByOpenAlexId("W2")!!.paper.readingStatus)
    assertEquals("%PDF-1.4 local-1", File(otherPdfs, "${w1.paper.id}.pdf").readText())
    assertEquals(mapOf("Thesis" to 1), other.collectionDao().observeCollections().first().associate { it.name to it.paperCount })
    other.close()
}

@Test
fun restoringTwiceAddsNothing() = runTest {
    backup.apply(ready(fixture).backup)
    val second = backup.apply(ready(fixture).backup)

    // Paper 2 has neither id, so it is new each time; the others match.
    assertEquals(1, second.papersAdded)
    assertEquals(0, second.collectionsCreated)
    assertEquals(0, second.pdfsAdded)
    assertEquals(mapOf("Thesis" to 3, "مراجعة" to 1), db.collectionDao().observeCollections().first().associate { it.name to it.paperCount })
}

@Test
fun theDevicesPdfIsKept() = runTest {
    library.save(Paper("W2741809807", null, "Mine", emptyList(), null, null, null, 0, false, null))
    storePdf("local-1", "%PDF-1.4 mine")

    val result = backup.apply(ready(fixture).backup)

    assertEquals(0, result.pdfsAdded)
    assertEquals("%PDF-1.4 mine", File(pdfDir, "local-1.pdf").readText())
    assertTrue(pdfDir.listFiles().orEmpty().none { it.name.endsWith(".part") })
}

@Test
fun pdfEntryNameMustMatchRef() = runTest {
    val archive = File(tmp.root, "evil.hashiya")
    java.util.zip.ZipOutputStream(archive.outputStream()).use { out ->
        fun put(name: String, text: String) {
            out.putNextEntry(java.util.zip.ZipEntry(name)); out.write(text.toByteArray()); out.closeEntry()
        }
        put(MANIFEST_ENTRY, """{"format":1}""")
        put(LIBRARY_ENTRY, """{"papers":[{"ref":1,"title":"A","savedAt":1,"pdf":{"source":"attached","addedAt":1,"file":"pdfs/2.pdf"}},{"ref":2,"title":"B","savedAt":1}]}""")
        put("pdfs/2.pdf", "%PDF-1.4 not yours")
    }

    val result = backup.apply(ready(archive).backup)

    assertEquals(0, result.pdfsAdded)
    assertEquals(1, result.pdfsMissing)
}

@Test
fun aFailedMergeLeavesNoStagedPdfs() = runTest {
    val failing = ArchiveLibraryBackup(
        backupDao = db.backupDao(), fileStore = PdfFileStore(pdfDir), gate = PdfStoreGate(), contentResolver = context.contentResolver,
        workDir = File(tmp.root, "work"), appVersion = "x", now = { 1L }, newId = { "id-${++ids}" }, io = Dispatchers.Unconfined,
        merge = { _, _, _ -> throw android.database.sqlite.SQLiteException("disk I/O error") }
    )

    try {
        failing.apply(ready(fixture, failing).backup)
        org.junit.Assert.fail("Expected a BackupException")
    } catch (e: BackupException) {
        assertEquals(BackupFailure.WriteFailed, e.failure)
    }
    assertEquals(0, db.backupDao().paperCount())
    assertTrue(pdfDir.listFiles().orEmpty().isEmpty())
}
```

Add imports: `kotlinx.coroutines.flow.first`, `com.etatech.hashiya.core.data.backup.RestoreResult` (same package, no import), `androidx.room.Room` (already).

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*ArchiveLibraryBackupTest'`
Expected: compilation FAILS — `apply`, `RestoreResult`, `merge` parameter unresolved.

- [ ] **Step 3: Write the mapping**

`android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/IncomingMapping.kt`:

```kotlin
package com.etatech.hashiya.core.data.backup

import com.etatech.hashiya.core.data.pdf.StageResult
import com.etatech.hashiya.core.database.model.IncomingPaper
import com.etatech.hashiya.core.database.model.PDF_SOURCE_ATTACHED
import com.etatech.hashiya.core.database.model.PDF_SOURCE_DOWNLOADED
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperNotesEntity

private val STORED_STATUSES = setOf("to_read", "reading", "read")

/**
 * The paper as rows under [localId]. Its PDF columns are set only when [staged] holds its file; notes with no text are dropped,
 * like the app never stores empty notes.
 */
internal fun BackupPaper.toIncoming(localId: String, staged: StageResult.Staged?): IncomingPaper {
    val backupPdf = pdf
    val entity = PaperEntity(
        id = localId,
        openAlexId = openAlexId?.trim()?.ifEmpty { null },
        doi = doi?.let(::normalizeDoi),
        title = title,
        year = year,
        venue = venue,
        abstract = abstract,
        citationCount = citationCount,
        isOpenAccess = isOpenAccess,
        oaPdfUrl = oaPdfUrl,
        savedAt = savedAt,
        readingStatus = readingStatus.takeIf { it in STORED_STATUSES } ?: "to_read",
        workType = workType,
        sourceType = sourceType,
        publisher = publisher,
        volume = volume,
        issue = issue,
        firstPage = firstPage,
        lastPage = lastPage,
        citeKey = citeKey?.ifBlank { null },
        detailsFetched = detailsFetched,
        pdfSource = if (staged != null && backupPdf != null) {
            if (backupPdf.source == PDF_SOURCE_DOWNLOADED) PDF_SOURCE_DOWNLOADED else PDF_SOURCE_ATTACHED
        } else {
            null
        },
        pdfSize = staged?.size,
        pdfAddedAt = if (staged != null) backupPdf?.addedAt else null,
        pdfLastPage = if (staged != null) backupPdf?.lastPage?.coerceAtLeast(0) else null
    )
    val noteRow = notes?.let {
        PaperNotesEntity(localId, it.summary, it.researchQuestion, it.method, it.keyFindings, it.limitations, it.thoughts, it.updatedAt)
    }?.takeIf { row -> listOf(row.summary, row.researchQuestion, row.method, row.keyFindings, row.limitations, row.thoughts).any { it.isNotBlank() } }
    return IncomingPaper(
        ref = ref,
        paper = entity,
        authors = authors.mapIndexed { index, author -> PaperAuthorEntity(localId, index, author.name, author.openAlexAuthorId) },
        notes = noteRow
    )
}
```

Add `import com.etatech.hashiya.core.model.normalizeDoi`.

- [ ] **Step 4: Implement `apply`**

Add to `LibraryBackup`:

```kotlin
    /**
     * Merges [backup] into the library; the library wins every conflict. All of it lands or none of it does. Throws
     * [BackupException]: [BackupFailure.NoSpace] before anything is written, [BackupFailure.Unreadable] when the archive can't be
     * read, [BackupFailure.WriteFailed] when the database write fails.
     */
    suspend fun apply(backup: PreparedBackup, onProgress: (Float) -> Unit = {}): RestoreResult
```

```kotlin
data class RestoreResult(
    val papersAdded: Int,
    val notesAdded: Int,
    val collectionsCreated: Int,
    val pdfsAdded: Int,
    /** PDFs the backup names but couldn't give: not in the archive, not a PDF, or too large. */
    val pdfsMissing: Int
)
```

Add to `ArchiveLibraryBackup` the constructor parameter (after `io`):

```kotlin
    private val merge: suspend (List<IncomingPaper>, List<IncomingCollection>, Long) -> MergeOutcome = backupDao::merge
```

and the method:

```kotlin
override suspend fun apply(backup: PreparedBackup, onProgress: (Float) -> Unit): RestoreResult = gate.storing {
    // Inside the gate: the sweep would delete the staged `.part` files.
    val staged = mutableMapOf<Int, StageResult.Staged>()
    try {
        var pdfsMissing = 0
        withContext(io) {
            val named = backup.library.papers.filter { it.pdf?.file != null }
            try {
                ZipFile(backup.file).use { zip ->
                    named.forEachIndexed { index, paper ->
                        // Only the paper's own entry name is ever read, so no entry can reach outside the PDF folder.
                        val entry = paper.pdf?.file?.takeIf { it == pdfEntryName(paper.ref) }?.let(zip::getEntry)
                        if (entry == null) {
                            pdfsMissing++
                        } else {
                            if (entry.size > 0 && fileStore.usableSpace() < entry.size + MIN_FREE_BYTES) {
                                throw BackupException(BackupFailure.NoSpace)
                            }
                            when (val result = zip.getInputStream(entry).use { fileStore.stage("restore", it, MAX_PDF_BYTES) {} }) {
                                is StageResult.Staged -> staged[paper.ref] = result
                                StageResult.NotPdf, StageResult.TooLarge -> pdfsMissing++
                            }
                        }
                        onProgress((index + 1).toFloat() / (named.size + 1))
                    }
                }
            } catch (e: IOException) {
                throw BackupException(BackupFailure.Unreadable, e)
            } catch (e: PdfWriteException) {
                throw BackupException(BackupFailure.NoSpace, e)
            }
        }
        val papers = backup.library.papers.map { it.toIncoming(newId(), staged[it.ref]) }
        val collections = backup.library.collections
            .filter { it.name.isNotBlank() }
            .map { IncomingCollection(it.name.trim(), collectionNameKey(it.name), it.createdAt, it.papers) }
        val outcome = try {
            merge(papers, collections, now())
        } catch (e: SQLiteException) {
            throw BackupException(BackupFailure.WriteFailed, e)
        }
        var pdfsAdded = 0
        withContext(io) {
            staged.forEach { (ref, file) ->
                val target = outcome.pdfTargets[ref]
                if (target == null) {
                    file.file.delete()
                } else {
                    // A failed rename leaves a row without its file; the next startup sweep clears it.
                    runCatching { fileStore.commit(file.file, target) }.onSuccess { pdfsAdded++ }.onFailure { pdfsMissing++ }
                }
            }
        }
        staged.clear()
        onProgress(1f)
        RestoreResult(outcome.added, outcome.notesAdded, outcome.collectionsCreated, pdfsAdded, pdfsMissing)
    } finally {
        staged.values.forEach { it.file.delete() }
    }
}
```

Imports for `ArchiveLibraryBackup`: `android.database.sqlite.SQLiteException`, `com.etatech.hashiya.core.data.pdf.MAX_PDF_BYTES`, `com.etatech.hashiya.core.data.pdf.PdfWriteException`, `com.etatech.hashiya.core.data.pdf.StageResult`, `com.etatech.hashiya.core.database.model.IncomingCollection`, `com.etatech.hashiya.core.database.model.IncomingPaper`, `com.etatech.hashiya.core.database.model.MergeOutcome`, `com.etatech.hashiya.core.model.collectionNameKey`, `java.util.zip.ZipFile`.

`MIN_FREE_BYTES` already exists in the companion (Task 5).

- [ ] **Step 5: Run the tests**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: PASS (whole module).

- [ ] **Step 6: Commit**

```bash
git add android/core/data/src
git commit -m "feat(android): restore a backup by merging it into the library"
```

---

### Task 8: Settings — Backup section and export

**Files:**
- Create: `android/core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeLibraryBackup.kt`
- Create: `android/feature/settings/src/main/java/com/etatech/hashiya/feature/settings/BackupSection.kt`
- Modify: `android/feature/settings/src/main/java/com/etatech/hashiya/feature/settings/SettingsViewModel.kt`, `SettingsScreen.kt`, `navigation/SettingsNavigation.kt`
- Modify: `android/feature/settings/src/main/res/values/strings.xml`, `values-ar/strings.xml`
- Modify: `android/feature/settings/src/test/java/com/etatech/hashiya/feature/settings/SettingsViewModelTest.kt` (constructor), `SettingsScreenshotTest.kt`, `SettingsContentTest.kt`
- Create: `android/feature/settings/src/test/java/com/etatech/hashiya/feature/settings/SettingsBackupViewModelTest.kt`

**Interfaces:**
- Consumes: `LibraryBackup.summary/export/save/discard(ExportedFile)`, `BackupSummary`, `ExportedFile`, `BackupException`.
- Produces:
  - `FakeLibraryBackup` (public, `core.testing`): settable `summary`, `exportFailure: BackupFailure?`, `openResult: OpenResult`, `applyResult`, `applyFailure`; records `exports: List<Boolean>`, `saved: List<Uri>`, `discardedExports: Int`, `discardedBackups: Int`; `fun exportedFile(name: String = "Hashiya-library-2026-10-04.hashiya", missingPdfs: Int = 0): ExportedFile`; `fun preparedBackup(): PreparedBackup`. Because `ExportedFile`/`PreparedBackup` constructors are internal to core:data, add public test factories in core:data: `fun ExportedFile.Companion`? — use instead **`@VisibleForTesting` public factory functions in core/data**: `fun exportedFileForTest(file: File, fileName: String, missingPdfs: Int): ExportedFile` and `fun preparedBackupForTest(file: File): PreparedBackup` in `backup/TestFactories.kt`.
  - `SettingsUiState.backup: BackupUiState`:

```kotlin
data class BackupUiState(
    val summary: BackupSummary? = null,
    val export: ExportState = ExportState.Idle,
    val message: BackupMessage? = null
)
sealed interface ExportState {
    data object Idle : ExportState
    data class Choosing(val includePdfs: Boolean) : ExportState
    data class Building(val includePdfs: Boolean, val progress: Float) : ExportState
    /** The UI opens the save dialog with [fileName] once, then reports back with onSaveDestination. */
    data class ReadyToSave(val fileName: String) : ExportState
    data object Saving : ExportState
}
sealed interface BackupMessage {
    data class Exported(val missingPdfs: Int) : BackupMessage
    data class ExportFailed(val failure: BackupFailure) : BackupMessage
}
```

  - `SettingsViewModel` functions: `onExportClick()`, `onIncludePdfsChange(Boolean)`, `onConfirmExport()`, `onDismissExport()`, `onSaveDestination(uri: Uri?)`, `onMessageShown()`.
  - `settingsScreen(onBack: () -> Unit, onOpenRestore: (String) -> Unit)`.

- [ ] **Step 1: Add the test factories and the fake**

`android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/TestFactories.kt`:

```kotlin
package com.etatech.hashiya.core.data.backup

import androidx.annotation.VisibleForTesting
import java.io.File

/** For fakes in other modules; the app gets these only from [LibraryBackup]. */
@VisibleForTesting
fun exportedFileForTest(file: File, fileName: String, missingPdfs: Int = 0): ExportedFile = ExportedFile(file, fileName, missingPdfs)

@VisibleForTesting
fun preparedBackupForTest(file: File): PreparedBackup = PreparedBackup(file, BackupLibrary())
```

(`androidx.annotation` comes with AndroidX; if `core:data` lacks it, add `implementation(libs.androidx.annotation)` — check `libs.versions.toml` for the alias; if none exists, drop the annotation and keep the KDoc.)

`android/core/testing/src/main/java/com/etatech/hashiya/core/testing/FakeLibraryBackup.kt`:

```kotlin
package com.etatech.hashiya.core.testing

import android.net.Uri
import com.etatech.hashiya.core.data.backup.BackupException
import com.etatech.hashiya.core.data.backup.BackupFailure
import com.etatech.hashiya.core.data.backup.BackupSummary
import com.etatech.hashiya.core.data.backup.ExportedFile
import com.etatech.hashiya.core.data.backup.LibraryBackup
import com.etatech.hashiya.core.data.backup.OpenFailure
import com.etatech.hashiya.core.data.backup.OpenResult
import com.etatech.hashiya.core.data.backup.PreparedBackup
import com.etatech.hashiya.core.data.backup.RestoreResult
import com.etatech.hashiya.core.data.backup.exportedFileForTest
import com.etatech.hashiya.core.data.backup.preparedBackupForTest
import java.io.File

/** Records every call; tests set what each operation returns or throws. */
class FakeLibraryBackup : LibraryBackup {
    var summary = BackupSummary(papers = 0, collections = 0, pdfCount = 0, pdfBytes = 0)
    var exportFailure: BackupFailure? = null
    var saveFailure: BackupFailure? = null
    var missingPdfs = 0
    var openResult: OpenResult = OpenResult.Failed(OpenFailure.NotABackup)
    var applyResult = RestoreResult(papersAdded = 0, notesAdded = 0, collectionsCreated = 0, pdfsAdded = 0, pdfsMissing = 0)
    var applyFailure: BackupFailure? = null

    val exports = mutableListOf<Boolean>()
    val saved = mutableListOf<Uri>()
    val opened = mutableListOf<Uri>()
    val applied = mutableListOf<PreparedBackup>()
    var discardedExports = 0
        private set
    var discardedBackups = 0
        private set

    fun preparedBackup(): PreparedBackup = preparedBackupForTest(File("backup.hashiya"))

    override suspend fun summary(): BackupSummary = summary

    override suspend fun export(includePdfs: Boolean, onProgress: (Float) -> Unit): ExportedFile {
        exports += includePdfs
        exportFailure?.let { throw BackupException(it) }
        onProgress(1f)
        return exportedFileForTest(File("export.hashiya"), "Hashiya-library-2026-10-04.hashiya", missingPdfs)
    }

    override suspend fun save(exported: ExportedFile, destination: Uri) {
        saveFailure?.let { throw BackupException(it) }
        saved += destination
    }

    override fun discard(exported: ExportedFile) {
        discardedExports++
    }

    override suspend fun open(source: Uri): OpenResult {
        opened += source
        return openResult
    }

    override suspend fun apply(backup: PreparedBackup, onProgress: (Float) -> Unit): RestoreResult {
        applied += backup
        applyFailure?.let { throw BackupException(it) }
        onProgress(1f)
        return applyResult
    }

    override fun discard(backup: PreparedBackup) {
        discardedBackups++
    }
}
```

- [ ] **Step 2: Write the failing ViewModel tests**

In `SettingsViewModelTest`, change the constructor call to `SettingsViewModel(preferences, languageController, pdfs, FakeLibraryBackup())`.

`android/feature/settings/src/test/java/com/etatech/hashiya/feature/settings/SettingsBackupViewModelTest.kt`:

```kotlin
package com.etatech.hashiya.feature.settings

import android.net.Uri
import com.etatech.hashiya.core.data.backup.BackupFailure
import com.etatech.hashiya.core.data.backup.BackupSummary
import com.etatech.hashiya.core.testing.FakeLibraryBackup
import com.etatech.hashiya.core.testing.FakePdfRepository
import com.etatech.hashiya.core.testing.FakeUserPreferencesRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsBackupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val backup = FakeLibraryBackup().apply { summary = BackupSummary(papers = 182, collections = 6, pdfCount = 41, pdfBytes = 238_000_000) }

    private fun TestScope.viewModel(): SettingsViewModel {
        val viewModel = SettingsViewModel(FakeUserPreferencesRepository(), FakeAppLanguageController(), FakePdfRepository(), backup)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        return viewModel
    }

    private val SettingsViewModel.backupState get() = uiState.value.backup

    @Test
    fun loadsTheSummary() = runTest {
        assertEquals(182, viewModel().backupState.summary?.papers)
    }

    @Test
    fun exportWithoutPdfsByDefaultThenSave() = runTest {
        val viewModel = viewModel()
        viewModel.onExportClick()
        assertEquals(ExportState.Choosing(includePdfs = false), viewModel.backupState.export)

        viewModel.onConfirmExport()
        assertEquals(listOf(false), backup.exports)
        assertEquals(ExportState.ReadyToSave("Hashiya-library-2026-10-04.hashiya"), viewModel.backupState.export)

        val uri = Uri.parse("content://docs/1")
        viewModel.onSaveDestination(uri)
        assertEquals(listOf(uri), backup.saved)
        assertEquals(ExportState.Idle, viewModel.backupState.export)
        assertEquals(BackupMessage.Exported(missingPdfs = 0), viewModel.backupState.message)
        assertEquals(1, backup.discardedExports)
    }

    @Test
    fun includePdfsIsPassedThrough() = runTest {
        val viewModel = viewModel()
        viewModel.onExportClick()
        viewModel.onIncludePdfsChange(true)
        viewModel.onConfirmExport()
        assertEquals(listOf(true), backup.exports)
    }

    @Test
    fun dismissDiscardsExportedFile() = runTest {
        val viewModel = viewModel()
        viewModel.onExportClick()
        viewModel.onConfirmExport()
        viewModel.onSaveDestination(null)
        assertEquals(ExportState.Idle, viewModel.backupState.export)
        assertEquals(1, backup.discardedExports)
        assertEquals(null, viewModel.backupState.message)
    }

    @Test
    fun aFailedExportShowsItsReason() = runTest {
        backup.exportFailure = BackupFailure.NoSpace
        val viewModel = viewModel()
        viewModel.onExportClick()
        viewModel.onConfirmExport()
        assertEquals(ExportState.Idle, viewModel.backupState.export)
        assertEquals(BackupMessage.ExportFailed(BackupFailure.NoSpace), viewModel.backupState.message)
        viewModel.onMessageShown()
        assertEquals(null, viewModel.backupState.message)
    }

    @Test
    fun missingPdfsAreReported() = runTest {
        backup.missingPdfs = 2
        val viewModel = viewModel()
        viewModel.onExportClick()
        viewModel.onConfirmExport()
        viewModel.onSaveDestination(Uri.parse("content://docs/1"))
        assertEquals(BackupMessage.Exported(missingPdfs = 2), viewModel.backupState.message)
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :feature:settings:testDebugUnitTest --tests '*SettingsBackupViewModelTest' --tests '*SettingsViewModelTest'`
Expected: compilation FAILS — 4-argument constructor, `ExportState` unresolved.

- [ ] **Step 4: Implement the ViewModel**

In `SettingsViewModel.kt`, add the state types above `SettingsUiState` and the field `val backup: BackupUiState = BackupUiState()` to `SettingsUiState`. Then:

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val languageController: AppLanguageController,
    private val pdfRepository: PdfRepository,
    private val libraryBackup: LibraryBackup
) : ViewModel() {
    private val editedKey = MutableStateFlow<String?>(null)
    private val language = MutableStateFlow(languageController.current())
    private val storage = MutableStateFlow<PdfStorage?>(null)
    private val backup = MutableStateFlow(BackupUiState())

    /** The archive waiting for the save dialog; deleted once saved, cancelled or the screen goes away. */
    private var exported: ExportedFile? = null

    val uiState: StateFlow<SettingsUiState> =
        combine(preferences.userApiKey, editedKey, language, storage, backup) { stored, edited, lang, pdfs, backupState ->
            SettingsUiState(
                usingUserKey = stored != null,
                keyInput = edited ?: stored.orEmpty(),
                language = lang,
                storage = pdfs,
                backup = backupState
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(language = language.value))

    init {
        viewModelScope.launch { storage.value = pdfRepository.storage() }
        viewModelScope.launch { refreshSummary() }
    }

    private suspend fun refreshSummary() {
        val summary = libraryBackup.summary()
        backup.update { it.copy(summary = summary) }
    }

    // (existing key, language and storage functions unchanged)

    fun onExportClick() {
        backup.update { it.copy(export = ExportState.Choosing(includePdfs = false)) }
    }

    fun onIncludePdfsChange(include: Boolean) {
        backup.update { state -> (state.export as? ExportState.Choosing)?.let { state.copy(export = it.copy(includePdfs = include)) } ?: state }
    }

    fun onDismissExport() {
        backup.update { if (it.export is ExportState.Choosing) it.copy(export = ExportState.Idle) else it }
    }

    fun onConfirmExport() {
        val choosing = backup.value.export as? ExportState.Choosing ?: return
        backup.update { it.copy(export = ExportState.Building(choosing.includePdfs, progress = 0f)) }
        viewModelScope.launch {
            try {
                val file = libraryBackup.export(choosing.includePdfs) { progress ->
                    backup.update { state ->
                        (state.export as? ExportState.Building)?.let { state.copy(export = it.copy(progress = progress)) } ?: state
                    }
                }
                exported = file
                backup.update { it.copy(export = ExportState.ReadyToSave(file.fileName)) }
            } catch (e: BackupException) {
                backup.update { it.copy(export = ExportState.Idle, message = BackupMessage.ExportFailed(e.failure)) }
            }
        }
    }

    /** The save dialog's answer; null when it was cancelled. */
    fun onSaveDestination(uri: Uri?) {
        val file = exported ?: return
        if (uri == null) {
            discardExport()
            backup.update { it.copy(export = ExportState.Idle) }
            return
        }
        backup.update { it.copy(export = ExportState.Saving) }
        viewModelScope.launch {
            val message = try {
                libraryBackup.save(file, uri)
                BackupMessage.Exported(file.missingPdfs)
            } catch (e: BackupException) {
                BackupMessage.ExportFailed(e.failure)
            }
            discardExport()
            backup.update { it.copy(export = ExportState.Idle, message = message) }
        }
    }

    fun onMessageShown() {
        backup.update { it.copy(message = null) }
    }

    private fun discardExport() {
        exported?.let(libraryBackup::discard)
        exported = null
    }

    override fun onCleared() {
        discardExport()
    }
}
```

`combine` with five flows is supported by the typed overload. Imports: `android.net.Uri`, `com.etatech.hashiya.core.data.backup.*` types used, `kotlinx.coroutines.flow.update`.

- [ ] **Step 5: Run the ViewModel tests**

Run: `./gradlew :feature:settings:testDebugUnitTest --tests '*SettingsBackupViewModelTest' --tests '*SettingsViewModelTest'`
Expected: PASS.

- [ ] **Step 6: Add the strings**

Append to `feature/settings/src/main/res/values/strings.xml` (before `</resources>`):

```xml
    <string name="settings_backup">Backup</string>
    <string name="settings_backup_description">Save your library to a file, or add papers from a backup.</string>
    <string name="settings_export_library">Export library</string>
    <string name="settings_restore_backup">Restore from backup</string>
    <string name="settings_export_title">Export library</string>
    <string name="settings_export_counts">%1$s · %2$s</string>
    <plurals name="settings_export_papers">
        <item quantity="one">%1$d paper</item>
        <item quantity="other">%1$d papers</item>
    </plurals>
    <plurals name="settings_export_collections">
        <item quantity="one">%1$d collection</item>
        <item quantity="other">%1$d collections</item>
    </plurals>
    <string name="settings_export_include_pdfs">Include PDFs</string>
    <plurals name="settings_export_pdfs_size">
        <item quantity="one">%1$d PDF, %2$s</item>
        <item quantity="other">%1$d PDFs, %2$s</item>
    </plurals>
    <string name="settings_export_no_settings">Your API key and settings aren\'t included.</string>
    <string name="settings_export">Export</string>
    <string name="settings_exporting">Preparing backup…</string>
    <string name="settings_exported">Library exported</string>
    <plurals name="settings_exported_missing">
        <item quantity="one">Library exported. %1$d PDF was missing and wasn\'t included.</item>
        <item quantity="other">Library exported. %1$d PDFs were missing and weren\'t included.</item>
    </plurals>
    <string name="settings_export_failed_space">Couldn\'t export — not enough storage space.</string>
    <string name="settings_export_failed">Couldn\'t export the library.</string>
```

Append to `values-ar/strings.xml`:

```xml
    <string name="settings_backup">النسخ الاحتياطي</string>
    <string name="settings_backup_description">احفظ مكتبتك في ملف، أو أضف أوراقًا من نسخة احتياطية.</string>
    <string name="settings_export_library">تصدير المكتبة</string>
    <string name="settings_restore_backup">الاستعادة من نسخة احتياطية</string>
    <string name="settings_export_title">تصدير المكتبة</string>
    <string name="settings_export_counts">%1$s · %2$s</string>
    <plurals name="settings_export_papers">
        <item quantity="zero">لا أوراق</item>
        <item quantity="one">ورقة واحدة</item>
        <item quantity="two">ورقتان</item>
        <item quantity="few">%1$d أوراق</item>
        <item quantity="many">%1$d ورقة</item>
        <item quantity="other">%1$d ورقة</item>
    </plurals>
    <plurals name="settings_export_collections">
        <item quantity="zero">لا مجموعات</item>
        <item quantity="one">مجموعة واحدة</item>
        <item quantity="two">مجموعتان</item>
        <item quantity="few">%1$d مجموعات</item>
        <item quantity="many">%1$d مجموعة</item>
        <item quantity="other">%1$d مجموعة</item>
    </plurals>
    <string name="settings_export_include_pdfs">تضمين ملفات PDF</string>
    <plurals name="settings_export_pdfs_size">
        <item quantity="zero">لا ملفات PDF، %2$s</item>
        <item quantity="one">ملف PDF واحد، %2$s</item>
        <item quantity="two">ملفا PDF، %2$s</item>
        <item quantity="few">%1$d ملفات PDF، %2$s</item>
        <item quantity="many">%1$d ملف PDF، %2$s</item>
        <item quantity="other">%1$d ملف PDF، %2$s</item>
    </plurals>
    <string name="settings_export_no_settings">لا يتضمن الملف مفتاح API ولا الإعدادات.</string>
    <string name="settings_export">تصدير</string>
    <string name="settings_exporting">جارٍ تجهيز النسخة الاحتياطية…</string>
    <string name="settings_exported">تم تصدير المكتبة</string>
    <plurals name="settings_exported_missing">
        <item quantity="zero">تم تصدير المكتبة.</item>
        <item quantity="one">تم تصدير المكتبة. ملف PDF واحد كان مفقودًا ولم يُضمَّن.</item>
        <item quantity="two">تم تصدير المكتبة. ملفا PDF كانا مفقودين ولم يُضمَّنا.</item>
        <item quantity="few">تم تصدير المكتبة. %1$d ملفات PDF كانت مفقودة ولم تُضمَّن.</item>
        <item quantity="many">تم تصدير المكتبة. %1$d ملف PDF كان مفقودًا ولم يُضمَّن.</item>
        <item quantity="other">تم تصدير المكتبة. %1$d ملف PDF كان مفقودًا ولم يُضمَّن.</item>
    </plurals>
    <string name="settings_export_failed_space">تعذّر التصدير — لا توجد مساحة تخزين كافية.</string>
    <string name="settings_export_failed">تعذّر تصدير المكتبة.</string>
```

Lint: Arabic `few`/`many` items that don't use `%1$d` are fine because `zero`/`one`/`two` read the number from the word; the existing files do the same.

- [ ] **Step 7: Write the section and the export dialog**

`android/feature/settings/src/main/java/com/etatech/hashiya/feature/settings/BackupSection.kt`:

```kotlin
package com.etatech.hashiya.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.data.backup.BackupSummary

@Composable
internal fun BackupSection(state: BackupUiState, onExportClick: () -> Unit, onRestoreClick: () -> Unit) {
    Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.settings_backup_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onExportClick, enabled = (state.summary?.papers ?: 0) > 0) {
            Text(stringResource(R.string.settings_export_library))
        }
        OutlinedButton(onClick = onRestoreClick) { Text(stringResource(R.string.settings_restore_backup)) }
    }
}

/** Shown while choosing and building; the save dialog takes over after that. */
@Composable
internal fun ExportDialog(
    summary: BackupSummary,
    export: ExportState,
    onIncludePdfsChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val building = export as? ExportState.Building
    val includePdfs = (export as? ExportState.Choosing)?.includePdfs ?: building?.includePdfs ?: false
    AlertDialog(
        onDismissRequest = { if (building == null) onDismiss() },
        title = { Text(stringResource(R.string.settings_export_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.settings_export_counts,
                        pluralStringResource(R.plurals.settings_export_papers, summary.papers, summary.papers),
                        pluralStringResource(R.plurals.settings_export_collections, summary.collections, summary.collections)
                    )
                )
                if (summary.pdfCount > 0) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = includePdfs,
                                enabled = building == null,
                                role = Role.Switch,
                                onValueChange = onIncludePdfsChange
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_export_include_pdfs))
                            Text(
                                pluralStringResource(
                                    R.plurals.settings_export_pdfs_size,
                                    summary.pdfCount,
                                    summary.pdfCount,
                                    Formatter.formatShortFileSize(context, summary.pdfBytes)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = includePdfs, onCheckedChange = null, enabled = building == null)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_export_no_settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (building != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.settings_exporting), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { building.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = building == null) { Text(stringResource(R.string.settings_export)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = building == null) { Text(stringResource(R.string.settings_cancel)) }
        }
    )
}
```

- [ ] **Step 8: Wire it into the screen**

In `SettingsScreen.kt`:

1. `SettingsScreen(onBack, onOpenRestore: (String) -> Unit, viewModel)`:

```kotlin
@Composable
internal fun SettingsScreen(onBack: () -> Unit, onOpenRestore: (String) -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val saveDialog = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
        viewModel.onSaveDestination(uri)
    }
    // Drive and some file managers report a .hashiya file as a generic binary.
    val openDialog = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onOpenRestore(it.toString()) }
    }
    val readyToSave = uiState.backup.export as? ExportState.ReadyToSave
    LaunchedEffect(readyToSave) { readyToSave?.let { saveDialog.launch(it.fileName) } }
    SettingsContent(
        uiState = uiState,
        onBack = onBack,
        onKeyInputChange = viewModel::onKeyInputChange,
        onSaveKey = viewModel::onSaveKey,
        onResetKey = viewModel::onResetKey,
        onLanguageSelected = viewModel::onLanguageSelected,
        onDeleteDownloadedPdfs = viewModel::onDeleteDownloadedPdfs,
        onExportClick = viewModel::onExportClick,
        onIncludePdfsChange = viewModel::onIncludePdfsChange,
        onConfirmExport = viewModel::onConfirmExport,
        onDismissExport = viewModel::onDismissExport,
        onRestoreClick = { openDialog.launch(arrayOf(BACKUP_MIME_TYPE, "application/octet-stream")) },
        onMessageShown = viewModel::onMessageShown
    )
}

internal const val BACKUP_MIME_TYPE = "application/zip"
```

Known limitation, acceptable: if the activity is recreated while `ReadyToSave` is set and the save dialog is open, the `LaunchedEffect` launches the dialog again. Guard it: in `LaunchedEffect`, launch only when `rememberSaveable { mutableStateOf<String?>(null) }` doesn't already hold that file name, then store it; clear it when `export` goes back to `Idle`.

2. `SettingsContent` gains parameters (all defaulted so existing tests compile): `onExportClick: () -> Unit = {}`, `onIncludePdfsChange: (Boolean) -> Unit = {}`, `onConfirmExport: () -> Unit = {}`, `onDismissExport: () -> Unit = {}`, `onRestoreClick: () -> Unit = {}`, `onMessageShown: () -> Unit = {}`.

3. Add a `SnackbarHostState` and `snackbarHost = { SnackbarHost(snackbarHostState) }` to the `Scaffold`, and show messages:

```kotlin
val snackbarHostState = remember { SnackbarHostState() }
val message = uiState.backup.message
val messageText = message?.let { backupMessageText(it) }
LaunchedEffect(message) {
    if (messageText != null) {
        snackbarHostState.showSnackbar(messageText)
        onMessageShown()
    }
}
```

```kotlin
@Composable
private fun backupMessageText(message: BackupMessage): String = when (message) {
    is BackupMessage.Exported -> if (message.missingPdfs == 0) {
        stringResource(R.string.settings_exported)
    } else {
        pluralStringResource(R.plurals.settings_exported_missing, message.missingPdfs, message.missingPdfs)
    }

    is BackupMessage.ExportFailed -> stringResource(
        if (message.failure == BackupFailure.NoSpace) R.string.settings_export_failed_space else R.string.settings_export_failed
    )
}
```

4. In the column, after the language section and before storage:

```kotlin
Spacer(Modifier.height(32.dp))
BackupSection(uiState.backup, onExportClick, onRestoreClick)
```

and after the column (inside the Scaffold content):

```kotlin
val summary = uiState.backup.summary
val export = uiState.backup.export
if (summary != null && (export is ExportState.Choosing || export is ExportState.Building)) {
    ExportDialog(summary, export, onIncludePdfsChange, onConfirmExport, onDismissExport)
}
```

5. `SettingsNavigation.kt`:

```kotlin
fun NavGraphBuilder.settingsScreen(onBack: () -> Unit, onOpenRestore: (String) -> Unit) {
    composable<SettingsRoute> { SettingsScreen(onBack = onBack, onOpenRestore = onOpenRestore) }
}
```

Update `HashiyaApp.kt`'s call to `settingsScreen(onBack = { navController.popBackStack() }, onOpenRestore = {})` for now; Task 9 connects it.

- [ ] **Step 9: Add the content and screenshot tests**

Add to `SettingsContentTest` (follow its existing compose-rule setup):

```kotlin
@Test
fun exportOpensTheDialogWithCounts() {
    var state by mutableStateOf(
        SettingsUiState(backup = BackupUiState(summary = BackupSummary(papers = 182, collections = 6, pdfCount = 41, pdfBytes = 238_000_000)))
    )
    composeRule.setContent {
        SettingsContent(
            uiState = state, onBack = {}, onKeyInputChange = {}, onSaveKey = {}, onResetKey = {}, onLanguageSelected = {},
            onExportClick = { state = state.copy(backup = state.backup.copy(export = ExportState.Choosing(includePdfs = false))) }
        )
    }
    composeRule.onNodeWithText("Export library").performScrollTo().performClick()
    composeRule.onNodeWithText("182 papers · 6 collections").assertIsDisplayed()
    composeRule.onNodeWithText("Include PDFs").assertIsDisplayed()
}

@Test
fun exportIsDisabledForAnEmptyLibrary() {
    composeRule.setContent {
        SettingsContent(
            uiState = SettingsUiState(backup = BackupUiState(summary = BackupSummary(0, 0, 0, 0))),
            onBack = {}, onKeyInputChange = {}, onSaveKey = {}, onResetKey = {}, onLanguageSelected = {}
        )
    }
    composeRule.onNodeWithText("Export library").performScrollTo().assertIsNotEnabled()
}
```

Add to `SettingsScreenshotTest`:

```kotlin
@Test
fun exportDialog() = composeRule.captureScreenshot("settings_export", variant, arabicText = "تصدير المكتبة", wholeScreen = true) {
    SettingsContent(
        uiState = SettingsUiState(
            language = AppLanguage.System,
            backup = BackupUiState(
                summary = BackupSummary(papers = 182, collections = 6, pdfCount = 41, pdfBytes = 238_000_000),
                export = ExportState.Choosing(includePdfs = true)
            )
        ),
        onBack = {}, onKeyInputChange = {}, onSaveKey = {}, onResetKey = {}, onLanguageSelected = {}
    )
}
```

- [ ] **Step 10: Run the module's tests**

Run: `./gradlew :feature:settings:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS. New screenshots have no baseline yet; CI records them (as with earlier features). If the screenshot task fails locally for a missing baseline, that is expected — note it in the PR.

- [ ] **Step 11: Commit**

```bash
git add android/core/data/src/main/java/com/etatech/hashiya/core/data/backup/TestFactories.kt android/core/testing android/feature/settings android/app/src/main/java/com/etatech/hashiya/navigation/HashiyaApp.kt
git commit -m "feat(android): export the library from Settings"
```

---

### Task 9: The Restore screen, the in-app picker and opening `.hashiya` files

**Files:**
- Create: `android/feature/settings/src/main/java/com/etatech/hashiya/feature/settings/navigation/RestoreNavigation.kt`
- Create: `android/feature/settings/src/main/java/com/etatech/hashiya/feature/settings/restore/RestoreViewModel.kt`
- Create: `android/feature/settings/src/main/java/com/etatech/hashiya/feature/settings/restore/RestoreScreen.kt`
- Modify: strings (en/ar)
- Modify: `android/app/src/main/AndroidManifest.xml`, `MainActivity.kt`, `navigation/HashiyaApp.kt`
- Test: `android/feature/settings/src/test/java/com/etatech/hashiya/feature/settings/restore/RestoreViewModelTest.kt`, `RestoreScreenshotTest.kt`, `android/app/src/test/java/com/etatech/hashiya/share/BackupIntentFilterTest.kt`

**Interfaces:**
- Consumes: `LibraryBackup.open/apply/discard(PreparedBackup)`, `OpenResult`, `RestorePreview`, `RestoreResult`, `BackupException`.
- Produces:
  - `@Serializable data class RestoreRoute(val uri: String)`, `fun NavController.navigateToRestore(uri: String)`, `fun NavGraphBuilder.restoreScreen(onDone: () -> Unit)`.
  - `sealed interface RestoreUiState { Loading; Invalid(reason: OpenFailure); Preview(preview: RestorePreview); Applying(progress: Float); Done(result: RestoreResult); Failed(failure: BackupFailure) }`
  - `RestoreViewModel(savedStateHandle, libraryBackup, @ApplicationScope applicationScope: CoroutineScope)` with `onConfirm()`, `onCancel()`.
  - `HashiyaApp(..., pendingRestore: String? = null, onPendingRestoreHandled: () -> Unit = {})`.

- [ ] **Step 1: Write the failing ViewModel test**

`android/feature/settings/src/test/java/com/etatech/hashiya/feature/settings/restore/RestoreViewModelTest.kt`:

```kotlin
package com.etatech.hashiya.feature.settings.restore

import androidx.lifecycle.SavedStateHandle
import com.etatech.hashiya.core.data.backup.BackupFailure
import com.etatech.hashiya.core.data.backup.OpenFailure
import com.etatech.hashiya.core.data.backup.OpenResult
import com.etatech.hashiya.core.data.backup.RestorePreview
import com.etatech.hashiya.core.data.backup.RestoreResult
import com.etatech.hashiya.core.testing.FakeLibraryBackup
import com.etatech.hashiya.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestoreViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val backup = FakeLibraryBackup()
    private val preview = RestorePreview(exportedAt = 1L, papers = 182, collections = 6, pdfs = 41, newPapers = 150, existingPapers = 32)

    /** The test scope stands in for the application scope the restore runs in. */
    private fun TestScope.viewModel() = RestoreViewModel(SavedStateHandle(mapOf("uri" to "content://docs/backup")), backup, this)

    @Test
    fun opensTheUriAndShowsThePreview() = runTest {
        backup.openResult = OpenResult.Ready(backup.preparedBackup(), preview)
        val viewModel = viewModel()
        assertEquals("content://docs/backup", backup.opened.single().toString())
        assertEquals(RestoreUiState.Preview(preview), viewModel.uiState.value)
    }

    @Test
    fun showsWhyAFileCantBeRestored() = runTest {
        backup.openResult = OpenResult.Failed(OpenFailure.NewerFormat)
        assertEquals(RestoreUiState.Invalid(OpenFailure.NewerFormat), viewModel().uiState.value)
    }

    @Test
    fun confirmAppliesAndShowsTheResult() = runTest {
        backup.openResult = OpenResult.Ready(backup.preparedBackup(), preview)
        backup.applyResult = RestoreResult(150, 3, 6, 38, 3)
        val viewModel = viewModel()
        viewModel.onConfirm()
        assertEquals(RestoreUiState.Done(backup.applyResult), viewModel.uiState.value)
        assertEquals(1, backup.discardedBackups)
    }

    @Test
    fun aFailedRestoreSaysWhy() = runTest {
        backup.openResult = OpenResult.Ready(backup.preparedBackup(), preview)
        backup.applyFailure = BackupFailure.NoSpace
        val viewModel = viewModel()
        viewModel.onConfirm()
        assertEquals(RestoreUiState.Failed(BackupFailure.NoSpace), viewModel.uiState.value)
    }

    @Test
    fun cancelDiscards() = runTest {
        backup.openResult = OpenResult.Ready(backup.preparedBackup(), preview)
        viewModel().onCancel()
        assertEquals(1, backup.discardedBackups)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :feature:settings:testDebugUnitTest --tests '*RestoreViewModelTest'`
Expected: compilation FAILS — `RestoreViewModel` unresolved.

- [ ] **Step 3: Write the route and the ViewModel**

`android/feature/settings/src/main/java/com/etatech/hashiya/feature/settings/navigation/RestoreNavigation.kt`:

```kotlin
package com.etatech.hashiya.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.etatech.hashiya.feature.settings.restore.RestoreScreen
import kotlinx.serialization.Serializable

/** [uri]: the picked or opened `.hashiya` file. */
@Serializable
data class RestoreRoute(val uri: String)

fun NavController.navigateToRestore(uri: String) = navigate(RestoreRoute(uri))

fun NavGraphBuilder.restoreScreen(onDone: () -> Unit) {
    composable<RestoreRoute> { RestoreScreen(onDone = onDone) }
}
```

`android/feature/settings/src/main/java/com/etatech/hashiya/feature/settings/restore/RestoreViewModel.kt`:

```kotlin
package com.etatech.hashiya.feature.settings.restore

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.data.backup.BackupException
import com.etatech.hashiya.core.data.backup.BackupFailure
import com.etatech.hashiya.core.data.backup.LibraryBackup
import com.etatech.hashiya.core.data.backup.OpenFailure
import com.etatech.hashiya.core.data.backup.OpenResult
import com.etatech.hashiya.core.data.backup.PreparedBackup
import com.etatech.hashiya.core.data.backup.RestorePreview
import com.etatech.hashiya.core.data.backup.RestoreResult
import com.etatech.hashiya.core.data.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RestoreUiState {
    data object Loading : RestoreUiState

    data class Invalid(val reason: OpenFailure) : RestoreUiState

    data class Preview(val preview: RestorePreview) : RestoreUiState

    data class Applying(val progress: Float) : RestoreUiState

    data class Done(val result: RestoreResult) : RestoreUiState

    data class Failed(val failure: BackupFailure) : RestoreUiState
}

@HiltViewModel
class RestoreViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val libraryBackup: LibraryBackup,
    /** A merge can't be cancelled halfway, so it runs on, and finishes, even if the screen goes away. */
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {
    private val state = MutableStateFlow<RestoreUiState>(RestoreUiState.Loading)
    val uiState: StateFlow<RestoreUiState> = state.asStateFlow()

    /** The copied archive; deleted when the restore finishes or the screen goes away. */
    private var backup: PreparedBackup? = null

    init {
        // RestoreRoute's only argument; read by name so the ViewModel doesn't need the navigation route type.
        val uri = Uri.parse(checkNotNull(savedStateHandle.get<String>("uri")))
        viewModelScope.launch {
            state.value = when (val result = libraryBackup.open(uri)) {
                is OpenResult.Ready -> {
                    backup = result.backup
                    RestoreUiState.Preview(result.preview)
                }

                is OpenResult.Failed -> RestoreUiState.Invalid(result.reason)
            }
        }
    }

    fun onConfirm() {
        val prepared = backup ?: return
        if (state.value !is RestoreUiState.Preview) return
        state.value = RestoreUiState.Applying(0f)
        applicationScope.launch {
            state.value = try {
                RestoreUiState.Done(libraryBackup.apply(prepared) { progress -> state.value = RestoreUiState.Applying(progress) })
            } catch (e: BackupException) {
                RestoreUiState.Failed(e.failure)
            }
            discard()
        }
    }

    fun onCancel() = discard()

    private fun discard() {
        backup?.let(libraryBackup::discard)
        backup = null
    }

    override fun onCleared() = discard()
}
```

`onCleared` may delete the archive copy while `apply` still reads it; an open `ZipFile` survives the file being unlinked on Android, so the restore finishes. Check that `ApplicationScope` in `core.data.di` is public; if it is `internal`, make it public.

- [ ] **Step 4: Run the ViewModel test**

Run: `./gradlew :feature:settings:testDebugUnitTest --tests '*RestoreViewModelTest'`
Expected: PASS (5 tests).

- [ ] **Step 5: Add the strings**

English (`values/strings.xml`):

```xml
    <string name="restore_title">Restore from backup</string>
    <string name="restore_back">Back</string>
    <string name="restore_reading">Reading backup…</string>
    <string name="restore_from_date">Backup from %1$s</string>
    <string name="restore_counts">%1$s · %2$s · %3$s</string>
    <plurals name="restore_pdfs">
        <item quantity="one">%1$d PDF</item>
        <item quantity="other">%1$d PDFs</item>
    </plurals>
    <plurals name="restore_new_papers">
        <item quantity="one">%1$d new paper will be added</item>
        <item quantity="other">%1$d new papers will be added</item>
    </plurals>
    <plurals name="restore_existing_papers">
        <item quantity="one">%1$d is already in your library and stays as it is</item>
        <item quantity="other">%1$d are already in your library and stay as they are</item>
    </plurals>
    <string name="restore_keeps_library">Nothing in your library is changed or removed.</string>
    <string name="restore_add">Add to library</string>
    <string name="restore_cancel">Cancel</string>
    <string name="restore_applying">Adding to your library…</string>
    <string name="restore_done_title">Backup restored</string>
    <string name="restore_done_body">Added %1$d papers, notes on %2$d existing papers, %3$d collections and %4$d PDFs.</string>
    <plurals name="restore_done_missing_pdfs">
        <item quantity="one">%1$d PDF in the backup couldn\'t be restored.</item>
        <item quantity="other">%1$d PDFs in the backup couldn\'t be restored.</item>
    </plurals>
    <string name="restore_done">Done</string>
    <string name="restore_not_backup">This isn\'t a Hashiya backup.</string>
    <string name="restore_newer">This backup was made by a newer version of Hashiya. Update the app to restore it.</string>
    <string name="restore_damaged">This backup is damaged and can\'t be restored.</string>
    <string name="restore_unreadable">The file couldn\'t be read.</string>
    <string name="restore_failed_space">Not enough storage space to restore this backup. Nothing was changed.</string>
    <string name="restore_failed">The backup couldn\'t be restored. Nothing was changed.</string>
```

Arabic (`values-ar/strings.xml`):

```xml
    <string name="restore_title">الاستعادة من نسخة احتياطية</string>
    <string name="restore_back">رجوع</string>
    <string name="restore_reading">جارٍ قراءة النسخة الاحتياطية…</string>
    <string name="restore_from_date">نسخة احتياطية من %1$s</string>
    <string name="restore_counts">%1$s · %2$s · %3$s</string>
    <plurals name="restore_pdfs">
        <item quantity="zero">لا ملفات PDF</item>
        <item quantity="one">ملف PDF واحد</item>
        <item quantity="two">ملفا PDF</item>
        <item quantity="few">%1$d ملفات PDF</item>
        <item quantity="many">%1$d ملف PDF</item>
        <item quantity="other">%1$d ملف PDF</item>
    </plurals>
    <plurals name="restore_new_papers">
        <item quantity="zero">لن تُضاف أوراق جديدة</item>
        <item quantity="one">ستُضاف ورقة جديدة واحدة</item>
        <item quantity="two">ستُضاف ورقتان جديدتان</item>
        <item quantity="few">ستُضاف %1$d أوراق جديدة</item>
        <item quantity="many">ستُضاف %1$d ورقة جديدة</item>
        <item quantity="other">ستُضاف %1$d ورقة جديدة</item>
    </plurals>
    <plurals name="restore_existing_papers">
        <item quantity="zero">لا توجد أوراق منها في مكتبتك</item>
        <item quantity="one">ورقة واحدة موجودة في مكتبتك وتبقى كما هي</item>
        <item quantity="two">ورقتان موجودتان في مكتبتك وتبقيان كما هما</item>
        <item quantity="few">%1$d أوراق موجودة في مكتبتك وتبقى كما هي</item>
        <item quantity="many">%1$d ورقة موجودة في مكتبتك وتبقى كما هي</item>
        <item quantity="other">%1$d ورقة موجودة في مكتبتك وتبقى كما هي</item>
    </plurals>
    <string name="restore_keeps_library">لن يُغيَّر أو يُحذف شيء من مكتبتك.</string>
    <string name="restore_add">إضافة إلى المكتبة</string>
    <string name="restore_cancel">إلغاء</string>
    <string name="restore_applying">جارٍ الإضافة إلى مكتبتك…</string>
    <string name="restore_done_title">تمت استعادة النسخة الاحتياطية</string>
    <string name="restore_done_body">أُضيفت أوراق: %1$d، وملاحظات على أوراق موجودة: %2$d، ومجموعات: %3$d، وملفات PDF: %4$d.</string>
    <plurals name="restore_done_missing_pdfs">
        <item quantity="zero">استُعيدت كل ملفات PDF.</item>
        <item quantity="one">تعذّرت استعادة ملف PDF واحد من النسخة.</item>
        <item quantity="two">تعذّرت استعادة ملفي PDF من النسخة.</item>
        <item quantity="few">تعذّرت استعادة %1$d ملفات PDF من النسخة.</item>
        <item quantity="many">تعذّرت استعادة %1$d ملف PDF من النسخة.</item>
        <item quantity="other">تعذّرت استعادة %1$d ملف PDF من النسخة.</item>
    </plurals>
    <string name="restore_done">تم</string>
    <string name="restore_not_backup">هذا الملف ليس نسخة احتياطية من حاشية.</string>
    <string name="restore_newer">أُنشئت هذه النسخة بإصدار أحدث من حاشية. حدّث التطبيق لاستعادتها.</string>
    <string name="restore_damaged">هذه النسخة الاحتياطية تالفة ولا يمكن استعادتها.</string>
    <string name="restore_unreadable">تعذّرت قراءة الملف.</string>
    <string name="restore_failed_space">لا توجد مساحة تخزين كافية لاستعادة هذه النسخة. لم يتغيّر شيء.</string>
    <string name="restore_failed">تعذّرت استعادة النسخة الاحتياطية. لم يتغيّر شيء.</string>
```

Check the app name used in the existing Arabic strings (`app/src/main/res/values-ar/strings.xml`) and use the same spelling for "Hashiya" in `restore_not_backup`/`restore_newer`.

- [ ] **Step 6: Write the screen**

`android/feature/settings/src/main/java/com/etatech/hashiya/feature/settings/restore/RestoreScreen.kt`:

```kotlin
package com.etatech.hashiya.feature.settings.restore

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.etatech.hashiya.core.data.backup.BackupFailure
import com.etatech.hashiya.core.data.backup.OpenFailure
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.designsystem.layout.centeredMaxWidth
import com.etatech.hashiya.core.designsystem.layout.horizontalMargin
import com.etatech.hashiya.feature.settings.R
import java.util.Date

@Composable
internal fun RestoreScreen(onDone: () -> Unit, viewModel: RestoreViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val leave = {
        viewModel.onCancel()
        onDone()
    }
    // While applying, leaving would look like cancelling, which a merge can't be: Back waits.
    BackHandler(enabled = uiState is RestoreUiState.Applying) {}
    RestoreContent(uiState, onConfirm = viewModel::onConfirm, onLeave = leave)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RestoreContent(uiState: RestoreUiState, onConfirm: () -> Unit, onLeave: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.restore_title)) },
                navigationIcon = {
                    if (uiState !is RestoreUiState.Applying) {
                        IconButton(onClick = onLeave) { Icon(HashiyaIcons.Back, contentDescription = stringResource(R.string.restore_back)) }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .centeredMaxWidth()
                .padding(horizontal = horizontalMargin(), vertical = 16.dp)
        ) {
            when (uiState) {
                RestoreUiState.Loading -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.restore_reading))
                }

                is RestoreUiState.Invalid -> {
                    Text(invalidText(uiState.reason), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onLeave) { Text(stringResource(R.string.restore_done)) }
                }

                is RestoreUiState.Preview -> PreviewBody(uiState, onConfirm, onLeave)

                is RestoreUiState.Applying -> {
                    Text(stringResource(R.string.restore_applying))
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { uiState.progress }, modifier = Modifier.fillMaxWidth())
                }

                is RestoreUiState.Done -> {
                    val result = uiState.result
                    Text(stringResource(R.string.restore_done_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.restore_done_body, result.papersAdded, result.notesAdded, result.collectionsCreated, result.pdfsAdded))
                    if (result.pdfsMissing > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(pluralStringResource(R.plurals.restore_done_missing_pdfs, result.pdfsMissing, result.pdfsMissing))
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onLeave) { Text(stringResource(R.string.restore_done)) }
                }

                is RestoreUiState.Failed -> {
                    Text(
                        stringResource(if (uiState.failure == BackupFailure.NoSpace) R.string.restore_failed_space else R.string.restore_failed),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onLeave) { Text(stringResource(R.string.restore_done)) }
                }
            }
        }
    }
}

@Composable
private fun PreviewBody(state: RestoreUiState.Preview, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val preview = state.preview
    preview.exportedAt?.let { millis ->
        Text(
            stringResource(R.string.restore_from_date, DateFormat.getMediumDateFormat(context).format(Date(millis))),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))
    }
    Text(
        stringResource(
            R.string.restore_counts,
            pluralStringResource(com.etatech.hashiya.feature.settings.R.plurals.settings_export_papers, preview.papers, preview.papers),
            pluralStringResource(R.plurals.settings_export_collections, preview.collections, preview.collections),
            pluralStringResource(R.plurals.restore_pdfs, preview.pdfs, preview.pdfs)
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(16.dp))
    Text(pluralStringResource(R.plurals.restore_new_papers, preview.newPapers, preview.newPapers))
    if (preview.existingPapers > 0) {
        Text(pluralStringResource(R.plurals.restore_existing_papers, preview.existingPapers, preview.existingPapers))
    }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.restore_keeps_library),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onConfirm) { Text(stringResource(R.string.restore_add)) }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.restore_cancel)) }
    }
}

@Composable
private fun invalidText(reason: OpenFailure): String = stringResource(
    when (reason) {
        OpenFailure.NotABackup -> R.string.restore_not_backup
        OpenFailure.NewerFormat -> R.string.restore_newer
        OpenFailure.Damaged -> R.string.restore_damaged
        OpenFailure.Unreadable -> R.string.restore_unreadable
    }
)
```

(Replace the fully qualified `com.etatech.hashiya.feature.settings.R.plurals.settings_export_papers` with `R.plurals.settings_export_papers` — same `R`.)

Add `RestoreScreenshotTest` in `feature/settings/src/test/.../restore/` modelled on `SettingsScreenshotTest`, with two shots: `restore_preview` (`RestoreUiState.Preview(RestorePreview(1_791_259_500_000L, 182, 6, 41, 150, 32))`, `arabicText = "إضافة إلى المكتبة"`) and `restore_done` (`RestoreUiState.Done(RestoreResult(150, 3, 6, 38, 3))`, `arabicText = "تمت استعادة النسخة الاحتياطية"`).

- [ ] **Step 7: Connect navigation and the VIEW intent**

`HashiyaApp.kt`:
- Add parameters `pendingRestore: String? = null, onPendingRestoreHandled: () -> Unit = {}`.
- Replace the settings registration and add the route:

```kotlin
settingsScreen(
    onBack = { navController.popBackStack() },
    onOpenRestore = { uri -> navController.navigateToRestore(uri) }
)
restoreScreen(onDone = { navController.popBackStack() })
```

- Next to the `pendingSearch` effect:

```kotlin
LaunchedEffect(pendingRestore) {
    pendingRestore?.let { uri ->
        navController.navigateToRestore(uri)
        onPendingRestoreHandled()
    }
}
```

`MainActivity.kt`:
- Add `private var pendingRestore by mutableStateOf<String?>(null)`.
- In `onCreate`, after the search line: `if (isFreshLaunch(savedInstanceState)) pendingRestore = intent.openedBackup()`.
- In `onNewIntent`: `intent.openedBackup()?.let { pendingRestore = it }`.
- Pass `pendingRestore = pendingRestore, onPendingRestoreHandled = { pendingRestore = null }` to `HashiyaApp`.
- Add:

```kotlin
/** A `.hashiya` file opened from Files, Drive or a mail app. */
private fun Intent.openedBackup(): String? = if (action == Intent.ACTION_VIEW) data?.toString() else null
```

`AndroidManifest.xml`, inside `MainActivity`'s `<activity>` after the SEND filter:

```xml
            <!-- Opening a .hashiya backup (a zip) from Files, Drive or mail goes to Restore; a zip that isn't one says so there. -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />

                <category android:name="android.intent.category.DEFAULT" />

                <data android:scheme="content" />
                <data android:mimeType="application/zip" />
            </intent-filter>
```

`android/app/src/test/java/com/etatech/hashiya/share/BackupIntentFilterTest.kt`:

```kotlin
package com.etatech.hashiya.share

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BackupIntentFilterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun activitiesFor(type: String) = context.packageManager
        .queryIntentActivities(
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://docs/backup.hashiya"), type).setPackage(context.packageName),
            0
        )
        .map { it.activityInfo.name }

    @Test
    fun openingAZipOpensMainActivity() {
        assertEquals(listOf(MainActivity::class.java.name), activitiesFor("application/zip"))
    }

    @Test
    fun otherFilesAreNotHandled() {
        assertTrue(activitiesFor("application/pdf").isEmpty())
    }
}
```

- [ ] **Step 8: Run the tests**

Run: `./gradlew :feature:settings:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS (new screenshots recorded on CI).

- [ ] **Step 9: Commit**

```bash
git add android/feature/settings android/app
git commit -m "feat(android): restore a backup from Settings or by opening the file"
```

---

### Task 10: Auto Backup rules

**Files:**
- Modify: `android/app/src/main/res/xml/data_extraction_rules.xml`, `android/app/src/main/res/xml/backup_rules.xml`
- Test: `android/app/src/test/java/com/etatech/hashiya/BackupRulesTest.kt`

**Interfaces:**
- Consumes: the DataStore file `files/datastore/user_preferences.preferences_pb`, the database `hashiya.db`, the AppCompat locale record `files/androidx.appcompat.app.AppCompatDelegate.application_locales_record_file`, `files/pdfs/`.
- Produces: nothing other tasks use.

- [ ] **Step 1: Write the failing test**

`android/app/src/test/java/com/etatech/hashiya/BackupRulesTest.kt`:

```kotlin
package com.etatech.hashiya

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BackupRulesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** section name → "domain:path" of each include in it. */
    private fun includes(xml: Int): Map<String, List<String>> {
        val parser = context.resources.getXml(xml)
        val result = mutableMapOf<String, MutableList<String>>()
        var section = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "cloud-backup", "device-transfer", "full-backup-content" -> section = parser.name
                "include" -> result.getOrPut(section) { mutableListOf() } +=
                    "${parser.getAttributeValue(null, "domain")}:${parser.getAttributeValue(null, "path")}"
            }
        }
        return result
    }

    private val core = listOf(
        "database:.",
        "file:datastore/",
        "file:androidx.appcompat.app.AppCompatDelegate.application_locales_record_file"
    )

    @Test
    fun cloudBackupLeavesPdfsOut() {
        val rules = includes(R.xml.data_extraction_rules)
        assertEquals(core, rules["cloud-backup"])
        assertEquals(core + "file:pdfs/", rules["device-transfer"])
    }

    @Test
    fun legacyBackupMatchesCloud() {
        assertEquals(core, includes(R.xml.backup_rules)["full-backup-content"])
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*BackupRulesTest'`
Expected: FAIL — the templates have no includes.

- [ ] **Step 3: Write the rules**

`android/app/src/main/res/xml/data_extraction_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    Android 12+. Cloud backup keeps the library database and settings but not PDFs: they would quickly pass the 25 MB
    quota, after which Android stops backing up the app at all. Open-access PDFs can be downloaded again; the in-app
    export can include them. A device-to-device transfer has no quota, so it brings the PDFs too.
    The API key lives in the DataStore file; cloud backup only runs when it is end-to-end encrypted.
-->
<data-extraction-rules>
    <cloud-backup disableIfNoEncryptionCapabilities="true">
        <include domain="database" path="." />
        <include domain="file" path="datastore/" />
        <include domain="file" path="androidx.appcompat.app.AppCompatDelegate.application_locales_record_file" />
    </cloud-backup>
    <device-transfer>
        <include domain="database" path="." />
        <include domain="file" path="datastore/" />
        <include domain="file" path="androidx.appcompat.app.AppCompatDelegate.application_locales_record_file" />
        <include domain="file" path="pdfs/" />
    </device-transfer>
</data-extraction-rules>
```

`android/app/src/main/res/xml/backup_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    Android 11 and older; the same as cloud backup in data_extraction_rules.xml. The settings file (with the API key) is
    only backed up when the backup is end-to-end encrypted.
-->
<full-backup-content>
    <include domain="database" path="." />
    <include domain="file" path="datastore/" requireFlags="clientSideEncryption" />
    <include domain="file" path="androidx.appcompat.app.AppCompatDelegate.application_locales_record_file" />
</full-backup-content>
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*BackupRulesTest'`
Expected: PASS.

- [ ] **Step 5: Run everything once**

Run: `./gradlew testDebugUnitTest lintDebug`
Expected: PASS, no new lint errors (missing Arabic translations or plural quantities show up here).

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/res/xml android/app/src/test/java/com/etatech/hashiya/BackupRulesTest.kt
git commit -m "feat(android): Auto Backup keeps the library and settings, not PDFs"
```

---

## Manual device checks (after CI is green, before merge)

1. **Export → restore on a fresh install:** export with PDFs to Drive, uninstall, reinstall, restore from Drive via Settings: papers, notes, statuses, collections and PDFs come back; restore the same file again: "0 new papers" except papers with no ids.
2. **Open from Files:** tap the `.hashiya` file in the Files app (and in Drive): Hashiya offers to open it and lands on the preview.
3. **Auto Backup:** `adb shell bmgr backupnow com.etatech.hashiya`, `adb uninstall com.etatech.hashiya`, reinstall the same build with `adb install` (restore happens on install): the library and the API key come back, PDFs don't, and those papers show "Download PDF" (the sweep cleared them).
4. **RTL:** the export dialog and restore screen in Arabic.
