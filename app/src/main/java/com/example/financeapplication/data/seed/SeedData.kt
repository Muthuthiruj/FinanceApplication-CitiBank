package com.example.financeapplication.data.seed

import com.example.financeapplication.domain.model.*
import java.util.Calendar

/** Static catalogue data (FAQs, branches, loan products). User data lives in Firebase. */
object SeedData {
    fun faqs() = listOf(
        Faq("f1", "How do I reset my password?", "Use \"Forgot Password?\" on the sign-in screen, or Profile > Security > Change Password."),
        Faq("f2", "How do I lock my card?", "Open Cards > Card Controls and switch on Quick Lock. You can unlock it any time."),
        Faq("f3", "What is the mobile check deposit limit?", "You can deposit up to \$5,000 per check using Mobile Check Deposit."),
        Faq("f4", "How long do transfers take?", "Transfers between your own accounts are instant. Transfers to other recipients arrive the same day."),
        Faq("f5", "How do I schedule a bill payment?", "Open Bill Pay, tap Pay on a bill and choose Schedule to pick a future date."),
        Faq("f6", "How do I redeem reward points?", "Open Rewards and tap Redeem Points to convert them into statement credit.")
    )

    fun branches() = listOf(
        Branch("br1", "Main Branch", BranchType.BRANCH, "123 Main St, New York, NY", 0.4, "+1 (555) 010-1001", "Mon–Fri 9:00–17:00, Sat 9:00–13:00", 0.45f, 0.40f),
        Branch("br2", "Downtown ATM", BranchType.ATM, "45 Broadway, New York, NY", 0.7, "+1 (555) 010-1002", "Open 24 hours", 0.70f, 0.28f),
        Branch("br3", "Midtown Branch", BranchType.BRANCH, "880 5th Ave, New York, NY", 1.2, "+1 (555) 010-1003", "Mon–Fri 9:00–18:00", 0.25f, 0.62f),
        Branch("br4", "Central Park ATM", BranchType.ATM, "59th St & 6th Ave, New York, NY", 1.5, "+1 (555) 010-1004", "Open 24 hours", 0.18f, 0.25f),
        Branch("br5", "Harbor ATM", BranchType.ATM, "12 Pier Rd, New York, NY", 2.3, "+1 (555) 010-1005", "Open 24 hours", 0.80f, 0.70f),
        Branch("br6", "Uptown Branch", BranchType.BRANCH, "300 Lexington Ave, New York, NY", 2.9, "+1 (555) 010-1006", "Mon–Fri 9:00–17:00", 0.55f, 0.82f)
    )

    fun loans() = listOf(
        LoanProduct("l1", "Personal Loan", "Flexible funds for anything you need.", 10.5, 50_000.0, 60),
        LoanProduct("l2", "Auto Loan", "Drive home your new car with low rates.", 6.9, 80_000.0, 84),
        LoanProduct("l3", "Home Loan", "Make your dream home a reality.", 6.1, 1_000_000.0, 360)
    )

    val redeemOptions = listOf(
        RedeemOption(500, 5.0),
        RedeemOption(1000, 10.0),
        RedeemOption(2500, 25.0)
    )

    /** First day of the month [monthsAgo] months back. */
    fun monthStart(monthsAgo: Int): Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.MONTH, -monthsAgo)
    }
}
