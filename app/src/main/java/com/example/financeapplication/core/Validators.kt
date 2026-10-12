package com.example.financeapplication.core

/** Pure input validation helpers (unit-tested). */
object Validators {
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(value: String) = EMAIL.matches(value.trim())

    fun isValidPhone(value: String): Boolean = value.count { it.isDigit() } in 10..13

    fun isValidName(value: String) = value.trim().length >= 2

    /** @return an error message or null if the password is acceptable. */
    fun passwordError(password: String): String? = when {
        password.length < 8 -> "Use at least 8 characters"
        password.none { it.isUpperCase() } -> "Add an uppercase letter"
        password.none { it.isDigit() } -> "Add a number"
        else -> null
    }

    fun isValidAccountNumber(value: String) = value.filter { it.isDigit() }.length in 8..17

    /** Parses user typed money ("1,250.50", "$20") – returns null when invalid. */
    fun parseAmount(raw: String): Double? {
        val cleaned = raw.replace(",", "").replace("$", "").trim()
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        return if (value.isNaN() || value.isInfinite()) null else Math.round(value * 100) / 100.0
    }
}
