# SwiftUI / UIKit — how to do each phase

APIs marked **(iOS 26+)** or **(iOS 27)** are recent; confirm availability and exact
signatures in Apple's docs before using them, and gate them with `#available`.

Contents: The big shift · Phase 1 · 2 · 3 · 4 · 5 · 6 · 7 · Testing

---

## The big shift (read first)

Since iOS 26/27 every Apple app can end up in a window of arbitrary size: resizable
iPad windows, iPhone apps in iPhone Mirroring on Mac, iPhone-only apps on iPad, and
iPhone Duo (two displays, a fold, iOS 27.1). Apple's position, from the HIG Layout
rewrite (2026-09-09) and WWDC26 "Modernize your UIKit app":
- **Never lay out by idiom, device model, `UIScreen` bounds or orientation.** They do
  not describe the space the app has.
- **Size classes are the official structural signal** (HIG-LAYOUT-2): the system sets
  them from device, window and multitasking state. Use them for structural switches:
  tab bar ↔ sidebar, one vs two split-view columns, popover vs sheet.
- **Measured geometry for finer decisions** (column counts, max widths, when a custom
  sidebar fits) via `onGeometryChange` / container size.
- Caveat seen in the iOS 27 betas (Fatbobman, June 2026): in an iPhone host, a widened
  window may keep `horizontalSizeClass == .compact`; Apple's view is that a wide iPhone
  window is still an iPhone experience (HIG-LAYOUT-4). Verify behavior on the current
  SDK; if you need a wide-iPhone layout, drive it from geometry, not by injecting
  `.regular`.

## Phase 1 — Foundations

**Info.plist / target settings**
- Remove `UIRequiresFullScreen`. Deprecated since iPadOS 26 and will be ignored.
  Migration steps: Apple TN3192 "Migrating your iPad app from the deprecated
  UIRequiresFullScreen key".
- `UISupportedInterfaceOrientations~ipad`: all four.
- App has a launch screen (required for resizable scenes).
- Universal target (iPhone + iPad) if the app is iPhone-only today — otherwise it runs
  in compatibility mode on iPad.

**Measure, don't ask the device.** SwiftUI:
```swift
struct AdaptiveRoot: View {
    @State private var width: CGFloat = 0
    var body: some View {
        content(for: LayoutClass(width: width))
            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
    }
}

enum LayoutClass {
    case compact, medium, expanded
    init(width: CGFloat) {
        switch width {
        case ..<600: self = .compact
        case ..<840: self = .medium
        default:     self = .expanded
        }
    }
}
```
`onGeometryChange` is iOS 18+ (back-deployed to iOS 16). Older targets: `GeometryReader`
at the root. `ViewThatFits` and `containerRelativeFrame` cover many cases without
explicit breakpoints.

UIKit: read `view.bounds.size` in `viewWillLayoutSubviews` / `viewWillTransition(to:with:)`,
or the scene: `windowScene.effectiveGeometry` and
`windowScene(_:didUpdateEffectiveGeometry:)` **(iOS 26+)**. For trait changes use
`registerForTraitChanges(_:handler:)` (iOS 17+) instead of overriding
`traitCollectionDidChange`.

**Red flags to remove:** `UIDevice.current.userInterfaceIdiom == .pad`,
`UIScreen.main.bounds`, `UIDevice.current.orientation` for layout,
`UIApplication.shared.statusBarOrientation`, hardcoded `375`/`390`/`414` widths.

**Orientation lock where truly needed** (e.g. during capture):
`prefersInterfaceOrientationLocked` on the view controller **(iOS 26+)**; it is a
request the system may honor, not a guarantee.

## Phase 2 — Navigation

SwiftUI, tab-based app:
```swift
TabView(selection: $tab) {
    Tab("Library", systemImage: "books.vertical", value: .library) { LibraryView() }
    Tab("Search", systemImage: "magnifyingglass", value: .search, role: .search) { SearchView() }
}
.tabViewStyle(.sidebarAdaptable)   // tab bar ↔ sidebar on iPad (iOS 18+)
```
HIG prefers a tab bar on iPad, convertible to a sidebar (HIG-NAV-1, HIG-LAYOUT-4);
`.sidebarAdaptable` is exactly that. When windowed, iPadOS draws window controls at the
toolbar's leading edge — keep leading toolbar items clear of them (HIG-WIN-2); system
toolbars handle this, custom bars must.

Hierarchical app: `NavigationSplitView` (sidebar / content / detail). It collapses to a
stack when narrow and shows columns automatically as the window widens; keep the
selection in `@State`/model so collapse/expand keeps the user's place.

If the app is an iPhone app that should show a sidebar when a resizable window is wide
(where `.sidebarAdaptable` will not, because the size class stays compact), switch on
your measured `LayoutClass` and render your own sidebar that drives the same tab state.

UIKit: `UISplitViewController(style: .doubleColumn / .tripleColumn)` with a `.compact`
column for the narrow layout; `UITabBarController` with `mode = .tabSidebar` (iOS 18+).

## Phase 3 — Layouts

- List–detail: `NavigationSplitView` with a `List(selection:)` sidebar and a detail
  view keyed by the selection; empty-state view when nothing is selected.
- Grids: `LazyVGrid(columns: [GridItem(.adaptive(minimum: 180))])`.
- Max width: `.frame(maxWidth: 700).frame(maxWidth: .infinity)` to center a readable
  column; same for buttons and text fields.
- Shrinking windows: keep the regular layout as long as it fits; collapse to compact
  last, hiding inspector columns first (HIG-LAYOUT-2).
- Sheets: on wide windows prefer `.presentationSizing(.form)` / `.page` (iOS 18+), or
  `.popover` anchored to the control; avoid `fullScreenCover` for simple forms.
- Avoid `UIScreen`-based image sizes; use `aspectRatio(_:contentMode:)`.

## Phase 4 — Continuity

- Per-window state: `@SceneStorage` (selection, tab, scroll anchor id) so each window
  restores independently.
- Keep selection and navigation path in an `@Observable` model owned above the layout
  switch, so one-column ↔ two-column switching does not reset it.
- `ScrollViewReader` / `scrollPosition(id:)` to restore scroll after layout changes.
- UIKit: state restoration with `NSUserActivity` per scene
  (`stateRestorationActivity(for:)`).
- Dynamic Type: test at the largest accessibility sizes; `ViewThatFits` and
  `dynamicTypeSize(...)` limits for dense UI.
- Interactive resizing: avoid expensive work on every frame —
  `onInteractiveResizeChange(_:)` (SwiftUI) /
  `UIWindowSceneGeometry.isInteractivelyResizing` **(iOS 26+)** let you defer it until
  the resize ends.

## Phase 5 — iPhone Duo and iPad windowing details

**iPhone Duo (iOS 27.1 SDK; APIs were beta in Sept 2026 — gate with `#available` and
check the docs).** Full guidance: `apple-hig.md` HIG-DUO-1…9.
- Outer display = compact width, inner = regular. If the app resizes correctly
  (Phases 1–4), most of Duo works; standard bars, sheets, alerts, menus and split views
  adapt to the fold automatically.
- Reserved regions for custom content: SwiftUI `GeometryProxy` →
  `reservedRegions(kind:options:layoutDirectionBehavior:)` with kinds `.division`
  (the fold, active only when partially folded) and `.occlusion` (cameras); UIKit
  `UIView.reservedRegions(kind:options:)`. Move only what overlaps, a little.
- Audit **centered custom layouts** first (Apple's first recommendation): anything
  placed at the horizontal center lands on the fold.
- `ArrangementView` (SwiftUI) / `UIArrangementViewController` (UIKit), styles
  `.split` / `.overlay`, for primary + secondary content; navigation containers stay
  outside it.
- Vertical bars: keep standard toolbars/tab bars; set `ToolbarItemVisibilityPriority`
  (UIKit `UIBarButtonItemVisibilityPriority`), group with `ToolbarItemGroup`, give
  every item a title + symbol, choose `ToolbarVerticalCompressionBehavior`
  (UIKit `UIVerticalBarCompressionBehavior`), use the system overflow menu
  (`ToolbarOverflowMenu` / `additionalOverflowItems`).
- Hinge angle (`onHingeChange` / `UIHingeInteraction`) only for interactive effects.
- Camera: `AVCaptureDeviceDirectionCoordinator` (position ≠ direction),
  `CameraCaptureAccessory` for a preview on the outer display.
- Test in Xcode 27.1 Device Hub with the iPhone Duo simulator: outer, inner, partial
  fold, Split View.

**iPad windowing**
- Test in all iPad modes the user can pick: full-screen apps, windowed apps, Stage
  Manager. Windows can be very narrow and very short.
- Minimum size preference: `windowScene.sizeRestrictions?.minimumSize = CGSize(…)` —
  best effort only; the layout must still survive smaller sizes.

## Phase 6 — Input

- Menu bar / commands: SwiftUI `.commands { CommandMenu("Library") { … } }`; on iPad
  this builds the menu bar **(iPadOS 26)**. Shortcuts: `.keyboardShortcut("f")`.
  UIKit: `UIKeyCommand` and `buildMenu(with:)`.
- Focus: `@FocusState`, `.focusable()`, `.focusSection()`; check Tab order.
- Pointer: `.hoverEffect(.highlight)`, `.onHover { }`; UIKit `UIPointerInteraction`.
- Context menus: `.contextMenu { … }` (works with long-press and secondary click);
  UIKit `UIContextMenuInteraction`.
- Drag & drop: `.draggable(_:)` / `.dropDestination(for:action:)`; UIKit
  `UIDragInteraction` / `UIDropInteraction`.
- Pencil: PencilKit for drawing; `.onPencilDoubleTap`, `.onPencilSqueeze` (iOS 17.5+).

## Phase 7 — Multi-window

- Enable multiple scenes: `UIApplicationSceneManifest` →
  `UIApplicationSupportsMultipleScenes = YES`; SwiftUI `WindowGroup` supports it.
- "Open in new window": `openWindow(value:)` with `WindowGroup(for: Item.ID.self)`.
- Never keep per-window UI state in singletons.

## Testing

- iPad simulators (iPad mini through 13-inch) with windowed apps and Stage Manager;
  resize windows by dragging.
- Xcode previews with different `.previewDevice` and traits; Xcode 27's Device Hub can
  resize iPhone app windows to test the iOS 27 behavior.
- Hardware keyboard: Simulator → I/O → Keyboard → Connect Hardware Keyboard.
