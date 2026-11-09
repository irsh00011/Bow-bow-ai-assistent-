package com.irshad.bowbow

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime

/** Polls the backend for due reminders and tasks awaiting approval,
 *  and shows them as local notifications. This replaces server push
 *  (dropped 2026-11-09): nothing wakes the phone from the server side,
 *  so the app checks itself every 15 minutes (see MainActivity).
 *
 *  - Reminders: a due reminder is marked "sent" on the backend BEFORE
 *    the notification is shown, so a retry can never notify twice.
 *  - Tasks awaiting approval: notified once per task, tracked in
 *    SharedPreferences (the backend has no "notified" flag for them).
 *  - Network errors return Result.retry(); anything else succeeds so
 *    a bad row never kills the periodic job. */
class ReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val api = BowBowApi(Prefs.getBackendUrl(applicationContext))
            checkDueReminders(api)
            checkTasksAwaitingApproval(api)
            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "Reminder check failed (network): ${e.message}")
            Result.retry()
        } catch (e: Exception) {
            Log.w(TAG, "Reminder check failed: ${e.message}")
            Result.success()
        }
    }

    private suspend fun checkDueReminders(api: BowBowApi) {
        val now = Instant.now()
        for (reminder in api.listReminders()) {
            val dueAt = parseTime(reminder.scheduled_at)
            if (dueAt == null) {
                Log.w(TAG, "Skipping reminder ${reminder.id}: " +
                    "unparseable scheduled_at '${reminder.scheduled_at}'")
                continue
            }
            if (dueAt.isAfter(now)) continue
            // Mark sent first — a retry after this point never re-notifies.
            api.markReminderSent(reminder.id)
            showNotification(
                id = reminder.id.hashCode(),
                title = applicationContext.getString(R.string.app_name),
                text = reminder.title
            )
        }
    }

    private suspend fun checkTasksAwaitingApproval(api: BowBowApi) {
        val notified = getNotifiedTaskIds().toMutableSet()
        for (task in api.listTasksAwaitingApproval()) {
            if (task.id in notified) continue
            showNotification(
                id = task.id.hashCode(),
                title = "Task awaiting your approval",
                text = task.title
            )
            notified.add(task.id)
        }
        saveNotifiedTaskIds(notified)
    }

    private fun parseTime(value: String): Instant? {
        return try {
            Instant.parse(value)
        } catch (e: Exception) {
            try {
                OffsetDateTime.parse(value).toInstant()
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun showNotification(id: Int, title: String, text: String) {
        val ctx = applicationContext
        val manager =
            ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(ctx, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    private fun getNotifiedTaskIds(): Set<String> =
        applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_NOTIFIED_TASKS, emptySet())
            .orEmpty()

    private fun saveNotifiedTaskIds(ids: Set<String>) {
        applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_NOTIFIED_TASKS, ids)
            .apply()
    }

    companion object {
        const val WORK_NAME = "bowbow-reminder-checks"
        private const val TAG = "BowBowWorker"
        private const val CHANNEL_ID = "bowbow_reminders"
        private const val PREFS_NAME = "bowbow"
        private const val KEY_NOTIFIED_TASKS = "notified_task_ids"
    }
}
