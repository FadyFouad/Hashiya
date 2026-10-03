/// Design-system strings that other targets show beside its components (the Details screen), so no string is
/// duplicated in another catalog.
@MainActor
public enum DesignSystemStrings {
    public static var abstract: String { L10n.string("designsystem.abstract") }
    public static var noAbstract: String { L10n.string("designsystem.noAbstract") }
    public static var openDOI: String { L10n.string("designsystem.openDOI") }
    public static var closePreview: String { L10n.string("designsystem.closePreview") }
    public static var removeFromLibrary: String { L10n.string("designsystem.removeFromLibrary") }
    /// "Done": the keyboard's Done above the note fields, and the Details collections checklist's Done.
    public static var doneEditing: String { L10n.string("notes.doneEditing") }

    /// The name sheet's error, which the Library and Details pass back on a clash.
    public static var collectionNameTaken: String { L10n.string("collection.nameTaken") }

    /// The open-access badge: "Open access", or "Open access · PDF available" when there is a PDF.
    public static func openAccess(hasPDF: Bool) -> String {
        L10n.string(hasPDF ? "designsystem.openAccessPDF" : "designsystem.openAccess")
    }
}
