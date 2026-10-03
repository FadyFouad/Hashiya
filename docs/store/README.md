# Store screenshots

Ready to upload, in English (`en`) and Arabic (`ar`):

| Folder | Size | Store slot |
|---|---|---|
| `app-store/<lang>/` | 1320 × 2868 | App Store, iPhone 6.9" display |
| `play-store/<lang>/` | 1080 × 1920 | Google Play, phone screenshots |
| `play-store/feature-graphic.png` | 1024 × 500 | Google Play, feature graphic |
| `play-store-tablet/<lang>/` | 1920 × 1080 | Google Play, 7" and 10" tablet screenshots |

| # | English caption | Arabic caption | Screen |
|---|---|---|---|
| 1 | Find any paper | ابحث عن أي ورقة بحثية | Search results |
| 2 | Preview, then save | اطّلع ثم احفظ | Paper preview with its abstract and reading status |
| 3 | Track your reading | تابع قراءاتك | Library with status filters |
| 4 | Your library, offline | مكتبتك معك دائمًا | Library search |
| 5 | Read with your notes | اقرأ وملاحظاتك بجانبك | PDF reader with the notes sheet open |
| 6 | Organize into collections | نظّم أوراقك في مجموعات | iOS: a collection; Android: the collection picker |

Tablet (Play only, landscape):

| # | English caption | Arabic caption | Screen |
|---|---|---|---|
| 1 | Search and preview side by side | ابحث واطّلع جنبًا إلى جنب | Search results with the preview pane |
| 2 | Your notes beside the PDF | ملاحظاتك بجانب ملف PDF | PDF reader with the notes pane |
| 3 | Track your reading | تابع قراءاتك | Details, a centered column on the wide screen |

## Regenerating

`raw/` holds the unframed captures. After replacing any of them, reframe with:

```bash
python3 scripts/frame-store-screenshots.py
```

The same script also redraws the feature graphic. It needs `rsvg-convert` and a Pillow built with libraqm, which shapes the Arabic captions (Homebrew's Python has it). Captions and colours are at the top of the script; the colours are the design system's primary teal.

- **iOS** captures come from the iPhone 17 Pro Max simulator, with the status bar set to 9:41 and full signal and battery (`xcrun simctl status_bar`).
- **Android** captures are rendered with Robolectric at 411 × 891 dp and 420 dpi. They have no system bars, so the script draws a status bar on them.
- **Android tablet** captures are light English and Arabic Robolectric renders at 1280 × 800 dp, so they show the tests' sample papers and a stub PDF. Shots 1 and 2 are the `SearchTabletScreenshotTest` and `ReaderTabletScreenshotTest` renders. Shot 3 is Details on its own, full width, as opened from Search, with a downloaded PDF: the `PaperDetailsTabletScreenshotTest` renders put it beside a stand-in list, which doesn't belong in a store listing. Record them locally with `./gradlew recordRoborazziDebug --tests '*TabletScreenshotTest'`, copy the `-EnglishLight` / `-ArabicLight` images into `raw/play-store-tablet/<lang>/`, and delete the recorded baselines again: baselines are only committed from Linux CI.

Both apps showed the same six real papers from OpenAlex, saved in the library as To read, Reading and Read, and a live search for "large language models".

The iOS shots 5 and 6 are real captures too, on the same simulator and setup. Shot 5 is "Attention Is All You Need" (its arXiv PDF) open in the reader, with notes in Summary and Method and the notes sheet at medium height. Shot 6 is a collection ("Thesis — Chapter 2", in Arabic "الفصل الثاني") holding four of the six papers, picked from the Library's title menu. The Arabic iOS captures use the Arabic language with the `ar_SA` region, so numbers show in Arabic-Indic digits, as in shots 1–4.

The Play Store shots 5 and 6 still come from the snapshot-test baselines (`android/feature/*/src/test/screenshots/`, light theme), so they show test data and a stub PDF. Replace them with real captures when you next reshoot Android.
