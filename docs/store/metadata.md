# Store listing and questionnaires

Everything to paste into App Store Connect and the Play Console. The App Store fields are for iOS 0.3.0 (build 3); the Google Play fields are still for 0.2.0. Character limits
are in brackets; `scripts/check-store-metadata.py` checks every field against them.

## Shared

| Field                          | Value                                                 |
|--------------------------------|-------------------------------------------------------|
| Price                          | Free, no in-app purchases, no ads                     |
| Privacy policy URL             | https://fadyfouad.github.io/Hashiya-Privacy-Policy/ |
| Support URL / contact          | https://fadyfouad.github.io/Hashiya-Privacy-Policy/ (email `fady.fouad.a@gmail.com`) |
| Primary category               | Education (App Store and Play)                        |
| Secondary category (App Store) | Reference                                             |
| Copyright (App Store)          | 2026 Fady Fouad                                       |

## App Store

<!-- lang:en store:app-store -->
**Name** [30]

```
Hashiya: Research Papers
```

**Subtitle** [30]

```
Find, save and track papers
```

**Promotional text** [170]

```
Now on iPad, with Split View and more than one window. Find papers, read PDFs beside your notes, group them into collections and export BibTeX. In English and Arabic.
```

**Keywords** [100]

```
research,papers,thesis,phd,masters,scholar,citations,doi,arxiv,bibtex,pdf,notes,literature,academic
```

**Description** [4000]

```
Hashiya helps master's and PhD students keep their research in one place, from finding a paper to tracking what they've read. The name is the Arabic ḥāshiya: the commentary scholars wrote in the margins of books.

FIND ANY PAPER
• Search millions of scholarly works from the OpenAlex catalog.
• Sort by relevance, most cited or newest, and filter by year or open access.
• Paste a DOI, an arXiv ID or a link to go straight to a paper.
• Share a paper's page from Safari to add it without leaving the browser.

PREVIEW, THEN SAVE
• See the abstract, authors, venue, year and citation count before you save.
• Open the paper's DOI page or its open-access PDF when one is available.

READ AND TAKE NOTES
• Download a paper's open-access PDF or attach one from Files, then read it offline with search, zoom and your notes beside the page.
• Notes for every paper: summary, research question, method, key findings, limitations and your own thoughts. They save as you type.

COLLECTIONS AND BIBTEX
• Group papers for a chapter, a course or a project.
• Export a collection or your whole library as a .bib file for Overleaf or LaTeX, or copy one paper's entry.

YOUR LIBRARY, OFFLINE
• Saved papers stay on your device and work without a connection.
• Search your library by words from a title, author, abstract, venue or your notes. Arabic search ignores diacritics and letter variants.
• Swipe to remove a paper, with Undo.
• Back up your library to a file and restore it later.

TRACK YOUR READING
• Mark each paper To read, Reading or Read.
• Filter your library by status, with a count for each.

MADE FOR IPAD TOO
• Split View, Stage Manager and more than one window: read a paper beside another, or beside your library.
• Keyboard shortcuts and a pointer menu for papers.

MADE FOR ARABIC AND ENGLISH
• Full Arabic and English interfaces with right-to-left layouts.
• Light and dark themes.

PRIVATE BY DESIGN
• No account, no ads and no tracking. Your library, notes and searches stay on your device.
• Crash reports and usage statistics help fix problems and decide what to improve. They are tied to a random identifier, not your name or account, and you can turn them off in Settings → Privacy.

Paper data comes from OpenAlex (openalex.org), a free and open catalog of scholarly works.
```

**What's New** [4000]

```
New in 0.3.0:

• iPad: full screen, Split View, Stage Manager and resizable windows. The Library and Search show a paper beside the list, and the reader shows your notes beside the PDF.
• More than one window: "Open in New Window" in a paper's menu opens it in a window of its own, to read it beside another paper or your library.
• Keyboard and pointer: ⌘N adds a paper, ⌘1 and ⌘2 switch between the Library and Search, ⌘, opens Settings and ⌘F finds in a PDF. Right-click a paper for its menu.
• Back up and restore: Settings → Backup saves your library (papers, notes, collections and, if you choose, PDFs) to a file. Restoring adds its papers and keeps the ones already on the device.
• Search keeps working on busy days. When the app's shared OpenAlex budget runs out, searches use OpenAlex's public budget, and if that runs out too, Search tells you when it works again. Repeated searches are kept for a day. For more searches, add a free personal OpenAlex key in Settings.
• Details puts Collections, PDF and DOI in one list. One button downloads, reads or attaches the PDF.
• Crash reports and usage statistics help fix problems and decide what to improve. They are tied to a random identifier, not your name or account, and never include your searches, papers or notes. Turn them off in Settings → Privacy.
• Fixes: you can search for titles that contain "?" or "*", and a failed PDF download now tries the paper's other open-access copies.

New in 0.2.0:

• Notes for every paper: summary, research question, method, key findings, limitations and your own thoughts. They save as you type, and your library search finds them.
• Read PDFs in the app: download a paper's open-access PDF or attach one from Files, then read it offline with search, zoom and your notes beside the page. The reader reopens where you left off.
• Collections: group papers for a chapter, a course or a project, and filter your library by collection.
• BibTeX export: export a collection or your whole library as a .bib file for Overleaf or LaTeX, or copy one paper's entry. Cite keys stay the same from one export to the next.
• Storage in Settings shows how much space PDFs use, and can delete the downloaded ones.
```

<!-- /lang -->

<!-- lang:ar store:app-store -->
**الاسم** [30]

```
حاشية: الأوراق البحثية
```

**العنوان الفرعي** [30]

```
ابحث واحفظ وتابع أبحاثك
```

**النص الترويجي** [170]

```
الآن على iPad مع Split View وأكثر من نافذة. ابحث عن الأوراق، واقرأ ملفات PDF بجانب ملاحظاتك، ونظّمها في مجموعات، وصدّرها بصيغة BibTeX، بالعربية والإنجليزية.
```

**الكلمات المفتاحية** [100]

```
بحث,أبحاث,رسالة,ماجستير,دكتوراه,أوراق,علمية,مراجع,استشهادات,دراسات,مكتبة,أكاديمي,ملاحظات,pdf,bibtex
```

**الوصف** [4000]

```
تساعد حاشية طلاب الماجستير والدكتوراه على جمع أبحاثهم في مكان واحد، من العثور على الورقة البحثية إلى متابعة ما قرأوه. والاسم مأخوذ من «الحاشية»: الشروح التي كتبها العلماء في هوامش الكتب.

ابحث عن أي ورقة بحثية
• ابحث في ملايين الأعمال العلمية من فهرس OpenAlex.
• رتّب النتائج حسب الصلة أو الأكثر استشهادًا أو الأحدث، وصفِّها حسب السنة أو الوصول المفتوح.
• الصق DOI أو معرّف arXiv أو رابطًا للوصول إلى الورقة مباشرة.
• شارك صفحة الورقة من Safari لإضافتها دون مغادرة المتصفح.

اطّلع ثم احفظ
• اطّلع على الملخص والمؤلفين وجهة النشر والسنة وعدد الاستشهادات قبل الحفظ.
• افتح صفحة DOI للورقة أو ملف PDF المفتوح عند توفّره.

اقرأ ودوّن ملاحظاتك
• نزّل ملف PDF المفتوح للورقة أو أرفق ملفًا من «الملفات»، ثم اقرأه دون اتصال مع البحث والتكبير وملاحظاتك بجانب الصفحة.
• ملاحظات لكل ورقة: الخلاصة، وسؤال البحث، والمنهجية، وأهم النتائج، والقيود، وأفكارك. تُحفظ أثناء الكتابة.

المجموعات وBibTeX
• اجمع الأوراق لفصل أو مقرر أو مشروع.
• صدّر مجموعة أو مكتبتك كاملة في ملف ‎.bib لاستخدامه في Overleaf أو LaTeX، أو انسخ مدخل ورقة واحدة.

مكتبتك معك دائمًا
• تبقى الأوراق المحفوظة على جهازك وتعمل دون اتصال.
• ابحث في مكتبتك بكلمات من العنوان أو المؤلفين أو الملخص أو جهة النشر أو ملاحظاتك، ويتجاهل البحث العربي التشكيل واختلاف أشكال الحروف.
• اسحب لإزالة ورقة، مع إمكانية التراجع.
• انسخ مكتبتك احتياطيًا في ملف واستعِدها لاحقًا.

تابع قراءاتك
• صنّف كل ورقة: للقراءة، قيد القراءة، مقروءة.
• صفِّ مكتبتك حسب الحالة، مع عدد الأوراق في كل منها.

على iPad أيضًا
• Split View وStage Manager وأكثر من نافذة: اقرأ ورقة بجانب أخرى أو بجانب مكتبتك.
• اختصارات لوحة المفاتيح وقائمة للأوراق بالمؤشر.

بالعربية والإنجليزية
• واجهة كاملة بالعربية والإنجليزية مع تخطيط من اليمين إلى اليسار.
• مظهر فاتح وداكن.

خصوصيتك أولًا
• بلا حساب ولا إعلانات ولا تتبّع، وتبقى مكتبتك وملاحظاتك وعمليات بحثك على جهازك.
• تساعد تقارير الأعطال وإحصاءات الاستخدام على إصلاح المشكلات وتحديد ما يجب تحسينه. وهي مرتبطة بمعرّف عشوائي، لا باسمك أو حسابك، ويمكنك إيقافها من الإعدادات ← الخصوصية.

بيانات الأوراق من OpenAlex ‏(openalex.org)، وهو فهرس مجاني ومفتوح للأعمال العلمية.
```

**ما الجديد** [4000]

```
الجديد في 0.3.0:

• iPad: ملء الشاشة وSplit View وStage Manager ونوافذ يتغيّر حجمها. تعرض المكتبة والبحث الورقة بجانب القائمة، ويعرض القارئ ملاحظاتك بجانب ملف PDF.
• أكثر من نافذة: يفتح «فتح في نافذة جديدة» من قائمة الورقة الورقةَ في نافذة مستقلة، لتقرأها بجانب ورقة أخرى أو بجانب مكتبتك.
• لوحة المفاتيح والمؤشر: ‏⌘N يضيف ورقة، و⌘1 و⌘2 للتنقل بين المكتبة والبحث، و⌘, يفتح الإعدادات، و⌘F للبحث في ملف PDF. انقر بزر الفأرة الأيمن على ورقة لفتح قائمتها.
• النسخ الاحتياطي والاستعادة: يحفظ «النسخ الاحتياطي» في الإعدادات مكتبتك (الأوراق والملاحظات والمجموعات، وملفات PDF إن أردت) في ملف. وتضيف الاستعادة أوراقه مع الإبقاء على الأوراق الموجودة على الجهاز.
• يستمر البحث في الأيام المزدحمة. عندما ينفد رصيد OpenAlex المشترك للتطبيق، يستخدم البحث الرصيد العام لـOpenAlex، وإن نفد هو أيضًا يخبرك البحث متى يعود للعمل. وتُحفظ عمليات البحث المتكررة ليوم واحد. ولمزيد من عمليات البحث، أضف مفتاح OpenAlex شخصيًا مجانيًا في الإعدادات.
• تجمع صفحة التفاصيل المجموعات وملف PDF وDOI في قائمة واحدة، وزر واحد ينزّل ملف PDF أو يقرؤه أو يرفقه.
• تساعد تقارير الأعطال وإحصاءات الاستخدام على إصلاح المشكلات وتحديد ما يجب تحسينه. وهي مرتبطة بمعرّف عشوائي، لا باسمك أو حسابك، ولا تتضمّن أبدًا عمليات بحثك أو أوراقك أو ملاحظاتك. يمكنك إيقافها من الإعدادات ← الخصوصية.
• إصلاحات: يمكنك البحث عن عناوين تحتوي على «?» أو «*»، وإذا فشل تنزيل ملف PDF يجرّب التطبيق النسخ المفتوحة الأخرى للورقة.

الجديد في 0.2.0:

• ملاحظات لكل ورقة: الخلاصة، وسؤال البحث، والمنهجية، وأهم النتائج، والقيود، وأفكارك. تُحفظ أثناء الكتابة، ويجدها البحث في مكتبتك.
• اقرأ ملفات PDF داخل التطبيق: نزّل ملف PDF المفتوح للورقة أو أرفق ملفًا من «الملفات»، ثم اقرأه دون اتصال مع البحث والتكبير وملاحظاتك بجانب الصفحة. ويعود القارئ إلى حيث توقفت.
• المجموعات: اجمع الأوراق لفصل أو مقرر أو مشروع، وصفِّ مكتبتك حسب المجموعة.
• تصدير BibTeX: صدّر مجموعة أو مكتبتك كاملة في ملف ‎.bib لاستخدامه في Overleaf أو LaTeX، أو انسخ مدخل ورقة واحدة. وتبقى مفاتيح الاستشهاد كما هي بين مرة وأخرى.
• يعرض قسم «التخزين» في الإعدادات المساحة التي تشغلها ملفات PDF، ويتيح حذف الملفات المنزّلة.
```

<!-- /lang -->

## Google Play

<!-- lang:en store:play-store -->
**App name** [30]

```
Hashiya: Research Papers
```

**Short description** [80]

```
Find, save and track research papers, with an offline library. English & Arabic.
```

**Full description** [4000]

```
Hashiya helps master's and PhD students keep their research in one place, from finding a paper to tracking what they've read. The name is the Arabic ḥāshiya: the commentary scholars wrote in the margins of books.

FIND ANY PAPER
• Search millions of scholarly works from the OpenAlex catalog.
• Sort by relevance, most cited or newest, and filter by year or open access.
• Paste a DOI, an arXiv ID or a link to go straight to a paper.
• Share a paper's page from your browser to add it in one step.

PREVIEW, THEN SAVE
• See the abstract, authors, venue, year and citation count before you save.
• Open the paper's DOI page or its open-access PDF when one is available.

YOUR LIBRARY, OFFLINE
• Saved papers stay on your device and work without a connection.
• Search your library by words from a title, author, abstract or venue. Arabic search ignores diacritics and letter variants.
• Swipe to remove a paper, with Undo.

TRACK YOUR READING
• Mark each paper To read, Reading or Read.
• Filter your library by status, with a count for each.

MADE FOR ARABIC AND ENGLISH
• Full Arabic and English interfaces with right-to-left layouts, and a per-app language setting.
• Light and dark themes.

MADE FOR BIG SCREENS
• On tablets, foldables and Chromebooks, a paper opens beside your library or search results, and your notes sit beside the PDF.
• Keyboard shortcuts and right-click menus when you use a keyboard and mouse.

PRIVATE BY DESIGN
• No account, no tracking and no ads. Your library never leaves your device.

Paper data comes from OpenAlex (openalex.org), a free and open catalog of scholarly works.
```

**Release notes** [500]

```
New: notes for every paper, an in-app PDF reader with your notes beside the page, collections, and BibTeX export for Overleaf. Settings now shows how much space PDFs use.
```

<!-- /lang -->

<!-- lang:ar store:play-store -->
**اسم التطبيق** [30]

```
حاشية: الأوراق البحثية
```

**الوصف المختصر** [80]

```
ابحث عن الأوراق البحثية واحفظها وتابع قراءتها، مع مكتبة تعمل دون اتصال.
```

**الوصف الكامل** [4000]

```
تساعد حاشية طلاب الماجستير والدكتوراه على جمع أبحاثهم في مكان واحد، من العثور على الورقة البحثية إلى متابعة ما قرأوه. والاسم مأخوذ من «الحاشية»: الشروح التي كتبها العلماء في هوامش الكتب.

ابحث عن أي ورقة بحثية
• ابحث في ملايين الأعمال العلمية من فهرس OpenAlex.
• رتّب النتائج حسب الصلة أو الأكثر استشهادًا أو الأحدث، وصفِّها حسب السنة أو الوصول المفتوح.
• الصق DOI أو معرّف arXiv أو رابطًا للوصول إلى الورقة مباشرة.
• شارك صفحة الورقة من المتصفح لإضافتها بخطوة واحدة.

اطّلع ثم احفظ
• اطّلع على الملخص والمؤلفين وجهة النشر والسنة وعدد الاستشهادات قبل الحفظ.
• افتح صفحة DOI للورقة أو ملف PDF المفتوح عند توفّره.

مكتبتك معك دائمًا
• تبقى الأوراق المحفوظة على جهازك وتعمل دون اتصال.
• ابحث في مكتبتك بكلمات من العنوان أو المؤلفين أو الملخص أو جهة النشر، ويتجاهل البحث العربي التشكيل واختلاف أشكال الحروف.
• اسحب لإزالة ورقة، مع إمكانية التراجع.

تابع قراءاتك
• صنّف كل ورقة: للقراءة، قيد القراءة، مقروءة.
• صفِّ مكتبتك حسب الحالة، مع عدد الأوراق في كل منها.

بالعربية والإنجليزية
• واجهة كاملة بالعربية والإنجليزية مع تخطيط من اليمين إلى اليسار، ولغة خاصة بالتطبيق.
• مظهر فاتح وداكن.

على الشاشات الكبيرة
• على الأجهزة اللوحية والقابلة للطي وأجهزة Chromebook، تُفتح الورقة بجانب مكتبتك أو نتائج البحث، وتظهر ملاحظاتك بجانب ملف PDF.
• اختصارات لوحة المفاتيح وقوائم النقر بالزر الأيمن عند استخدام لوحة مفاتيح وفأرة.

خصوصيتك أولًا
• بلا حساب ولا تتبّع ولا إعلانات، ولا تغادر مكتبتك جهازك.

بيانات الأوراق من OpenAlex ‏(openalex.org)، وهو فهرس مجاني ومفتوح للأعمال العلمية.
```

**ملاحظات الإصدار** [500]

```
الجديد: ملاحظات لكل ورقة، وقارئ PDF داخل التطبيق مع ملاحظاتك بجانب الصفحة، والمجموعات، وتصدير BibTeX إلى Overleaf. ويعرض قسم «التخزين» في الإعدادات مساحة ملفات PDF.
```

<!-- /lang -->

## Questionnaires

### App Store Connect: App Privacy

- **Data collection:** "Yes, we collect data from this app."
  - **Crash Data** — App Functionality; not linked to the user; not used for tracking.
  - **Other Diagnostic Data** — App Functionality; not linked; no tracking.
  - **Product Interaction** — Analytics; not linked; no tracking.
  - **Device ID** (Firebase's installation and app-instance ids) — App Functionality and Analytics; not linked; no tracking.
  - **Coarse Location** (approximate location Google derives from the IP address) — Analytics; not linked; no tracking.
  - Nothing else: search text, papers, notes and the library stay on the device; searches go straight to OpenAlex and arXiv.
- **Tracking:** none. No advertising id (the app uses `FirebaseAnalyticsCore`, which has no IDFA support), no App Tracking Transparency prompt.
- **Privacy manifest:** `PrivacyInfo.xcprivacy` in the app declares the five types above and the required-reason APIs (UserDefaults CA92.1, file timestamps C617.1, disk space E174.1); the share extension declares no collected data and the same APIs.
- Check against Firebase's current Apple data-disclosure page before each release that changes Firebase.

### App Store Connect: Age rating

Answer "None" or "No" to every question, which gives **4+**. The app has no web browser of its own (
links open in Safari), no user-generated content, no chat, and no gambling or contests.

### App Store Connect: Export compliance

`ITSAppUsesNonExemptEncryption` is `false` in `Info.plist`. The app uses only HTTPS (Apple's system
encryption), which is exempt, so uploads don't ask this question.

### App Store Connect: App Review notes

```
Hashiya needs no account. To try it: open Search and search for "large language models", tap Save on a result, then open Library and change the paper's status.
Share extension: in Safari, open https://arxiv.org/abs/1706.03762, tap Share and choose Hashiya.
Paper data comes from the public OpenAlex API (openalex.org).
iPad: the app supports Split View, Stage Manager and more than one window; long-press a paper and choose "Open in New Window".
Crash reports (Firebase Crashlytics) and usage statistics (Firebase Analytics) are on by default and can be turned off in Settings → Privacy. They are tied to a random identifier, not to a name or account, and contain no search text, papers or notes. The app has no advertising identifier and does no tracking.
```

### App Store Connect: before submitting 0.3.0

- **Privacy policy:** the URL stays the same, but the page must describe crash reports and usage statistics first. Merge the policy repository's "Usage statistics" PR, with its effective date set to the merge day, before inviting external TestFlight testers or submitting for review.
- **App Privacy:** 0.3.0 is the first iOS build that collects data. If App Store Connect still says "Data Not Collected", replace that with the five types under *App Store Connect: App Privacy* above, then publish the answers. They apply to the whole app, not to a single version.
- **What's New** and **Promotional text:** paste the 0.3.0 text in both languages. Promotional text can change at any time without a review.
- **Screenshots:** 0.3.0 runs natively on iPad, so App Store Connect asks for 13-inch iPad screenshots in both languages.
- **TestFlight → What to Test** [4000]

```
New in 0.3.0: iPad (Split View, Stage Manager, more than one window), keyboard shortcuts, backup and restore, and a grouped Details screen.
Please try:
• On iPad, open a paper in a new window from its menu, and resize the windows.
• Settings → Backup: back up with PDFs, delete a paper, then restore.
• Settings → Privacy: both switches are on by default. Turning them off stops crash reports and usage statistics.
Searches with no personal OpenAlex key share a daily budget; when it runs out, Search should keep working or say when it works again.
```

### Play Console: Data safety

- **Does your app collect or share any of the required user data types?** Yes, collects; nothing is shared.
- **Data types collected:**
  - App activity → **App interactions**: collected for **Analytics**; not shared; optional (Settings → Privacy → Share usage statistics).
  - Location → **Approximate location**: derived by Google Analytics from the IP address; **Analytics**; not shared; optional (Share usage statistics).
  - App info and performance → **Crash logs** and **Diagnostics**: collected for **App functionality** and **Analytics** (stability); not shared; optional (Settings → Privacy → Send crash reports).
  - Device or other IDs: Firebase installation and app-instance ids; **App functionality** and **Analytics**.
- **For each type:** collected, not shared; processing is not ephemeral.
- **Does your app use advertising ID?** No (the `AD_ID`, `ACCESS_ADSERVICES_AD_ID` and `ACCESS_ADSERVICES_ATTRIBUTION` permissions are removed, so the app has no advertising id and no ad attribution).
- **Is all of the user data collected by your app encrypted in transit?** Yes (HTTPS only).
- **Do you provide a way for users to request that their data is deleted?** No — reports and usage statistics are tied only to a random per-install identifier, not to a name or account, so we can't single out a user's data; Crashlytics deletes crash reports after 90 days, and Google deletes detailed analytics data after 2 months (overall totals remain).

### Play Console: Content rating (IARC)

Category **Reference, News, or Educational**. Answer "No" to violence, sexuality, language,
controlled substances, gambling, user interaction/sharing, location sharing and digital purchases.
Expected rating: Everyone / PEGI 3.

### Play Console: App content

| Question           | Answer                                                                       |
|--------------------|------------------------------------------------------------------------------|
| Target audience    | 18 and over (university students; keeps the app out of the Families program) |
| Contains ads       | No                                                                           |
| App access         | All functionality is available without special access                        |
| Government app     | No                                                                           |
| Financial features | None                                                                         |
| Health             | None                                                                         |
| News app           | No                                                                           |
