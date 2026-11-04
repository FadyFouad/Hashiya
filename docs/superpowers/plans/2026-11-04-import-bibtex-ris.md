# Importing from BibTeX and RIS — Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import a `.bib` or `.ris` file into the library: parse it, match DOIs to OpenAlex in batches through the quota system, preview the counts, then save matched and local papers in a resumable background job, optionally into a new collection named after the file.

**Architecture:** A new plain-JVM module `:core:importer` parses both formats, plans the import against the library and builds local papers. `:core:network` gains an import share in `OpenAlexQuota` and `findWorksForImport`. `:core:analytics` gains the `import` event. Room version 6 adds `import_jobs` and `import_job_papers`. `:core:data` gains `OpenAlexDoiMatcher`, `ImportRunner`, `DefaultImportRepository` over an `ImportStore` (Room in the app, in-memory in tests) and a WorkManager `ImportWorker`. A new `:feature:importer` shows the screen; the Library gets an Import button and `MainActivity` receives `.bib`/`.ris` files.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room (KSP), Hilt, WorkManager 2.12.0, OkHttp/Retrofit, coroutines/Flow, JUnit 4, Robolectric, Turbine, Roborazzi, kotlinx.serialization (JSON in tests).

**Spec:** `docs/superpowers/specs/2026-11-04-import-bibtex-ris-design.md` (second version, on `main` at `c4f0085`).

## Global Constraints

- Commits are authored `Fady <fady.fouad.a@gmail.com>`. No AI credits anywhere.
- All Android paths are under `android/`; the shared fixtures stay in `testdata/import` at the repository root.
- `:core:importer` depends only on `:core:model`, has no Android dependency and no I/O except in tests.
- Features never depend on `:core:database` or `:core:network`.
- Schema version 6: new tables only. No destructive fallback.
- OpenAlex: 50 DOIs per batch as import calls through the quota; at most half the day's shared calls; `DailyLimit` switches to free single lookups at ≤ 10 per second.
- Analytics: closed values only; one `import` event per job; no `paper_saved` for imported papers.
- Every user-facing string in English and Arabic.
- ktlint `android_studio`, max line length 140. TDD, hand-written fakes; screenshot baselines recorded on CI Linux only.
- Existing Library screenshots must not change: `LibraryActions.onImport` is null by default.

## Tasks

### Task 1: `:core:importer` with the fixture tests
- [ ] Carry the module over as written for the first version (parsers, planner, cite keys, local papers, all tests), under `android/core/importer`. `ExpectedJson.kt` finds `testdata/import` by walking up from the working directory.
- [ ] Add `IosFixtureCopyTest` once the iOS copy exists (iOS plan, Task 1).
- [ ] Include the module in `settings.gradle.kts`; add `:core:importer:test` to CI. Commit `feat(android): read BibTeX and RIS exports from Zotero, Mendeley, EndNote and Google Scholar`.

### Task 2: Model, analytics event and the quota's import share
- [ ] Tests first: `LocalPaperTest` (`core:model`); `AnalyticsEventTest` for `Import` (names, buckets, matched share); `OpenAlexQuotaTest` (import route below/above half the cap, searches unaffected); `QuotaInterceptorTest` (a tagged request uses the import route).
- [ ] `LocalPaper.kt`, `ImportJob.kt`; `AnalyticsEvent.Import`, `ImportFileFormat`, `ImportResult`, `MatchedShare`, `Screen.Import`; `OpenAlexQuota.importRoute()`, `MeteredUse` tag, `OpenAlexApi.findWorksForImport`, `OpenAlexLookupDataSource.findWorksForImport` (default: `findWorks`).
- [ ] Commit `feat(android): let imports use half the day's shared OpenAlex calls and count them`.

### Task 3: Schema 6
- [ ] Tests first: `MigrationTest` 5 → 6 (v5 rows with PDFs unchanged; new tables empty; Room opens at 6); `ImportJobDaoTest`.
- [ ] Entities, `ImportJobDao` (incl. `replaceIf`, `libraryKeys`), `PaperDao.insertImportBatch`, `MIGRATION_5_6`, `6.json` (Room identity hash), DI.
- [ ] Commit `feat(android): store import jobs and save imported papers in one transaction`.

### Task 4: Matching, the runner and the job
- [ ] Tests first: `OpenAlexDoiMatcherTest`, `ImportRunnerTest` (incl. the analytics event), `DefaultImportRepositoryTest`, `ImportFileStoreTest`, `RoomImportStoreTest`, `WorkManagerImportSchedulerTest`, `RoomCitationRepositoryTest` (local papers), `FakeImportRepositoryTest`.
- [ ] Implement; add WorkManager 2.12.0.
- [ ] Commit `feat(android): import a file in the background and resume after the app is killed`.

### Task 5: The Import screen
- [ ] Tests first: `ImportUiStateTest`, `ReadAtMostTest`, `ImportViewModelTest`, `ImportContentTest`, `ImportScreenshotTest` (seven states × four variants).
- [ ] `:feature:importer`. Commit `feat(android): preview an import and follow it on the Import screen`.

### Task 6: Entry points
- [ ] Tests first: `LibraryContentTest` (button shown only with `onImport`), `ShareIntentFilterTest`, `ImportIntentTest` (and that a `.bib` VIEW is not taken for a backup), `ScreenKeyTest` for `import`, `HashiyaAppNavigationTest`.
- [ ] Commit `feat(android): open .bib and .ris files from the Library and from other apps`.

### Task 7: Docs
- [ ] `CHANGELOG.md` under `[Unreleased]`, `README.md`. Commit `docs: describe importing from BibTeX and RIS`.
