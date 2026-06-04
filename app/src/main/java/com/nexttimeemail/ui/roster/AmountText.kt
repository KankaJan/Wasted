package com.nexttimeemail.ui.roster

/** Keeps only digits and a single decimal separator while typing an amount. */
internal fun String.filterAmount(): String {
    val cleaned = filter { it.isDigit() || it == '.' || it == ',' }
    val firstSep = cleaned.indexOfFirst { it == '.' || it == ',' }
    if (firstSep == -1) return cleaned
    val head = cleaned.substring(0, firstSep + 1)
    val tail = cleaned.substring(firstSep + 1).filter { it.isDigit() }
    return head + tail
}

/** Renders an amount without a trailing ".0" when it is a whole number. */
internal fun trimAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
