package com.example.financeapplication.domain.model

enum class AccountType { CHECKING, SAVINGS, CREDIT }
enum class TxCategory { SHOPPING, FOOD, TRANSPORT, INCOME, UTILITIES, TRANSFER, DEPOSIT, ENTERTAINMENT, TRAVEL, OTHER }
enum class PayeeKind { BANK, MOBILE }
enum class BillCategory { ELECTRICITY, INTERNET, MOBILE, WATER }
enum class OfferCategory { TRAVEL, DINING, SHOPPING, CASHBACK }
enum class BranchType { ATM, BRANCH }
enum class NotificationType { PAYMENT, CARD, SECURITY, PROMO }
enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class User(
    val id: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val address: String = ""
) {
    val firstName: String get() = fullName.trim().substringBefore(' ')
    val initials: String
        get() = fullName.trim().split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
}

data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val number: String,
    /** Money held for deposit accounts, amount OWED for credit accounts. */
    val balance: Double,
    val creditLimit: Double = 0.0
) {
    val masked: String get() = "**** ${number.takeLast(4)}"
    val isCredit: Boolean get() = type == AccountType.CREDIT
    val availableBalance: Double get() = if (isCredit) creditLimit - balance else balance
}

data class Transaction(
    val id: String,
    val accountId: String,
    val title: String,
    val category: TxCategory,
    /** Positive = money in, negative = money out. */
    val amount: Double,
    val timestamp: Long,
    val note: String = ""
) {
    val isCredit: Boolean get() = amount >= 0
}

data class Card(
    val id: String,
    val accountId: String,
    val holder: String,
    val number: String,
    val expiry: String,
    val creditLimit: Double,
    val currentBalance: Double
) {
    val availableCredit: Double get() = creditLimit - currentBalance
    val masked: String get() = "**** **** **** ${number.takeLast(4)}"
    val spaced: String get() = number.chunked(4).joinToString(" ")
}

data class CardControls(
    val quickLock: Boolean = false,
    val onlinePurchases: Boolean = true,
    val atmWithdrawals: Boolean = true,
    val international: Boolean = false,
    val transactionAlerts: Boolean = true
)

data class Payee(val id: String, val name: String, val kind: PayeeKind, val identifier: String) {
    val subtitle: String
        get() = when (kind) {
            PayeeKind.BANK -> "Bank •••• ${identifier.takeLast(4)}"
            PayeeKind.MOBILE -> identifier
        }
}

data class Bill(
    val id: String,
    val name: String,
    val category: BillCategory,
    val amount: Double,
    val dueDate: Long,
    val paid: Boolean = false,
    val scheduledFor: Long? = null
)

data class ScheduledPayment(
    val id: String,
    val title: String,
    val amount: Double,
    val date: Long,
    val fromAccountId: String,
    val billId: String? = null,
    val payeeId: String? = null
)

data class Statement(val id: String, val accountId: String, val accountName: String, val year: Int, val month: Int)

data class StatementData(
    val statement: Statement,
    val account: Account,
    val openingBalance: Double,
    val closingBalance: Double,
    val transactions: List<Transaction>
)

data class RewardsSummary(val points: Int) {
    val tier: String get() = when {
        points >= 5000 -> "Platinum"
        points >= 2000 -> "Gold"
        else -> "Silver"
    }
}

data class RedeemOption(val points: Int, val credit: Double)

data class Offer(
    val id: String,
    val title: String,
    val description: String,
    val category: OfferCategory,
    val badge: String,
    val activated: Boolean = false
)

data class AppNotification(
    val id: String,
    val title: String,
    val body: String,
    val timestamp: Long,
    val important: Boolean,
    val read: Boolean,
    val type: NotificationType
)

data class Branch(
    val id: String,
    val name: String,
    val type: BranchType,
    val address: String,
    val distanceMiles: Double,
    val phone: String,
    val hours: String,
    /** Position on the schematic map, 0..1 */
    val mapX: Float,
    val mapY: Float
)

data class Faq(val id: String, val question: String, val answer: String)

data class ChatMessage(val id: String, val text: String, val fromUser: Boolean, val timestamp: Long)

data class LoanProduct(
    val id: String,
    val name: String,
    val description: String,
    val annualRatePercent: Double,
    val maxAmount: Double,
    val maxMonths: Int
)

data class AppSettings(
    val pushEnabled: Boolean = true,
    val locationEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val twoFactorEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

data class DeviceInfo(val name: String, val lastActive: String, val isCurrent: Boolean)
data class LoginRecord(val device: String, val location: String, val time: String, val success: Boolean)
