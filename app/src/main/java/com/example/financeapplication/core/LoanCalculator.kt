package com.example.financeapplication.core

import kotlin.math.pow

object LoanCalculator {
    /** Standard EMI formula. [annualRatePercent] is e.g. 8.5 for 8.5%. */
    fun monthlyPayment(principal: Double, annualRatePercent: Double, months: Int): Double {
        if (principal <= 0.0 || months <= 0) return 0.0
        val r = annualRatePercent / 12.0 / 100.0
        if (r == 0.0) return principal / months
        val factor = (1 + r).pow(months)
        return principal * r * factor / (factor - 1)
    }
}
