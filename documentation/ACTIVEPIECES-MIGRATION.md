# BOW BOW — Activepieces migration record (2026-11-08)

## Decision
Activepieces is the permanent automation engine for BOW BOW. It runs
every workflow, trigger and webhook: Gmail, Google Calendar, Google
Drive, and all future integrations. No other automation platform is
used in this project.

## Audit (2026-11-08)
- Searched the whole project for any other automation platform.
  Found: **zero dependencies, zero configs, zero code** — the only
  traces were two textual mentions ("(NOT n8n)" in README.md and
  "No n8n anywhere in this project" in automation/README.md).
- Removed both mentions. A fresh grep over the project (excluding
  `.venv`, `.git`, `__pycache__`) now returns **zero matches**.
- Checked Muse's connectors: no n8n connector is connected here.
- Searched this machine for an n8n MCP server/config: none found.
  The only hits were historical mentions inside self-improvement
  staging snapshots of MEMORY.md. The separate lightweight n8n pack
  for the government laptop (`~/workspace/n8n-lightweight/`) is NOT
  part of BOW BOW and was left untouched.
- mohamed has an n8n MCP on n8n Cloud
  (https://anasaa22.app.n8n.cloud/mcp-server/http, live, 401 without
  auth). DECIDED 2026-11-08: leave it alone — it stays for other work
  outside BOW BOW. Nothing in BOW BOW connects to it.

## Preserved (nothing recreated)
- Activepieces Cloud project `bow-bow-assistant` (Free plan), signed
  in as mohairshu0000@gmail.com via Google phone verification.
- Connected accounts: Gmail (read-only), Google Calendar
  (events-only), Google Drive (full scope — forced, no read-only
  option offered).
- Flows A–E specs in `automation/flows/` and the flow-building
  browser task that was already running; it was not restarted.

## Backend wiring (new in this migration)
- `backend/main.py` now triggers Activepieces flows by webhook:
  `FLOW_MAP` routes each model tool call to its flow (A Gmail,
  B tasks, C reminders, D calendar; E is schedule-triggered, so no
  chat tool maps to it).
- Webhook URLs come from the environment only:
  `ACTIVEPIECES_WEBHOOK_A` … `_E` (see `backend/.env.example`).
  A flow with no URL is reported as `not_configured`, never faked.
- `GET /flows` lists the five flows with configured true/false.
  Webhook URLs are never exposed in any API response.
- `GET /health` now reports the Activepieces engine and how many
  flows are configured.
- `POST /chat` returns a `flows` list with each triggered flow's
  status alongside the reply and tool calls.

## Test status
- Verified 2026-11-08: backend compiles; `/health` reports
  Activepieces status; `/flows` lists all five flows; `/chat`
  still refuses without an OpenRouter key (503, by design).
- **All five flows built, published and renamed** (browser task
  completed 22:34 IST, 2h22m). Webhook URLs (production) in
  git-ignored `backend/.env` as `/sync` URLs — the plain webhook
  URL returns only the async acknowledgement (HTTP 200 `{}` by
  design); the real response needs `/sync`.
- **Verified live by me, 2026-11-08 22:34 IST:** Flow A `/sync`
  returns 10 real unread messages; Flow D `/sync` list returns
  the real calendar object (0 events next 3 days); Flow D
  unconfirmed create returns HTTP 403 `{"error":"needs
  confirmation"}`. No stray "BOW BOW guard test" events ever
  existed — nothing created or deleted.
- **Flow E** tested by the browser (cron → GET tasks → 0
  awaiting approval → HTTP correctly skipped). Runs every 6h.
- **Flows B/C/E — notify steps removed, re-tested 2026-11-09 08:31 IST:**
  Flow B SUCCEEDED end-to-end (task completed + history row, verified
  in Supabase). Flow E published (cron trigger, no webhook test
  applies). Flow C's final PATCH FAILED with HTTP 400 "invalid input
  syntax for type uuid" — pre-existing token bug: the PATCH URL uses
  `{{step_2['output'][0]['id']}}` but the insert step's output nests
  the row under `body`, so the id resolved to empty. Fix (one token
  change to `{{step_2['output']['body'][0]['id']}}`, republish,
  re-test) was sent to the browser task. The stuck test reminder
  `eb10761a` was marked `sent` directly to keep it from notifying.
  All three flows are switched on; the "unpublished changes" banners
  are gone.
- **Flow C FIXED + VERIFIED 2026-11-09 08:39 IST:** token changed to
  `{{step_2['output']['body'][0]['id']}}`, republished, retest run
  SUCCEEDED — reminder `143d9351` ended "sent", verified directly in
  Supabase (the earlier `eb10761a` row is also "sent" after a direct
  cleanup). **All 5 flows now work end-to-end without Firebase.**
- **Diagnosis confirmed 22:42 IST (browser checked both runs):**
  in B and C, ONLY step 5 (HTTP notify) fails — "Invalid URL"
  because `{{env.FCM_NOTIFY_URL}}` is unset. Every other step
  works. The first Flow C test's timestamp was 5.5h in the past
  (this box has no IST tzdata — `TZ=Asia/Calcutta date` returned
  UTC wall time), so its Delay completed immediately; retested
  with a correct future IST time (22:46:50 IST), reminder saved,
  run waiting in Delay. Note: `/sync` times out with HTTP 408
  after ~30s on long runs — the run continues server-side.
- `automation/flows/test-webhooks.sh` updated with the real test
  UUIDs and all four `/sync` URLs.
- **Still open:** FCM_NOTIFY_URL is unset, so the HTTP notify
  steps in B, C and E will fail until Phase 10 sets it. Flow D's
  confirmed-create path was never live-tested (would create a
  real event). No flow is claimed working until its real
  production webhook call returns real data.
