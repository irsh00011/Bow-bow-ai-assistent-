# BOW BOW — Personal AI Assistant

Voice assistant for mohamed (Mohammed Irshad). Speak Tamil / English / Tanglish,
BOW BOW plans the steps, calls approved tools, remembers the outcome, and replies
in the language you spoke.

**Status: Phase 1 complete (2026-11-08). Phase 2 blocked on OpenRouter API key.**

## Stack (as specified)
- AI brain: OpenRouter API (model configurable, default free model)
- Automation: Activepieces — the permanent automation engine for all workflows, triggers and webhooks
- Database / memory: Supabase
- App: Native Android (Kotlin)
- Voice: Android SpeechRecognizer + TextToSpeech (v1)
- Notifications: in-app polling (WorkManager, every 15 min) — no Firebase, no push
- Email / Calendar / Drive: Gmail API, Google Calendar API, Google Drive API
- Calls: Exotel / Twilio — investigate only, no paid activation without approval
- Backend: Python FastAPI
- Hosting: free tier where practical
- VCS: GitHub (repo not created yet — local git init done)

## Layout
```
android-app/      Kotlin app (Phase 8)
backend/          FastAPI backend (Phase 5)
automation/       Activepieces compose + flow specs (Phase 3)
database/         Supabase schema + RLS (Phase 4)
documentation/    Phase reports
deployment/       Hosting / deploy notes
```

## Safety rules (standing)
- Read / draft: automated. Send / delete / pay / call: confirmation only.
- Never execute arbitrary AI-generated shell commands.
- Never expose OpenRouter key, Supabase service-role key, or OAuth secrets
  in the Android app or in git. Keys live only in backend `.env` (git-ignored).
- Sign-in rule: **Continue with Google first**, account mohairshu0000@gmail.com.
  Google sign-in goes through Google's own screen / the already-signed-in
  browser profile. Never paste a Google password into an app or chat.
- All important actions are recorded in `task_history`.

## Phase 1 findings (2026-11-08, this VM)
- Ubuntu 24.04.5 LTS, 4 CPU, 7.7 GB RAM (~0.8 GB available — tight),
  150 GB disk, 134 GB free. /tmp is 512 MB tmpfs.
- Python 3.12.3, Node v24.22.1, npm 10.9.4, git 2.43.0 — present.
- Java 17 (OpenJDK 17.0.20.1) — **installed in Phase 1**.
- Docker 29.1.3 + Compose 2.40.3 — **installed in Phase 1, but the daemon
  cannot start on this VM**: `iptables … Permission denied (you must be root)`
  when creating the bridge NAT chain. So Activepieces cannot run locally here
  until that sandbox limit is lifted. Recommended path: Activepieces Cloud
  free tier (1,000 credits/mo, no card) for flows, or a small VPS later.
- Android SDK / adb / Gradle — NOT installed yet. Android Studio build
  happens on mohamed's PC/laptop (Phase 8), not on this VM.
- FastAPI 0.162.2 + uvicorn + httpx installed in `backend/.venv`.
- Existing related work: `~/workspace/offline-voice-ai-g35/` (Track B starter)
  and PocketPal Track A proved on his moto g35 5G (2026-11-07). BOW BOW is
  the online, tool-using successor — it does NOT replace that offline proof.

## Run the backend (dev)
```bash
cd backend
cp .env.example .env   # fill OPENROUTER_API_KEY, then start
.venv/bin/uvicorn main:app --reload --port 8000
curl http://127.0.0.1:8000/health
```
Without a key, `/health` reports `openrouter: missing_key` and `/chat`
returns 503 — it never fakes a reply.

## Next: Phase 2
Needs mohamed to create an OpenRouter key named BOW-BOW at
https://openrouter.ai/keys (sign in with Google first) and share it via
the secure flow — not in chat. Then the backend is tested live in
Tamil, English, Tanglish, and tool-calling.
