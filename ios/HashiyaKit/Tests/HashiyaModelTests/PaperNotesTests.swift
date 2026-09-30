import HashiyaModel
import Testing

struct PaperNotesTests {
    @Test func sectionsAreInTheTemplatesOrder() {
        #expect(NoteSection.allCases == [.summary, .researchQuestion, .method, .keyFindings, .limitations, .thoughts])
    }

    @Test(arguments: NoteSection.allCases)
    func eachSectionReadsAndWritesItsOwnText(section: NoteSection) {
        let notes = PaperNotes().with(section, "Text for \(section)")

        #expect(notes[section] == "Text for \(section)")
        for other in NoteSection.allCases where other != section {
            #expect(notes[other] == "")
        }
    }

    @Test func theSubscriptSetsASection() {
        var notes = PaperNotes(summary: "S")
        notes[.thoughts] = "T"
        #expect(notes == PaperNotes(summary: "S", thoughts: "T"))
        #expect(notes.summary == "S")
        #expect(notes.thoughts == "T")
    }

    @Test func textIsKeptExactlyAsTyped() {
        let text = "  Two lines\nwith spaces  "
        #expect(PaperNotes().with(.method, text).method == text)
    }

    @Test func blankNotesAreEmpty() {
        #expect(PaperNotes().isEmpty)
        #expect(PaperNotes(summary: "   ", method: "\n\t", thoughts: " \n ").isEmpty)
        #expect(!PaperNotes(limitations: " x ").isEmpty)
        #expect(!PaperNotes(keyFindings: "٣").isEmpty)
    }
}
