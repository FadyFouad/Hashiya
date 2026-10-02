# Store screenshots

Ready to upload, in English (`en`) and Arabic (`ar`):

| Folder | Size | Store slot |
|---|---|---|
| `app-store/<lang>/` | 1320 × 2868 | App Store, iPhone 6.9" display |
| `play-store/<lang>/` | 1080 × 1920 | Google Play, phone screenshots |
| `play-store/feature-graphic.png` | 1024 × 500 | Google Play, feature graphic |

| # | English caption | Arabic caption | Screen |
|---|---|---|---|
| 1 | Find any paper | ابحث عن أي ورقة بحثية | Search results |
| 2 | Preview, then save | اطّلع ثم احفظ | Paper preview with its abstract and reading status |
| 3 | Track your reading | تابع قراءاتك | Library with status filters |
| 4 | Your library, offline | مكتبتك معك دائمًا | Library search |
| 5 | Read with your notes | اقرأ وملاحظاتك بجانبك | PDF reader with the notes sheet open |
| 6 | Organize into collections | نظّم أوراقك في مجموعات | iOS: a collection; Android: the collection picker |

## Regenerating

`raw/` holds the unframed captures. After replacing any of them, reframe with:

```bash
python3 scripts/frame-store-screenshots.py
```

The same script also redraws the feature graphic. It needs `rsvg-convert` and a Pillow built with libraqm, which shapes the Arabic captions (Homebrew's Python has it). Captions and colours are at the top of the script; the colours are the design system's primary teal.

- **iOS** captures come from the iPhone 17 Pro Max simulator, with the status bar set to 9:41 and full signal and battery (`xcrun simctl status_bar`).
- **Android** captures are rendered with Robolectric at 411 × 891 dp and 420 dpi. They have no system bars, so the script draws a status bar on them.

Both apps showed the same six real papers from OpenAlex, saved in the library as To read, Reading and Read, and a live search for "large language models".

Shots 5 and 6 come from the snapshot-test baselines instead (`ios/HashiyaSnapshotTests/__Snapshots__/iOS26/` and `android/feature/*/src/test/screenshots/`, light theme), so they show test data and a stub PDF. The iOS ones are 1170 × 2532 with a drawn 9:41 status bar. Replace them with real captures when you next reshoot.
