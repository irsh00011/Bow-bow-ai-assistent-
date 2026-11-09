#!/bin/bash
# BOW BOW — webhook smoke tests. URLs come from backend/.env (the /sync
# form — the plain webhook URL only returns the async ack 200 {}).
# Run: set -a; source ../backend/.env; set +a; bash test-webhooks.sh
# A flow is NOT "working" until its real call returns the expected result.
# NOTE: Firebase was dropped 2026-11-09 — the HTTP notify steps were
# removed from Flows B/C/E. Notifications are pull-based: the Android app
# polls GET /reminders and GET /tasks and shows local notifications.
set -u
: "${ACTIVEPIECES_WEBHOOK_A:?set ACTIVEPIECES_WEBHOOK_A}"
: "${ACTIVEPIECES_WEBHOOK_B:?set ACTIVEPIECES_WEBHOOK_B}"
: "${ACTIVEPIECES_WEBHOOK_C:?set ACTIVEPIECES_WEBHOOK_C}"
: "${ACTIVEPIECES_WEBHOOK_D:?set ACTIVEPIECES_WEBHOOK_D}"

# Real test IDs (created 2026-11-08 via Supabase SQL editor):
TEST_USER_ID="5897b826-feff-4dd2-9841-f8d4b510f6bf"
TEST_TASK_ID="1b98b785-c8a7-4b73-a735-a0931cb6acad"

echo "== Flow A — Gmail summary =="
curl -s -X POST "$ACTIVEPIECES_WEBHOOK_A" -H 'Content-Type: application/json' \
  -d '{"user_id":"mohamed","limit":5,"unread_only":true}'; echo

echo "== Flow D — Calendar list (read-only) =="
curl -s -X POST "$ACTIVEPIECES_WEBHOOK_D" -H 'Content-Type: application/json' \
  -d '{"mode":"list","days_ahead":1,"user_id":"mohamed"}'; echo

echo "== Flow D — guard: unconfirmed create must return 403 =="
curl -s -X POST "$ACTIVEPIECES_WEBHOOK_D" -H 'Content-Type: application/json' \
  -d '{"mode":"create","title":"BOW BOW guard test","start":"2026-11-09T18:00:00+05:30","end":"2026-11-09T19:00:00+05:30","user_id":"mohamed"}'; echo

echo "== Flow B — complete the test task (end-to-end, no notify step) =="
curl -s -X POST "$ACTIVEPIECES_WEBHOOK_B" -H 'Content-Type: application/json' \
  -d "{\"task_id\":\"$TEST_TASK_ID\",\"status\":\"completed\",\"execution_result\":{\"summary\":\"test\"}}"; echo

echo "== Flow C — create a reminder (insert + Delay + mark sent) =="
SCHED=$(TZ=Asia/Calcutta date -d "+3 minutes" "+%Y-%m-%dT%H:%M:%S+05:30")
curl -s -X POST "$ACTIVEPIECES_WEBHOOK_C" -H 'Content-Type: application/json' \
  -d "{\"title\":\"BOW BOW test reminder\",\"scheduled_at\":\"$SCHED\",\"user_id\":\"$TEST_USER_ID\"}"; echo

echo "== Flow E — schedule trigger, no webhook; check in Activepieces Runs =="
echo "Runs every 6h (cron 0 */6 * * * Asia/Kolkata); verify no awaiting_approval tasks."
