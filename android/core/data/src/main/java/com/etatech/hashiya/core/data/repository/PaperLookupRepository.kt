package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.SearchError

/** Finds the single paper a DOI or arXiv ID refers to. */
interface PaperLookupRepository {
    suspend fun lookup(identifier: PaperIdentifier): LookupResult
}

sealed interface LookupResult {
    data class Found(val paper: Paper) : LookupResult

    /** [arxivTitle] is arXiv's own title for an arXiv ID OpenAlex couldn't match, so the UI can offer a title search. */
    data class NotFound(val arxivTitle: String?) : LookupResult

    data class Failed(val error: SearchError) : LookupResult
}
