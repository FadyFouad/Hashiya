package com.etatech.hashiya.core.model

/** Where the user is with a saved paper. New saves start as [ToRead]. */
enum class ReadingStatus { ToRead, Reading, Read }

/** A paper in the user's library, with its reading status. */
data class LibraryPaper(val paper: Paper, val status: ReadingStatus)
