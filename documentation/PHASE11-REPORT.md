# Phase 11 Report — Phone Calls via Twilio (Announce mode)

Date: 2026-11-09. Status: **Announce mode verified end-to-end on the Twilio trial.** Bridge mode and conversational AI voice are later steps, not built.

## What was built
- `POST /call` in `backend/main.py` (commit `1eb8aa0`, live on Vercel). Request: `{"to": E.164, "message": 1–500 chars}`.
  - Reads `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER` and `TWILIO_ALLOWED_NUMBERS` (default `+917418189588` — mohamed's own number).
  - 503 if Twilio unconfigured; **403 if `to` is not allowlisted** (checked before any network call).
  - Tamil messages use voice `Polly.Aditi` / `ta-IN`; everything else `alice` / `en-IN`.
  - On Twilio error (≥400) returns 502 with Twilio's error text (first 200 chars). Success returns `{"status":"placed","call_sid","to"}`.
- Tool spec `request_phone_call` now takes `to` + `message`, both required. The model never dials by itself: /chat only returns the tool call; dialling happens via POST /call after mohamed's confirmation.
- `/health` gained a `twilio` block (`configured`, `allowed_count`).

## Twilio trial account (free, no card)
- Account "My First Twilio Account", SID `AC…a31` (full value in Vercel env vars only), created 2026-11-09 09:08 IST (browser, Continue with Google, SMS 2FA). Limited trial, 30 days, **75 free voice minutes**, 10 min/call cap, calls only to up to 5 verified numbers.
- Trial From number: `+17372508034` (shown in Console "Try out Voice" panel; the Phone Numbers inventory lists none).
- Auth Token: copied by the browser straight from the Twilio Console into the Vercel env vars — **the value exists only in Vercel env vars, never in chat, git, memory, or backend/.env.**

## The trial limitation that broke the first test (verified from Twilio's own docs)
Twilio's "Try out Voice" docs: on a trial account the Calls API only accepts `to`, a `Url` pointing at one of Twilio's four template webhooks (voice_speech_recognition, voice_text_to_speech, voice_keyboard_input, voice_play_audio), and optional `statusCallback`. **Inline `Twiml` is rejected** with 400 "Invalid or disallowed parameters provided — trial accounts have limited parameter access, upgrade your account to unlock full functionality". Custom TwiML is supported only as TwiML fetched from a URL, and custom spoken text is not available on trial at all.
- First test call (09:28 IST) failed exactly on this: inline `Twiml` → 400. No call placed, no charge.
- Fix (commit `3539a7f`, pushed `a80e4ab2`): when `TWILIO_CALL_URL` is set, /call sends `Url` with that value and skips `Twiml`; without it (upgraded account) it sends the custom inline TwiML as before. `TWILIO_CALL_URL=https://webhooks.twilio.com/v1/Voice/Template/voice_text_to_speech` was set in Vercel (Production) and the deployment redeployed Ready 09:37 IST.
- **Consequence: on the trial, the call speaks Twilio's sample text-to-speech message, not BOW BOW's custom text.** Custom spoken messages need the account upgraded (add a payment method; trial minutes still apply). **No upgrade without mohamed's approval.**

## Test results
- Local (dummy creds): unconfigured → 503; non-allowlisted number → 403 (no network); allowlisted number → 502 with Twilio's real 401 (code 20003) — request shape/auth correct.
- Live 2026-11-09 09:37 IST: `POST /call` to +917418189588 → `{"status":"placed","call_sid":"CAd1cb8f5eccffa11c20fcc1bfe1fa9ffb","to":"+917418189588"}`. **mohamed confirmed he heard the call.** Announce mode works end-to-end on the trial.

## Costs
Nothing paid. No card on file. Trial minutes used: one short test call.

## Open items
- Flow B create branch still broken (separate item; browser task diagnosing).
- Bridge mode (connect callee + mohamed) not built; conversational AI voice not built.
- No get/list-tasks tool — the model cannot complete a task by title yet.
- Android app has no call UI yet (no UI calls POST /call); first build on his PC still pending.
