# BOW BOW — Phase 1 report (2026-11-08)

## What was completed
- Inspected this computer: Ubuntu 24.04.5 LTS, 4 CPU, 7.7 GB RAM
  (~0.8 GB available), 150 GB disk / 134 GB free, /tmp 512 MB.
- Terminal: yes. Browser: yes (cloud browser).
- Python 3.12.3, Node v24.22.1, npm 10.9.4, git 2.43.0 — already present.
- Java 17 (OpenJDK 17.0.20.1) — installed.
- Docker 29.1.3 + Compose 2.40.3 — installed.
- Project folder `bow-bow-assistant/` created with
  android-app / backend / automation / database / documentation / deployment.
  Local git init done (no remote yet).
- Backend skeleton written and dependency-checked: FastAPI 0.162.2,
  `backend/main.py` — real OpenRouter client, configurable model,
  11 allowlisted tools, no arbitrary shell execution, 503 without a key
  instead of a fake reply. `backend/.venv` created.
- Supabase schema drafted: `database/schema.sql` (users, tasks,
  conversations, reminders, task_history + RLS).
- Activepieces compose written: `automation/docker-compose.yml`
  (app + Postgres + Redis), plus single-container PGLite alternative.
- OpenRouter free-model research (checked 2026-11-08):
  50 req/day with no credits, 1,000/day after a $10 credit purchase,
  20/min. Default chosen: `google/gemma-4-31b-it:free`
  (262K context, 140+ languages, function calling). Fallback:
  `openrouter/free` router. Free list rotates — re-verify at test time.
- Activepieces research: Cloud free $0 / 1,000 credits/mo, unlimited flows;
  self-host minimum 2 vCPU / 4 GB RAM / 30 GB disk.

## What was tested
- `java -version` → 17.0.20.1 OK. `docker --version` / `compose version` OK.
- `python -c "import fastapi"` in backend venv → OK.
- Docker daemon start — **FAILED**: iptables bridge NAT permission denied
  in this VM sandbox. Docker is installed but cannot run containers here.

## Whether it works
- Phase 1 environment: mostly yes. Two limits: (1) Docker daemon blocked
  on this VM, (2) Android SDK not installed here — Android build is a
  Phase 8 job for his PC/laptop, as with the earlier offline-voice-ai-g35
  starter (never built an APK on this VM).

## What remains
- Phase 2: OpenRouter key (needs mohamed).
- Phase 3: Activepieces — Cloud free account (Google sign-in) OR a host
  where Docker actually runs.
- Phase 4: Supabase project `bow-bow-assistant` (Google sign-in).
- Phases 5–12 per the master instruction.

## What I need from mohamed
**What I need:** OpenRouter API key, named BOW-BOW.
**Where to get it:** https://openrouter.ai/keys
**What to do:** Sign in with Google first (mohairshu0000@gmail.com) →
  API Keys → Create Key → name it BOW-BOW.
**Why I need it:** To connect the AI brain and test Tamil / English /
  Tanglish + tool calling live.
Share it only through the secure flow, never in chat.
