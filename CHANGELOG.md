# Changelog

What changed in each version of Hashiya, for both the iOS and Android apps unless a line says otherwise. Versions follow `MARKETING_VERSION` (iOS) and `versionName` (Android); the build number is `CURRENT_PROJECT_VERSION` / `versionCode`.

## [Unreleased]

### Added
- **Android: tablets, foldables and resizable windows.** A navigation rail from 600 dp that stays on every screen; its menu button expands it to show the labels beside the icons, and collapses it again. From 840 dp, or at a book-posture hinge, the Library opens a paper beside the list, Search shows the preview beside the results, and the reader shows your notes beside the PDF. Details and Settings become a centered column. An open paper and the reader's zoom survive folding, rotating and resizing. (#28)
- **Android: keyboard and mouse.** Ctrl+F finds, Ctrl+N adds a paper, Ctrl+, opens Settings, and Esc goes back. Right-click a Library row or a Search result for its menu, and Ctrl+scroll zooms the reader. (#28)
- **Android: back up and restore your library.** Settings → Backup saves the library to a `.hashiya` file and Restore adds the papers from one, keeping what's already on the device; downloaded PDFs stay out of Google's cloud backup. (#33)
- **Android: crash reports.** Release builds send crash reports (Firebase Crashlytics), tied to a random identifier rather than your name or account; never your papers, notes or searches. On by default; turn it off in Settings → Privacy. (#35)
- **Android: usage statistics.** Release builds send usage statistics (Firebase Analytics), tied to a random identifier rather than your name or account: which features are used and a broad research area worked out on the device from search results — never search text, papers or notes. On by default; turn it off in Settings → Privacy → Share usage statistics.
- **Android: searching keeps working when OpenAlex's shared budget runs out.** Searches without a personal OpenAlex key share a daily allowance per device, then fall back to OpenAlex's keyless budget; when both are used up, Search says when it works again. Repeated searches are kept for a day, and Settings links to free personal keys.

### Changed
- **Android: Details groups Collections, PDF and DOI in one list.** The PDF row's main action is a button that follows its state: Download PDF, Read PDF or Attach PDF; Replace and Remove stay in its ⋮ menu. The DOI is a row that opens the paper's DOI page, and is hidden when there is none.

### Fixed
- **iOS: the app opens on a Mac.** On a Mac with Apple silicon, 0.3.0 closed at launch while macOS built its menu bar. Its commands now sit in a Go menu there: Add Paper ⌘N, Library ⌘1, Search ⌘2 and Settings ⌘,.
- **Android: the Save button on search results** no longer breaks into letters with the largest font size. (#28)

## [0.3.0] — build 3, 2026-10-05 (TestFlight)

### Added
- **iOS: iPad.** The app runs natively on iPad: full screen, Split View, Stage Manager and resizable windows. The tabs sit at the top. The Library and Search show a paper beside their list, with a button to hide the list. The reader shows your notes beside the PDF and hides the list for a wider page; leaving it brings the list back. An open paper survives resizing, and each window keeps its own tab and paper.
- **iOS: more than one iPad window.** "Open in New Window" in a paper's menu opens it in a window of its own, to read it beside another paper or the Library.
- **iOS: keyboard and pointer on iPad.** ⌘N adds a paper, ⌘1 and ⌘2 switch between the Library and Search, and ⌘, opens Settings; in the reader, ⌘F finds in the PDF. Long-press or right-click a Library row or a Search result for its menu.
- **iOS: searching keeps working when OpenAlex's shared budget runs out.** Searches without a personal OpenAlex key share a daily allowance per device, then fall back to OpenAlex's keyless budget; when both are used up, Search says when it works again. Repeated searches are kept for a day, and Settings links to free personal keys.
- **iOS: crash reports and usage statistics.** Release builds send crash reports (Firebase Crashlytics) and usage statistics (Firebase Analytics), tied to a random identifier rather than your name or account: which features are used and a broad research area worked out on the device from search results — never search text, papers or notes. Both are on by default and can be turned off in Settings → Privacy.
- **iOS: back up and restore your library.** Settings → Backup saves the library — papers, notes, collections and, if you choose, PDFs — to a `.hashiya` file, and Restore adds the papers from one, keeping what's already on the device. Downloaded PDFs are left out of the phone's iCloud backup, since they can be downloaded again. (#34)

### Changed
- **iOS: the same grouped Details.** Collections, PDF and DOI share one list as on Android; the PDF row's main action is a prominent button (Download PDF, Read PDF or Attach PDF), and the DOI is a row that opens its page.

### Fixed
- **Searching for a title with "?" or "*" works.** OpenAlex reads them as wildcards and refused the search, so pasting a title such as *ChatGPT for good? …* showed "Something went wrong". Search now leaves them out.
- **iOS: Add paper always puts the cursor in Search's field,** also when an earlier search left the field active.
- **A failed download now tries the paper's other open-access copies.** If the saved link fails as "not a PDF" or a server error, the app looks up the paper's other open-access copies on OpenAlex and tries them, arXiv first. Then it keeps the link that worked. Example: *Attention Is All You Need*, whose saved link now returns a web page. (#24)

## [0.2.0] — build 2, 2026-10-02 (TestFlight)

### Added
- **Notes for every paper** in Details: Summary, Research question, Method, Key findings, Limitations and My thoughts. They save as you type, and Library search finds words in them. Details also shows every author, the full abstract, the venue and a reading-status selector. (#16, #17)
- **Collections.** Make one from a paper's Details and pick it from the Library's title menu ("All papers ⌄"); search and the status chips work inside it. Rename and delete keep the papers. Swiping in a collection removes the paper from that collection only, with Undo. (#18, #20)
- **BibTeX export.** Export a collection or the whole library as a `.bib` file for Overleaf or LaTeX, or use Copy BibTeX on one paper. Cite keys follow Google Scholar's style (`vaswani2017attention`) and never change once given. (#18, #20)
- **PDFs.** Download a paper's open-access PDF, always over https, or attach one from Files. Then read it in the app, offline:
  - zoom, Search in PDF and a page indicator;
  - the reader reopens at your last page;
  - Share the file;
  - your notes in a sheet beside the page.

  Remove → Undo keeps the PDF. (#19, #21, #22)
- **Library** shows a PDF symbol on papers that have one.
- **Settings → Storage** shows the space downloaded and attached PDFs use, and can delete the downloaded ones. Attached ones stay. (#19, #22)

### Changed
- **iOS:** Liquid Glass design on iOS 26, with the same layout on iOS 17–18. (#14)
- **iOS:** the Library title is now a small, centred menu, so collections can be picked from it. (#20)

## [0.1.0] — build 1, 2026-09-29

The first version.

- **Search** millions of scholarly works from OpenAlex.
  - The search runs when you press Search.
  - Sort by relevance, most cited or newest, and filter by year or open access.
- **Add a paper by ID or from the browser:** paste a DOI, an arXiv ID or a link to go straight to it, or share a paper's page from the browser to add it.
- **Preview a paper before you save it:** abstract, authors, venue, year and citation count.
- **An offline library.**
  - Saved papers stay on the device.
  - Library search covers title, author, abstract and venue words; Arabic search ignores diacritics and letter variants.
  - Swipe to remove, with Undo.
- **Reading status:** To read, Reading and Read, with filter chips and counts.
- **Languages and themes:** English and Arabic, with right-to-left layouts, and light and dark themes.
- **Forced update:** the app shows "Update required" when a build is below the minimum set in `app-config.json`. (#15)
