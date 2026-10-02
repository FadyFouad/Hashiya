# Jetpack Compose — how to do each phase

APIs marked **(2026)** are recent; confirm exact signatures in the official docs
(developer.android.com/develop/adaptive-apps) before using them.

Contents: Dependencies · Phase 1 · 2 · 3 · 4 · 5 · 6 · 7 · Testing

---

## Dependencies

```kotlin
// Material 3 adaptive (window info, pane scaffolds, folding features)
implementation("androidx.compose.material3.adaptive:adaptive")
implementation("androidx.compose.material3.adaptive:adaptive-layout")
implementation("androidx.compose.material3.adaptive:adaptive-navigation")
// Adaptive navigation suite (bar ↔ rail ↔ drawer)
implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
// Window size classes incl. Large / Extra-large (WindowManager 1.5+)
implementation("androidx.window:window:1.5.0")
```
Prefer the project's Compose BOM for versions; check the latest stable releases.

## Phase 1 — Foundations

**Remove locks.** In `AndroidManifest.xml`, delete `android:screenOrientation`,
`android:resizeableActivity="false"`, `android:maxAspectRatio`, `android:minAspectRatio`.
In code, remove `requestedOrientation = …`.

Why it is mandatory: for apps targeting API 36, the system ignores orientation,
resizability and aspect-ratio restrictions on displays with smallest width ≥ 600dp, and
letterboxing is no longer applied. Play requires target API 36 from August 2026.

If one screen genuinely needs portrait on phones (e.g. a camera capture flow), restrict
it on compact windows only — see Android's cookbook recipe "App orientation restricted
on phones but not on large screens". Do not use `nosensor` as a workaround.

**Central breakpoints.** One source of truth, read from the current window:

```kotlin
@Composable
fun rememberLayoutClass(): LayoutClass {
    val wsc = currentWindowAdaptiveInfoV2().windowSizeClass
    return when {
        wsc.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) -> LayoutClass.Expanded
        wsc.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)   -> LayoutClass.Medium
        else -> LayoutClass.Compact
    }
}
```
Add Large/Extra-large (1200 / 1600) only if the app has a use for them.

Height: `wsc.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND)` etc. — use it to
collapse top bars, move controls beside content, or shrink headers on short windows.

**Red flags to remove:** `resources.configuration.smallestScreenWidthDp >= 600` as an
"is tablet" flag, `Build.MODEL` checks, `DisplayMetrics` of the whole screen,
`LocalConfiguration.current.orientation` used to pick layouts (orientation of the
*window* is fine for minor tweaks; never the device rotation).

**Inside a component**, prefer `BoxWithConstraints` (or Layout/measure) over global
window info: a component inside a pane should react to the pane's width.

**(2026)** The `MediaQuery` API lets UI query window size, posture and keyboard type
declaratively; `Grid` and `FlexBox` layout containers handle spans and wrapping. Use
them when the project's Compose version has them (BOM 2026.04.01+).

## Phase 2 — Navigation

```kotlin
NavigationSuiteScaffold(
    navigationSuiteItems = {
        destinations.forEach { d ->
            item(selected = d == current, onClick = { navigate(d) },
                 icon = { Icon(d.icon, null) }, label = { Text(d.label) })
        }
    }
) { AppContent() }
```
It picks bar / rail / drawer from window info. Material 3 Expressive no longer
recommends the navigation drawer: on large widths prefer the expanded rail (check the
navigation-suite version for a `WideNavigationRail` / expanded-rail type) and never a
bottom bar at expanded+ (Tier 2 UI_Secondary_Elements). Override the type with
`NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(...)` only if the design
needs it. Keep navigation state in the NavController / ViewModel, not inside the
scaffold branch, so switching presentation does not reset it.

## Phase 3 — Layouts

**List–detail:**
```kotlin
val navigator = rememberListDetailPaneScaffoldNavigator<ItemId>()
NavigableListDetailPaneScaffold(
    navigator = navigator,
    listPane = { AnimatedPane { ItemList(onClick = { id ->
        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) } }) } },
    detailPane = { AnimatedPane {
        navigator.currentDestination?.contentKey?.let { ItemDetail(it) } ?: EmptyDetail() } }
)
```
It shows one or two panes based on space, handles back, and avoids the hinge.
`SupportingPaneScaffold` follows the same pattern for main + supporting content.
Older versions name things slightly differently (`ListDetailPaneScaffold` +
`navigator.scaffoldDirective`); match the project's library version.

**Spacing:** Material margins 16dp compact, 24dp medium+, 24dp spacer between panes.
Material recommends a single pane at medium; the default pane scaffold directive
follows this, so don't force two panes at 600dp unless the content is low density.

**Grids:** `LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 180.dp))`.

**Max width:** `Modifier.widthIn(max = 840.dp).fillMaxWidth()` inside a centered Box.
Same for buttons (`widthIn(max = …)`) and text fields.

**Dialogs/sheets:** on medium+ use `Dialog`/`AlertDialog` or a side sheet instead of a
full-width `ModalBottomSheet`; if keeping the bottom sheet, constrain its width
(`sheetMaxWidth`).

## Phase 4 — Continuity

- UI state in `ViewModel` (survives config changes) or `rememberSaveable`.
- Selected item for list–detail lives in the navigator / ViewModel, so after unfold
  the detail pane shows the same item.
- `rememberLazyListState()` is saveable — keep it hoisted above layout branches so
  switching between one- and two-pane layouts does not recreate it.
- Folding triggers config changes including `screenSize`, `smallestScreenSize`,
  `screenLayout`, `orientation` and `density`. Density changes restart the activity by
  default; if the app holds non-saveable state (custom views, bitmaps), either handle
  `density` in `android:configChanges` and update resources in
  `onConfigurationChanged`, or make sure all state is restorable.

## Phase 5 — Foldables

- `currentWindowAdaptiveInfoV2().windowPosture` → `isTabletop`, hinge bounds.
- `collectFoldingFeaturesAsState()` (Material 3 adaptive) or Jetpack WindowManager
  `WindowInfoTracker.getOrCreate(context).windowLayoutInfo(activity)` →
  `FoldingFeature` with `state` (FLAT / HALF_OPENED), `orientation`, `isSeparating`,
  `occlusionType`, `bounds`.
- Tabletop (HALF_OPENED + horizontal fold): content above the fold, controls below.
- Never derive orientation from device rotation; use the window bounds
  (`WindowMetrics`/`Configuration.orientation`). Landscape-first foldables have
  rotation 0 = landscape on the inner display.
- Camera: use CameraX `PreviewView` (handles sensor orientation, rotation, scaling);
  for existing Camera2 code use `CameraViewfinder`.
- Rear display / dual-screen modes exist (WindowManager `WindowAreaController`); only
  for apps with a real use (selfie preview with rear camera, etc.).

## Phase 6 — Input

- Keyboard: `Modifier.onPreviewKeyEvent { … }` / `onKeyEvent` for shortcuts
  (check `it.isCtrlPressed`, `it.key == Key.F`). Expose shortcuts to the system
  shortcut helper via `Activity.onProvideKeyboardShortcuts`.
- Focus: `Modifier.focusable()`, `FocusRequester`, `focusProperties { next = … }`;
  check Tab order matches visual order.
- Hover: `Modifier.hoverable(interactionSource)` + `collectIsHoveredAsState()`;
  cursor: `Modifier.pointerHoverIcon(PointerIcon.Hand)`.
- Secondary click: detect in `pointerInput` (`event.buttons.isSecondaryPressed`) and
  show a `DropdownMenu` as context menu.
- Drag & drop: `Modifier.dragAndDropSource` / `Modifier.dragAndDropTarget`.
- Stylus: `Modifier.pointerInput` with `PointerType.Stylus`; handwriting in text
  fields is automatic on supported devices.

## Phase 7 — Multi-window

- Test in split screen and desktop windowing; avoid pausing work in `onPause`
  (multi-resume: several apps are resumed at once) — use `onStop` for heavy pauses.
- Multiple instances: `android:resizeableActivity="true"` (default) and, if wanted,
  `FLAG_ACTIVITY_MULTIPLE_TASK` / `documentLaunchMode` for "open in new window".
- Minimum size: `<layout android:minWidth=… android:minHeight=…>` in the manifest
  (honored in freeform windowing).

## Testing

- Emulators: Resizable (switch phone/foldable/tablet/desktop live), Pixel Tablet,
  Pixel Fold, and other foldable profiles in Device Manager. Extended controls →
  Virtual sensors → fold posture.
- Compose UI tests: `DeviceConfigurationOverride.ForcedSize(DpSize(…))` to run a test
  at several window sizes; `@Preview(device = Devices.TABLET / FOLDABLE / …)` or
  `@PreviewScreenSizes` for visual checks.
- Android Studio "Layout Validation" for side-by-side preview at many sizes.
