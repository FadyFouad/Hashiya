import HashiyaModel

/// The same papers as Android's `SamplePapers`.
public enum SamplePapers {
    public static let attention = Paper(
        openAlexID: "W2626778328",
        doi: "10.48550/arxiv.1706.03762",
        title: "Attention Is All You Need",
        authors: [
            Author(name: "Ashish Vaswani", openAlexID: "A5103024730"),
            Author(name: "Noam Shazeer", openAlexID: "A5021878400"),
            Author(name: "Niki Parmar"),
            Author(name: "Jakob Uszkoreit"),
            Author(name: "Llion Jones"),
        ],
        year: 2017,
        venue: "Neural Information Processing Systems",
        abstract: "The dominant sequence transduction models are based on complex recurrent or convolutional "
            + "neural networks that include an encoder and a decoder. We propose a new simple network "
            + "architecture, the Transformer, based solely on attention mechanisms.",
        citationCount: 128_412,
        isOpenAccess: true,
        openAccessPDFURL: "https://arxiv.org/pdf/1706.03762"
    )

    public static let bert = Paper(
        openAlexID: "W2896457183",
        doi: "10.18653/v1/n19-1423",
        title: "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding",
        authors: [
            Author(name: "Jacob Devlin"),
            Author(name: "Ming-Wei Chang"),
            Author(name: "Kenton Lee"),
            Author(name: "Kristina Toutanova"),
        ],
        year: 2019,
        venue: "NAACL",
        abstract: "We introduce a new language representation model called BERT, which stands for Bidirectional "
            + "Encoder Representations from Transformers.",
        citationCount: 94_112,
        isOpenAccess: true,
        openAccessPDFURL: nil
    )

    public static let vit = Paper(
        openAlexID: "W3094502228",
        doi: nil,
        title: "An Image Is Worth 16x16 Words: Transformers for Image Recognition at Scale",
        authors: [Author(name: "Alexey Dosovitskiy"), Author(name: "Lucas Beyer")],
        year: 2021,
        venue: "ICLR",
        abstract: nil,
        citationCount: 41_230,
        isOpenAccess: false,
        openAccessPDFURL: nil
    )

    /// Right-to-left paper content, to check mixed-direction layouts.
    public static let arabicTitled = Paper(
        openAlexID: "W4000000001",
        doi: nil,
        title: "تطبيقات التعلم العميق في معالجة اللغة العربية",
        authors: [Author(name: "محمد علي")],
        year: 2022,
        venue: nil,
        abstract: nil,
        citationCount: 12,
        isOpenAccess: false,
        openAccessPDFURL: nil
    )

    /// A work with no title in OpenAlex.
    public static let untitled = Paper(
        openAlexID: "W4000000002",
        doi: nil,
        title: "",
        authors: [],
        year: nil,
        venue: nil,
        abstract: nil,
        citationCount: 0,
        isOpenAccess: false,
        openAccessPDFURL: nil
    )

    public static let all = [attention, bert, vit]
}
