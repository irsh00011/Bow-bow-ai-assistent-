# BOW BOW — Activepieces automation

**Decision (Phase 3): Activepieces Cloud free tier first, NOT self-host.**
- Cloud free: $0, no card, 1,000 credits/month, unlimited flows.
  Sign up at https://cloud.activepieces.com/sign-up — Continue with Google first.
- Self-host is blocked on this VM: Docker daemon cannot start here
  (iptables bridge NAT permission denied). `docker-compose.yml` in this
  folder is ready for a VPS / PC where Docker actually runs.
- Activepieces is the only automation engine in this project — the permanent choice for all workflows, triggers and webhooks.

## Flows (specs in `flows/`)
| Flow | Trigger | What it does |
|---|---|---|
| A — Gmail Summary | Webhook from backend: `{user_id, limit, unread_only}` | Gmail piece → list messages → return id/sender/subject/snippet/date → backend summarises with OpenRouter |
| B — Task Completion | Webhook: `{task_id, status, execution_result}` | Update `tasks` in Supabase → insert `task_history` row (no notify step — app polls) |
| C — Reminder | Webhook: `{title, scheduled_at, user_id}` | Insert `reminders` row → Delay-until step → mark `sent` (app polls and notifies locally) |
| D — Calendar | Webhook: `{days}` / `{title, start, end}` | Calendar piece → list upcoming events, or create event (only after user confirmation in the app) |
| E — Follow-up | Webhook / Schedule: `{task_id}` | Check task status in Supabase (app polls awaiting_approval and notifies locally) |

## Rules
- Every flow is triggered by the backend, never directly by the AI model.
  The model only *requests* a tool; the backend validates it, then calls
  the flow webhook.
- Gmail / Calendar / Drive connections use OAuth in Activepieces, with the
  minimum scopes. Sending mail, deleting files, and creating events need
  the user's confirmation in the app first.
- Test each flow with a real webhook call (`curl` examples in
  `flows/test-webhooks.sh`) and record the result in
  `documentation/PHASE3-4-PREP.md`. Do not claim a flow works until it
  has run successfully end-to-end.

## Setup (when mohamed is ready)
1. Sign up on Activepieces Cloud with Google (mohairshu0000@gmail.com).
2. Create a project `bow-bow-assistant`.
3. Connect Gmail + Google Calendar + Google Drive pieces (OAuth, Google sign-in).
4. Create Flows A–E from the JSON specs in `flows/`, copy each flow's
   webhook URL into `backend/.env` as `ACTIVEPIECES_WEBHOOK_<FLOW>`.
5. Run the test calls and tick them off in the prep doc.
