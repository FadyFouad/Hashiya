import Foundation
import HashiyaData
import Testing

struct ExportFilesTests {
    @Test func wholeLibrary() {
        #expect(ExportFiles.fileName(collectionName: nil) == "hashiya-library")
    }

    @Test func collectionNameWithUnsafeCharactersReplaced() {
        #expect(ExportFiles.fileName(collectionName: "Chapter 2") == "Chapter 2")
        #expect(ExportFiles.fileName(collectionName: "a/b\\c:d*e?f\"g<h>i|j") == "a-b-c-d-e-f-g-h-i-j")
        #expect(ExportFiles.fileName(collectionName: "tab\tx") == "tab-x")
        #expect(ExportFiles.fileName(collectionName: "الفصل الثاني") == "الفصل الثاني")
    }

    @Test func nothingLeftIsCollection() {
        #expect(ExportFiles.fileName(collectionName: "/") == "-")
        #expect(ExportFiles.fileName(collectionName: "   ") == "collection")
    }

    @Test func writesUTF8AndDeletesEarlierExports() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let files = ExportFiles(directory: directory)

        let first = try files.write("@misc{a,\n}\n", name: "hashiya-library")
        let second = try files.write("@misc{müller2020,\n}\n", name: "Thesis")

        #expect(second.lastPathComponent == "Thesis.bib")
        #expect(try String(contentsOf: second, encoding: .utf8) == "@misc{müller2020,\n}\n")
        #expect(!FileManager.default.fileExists(atPath: first.path))
        #expect(try FileManager.default.contentsOfDirectory(atPath: directory.path) == ["Thesis.bib"])
    }

    @Test func liveIsTheCachesExportsFolder() {
        #expect(ExportFiles.live.directory.lastPathComponent == "exports")
        #expect(ExportFiles.live.directory.deletingLastPathComponent().lastPathComponent == "Caches")
    }
}
