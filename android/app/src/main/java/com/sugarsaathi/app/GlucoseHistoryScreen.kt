package com.sugarsaathi.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseHistoryScreen(
    glucoseViewModel: GlucoseViewModel,
    profile: UserProfileData,
    onBack: () -> Unit
) {
    val readings by glucoseViewModel.readings.collectAsState()
    val summary = remember(readings) { glucoseViewModel.sevenDaySummary() }
    val latest = readings.firstOrNull()   // list is newest-first

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var showRangeDialog by remember { mutableStateOf(false) }
    val exportFailed = stringResource(R.string.report_failed)

    fun export(days: Int) {
        showRangeDialog = false
        exporting = true
        scope.launch {
            // Drawing and file IO - keep it off the main thread or the UI
            // freezes while a large report is built.
            val uri = withContext(Dispatchers.IO) {
                GlucoseReport.generate(context, profile, readings, days)
            }
            exporting = false
            if (uri != null) {
                GlucoseReport.share(context, uri)
            } else {
                Toast.makeText(context, exportFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showRangeDialog) {
        AlertDialog(
            onDismissRequest = { showRangeDialog = false },
            title = { Text(stringResource(R.string.report_title)) },
            text = { Text(stringResource(R.string.report_choose_range)) },
            confirmButton = {
                TextButton(onClick = { export(30) }) {
                    Text(stringResource(R.string.chart_30_days), color = TealGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { export(7) }) {
                    Text(stringResource(R.string.chart_7_days), color = TealGreen)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

        TopAppBar(
            title = { Text(stringResource(R.string.glucose_history), fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            actions = {
                IconButton(
                    enabled = readings.isNotEmpty() && !exporting,
                    onClick = { showRangeDialog = true }
                ) {
                    if (exporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.report_share),
                            tint = Color.White
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = TealGreen,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White
            )
        )

        if (readings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.no_readings_yet),
                    fontSize = 15.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    GlucoseChart(
                        readings = readings,
                        displayUnit = readings.firstOrNull()?.unit ?: "mg/dL"
                    )
                }

                item {
                    latest?.let { r ->
                        SuggestionCard(suggestionFor(r.value, r.unit, r.timestamp))
                    }
                }

                item {
                    SummaryCard(summary)
                }

                items(readings) { reading ->
                    ReadingCard(
                        reading = reading,
                        onDelete = { glucoseViewModel.deleteReading(reading) }
                    )
                }
            }
        }
    }
}

@Composable
fun ReadingCard(reading: GlucoseReading, onDelete: () -> Unit) {

    val statusColor = glucoseStatusColor(reading.value, reading.unit)
    val statusLabel = glucoseStatusLabel(reading.value, reading.unit)

    val dateFmt = remember { SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dateText = dateFmt.format(Date(reading.timestamp))
    val timeText = timeFmt.format(Date(reading.timestamp))

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
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(statusColor, CircleShape)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${reading.value}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(reading.unit, fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.width(8.dp))
                    Text(statusLabel, fontSize = 12.sp, color = statusColor,
                        fontWeight = FontWeight.Medium)
                }
                Text("🏷️ ${reading.readingType}", fontSize = 13.sp, color = Color.DarkGray)
                Text("📅 $dateText", fontSize = 11.sp, color = Color.Gray)
                Text("🕒 $timeText", fontSize = 11.sp, color = Color.Gray)
                if (reading.note.isNotEmpty()) {
                    Text("📝 ${reading.note}", fontSize = 12.sp, color = Color.Gray)
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
            }
        }
    }
}

// Returns just the color (plain function, no strings)
fun glucoseStatusColor(value: Float, unit: String): Color {
    val mgdl = if (unit == "mmol/L") value * 18f else value
    return when {
        mgdl < 70   -> Color(0xFF2196F3)
        mgdl <= 140 -> Color(0xFF4CAF50)
        mgdl <= 180 -> Color(0xFFFFC107)
        else        -> Color(0xFFF44336)
    }
}

// Returns the translated label (composable, so stringResource works)
@Composable
fun glucoseStatusLabel(value: Float, unit: String): String {
    val mgdl = if (unit == "mmol/L") value * 18f else value
    return when {
        mgdl < 70   -> stringResource(R.string.status_low)
        mgdl <= 140 -> stringResource(R.string.status_normal)
        mgdl <= 180 -> stringResource(R.string.status_borderline)
        else        -> stringResource(R.string.status_high)
    }
}

@Composable
fun SummaryCard(summary: GlucoseSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE1F5EE)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.last_7_days),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0D5A44)
            )
            Spacer(Modifier.height(12.dp))

            if (summary.count == 0) {
                Text(
                    stringResource(R.string.no_readings_7days),
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SummaryStat(stringResource(R.string.stat_average), "${summary.average}", "mg/dL")
                    SummaryStat(stringResource(R.string.stat_highest), "${summary.highest}", "mg/dL")
                    SummaryStat(stringResource(R.string.stat_lowest), "${summary.lowest}", "mg/dL")
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SummaryStat(stringResource(R.string.stat_readings), "${summary.count}", "")
                    SummaryStat(stringResource(R.string.stat_in_range), "${summary.inRangePercent}%", "70-180")
                }
            }
        }
    }
}

@Composable
fun SummaryStat(label: String, value: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D5A44))
        if (sub.isNotEmpty()) {
            Text(sub, fontSize = 9.sp, color = Color.Gray)
        }
    }
}