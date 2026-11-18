package com.grocerylist.app.data

import kotlin.math.abs
import kotlin.math.floor

// Scales a recipe's ingredient amounts for a different number of servings. Same as `scaleQuantity` in Logic.swift.

private val FRACTION_CHARS = mapOf('½' to 0.5, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '¼' to 0.25, '¾' to 0.75, '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875)

// A whole number with a fraction ("1 1/2", "1½"), a fraction, a decimal, or a whole number.
private const val NUMBER = """(?:\d+\s+\d+/\d+|\d+/\d+|\d+\s?[½⅓⅔¼¾⅛⅜⅝⅞]|\d*\.\d+|\d+|[½⅓⅔¼¾⅛⅜⅝⅞])"""

// The amount at the start of a quantity, optionally a range ("2-3", "2 to 3").
private val LEADING_AMOUNT = Regex("""^\s*($NUMBER)(\s*(?:-|–|to)\s*($NUMBER))?""")

// Fractions people cook with, for rounding scaled amounts.
private val NICE_FRACTIONS = listOf(0.0 to "", 0.125 to "⅛", 0.25 to "¼", 1.0 / 3 to "⅓", 0.375 to "⅜", 0.5 to "½", 0.625 to "⅝", 2.0 / 3 to "⅔", 0.75 to "¾", 0.875 to "⅞", 1.0 to "")

private fun parseNumber(text: String): Double? {
    val t = text.trim()
    FRACTION_CHARS[t.lastOrNull()]?.let { frac ->
        val whole = t.dropLast(1).trim()
        return (if (whole.isEmpty()) 0.0 else whole.toDoubleOrNull() ?: return null) + frac
    }
    val parts = t.split(Regex("\\s+"))
    if (parts.size == 2) return (parts[0].toDoubleOrNull() ?: return null) + (parseNumber(parts[1]) ?: return null)
    if ('/' in t) {
        val (n, d) = t.split('/').map { it.toDoubleOrNull() ?: return null }
        return if (d == 0.0) null else n / d
    }
    return t.toDoubleOrNull()
}

private fun format(value: Double, decimal: Boolean): String {
    if (decimal) return String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    var whole = floor(value).toInt()
    val (frac, symbol) = NICE_FRACTIONS.minBy { abs(it.first - (value - whole)) }
    if (frac == 1.0) whole++
    // Never round an amount away to nothing.
    if (whole == 0 && symbol.isEmpty()) return "⅛"
    return if (whole == 0) symbol else "$whole$symbol"
}

/**
 * [quantity] with its leading amount (or range) multiplied by [factor]: "1 1/2 lb" × 2 is "3 lb", "2-3 cloves"
 * × 2 is "4–6 cloves". Amounts round to cooking fractions (⅛ to ⅞); decimals stay decimals. Units aren't
 * re-pluralized. Quantities without a leading number ("to taste", "a pinch") come back unchanged.
 */
fun scaleQuantity(quantity: String, factor: Double): String {
    if (factor == 1.0) return quantity
    val match = LEADING_AMOUNT.find(quantity) ?: return quantity
    val decimal = '.' in match.value
    val low = parseNumber(match.groupValues[1]) ?: return quantity
    val scaled = StringBuilder(format(low * factor, decimal))
    if (match.groupValues[3].isNotEmpty()) {
        val high = parseNumber(match.groupValues[3]) ?: return quantity
        scaled.append("–").append(format(high * factor, decimal))
    }
    val rest = quantity.substring(match.range.last + 1)
    return scaled.toString() + rest
}

/** The factor for serving [people] from a recipe that serves [servings], or 1 if it doesn't say. */
fun servingsFactor(servings: Int, people: Int): Double = if (servings > 0 && people > 0) people.toDouble() / servings else 1.0
