package com.sugarsaathi.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Hba1cHistoryScreen(
    viewModel: Hba1cViewModel,
    onBack: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {

        TopAppBar(
            title = { Text(stringResource(R.string.hba1c_screen_title), fontSize = 18.sp) },
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

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Hba1cSummaryCard(entries) }

            item {
                Text(
                    stringResource(R.string.hba1c_lab_only_note),
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            if (entries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.hba1c_none_yet),
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                items(entries, key = { it.id }) { entry ->
                    Hba1cCard(entry = entry, onDelete = { viewModel.delete(entry) })
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Button(
                onClick = { showAdd = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.hba1c_add_button),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }

    if (showAdd) {
        AddHba1cDialog(
            onDismiss = { showAdd = false },
            onSave = { value, dateMillis, note ->
                viewModel.save(value, dateMillis, note) { showAdd = false }
            }
        )
    }
}

/**
 * Current / Previous / Trend at the top of the screen.
 *
 * Trend arrow rules:
 *   diff <= -0.3  down  (improving for most patients)
 *   diff >=  0.3  up
 *   otherwise     level  (0.2 is inside typical lab variance and not a real
 *                         trend a patient should act on)
 */
@Composable
private fun Hba1cSummaryCard(entries: List<Hba1cEntry>) {
    val current = entries.getOrNull(0)
    val previous = entries.getOrNull(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE1F5EE)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.hba1c_summary_heading),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0D5A44)
            )
            Spacer(Modifier.height(12.dp))

            if (current == null) {
                Text(
                    stringResource(R.string.hba1c_no_entries_yet),
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.hba1c_current),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        "${formatValue(current.value)}%",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D5A44)
                    )
                    Text(formatDate(current.timestamp), fontSize = 10.sp, color = Color.Gray)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.hba1c_previous),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        if (previous != null) "${formatValue(previous.value)}%"
                        else "—",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (previous != null) Color(0xFF0D5A44) else Color.LightGray
                    )
                    Text(
                        previous?.let { formatDate(it.timestamp) } ?: "",
                        fontSize = 10.sp, color = Color.Gray
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.hba1c_trend),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    if (previous != null) {
                        val diff = current.value - previous.value
                        val (arrow, colour, label) = when {
                            diff <= -0.3f -> Triple("↓",
                                Color(0xFF2E7D32),
                                stringResource(R.string.hba1c_trend_down))
                            diff >= 0.3f -> Triple("↑",
                                Color(0xFFC62828),
                                stringResource(R.string.hba1c_trend_up))
                            else -> Triple("→",
                                Color(0xFF616161),
                                stringResource(R.string.hba1c_trend_level))
                        }
                        Text(arrow, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colour)
                        Text(label, fontSize = 10.sp, color = colour)
                        val sign = if (diff > 0) "+" else ""
                        Text(
                            "$sign${formatValue(abs(diff).let { if (diff < 0) -it else it })}%",
                            fontSize = 10.sp, color = Color.Gray
                        )
                    } else {
                        Text("—", fontSize = 24.sp, color = Color.LightGray)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hba1cCard(entry: Hba1cEntry, onDelete: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.hba1c_delete_title)) },
            text = { Text(stringResource(R.string.hba1c_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.hba1c_delete_ok), color = Color(0xFFC62828))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.hba1c_cancel), color = TealGreen)
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${formatValue(entry.value)}%",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    // Source pill. Reserved for the future "estimated" case; today
                    // only "lab" ever renders, but the branch is here so we don't
                    // silently show an unlabelled estimate later.
                    val pillColor = if (entry.source == "lab") Color(0xFF2E7D32)
                    else Color(0xFFEF6C00)
                    val pillLabel =
                        if (entry.source == "lab") stringResource(R.string.hba1c_source_lab)
                        else stringResource(R.string.hba1c_source_estimated)
                    Box(
                        modifier = Modifier
                            .background(pillColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(BorderStroke(0.5.dp, pillColor.copy(alpha = 0.4f)),
                                RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(pillLabel, fontSize = 10.sp, color = pillColor,
                            fontWeight = FontWeight.Medium)
                    }
                }
                Text(
                    "📅 ${formatDate(entry.timestamp)}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                if (entry.note.isNotBlank()) {
                    Text("📝 ${entry.note}", fontSize = 12.sp, color = Color.Gray)
                }
            }
            IconButton(onClick = { confirming = true }) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Gray)
            }
        }
    }
}

/**
 * Modal entry form.
 *
 * The date is chosen from three quick options plus a plain YYYY-MM-DD text
 * field. A real DatePickerDialog would be nicer, but is a lot of extra code
 * for a value entered once every three months. Text with quick buttons is
 * fine here and doesn't need any new dependencies.
 */
@Composable
private fun AddHba1cDialog(
    onDismiss: () -> Unit,
    onSave: (Float, Long, String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(defaultDateText()) }
    var note by remember { mutableStateOf("") }

    val parsedValue = value.toFloatOrNull()
    val parsedDate = parseDate(dateText)
    // Plausibility gate. Human HbA1c below 3.5 or above 20 is not physiological
    // and the value was almost certainly a typo. Better to make the patient
    // check than to insert junk that will drive a wrong trend arrow forever.
    val valueOk = parsedValue != null && parsedValue in 3.5f..20f
    val dateOk = parsedDate != null && parsedDate <= System.currentTimeMillis()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hba1c_add_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.hba1c_add_note),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = value,
                    onValueChange = { s ->
                        value = s.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text(stringResource(R.string.hba1c_value_label)) },
                    placeholder = { Text("6.5") },
                    isError = value.isNotEmpty() && !valueOk,
                    supportingText = {
                        if (value.isNotEmpty() && !valueOk) {
                            Text(
                                stringResource(R.string.hba1c_value_error),
                                fontSize = 11.sp,
                                color = Color(0xFFC62828)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))

                Text(
                    stringResource(R.string.hba1c_date_label),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF0D5A44)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickDateChip(stringResource(R.string.hba1c_today)) {
                        dateText = defaultDateText()
                    }
                    QuickDateChip(stringResource(R.string.hba1c_1mo)) {
                        dateText = dateTextForOffset(-1)
                    }
                    QuickDateChip(stringResource(R.string.hba1c_3mo)) {
                        dateText = dateTextForOffset(-3)
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("YYYY-MM-DD") },
                    isError = dateText.isNotEmpty() && !dateOk,
                    supportingText = {
                        if (dateText.isNotEmpty() && !dateOk) {
                            Text(
                                stringResource(R.string.hba1c_date_error),
                                fontSize = 11.sp,
                                color = Color(0xFFC62828)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.hba1c_note_label)) },
                    placeholder = { Text(stringResource(R.string.hba1c_note_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(parsedValue!!, parsedDate!!, note.trim()) },
                enabled = valueOk && dateOk,
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen)
            ) {
                Text(stringResource(R.string.hba1c_save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.hba1c_cancel), color = TealGreen)
            }
        }
    )
}

@Composable
private fun QuickDateChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFE1F5EE),
        border = BorderStroke(1.dp, TealGreen)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = TealGreen,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

private fun formatValue(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

private fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))

private fun defaultDateText(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun dateTextForOffset(months: Int): String {
    val c = Calendar.getInstance()
    c.add(Calendar.MONTH, months)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
}

private fun parseDate(text: String): Long? = try {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        .parse(text.trim())?.time
} catch (_: Exception) { null }