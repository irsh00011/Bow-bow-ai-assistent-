# BOW BOW — Android app

Native Kotlin app for BOW BOW, mohamed's personal AI voice assistant.
Dark theme, voice-first. The backend holds all keys — **no API key
lives in the app.**

## What it does

- **Voice:** tap the big mic, speak in Tamil / English / Tanglish
  (SpeechRecognizer, Tamil first), the reply is spoken back with
  Text-to-Speech (Tamil voice if the phone has it, else English).
  The waveform above the mic animates with your voice; the status
  line shows Listening / Thinking / Speaking.
- **Chat:** the conversation shows as bubbles; a text box is the
  fallback when voice is not convenient.
- **History:** top-bar clock icon — all tasks and reminders from the
  backend, newest first, with a refresh button.
- **Phone call:** top-bar phone icon — asks for the number and the
  message, confirms in-app, then the backend places the call to your
  own number (Twilio trial speaks its sample message; custom text
  needs the Twilio account upgraded).
- **Settings:** backend URL, voice language (Auto/Tamil/English),
  speak-replies on/off, backend status check, version.
- **Backend:** every message goes to `POST /chat` on the BOW BOW
  FastAPI backend (default `https://bow-bow-backend.vercel.app`).
- **Notifications:** no Firebase push — the app checks the backend
  itself every 15 minutes (`ReminderWorker`, WorkManager) and shows
  due reminders as local notifications, marking each "sent" on the
  backend so it is only shown once. Tasks waiting on your approval
  are notified once each.

## Build it

### On this VM (or any Linux machine with Java 17)

```bash
cd android-app
./build-apk.sh        # needs JAVA_HOME, ANDROID_HOME set (see script)
```

The script uses the Gradle wrapper when present, otherwise a local
Gradle 8.7 install. It also teaches Gradle about an authenticated
`https_proxy` from the environment when one is set (proxy password is
written only to `$GRADLE_USER_HOME/gradle.properties`, never to the
repo).

The release APK lands at
`app/build/outputs/apk/release/app-release.apk`.

### On his PC with Android Studio (recommended for him)

1. Install **Android Studio** (Ladybug or newer) with JDK 17 (bundled).
2. Open this folder (`android-app/`) as the project. Let Android
   Studio sync Gradle — it downloads the Gradle 8.7 wrapper and the
   SDK pieces itself on first run (SDK 34, build-tools 34.0.0).
3. **Release keystore:** the release build signs with the keystore
   described in `keystore.properties` (git-ignored). To make your own:
   in Android Studio, Build → Generate Signed App Bundle / APK →
   Create new keystore — or from the command line:
   ```bash
   keytool -genkeypair -keystore bow-bow-keystore.jks -alias bowbow \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
   Then create `android-app/keystore.properties` with:
   ```properties
   storeFile=/absolute/path/to/bow-bow-keystore.jks
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=bowbow
   keyPassword=YOUR_KEY_PASSWORD
   ```
   **Keep the keystore file and its passwords safe.** Lose them and
   future updates cannot be signed with the same key — the Play
   Store / Android will treat a differently-signed APK as a new app.
   Without `keystore.properties` the release build is unsigned.
4. Build → Build App Bundle(s) / APK(s) → Build APK(s), or run
   `./gradlew assembleRelease` in the project folder.
5. Install the APK on the phone: copy
   `app/build/outputs/apk/release/app-release.apk` to the phone and
   tap it (allow "Install unknown apps" for the file manager / browser
   once), or `adb install app-release.apk` over USB debugging.
6. First run: grant Microphone and Notifications when asked. The
   backend URL defaults to the hosted backend — change it in
   Settings only if you run your own backend.

## Files

| Path | What it is |
|---|---|
| `app/src/main/java/com/irshad/bowbow/MainActivity.kt` | Main screen: mic, waveform, chat, TTS, call dialog |
| `app/src/main/java/com/irshad/bowbow/WaveformView.kt` | Animated voice waveform (driven by mic RMS) |
| `app/src/main/java/com/irshad/bowbow/HistoryActivity.kt` | Tasks + reminders history |
| `app/src/main/java/com/irshad/bowbow/SettingsActivity.kt` | Backend URL, language, TTS, backend status |
| `app/src/main/java/com/irshad/bowbow/BowBowApi.kt` | OkHttp client: `chat()`, tasks, reminders, `requestCall()` |
| `app/src/main/java/com/irshad/bowbow/ReminderWorker.kt` | 15-minute poller: due reminders + tasks awaiting approval → local notifications |
| `app/src/main/java/com/irshad/bowbow/ChatAdapter.kt` | Two-sided chat bubbles |
| `app/src/main/java/com/irshad/bowbow/Prefs.kt` | Backend URL, language, TTS storage |
| `app/src/main/AndroidManifest.xml` | Permissions (Internet, mic, notifications), activities |
| `app/src/main/res/drawable/ic_launcher_*.xml` | Adaptive launcher icon (waveform mark) |
| `build-apk.sh` | One-command release build (Linux) |
| `keystore.properties` | Release signing (git-ignored, never committed) |

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
- No `google-services.json` is needed, and no Firebase project
  exists for BOW BOW.

## Wake-word (later enhancement, not built)

A user-controlled wake word ("Hey BOW BOW") is a future step, not in
this build. It needs a foreground service (always-on mic, persistent
notification, battery exemption) plus a wake-word engine such as
Picovoice Porcupine (its own access key). Deliberately not built now:
it changes the app's battery and privacy profile, and the push-button
mic already covers voice use.

## Notes / limits

- Tamil speech recognition depends on the Google speech services on
  the phone (Google app + Tamil language pack). If Tamil is missing,
  it falls back to the phone's default language.
- Sending email / deleting / paying always need mohamed's confirmation
  first — the app never does those on its own.
- The Twilio trial only speaks Twilio's sample message on calls; the
  custom message in the call dialog is sent to the backend but the
  trial ignores it. Custom spoken text needs the Twilio account
  upgraded (his approval required — never done silently).
