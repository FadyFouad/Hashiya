# Android official guidelines (Adaptive app quality + Material 3)

Reviewed: 2026-10-02. Sources at the bottom. Guideline IDs are Google's own; cite them
in the audit report (e.g. "violates **Config_Changes**").

Contents: Tiers · Tier 3 · Tier 2 · Tier 1 (foldables) · Material 3 layout ·
Test devices · Sources

---

## Tiers

| Tier | Name | Meaning |
| --- | --- | --- |
| 3 | Adaptive ready | Runs full screen / full window everywhere, not letterboxed, no compatibility mode; critical flows work; basic keyboard, mouse, trackpad, stylus. |
| 2 | Adaptive optimized | Layouts optimized for every size and configuration + enhanced external input. **Default target of this skill.** |
| 1 | Adaptive differentiated | Experience designed per device: multitasking, foldable postures, drag & drop, stylus. Organized by "experiences" (Desktop, Foldables, Camera•Audio, Stylus…). |

Google notes most apps don't need every requirement: implement those that fit the
app's use cases. In the audit, mark non-applicable items "N/A — <reason>".

## Tier 3 — Adaptive ready (requirements)

| ID | Requirement (paraphrased) | Skill phase |
| --- | --- | --- |
| **Config_Changes** | Fills the available area (screen or window); content doesn't overflow; not letterboxed, not in compatibility mode. Keeps/restores state through rotation, fold/unfold, resizing in split-screen and desktop windowing: scroll position, typed text + keyboard state, media position. | 1, 4 |
| **Config_Combinations** | Survives combinations: resize then rotate, rotate then fold/unfold, etc. | 4 |
| **Multi-Window_Functionality** | Fully functional in multi-window mode. | 7 |
| **Multi-Resume** | Keeps updating UI when not the focused app (media keeps playing, new messages appear, progress updates); releases and regains exclusive resources (camera, mic). | 7 |
| **Camera_Preview** | Camera preview correctly oriented and proportioned in portrait, landscape, folded, unfolded, multi-window. | 5 |
| **Media_Projection** | Same as above for media projection. | 5 |
| **Keyboard_Input** | Text input via external keyboard; switches physical ↔ virtual keyboard without relaunch. | 6 |
| **Mouse_Trackpad_Basic** | Click every clickable element, select (radio, checkbox, text by drag/double-click), scroll lists/pickers both directions. | 6 |
| **Stylus_Basic** | Stylus can select, manipulate, scroll (automatic for standard views). | 6 |
| **Stylus_Text_Input** | Android 14+: handwriting into text fields (automatic for EditText; check custom fields). | 6 |

Test note from Google: test on a large-screen device (sw ≥ 600dp) with Android 12+,
which allows all orientations and multi-window even when the manifest restricts them.

## Tier 2 — Adaptive optimized (requirements)

| ID | Requirement (paraphrased) | Skill phase |
| --- | --- | --- |
| **Responsive_adaptive_layouts** | All layouts responsive; adaptive layouts chosen by window size classes. May include: leading-edge nav rail expanding into a full panel on larger windows, grids that change column count, text in columns, trailing panels open by default on desktop sizes. Multi-pane via canonical layouts; in Compose use `ListDetailPaneScaffold` etc.; legacy activity apps use Activity embedding. | 2, 3 |
| **UI_Secondary_Elements** | Bottom sheets not full width (apply max width); buttons not full width; text fields not stretched; small menus/modals don't cover the screen; context menus appear next to the selected item; **nav rails replace nav bars** on large screens; **nav drawers become expanded nav rails**; dialogs use the current Material component; images not stretched or cropped. | 2, 3 |
| **Touch_Targets** | ≥ 48dp, not obscured, at every size and configuration. | 8 |
| **Drawable_Focus** | Interactive custom drawables are focusable (non-touch mode) with a visible focus state. | 6 |
| **Keyboard_Navigation** | Main task flows navigable with Tab and arrow keys. | 6 |
| **Keyboard_Shortcuts** | Shortcuts for common actions: select, cut, copy, paste, undo, redo. | 6 |
| **Keyboard_Media_Playback** | Keyboard controls playback (Space = play/pause). | 6 |
| **Keyboard_Send** | Enter = send in communication apps. | 6 |
| **Keyboard_Exit** | Esc closes modals/dialogs/menus, clears search, cancels focus, exits full screen/PiP, deselects, aborts edits. | 6 |
| **Context_Menus** | Context menus open with right-click / secondary tap. | 6 |
| **Content_Zoom** | Ctrl + scroll wheel and trackpad pinch zoom content (where content is zoomable). | 6 |
| **Hover_States** | Actionable elements show hover states where appropriate. | 6 |

## Tier 1 — Foldables experience (requirements)

| ID | Requirement (paraphrased) | Skill phase |
| --- | --- | --- |
| **Foldables_Postures** | Supports postures and their use cases: tabletop for video calls and playback; book for long reading. | 5 |
| **Foldables_Camera** | Camera apps adjust preview for folded/unfolded and support front/back screen preview. | 5 |
| **Foldables_Multitasking_Scenarios** | PiP enter/exit in all states and orientations; messaging apps open attachments in a separate window. | 7 |
| **Foldables_PiP** | Interactive PiP with custom controls. | 7 |
| **Foldables_Multi-Instance** | Can launch multiple instances in separate windows, folded and unfolded. | 7 |

Other Tier 1 experiences (Desktop, Camera•Audio, Stylus) exist; open them only when the
app is in that category.

## Material 3 layout guidance

Breakpoints (M3 now calls window size classes "breakpoints"):

| Breakpoint | Width | Panes | Navigation |
| --- | --- | --- | --- |
| Compact | < 600dp | 1 | Navigation bar |
| Medium | 600–839dp | 1 recommended, 2 possible | Nav bar or modal expanded nav rail per M3 table; Android Tier 2 asks for a rail on large screens → prefer a (collapsed) rail |
| Expanded | 840–1199dp | 2 recommended | Rail (collapsed, or standard/modal expanded) — never a bar |
| Large | 1200–1599dp | 2 recommended | Standard expanded rail |
| Extra-large | ≥ 1600dp | up to 3 | Standard expanded rail |

- **Navigation drawer is no longer recommended** in Material 3 Expressive; use the
  **expanded navigation rail** instead.
- Medium: single pane recommended; use two panes only for low-density content such as
  settings, each pane 50% width.
- Margins: **16dp compact, 24dp medium and up**; spacer between panes **24dp** (holds a
  drag handle when panes are resizable).
- Height classes matter too (short windows: collapse top app bars, move controls beside
  content).

## Test devices Google lists

At minimum: foldable 841×701dp, 8" tablet 1024×640dp, 10.5" tablet 1280×800dp,
13" Chromebook 1600×900dp. Emulators: 7.6" fold-in with outer display, Pixel C tablet,
Surface Duo (dual display), plus the Resizable emulator.

## Sources

- Adaptive app quality overview — https://developer.android.com/docs/quality-guidelines/adaptive-app-quality (updated 2026-04-10)
- Tier 3 — https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/tier-3 (updated 2026-08-18)
- Tier 2 — https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/tier-2 (updated 2026-08-18)
- Tier 1 Foldables — https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/experiences/foldables (updated 2026-08-18)
- M3 Breakpoints — https://m3.material.io/foundations/layout/breakpoints/overview and /medium
- M3 Navigation rail — https://m3.material.io/components/navigation-rail/guidelines
- M3 Navigation drawer — https://m3.material.io/components/navigation-drawer/overview
