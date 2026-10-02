package com.etatech.hashiya.core.data.lookup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArxivLookupTest {
    @Test
    fun filterCoversUnversionedVersionedAndDoiLandingPages() {
        val pages = listOf(
            "http://arxiv.org/abs/1810.04805",
            "http://arxiv.org/abs/1810.04805v1",
            "http://arxiv.org/abs/1810.04805v2",
            "http://arxiv.org/abs/1810.04805v3",
            "http://arxiv.org/abs/1810.04805v4",
            "http://arxiv.org/abs/1810.04805v5",
            "https://arxiv.org/abs/1810.04805",
            "https://arxiv.org/abs/1810.04805v1",
            "https://arxiv.org/abs/1810.04805v2",
            "https://arxiv.org/abs/1810.04805v3",
            "https://arxiv.org/abs/1810.04805v4",
            "https://arxiv.org/abs/1810.04805v5",
            "https://doi.org/10.48550/arxiv.1810.04805"
        )
        assertEquals("locations.landing_page_url:" + pages.joinToString("|"), arxivLandingPageFilter("1810.04805"))
    }

    @Test
    fun oldStyleIdsKeepTheirSlash() {
        assertTrue(arxivLandingPageFilter("hep-th/9901001").contains("http://arxiv.org/abs/hep-th/9901001|"))
    }
}
