# Phase 10 — Notifications WITHOUT Firebase

Status: **Firebase DROPPED by the user's decision, 2026-11-09 08:19 IST.**
Firebase console demanded two-step verification (2SV) on
mohairshu0000@gmail.com before any project could be created; the user
asked whether BOW BOW could be completed without Firebase, was told
yes with one tradeoff, and chose to drop it. No Firebase project was
ever created, no key was generated, no 2SV change was made.

## What Firebase did (only one job)
Push notifications — the HTTP notify step at the end of Flows B, C
and E, delivered through `POST /notify` (FCM HTTP v1). Everything
else in BOW BOW never touched Firebase.

## Replacement: pull-based local notifications
- The Android app runs a WorkManager job every 15 minutes
  (`ReminderWorker.kt`): it calls `GET /reminders?status=pending`,
  shows a local notification for each reminder whose `scheduled_at`
  has passed, then marks it `sent` via `PATCH /reminders/{id}`.
  It also checks `GET /tasks?status=awaiting_approval` and notifies
  once per task (tracked in SharedPreferences).
- Flows B, C and E had their HTTP notify steps removed (browser task,
  2026-11-09) and now complete end-to-end on their database steps
  alone. Flow C still ends by marking the reminder `sent` after its
  Delay — that step is now the flow's own work, not gated on a push.
- Backend: `POST /notify`, `POST /users/fcm-token` and the
  `send_notification` tool were removed (commit: "Drop Firebase").
  `PATCH /reminders/{id}` was added for the worker.
- `users.fcm_token` (migration 002) stays in the schema, unused and
  null — harmless; no migration to remove it.

## Tradeoff (told to the user, accepted)
- Reminders can arrive up to ~15 minutes late instead of instantly,
  and only while the app's background work runs (Android Doze can
  delay WorkManager jobs; a force-stopped app gets nothing).
- When the app is open, reminders are instant (the list is right there).
- No server-side push exists at all — nothing can wake the phone.
  If instant push is ever wanted later, Firebase (or any FCM-based
  provider) can be re-added; the flow specs in `automation/flows/`
  still carry the original notify-step design in their history.
