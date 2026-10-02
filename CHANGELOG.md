# Changelog

What changed in each version of Hashiya, for both the iOS and Android apps unless a line says otherwise. Versions follow `MARKETING_VERSION` (iOS) and `versionName` (Android); the build number is `CURRENT_PROJECT_VERSION` / `versionCode`.

## [Unreleased]

### Fixed
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
