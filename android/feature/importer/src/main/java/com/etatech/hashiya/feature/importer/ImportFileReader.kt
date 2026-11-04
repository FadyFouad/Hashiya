package com.etatech.hashiya.feature.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.etatech.hashiya.core.data.repository.MAX_IMPORT_FILE_BYTES
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.ViewModelComponent
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImportFile(val name: String, val bytes: ByteArray)

/** Reads a file the user picked or shared. */
interface ImportFileReader {
    /** The file's name and bytes (at most one byte over the import limit, so a large file is never read whole); null if unreadable. */
    suspend fun read(uri: String): ImportFile?
}

internal class ContentResolverImportFileReader @Inject constructor(@ApplicationContext private val context: Context) : ImportFileReader {
    override suspend fun read(uri: String): ImportFile? = withContext(Dispatchers.IO) {
        try {
            val parsed = Uri.parse(uri)
            val resolver = context.contentResolver
            val bytes = resolver.openInputStream(parsed)?.use { it.readAtMost(MAX_IMPORT_FILE_BYTES + 1) } ?: return@withContext null
            val name = resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: parsed.lastPathSegment
            ImportFile(name?.takeIf { it.isNotBlank() } ?: "import", bytes)
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            // A share or Open with whose read grant is gone.
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

@Module
@InstallIn(ViewModelComponent::class)
internal abstract class ImportFileReaderModule {
    @Binds
    abstract fun bindImportFileReader(impl: ContentResolverImportFileReader): ImportFileReader
}
