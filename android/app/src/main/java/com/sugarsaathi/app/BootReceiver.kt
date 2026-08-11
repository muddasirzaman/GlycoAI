package com.sugarsaathi.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms reminders after the device restarts.
 *
 * WorkManager does persist its own queue across reboot, so this is a safety
 * net rather than the primary mechanism - it also repairs schedules lost to a
 * force-stop, which WorkManager does NOT survive on some manufacturer builds
 * (Xiaomi, Oppo and Vivo are the usual offenders, which matters here).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        // goAsync keeps the receiver alive long enough to touch the database.
        val pending = goAsync()
        val app = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.rescheduleAll(app)
                android.util.Log.d("GLYCOREMIND", "Reminders rescheduled after $action")
            } catch (e: Exception) {
                android.util.Log.e("GLYCOREMIND", "Reschedule failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}