@testable import HashiyaDesignSystem
import HashiyaModel
import SwiftUI
import Testing
import UIKit

@MainActor
@Suite(.serialized)
struct CollectionNameSheetTests {
    private func inLanguage<T>(_ language: String, _ body: () -> T) -> T {
        let previous = HashiyaLanguage.override
        HashiyaLanguage.override = language
        defer { HashiyaLanguage.override = previous }
        return body()
    }

    /// The strings `view` looks up while it is laid out in a window, in English.
    private func renderedStrings(of view: some View) -> [String] {
        inLanguage("en") {
            let previous = HashiyaStrings.recordedLookups
            HashiyaStrings.recordedLookups = []
            defer { HashiyaStrings.recordedLookups = previous }
            let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
            window.rootViewController = UIHostingController(rootView: view)
            window.makeKeyAndVisible()
            window.layoutIfNeeded()
            let rendered = HashiyaStrings.recordedLookups ?? []
            window.isHidden = true
            return rendered
        }
    }

    @Test func createAndSaveAreEnabledOnlyForAValidName() {
        #expect(!CollectionNameSheet.canSubmit(""))
        #expect(!CollectionNameSheet.canSubmit("   \n"))
        #expect(CollectionNameSheet.canSubmit("Thesis"))
        #expect(CollectionNameSheet.canSubmit("  Thesis  "))
        #expect(CollectionNameSheet.canSubmit(String(repeating: "x", count: collectionNameMaxLength)))
        #expect(CollectionNameSheet.canSubmit("  " + String(repeating: "x", count: collectionNameMaxLength) + "  "))
        #expect(!CollectionNameSheet.canSubmit(String(repeating: "x", count: collectionNameMaxLength + 1)))
    }

    @Test func newCollectionHasCreateAndRenameHasSave() {
        let create = renderedStrings(of: CollectionNameSheet(mode: .create, error: nil, onSubmit: { _ in }, onCancel: {}))
        #expect(create.contains("New collection"))
        #expect(create.contains("Create"))
        #expect(create.contains("Cancel"))
        #expect(!create.contains("Save"))

        let rename = renderedStrings(of: CollectionNameSheet(mode: .rename, initialName: "Thesis", error: nil, onSubmit: { _ in }, onCancel: {}))
        #expect(rename.contains("Rename collection"))
        #expect(rename.contains("Save"))
        #expect(!rename.contains("Create"))
    }

    @Test func theStringsHaveBothLanguages() {
        #expect(inLanguage("en") { DesignSystemStrings.collectionNameTaken } == "A collection with that name already exists")
        #expect(inLanguage("ar") { DesignSystemStrings.collectionNameTaken } == "توجد مجموعة بهذا الاسم بالفعل")
        #expect(inLanguage("en") { L10n.string("collection.nameLabel") } == "Collection name")
        #expect(inLanguage("ar") { L10n.string("collection.nameLabel") } == "اسم المجموعة")
        #expect(inLanguage("ar") { L10n.string("collection.newTitle") } == "مجموعة جديدة")
        #expect(inLanguage("ar") { L10n.string("collection.renameTitle") } == "إعادة تسمية المجموعة")
        #expect(inLanguage("ar") { L10n.string("collection.create") } == "إنشاء")
        #expect(inLanguage("ar") { L10n.string("collection.save") } == "حفظ")
        #expect(inLanguage("ar") { L10n.string("collection.cancel") } == "إلغاء")
    }
}

@MainActor
@Suite(.serialized)
struct ShareSheetTests {
    @Test func returnsAtOnceWhenThereIsNoWindowToPresentFrom() async {
        // No key window is showing a view controller in a package test run.
        await ShareSheet.present(fileURL: URL(fileURLWithPath: "/tmp/none.bib"))
    }
}
