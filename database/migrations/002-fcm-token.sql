-- BOW BOW migration 002 — FCM device token on users (Phase 10)
-- Run in the Supabase SQL Editor. The Android app writes the token
-- here on first launch; POST /notify reads it to push reminders and
-- task/follow-up notifications to the phone.
alter table public.users
  add column if not exists fcm_token text;

-- Only the backend (service key) writes the token; the anon key must
-- NOT be able to read or write it, so no anon policy is added here.
-- If the app ever needs to write its own token, add a narrow policy
-- for that column only, or route the write through POST /notify's
-- future companion endpoint.
