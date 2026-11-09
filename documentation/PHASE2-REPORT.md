# BOW BOW — Phase 2 report (2026-11-08)

## What was completed
- OpenRouter account + key named BOW-BOW created by mohamed (Google sign-in).
- Key stored via the secure connector flow as `custom.openrouter`
  (bearer header, host `openrouter.ai`). The key value is nowhere in chat,
  files, or git.
- Connector doc authored: `~/workspace/custom-connectors/custom.openrouter/`
  (`CONNECTOR.md`, `connector.yaml`, helper `bin/openrouter.py`).
- Backend hardened: `backend/main.py` now retries once with the free router
  (`openrouter/free`) when the configured model returns 429.

## What was tested (live, 8 free requests used of 50/day)
| Test | Model used | Result |
|---|---|---|
| List models (harmless read) | — | 461 models; `google/gemma-4-31b-it:free` exists |
| English: "Hello BOW BOW, what can you do?" | gemma-4-31b-it:free | **429 upstream** — shared Google pool busy, not an auth failure |
| Tanglish: "en Gmail check panni important emails sollu" | gemma-4-31b-it:free | **429 upstream** (same) |
| Tamil: "நாளை என் காலண்டரில் என்ன இருக்கிறது?" | gemma-4-31b-it:free | **429 upstream** (same) |
| Tool call: "Check my Gmail…" | gemma-4-31b-it:free | **429 upstream** (same) |
| English via `openrouter/free` | nvidia/nemotron-3-ultra-550b-a55b:free | PASS — "I can help you manage emails, calendar events, tasks, and more…" |
| Tool call via `openrouter/free` | recursive/flint-v1.0:free | PASS — returned a real tool call: `get_emails {"limit": 10, "unread_only": true}` |
| Tanglish via `openrouter/free` | nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free | PASS — understood, replied in Tanglish (slightly rough wording) |
| Tamil via `openrouter/free` | recursive/flint-v1.0:free | PASS — replied in Tamil, correctly said it has no calendar access yet instead of inventing events |

## Whether it works
- **Yes — the AI brain is live.** The key is valid (a bad key would 401;
  these calls reached the providers). English, Tanglish, Tamil and tool
  calling all verified.
- Caveats: (1) the default model gemma-4-31b-it:free was rate-limited on
  its shared pool at test time — the backend now falls back to the router
  automatically; (2) the router picks a random free model per call, so
  reply quality varies; (3) the Tanglish reply was understandable but
  slightly off — a named model will be steadier once its pool recovers.
- Free limits: 50 requests/day, 20/min; 1,000/day after a $10 credit
  purchase (credits are not spent by `:free` models).

## What remains
- Phase 3: Activepieces Cloud account + connect Gmail/Calendar/Drive.
- Phase 4: Supabase project `bow-bow-assistant` + run `database/schema.sql`.
- Phase 5: wire backend → OpenRouter (done, via connector) → Activepieces
  webhooks → Supabase; then Phases 6–12.

## What I need from mohamed
- Activepieces Cloud sign-up (Google sign-in) at
  https://cloud.activepieces.com/sign-up, then connect Gmail, Calendar, Drive.
- Supabase project at https://supabase.com (Google sign-in), name
  `bow-bow-assistant`, region Mumbai if offered, else Singapore.
- Nothing to pay. Both are free tiers.
