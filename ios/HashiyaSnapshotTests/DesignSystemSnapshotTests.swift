import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import SwiftUI
import Testing

@MainActor
@Suite(.serialized)
struct DesignSystemSnapshotTests {
    @Test func paperCards() {
        let cards = ScrollView {
            VStack(spacing: 0) {
                PaperCard(paper: SamplePapers.attention, inLibrary: true, onOpen: {}, onSave: {})
                PaperCard(paper: SamplePapers.bert, inLibrary: false, onOpen: {}, onSave: {})
                PaperCard(paper: SamplePapers.arabicTitled, inLibrary: false, onOpen: {}, onSave: {})
                PaperCard(paper: SamplePapers.untitled, inLibrary: false, onOpen: {}, onSave: {})
            }
        }
        .background(HashiyaColors.surface)
        assertHashiyaSnapshots(of: cards, named: "cards", arabicText: "في المكتبة")
    }

    @Test func previewOpenAccessWithPDF() {
        let preview = PaperPreviewContent(paper: SamplePapers.attention, inLibrary: false, onToggleSave: {}, onOpenDOI: { _ in })
        assertHashiyaSnapshots(of: preview, named: "previewOpenAccessPDF", arabicText: "وصول مفتوح · ملف PDF متاح")
    }

    @Test func previewWithoutAbstractInLibrary() {
        let preview = PaperPreviewContent(paper: SamplePapers.vit, inLibrary: true, onToggleSave: {}, onOpenDOI: { _ in })
        assertHashiyaSnapshots(of: preview, named: "previewNoAbstract", arabicText: "لا يوجد ملخص")
    }

    @Test func previewWithoutDOI() {
        let preview = PaperPreviewContent(paper: SamplePapers.arabicTitled, inLibrary: false, onToggleSave: {}, onOpenDOI: { _ in })
        assertHashiyaSnapshots(of: preview, named: "previewNoDOI", arabicText: "حفظ في المكتبة")
    }

    @Test func previewWithTheStatusSelector() {
        let preview = PaperPreviewContent(
            paper: SamplePapers.arabicTitled,
            inLibrary: true,
            status: .reading,
            onToggleSave: {},
            onOpenDOI: { _ in }
        )
        assertHashiyaSnapshots(of: preview, named: "previewStatus", arabicText: "قيد القراءة")
    }

    /// A saved paper's sheet in Search: Open details above Open DOI and Remove.
    @Test func previewWithOpenDetails() {
        let preview = PaperPreviewContent(
            paper: SamplePapers.attention,
            inLibrary: true,
            onToggleSave: {},
            onOpenDOI: { _ in },
            onOpenDetails: {}
        )
        assertHashiyaSnapshots(of: preview, named: "previewOpenDetails", arabicText: "فتح التفاصيل")
    }

    @Test func messageStatesAndBanner() {
        let states = VStack(spacing: 0) {
            EmptyStateView(icon: "books.vertical", title: "Empty", message: "Message", actionTitle: "Action", action: {})
            LoadingSkeleton(rows: 2)
            HashiyaBanner(text: "Banner", actionTitle: "Undo", action: {})
            PaperCard(paper: SamplePapers.untitled, inLibrary: true, onOpen: {}, onSave: {})
        }
        .background(HashiyaColors.surface)
        assertHashiyaSnapshots(of: states, named: "components", arabicText: "بدون عنوان")
    }

    @Test func updateRequired() {
        assertHashiyaSnapshots(of: UpdateRequiredView(onUpdate: {}), named: "updateRequired", arabicText: "يلزم التحديث")
    }

    /// Chips, the two button styles and a banner over cards, so the glass has content to refract.
    ///
    /// The content lives in `GlassSurfacesFixture`'s own `body` (not a `let` built once) so the status labels are
    /// looked up fresh on every one of `assertHashiyaSnapshots`' four render passes: they read `HashiyaLanguage
    /// .override` at call time, and a `let` evaluated before the loop bakes in whatever language was current
    /// before it starts, which showed up as the Arabic passes rendering the English labels.
    @Test func glassSurfaces() {
        assertHashiyaSnapshots(of: GlassSurfacesFixture(), named: "glass", arabicText: "قيد القراءة")
    }
}

private struct GlassSurfacesFixture: View {
    var body: some View {
        ZStack(alignment: .top) {
            VStack(spacing: 0) {
                PaperCard(paper: SamplePapers.attention, inLibrary: true, onOpen: {}, onSave: {})
                PaperCard(paper: SamplePapers.bert, inLibrary: false, onOpen: {}, onSave: {})
                PaperCard(paper: SamplePapers.arabicTitled, inLibrary: false, onOpen: {}, onSave: {})
            }
            VStack(spacing: 16) {
                HashiyaGlassGroup(spacing: 8) {
                    HStack(spacing: 8) {
                        chip(readingStatusLabel(.toRead), isSelected: false)
                        chip(readingStatusLabel(.reading), isSelected: true)
                    }
                }
                HashiyaGlassGroup(spacing: 12) {
                    HStack(spacing: 12) {
                        Button {} label: {
                            Text(verbatim: readingStatusLabel(.read)).frame(maxWidth: .infinity)
                        }
                        .hashiyaSecondaryButton()
                        Button {} label: {
                            Text(verbatim: readingStatusLabel(.reading))
                                .foregroundStyle(HashiyaColors.onPrimary)
                                .frame(maxWidth: .infinity)
                        }
                        .hashiyaProminentButton()
                    }
                }
                .controlSize(.large)
                .padding(.horizontal, 16)
                HashiyaBanner(text: readingStatusLabel(.toRead), actionTitle: readingStatusLabel(.read), action: {})
            }
            .padding(.top, 60)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(HashiyaColors.surface)
    }

    private func chip(_ text: String, isSelected: Bool) -> some View {
        Text(verbatim: text)
            .font(.hashiya(.label))
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .hashiyaChip(isSelected: isSelected)
    }
}
