# Adaptive UI checklist (stack-independent)

Each phase ends with a **Gate**: a concrete check the app must pass before the next
phase. Stack-specific "how" lives in `compose.md` and `swiftui-uikit.md`.

Contents: Audit report template · Phases 1–10 · Breakpoints

---

## Audit report template

```markdown
# Adaptive audit — <app name>
Stack: <Compose | SwiftUI | UIKit | mixed>   Date: <YYYY-MM-DD>

## Current state
<2–3 sentences: how the app behaves today on a tablet / foldable / resized window.>

## Screen map
| Screen | Canonical layout | Notes |
| --- | --- | --- |
| Library | List–detail | detail opens as a new screen today |
| Reader  | Simple (max width) | text spans full width on tablets |

## Red flags
| Where (file:line) | Problem | Rule broken | Phase that fixes it |
| --- | --- | --- | --- |
| AndroidManifest.xml:12 | portrait lock | Config_Changes (Tier 3) | 1 |

## Guideline scorecard
Android (target Tier 2): Tier 3 <n pass / n fail / n N/A> · Tier 2 <…>
iPad (HIG): <pass / fail per HIG-* label that applies>
List each fail once; N/A items with a one-line reason.

## Plan
Phase 1–4 (required): <one line each, specific to this app>
Phase 5–8 (recommended): <which ones matter for this app and why; which can be skipped>

## Decisions needed
- <question for the user>
```

---

## Phase 1 — Foundations

- Remove orientation locks, or scope them to compact phones only where truly needed.
- iOS: drop `UIRequiresFullScreen`; support all orientations on iPad.
- Define breakpoints in ONE place (600 / 840 / 1200 / 1600 dp-pt widths).
- Every layout decision reads the available window/container size — never device
  type, idiom, `UIScreen` bounds or physical rotation.
- Width class decides the layout; height class adjusts it.
- Remove `isTablet`-style helpers; keep platform checks for conventions only.

**Gate:** the app opens at any size and orientation with no crash and no
letterboxing/pillarboxing, even if layouts still look phone-like.

## Phase 2 — Navigation

- Android: compact → navigation bar · medium → rail (M3 also permits a bar here) ·
  expanded+ → rail, expanding to an expanded rail at large sizes. Replace navigation
  drawers with the expanded rail (Material 3 Expressive). (UI_Secondary_Elements)
- iPad: prefer a tab bar, convertible to a sidebar (`sidebarAdaptable`); sidebar max two
  levels; never hide or disable tabs. (HIG-NAV-1…5)
- One navigation hierarchy; only its presentation changes.
- Selected destination and back stack survive presentation changes.

**Gate:** resize live through every breakpoint; navigation morphs without losing place.

## Phase 3 — Layouts

- List–detail: two panes from expanded (Material: one pane recommended at medium);
  separate screens when compact. iPad: keep the full layout as long as it fits while
  the window shrinks; hide inspector columns first. (HIG-LAYOUT-2, HIG-SPLIT-2)
- Split only when seeing both panes at once helps the user.
- Margins 16dp compact / 24dp medium+, 24dp spacer between panes (Material).
- iPad: popovers only in wide views; page/form sheets instead of full screen.
  (HIG-MODAL-1, HIG-MODAL-3)
- Grids: column count grows with width; cards keep sensible sizes.
- Reading text: max width ~600–840.
- Buttons, text fields, bottom sheets: not full width on large windows.
- Dialogs and sheets: centered dialog / popover instead of full screen on wide windows.
- Images and media: fixed aspect ratios, never stretched.
- No hardcoded sizes that assume a phone.

**Gate:** every screen looks intentional at compact, medium and expanded — not a
stretched phone.

## Phase 4 — Continuity and state

Across rotate, fold/unfold, resize, split-screen entry/exit, these must survive:
- scroll position
- typed text and keyboard state
- selected item (a detail open on the phone stays open in the second pane after unfold)
- media playback position
- state lives in ViewModel / saveable state / scene storage — not in a view that
  disappears when the layout changes
- Android: density change (cover and inner screens can differ)
- font scale / Dynamic Type at the largest sizes

**Gate:** mid-form, fold, unfold, rotate and resize — nothing is lost.

## Phase 5 — Foldables (Android) / window details (iPad)

- Never assume the natural orientation is portrait (landscape-first foldables and
  trifolds exist).
- Works on cover screens and flip phones' small outer displays.
- No key control or text exactly on the fold; separating hinge → panes on either side.
- Optional: tabletop posture (content top, controls bottom); book posture (two pages).
  Trifolds do not support tabletop.
- Camera: preview correct (not sideways, stretched or cropped) after fold/unfold.
- iPhone Duo: works on outer (compact) and inner (regular) displays, partial fold and
  Split View; custom elements clear of reserved regions; no centered controls on the
  fold; standard vertical bars with visibility priorities. (HIG-DUO-1…8)
- iPad: behaves in every windowing mode (full screen, windowed apps, Stage Manager).

**Gate:** works on portrait-first, landscape-first and flip foldables, on iPhone Duo
(if the app ships on iOS), and on iPad in each windowing mode.

## Phase 6 — Input

- Keyboard: Tab + arrow-key navigation, Enter = send (communication apps), Esc =
  close/cancel, shortcuts for select/cut/copy/paste/undo/redo, Space for media.
  (Keyboard_* IDs; HIG-KEY-1) iPad menu bar via commands. (HIG-KEY-2)
- Ctrl + scroll / pinch zooms zoomable content. (Content_Zoom)
- Visible focus indicator.
- Pointer: hover states, secondary click / context menus, scroll wheel.
- Optional: stylus where drawing or annotation exists; drag & drop in/out of the app.

**Gate:** the whole app is usable with keyboard and mouse, without touching the screen.

## Phase 7 — Multitasking and multi-window

- Split screen at 1/2 and 1/3 width; iPad tiling at halves, thirds, quadrants. (HIG-LAYOUT-3)
- iPad windowed: leading toolbar buttons not covered by window controls. (HIG-WIN-2)
- Multi-resume: keep updating when not focused; release/regain camera & mic. (Multi-Resume)
- Desktop/freeform windowing (Android), windowed apps / Stage Manager (iPad).
- Continuous resize without lag or flicker.
- Optional: multiple windows/scenes of the same app.
- Optional: a sensible minimum window size (a preference the system may not honor).

**Gate:** the app is respectable at any window size the user chooses.

## Phase 8 — Ergonomics

- Primary actions near the edges, within thumb reach on a two-handed grip.
- No primary action in the top-center of a large screen.
- Touch targets ≥ 48dp (Android) / 44pt (iOS).
- Frequent gestures in regions close to the palm.

**Gate:** five minutes holding the tablet with both hands without strain.

## Phase 9 — Testing matrix

| Case | Android | iOS |
| --- | --- | --- |
| Small phone | compact phone | iPhone SE / mini |
| Tablet portrait + landscape | Pixel Tablet emulator | iPad + iPad mini |
| Portrait-first foldable | Pixel Fold / Fold emulator | — |
| Landscape-first foldable / trifold | matching emulator if available | iPhone Duo simulator (Xcode 27.1 Device Hub): outer, inner, partial fold |
| Free resize | Resizable emulator + desktop windowing | windowed apps / Stage Manager |
| Split screen | 1/2 and 1/3 | Split View |
| Large text | font scale 200% | largest Dynamic Type |
| Keyboard + mouse | yes | yes |

## Phase 10 — Release

- Tablet / foldable screenshots in Google Play and App Store listings.
- Check against Tier 2 of Android's Adaptive app quality guidelines.
- Mention large-screen support in release notes.

---

## Breakpoints

| Class | Width | Typical navigation | Panes |
| --- | --- | --- | --- |
| Compact | < 600 | bar (iPad/iPhone: tab bar) | 1 |
| Medium | 600–839 | rail (or bar) | 1 recommended |
| Expanded | 840–1199 | rail / sidebar | 2 recommended |
| Large | 1200–1599 | expanded rail / sidebar | 2 recommended |
| Extra-large | ≥ 1600 | expanded rail / sidebar | up to 3 |

Units: dp on Android, points on iOS (Apple publishes no such classes; these are a
practical default for custom breakpoints).
