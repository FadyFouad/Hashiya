package com.etatech.hashiya.core.model

import java.util.Locale

/** A user-made group of saved papers. [paperCount] is how many saved papers are in it. */
data class PaperCollection(val id: Long, val name: String, val paperCount: Int)

const val COLLECTION_NAME_MAX_LENGTH = 60

/** A name is valid when, trimmed, it has 1 to [COLLECTION_NAME_MAX_LENGTH] characters. */
fun isValidCollectionName(name: String): Boolean = name.trim().length in 1..COLLECTION_NAME_MAX_LENGTH

/** Two collections may not share this key: the name trimmed and lowercased. */
fun collectionNameKey(name: String): String = name.trim().lowercase(Locale.ROOT)
