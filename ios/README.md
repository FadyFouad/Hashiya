# Hashiya for iOS

The iOS app: SwiftUI, iOS 17 or later, English and Arabic with full right-to-left layouts. It matches the Android app's first milestone: OpenAlex search with filters, a preview sheet, an offline Library with swipe-to-remove and Undo, and Settings for the OpenAlex API key and the language. Adding papers by DOI or arXiv ID and from the Share sheet, then Library search and reading status, come next.

## Opening the project

The Xcode project is generated from `project.yml` with [XcodeGen](https://github.com/yonaskolb/XcodeGen) and is not committed.

```bash
brew install xcodegen
xcodegen generate --spec ios/project.yml
open ios/Hashiya.xcodeproj
```

Run `xcodegen generate` again after pulling changes to `project.yml` or adding files to the app or UI-test targets. Everything else lives in the local Swift package `ios/HashiyaKit` (targets `HashiyaModel`, `HashiyaNetwork`, `HashiyaDatabase`, `HashiyaData`, `HashiyaDesignSystem`, `FeatureSearch`, `FeatureLibrary`, `FeatureSettings`, and `HashiyaTesting` for tests); features see only `HashiyaData`, `HashiyaModel` and `HashiyaDesignSystem`, and the manifest enforces it.

## OpenAlex API key (optional)

```bash
cp ios/Config/Secrets.example.xcconfig ios/Config/Secrets.xcconfig
```

Put your key after `OPENALEX_API_KEY =` in `ios/Config/Secrets.xcconfig` (git-ignored). Without it the app sends requests without a key, at OpenAlex's lower free limits. Users can enter their own key in Settings; it is stored in the Keychain and never logged.

## Running on a device

The simulator needs no signing. A device does: the app's App Group and shared Keychain group entitlements need a development team. Because `xcodegen generate` rewrites the project, a team picked in Xcode's Signing & Capabilities is lost on the next generate, so set it in the git-ignored secrets file instead:

```bash
cp ios/Config/Secrets.example.xcconfig ios/Config/Secrets.xcconfig   # if you have not already
```

Uncomment the `DEVELOPMENT_TEAM` line in `ios/Config/Secrets.xcconfig` and put your team ID after the `=` (for example `DEVELOPMENT_TEAM = ABCDE12345`; find it under Membership details at developer.apple.com/account). The app and UI-test targets both read it through `ios/Config/Base.xcconfig`, so it survives every `xcodegen generate`. Bundle IDs and App Groups are unique across all teams: on a team that does not own `com.etatech.hashiya`, automatic signing cannot register it or `group.com.etatech.hashiya`, and you need your own identifiers locally (`ios/project.yml`, `ios/Hashiya/Hashiya.entitlements` and `HashiyaDatabase.appGroup`).

## Tests

```bash
# Everything CI runs: package unit and snapshot tests, and the UI tests
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2'

# Package tests only (faster), from the package directory
cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2'

# Every String Catalog key has an Arabic translation
python3 ios/scripts/check-translations.py
```

## Snapshot baselines

Snapshot tests render every screen in English and Arabic, light and dark. The baselines under `ios/HashiyaKit/Tests/*/__Snapshots__/` are recorded only on CI (`macos-15`, Xcode 16.4, iPhone 16 on iOS 18.5), which is the source of truth; images recorded on your Mac are for inspection only and are not committed. After an intended UI change:

```bash
bash ios/scripts/record-snapshots-on-ci.sh   # 15–25 minutes; needs `gh` logged in
git add -- ':(glob)ios/**/__Snapshots__/**'
```
