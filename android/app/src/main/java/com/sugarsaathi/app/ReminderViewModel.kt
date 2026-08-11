package com.sugarsaathi.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ReminderViewModel : ViewModel() {

    private var dao: ReminderDao? = null
    private var appContext: Context? = null

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders

    /** Call once from the screen, same pattern as GlucoseViewModel.init(). */
    fun init(context: Context) {
        if (dao == null) {
            appContext = context.applicationContext
            dao = AppDatabase.getInstance(context).reminderDao()
            observe()
            // Repair any schedule lost to a force-stop or data clear.
            viewModelScope.launch {
                appContext?.let { ReminderScheduler.rescheduleAll(it) }
            }
        }
    }

    private fun observe() {
        viewModelScope.launch {
            dao?.getAll()?.collect { _reminders.value = it }
        }
    }

    /**
     * Insert or update. Scheduling always happens AFTER the write, using the
     * row that now exists - otherwise a new reminder would be scheduled with
     * id 0 and every reminder would share one work name.
     */
    fun save(reminder: Reminder) {
        val ctx = appContext ?: return
        viewModelScope.launch {
            val saved = if (reminder.id == 0L) {
                val newId = dao?.insert(reminder) ?: return@launch
                reminder.copy(id = newId)
            } else {
                dao?.update(reminder)
                reminder
            }
            ReminderScheduler.schedule(ctx, saved)
        }
    }

    fun setEnabled(reminder: Reminder, enabled: Boolean) {
        val ctx = appContext ?: return
        viewModelScope.launch {
            dao?.setEnabled(reminder.id, enabled)
            // schedule() cancels when disabled, so this covers both directions.
            ReminderScheduler.schedule(ctx, reminder.copy(enabled = enabled))
        }
    }

    fun delete(reminder: Reminder) {
        val ctx = appContext ?: return
        viewModelScope.launch {
            // Cancel first: if the delete succeeded but the cancel did not, a
            // notification would fire for a reminder that no longer exists.
            ReminderScheduler.cancel(ctx, reminder.id)
            dao?.delete(reminder)
        }
    }
}