#!/usr/bin/env bash
# adaptive-ui red-flag scan.
# Usage: bash audit.sh <project-root>
# Prints grouped hits (file:line: text). Hits are leads to inspect, not verdicts.

set -u
ROOT="${1:-.}"
if [ ! -d "$ROOT" ]; then echo "Not a directory: $ROOT" >&2; exit 1; fi

if command -v rg >/dev/null 2>&1; then
  search() { rg -n --no-heading -S -g '!**/build/**' -g '!**/.gradle/**' -g '!**/Pods/**' -g '!**/DerivedData/**' -g "$1" -e "$2" "$ROOT" 2>/dev/null; }
else
  search() { grep -rnE --include="$1" --exclude-dir=build --exclude-dir=.gradle --exclude-dir=Pods --exclude-dir=DerivedData -e "$2" "$ROOT" 2>/dev/null; }
fi

TOTAL=0
section() {
  local title="$1" glob="$2" pattern="$3"
  local out
  out="$(search "$glob" "$pattern")"
  if [ -n "$out" ]; then
    local n; n=$(printf '%s\n' "$out" | wc -l | tr -d ' ')
    TOTAL=$((TOTAL + n))
    printf '\n### %s (%s)\n%s\n' "$title" "$n" "$out"
  fi
}

echo "# adaptive-ui audit: $ROOT"

HAS_ANDROID=$(find "$ROOT" -name 'AndroidManifest.xml' -not -path '*/build/*' 2>/dev/null | head -1)
HAS_IOS=$(find "$ROOT" \( -name '*.swift' -o -name 'Info.plist' \) -not -path '*/Pods/*' 2>/dev/null | head -1)

if [ -n "$HAS_ANDROID" ]; then
  echo; echo "## Android / Compose"
  section "Orientation / resizability locks (manifest)" "AndroidManifest.xml" 'screenOrientation|resizeableActivity="false"|maxAspectRatio|minAspectRatio'
  section "Orientation set in code" "*.kt" 'requestedOrientation|SCREEN_ORIENTATION_'
  section "Tablet / device-type checks" "*.kt" 'isTablet|smallestScreenWidthDp|Build\.MODEL|Build\.DEVICE|SCREENLAYOUT_SIZE'
  section "Whole-screen metrics used for layout" "*.kt" 'displayMetrics|DisplayMetrics|getRealMetrics|defaultDisplay|currentWindowMetrics|screenWidthDp|screenHeightDp'
  section "Device orientation used for layout" "*.kt" 'configuration\.orientation|ORIENTATION_PORTRAIT|ORIENTATION_LANDSCAPE'
  section "Old/legacy size-class APIs (check version)" "*.kt" 'calculateWindowSizeClass|WindowWidthSizeClass|currentWindowAdaptiveInfo\('
  section "Full-width bottom sheets / full-screen dialogs" "*.kt" 'ModalBottomSheet|usePlatformDefaultWidth\s*=\s*false'
  section "Possible fixed phone widths" "*.kt" '\b(width|requiredWidth|size)\((3[2-9][0-9]|4[0-2][0-9])\.dp'
  section "Pause-in-onPause (multi-resume risk)" "*.kt" 'override fun onPause'
  section "configChanges declarations (review density handling)" "AndroidManifest.xml" 'configChanges'
  section "Camera APIs (check preview on foldables)" "*.kt" 'Camera2|CameraDevice|CameraManager|androidx\.camera'
fi

if [ -n "$HAS_IOS" ]; then
  echo; echo "## iOS / SwiftUI / UIKit"
  section "UIRequiresFullScreen (deprecated iPadOS 26)" "*.plist" 'UIRequiresFullScreen'
  section "Supported orientations (check iPad has all four)" "*.plist" 'UISupportedInterfaceOrientations'
  section "Idiom checks used for layout" "*.swift" 'userInterfaceIdiom|\.pad\b|isPad|isIPad'
  section "Screen bounds used for layout" "*.swift" 'UIScreen\.main|\.screen\.bounds|nativeBounds'
  section "Device orientation used for layout" "*.swift" 'UIDevice\.current\.orientation|statusBarOrientation|interfaceOrientation'
  section "Orientation locks in code" "*.swift" 'supportedInterfaceOrientations|shouldAutorotate|requestGeometryUpdate'
  section "Size-class driven layout (fine for semantics, not breakpoints)" "*.swift" 'horizontalSizeClass|verticalSizeClass'
  section "Full-screen covers" "*.swift" 'fullScreenCover|\.overFullScreen|\.fullScreen\b'
  section "Possible fixed phone widths" "*.swift" '(width|minWidth|maxWidth):\s*(3[2-9][0-9]|4[0-2][0-9])\b'
  section "Multiple scenes setting" "*.plist" 'UIApplicationSupportsMultipleScenes'
  if [ -z "$(search "*.plist" 'UILaunchScreen|UILaunchStoryboardName')" ]; then
    printf '\n### Missing launch screen (required for resizable scenes; App Store requires it from iOS 27) (1)\nNo UILaunchScreen / UILaunchStoryboardName found in any Info.plist (check build settings too)\n'
    TOTAL=$((TOTAL + 1))
  fi
  section "Centered custom placement (lands on the iPhone Duo fold)" "*.swift" '\.position\(|\.center\b|midX|frame\.width\s*/\s*2|bounds\.width\s*/\s*2'
  section "Hinge angle used (should not drive layout)" "*.swift" 'onHingeChange|UIHingeInteraction'
fi

if [ -z "$HAS_ANDROID" ] && [ -z "$HAS_IOS" ]; then
  echo "No Android manifest or Swift sources found. This skill covers Compose and SwiftUI/UIKit."
fi

echo; echo "Total hits: $TOTAL (inspect each in context)"
