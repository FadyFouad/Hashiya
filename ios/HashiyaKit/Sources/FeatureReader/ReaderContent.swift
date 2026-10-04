import HashiyaDesignSystem
import SwiftUI

/// What the reader does when the user acts; `ReaderScreen` wires these to the view model.
public struct ReaderActions {
    public var back: () -> Void = {}
    public var search: () -> Void = {}
    public var showNotes: () -> Void = {}
    public var replace: () -> Void = {}
    public var removePdf: () -> Void = {}
    public var retrySave: () -> Void = {}

    public init() {}
}

/// The reader's chrome around its pages: the navigation bar's Back, Search, Notes and Share, the page pill, the
/// banners, and the can't-open screen. Generic over the pages so snapshots can draw still images. Put it in a
/// `NavigationStack`.
public struct ReaderContent<Pages: View>: View {
    private let title: String
    private let state: ReaderState
    private let pageLabel: String?
    private let showsPill: Bool
    private let fileURL: URL?
    private let message: ReaderMessage?
    private let notesSaveFailed: Bool
    private let actions: ReaderActions
    private let pages: () -> Pages

    public init(
        title: String,
        state: ReaderState,
        pageLabel: String?,
        showsPill: Bool,
        fileURL: URL?,
        message: ReaderMessage?,
        notesSaveFailed: Bool,
        actions: ReaderActions,
        @ViewBuilder pages: @escaping () -> Pages
    ) {
        self.title = title
        self.state = state
        self.pageLabel = pageLabel
        self.showsPill = showsPill
        self.fileURL = fileURL
        self.message = message
        self.notesSaveFailed = notesSaveFailed
        self.actions = actions
        self.pages = pages
    }

    public var body: some View {
        ZStack {
            HashiyaColors.surface.ignoresSafeArea()
            switch state {
            case .loading:
                ProgressView()
            case .ready:
                // The pages keep their own direction: a PDF is never mirrored.
                pages()
                    .environment(\.layoutDirection, .leftToRight)
                    .ignoresSafeArea(edges: .bottom)
            case .cantOpen:
                cantOpen
            }
        }
        .overlay(alignment: .bottom) {
            // The pill above the banner; on iOS 26 both are glass, grouped so they blend as they come and go. The
            // banner pads itself.
            HashiyaGlassGroup(spacing: 8) {
                VStack(spacing: 0) {
                    if showsPill, case .ready = state, let pageLabel {
                        PagePill(text: pageLabel)
                            .padding(.bottom, 8)
                            .transition(.opacity)
                    }
                    banner
                }
                .padding(.bottom, 8)
                .animation(.default, value: showsPill)
                .animation(.default, value: message)
                .animation(.default, value: notesSaveFailed)
            }
        }
        .navigationTitle(Text(verbatim: title))
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: actions.back) {
                    Image(systemName: "chevron.backward")
                }
                .accessibilityLabel(Text(verbatim: L10n.string("reader.back")))
                .accessibilityIdentifier("reader.back")
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                if case .ready = state {
                    Button(action: actions.search) {
                        Image(systemName: "magnifyingglass")
                    }
                    .accessibilityLabel(Text(verbatim: L10n.string("reader.search")))
                    .accessibilityIdentifier("reader.search")
                }
                Button(action: actions.showNotes) {
                    Image(systemName: "note.text")
                }
                .accessibilityLabel(Text(verbatim: L10n.string("reader.notes")))
                .accessibilityIdentifier("reader.notes")
                if case .ready = state, let fileURL {
                    ShareLink(item: fileURL) {
                        Image(systemName: "square.and.arrow.up")
                    }
                    .accessibilityLabel(Text(verbatim: L10n.string("reader.share")))
                    .accessibilityIdentifier("reader.share")
                }
            }
        }
    }

    private var cantOpen: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.largeTitle)
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .accessibilityHidden(true)
            Text(verbatim: L10n.string("reader.cantOpen"))
                .font(.headline)
                .foregroundStyle(HashiyaColors.onSurface)
                .multilineTextAlignment(.center)
            HashiyaGlassGroup(spacing: 12) {
                VStack(spacing: 12) {
                    Button(action: actions.replace) {
                        Text(verbatim: L10n.string("reader.replacePdf"))
                            .frame(maxWidth: .infinity)
                    }
                    .hashiyaProminentButton()
                    Button(role: .destructive, action: actions.removePdf) {
                        Text(verbatim: L10n.string("reader.removePdf"))
                            .frame(maxWidth: .infinity)
                    }
                    .hashiyaSecondaryButton()
                }
            }
            .controlSize(.large)
            // Never stretched across an iPad; untouched on iPhone.
            .maxWidthWhenWide()
        }
        .padding(32)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("reader.cantOpen")
    }

    @ViewBuilder
    private var banner: some View {
        if notesSaveFailed {
            HashiyaBanner(
                text: L10n.string("reader.notesSaveFailed"),
                actionTitle: L10n.string("reader.retry"),
                action: actions.retrySave
            )
        } else {
            switch message {
            case .notPDF: HashiyaBanner(text: L10n.string("reader.notPdf"))
            case .tooLarge: HashiyaBanner(text: L10n.string("reader.tooLarge"))
            case .attachFailed: HashiyaBanner(text: L10n.string("reader.attachFailed"))
            case nil: EmptyView()
            }
        }
    }
}

/// "3 of 14" over the pages while they scroll: a floating, non-interactive label.
private struct PagePill: View {
    let text: String

    var body: some View {
        Text(verbatim: text)
            .font(.footnote.monospacedDigit())
            .foregroundStyle(HashiyaColors.onSurface)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .hashiyaFloatingLabel()
            .accessibilityIdentifier("reader.pagePill")
    }
}
