# Apple official guidance (HIG + technotes) for iPad and resizable windows

Reviewed: 2026-10-02. Apple's HIG has no requirement IDs; the IDs below (`HIG-…`) are
this skill's own labels so the audit report can point at one rule. Each section names
its HIG page — cite that page in the report.

Contents: Platform facts · Layout · Navigation · iPhone Duo · Split views & sidebars ·
Modality · Windows & multitasking · Input · Technotes · Sources

---

## Platform facts (from "Designing for iPadOS", "Multitasking", "Windows")

- iPad runs apps **full screen** or **windowed**. Windowed apps are freely resizable
  (like macOS), several can be on screen, and the system remembers size and placement.
- **Apps do not control multitasking configurations and get no indication of which one
  the user picked.** The only reliable input is the size the app is given.
- People hold iPad, put it on a stand or a surface, and mix touch, keyboard, pointer,
  Apple Pencil and voice.
- HIG size classes: every iPad, mini included, is regular × regular **when full screen**;
  in windows the system changes them with the window. For the iPhone-host caveat see
  `swiftui-uikit.md`, "The big shift".

## Layout — HIG page "Layout" (rewritten by Apple on 2026-09-09)

| ID | Guidance (paraphrased) | Phase |
| --- | --- | --- |
| HIG-LAYOUT-1 | Layout adapts gracefully and consistently to rotation, window resize, another display, another device. Respect safe areas, margins, layout guides. Even an orientation-locked app must resize well. | 1, 3 |
| HIG-LAYOUT-2 | **Determine layout from size classes, not device type (idiom) or orientation** — those say nothing about the space available. The system sets size classes from device, window configuration and multitasking state; apps can be in every combination. | 1 |
| HIG-LAYOUT-3 | **Consider all size-class combinations** in portrait and landscape aspect ratios (e.g. a landscape-iPhone layout must still use vertical space on iPad when the window is regular height). | 3, 9 |
| HIG-LAYOUT-4 | **Keep functionality the same as size classes change**; only the *amount visible* may change (tab bar → sidebar, overflow items exposed). The idiom stays the same while resizing, so the layout stays recognizable for that platform — a wide iPhone window is still an iPhone app. | 2, 3 |
| HIG-LAYOUT-5 | Be prepared for text-size changes (Dynamic Type): stack adjacent views, grow rows, allow multiple lines. | 4 |
| HIG-LAYOUT-6 | Preview at different size classes, localizations, text sizes; start with the largest and smallest layouts; Device Hub can test resizing on iPad and in iPhone Mirroring. | 9 |
| HIG-LAYOUT-7 | Scale background artwork to fill; windows can be very wide-and-short or tall-and-narrow, so art may need to extend beyond the usual visible area. Never distort it. | 3 |
| HIG-LAYOUT-8 | Extend backgrounds and scrollable content to the window edges; controls float above content (Liquid Glass + scroll edge effect instead of solid bar backgrounds). | 3 |
| HIG-LAYOUT-9 | Avoid full-width buttons — inset them within system margins (iOS). | 3 |
| HIG-LAYOUT-10 | Order content by importance: top and leading side first. | 8 |

Earlier versions of this page (before 2026-09-09) also said: while a window shrinks,
**defer switching to the compact layout as long as possible** (hide inspectors first),
and **test at the tiling sizes — halves, thirds, quadrants**. They are no longer in the
current text; keep them as good practice, but cite HIG-LAYOUT-1…4 in reports.

"Designing for iPadOS" adds: use the large display for content, **minimize modal
interfaces and full-screen transitions**, put controls where they are easy to reach
but not in the way, and adapt to orientation, multitasking, Dark Mode and Dynamic Type.

## Navigation — HIG pages "Tab bars", "Sidebars"

| ID | Guidance (paraphrased) | Phase |
| --- | --- | --- |
| HIG-NAV-1 | On iPad **prefer a tab bar**; if the app is complex, let people convert it to a sidebar. | 2 |
| HIG-NAV-2 | Tab bar is for navigation, not actions (actions go in toolbars). | 2 |
| HIG-NAV-3 | Keep the tab bar visible while navigating sections (modals excepted); never disable or hide tab items — explain empty sections instead. | 2 |
| HIG-NAV-4 | Avoid overflow ("More") tabs; every tab has an SF Symbol and a short label. | 2 |
| HIG-NAV-5 | Sidebar: no more than **two levels** of hierarchy; deeper → split view with a content column. Consider a tab bar first. | 2, 3 |
| HIG-NAV-6 | A sidebar inside a tab is fine for deep hierarchies; selecting in it must not switch tabs. | 2 |

## Split views and sidebars — HIG page "Split views"

| ID | Guidance (paraphrased) | Phase |
| --- | --- | --- |
| HIG-SPLIT-1 | Persistently highlight the current selection in each pane leading to the detail. | 3 |
| HIG-SPLIT-2 | **Account for narrow, compact and intermediate widths**; navigation between panes must stay logical at each width. | 3 |
| HIG-SPLIT-3 | Prefer split views in regular environments; in compact, collapse to a stack. | 3 |
| HIG-SPLIT-4 | Consider drag and drop between panes. | 6 |
| HIG-SPLIT-5 | For supplementary information prefer a split view over a new window. | 3 |
| HIG-SPLIT-6 | Show the most relevant detail on launch (not an empty pane when avoidable). | 3 |

## Modality — HIG pages "Popovers", "Sheets"

| ID | Guidance (paraphrased) | Phase |
| --- | --- | --- |
| HIG-MODAL-1 | **Avoid popovers in compact views**; reserve them for wide views and use a sheet when compact. | 3 |
| HIG-MODAL-2 | Popover: small amount of functionality, arrow points at its source, one at a time, never stacked, save work on auto-dismiss, not for warnings. | 3 |
| HIG-MODAL-3 | **On iPad prefer page or form sheet styles** (centered, default size) over full-screen presentation. | 3 |
| HIG-MODAL-4 | One sheet at a time; pair Done with Cancel; support swipe to dismiss (confirm if unsaved changes). | 3 |

## Windows and multitasking — HIG pages "Windows", "Multitasking"

| ID | Guidance (paraphrased) | Phase |
| --- | --- | --- |
| HIG-WIN-1 | Windows adapt fluidly to any size for multitasking and multi-window workflows. | 7 |
| HIG-WIN-2 | **Window controls appear at the toolbar's leading edge when windowed — move leading toolbar buttons inward so they aren't covered.** | 7 |
| HIG-WIN-3 | Open new windows only when it helps (e.g. compose beside the list); offer "Open in New Window" via context menu or gesture (pinch). | 7 |
| HIG-WIN-4 | Don't build custom window chrome; say "window" (not "scene") in user-facing text. | 7 |
| HIG-WIN-5 | Pause attention-requiring activities when people switch away; finish user-started tasks in the background; handle audio interruptions. | 7 |

## Input — HIG pages "Keyboards", "Pointing devices", "Context menus", "Drag and drop", "The menu bar", "Apple Pencil and Scribble"

| ID | Guidance (paraphrased) | Phase |
| --- | --- | --- |
| HIG-KEY-1 | Support **Full Keyboard Access**; respect standard shortcuts and don't repurpose them; custom shortcuts only for the most frequent commands. | 6 |
| HIG-KEY-2 | iPad menu bar: support the standard menus and their order, keep items visible (disable rather than hide), add app-specific menus for custom commands, consider each tab as a View-menu item with a shortcut, use submenus to save vertical space. Everything must also be reachable in the UI (menu bar can be hidden). | 6 |
| HIG-PTR-1 | Prefer system pointer effects; pad hit regions; contiguous hit regions in bars; distinguish pointer vs finger only when it adds value; allow drag-select of multiple items in custom collections. | 6 |
| HIG-CTX-1 | Context menus: short, relevant, consistent across the app, items also available in the main UI, destructive items last, one submenu level max. | 6 |
| HIG-DND-1 | Support drag and drop widely (multi-item where sensible), give alternatives (menu commands), show acceptance/failure feedback, allow undo. | 6 |
| HIG-PENCIL-1 | Scribble works in standard text fields automatically; custom text inputs must support it, stay stationary while writing, and have room to write. | 6 |

## iPhone Duo — HIG page "Designing for iPhone Duo" (new, 2026-09-09)

Apple's foldable iPhone: an outer display and an inner display, a center hinge, a
front camera on each display. SDK: iOS 27.1 / Xcode 27.1 (APIs were beta in
September 2026 — check availability before using them).

| ID | Guidance (paraphrased) | Phase |
| --- | --- | --- |
| HIG-DUO-1 | **Build the app to resize**: size classes, layout margins, safe areas; no fixed widths or display-specific code. Compact width on the outer display + regular width on the inner display covers every pose — don't design a layout per pose. | 1, 3 |
| HIG-DUO-2 | **Same functionality and state on both displays and in every pose.** Optionally show one more hierarchy level on the inner display (Mail: list *or* message closed; both side by side open). | 3, 4 |
| HIG-DUO-3 | **Reserved regions**: outer camera (always), inner camera (only when active), folding region (only when partially folded). Keep important custom elements clear of them with `ReservedRegion` (SwiftUI) / `UIView.ReservedRegion` (UIKit). System alerts, menus, sheets and split views adapt automatically. Continuous scrolling content (lists, feeds, articles) need not move off the fold. | 5 |
| HIG-DUO-4 | **Adapt when folded, with small moves.** Prefer self-adapting containers; in grids prefer an even number of columns; avoid extreme rearrangement while folding. | 5 |
| HIG-DUO-5 | Split views: expanded on the inner display, one pane on the outer — same as regular vs compact on other iPhones. | 3 |
| HIG-DUO-6 | **Arrangement views** (`ArrangementView` / `UIArrangementViewController`): a primary + secondary view, *split* (side by side or stacked by aspect ratio) or *overlay* (moves to each side of the fold when partially folded). Use when the layout already looks like an HStack/VStack (split) or ZStack (overlay). Keep navigation containers *outside* them. Available on iOS, iPadOS and Mac Catalyst. | 3, 5 |
| HIG-DUO-7 | **Vertical controls**: on the outer display (and the inner display in landscape) toolbars, tab bars and navigation controls move to the side. Use standard bars and don't override placement; account for asymmetric content area via safe areas; top of the vertical axis = Back/Close then Done; set visibility priorities (`ToolbarItemVisibilityPriority`) so frequent items survive overflow; group items with `ToolbarItemGroup` instead of manual spacing; give every symbol item a title; minimize text buttons; use the system overflow menu. | 2 |
| HIG-DUO-8 | When space is short: navigation-focused views keep the tab bar (toolbar items overflow); task-focused views minimize the tab bar to keep the toolbar. | 2 |
| HIG-DUO-9 | Games: may lock orientation but must fill the screen in every pose; prefer changing aspect ratio over letterboxing. | 1 |

Hinge angle (`onHingeChange` / `UIHingeInteraction`) is for interactive effects, not
layout — Apple says layout should use arrangement views and reserved regions. Camera
apps: camera position no longer implies direction (`AVCaptureDeviceDirectionCoordinator`),
and `CameraCaptureAccessory` can show a preview on the outer display.

## Technotes and platform requirements

- **TN3192 — UIRequiresFullScreen deprecated (iPadOS 26).** Ignored in a future release.
  To support resizable scenes the app must provide a launch screen and support all
  interface orientations; replace frame-based layouts with Auto Layout / SwiftUI.
- **Launch screen is required for App Store submission starting iOS 27 / iPadOS 27**
  (per TN3192).
- `UIInterfaceOrientationMask`: all iPads support portrait upside-down; Apple calls
  enabling it for the iPad idiom best practice.

## Sources

HIG pages (canonical URLs). Read via two Markdown mirrors of Apple's site:
github.com/doppe7gangar/hig (synced 2026-08-30) and github.com/dickwu/apple-design-skill
(synced 2026-09-29; source of the 2026-09-09 Layout rewrite and the iPhone Duo page).
To refresh, pull the latest version of either mirror. Pages:
- https://developer.apple.com/design/human-interface-guidelines/designing-for-iphone-duo
- https://developer.apple.com/design/human-interface-guidelines/designing-for-ipados
- https://developer.apple.com/design/human-interface-guidelines/layout
- https://developer.apple.com/design/human-interface-guidelines/multitasking
- https://developer.apple.com/design/human-interface-guidelines/windows
- https://developer.apple.com/design/human-interface-guidelines/tab-bars
- https://developer.apple.com/design/human-interface-guidelines/sidebars
- https://developer.apple.com/design/human-interface-guidelines/split-views
- https://developer.apple.com/design/human-interface-guidelines/popovers
- https://developer.apple.com/design/human-interface-guidelines/sheets
- https://developer.apple.com/design/human-interface-guidelines/keyboards
- https://developer.apple.com/design/human-interface-guidelines/pointing-devices
- https://developer.apple.com/design/human-interface-guidelines/context-menus
- https://developer.apple.com/design/human-interface-guidelines/drag-and-drop
- https://developer.apple.com/design/human-interface-guidelines/the-menu-bar
- https://developer.apple.com/design/human-interface-guidelines/apple-pencil-and-scribble

Technotes and API docs:
- TN3192 — https://developer.apple.com/documentation/technotes/tn3192-migrating-your-app-from-the-deprecated-uirequiresfullscreen-key
- supportedInterfaceOrientations — https://developer.apple.com/documentation/uikit/uiviewcontroller/supportedinterfaceorientations
- Preparing your app for iPhone Duo — https://developer.apple.com/documentation/technologyoverviews/preparing-your-app-for-iphone-duo
- Tech Talk "Strike a pose with adaptive layouts on iPhone Duo" — https://developer.apple.com/videos/play/tech-talks/111463/
