package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper

object SamplePapers {
    val attention = Paper(
        openAlexId = "W2626778328",
        doi = "10.48550/arxiv.1706.03762",
        title = "Attention Is All You Need",
        authors = listOf(
            Author("Ashish Vaswani", "A5103024730"),
            Author("Noam Shazeer", "A5021878400"),
            Author("Niki Parmar", null),
            Author("Jakob Uszkoreit", null),
            Author("Llion Jones", null)
        ),
        year = 2017,
        venue = "Neural Information Processing Systems",
        abstract = "The dominant sequence transduction models are based on complex recurrent or convolutional " +
            "neural networks that include an encoder and a decoder. We propose a new simple network " +
            "architecture, the Transformer, based solely on attention mechanisms.",
        citationCount = 128412,
        isOpenAccess = true,
        openAccessPdfUrl = "https://arxiv.org/pdf/1706.03762"
    )

    val bert = Paper(
        openAlexId = "W2896457183",
        doi = "10.18653/v1/n19-1423",
        title = "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding",
        authors = listOf(
            Author("Jacob Devlin", null),
            Author("Ming-Wei Chang", null),
            Author("Kenton Lee", null),
            Author("Kristina Toutanova", null)
        ),
        year = 2019,
        venue = "NAACL",
        abstract = "We introduce a new language representation model called BERT, which stands for Bidirectional " +
            "Encoder Representations from Transformers.",
        citationCount = 94112,
        isOpenAccess = true,
        openAccessPdfUrl = null
    )

    val vit = Paper(
        openAlexId = "W3094502228",
        doi = null,
        title = "An Image Is Worth 16x16 Words: Transformers for Image Recognition at Scale",
        authors = listOf(Author("Alexey Dosovitskiy", null), Author("Lucas Beyer", null)),
        year = 2021,
        venue = "ICLR",
        abstract = null,
        citationCount = 41230,
        isOpenAccess = false,
        openAccessPdfUrl = null
    )

    /** Right-to-left paper content, to check mixed-direction layouts. */
    val arabicTitled = Paper(
        openAlexId = "W4000000001",
        doi = null,
        title = "تطبيقات التعلم العميق في معالجة اللغة العربية",
        authors = listOf(Author("محمد علي", null)),
        year = 2022,
        venue = null,
        abstract = null,
        citationCount = 12,
        isOpenAccess = false,
        openAccessPdfUrl = null
    )

    /** A work with no title in OpenAlex. */
    val untitled = Paper(
        openAlexId = "W4000000002",
        doi = null,
        title = "",
        authors = emptyList(),
        year = null,
        venue = null,
        abstract = null,
        citationCount = 0,
        isOpenAccess = false,
        openAccessPdfUrl = null
    )

    val all = listOf(attention, bert, vit)
}
