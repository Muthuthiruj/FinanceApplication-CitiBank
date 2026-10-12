package com.example.financeapplication.data.remote

import com.example.financeapplication.domain.model.*
import java.util.UUID

/** Starter data written once when a new user registers. Everything afterwards is user-driven. */
object UserSeeder {
    private const val DAY = 86_400_000L

    fun build(fullName: String, email: String, phone: String, demo: Boolean = false): Map<String, Any?> {
        val now = System.currentTimeMillis()
        val out = linkedMapOf<String, Any?>()
        fun id() = UUID.randomUUID().toString()

        out["profile"] = mapOf("fullName" to fullName, "email" to email, "phone" to phone, "address" to "", "createdAt" to now)

        val checking = id(); val savings = id(); val credit = id()
        out["accounts/$checking"] = Account(checking, "Checking Account", AccountType.CHECKING, generateNumber(false), if (demo) 5230.0 else 2500.0).toMap(now)
        out["accounts/$savings"] = Account(savings, "Savings Account", AccountType.SAVINGS, generateNumber(false), if (demo) 7250.0 else 5000.0).toMap(now)
        out["accounts/$credit"] = Account(credit, "Credit Card", AccountType.CREDIT, generateNumber(true), if (demo) 1750.0 else 0.0, creditLimit = 6000.0).toMap(now)

        if (demo) {
            addDemoHistory(out, checking, savings, credit, now)
        } else {
            out["transactions/${id()}"] = transactionMap(checking, "Welcome deposit", TxCategory.DEPOSIT, 2500.0, now, "Starter funds")
            out["transactions/${id()}"] = transactionMap(savings, "Welcome deposit", TxCategory.DEPOSIT, 5000.0, now - 1, "Starter funds")
        }

        listOf(
            Triple("City Power", BillCategory.ELECTRICITY, 120.00) to 5,
            Triple("FiberNet Internet", BillCategory.INTERNET, 59.99) to 8,
            Triple("T-Mobile", BillCategory.MOBILE, 45.00) to 3,
            Triple("Metro Water", BillCategory.WATER, 32.10) to 12
        ).forEach { (b, days) ->
            out["bills/${id()}"] = mapOf(
                "name" to b.first, "category" to b.second.name, "amount" to b.third,
                "dueDate" to now + days * DAY, "paid" to false
            )
        }

        listOf(
            arrayOf("Travel More, Earn More", "Earn up to 5x points on flights and hotels.", OfferCategory.TRAVEL, "5x"),
            arrayOf("Dining Rewards", "Earn up to 3x points at restaurants.", OfferCategory.DINING, "3x"),
            arrayOf("Shop Online", "2x points on all online shopping.", OfferCategory.SHOPPING, "2x"),
            arrayOf("Grocery Cashback", "5% cashback on groceries for 3 months.", OfferCategory.CASHBACK, "5%")
        ).forEach { o ->
            out["offers/${id()}"] = mapOf(
                "title" to o[0], "description" to o[1], "category" to (o[2] as OfferCategory).name,
                "badge" to o[3], "activated" to false
            )
        }

        out["rewards/points"] = if (demo) 2450 else 500
        out["cardControls"] = CardControls().toMap()
        out["notifications/${id()}"] = notificationMap(
            "Welcome to CitiBank", "Your accounts are ready. Explore payments, rewards and card controls.", true, NotificationType.PROMO, now
        )
        if (demo) {
            listOf(
                Triple("John Smith", PayeeKind.BANK, "000123456789"),
                Triple("Sarah Johnson", PayeeKind.BANK, "000987654321"),
                Triple("Mike Brown", PayeeKind.MOBILE, "+1 (555) 010-2233"),
                Triple("Emily Davis", PayeeKind.MOBILE, "+1 (555) 010-4455")
            ).forEach { (n, k, i) -> out["payees/${id()}"] = mapOf("name" to n, "kind" to k.name, "identifier" to i) }
            out["notifications/${id()}"] = notificationMap("Payment received", "Your salary of \$2,850.00 was credited to your checking account.", true, NotificationType.PAYMENT, now - 3 * DAY)
            out["notifications/${id()}"] = notificationMap("Security alert", "A new device signed in to your account. Review recent activity.", true, NotificationType.SECURITY, now - 4 * DAY)
            out["notifications/${id()}"] = notificationMap("Bill due soon", "Your T-Mobile bill of \$45.00 is due in 3 days.", true, NotificationType.PAYMENT, now - 1 * DAY)
        }
        out["chat/${id()}"] = mapOf("text" to "Hello! How can I help you today?", "fromUser" to false, "timestamp" to now)
        return out
    }

    private fun addDemoHistory(out: MutableMap<String, Any?>, checking: String, savings: String, credit: String, now: Long) {
        fun tx(acc: String, title: String, cat: TxCategory, amount: Double, daysAgo: Int) {
            out["transactions/${UUID.randomUUID()}"] = transactionMap(acc, title, cat, amount, now - daysAgo * DAY, "")
        }
        tx(checking, "Uber", TxCategory.TRANSPORT, -24.50, 1)
        tx(credit, "Amazon", TxCategory.SHOPPING, -128.00, 2)
        tx(checking, "Salary Deposit", TxCategory.INCOME, 2850.00, 3)
        tx(checking, "Starbucks", TxCategory.FOOD, -5.75, 4)
        tx(checking, "Walmart", TxCategory.SHOPPING, -76.30, 5)
        tx(credit, "Apple Store", TxCategory.SHOPPING, -249.00, 6)
        tx(checking, "Direct Deposit", TxCategory.DEPOSIT, 450.00, 8)
        tx(checking, "City Power", TxCategory.UTILITIES, -120.00, 9)
        tx(credit, "Uber Eats", TxCategory.FOOD, -32.40, 10)
        tx(savings, "Interest Earned", TxCategory.INCOME, 12.40, 11)
        for (m in 1..6) { // previous months so every statement has content
            val b = m * 30
            tx(checking, "Salary Deposit", TxCategory.INCOME, 2850.00, b + 3)
            tx(checking, "Apartment Rent", TxCategory.UTILITIES, -1200.00, b + 6)
            tx(checking, "Whole Foods", TxCategory.FOOD, -(85.0 + m * 7.5), b + 10)
            tx(checking, "Shell Gas Station", TxCategory.TRANSPORT, -(42.0 + m), b + 14)
            tx(checking, "Netflix", TxCategory.ENTERTAINMENT, -15.49, b + 18)
            tx(credit, "Amazon", TxCategory.SHOPPING, -(60.0 + m * 10), b + 12)
            tx(savings, "Interest Earned", TxCategory.INCOME, 12.40, b + 1)
        }
    }

    /** Random account / card number (cards start with 4). */
    fun generateNumber(card: Boolean): String {
        val len = if (card) 16 else 10
        val sb = StringBuilder(if (card) "4" else "1")
        while (sb.length < len) sb.append((0..9).random())
        return sb.toString()
    }
}
