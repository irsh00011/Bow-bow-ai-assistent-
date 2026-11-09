# Phase 8 — Android app (scaffold)

Date: 2026-11-08 (evening IST). Status: **scaffold complete, NOT built
yet — no Android SDK on this VM; first build is on his PC in Android
Studio.** Committed as `ef05885` (20 files).

## What's in `android-app/`
- Package `com.irshad.bowbow`, minSdk 26, targetSdk 34, compileSdk 34,
  AGP 8.5.2, Kotlin 2.0.20, Gradle 8.7, Material 3.
- `MainActivity.kt` — single screen: big mic button (SpeechRecognizer,
  `ta-IN` first), listening/thinking status, chat bubbles, text input
  fallback, TTS reply (Tamil voice if available, else `en-IN`),
  Settings dialog for the backend URL, runtime permissions
  (RECORD_AUDIO + POST_NOTIFICATIONS), FCM token registration on launch.
- `BowBowApi.kt` — OkHttp + Gson; `chat(message, "auto", userId)` →
  `POST /chat` (parses `reply`), `registerFcmToken(token, userId)` →
  `POST /users/fcm-token`; 60s timeouts; no API keys in the app.
- `MyFirebaseMessagingService.kt` — shows FCM pushes as notifications
  (channel `bowbow_reminders`), registers refreshed tokens.
- `ChatAdapter.kt` (two-sided bubbles), `Prefs.kt` (user UUID
  `5897b826-feff-4dd2-9841-f8d4b510f6bf`, default backend URL
  `http://10.0.2.2:8000` for the emulator, SharedPreferences for the URL).
- Manifest (INTERNET, RECORD_AUDIO, POST_NOTIFICATIONS), layouts,
  Material 3 theme, README with PC build steps. Launcher icon is the
  system placeholder — generate a real one in Android Studio before
  release. Phone calls (Phase 11) are not in the app yet.

## Verified by the builder + spot-checked by me
- Every `R.string.*` / `R.color.*` / `binding.*` reference resolves
  against the XML; MainActivity read end to end (listeners, TTS
  fallback, permission launcher, dialog scoping correct).
- `google-services.json` was NOT created and is NOT tracked
  (`.gitignore` covers it and `firebase-service-account.json`).
- **Not compiled** — first build on his PC. Tamil STT quality depends
  on the Google speech services / Tamil pack on the phone.

## Still missing before first run on his phone
1. A reachable backend URL — the backend currently runs only on this
   VM; hosting decision is Phase 12 (Render free tier is the leading
   candidate). Until then, the app can only talk to the backend over
   the emulator or his PC.
2. Android Studio on his PC + the first build/run on the moto g35 5G.

## Firebase dropped — 2026-11-09 08:19 IST (user's decision)
- Firebase/FCM removed from the app entirely: `MyFirebaseMessagingService`
  deleted, firebase-bom/firebase-messaging and the google-services plugin
  removed from Gradle, FCM code removed from MainActivity and the manifest.
  Zero Firebase references remain in code/Gradle/manifest.
- Replacement: `ReminderWorker.kt` (WorkManager, every 15 min, network
  constraint) polls `GET /reminders?status=pending` and
  `GET /tasks?status=awaiting_approval`, shows local notifications, and
  marks each shown reminder `sent` via `PATCH /reminders/{id}` (marked
  sent *before* notifying, so a retry never double-notifies). Tasks are
  notified once each (IDs in SharedPreferences).
- No `google-services.json` is needed to build anymore. Tradeoff (accepted
  by the user): reminders can arrive up to ~15 min late, and only while
  the app's background work runs — see `documentation/PHASE10-PREP.md`.
