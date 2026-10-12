package com.example.financeapplication.core

/** Shared demo credentials. The account is created in Firebase the first time they are used. */
object DemoAccount {
    const val EMAIL = "alex.carter@citibank.demo"
    const val PASSWORD = "Password@123"
    const val NAME = "Alex Carter"
    const val PHONE = "+1 (555) 123-4567"

    fun isDemoEmail(email: String?) = email.equals(EMAIL, ignoreCase = true)
}
