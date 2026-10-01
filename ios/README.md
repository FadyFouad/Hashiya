# Hashiya for iOS

The iOS app: SwiftUI, iOS 17 or later, English and Arabic with full right-to-left layouts. It behaves like the Android app — OpenAlex search with filters, adding a paper by DOI, arXiv ID or link (in Search, or with the Library's **Add paper** button), a preview sheet, an offline Library you can search by title, author, abstract or venue (Arabic search ignores tashkeel and letter variants) and track as To read, Reading or Read, and Settings. The Share Extension `HashiyaShare` looks up a page shared from Safari or any app and saves the paper from the share sheet. On iOS 26 and later the chips, reading-status badges, banners, the preview's buttons and the Add paper button use Liquid Glass; iOS 17 and 18 keep the teal styling.

## Opening the project

The Xcode project is generated from `project.yml` with [XcodeGen](https://github.com/yonaskolb/XcodeGen) and is not committed.

```bash
brew install xcodegen
xcodegen generate --spec ios/project.yml
open ios/Hashiya.xcodeproj
```

Run `xcodegen generate` again after pulling changes to `project.yml` or adding files to the app, Share Extension (`ios/HashiyaShare`), shared (`ios/Shared`) or UI-test targets. Everything else lives in the local Swift package `ios/HashiyaKit` (targets `HashiyaModel`, `HashiyaNetwork`, `HashiyaDatabase`, `HashiyaBibTeX`, `HashiyaData`, `HashiyaDesignSystem`, `FeatureSearch`, `FeatureLibrary`, `FeaturePaperDetails`, `FeatureSettings`, and `HashiyaTesting` for tests); features see only `HashiyaData`, `HashiyaModel` and `HashiyaDesignSystem`, and the manifest enforces it. BibTeX is generated on the device by `HashiyaBibTeX`, a port of Android's `core/bibtex` whose tests mirror Android's case for case, so both apps export identical entries with the same cite-key rules. The app and the Share Extension share the library database (`group.com.etatech.hashiya`) and the user's key (Keychain group `com.etatech.hashiya.shared`); the app refreshes its Library whenever it comes to the foreground. The database is migrated in place with GRDB migrations (`v1`, then `v2` for the reading status and the full-text index, then `v3` for the notes, then `v4` for collections and citation details); there is no destructive fallback.

## OpenAlex API key (optional)

```bash
cp ios/Config/Secrets.example.xcconfig ios/Config/Secrets.xcconfig
```

Put your key after `OPENALEX_API_KEY =` in `ios/Config/Secrets.xcconfig` (git-ignored). Without it the app sends requests without a key, at OpenAlex's lower free limits. Users can enter their own key in Settings; it is stored in the Keychain and never logged.

## Running on a device

The simulator needs no signing. A device does: the App Group and shared Keychain group entitlements of the app and the Share Extension need a development team. Because `xcodegen generate` rewrites the project, a team picked in Xcode's Signing & Capabilities is lost on the next generate, so set it in the git-ignored secrets file instead:

```bash
cp ios/Config/Secrets.example.xcconfig ios/Config/Secrets.xcconfig   # if you have not already
```

Uncomment the `DEVELOPMENT_TEAM` line in `ios/Config/Secrets.xcconfig` and put your team ID after the `=` (for example `DEVELOPMENT_TEAM = ABCDE12345`; find it under Membership details at developer.apple.com/account). The app, Share Extension and UI-test targets all read it through `ios/Config/Base.xcconfig`, so it survives every `xcodegen generate`. Bundle IDs and App Groups are unique across all teams: on a team that does not own `com.etatech.hashiya`, automatic signing cannot register it, `com.etatech.hashiya.share` or `group.com.etatech.hashiya`, and you need your own identifiers locally (`ios/project.yml`, `ios/Hashiya/Hashiya.entitlements`, `ios/HashiyaShare/HashiyaShare.entitlements`, `HashiyaDatabase.appGroup` and `UITestingFlags`).

## Tests

```bash
# Everything CI runs: package unit and snapshot tests, and the UI tests
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.4'

# The same on iOS 18 (the pre-Liquid Glass look)
xcodebuild test -project ios/Hashiya.xcodeproj -scheme Hashiya -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=18.2'

# Package tests only (faster), from the package directory
cd ios/HashiyaKit && xcodebuild test -scheme HashiyaKit-Package -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.4'

# Every String Catalog key has an Arabic translation
python3 ios/scripts/check-translations.py
```

Until CI records the snapshot baselines, these test commands record any missing images locally and fail (recording
always fails, by design); the baselines come from CI's iOS 26.2 and 18.5 simulators, so a local iPhone 16 Pro iOS
18.2 run may render slightly differently from them.

The UI tests include sharing a link through a real share sheet: launched with `-ui-testing -ui-testing-share <url>`, a Debug app presents the share sheet for the URL and tells a Debug Share Extension (through the App Group) to use a stub lookup and the UI tests' own library file. Release builds contain none of these hooks.

## Snapshot baselines

Snapshot tests render every screen in English and Arabic, light and dark, on iOS 26 (Liquid Glass) and on iOS 18 (the teal styling iOS 17 and 18 keep). They live in `ios/HashiyaSnapshotTests`, a test bundle hosted by the app, because only a window render captures Liquid Glass. Their baselines under `ios/HashiyaSnapshotTests/__Snapshots__/iOS26/` and `…/iOS18/` are recorded only on CI (`macos-15`, Xcode 26.3, iPhone 16 on iOS 26.2 and on iOS 18.5), which is the source of truth; images recorded on your Mac are for inspection only and are not committed. Building needs Xcode 26 or later. After an intended UI change:

```bash
bash ios/scripts/record-snapshots-on-ci.sh   # 15–25 minutes; needs `gh` logged in
git add -- ':(glob)ios/**/__Snapshots__/**'
```
