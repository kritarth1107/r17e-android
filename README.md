# r17e-android

R17E guide app: a voice helper for older people in India (Kotlin + Jetpack Compose, package `com.r17e.guide`, minSdk 26).

**Guide-only:** the app reads the screen and points at buttons. It never calls `performAction` or `dispatchGesture`; `GuideOnlyTest` fails CI if either appears in `src/main`.

## How it works
1. **Device auth** (`backend/`): an EC P-256 key in the Android Keystore is registered with `POST /v1/devices/register`; access tokens come from signing the `/v1/auth/challenge` message (`/v1/auth/device/token`) and rotate via `/v1/auth/refresh`.
2. **Live credentials**: `POST /v1/live/session` returns a short-lived Vertex token, model resource and websocket URL (`LiveTokenProvider`); the session is closed with `/v1/sessions/:id/end`.
3. **Gemini Live** (`live/`): `LiveSession` opens the Vertex BidiGenerateContent websocket; `AudioIO` streams 16 kHz mic PCM (echo cancel + noise suppress) and plays 24 kHz replies with barge-in. `GuideSessionService` is a microphone foreground service so it keeps running while the user is in another app.
4. **Overlay from tool calls** (`session/ScreenTools`): the model gets `get_screen`, `highlight_node(node_id, caption)`, `clear_highlight` and `find_recipe`. Highlights only accept ids from the latest/live snapshot. There is no tap/type/scroll tool.
5. **Safety** (`safety/SafetyPolicy`): redacts password/OTP/PIN/CVV fields and long digit runs before snapshots leave the phone; flags PIN-entry, payment, scam-text and remote-access-app screens; refuses to point at secret fields or PIN keypad digits; asks for spoken confirmation before Pay.
6. **Recipes + offline** (`recipes/`, `offline/`): `assets/recipes.json` has English/Hindi step guides. If Live can't start, `OfflineGuide` uses on-device speech recognition + TTS to pick a recipe and points at each step as the screen changes.

### Play Integrity
Registration sends a classic Play Integrity token (nonce = base64url(SHA-256(`installId:publicKey`))) when the GCP
project **number** is set via `r17e.gcpProjectNumber` (committed in `gradle.properties`: 261222057477, project r17e-live-xsvbu; override with `-P`). Without it the
build warns and registration skips the token (the backend currently records `not_checked`).

### Backend URL
Defaults to `http://10.0.2.2:8080` (emulator → Mac). For a USB phone: `adb reverse tcp:8080 tcp:8080` and build with
`./gradlew assembleDebug -Pr17e.backendUrl=http://localhost:8080`. Cleartext is allowed only for those local hosts.

## M0 spike pieces
- `a11y/GuideAccessibilityService` – listens for window/content/scroll/click/focus events, debounces 150 ms (immediate on window state change), builds a compact snapshot and logs it to logcat tag `R17E_SNAP`.
- `a11y/SnapshotBuilder` + `Snapshot` – copies nodes into plain data classes (never keeps `AccessibilityNodeInfo`). JSON: `pkg, screen, w, h, nodes[{id, r, txt, desc, vid, b, c, e, pw}]`. Password text omitted, capped at 150 nodes, stable hash IDs.
- `overlay/HighlightOverlay` – `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_TOUCHABLE|FLAG_NOT_FOCUSABLE`, pulsing ring + caption. Re-anchors on each snapshot, hides if the node disappears.
- `overlay/DebugBubble` – draggable blue "?" bubble. Tap it to list current clickable nodes; tap one to ring it.
- `ui/MainActivity` – plain-language disclosure and a button to open Accessibility settings.

## Build and test
Requires Android SDK (platform android-36.1) and JDK 17+ (Android Studio's bundled JBR works).

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # gitignored
./gradlew testDebugUnitTest assembleDebug
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

Voice helper: start the backend (`r17e-backend`, `npm run dev`), tap **Start talking to helper**, allow the microphone, and ask e.g. "WhatsApp pe video call kaise karun?". Logs: `adb logcat -s R17E_LIVE R17E_CTL R17E_MIC`.

Emulator testing without a working mic (debug builds only): send a typed turn with
`adb shell "am broadcast -a com.r17e.guide.DEBUG_SAY --es text 'turn on bluetooth'"`, or speech from a file:
`say -o bt.wav --file-format=WAVE --data-format=LEI16@16000 "..."`, `adb push bt.wav /sdcard/Android/data/com.r17e.guide/files/`,
then `adb shell "am broadcast -a com.r17e.guide.DEBUG_SAY --es audio bt.wav"`.

## CI and debug releases
`.github/workflows/android-debug.yml` runs unit tests and builds `assembleDebug` on every push and PR and uploads the APK as an artifact (`r17e-android-debug-<sha>`).
Pushing a tag like `v0.1.0-debug` also publishes a GitHub **prerelease** with `r17e-android-<tag>-debug.apk`.

Debug APKs from CI and local builds share one signature: CI decodes the `DEBUG_KEYSTORE_BASE64` secret, and locally set
`r17e.debugKeystore=/path/to/r17e-debug.keystore` in `~/.gradle/gradle.properties`. Without it, the default Android debug key is used.
The keystore is never committed.
