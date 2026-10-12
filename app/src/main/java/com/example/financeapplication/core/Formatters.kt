package com.example.financeapplication.core

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Formatters {
    fun money(value: Double): String =
        NumberFormat.getCurrencyInstance(Locale.US).format(value)

    fun signedMoney(value: Double): String =
        (if (value < 0) "-" else "+") + money(abs(value))

    fun date(ts: Long): String = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(ts))
    fun shortDate(ts: Long): String = SimpleDateFormat("MMM dd", Locale.US).format(Date(ts))
    fun monthYear(year: Int, month: Int): String {
        val cal = Calendar.getInstance().apply { clear(); set(year, month, 1) }
        return SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
    }

    fun greeting(hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)): String = when (hourOfDay) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..21 -> "Good Evening"
        else -> "Hello"
    }

    fun points(value: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(value)
}
