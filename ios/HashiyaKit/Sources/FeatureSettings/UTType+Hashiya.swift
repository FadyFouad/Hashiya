import UniformTypeIdentifiers

public extension UTType {
    /// A `.hashiya` library backup. The app's Info.plist declares it as an exported type.
    static let hashiyaBackup = UTType(exportedAs: "com.etatech.hashiya.backup", conformingTo: .zip)
}
