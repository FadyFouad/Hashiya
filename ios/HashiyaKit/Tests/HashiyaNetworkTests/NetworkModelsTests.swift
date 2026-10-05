import Foundation
import HashiyaNetwork
import HashiyaTesting
import Testing

struct NetworkModelsTests {
    private func decodePage() throws -> NetworkWorksResponse {
        try JSONDecoder().decode(NetworkWorksResponse.self, from: Fixtures.data("works_page.json"))
    }

    @Test func parsesMeta() throws {
        let page = try decodePage()
        #expect(page.meta.count == 48210)
        #expect(page.meta.nextCursor == "IlsxMDAuMCwgJ1czMTc3ODI4OTA5J10i")
        #expect(page.results.count == 2)
    }

    @Test func parsesACompleteWork() throws {
        let work = try decodePage().results[0]
        #expect(work.id == "https://openalex.org/W2626778328")
        #expect(work.doi == "https://doi.org/10.48550/arXiv.1706.03762")
        #expect(work.displayName == "Attention Is All You Need")
        #expect(work.publicationYear == 2017)
        #expect(work.primaryLocation?.source?.displayName == "Neural Information Processing Systems")
        #expect(work.authorships.map(\.author.displayName) == ["Ashish Vaswani", "Noam Shazeer", "Illia Polosukhin"])
        #expect(work.authorships.map(\.author.id) == ["https://openalex.org/A5103024730", "https://openalex.org/A5021878400", nil])
        #expect(work.citedByCount == 128412)
        #expect(work.openAccess?.isOA == true)
        #expect(work.bestOALocation?.pdfURL == "https://arxiv.org/pdf/1706.03762")
        #expect(work.abstractInvertedIndex?["dominant"] == [1])
        #expect(work.type == "preprint")
        #expect(work.biblio == NetworkBiblio(volume: "30", issue: nil, firstPage: "5998", lastPage: "6008"))
        #expect(work.primaryLocation?.source?.type == "conference")
        #expect(work.primaryLocation?.source?.hostOrganizationName == "Neural Information Processing Systems Foundation")
    }

    @Test func parsesASparseWork() throws {
        let work = try decodePage().results[1]
        #expect(work.id == "https://openalex.org/W4385245566")
        #expect(work.doi == nil)
        #expect(work.displayName == nil)
        #expect(work.publicationYear == nil)
        #expect(work.primaryLocation?.source == nil)
        #expect(work.authorships.isEmpty)
        #expect(work.citedByCount == 0)
        #expect(work.openAccess?.isOA == false)
        #expect(work.bestOALocation == nil)
        #expect(work.abstractInvertedIndex == nil)
        #expect(work.type == nil)
        #expect(work.biblio == nil)
    }

    @Test func parsesASingleWork() throws {
        let work = try JSONDecoder().decode(NetworkWork.self, from: Fixtures.data("work.json"))
        #expect(work.id == "https://openalex.org/W2919115771")
        #expect(work.primaryLocation?.source?.displayName == "Nature")
        #expect(work.authorships.count == 3)
    }

    @Test func missingFieldsTakeTheirDefaults() throws {
        let json = #"{"meta": {"next_cursor": null}, "results": [{"id": "https://openalex.org/W1", "open_access": {}}]}"#
        let page = try JSONDecoder().decode(NetworkWorksResponse.self, from: Data(json.utf8))
        #expect(page.meta.count == 0)
        #expect(page.meta.nextCursor == nil)
        #expect(page.results[0].authorships.isEmpty)
        #expect(page.results[0].citedByCount == 0)
        #expect(page.results[0].openAccess?.isOA == false)
    }

    @Test func missingResultsIsAnEmptyPage() throws {
        let page = try JSONDecoder().decode(NetworkWorksResponse.self, from: Data(#"{"meta": {"count": 0}}"#.utf8))
        #expect(page.results.isEmpty)
    }

    @Test func missingBiblioAndSourceFieldsDecodeAsNil() throws {
        let json = #"{"id": "https://openalex.org/W1", "biblio": {}, "primary_location": {"source": {"display_name": "Nature"}}}"#
        let work = try JSONDecoder().decode(NetworkWork.self, from: Data(json.utf8))
        #expect(work.biblio == NetworkBiblio())
        #expect(work.primaryLocation?.source == NetworkSource(displayName: "Nature"))
        #expect(work.primaryLocation?.source?.type == nil)
        #expect(work.primaryLocation?.source?.hostOrganizationName == nil)
    }

    @Test func aBodyWithoutMetaIsMalformed() {
        #expect(throws: DecodingError.self) {
            try JSONDecoder().decode(NetworkWorksResponse.self, from: Data(#"{"unexpected": true}"#.utf8))
        }
    }

    @Test func aLocationWithoutIsOAIsNotOpenAccess() throws {
        let location = try JSONDecoder().decode(NetworkLocation.self, from: Data(#"{"pdf_url": "https://a.example/x.pdf"}"#.utf8))
        #expect(location == NetworkLocation(pdfURL: "https://a.example/x.pdf", source: nil, isOA: false))
        let open = try JSONDecoder().decode(NetworkLocation.self, from: Data(#"{"is_oa": true, "pdf_url": null}"#.utf8))
        #expect(open.isOA)
    }

    @Test func decodesThePrimaryTopicIds() throws {
        let json = #"{"id":"https://openalex.org/W1","primary_topic":{"id":"https://openalex.org/T10036","display_name":"Advanced Neural Network Applications","subfield":{"id":"https://openalex.org/subfields/1707","display_name":"Computer Vision and Pattern Recognition"},"field":{"id":"https://openalex.org/fields/17","display_name":"Computer Science"},"domain":{"id":"https://openalex.org/domains/3","display_name":"Physical Sciences"}}}"#
        let work = try JSONDecoder().decode(NetworkWork.self, from: Data(json.utf8))
        #expect(work.primaryTopic == NetworkTopic(subfieldID: "https://openalex.org/subfields/1707", fieldID: "https://openalex.org/fields/17", domainID: "https://openalex.org/domains/3"))
    }

    @Test func aWorkWithoutAPrimaryTopicStillDecodes() throws {
        let work = try JSONDecoder().decode(NetworkWork.self, from: Data(#"{"id":"https://openalex.org/W1","primary_topic":null}"#.utf8))
        #expect(work.primaryTopic == nil)
    }
}
