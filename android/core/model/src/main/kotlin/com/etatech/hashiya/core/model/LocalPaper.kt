package com.etatech.hashiya.core.model

/**
 * Papers imported from a file that OpenAlex couldn't match are stored with an id of this prefix and a UUID in place of an OpenAlex
 * id, so every screen keyed by `openAlexId` works for them unchanged.
 */
const val LOCAL_PAPER_ID_PREFIX = "local-"

/** True for an id made with [LOCAL_PAPER_ID_PREFIX]: a paper that exists only in this library, never sent to OpenAlex. */
fun isLocalPaperId(id: String?): Boolean = id?.startsWith(LOCAL_PAPER_ID_PREFIX) == true

/** True when the paper came from an imported file and has no OpenAlex record. */
val Paper.isLocal: Boolean get() = isLocalPaperId(openAlexId)
