# r17e Android v0.2.0-debug (prerelease)
Guide-only voice helper for elders: it shows where to tap and never taps for you.

## New
- Elder-friendly Compose UI: large text, high contrast, Hindi / English / Hinglish.
- Step-by-step onboarding for every permission, including the restricted-settings hint.
- Floating helper bubble: tap to start or stop, drag to an edge. Its colour shows listening, speaking, reconnecting or offline.
- Points at the prod backend by default.
- Live session resumption, plus automatic reconnects with backoff (1/2/4/8 s) before falling back to offline.
- Offline recipes still ring the right control without a connection.
- **Family link:** enter a caregiver's invite code (`POST /v1/family/link`) in onboarding or on Home.
- **Safety alerts to family** (`POST /v1/safety-events`): scam message, PIN/OTP screen, payment screen and remote-access app.
  Alerts carry a fixed message and metadata only; screen text and OTPs are never sent. They are queued while offline and de-duplicated for 10 minutes.
- Fixed: the false "Helper is OFF" on Home.

Verified on prod: voice in Hindi and English, resumption after a drop, family link, and safety alerts (metadata only, no OTP present).
See docs/TESTING.md and docs/KNOWN_ISSUES.md.

APK SHA-256: 5481f3ab529c05cc5a4bb48e7a800d40689747240403e7e726b4953ee58f10e2
