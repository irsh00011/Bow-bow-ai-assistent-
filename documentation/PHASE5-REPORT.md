# Phase 5 — Backend wired to Supabase

Date: 2026-11-08 (evening IST). Status: **wired and live-tested;
service key verified 22:48 IST. Firebase dropped 2026-11-09 — the
`POST /notify` endpoint described below was removed; see the note at
the end of this file.**

## What was built (`backend/main.py`)
- Supabase REST client over PostgREST (`_sb_request`), using
  `SUPABASE_URL` + `SUPABASE_SERVICE_ROLE_KEY` (preferred) or
  `SUPABASE_ANON_KEY` (fallback). `BOWBOW_USER_ID` (the user's Supabase
  user UUID `5897b826-feff-4dd2-9841-f8d4b510f6bf`) is stored in
  `backend/.env` (git-ignored) and used as `user_id` on every row.
- New endpoints:
  - `GET /tasks` (+ `?status=`), `POST /tasks`, `POST /tasks/{id}/complete`
    (updates the task and writes a `task_history` row)
  - `GET /reminders`, `POST /reminders`
  - `GET /conversations`, `POST /conversations`
- `GET /health` now reports `supabase: {configured, role, user_id_configured}`.
- `POST /chat` records each exchange in `conversations` best-effort —
  a logging failure never breaks the chat reply.

## Live test results (2026-11-08 ~22:46 IST, uvicorn on this VM)
- `GET /tasks` → 200, returned the Flow B test task (completed).
- `POST /tasks` → 201, created task `a07b1143-8b0d-4064-9c29-dd80c12ee0bc`
  ("Backend test task 2", pending) — backend writes work with the anon key.
- `GET /reminders` → 200, returned both test reminders.
- `POST /conversations` → honest 401: `new row violates row-level
  security policy for table "conversations"` — the anon key has no policy
  on conversations (by design); it works once the service-role key lands.

## Bug found and fixed — egress proxy + TLS (would have broken /chat too)
All backend HTTP calls first failed with `Internal Server Error`. Two
separate causes, both in the runtime environment:
1. httpx's default `trust_env` parsing chokes on this VM's `NO_PROXY`
   entries (`httpx.InvalidURL: Invalid port: ':1]'`).
2. The egress proxy re-signs TLS with its own CA; Python's certifi
   bundle doesn't trust it (`SSL: CERTIFICATE_VERIFY_FAILED`).
Fix: a single `_http_client()` helper used by every outbound call
(Supabase, OpenRouter, Activepieces) — `trust_env=False`, proxy
taken explicitly from `https_proxy`, TLS verified against the runtime
CA bundle at `SSL_CERT_FILE` (`/run/hatch/egress-tls/ca-bundle.pem`).
TLS stays verified; nothing is disabled. This also fixed the latent
breakage in `/chat`'s OpenRouter and Activepieces calls, which used the
same default client.

## Service key — received and verified 2026-11-08 22:48 IST
- Browser retrieved it from Settings → API (legacy), verified its
  payload carries role `service_role` and project ref
  `yrwjsnfitkjzmtimsgeg`. Saved to `backend/.env` (git-ignored; the
  value is nowhere in chat or git).
- Live verification: `/health` reports `role: service_role`;
  `POST /conversations` created row `d4f208c5-fa30-4e03-82ce-8d0ef0a6068d`
  and `GET /conversations` reads it back.

## Migration 002 — done 2026-11-08 22:49 IST
- Browser ran `alter table public.users add column if not exists
  fcm_token text;` in the SQL Editor: "Success. No rows returned".
  Verified independently via REST: the users row now carries
  `fcm_token: null` (no device token registered yet — correct until
  the Android app launches).

## Firebase dropped — 2026-11-09 08:19 IST (user's decision)
- `POST /notify`, `POST /users/fcm-token` and the `send_notification`
  tool were removed from the backend; `google-auth` removed from
  requirements. `PATCH /reminders/{id}` added so the Android app's
  WorkManager poller can mark reminders `sent` after showing them.
- Verified live: `/notify` and `/users/fcm-token` return 404,
  `PATCH /reminders/c6556a96...` → `sent`, `/health` tools list no
  longer contains `send_notification`. The no-Firebase notification
  design is in `documentation/PHASE10-PREP.md`.
