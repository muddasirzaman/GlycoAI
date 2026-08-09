package com.sugarsaathi.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class GlucoseSuggestion(
    val title: String,
    val message: String,
    val color: Color,
    val isUrgent: Boolean
)

/**
 * Severity is decided by fixed rules, never by rotation.
 * Only the encouraging messages vary, so dangerous readings
 * always produce the same clear instruction.
 */
@Composable
fun suggestionFor(value: Float, unit: String, seed: Long): GlucoseSuggestion {
    val mgdl = if (unit == "mmol/L") value * 18f else value

    // Rotate encouraging messages using the reading's own timestamp,
    // so it stays stable for a given reading but varies between readings.
    val variant = ((seed / 1000) % 3).toInt()

    return when {
        mgdl < 54f -> GlucoseSuggestion(
            title = stringResource(R.string.sugg_severe_low_title),
            message = stringResource(R.string.sugg_severe_low),
            color = Color(0xFFD32F2F),
            isUrgent = true
        )

        mgdl < 70f -> GlucoseSuggestion(
            title = stringResource(R.string.sugg_low_title),
            message = stringResource(R.string.sugg_low),
            color = Color(0xFFEF6C00),
            isUrgent = true
        )

        mgdl <= 180f -> GlucoseSuggestion(
            title = stringResource(R.string.sugg_in_range_title),
            message = when (variant) {
                0 -> stringResource(R.string.sugg_in_range_1)
                1 -> stringResource(R.string.sugg_in_range_2)
                else -> stringResource(R.string.sugg_in_range_3)
            },
            color = Color(0xFF2E7D32),
            isUrgent = false
        )

        mgdl <= 250f -> GlucoseSuggestion(
            title = stringResource(R.string.sugg_slightly_high_title),
            message = when (variant) {
                0 -> stringResource(R.string.sugg_slightly_high_1)
                1 -> stringResource(R.string.sugg_slightly_high_2)
                else -> stringResource(R.string.sugg_slightly_high_3)
            },
            color = Color(0xFFF9A825),
            isUrgent = false
        )

        mgdl <= 400f -> GlucoseSuggestion(
            title = stringResource(R.string.sugg_high_title),
            message = stringResource(R.string.sugg_high),
            color = Color(0xFFD32F2F),
            isUrgent = false
        )

        else -> GlucoseSuggestion(
            title = stringResource(R.string.sugg_very_high_title),
            message = stringResource(R.string.sugg_very_high),
            color = Color(0xFFB71C1C),
            isUrgent = true
        )
    }
}

@Composable
fun SuggestionCard(suggestion: GlucoseSuggestion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = suggestion.color.copy(alpha = 0.10f)
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (suggestion.isUrgent) 4.dp else 1.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                suggestion.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = suggestion.color
            )
            Spacer(Modifier.height(8.dp))
            Text(
                suggestion.message,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.sugg_disclaimer),
                fontSize = 10.sp,
                color = Color.Gray
            )
        }
    }
}

// A plain, non-Composable version of the urgency check, since it needs to run
// inside a button's onClick handler, which can't call Composable functions.
// Mirrors the exact same thresholds as suggestionFor()'s isUrgent flags below.
fun isUrgentReading(value: Float, unit: String): Boolean {
    val mgdl = if (unit == "mmol/L") value * 18f else value
    return mgdl < 70f || mgdl > 400f
}