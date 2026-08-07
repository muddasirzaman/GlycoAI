package com.sugarsaathi.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DAY_MS = 24L * 60 * 60 * 1000

// Target range in mg/dL. Matches sevenDaySummary() and glucoseStatusColor()
// so the chart, the stats card and the reading list never disagree.
private const val TARGET_LOW = 70f
private const val TARGET_HIGH = 180f

/**
 * Line chart of glucose readings over time.
 *
 * Points are positioned by their ACTUAL timestamp, not evenly spaced. Even
 * spacing is easier to draw but lies: three readings in one morning followed by
 * a week of silence would look like a steady trend. The line also breaks across
 * long gaps rather than drawing a straight line through days with no data.
 */
@Composable
fun GlucoseChart(
    readings: List<GlucoseReading>,
    displayUnit: String,
    modifier: Modifier = Modifier
) {
    var rangeDays by remember { mutableIntStateOf(7) }

    val now = remember(readings) { System.currentTimeMillis() }
    val cutoff = now - rangeDays * DAY_MS

    // Oldest first for drawing; everything normalised to mg/dL for the maths.
    val points = remember(readings, rangeDays) {
        readings
            .filter { it.timestamp >= cutoff }
            .sortedBy { it.timestamp }
            .map { it.timestamp to (if (it.unit == "mmol/L") it.value * 18f else it.value) }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.chart_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D5A44)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RangeChip(stringResource(R.string.chart_7_days), rangeDays == 7) {
                        rangeDays = 7
                    }
                    RangeChip(stringResource(R.string.chart_30_days), rangeDays == 30) {
                        rangeDays = 30
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.chart_no_data),
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            } else {
                ChartCanvas(
                    points = points,
                    rangeStart = cutoff,
                    rangeEnd = now,
                    rangeDays = rangeDays,
                    displayUnit = displayUnit
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    LegendDot(Color(0xFF4CAF50).copy(alpha = 0.18f), stringResource(R.string.chart_legend_target))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.chart_reading_count, points.size),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartCanvas(
    points: List<Pair<Long, Float>>,
    rangeStart: Long,
    rangeEnd: Long,
    rangeDays: Int,
    displayUnit: String
) {
    val density = LocalDensity.current
    val isMmol = displayUnit == "mmol/L"

    // Vertical scale always includes the target band, so "in range" is
    // visible even when every reading sits far outside it.
    val values = points.map { it.second }
    val rawMin = minOf(values.min(), TARGET_LOW)
    val rawMax = maxOf(values.max(), TARGET_HIGH)
    val pad = ((rawMax - rawMin) * 0.15f).coerceAtLeast(20f)
    val yMin = (rawMin - pad).coerceAtLeast(0f)
    val yMax = rawMax + pad

    // A straight line across a multi-day gap implies readings that never
    // happened, so break the path instead.
    val gapThreshold = if (rangeDays <= 7) DAY_MS else 3 * DAY_MS

    val labelPaint = remember(density) {
        android.graphics.Paint().apply {
            color = Color(0xFF9E9E9E).toArgb()
            textSize = with(density) { 9.sp.toPx() }
            isAntiAlias = true
        }
    }
    val labelPaintRight = remember(density) {
        android.graphics.Paint().apply {
            color = Color(0xFF9E9E9E).toArgb()
            textSize = with(density) { 9.sp.toPx() }
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
    }

    val dateFmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val leftPad = with(density) { 34.dp.toPx() }
        val bottomPad = with(density) { 18.dp.toPx() }
        val topPad = with(density) { 6.dp.toPx() }
        val rightPad = with(density) { 6.dp.toPx() }

        val plotW = size.width - leftPad - rightPad
        val plotH = size.height - topPad - bottomPad

        fun yFor(v: Float) = topPad + plotH * (1f - (v - yMin) / (yMax - yMin))
        fun xFor(t: Long) =
            leftPad + plotW * ((t - rangeStart).toFloat() / (rangeEnd - rangeStart).toFloat())

        // Target band
        val bandTop = yFor(TARGET_HIGH)
        val bandBottom = yFor(TARGET_LOW)
        drawRect(
            color = Color(0xFF4CAF50).copy(alpha = 0.13f),
            topLeft = androidx.compose.ui.geometry.Offset(leftPad, bandTop),
            size = androidx.compose.ui.geometry.Size(plotW, bandBottom - bandTop)
        )

        // Horizontal guides at the band edges, plus axis labels
        listOf(TARGET_LOW, TARGET_HIGH).forEach { v ->
            val y = yFor(v)
            drawLine(
                color = Color(0xFF4CAF50).copy(alpha = 0.35f),
                start = androidx.compose.ui.geometry.Offset(leftPad, y),
                end = androidx.compose.ui.geometry.Offset(leftPad + plotW, y),
                strokeWidth = 1f
            )
            val label = if (isMmol) String.format(Locale.US, "%.1f", v / 18f) else v.toInt().toString()
            drawContext.canvas.nativeCanvas.drawText(
                label, leftPad - with(density) { 4.dp.toPx() }, y + labelPaint.textSize / 3,
                labelPaintRight
            )
        }

        // Top and bottom of the visible scale
        listOf(yMin to bottomPad, yMax to topPad).forEach { (v, _) ->
            val y = yFor(v)
            val label = if (isMmol) String.format(Locale.US, "%.1f", v / 18f) else v.toInt().toString()
            drawContext.canvas.nativeCanvas.drawText(
                label, leftPad - with(density) { 4.dp.toPx() }, y + labelPaint.textSize / 3,
                labelPaintRight
            )
        }

        // The line, broken across long gaps
        if (points.size > 1) {
            var path = Path()
            var started = false
            var prevT = 0L

            points.forEach { (t, v) ->
                val x = xFor(t)
                val y = yFor(v)
                if (!started) {
                    path.moveTo(x, y); started = true
                } else if (t - prevT > gapThreshold) {
                    drawPath(path, color = TealGreen, style = Stroke(width = 2.5f))
                    path = Path()
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
                prevT = t
            }
            drawPath(path, color = TealGreen, style = Stroke(width = 2.5f))
        }

        // Points, coloured by status so outliers read at a glance
        points.forEach { (t, v) ->
            val colour = when {
                v < TARGET_LOW -> Color(0xFF2196F3)
                v <= 140f -> Color(0xFF4CAF50)
                v <= TARGET_HIGH -> Color(0xFFFFC107)
                else -> Color(0xFFF44336)
            }
            drawCircle(
                color = Color.White,
                radius = with(density) { 4.dp.toPx() },
                center = androidx.compose.ui.geometry.Offset(xFor(t), yFor(v))
            )
            drawCircle(
                color = colour,
                radius = with(density) { 2.8.dp.toPx() },
                center = androidx.compose.ui.geometry.Offset(xFor(t), yFor(v))
            )
        }

        // Date labels at each end
        val baseY = size.height - with(density) { 4.dp.toPx() }
        drawContext.canvas.nativeCanvas.drawText(
            dateFmt.format(Date(rangeStart)), leftPad, baseY, labelPaint
        )
        drawContext.canvas.nativeCanvas.drawText(
            dateFmt.format(Date(rangeEnd)), leftPad + plotW, baseY, labelPaintRight
        )
    }
}

@Composable
private fun RangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFFE1F5EE) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) TealGreen else Color.LightGray
        )
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) TealGreen else Color.Gray,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun LegendDot(colour: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(colour, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}