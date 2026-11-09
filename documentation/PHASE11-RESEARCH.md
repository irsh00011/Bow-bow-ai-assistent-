# Phase 11 — Phone Calls: research (2026-11-09)

Investigation only. No account created, no trial started, nothing paid.
Figures below were checked 2026-11-09 from Twilio's official trial docs and
September 2026 pricing trackers; confirm on the provider's own page before
spending anything.

## What "calls" means for BOW BOW

Two modes, both gated by mohamed's confirmation before dialling (standing
safety rule — calls are never placed without his tap):

1. **Announce** — BOW BOW dials a number and speaks a short message
   (text-to-speech), e.g. "Hi, this is a call from Mohamed Irshad's
   assistant — your order is ready." The callee hears the message; no live
   conversation.
2. **Bridge** — BOW BOW dials the callee, dials mohamed's own phone, and
   connects the two so he talks himself. Simpler, no AI-voice weirdness.

A fully conversational AI voice call (the assistant talks back and forth
live) is a later step: it needs Twilio Media Streams or Exotel AgentStream
plus a realtime voice pipeline. V1 is Announce or Bridge.

## Twilio (fastest free test)

- Trial: no credit card, 30 days, **75 free voice minutes**.
  Trial limits: calls only to **up to 5 phone numbers you verify** by code
  (your own numbers / family), and the caller ID is a shared Twilio number
  that you do not keep after upgrade.
- India outbound (Sept 2026): **$0.0496/min to mobiles** (~₹4.40/min),
  $0.0699/min to landlines; duration rounded up to the next minute.
- Twilio has **no Indian local/mobile numbers** — only international numbers
  from $1.15/mo. So the caller ID on the callee's phone would be a foreign
  number, which many people ignore.
- Production: add a payment method (small card pre-auth), top up, calls to
  any number. Upgraded accounts need a Primary Customer Profile; Individual
  profile = max 3 concurrent calls (fine for a personal assistant).
- API: one POST to Programmable Voice creates a call with TwiML
  (`<Say>` for Announce, `<Dial>` for Bridge). Media Streams for live AI
  talk: +$0.0044/min.
- India rules: TRAI DND, consent, and calling hours apply — call only
  people who expect the call.

Verdict: the right way to **test** calls for free. Limits (verified numbers
only, foreign caller ID, 30-day trial) make it a test bed, not the final
home for the feature.

## Exotel (the proper India option)

- India-native: Indian virtual numbers (ExoPhone), DND filtering built in,
  TRAI-compliant, 40M+ daily voice transactions.
- Trial: 7 days with **500 credits (₹500)** — but requires **company
  registration and KYC first**, a multi-day onboarding, not instant.
- Paid: Dabbler ₹9,999 (5 months, 5,000 credits), Believer ₹19,999
  (11 months, 9,500 credits), Influencer ₹49,499 (11 months, 39,000
  credits). 1 credit = ₹1, shared across voice/SMS/WhatsApp; 18% GST;
  credits expire with the plan's validity. Per-minute rates are NOT
  published — they come from your bill plan. A bridged two-party call has
  two legs, so it costs roughly double a one-leg call.
- AI voice streaming (AgentStream, needed for a live talking assistant) is
  **excluded from the listed plans** and priced on request
  (hello@exotel.com).

Verdict: the right **production** choice for India, but entry is ~₹10,000
+ KYC + company paperwork. Not a free path, and not worth it until the
Twilio trial proves the feature is useful.

## Recommendation

1. **Test with Twilio trial** (free, no card): verify mohamed's own number,
   BOW BOW places an Announce call to it and speaks a test message. Proves
   the whole path — backend → provider → real phone — in one afternoon.
2. If he uses calls often, **move to Exotel** for the Indian number and
   DND handling; keep Twilio only as the trial.
3. Implement Announce first, Bridge second, live two-way AI voice last.
4. Never dial without his confirmation; never call numbers that did not
   ask to be called.

## What this needs from mohamed (when he wants it)

- Twilio test: create a free Twilio account (Continue with Google), verify
  his own phone number in the console, share the Account SID + Auth Token
  via the secure flow (never in chat).
- Exotel production: company registration + KYC + ₹9,999 plan — his call,
  only after the Twilio test proves the feature earns its keep.
