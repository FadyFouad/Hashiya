import HashiyaModel
import Testing

struct ImportJobTests {
    @Test func theCollectionIsNamedAfterTheFile() {
        #expect(importCollectionName(fileName: "thesis.bib") == "thesis")
        #expect(importCollectionName(fileName: "  My library .ris") == "My library")
        #expect(importCollectionName(fileName: ".bib") == ".bib")
        #expect(importCollectionName(fileName: " .ris") == "Import")
        #expect(importCollectionName(fileName: String(repeating: "a", count: 80) + ".bib").count == collectionNameMaxLength)
    }

    @Test func onlyAnalyzingAndImportingAreRunning() {
        let running = [ImportState.analyzing, .ready, .importing, .done, .failed, .cancelled].filter {
            ImportJob(id: 1, fileName: "f", state: $0).isRunning
        }
        #expect(running == [.analyzing, .importing])
        #expect(ImportCounts(matched: 2, local: 3).toImport == 5)
    }
}
