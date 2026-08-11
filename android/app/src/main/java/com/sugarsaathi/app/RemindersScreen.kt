package com.sugarsaathi.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    reminderViewModel: ReminderViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Reminder) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { reminderViewModel.init(context) }

    val reminders by reminderViewModel.reminders.collectAsState()
    var pendingDelete by remember { mutableStateOf<Reminder?>(null) }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.rem_delete_title)) },
            text = { Text(stringResource(R.string.rem_delete_confirm, target.title)) },
            confirmButton = {
                TextButton(onClick = {
                    reminderViewModel.delete(target)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.rem_delete), color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

        TopAppBar(
            title = { Text(stringResource(R.string.reminders_title), fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) {
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

        Column(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.rem_add),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⏰", fontSize = 44.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.rem_empty),
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(reminders, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        onToggle = { on -> reminderViewModel.setEnabled(reminder, on) },
                        onEdit = { onEdit(reminder) },
                        onDelete = { pendingDelete = reminder }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // A disabled reminder is dimmed rather than hidden, so it stays findable
    // when the user wants to turn it back on.
    val alpha = if (reminder.enabled) 1f else 0.5f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (reminder.enabled) Color(0xFFE1F5EE)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (reminder.enabled) TealGreen.copy(alpha = 0.35f) else Color.LightGray
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEdit() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(reminder.typeEnum.emoji, fontSize = 24.sp)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D5A44).copy(alpha = alpha)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = scheduleSummary(reminder),
                    fontSize = 12.5.sp,
                    color = Color.Gray.copy(alpha = alpha)
                )
                if (reminder.notes.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = reminder.notes,
                        fontSize = 11.5.sp,
                        color = Color.Gray.copy(alpha = alpha),
                        maxLines = 1
                    )
                }
            }

            Switch(
                checked = reminder.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = TealGreen
                )
            )

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.rem_delete),
                    tint = Color.Gray
                )
            }
        }
    }
}

/** "Every day • 8:00 AM" / "Mon, Wed, Fri • 6:00 PM" */
@Composable
fun scheduleSummary(reminder: Reminder): String {
    val time = LocalTime.of(
        reminder.hour.coerceIn(0, 23),
        reminder.minute.coerceIn(0, 59)
    ).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

    val repeat = when (reminder.repeatEnum) {
        RepeatMode.ONCE -> stringResource(R.string.rem_repeat_once)
        RepeatMode.DAILY -> stringResource(R.string.rem_repeat_daily)
        RepeatMode.WEEKLY, RepeatMode.CUSTOM -> {
            val days = reminder.selectedDays
            if (days.isEmpty()) stringResource(R.string.rem_repeat_daily)
            else days.sortedBy { it.value }.joinToString(", ") { shortDay(it) }
        }
    }

    return "$repeat  •  $time"
}

private fun shortDay(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.SHORT, Locale.getDefault())