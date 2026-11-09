# Phase 12 — Backend hosting: Vercel (2026-11-09)

## Decision

Render was started first (free tier, Singapore) but mohamed asked to use
Vercel instead — he already runs Miraasu and Roots Handicraft there. The
Render browser task was stopped before it created anything; no Render
service exists.

## Why Vercel Hobby works for this backend

- Fluid compute is the default for new projects: **300 s max function
  duration** on Hobby (the old 10 s limit is legacy, pre-April-2025
  projects only). `/chat` worst case (60 s upstream + one retry) fits.
- 1M invocations/month, 4 active CPU-hours, 360 GB-hours memory — far
  above a personal assistant's use.
- Hobby is non-commercial personal use only — BOW BOW is mohamed's own
  assistant, so it fits.
- Caveat: the backend is stateless (all state in Supabase), so serverless
  fits. Cold starts add a few seconds on the first call after idle.

## Vercel adaptation (this repo)

- `api/index.py` — serverless entry point; imports the FastAPI `app`
  from `backend/main.py` and exports it (Vercel serves ASGI `app`).
- `vercel.json` — rewrites every path to `/api/index`;
  `api/index.py` maxDuration 300.
- `requirements.txt` (repo root) — Vercel installs from here; keep in
  sync with `backend/requirements.txt`.
- No code changes to `backend/main.py`: `_http_client` falls back to
  `verify=True` and no proxy when `SSL_CERT_FILE` / `https_proxy` are
  unset, which is the case on Vercel.

## Environment variables (set in Vercel, production)

From `backend/.env` (never in git): SUPABASE_URL, SUPABASE_ANON_KEY,
SUPABASE_SERVICE_ROLE_KEY, BOWBOW_USER_ID, OPENROUTER_MODEL,
ACTIVEPIECES_WEBHOOK_A/B/C/D. ACTIVEPIECES_WEBHOOK_E is empty (Flow E
has no webhook by design). OPENROUTER_API_KEY is empty in `.env` —
mohamed adds a fresh key (BOW-BOW-HOSTED) in the Vercel dashboard;
until then `/health` reports `missing_key` and `/chat` returns 503
(by design, never a faked reply).

## Deploy

Project `bow-bow-backend`, linked to GitHub
irsh00011/Bow-bow-ai-assistent- (private), branch main. Created via the
Vercel connector (`create_git_project`, env via `create_project_env`,
deploy via `create_deployment` with `gitSource` + `forceNew: 1`).
