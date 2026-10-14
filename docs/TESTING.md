# Testing r17e Android v0.2.0-debug

## Install
1. Install `r17e-android-v0.2.0-debug.apk` (it talks to the prod backend by default).
   To use a local backend instead, build with `./gradlew assembleDebug -Pr17e.backendUrl=http://10.0.2.2:8080`.
2. Open **R17E Helper** and follow the onboarding: language, how it works, microphone, notifications, accessibility, family code, ready.
   - On Android 13+ sideloads, accessibility may say "Restricted setting". Open App info, then the ⋮ menu, then **Allow restricted settings**, and turn the helper on again.

## Voice guidance
- Tap **Start** (or the floating bubble). The orb turns green while listening and blue while speaking.
- Try asking "Bluetooth kaise on karein?" or "awaaz badhani hai". A ring highlights the control to press. The app never taps anything for you.

## Family link
1. A caregiver creates an elder invite code in the R17E family dashboard.
2. Enter it in onboarding step 5 or on the Home "Family se connect karein" card. Codes are case- and dash-insensitive.
3. You should see "Connected hai: <group name> ✓".

## Safety alerts (needs a linked family)
- Open a scam-like page with a KYC/OTP/UPI PIN form. The caregiver's `GET /v1/alerts` should show `other/high` (scam risk) and `otp_request/medium` (PIN screen).
- Alerts hold only a fixed message plus metadata (flag, app package, time). Screen text and OTPs are never sent.
- While offline, alerts wait in a queue (up to 200) and are sent on the next connection. The same alert kind for the same app is sent at most once every 10 minutes.

## Debug hooks (debug builds only)
```
adb shell am broadcast -a com.r17e.guide.DEBUG_SAY --es text "'how do I turn on bluetooth'"
adb shell am broadcast -a com.r17e.guide.DEBUG_SAY --es audio q.wav   # file in /sdcard/Android/data/com.r17e.guide/files/
adb shell am broadcast -a com.r17e.guide.DEBUG_SAY --ez drop true     # simulate a dropped connection and resume
```
