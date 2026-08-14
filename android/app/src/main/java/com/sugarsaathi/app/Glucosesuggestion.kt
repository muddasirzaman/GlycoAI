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

// =====================================================================
// TARGET RANGES — REQUIRES CLINICAL REVIEW
// =====================================================================
//
// These are ADA general adult guidance. They are NOT universal:
//   - frail or elderly patients are often given looser targets
//   - hypoglycemia unawareness warrants a higher floor
//   - pregnancy targets are tighter than any of these
//   - children and adolescents differ again
//
// A clinician reviewing this app should confirm or replace every number
// in this block. Nothing else in the file needs changing to adjust them.
//
// The upper bound is what varies by context. The LOW thresholds are
// deliberately identical across every reading type: a hypo is a hypo
// whatever time of day it happened, and softening that by context would
// be the dangerous direction to err in.
//
// All values mg/dL.
// ---------------------------------------------------------------------

/** Below this is a medical emergency regardless of context. */
private const val SEVERE_LOW = 54f

/** Below this is hypoglycemia needing treatment, any reading type. */
private const val LOW = 70f

/** Fasting / pre-meal upper target. */
private const val TARGET_HIGH_FASTING = 130f

/** Post-meal (1-2 hr) upper target. */
private const val TARGET_HIGH_POST_MEAL = 180f

/** Bedtime upper target. */
private const val TARGET_HIGH_BEDTIME = 140f

/** Fallback for random / unrecognised reading types. */
private const val TARGET_HIGH_DEFAULT = 180f

/** Above target but not yet concerning — the "a little high" band ceiling. */
private const val SLIGHTLY_HIGH_CEILING = 250f

/** Above this warrants same-day medical contact. */
private const val HIGH_CEILING = 400f

/**
 * Upper target for a given reading type.
 *
 * Matching is intentionally forgiving: reading types are stored as free
 * strings ("Fasting", "After Lunch", "Bedtime"), and an unrecognised value
 * must fall back to the widest band rather than crash or accidentally apply
 * a tight fasting target to a post-meal reading. Being wrong in the
 * permissive direction here only costs a missed nudge; being wrong the other
 * way would tell a patient their normal post-meal reading is too high.
 */
private fun upperTargetFor(readingType: String?): Float {
    val t = (readingType ?: "").lowercase().trim()
    return when {
        t.contains("fasting") ||
                t.contains("before") ||
                t.contains("pre-meal") ||
                t.contains("pre meal") -> TARGET_HIGH_FASTING

        t.contains("bedtime") ||
                t.contains("night") -> TARGET_HIGH_BEDTIME

        t.contains("after") ||
                t.contains("post") -> TARGET_HIGH_POST_MEAL

        else -> TARGET_HIGH_DEFAULT
    }
}

/**
 * Severity is decided by fixed rules, never by rotation.
 * Only the encouraging messages vary, so dangerous readings
 * always produce the same clear instruction.
 *
 * @param readingType the stored context ("Fasting", "After Lunch", ...).
 *        Null or unrecognised falls back to the widest 70-180 band, which
 *        is the previous behaviour — so existing callers that do not pass
 *        it keep working exactly as before.
 */
@Composable
fun suggestionFor(
    value: Float,
    unit: String,
    seed: Long,
    readingType: String? = null
): GlucoseSuggestion {
    val mgdl = if (unit == "mmol/L") value * 18f else value
    val upperTarget = upperTargetFor(readingType)

    // Rotate encouraging messages using the reading's own timestamp,
    // so it stays stable for a given reading but varies between readings.
    val variant = ((seed / 1000) % 3).toInt()

    return when {
        mgdl < SEVERE_LOW -> GlucoseSuggestion(
            title = stringResource(R.string.sugg_severe_low_title),
            message = stringResource(R.string.sugg_severe_low),
            color = Color(0xFFD32F2F),
            isUrgent = true
        )

        mgdl < LOW -> GlucoseSuggestion(
            title = stringResource(R.string.sugg_low_title),
            message = stringResource(R.string.sugg_low),
            color = Color(0xFFEF6C00),
            isUrgent = true
        )

        // In target FOR THIS CONTEXT. A 150 fasting no longer reads as
        // "nicely in range" the way it did under the old single 70-180 band.
        mgdl <= upperTarget -> GlucoseSuggestion(
            title = stringResource(R.string.sugg_in_range_title),
            message = when (variant) {
                0 -> stringResource(R.string.sugg_in_range_1)
                1 -> stringResource(R.string.sugg_in_range_2)
                else -> stringResource(R.string.sugg_in_range_3)
            },
            color = Color(0xFF2E7D32),
            isUrgent = false
        )

        // Above this reading type's target but below the general concern
        // level. For a fasting 150 this is where we now land: a gentle
        // "above target for a fasting reading" rather than congratulation.
        mgdl <= SLIGHTLY_HIGH_CEILING -> GlucoseSuggestion(
            title = stringResource(R.string.sugg_slightly_high_title),
            message = when (variant) {
                0 -> stringResource(R.string.sugg_slightly_high_1)
                1 -> stringResource(R.string.sugg_slightly_high_2)
                else -> stringResource(R.string.sugg_slightly_high_3)
            },
            color = Color(0xFFF9A825),
            isUrgent = false
        )

        mgdl <= HIGH_CEILING -> GlucoseSuggestion(
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
// Mirrors the exact same thresholds as suggestionFor()'s isUrgent flags above.
//
// Deliberately NOT reading-type aware: urgency is driven by the low and
// very-high bounds, and both of those are the same for every context. Adding
// a type parameter here would imply the urgency line moves by time of day,
// which it does not.
fun isUrgentReading(value: Float, unit: String): Boolean {
    val mgdl = if (unit == "mmol/L") value * 18f else value
    return mgdl < LOW || mgdl > HIGH_CEILING
}