package com.sugarsaathi.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

/**
 * Add or edit one reminder.
 *
 * @param existing null when creating. When editing, every field is pre-filled
 *                 and saving keeps the same row id so the schedule is replaced
 *                 rather than duplicated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditScreen(
    existing: Reminder?,
    reminderViewModel: ReminderViewModel,
    onDone: () -> Unit
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var type by remember { mutableStateOf(existing?.typeEnum ?: ReminderType.GLUCOSE) }
    var repeat by remember { mutableStateOf(existing?.repeatEnum ?: RepeatMode.DAILY) }
    var hour by remember { mutableIntStateOf(existing?.hour ?: 8) }
    var minute by remember { mutableIntStateOf(existing?.minute ?: 0) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var days by remember {
        mutableStateOf(existing?.selectedDays?.toSet() ?: emptySet())
    }
    var dateMillis by remember {
        mutableLongStateOf(
            existing?.dateMillis?.takeIf { it > 0 } ?: System.currentTimeMillis()
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

        TopAppBar(
            title = {
                Text(
                    stringResource(
                        if (existing == null) R.string.rem_new else R.string.rem_edit
                    ),
                    fontSize = 18.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = onDone) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = TealGreen,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            // ── Type ───────────────────────────────────────
            SectionLabel(stringResource(R.string.rem_field_type))
            Spacer(Modifier.height(8.dp))
            ReminderType.entries.forEach { option ->
                TypeRow(
                    type = option,
                    selected = type == option,
                    onClick = {
                        type = option
                        // Only prefill the title while it is still untouched,
                        // so switching type never overwrites what was typed.
                        if (title.isBlank() || title == defaultTitleFor(context, type)) {
                            title = defaultTitleFor(context, option)
                        }
                    }
                )
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(20.dp))

            // ── Title ──────────────────────────────────────
            SectionLabel(stringResource(R.string.rem_field_title))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(stringResource(R.string.rem_title_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )

            Spacer(Modifier.height(20.dp))

            // ── Time ───────────────────────────────────────
            SectionLabel(stringResource(R.string.rem_field_time))
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m -> hour = h; minute = m },
                        hour, minute, false
                    ).show()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, TealGreen)
            ) {
                Text(
                    LocalTime.of(hour, minute)
                        .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealGreen
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Repeat ─────────────────────────────────────
            SectionLabel(stringResource(R.string.rem_field_repeat))
            Spacer(Modifier.height(8.dp))
            RepeatMode.entries.forEach { option ->
                SelectableCard(
                    title = stringResource(repeatLabel(option)),
                    subtitle = "",
                    isSelected = repeat == option,
                    onClick = {
                        repeat = option
                        // WEEKLY with nothing chosen would never fire, so seed
                        // it with today rather than leaving a dead reminder.
                        if (option == RepeatMode.WEEKLY && days.isEmpty()) {
                            days = setOf(LocalDate.now().dayOfWeek)
                        }
                    }
                )
                Spacer(Modifier.height(6.dp))
            }

            // ── Date, only for ONCE ────────────────────────
            if (repeat == RepeatMode.ONCE) {
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.rem_field_date))
                Spacer(Modifier.height(8.dp))

                val chosen = Instant.ofEpochMilli(dateMillis)
                    .atZone(ZoneId.systemDefault()).toLocalDate()

                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
                        DatePickerDialog(
                            context,
                            { _, y, mo, d ->
                                dateMillis = Calendar.getInstance().apply {
                                    set(y, mo, d, 0, 0, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).apply {
                            // A reminder in the past can never fire.
                            datePicker.minDate = System.currentTimeMillis() - 86_400_000
                        }.show()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TealGreen)
                ) {
                    Text(
                        chosen.format(
                            DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
                        ),
                        fontSize = 15.sp,
                        color = TealGreen
                    )
                }
            }

            // ── Weekdays, for WEEKLY and CUSTOM ────────────
            if (repeat == RepeatMode.WEEKLY || repeat == RepeatMode.CUSTOM) {
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.rem_field_days))
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DayOfWeek.entries.forEach { day ->
                        DayChip(
                            day = day,
                            selected = day in days,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                days = if (day in days) days - day else days + day
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Notes ──────────────────────────────────────
            SectionLabel(stringResource(R.string.rem_field_notes))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text(stringResource(R.string.rem_notes_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )

            Spacer(Modifier.height(24.dp))
        }

        // ── Save ───────────────────────────────────────────
        val daysInvalid =
            (repeat == RepeatMode.WEEKLY || repeat == RepeatMode.CUSTOM) && days.isEmpty()

        Column(modifier = Modifier.padding(16.dp)) {
            if (daysInvalid) {
                Text(
                    stringResource(R.string.rem_pick_a_day),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    reminderViewModel.save(
                        Reminder(
                            id = existing?.id ?: 0L,
                            title = title.trim(),
                            type = type.name,
                            hour = hour,
                            minute = minute,
                            repeatMode = repeat.name,
                            dateMillis = if (repeat == RepeatMode.ONCE) dateMillis else 0L,
                            daysOfWeek = days.sortedBy { it.value }
                                .joinToString(",") { it.value.toString() },
                            notes = notes.trim(),
                            enabled = existing?.enabled ?: true,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
                shape = RoundedCornerShape(14.dp),
                enabled = title.isNotBlank() && !daysInvalid
            ) {
                Text(
                    stringResource(R.string.rem_save),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D5A44))
}

@Composable
private fun TypeRow(type: ReminderType, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFE1F5EE)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 0.5.dp,
            color = if (selected) TealGreen else Color.LightGray
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(type.emoji, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(type.labelRes),
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Text("✓", color = TealGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DayChip(
    day: DayOfWeek,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color(0xFFE1F5EE) else Color.Transparent,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) TealGreen else Color.LightGray
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                // First letter only - seven full names never fit on a phone.
                day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) TealGreen else Color.Gray
            )
        }
    }
}

private fun repeatLabel(mode: RepeatMode): Int = when (mode) {
    RepeatMode.ONCE -> R.string.rem_repeat_once
    RepeatMode.DAILY -> R.string.rem_repeat_daily
    RepeatMode.WEEKLY -> R.string.rem_repeat_weekly
    RepeatMode.CUSTOM -> R.string.rem_repeat_custom
}

private fun defaultTitleFor(context: android.content.Context, type: ReminderType): String =
    context.getString(type.labelRes)