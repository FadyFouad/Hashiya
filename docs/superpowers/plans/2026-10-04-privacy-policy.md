# Privacy Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish Hashiya's privacy policy in English and Arabic at a stable public URL before either app ships crash reporting.

**Architecture:** Two Markdown pages under `docs/privacy/`, published by GitHub Pages from the `main` branch's `docs/` folder with a `_config.yml` that publishes only the privacy pages.

**Tech Stack:** GitHub Pages (Jekyll, default theme), Markdown.

**Spec:** `docs/superpowers/specs/2026-10-04-crash-reporting-design.md` §7

## Global Constraints

- Contact address: fady.fouad.a@gmail.com.
- The policy describes the app as it will be once crash reporting ships: library, notes and PDFs stay on the device; searches and identifiers go to OpenAlex and arXiv; crash and diagnostic data, with a random installation ID, go to Firebase Crashlytics (Google) when the Settings switch is on (on by default); no accounts, no ads, no analytics, no tracking, nothing sold.
- English and Arabic pages say the same thing; the Arabic page is right-to-left.
- Published URLs: `https://fadyfouad.github.io/Hashiya/privacy/` and `https://fadyfouad.github.io/Hashiya/privacy/ar`.
- Only the privacy pages are published; specs, plans, store notes and release docs are excluded from the site.
- Commits authored `Fady <fady.fouad.a@gmail.com>`; no AI attribution.

## Review Focus

1. **A reader on a phone** — pages must read well on a narrow screen (plain Markdown, no wide tables).
2. **Arabic page direction** — the whole page right-to-left, English product names (OpenAlex, arXiv, Firebase Crashlytics) still readable.
3. **Accidental publishing** — `docs/superpowers/**`, `docs/store/**`, `docs/brand/**`, `docs/release.md` must not appear on the site.
4. **Accuracy against the code** — nothing promised that the apps don't do (e.g. "never leaves your device" must not be said about searches).
5. **A dated policy** — an "Updated" date so later changes (analytics, ads) are visible.

---

### Task 1: The policy pages and the Pages config

**Files:**
- Create: `docs/_config.yml`, `docs/privacy/index.md`, `docs/privacy/ar.md`
- Modify: `docs/store/metadata.md` (privacy policy URL lines for both stores)

- [ ] **Step 1: Write `docs/_config.yml`**

```yaml
title: Hashiya
description: Hashiya — a research paper library for your phone
# Only the privacy policy is published; everything else in docs/ is for the project.
exclude:
  - superpowers
  - store
  - brand
  - release.md
  - README.md
```

- [ ] **Step 2: Write `docs/privacy/index.md`**

```markdown
---
title: Privacy Policy
permalink: /privacy/
---

# Hashiya Privacy Policy

Updated: <the date this PR is merged, e.g. 5 October 2026>

Hashiya is a library for research papers. It has no accounts and no server of its own.

## What stays on your device

Your library — saved papers, reading status, notes, collections and PDFs — is stored only on your device. Hashiya does not upload it anywhere. When you export a backup, the file goes only where you choose to save or send it.

Your operating system may include Hashiya's data in its own device backups (Google or iCloud backups), under your account and settings. Downloaded PDFs are left out of those backups because they can be downloaded again.

## What Hashiya sends, and to whom

- **Searches and paper lookups** go to [OpenAlex](https://openalex.org) and, for arXiv identifiers, to [arXiv](https://arxiv.org), to answer that request. If you add your own OpenAlex API key, it is sent to OpenAlex with your requests and stored only on your device.
- **PDFs** are downloaded from the open-access links OpenAlex provides, when you ask for them.
- **Crash reports.** When Hashiya crashes or hits certain errors, it sends a report to Firebase Crashlytics, a Google service, so the problem can be fixed. A report contains the technical details of the error, the app version, your device model and operating system, the app's language, a rough library size (for example "51–500 papers"), which screen was open, and a random installation identifier created by Firebase. It never contains your papers, notes, searches, collection names or files. Google processes this data on Hashiya's behalf; see [Firebase's privacy information](https://firebase.google.com/support/privacy).

## Turning crash reports off

Crash reports are on by default. Turn them off in **Settings → Privacy → Send crash reports**. When you turn them off, reports not yet sent are deleted and nothing more is sent.

## What Hashiya does not do

No accounts, no advertising, no analytics, no tracking across apps or websites, and no selling or sharing of your data.

## Children

Hashiya is not directed at children and does not knowingly collect information from them.

## Changes

If this policy changes — for example if a future version adds new features that send data — the new policy will be published here with a new date before that version is released.

## Contact

Questions or requests: [fady.fouad.a@gmail.com](mailto:fady.fouad.a@gmail.com)

[العربية](ar)
```

- [ ] **Step 3: Write `docs/privacy/ar.md`**

```markdown
---
title: سياسة الخصوصية
permalink: /privacy/ar
---

<div dir="rtl" lang="ar" markdown="1">

# سياسة الخصوصية لتطبيق حاشية

آخر تحديث: <تاريخ دمج هذا التغيير، مثل ٥ أكتوبر ٢٠٢٦>

حاشية مكتبة للأوراق البحثية. لا يحتاج إلى حساب، ولا يملك خادمًا خاصًا به.

## ما يبقى على جهازك

مكتبتك — الأوراق المحفوظة وحالة القراءة والملاحظات والمجموعات وملفات PDF — تُخزَّن على جهازك فقط، ولا يرفعها حاشية إلى أي مكان. وعندما تصدّر نسخة احتياطية، يذهب الملف إلى المكان الذي تختار حفظه أو إرساله إليه فقط.

قد يضمّ نظام التشغيل بيانات حاشية إلى نسخه الاحتياطية للجهاز (نسخ Google أو iCloud الاحتياطية) وفق حسابك وإعداداتك. ولا تُضمَّن ملفات PDF المُنزَّلة في هذه النسخ لأن تنزيلها ممكن مرة أخرى.

## ما يرسله حاشية وإلى من

- **عمليات البحث وجلب بيانات الأوراق** تُرسَل إلى [OpenAlex](https://openalex.org)، وإلى [arXiv](https://arxiv.org) عند البحث بمعرّف arXiv، للإجابة عن الطلب. وإذا أضفت مفتاح OpenAlex API الخاص بك، فإنه يُرسَل إلى OpenAlex مع طلباتك ولا يُخزَّن إلا على جهازك.
- **ملفات PDF** تُنزَّل من روابط الوصول المفتوح التي يوفّرها OpenAlex عندما تطلبها.
- **تقارير الأعطال.** عندما يتعطّل حاشية أو يواجه أخطاء معيّنة، يرسل تقريرًا إلى Firebase Crashlytics، وهي خدمة من Google، لإصلاح المشكلة. يتضمّن التقرير التفاصيل التقنية للخطأ، وإصدار التطبيق، وطراز جهازك ونظام التشغيل، ولغة التطبيق، وحجمًا تقريبيًا للمكتبة (مثل «51–500 ورقة»)، والشاشة التي كانت مفتوحة، ومعرّف تثبيت عشوائيًا تنشئه Firebase. ولا يتضمّن أبدًا أوراقك أو ملاحظاتك أو عمليات بحثك أو أسماء مجموعاتك أو ملفاتك. تعالج Google هذه البيانات نيابةً عن حاشية؛ راجع [معلومات الخصوصية في Firebase](https://firebase.google.com/support/privacy).

## إيقاف تقارير الأعطال

تقارير الأعطال مفعّلة افتراضيًا. يمكنك إيقافها من **الإعدادات ← الخصوصية ← إرسال تقارير الأعطال**. وعند إيقافها تُحذف التقارير التي لم تُرسَل بعد، ولا يُرسَل شيء بعد ذلك.

## ما لا يفعله حاشية

لا حسابات، ولا إعلانات، ولا تحليلات، ولا تتبّع عبر التطبيقات أو المواقع، ولا بيع لبياناتك أو مشاركتها.

## الأطفال

حاشية غير موجّه إلى الأطفال، ولا يجمع معلومات عنهم عن قصد.

## التغييرات

إذا تغيّرت هذه السياسة — كأن يضيف إصدار قادم ميزات جديدة ترسل بيانات — ستُنشر السياسة الجديدة هنا بتاريخ جديد قبل صدور ذلك الإصدار.

## التواصل

للأسئلة أو الطلبات: [fady.fouad.a@gmail.com](mailto:fady.fouad.a@gmail.com)

[English](./)

</div>
```

Check the Arabic Settings labels against the strings the Android plan adds (`الخصوصية`, `إرسال تقارير الأعطال`) and keep them identical.

- [ ] **Step 4: Point the store notes at the policy**

In `docs/store/metadata.md`, add a "Privacy policy URL" line under both "App Store Connect: App Privacy" and "Play Console: Data safety": `https://fadyfouad.github.io/Hashiya/privacy/`. Leave the data-collection answers unchanged in this PR — each platform's crash-reporting PR changes them when it ships.

- [ ] **Step 5: Check the rendering**

There is no local Jekyll setup; check both files' rendered Markdown in the PR's "Files changed" view (the Arabic page should read right-to-left), and check the published site after merging (see below).

- [ ] **Step 6: Commit**

```bash
git add docs/_config.yml docs/privacy docs/store/metadata.md
git commit -m "docs: privacy policy in English and Arabic"
```

## After merging (the user)

GitHub → repository Settings → Pages → Source: **Deploy from a branch**, Branch: **main**, Folder: **/docs** → Save. After a minute, open both URLs and confirm only the privacy pages are served (e.g. `https://fadyfouad.github.io/Hashiya/superpowers/` returns 404).
