package com.sugarsaathi.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking

/**
 * Posts a reminder notification.
 *
 * Handles BOTH:
 *   - Smart Reminders, when input data carries a reminder id. It looks the
 *     reminder up, posts a notification naming its type and title, then
 *     schedules the next occurrence - that self-chaining is what makes a
 *     repeating wall-clock time work under WorkManager.
 *   - The original generic daily nudge, when no id is supplied. Kept so the
 *     existing PeriodicWorkRequest in MainActivity keeps behaving as before.
 */
@SuppressLint("MissingPermission")
class ReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        createNotificationChannel()

        if (!notificationsAllowed()) {
            android.util.Log.d("GLYCOREMIND", "Permission NOT granted - skipping")
            return Result.success()
        }

        val reminderId = inputData.getLong(ReminderScheduler.KEY_REMINDER_ID, -1L)

        return if (reminderId > 0) {
            handleSmartReminder(reminderId)
        } else {
            showGenericNudge()
            Result.success()
        }
    }

    // ── Smart reminder ─────────────────────────────────────

    private fun handleSmartReminder(id: Long): Result {
        val dao = AppDatabase.getInstance(applicationContext).reminderDao()

        // Worker runs on a background thread already, so blocking is safe here.
        val reminder = runBlocking { dao.getById(id) }

        if (reminder == null) {
            android.util.Log.d("GLYCOREMIND", "Reminder $id no longer exists")
            return Result.success()
        }

        if (!reminder.enabled) {
            android.util.Log.d("GLYCOREMIND", "Reminder $id is off - not showing")
            return Result.success()
        }

        val label = applicationContext.getString(reminder.typeEnum.labelRes)

        val body = buildString {
            append(label)
            if (reminder.notes.isNotBlank()) {
                append(" · ")
                append(reminder.notes)
            }
        }

        notify(
            // Distinct id per reminder so several can coexist rather than
            // overwriting each other in the shade.
            notificationId = (NOTIFICATION_ID_BASE + id).toInt(),
            title = "${reminder.typeEnum.emoji}  ${reminder.title}",
            body = body
        )

        // Chain the next occurrence. A ONCE reminder returns null from
        // nextTriggerAt(), so this simply schedules nothing.
        ReminderScheduler.schedule(applicationContext, reminder)

        return Result.success()
    }

    // ── Original generic daily nudge ───────────────────────

    private fun showGenericNudge() {
        notify(
            notificationId = NOTIFICATION_ID,
            title = applicationContext.getString(R.string.app_name),
            body = applicationContext.getString(R.string.reminder_generic_body)
        )
        android.util.Log.d("GLYCOREMIND", "Generic reminder shown")
    }

    // ── Shared ─────────────────────────────────────────────

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun notify(notificationId: Int, title: String, body: String) {
        // Tapping the notification opens the app rather than doing nothing.
        val launch = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        manager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = applicationContext.getString(R.string.reminder_channel_desc)
        }

        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder_channel"
        const val NOTIFICATION_ID = 1001

        // Smart reminder ids start well above the generic one so they can
        // never collide with it.
        const val NOTIFICATION_ID_BASE = 20000L
    }
}