---
name: adaptive-ui
description: Make a phone-first native app work properly on tablets, foldables (Android foldables and iPhone Duo), iPad and resizable windows — audit the codebase, write a phased plan, then implement it one phase at a time with a stop-and-review gate after each. Supports Jetpack Compose (Android) and SwiftUI/UIKit (iOS/iPadOS). Use this skill whenever the user wants tablet, iPad, foldable, large-screen, landscape, split-screen, multi-window or desktop-windowing support; mentions adaptive or responsive layouts, window size classes, size classes, NavigationSplitView, ListDetailPaneScaffold, letterboxing, orientation locks, Android 16 / API 36 large-screen behavior, UIRequiresFullScreen, iPhone Duo, ReservedRegion or ArrangementView; or says things like "make it work on the tablet", "عايز الأبلكيشن يشتغل على التابلت", "حوّل الـ UI للتابلت", "دعم الـ foldables" — even if they never say "adaptive".
---

# Adaptive UI

Turn a phone-only app into one that is deliberate at every window size. The work is
done in phases. **After every phase you stop, report, and wait for the user's go-ahead.**
The user reviews each step before the next one starts; never batch several phases into
one turn, even when they look small.

## The one rule behind everything

Lay out by **the space the app actually has right now**, never by the kind of device.
A tablet can run the app in a phone-sized split-screen pane; a foldable switches from
phone to tablet while the app is open; on iOS 27 even iPhone apps run in resizable
windows, and iPhone Duo folds between a compact and a regular display. Device type, idiom, screen bounds and physical orientation are all unreliable
inputs for layout. When you are unsure about a decision, come back to this rule.

## Step 1 — Detect the stack and read the matching reference

Look at the project before anything else:

- `build.gradle(.kts)` with Compose dependencies, `@Composable` → **Compose**:
  read `references/compose.md` and `references/android-guidelines.md`.
- `.xcodeproj` / `Package.swift`, `import SwiftUI` or `UIViewController` →
  **SwiftUI / UIKit**: read `references/swiftui-uikit.md` and `references/apple-hig.md`.
- Both (e.g. a KMP app with native UIs) → read both, treat each platform separately.
- Flutter, React Native or anything else → say this skill covers Compose and
  SwiftUI/UIKit only; the principles in `references/checklist.md` still apply, so offer
  to use them as a guide without the stack-specific steps.

The `*-guidelines` / `apple-hig` files are the official yardstick: Google's Adaptive
app quality tiers (with Google's guideline IDs) plus Material 3 layout rules, and
Apple's HIG plus the relevant technotes. The audit judges the app against them, and
every red flag names the rule it breaks. The default target is **Android Tier 2** and
the **HIG iPad guidance**; ask the user before aiming lower or higher.

The stack references hold the APIs. APIs in this area change fast (Compose released new
adaptive layout APIs in 2026; Apple changed windowing in iPadOS 26 and iOS 27). If a
reference marks an API as newer, or you are unsure of an exact signature, check the
official docs with web search before writing the code.

## Step 2 — Phase 0: Audit (no code changes)

1. Run the red-flag scan:
   ```bash
   bash scripts/audit.sh <project-root>
   ```
   It greps for device-type checks, orientation locks, hardcoded sizes and similar.
   Treat its output as leads, not verdicts: read each hit in context and decide.
2. Read the main navigation setup and every top-level screen.
3. Classify each screen into one canonical layout:
   - **List–detail** (inbox, library, settings with sub-pages)
   - **Feed / grid** (cards, galleries)
   - **Supporting pane** (main content + filters / info / notes)
   - **Simple** (forms, a single article) → needs a max width only
   - **Special** (camera, media player, canvas) → note what it needs
4. Check the app against the guideline file(s): for each requirement, mark
   pass / fail / N/A (with a reason — Google itself says not every requirement fits
   every app). Every red flag cites its rule: a Google guideline ID such as
   `Config_Changes` or `UI_Secondary_Elements`, or an Apple label such as
   `HIG-LAYOUT-2` with its HIG page.
5. Write the audit report using the template in `references/checklist.md`
   ("Audit report template"). Save it as `adaptive-audit.md` in the project root
   (or deliver it in chat if you cannot write files).

**Gate 0.** Show the user the report: screen map, red flags, proposed phase plan, and
anything you need them to decide (e.g. "the camera screen can keep a portrait lock on
phones only — OK?"). Ask whether to start Phase 1. Stop here.

## Step 3 — Phases 1 to 8, one per turn

The full checklist for each phase, with its gate, is in `references/checklist.md`.
The stack reference says *how* to do each item. Order:

| Phase | Focus | Required? |
|---|---|---|
| 1 | Foundations: remove locks and device checks, central breakpoints | Yes |
| 2 | Adaptive navigation (Android: bar → rail → expanded rail; iPad: tab bar ↔ sidebar) | Yes |
| 3 | Canonical layouts, max widths, grids, dialogs | Yes |
| 4 | Continuity and state across resize / fold / rotate | Yes |
| 5 | Foldables (Android, iPhone Duo) / windowing details (iPad) | Recommended |
| 6 | Keyboard, mouse/trackpad, stylus, drag & drop | Recommended |
| 7 | Multitasking and multi-window | Recommended |
| 8 | Ergonomics on large handheld screens | Recommended |
| 9 | Testing matrix | Always, at the end |
| 10 | Store and release | Always, at the end |

Phases 1–4 are the minimum for an acceptable app. Ask before skipping any later phase,
and say what the user would lose.

### How to run a phase

1. Restate the phase's items in one or two lines so the user knows what is coming.
2. Make the changes. Keep diffs small and scoped to the phase — no drive-by refactors.
   If a change outside the phase is necessary, say why.
3. Build (or at least type-check) if the environment allows it. Fix what you broke.
4. Write the gate report (format below) and stop.

### Gate report format

```
## Phase N — <name>: done

Changed
- <file>: <what and why, one line>

How to check it
- <concrete action on a concrete device/emulator, e.g. "Resizable emulator: drag the
  window from 400dp to 1300dp — bar becomes rail at 600, sidebar at 840">

Open questions / decisions for you
- <only if any>

Next: Phase N+1 — <name>. Shall I continue?
```

Wait for the user's answer. If they report a problem, fix it inside the same phase and
re-issue the gate report before moving on.

## Decisions that come up often

- **"Can't I just keep portrait?"** On Android, apps targeting API 36 have orientation
  and resizability restrictions ignored on displays with smallest width ≥ 600dp, and
  Google Play requires API 36 for updates from August 2026. On iPad,
  `UIRequiresFullScreen` is deprecated since iPadOS 26. A portrait lock on phones only
  is still fine where it genuinely helps (some camera or game screens); see the stack
  references for how to scope it.
- **When to split into two panes.** Only when the user benefits from seeing both at once
  (list + selected item, content + its filters). A tablet is still a smallish screen;
  splitting just to fill space makes things worse. A grid or a centered max-width column
  is often the better answer.
- **Width first, then height.** Choose the layout from the width class; then adjust for
  short heights (landscape phones, wide foldables, split windows).
- **Platform conventions still matter.** Same adaptive logic on both platforms, but follow
  Material on Android (rail and expanded rail — the navigation drawer is no longer
  recommended in Material 3 Expressive) and the HIG on iPad (tab bar convertible to a
  sidebar, toolbars, popovers only when wide, form/page sheets, menu bar commands).
- **Same functions at every size.** HIG: functionality never changes with size class —
  only how much of it is visible (tab bar → sidebar, overflow items exposed). Don't
  collapse to the compact layout earlier than needed; hide inspectors first.
- **Size classes vs geometry on Apple platforms.** Size classes for structural switches
  (Apple's official signal), measured geometry for fine breakpoints. Never idiom,
  `UIScreen` or orientation.
- **Medium width ≠ two panes.** Material recommends one pane at medium (600–839dp) and
  two from expanded; use two at medium only for low-density content like settings.

## Communicating

Reply in the user's language. If they write in Egyptian Arabic, answer in Egyptian
Arabic and keep technical terms (window size class, pane, rail, scene) in English, as
they do. Keep gate reports short: the user reads them to decide, not to learn.
