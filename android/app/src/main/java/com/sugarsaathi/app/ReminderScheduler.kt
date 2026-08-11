package com.sugarsaathi.app

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules reminders with WorkManager.
 *
 * WHY NOT AlarmManager: from Android 12 an exact alarm needs
 * SCHEDULE_EXACT_ALARM, which Play Store only grants to genuine alarm-clock and
 * calendar apps. A medication reminder does not qualify. WorkManager fires
 * within a few minutes of the target, which is fine for "take your medicine at
 * 9pm", and it survives reboot without extra work.
 *
 * HOW REPEATS WORK: a PeriodicWorkRequest cannot be pinned to a wall-clock
 * time, so each reminder is scheduled as a ONE-TIME request for its next
 * occurrence. After firing, the worker schedules the following one. That chain
 * is what makes "every day at 8am" actually land at 8am.
 *
 * DUPLICATE PREVENTION: each reminder owns a unique work name, so enqueuing
 * with REPLACE can never leave two schedules for the same reminder alive.
 */
object ReminderScheduler {

    const val KEY_REMINDER_ID = "reminder_id"

    private fun workName(id: Long) = "glyco_reminder_$id"

    /**
     * Schedule (or reschedule) one reminder. Cancels any existing schedule for
     * it first, so edits and toggles cannot leave a stale alarm behind.
     */
    fun schedule(context: Context, reminder: Reminder) {
        val wm = WorkManager.getInstance(context.applicationContext)

        if (!reminder.enabled) {
            wm.cancelUniqueWork(workName(reminder.id))
            return
        }

        val nextAt = reminder.nextTriggerAt()
        if (nextAt == null) {
            // A one-off whose moment has passed. Nothing more to schedule.
            wm.cancelUniqueWork(workName(reminder.id))
            return
        }

        val delay = (nextAt - System.currentTimeMillis()).coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder().putLong(KEY_REMINDER_ID, reminder.id).build()
            )
            .addTag(TAG_ALL)
            .build()

        wm.enqueueUniqueWork(
            workName(reminder.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, reminderId: Long) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(workName(reminderId))
    }

    /**
     * Reschedule everything enabled. Called from BootReceiver and on app start.
     * WorkManager already persists across reboot, so this is belt and braces -
     * but it also repairs anything lost to a force-stop or an app data clear.
     */
    suspend fun rescheduleAll(context: Context) {
        val dao = AppDatabase.getInstance(context).reminderDao()
        dao.getEnabledOnce().forEach { schedule(context, it) }
    }

    const val TAG_ALL = "glyco_reminders"
}