package com.etatech.hashiya.core.analytics

/** Papers whose notes were edited in this process, so `note_edited` counts each paper once per session. Never sent. */
object NotedPapers {
    private val ids = mutableSetOf<String>()

    /** True the first time [openAlexId] is seen in this process. */
    @Synchronized
    fun firstEdit(openAlexId: String): Boolean = ids.add(openAlexId)
}
