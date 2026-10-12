package com.example.financeapplication

import com.example.financeapplication.core.Formatters
import com.example.financeapplication.core.LoanCalculator
import com.example.financeapplication.core.Validators
import com.example.financeapplication.domain.model.Account
import com.example.financeapplication.domain.model.AccountType
import com.example.financeapplication.domain.model.RewardsSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {

    @Test fun email_validation() {
        assertTrue(Validators.isValidEmail("alex.carter@citibank.demo"))
        assertFalse(Validators.isValidEmail("alex@"))
        assertFalse(Validators.isValidEmail("no-at-sign.com"))
    }

    @Test fun phone_validation() {
        assertTrue(Validators.isValidPhone("+1 (555) 123-4567"))
        assertFalse(Validators.isValidPhone("12345"))
    }

    @Test fun password_rules() {
        assertNotNull(Validators.passwordError("short1A"))
        assertNotNull(Validators.passwordError("alllowercase1"))
        assertNotNull(Validators.passwordError("NoDigitsHere"))
        assertNull(Validators.passwordError("Password@123"))
    }

    @Test fun amount_parsing() {
        assertEquals(1250.5, Validators.parseAmount("1,250.50")!!, 0.0001)
        assertEquals(20.0, Validators.parseAmount("$20")!!, 0.0001)
        assertNull(Validators.parseAmount(""))
        assertNull(Validators.parseAmount("abc"))
    }

    @Test fun loan_payment_matches_reference_value() {
        // 10,000 at 12% APR over 12 months => 888.49 / month
        assertEquals(888.49, LoanCalculator.monthlyPayment(10_000.0, 12.0, 12), 0.01)
        assertEquals(100.0, LoanCalculator.monthlyPayment(1_200.0, 0.0, 12), 0.0001)
        assertEquals(0.0, LoanCalculator.monthlyPayment(0.0, 5.0, 12), 0.0)
    }

    @Test fun credit_account_available_balance_is_limit_minus_owed() {
        val card = Account("c", "Credit Card", AccountType.CREDIT, "4111111111112398", 1750.0, creditLimit = 6000.0)
        assertEquals(4250.0, card.availableBalance, 0.0001)
        assertEquals("**** 2398", card.masked)
    }

    @Test fun reward_tiers() {
        assertEquals("Silver", RewardsSummary(100).tier)
        assertEquals("Gold", RewardsSummary(2450).tier)
        assertEquals("Platinum", RewardsSummary(9000).tier)
    }

    @Test fun greeting_by_hour() {
        assertEquals("Good Morning", Formatters.greeting(9))
        assertEquals("Good Afternoon", Formatters.greeting(14))
        assertEquals("Good Evening", Formatters.greeting(19))
    }
}
