package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.network.model.NetworkLocation
import com.etatech.hashiya.core.network.model.NetworkSource
import org.junit.Assert.assertEquals
import org.junit.Test

class FallbackPdfLinksTest {
    private fun oa(url: String?, source: String? = null, isOa: Boolean = true) =
        NetworkLocation(source = source?.let { NetworkSource(displayName = it) }, pdfUrl = url, isOa = isOa)

    @Test
    fun keepsOpenAccessLinksOverHttpsWithoutTheOneTried() {
        val links = fallbackPdfLinks(
            listOf(
                oa("https://langtaosha.org.cn/download/10/108"),
                oa("https://closed.example/paper.pdf", isOa = false),
                oa(null),
                oa("  "),
                oa("http://repository.example/a.pdf")
            ),
            tried = "https://langtaosha.org.cn/download/10/108"
        )

        assertEquals(listOf("https://repository.example/a.pdf"), links)
    }

    @Test
    fun putsArxivFirstThenKeepsOpenAlexsOrder() {
        val links = fallbackPdfLinks(
            listOf(
                oa("https://one.example/a.pdf"),
                oa("https://two.example/b.pdf"),
                oa("https://export.arxiv.org/pdf/1706.03762v7"),
                oa("https://mirror.example/1706.03762.pdf", source = "arXiv (Cornell University)"),
                oa("http://arxiv.org/pdf/1706.03762")
            ),
            tried = "https://stored.example/x"
        )

        assertEquals(
            listOf(
                "https://export.arxiv.org/pdf/1706.03762v7",
                "https://mirror.example/1706.03762.pdf",
                "https://arxiv.org/pdf/1706.03762"
            ),
            links
        )
    }

    @Test
    fun dropsDuplicatesAfterTheUpgradeAndKeepsAtMostThree() {
        val links = fallbackPdfLinks(
            listOf(
                oa("http://arxiv.org/pdf/1706.03762"),
                oa("https://arxiv.org/pdf/1706.03762"),
                oa("https://one.example/a.pdf"),
                oa("https://two.example/b.pdf"),
                oa("https://three.example/c.pdf")
            ),
            tried = "http://stored.example/x"
        )

        assertEquals(listOf("https://arxiv.org/pdf/1706.03762", "https://one.example/a.pdf", "https://two.example/b.pdf"), links)
    }

    @Test
    fun theTriedLinkIsDroppedInEitherScheme() {
        assertEquals(
            emptyList<String>(),
            fallbackPdfLinks(listOf(oa("http://arxiv.org/pdf/1"), oa("https://arxiv.org/pdf/1")), tried = "https://arxiv.org/pdf/1")
        )
    }

    @Test
    fun aHostThatOnlyEndsInArxivIsNotArxiv() {
        val links = fallbackPdfLinks(
            listOf(oa("https://one.example/a.pdf"), oa("https://notarxiv.org/b.pdf")),
            tried = "https://stored.example/x"
        )

        assertEquals(listOf("https://one.example/a.pdf", "https://notarxiv.org/b.pdf"), links)
    }
}
