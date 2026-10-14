# r17e-android

M0 spike of the R17E guide app (Kotlin + Jetpack Compose, package `com.r17e.guide`, minSdk 26).

**Guide-only:** the app reads the screen and points at buttons. It never calls `performAction` or `dispatchGesture`.

## What's in the spike
- `a11y/GuideAccessibilityService` – listens for window/content/scroll/click/focus events, debounces 150 ms (immediate on window state change), builds a compact snapshot and logs it to logcat tag `R17E_SNAP`.
- `a11y/SnapshotBuilder` + `Snapshot` – copies nodes into plain data classes (never keeps `AccessibilityNodeInfo`). JSON: `pkg, screen, w, h, nodes[{id, r, txt, desc, vid, b, c, e, pw}]`. Password text omitted, capped at 150 nodes, stable hash IDs.
- `overlay/HighlightOverlay` – `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_TOUCHABLE|FLAG_NOT_FOCUSABLE`, pulsing ring + caption. Re-anchors on each snapshot, hides if the node disappears.
- `overlay/DebugBubble` – draggable blue "?" bubble. Tap it to list current clickable nodes; tap one to ring it.
- `ui/MainActivity` – plain-language disclosure and a button to open Accessibility settings.

## Build
Requires Android SDK (platform android-36.1) and JDK 17+ (Android Studio's bundled JBR works).

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # gitignored
./gradlew assembleDebug
```

## Run
```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.r17e.guide/.ui.MainActivity
```
Open Accessibility settings from the app, enable **R17E Guide**, then open any app (e.g. WhatsApp, Settings).

Watch snapshots:
```bash
adb logcat -s R17E_SNAP R17E
```
Tap the blue **?** bubble → pick a node → a pulsing ring appears over it.

## CI and debug releases
`.github/workflows/android-debug.yml` builds `assembleDebug` on every push and PR and uploads the APK as an artifact (`r17e-android-debug-<sha>`).
Pushing a tag like `v0.1.0-debug` also publishes a GitHub **prerelease** with `r17e-android-<tag>-debug.apk`.

Debug APKs from CI and local builds share one signature: CI decodes the `DEBUG_KEYSTORE_BASE64` secret, and locally set
`r17e.debugKeystore=/path/to/r17e-debug.keystore` in `~/.gradle/gradle.properties`. Without it, the default Android debug key is used.
The keystore is never committed.
