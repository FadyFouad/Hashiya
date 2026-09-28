package com.etatech.hashiya.core.model

import com.etatech.hashiya.core.model.PaperIdentifier.Arxiv
import com.etatech.hashiya.core.model.PaperIdentifier.Doi
import org.junit.Assert.assertEquals
import org.junit.Test

class PaperIdentifierTest {
    private val sici = "10.1002/(sici)1099-1212(199901/02)9:1<8::aid-oa453>3.0.co;2-z"

    private fun strict(vararg cases: Pair<String, PaperIdentifier?>) = cases.forEach { (input, expected) ->
        assertEquals("parsePaperIdentifier(\"$input\")", expected, parsePaperIdentifier(input))
    }

    private fun lenient(vararg cases: Pair<String, PaperIdentifier?>) = cases.forEach { (input, expected) ->
        assertEquals("extractPaperIdentifier(\"$input\")", expected, extractPaperIdentifier(input))
    }

    @Test
    fun strictRecognizesDois() = strict(
        "10.1038/nature14539" to Doi("10.1038/nature14539"),
        "  https://doi.org/10.1038/NATURE14539 " to Doi("10.1038/nature14539"),
        "http://dx.doi.org/10.1038/nature14539" to Doi("10.1038/nature14539"),
        "doi:10.1038/nature14539" to Doi("10.1038/nature14539"),
        "DOI: 10.1038/nature14539" to Doi("10.1038/nature14539"),
        "https://onlinelibrary.wiley.com/doi/full/10.1002/anie.201915678?af=R#section" to Doi("10.1002/anie.201915678"),
        "https://dl.acm.org/doi/10.1145/3292500.3330701" to Doi("10.1145/3292500.3330701"),
        "https://link.springer.com/article/10.1007/s11263-015-0816-y" to Doi("10.1007/s11263-015-0816-y"),
        "https://doi.org/10.1002/(SICI)1099-1212(199901/02)9:1%3C8::AID-OA453%3E3.0.CO;2-Z" to Doi(sici),
        "10.1038/nature14539." to Doi("10.1038/nature14539")
    )

    @Test
    fun publisherLinksDropPdfAndViewSuffixes() = strict(
        "https://link.springer.com/content/pdf/10.1007/s11263-015-0816-y.pdf" to Doi("10.1007/s11263-015-0816-y"),
        "https://onlinelibrary.wiley.com/doi/10.1002/anie.201915678/full" to Doi("10.1002/anie.201915678"),
        "https://onlinelibrary.wiley.com/doi/10.1002/anie.201915678/abstract" to Doi("10.1002/anie.201915678"),
        "https://onlinelibrary.wiley.com/doi/10.1002/anie.201915678/epdf" to Doi("10.1002/anie.201915678"),
        "https://example.org/10.1002/anie.201915678/PDF/" to Doi("10.1002/anie.201915678"),
        "https://www.biorxiv.org/content/10.1101/2020.01.01.123456v1" to Doi("10.1101/2020.01.01.123456"),
        "https://www.biorxiv.org/content/10.1101/2020.01.01.123456v2.full" to Doi("10.1101/2020.01.01.123456"),
        "https://www.medrxiv.org/content/10.1101/2020.01.01.123456v1.full.pdf" to Doi("10.1101/2020.01.01.123456")
    )

    @Test
    fun doiLinksAndPlainDoisKeepTheirSuffixes() = strict(
        "https://doi.org/10.1000/xyz.pdf" to Doi("10.1000/xyz.pdf"),
        "https://doi.org/10.1000/abc/full" to Doi("10.1000/abc/full"),
        "doi:10.1000/xyz.pdf" to Doi("10.1000/xyz.pdf"),
        "10.1101/2020.01.01.123456v1" to Doi("10.1101/2020.01.01.123456v1")
    )

    @Test
    fun strictRecognizesArxivIds() = strict(
        "1706.03762" to Arxiv("1706.03762"),
        "2401.00001v2" to Arxiv("2401.00001"),
        "ARXIV:2401.00001" to Arxiv("2401.00001"),
        "arXiv: 2401.00001" to Arxiv("2401.00001"),
        "https://arxiv.org/abs/1706.03762v5" to Arxiv("1706.03762"),
        "arxiv.org/pdf/2401.00001v2.pdf" to Arxiv("2401.00001"),
        "https://www.arxiv.org/abs/2310.06825" to Arxiv("2310.06825"),
        "http://export.arxiv.org/abs/hep-th/9901001v2" to Arxiv("hep-th/9901001"),
        "hep-th/9901001" to Arxiv("hep-th/9901001"),
        "math.GT/0309136" to Arxiv("math/0309136"),
        "10.48550/ARXIV.1706.03762" to Arxiv("1706.03762"),
        "https://doi.org/10.48550/arXiv.2310.06825" to Arxiv("2310.06825"),
        "10.48550/arXiv.math/0309136" to Arxiv("math/0309136")
    )

    @Test
    fun strictAcceptsOnlyRealNewStyleArxivShapes() = strict(
        "0704.0001" to Arxiv("0704.0001"),
        "1412.6980" to Arxiv("1412.6980"),
        "2401.00001" to Arxiv("2401.00001"),
        "1706.0376" to null,
        "2401.0001" to null,
        "0612.0001" to null,
        "1412.69801" to null
    )

    @Test
    fun strictRejectsEverythingElse() = strict(
        "" to null,
        "   " to null,
        "machine learning" to null,
        "a study of 10.1038/nature14539" to null,
        "https://example.com/2401.00001" to null,
        "https://arxiv.org/list/cs.LG/recent" to null,
        "https://www.nature.com/articles/nature14539" to null,
        "10.1038" to null,
        "2401.001" to null,
        "2023.12345" to null,
        "1234" to null
    )

    @Test
    fun strictHandlesParentheses() = strict(
        "10.1000/abc(1)" to Doi("10.1000/abc(1)"),
        "(10.1000/abc)" to Doi("10.1000/abc"),
        "<https://arxiv.org/abs/1706.03762>" to Arxiv("1706.03762")
    )

    @Test
    fun lenientFindsIdsInsideText() = lenient(
        "Attention Is All You Need https://arxiv.org/abs/1706.03762" to Arxiv("1706.03762"),
        "a study of 10.1038/nature14539." to Doi("10.1038/nature14539"),
        "(see doi: 10.1000/xyz123)" to Doi("10.1000/xyz123"),
        "Ref: 10.1000/abc(1), page 3" to Doi("10.1000/abc(1)"),
        "SICI $sici is old" to Doi(sici),
        "see 2401.00001, it is good" to Arxiv("2401.00001"),
        "check https://example.com/page and 10.1000/xyz" to Doi("10.1000/xyz"),
        "Check this out: https://arxiv.org/abs/2401.00001v2" to Arxiv("2401.00001"),
        "Check this out: [Attention Is All You Need](https://arxiv.org/abs/1706.03762)" to Arxiv("1706.03762"),
        "[Deep learning](https://doi.org/10.1038/nature14539)" to Doi("10.1038/nature14539"),
        "Link:https://arxiv.org/abs/2401.00001" to Arxiv("2401.00001")
    )

    @Test
    fun lenientPrefersTheFirstLink() = lenient(
        "https://arxiv.org/abs/2401.00001 also 10.1038/nature14539" to Arxiv("2401.00001"),
        "10.1038/nature14539 then https://arxiv.org/abs/2401.00001" to Arxiv("2401.00001")
    )

    @Test
    fun lenientRejectsTextWithoutIds() = lenient(
        "Deep learning https://www.nature.com/articles/nature14539" to null,
        "https://example.com/2401.00001" to null,
        "[x](https://example.com/2401.00001)" to null,
        "nothing to see here" to null,
        "" to null
    )

    @Test
    fun lenientIgnoresTextBeyondTwoThousandCharacters() {
        val longText = "x".repeat(2_000) + " 10.1038/nature14539"
        assertEquals(null, extractPaperIdentifier(longText))
        assertEquals(Doi("10.1038/nature14539"), extractPaperIdentifier("x".repeat(1_900) + " 10.1038/nature14539"))
    }
}
