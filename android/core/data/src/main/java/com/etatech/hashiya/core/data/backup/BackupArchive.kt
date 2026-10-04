package com.etatech.hashiya.core.data.backup

import com.etatech.hashiya.core.database.model.LibrarySnapshot
import com.etatech.hashiya.core.database.model.PDF_SOURCE_ATTACHED
import com.etatech.hashiya.core.database.model.PDF_SOURCE_DOWNLOADED
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
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
    onProgress: (Float) -> Unit,
    /** Called before each PDF; throws to stop the export. */
    checkCancelled: () -> Unit = {}
): WrittenArchive {
    val refs = snapshot.papers.mapIndexed { index, row -> row.paper.id to index + 1 }.toMap()
    val notesByPaper = snapshot.notes.associateBy { it.paperId }
    val withPdf = snapshot.papers.filter { it.paper.pdfSource != null }
    val included = mutableSetOf<String>()
    var missing = 0
    ZipOutputStream(out.buffered()).use { zip ->
        if (includePdfs) {
            withPdf.forEachIndexed { index, row ->
                checkCancelled()
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

internal sealed interface ArchiveRead {
    data class Valid(val manifest: BackupManifest, val library: BackupLibrary) : ArchiveRead

    data class Invalid(val reason: OpenFailure) : ArchiveRead
}

/**
 * Reads and checks [file]'s manifest and library. Only those two entries are read; PDFs are read by name at restore. A zip the reader
 * can't handle, malformed entry names included, is not a backup.
 */
internal fun readArchive(file: File): ArchiveRead = try {
    ZipFile(file).use(::readEntries)
} catch (e: IOException) {
    ArchiveRead.Invalid(OpenFailure.NotABackup)
} catch (e: RuntimeException) {
    ArchiveRead.Invalid(OpenFailure.NotABackup)
}

private fun readEntries(zip: ZipFile): ArchiveRead {
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
