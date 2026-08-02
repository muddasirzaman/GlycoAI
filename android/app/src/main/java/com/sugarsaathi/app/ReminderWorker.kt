package com.sugarsaathi.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters


@SuppressLint("MissingPermission")
class ReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {

        createNotificationChannel()

        // Stop if notification permission is not granted.
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        // If this worker was scheduled for a specific medicine, inputData
        // will carry its name. If not, we fall back to the original
        // generic reminder — this covers users who picked "None".
        val medicationName = inputData.getString(KEY_MEDICATION_NAME)

        val title: String
        val text: String
        val notificationId: Int

        if (medicationName != null) {
            title = "Medicine Reminder"
            text = "Time to take your $medicationName"
            notificationId = NOTIFICATION_ID_MED_BASE + medicationName.hashCode()
        } else {
            title = "SugarSaathi Reminder"
            text = "Take a moment to check your diabetes care today."
            notificationId = NOTIFICATION_ID
        }

        val notification = NotificationCompat.Builder(
            applicationContext,
            CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager =
            applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        notificationManager.notify(
            notificationId,
            notification
        )

        return Result.success()
    }

    private fun createNotificationChannel() {

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Daily SugarSaathi health reminders"
        }

        val notificationManager =
            applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder_channel"
        const val NOTIFICATION_ID = 1001
        const val KEY_MEDICATION_NAME = "medication_name"
        const val NOTIFICATION_ID_MED_BASE = 2000000 // keeps medicine notification IDs separate from the generic one
    }
}