# Importing from BibTeX and RIS — iOS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The iOS side of `docs/superpowers/specs/2026-11-04-import-bibtex-ris-design.md` §12: the same parsers and matching as Android, a preview, a resumable import into the library and optionally a new collection, and Import from the Library and from "Open in Hashiya".

**Architecture:** A pure `HashiyaImport` package target ports `core/importer` and passes the same fixtures with the same expected JSON. GRDB migration `v6` adds `import_jobs` and `import_job_papers` (cite keys exist since `v4`). `HashiyaNetwork` gains the quota's import share and `OpenAlexLookupService.importWorks(filter:perPage:)`. `HashiyaDiagnostics` gains the `import` event and screen. `HashiyaData` gains `DOIMatcher` and `LiveImportRepository`, an actor over `PaperStore` that keeps the job in the database. `FeatureImport` holds the screen. The app adds a Library toolbar button, the sheet, `onOpenURL` routing beside the backup's, the UTType and document-type declarations and a `beginBackgroundTask` wrapper.

**Tech Stack:** Swift 6, SwiftUI, Observation, GRDB 7, Swift Testing, swift-snapshot-testing, XcodeGen.

**Spec:** `docs/superpowers/specs/2026-11-04-import-bibtex-ris-design.md` (second version, on `main` at `c4f0085`).

## Global Constraints

- Commits authored `Fady <fady.fouad.a@gmail.com>`, no AI credits anywhere.
- Features see only `HashiyaData`, `HashiyaModel`, `HashiyaDesignSystem` and `HashiyaDiagnostics`; `HashiyaImport` is used by `HashiyaData`.
- `ios/HashiyaKit/Tests/HashiyaImportTests/Fixtures` is a byte-for-byte copy of `testdata/import`; Android's `IosFixtureCopyTest` fails when they differ.
- Parsing rules and expected results are the Android ones. A parser difference is fixed in both places, never by editing one platform's expected JSON.
- Migrations `v1`–`v5` are untouched; `v6` adds tables only.
- OpenAlex: 50 DOIs per import call through the quota; at most half the day's shared calls; `dailyLimit` switches to free single lookups.
- Analytics: closed values only, one `import` event per job, no `paper_saved` for imported papers.
- Every String Catalog key has Arabic (`python3 ios/scripts/check-translations.py`), plural keys all six Arabic forms.
- Snapshot baselines are recorded on CI only (`bash ios/scripts/record-snapshots-on-ci.sh`).
- The Library's existing snapshots must not change: the Import button appears only when the app passes `onImport`.

## Tasks

### Task 1: `HashiyaImport` and its fixture tests
- [ ] Copy `testdata/import` to `Tests/HashiyaImportTests/Fixtures`; add `IosFixtureCopyTest` to `core/importer`.
- [ ] Carry over the first version's `HashiyaImport` sources and tests as they are (`FixtureTests`, `LatexDecoderTests`, `BibTeXParserTests`, `RISParserTests`, `ImportParserTests`, `ImportPlannerTests`).
- [ ] Add the target and test target to `Package.swift` and the scheme in `project.yml`. Commit `feat(ios): read BibTeX and RIS exports exactly as Android does`.

### Task 2: Quota share, analytics event and schema `v6`
- [ ] Tests first: `OpenAlexQuotaTests` (import route below/above half the cap), `OpenAlexHTTPTests` (import calls use the import route), `AnalyticsEventTests` (`import` parameters), `MigrationTests` (`v1`–`v6`; `v6` keeps every paper and adds empty tables), `PaperStoreImportTests` (batch, job links, collection links and progress in one transaction).
- [ ] `OpenAlexQuota.importRoute()`, `OpenAlexLookupService.importWorks`; `AnalyticsEvent.importFile`, `Screen.import`; migration `v6`, records, `PaperStore` import methods.
- [ ] Commit `feat(ios): store import jobs and let imports use half the day's shared OpenAlex calls`.

### Task 3: Matching and the import repository
- [ ] Tests first: `DOIMatcherTests` (batches of 50, odd DOIs alone, `dailyLimit` → single lookups, connectivity stops, other failures skip a batch), `LiveImportRepositoryTests` (counts, import with cite keys, local papers and the collection, too large, unsupported, nothing to import, resume without duplicates, cancel, dismiss, analytics).
- [ ] `ImportJob` in `HashiyaModel`; `DOIMatcher`, `ImportRepository`, `LiveImportRepository` in `HashiyaData`; `FakeImportRepository` in `HashiyaTesting`; `LiveDependencies.importRepository`.
- [ ] Commit `feat(ios): import a file in the background and resume it later`.

### Task 4: The Import screen
- [ ] Tests first: `ImportViewModelTests`.
- [ ] `FeatureImport` with the collection option, String Catalog in English and Arabic. Commit `feat(ios): preview an import and follow it on the Import screen`.

### Task 5: Entry points and snapshots
- [ ] Library toolbar Import button (only with `onImport`); `RootView` sheet and `onOpenURL` (`.bib`/`.ris` → Import, `.hashiya` → Restore); resume at launch and on activation; `Info.plist` types beside the backup's; `project.yml`.
- [ ] `ImportSnapshotTests` in both languages and themes. Commit `feat(ios): import .bib and .ris files from the Library and with Open in Hashiya`.

### Task 6: Docs
- [ ] `CHANGELOG.md` under `[Unreleased]`, `ios/README.md`. Commit `docs: describe importing from BibTeX and RIS on iOS`.
