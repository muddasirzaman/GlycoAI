package com.sugarsaathi.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Replaces the old 7-day-only SummaryCard.
 *
 * Toggle between Today / 7 days / 14 days / 30 days. The chart's own 7/30
 * chip stays as-is: the two controls do different jobs and a patient will
 * often want a wider chart than the window they're reading numbers from.
 *
 * Empty state uses the count to decide, not the average. A zero average
 * printed as "0 mg/dL" would look like a broken meter.
 */
@Composable
fun DashboardCard(viewModel: GlucoseViewModel) {

    // Recompute whenever readings change or the user flips the range. Using
    // the readings list as a key means a newly-added value shows up in the
    // stats immediately without any manual invalidation.
    val readings by viewModel.readings.collectAsState()
    var range by remember { mutableIntStateOf(7) }

    val stats = remember(readings, range) { viewModel.dashboardStats(range) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE1F5EE)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.dashboard_heading),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D5A44)
                )
            }
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RangeToggle(stringResource(R.string.range_today), range == 0)   { range = 0 }
                RangeToggle(stringResource(R.string.range_7d),    range == 7)   { range = 7 }
                RangeToggle(stringResource(R.string.range_14d),   range == 14)  { range = 14 }
                RangeToggle(stringResource(R.string.range_30d),   range == 30)  { range = 30 }
            }

            Spacer(Modifier.height(14.dp))

            if (stats.count == 0) {
                Text(
                    stringResource(R.string.dashboard_no_data),
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DashboardStat(stringResource(R.string.stat_average),
                    "${stats.average}", "mg/dL")
                DashboardStat(stringResource(R.string.stat_highest),
                    "${stats.highest}", "mg/dL")
                DashboardStat(stringResource(R.string.stat_lowest),
                    "${stats.lowest}", "mg/dL")
                DashboardStat(stringResource(R.string.stat_readings),
                    "${stats.count}", "")
            }

            Spacer(Modifier.height(16.dp))

            // In-range / above / below rows. Coloured bars are more honest
            // than a pie chart for three-way splits at small sizes.
            RangeBar(
                label = stringResource(R.string.range_in),
                percent = stats.inRangePercent,
                colour = Color(0xFF4CAF50)
            )
            Spacer(Modifier.height(6.dp))
            RangeBar(
                label = stringResource(R.string.range_above),
                percent = stats.aboveRangePercent,
                colour = Color(0xFFF44336)
            )
            Spacer(Modifier.height(6.dp))
            RangeBar(
                label = stringResource(R.string.range_below),
                percent = stats.belowRangePercent,
                colour = Color(0xFF2196F3)
            )

            if (stats.fastingAverage != null || stats.postMealAverage != null) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF0D5A44).copy(alpha = 0.15f))
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    stats.fastingAverage?.let {
                        DashboardStat(
                            stringResource(R.string.stat_fasting_avg),
                            "$it", "mg/dL"
                        )
                    }
                    stats.postMealAverage?.let {
                        DashboardStat(
                            stringResource(R.string.stat_postmeal_avg),
                            "$it", "mg/dL"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardStat(label: String, value: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D5A44))
        if (sub.isNotEmpty()) {
            Text(sub, fontSize = 9.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun RangeToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) TealGreen else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) TealGreen else Color.LightGray)
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else Color.Gray,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun RangeBar(label: String, percent: Int, colour: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 12.sp,
            color = Color.DarkGray,
            modifier = Modifier.width(80.dp)
        )
        // The bar itself. A percent of 0 still renders the track without a
        // fill, which is important on the empty split - the row must not
        // disappear or the layout jumps.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .background(colour.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
        ) {
            if (percent > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(percent / 100f)
                        .background(colour, RoundedCornerShape(5.dp))
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "$percent%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colour,
            modifier = Modifier.width(38.dp)
        )
    }
}