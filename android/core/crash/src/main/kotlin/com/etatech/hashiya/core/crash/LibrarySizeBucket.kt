package com.etatech.hashiya.core.crash

/** The library's size, coarse enough to say nothing about a person: 0, 1-50, 51-500, 501-5000, 5000+. */
fun librarySizeBucket(papers: Int): String = when {
    papers <= 0 -> "0"
    papers <= 50 -> "1-50"
    papers <= 500 -> "51-500"
    papers <= 5000 -> "501-5000"
    else -> "5000+"
}
