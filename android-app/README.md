# BOW BOW — Android app

Native Kotlin app for BOW BOW, mohamed's personal AI voice assistant.
**Source-only scaffold — not yet built.** There is no Android SDK on the
build VM, so the first build happens on his PC in Android Studio.

## What it does

- **Voice:** tap the big mic, speak in Tamil / English / Tanglish
  (SpeechRecognizer, `ta-IN` first), the reply is spoken back with
  Text-to-Speech (Tamil voice if the phone has it, else English).
- **Chat:** the conversation shows as bubbles; a text box is the
  fallback when voice is not convenient.
- **Backend:** every message goes to `POST /chat` on the BOW BOW
  FastAPI backend (`backend/main.py`). The backend holds all keys —
  **no API key lives in the app.**
- **Notifications:** no Firebase push — the app checks the backend
  itself every 15 minutes (`ReminderWorker`, WorkManager) and shows
  due reminders as local notifications, marking each "sent" on the
  backend so it is only shown once. Tasks waiting on your approval
  are notified once each. See "Notifications without Firebase" below.

## Build it (on his PC / laptop)

1. Install **Android Studio** (Ladybug or newer) with JDK 17 (bundled).
2. Open this folder (`android-app/`) as the project. Let Android
   Studio sync Gradle — it downloads the Gradle 8.7 wrapper and the
   SDK pieces itself on first run.
3. **Backend address:** run the backend (see `backend/README.md` or
   `backend/main.py`) and set its address in the app: gear icon →
   backend URL. Defaults:
   - Emulator: `http://10.0.2.2:8000` (reaches the host PC's
     localhost).
   - Real phone: the backend's LAN address (e.g.
     `http://192.168.1.5:8000`) or the hosted URL once deployed.
4. Connect the moto g35 5G over USB (enable USB debugging), pick it
   in Android Studio, press **Run**.

## Files

| Path | What it is |
|---|---|
| `app/src/main/java/com/irshad/bowbow/MainActivity.kt` | The whole screen: mic, chat list, TTS, Settings |
| `app/src/main/java/com/irshad/bowbow/BowBowApi.kt` | OkHttp client: `chat()`, `listReminders()`, `markReminderSent()`, `listTasksAwaitingApproval()` |
| `app/src/main/java/com/irshad/bowbow/ReminderWorker.kt` | 15-minute poller: due reminders + tasks awaiting approval → local notifications |
| `app/src/main/java/com/irshad/bowbow/ChatAdapter.kt` | Two-sided chat bubbles |
| `app/src/main/java/com/irshad/bowbow/Prefs.kt` | User UUID + backend-URL storage |
| `app/src/main/AndroidManifest.xml` | Permissions, MainActivity |
| `app/src/main/res/layout/activity_main.xml` | Main screen layout |
| `app/src/main/res/layout/item_message.xml` | One chat bubble |

## Still missing before first run

- A reachable backend: local run on the PC, or a hosted deployment
  (Phase 12). The emulator default only works from the emulator.
  Notifications also need the backend reachable — the poller cannot
  check what it cannot reach.
- A real launcher icon — the manifest uses a system placeholder;
  generate one in Android Studio (right-click `res` → New → Image
  Asset) before release.

## Notifications without Firebase

Firebase was dropped on 2026-11-09 (its console demanded two-step
verification on the Google account, and push was its only job). The
replacement is pull-based:

- `MainActivity` schedules `ReminderWorker` as a unique periodic
  WorkManager job (15 minutes, network required, `KEEP` policy).
- The worker fetches `GET /reminders?status=pending`, shows a local
  notification for each reminder whose `scheduled_at` has passed,
  then `PATCH /reminders/{id}` `{"status":"sent"}` so it is shown
  exactly once (marked sent *before* notifying, so a retry never
  duplicates it). It also fetches `GET /tasks?status=awaiting_approval`
  and notifies once per task (tracked in SharedPreferences).
- **Caveats, stated plainly:** a reminder can arrive up to ~15 minutes
  late; Android's Doze / battery optimisation can delay background
  work further when the phone sleeps, and force-stopping the app stops
  the checks entirely. Notifications work only while the backend is
  reachable from the phone. When the app is open, everything is
  instant — only the background alerts are delayed.
- No `google-services.json` is needed anymore, and no Firebase project
  exists for BOW BOW. The backend's `POST /notify` and
  `POST /users/fcm-token` endpoints were removed together with
  Firebase — the app never calls them.

## Notes / limits

- Tamil speech recognition depends on the Google speech services on
  the phone (Google app + Tamil language pack). If Tamil is missing,
  it falls back to the phone's default language.
- Phone calls (Phase 11) are not in the app yet — the backend has a
  `request_phone_call` tool, but it only asks for confirmation; the
  actual call path is still to be built.
- Sending email / deleting / paying always need mohamed's confirmation
  first — the app never does those on its own.
