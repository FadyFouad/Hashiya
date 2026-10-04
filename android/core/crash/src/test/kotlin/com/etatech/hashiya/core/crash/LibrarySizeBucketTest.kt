package com.etatech.hashiya.core.crash

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySizeBucketTest {
    @Test
    fun boundaries() {
        mapOf(0 to "0", 1 to "1-50", 50 to "1-50", 51 to "51-500", 500 to "51-500", 501 to "501-5000", 5000 to "501-5000", 5001 to "5000+")
            .forEach { (papers, bucket) -> assertEquals("$papers", bucket, librarySizeBucket(papers)) }
    }
}
