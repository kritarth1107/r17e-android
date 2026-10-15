# r17e Android v0.3.0-debug (prerelease, not yet published)
Guide-only voice helper for elders: it shows where to tap and never taps for you.

## Redesign
- Material 3 design system: one calm blue on neutral surfaces, full dark mode, large type (body 18–20sp), 8dp grid, subtle motion.
- Material Symbols icons everywhere; no emoji in the UI.
- Redesigned onboarding (7 steps with a step bar), Home (status hero for off / getting ready / listening / speaking / reconnecting / offline), family card, and a new Settings screen (helper, screen helper, readiness, language, family, privacy, about).
- Overlay: vector-icon bubble, blue guide ring with halo and a "Next step" caption card, and a visual scam-warning card.
- Elder accessibility: 48dp+ touch targets, WCAG AA contrast checked by unit tests in light and dark, TalkBack labels, headings and a live status line, works at 1.3x font.

## Reliability
- "Helper is OFF" glitch fixed: a rebind race no longer clears the state; verified after adb reinstall, process kill and reboot.
- The bubble hides only while R17E itself is open (lifecycle-based), and the scam card is dismissed when you return to the app.
- Network: reconnects with backoff, falls back to offline help, then probes every 30 s and on network-available; resumes the same session (2.3 s measured).
- Expired login tokens are refreshed once for family link, recipes and safety alerts (live sessions already did this).
- Idle auto-stop after 10 minutes of silence, with a note on Home.
- Soak: a 34-minute session (wall clock longer due to host sleep) with 10 minutes of forced deep Doze kept the same process and foreground service; memory flat at ~118–126 MB; goAway and drops resumed the session (connect 1.5–4.2 s).

## Voice
- Faster connect (~6 s before, 2.3–2.8 s after). Reply latency median ~1.7 s.
- Local barge-in: playback ducks within one 100 ms mic chunk when you start talking (server-confirmed barge-in ~550 ms, bound by server VAD).

## Safety
- Spoken scam warning while the helper runs (rate-limited: 5 min per app, 60 s overall), shown as a card too; family alert only if linked.

## Language
- Hindi and Hinglish use one consistent polite form (-इए / -iye); gender-neutral agreement; copy matches the blue ring.

## Tests and CI
- Unit tests: 38 (v0.2) → 65. Instrumented tests: 0 → 10 (screens render, every clickable labelled, 48dp targets, onboarding, Settings, Hindi home, no emoji, bubble foreground flag).
- CI runs the instrumented tests on a free KVM emulator (API 34) next to build, unit tests and lint.

Screenshots: before/after redesign (light, dark, 1.3x font) and v0.3 walk-through.
See docs/TESTING.md and docs/KNOWN_ISSUES.md.

APK: r17e-android-v0.3.0-debug.apk, built from 86e6c90, signed with the shared debug key (same certificate as v0.2.0-debug, so it installs as an update).
APK SHA-256: e7d28dc80495b3417afe40fc0c4dc018ab5f026770fd46a9d9971a1d68a4e38f
