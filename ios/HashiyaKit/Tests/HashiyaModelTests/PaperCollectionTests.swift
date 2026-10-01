import HashiyaModel
import Testing

/// Mirrors Android's core/model PaperCollectionTest.
struct PaperCollectionTests {
    @Test func namesAreTrimmedAndLimitedTo60Characters() {
        #expect(isValidCollectionName("Chapter 2"))
        #expect(isValidCollectionName("  x  "))
        #expect(isValidCollectionName(String(repeating: "a", count: 60)))
        #expect(!isValidCollectionName(String(repeating: "a", count: 61)))
        #expect(!isValidCollectionName(""))
        #expect(!isValidCollectionName("   "))
        #expect(collectionNameMaxLength == 60)
    }

    /// Spec §3: Swift counts Characters, so an emoji is one (Android counts two UTF-16 units). Never stricter than Android.
    @Test func anEmojiCountsAsOneCharacter() {
        #expect(isValidCollectionName(String(repeating: "📚", count: 60)))
        #expect(!isValidCollectionName(String(repeating: "📚", count: 61)))
        #expect(isValidCollectionName("👩‍🔬" + String(repeating: "a", count: 59)))
    }

    @Test func trimmingRemovesSpacesAndNewlines() {
        #expect(trimmedCollectionName("  Thesis\n") == "Thesis")
        #expect(trimmedCollectionName("\t الفصل الثاني ") == "الفصل الثاني")
        #expect(!isValidCollectionName("\n\t"))
    }

    @Test func nameKeyIgnoresCaseAndSurroundingSpaces() {
        #expect(collectionNameKey("  Thesis Refs ") == "thesis refs")
        #expect(collectionNameKey("Thesis") == collectionNameKey(" thesis "))
        #expect(collectionNameKey(" الفصل الثاني ") == "الفصل الثاني")
    }

    @Test func paperHasEmptyPublicationDetailsByDefault() {
        let paper = Paper(openAlexID: "W1", title: "T")
        #expect(paper.publication == PublicationDetails())
        #expect(PublicationDetails().workType == nil)
        #expect(PublicationDetails().lastPage == nil)
    }

    @Test func publicationDetailsKeepTheOpenAlexStringsAsGiven() {
        let details = PublicationDetails(
            workType: "article", sourceType: "journal", publisher: "Springer Nature",
            volume: "521", issue: "7553", firstPage: "436", lastPage: "444"
        )
        let paper = Paper(openAlexID: "W1", title: "Deep learning", publication: details)
        #expect(paper.publication.workType == "article")
        #expect(paper.publication.sourceType == "journal")
        #expect(paper.publication.publisher == "Springer Nature")
        #expect(paper.publication.volume == "521")
        #expect(paper.publication.issue == "7553")
        #expect(paper.publication.firstPage == "436")
        #expect(paper.publication.lastPage == "444")
        #expect(paper != Paper(openAlexID: "W1", title: "Deep learning"))
    }

    @Test func aCollectionIsIdentifiedByItsID() {
        let collection = PaperCollection(id: 7, name: "Thesis", paperCount: 3)
        #expect(collection.id == 7)
        #expect(collection.name == "Thesis")
        #expect(collection.paperCount == 3)
    }
}
