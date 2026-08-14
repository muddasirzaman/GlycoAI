package com.sugarsaathi.app

import kotlin.math.roundToInt

/**
 * Converts centimetres to (feet, inches), rounded to the nearest whole inch.
 * Used so people can enter and see height in the format common in Pakistan,
 * while everything is still stored internally as centimetres for BMI etc.
 */
fun cmToFeetInches(cm: Float): Pair<Int, Int> {
    val totalInches = (cm / 2.54f).roundToInt()
    var feet = totalInches / 12
    var inches = totalInches % 12
    // Rounding can push inches to exactly 12 (e.g. 71.6" rounds to 72") -
    // that must become 6 ft 0 in, not "5 ft 12 in".
    if (inches == 12) {
        feet += 1
        inches = 0
    }
    return feet to inches
}

/** Converts feet + inches back to centimetres, for internal storage. */
fun feetInchesToCm(feet: Int, inches: Int): Float {
    val totalInches = feet * 12 + inches
    return totalInches * 2.54f
}