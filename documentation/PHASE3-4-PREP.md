# BOW BOW — Phases 3–4 prep (2026-11-08)

Prep is complete. Live account creation still needs mohamed's Google
sign-in tap — nothing was created, and no key or password was stored.

## Phase 3 — Activepieces: research (verified 2026-11-08)
- Cloud free tier: $0, no card, **1,000 credits/month, unlimited flows**.
  Sign up: https://cloud.activepieces.com/sign-up — Continue with Google first.
- Self-host: MIT Community Edition, Docker Compose (app + Postgres + Redis),
  minimum 2 vCPU / 4 GB RAM / 30 GB disk. **Blocked on this VM** — Docker
  daemon cannot start (iptables bridge NAT permission denied). Compose file
  at `automation/docker-compose.yml` is ready for a VPS/PC.
- Single-container eval alternative: `docker run … -e AP_DB_TYPE=PGLITE
  activepieces/activepieces:latest` (also needs a working Docker daemon).
- Flows A–E are specified in `automation/flows/*.json` with triggers, steps,
  guards, and success criteria. `test-webhooks.sh` has the real test calls.
- Test status: **not run yet** — needs the Cloud account + Gmail/Calendar
  connections + webhook URLs in `backend/.env`. No flow is claimed working.

## Phase 4 — Supabase: research (verified 2026-11-08)
- Free tier: $0, **2 active projects**, 500 MB database per project
  (~40–60 MB used by a fresh project), 1 GB file storage, 5 GB egress,
  50,000 monthly active users, 500,000 edge-function calls, unlimited API
  requests. **Free projects pause after 7 days of inactivity** — resume
  from the dashboard; data is kept. No backups on Free.
- Pro is $25/month — NOT needed now. BOW BOW fits in Free (one user).
- Project to create: name `bow-bow-assistant`, region closest to India
  (Mumbai `ap-south-1` if offered, else Singapore `ap-southeast-1`).
  Sign in at https://supabase.com — Continue with Google first.
- After provisioning: Project Settings → API → copy Project URL + anon key
  into `backend/.env` (`SUPABASE_URL`, `SUPABASE_ANON_KEY`). The
  **service-role key stays server-side only** — never in the Android app.
- Then run `database/schema.sql` in the Supabase SQL editor (users, tasks,
  conversations, reminders, task_history + RLS). Verify with one insert +
  one select per table before the backend uses it.
- Watch-out: his Google account was over its 15 GB storage limit on
  2026-11-01 (Drive/Gmail). That does not block Supabase, but Gmail
  searches may be slow until he frees space — not our call to delete.

## What still needs mohamed (one tap each, Google sign-in)
1. Activepieces Cloud account (Google sign-in) + connect Gmail/Calendar/Drive.
2. Supabase project `bow-bow-assistant` (Google sign-in) + paste URL/key
   into the backend via the secure flow.
3. OpenRouter key (Phase 2 — still the main blocker).

## Next after those taps
Phase 5: wire backend → OpenRouter (live test) → Activepieces webhooks →
Supabase, then Phase 6 Gmail, Phase 7 Calendar/Drive, Phase 8 Android app.
