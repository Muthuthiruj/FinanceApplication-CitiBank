package com.example.financeapplication.data.repository

import android.os.Build
import com.example.financeapplication.core.Formatters
import com.example.financeapplication.core.Resource
import com.example.financeapplication.data.local.AppPrefs
import com.example.financeapplication.data.remote.FirebaseStore
import com.example.financeapplication.data.remote.WriteBatch
import com.example.financeapplication.data.remote.toMap
import com.example.financeapplication.data.seed.SeedData
import com.example.financeapplication.domain.model.*
import com.example.financeapplication.domain.repository.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepositoryImpl @Inject constructor(
    private val store: FirebaseStore,
    auth: AuthRepository
) : CardRepository {

    override val card: Flow<Card?> = combine(store.accounts, auth.currentUser) { accounts, user ->
        val acc = accounts.firstOrNull { it.isCredit }
        if (acc == null) null else Card(
            id = acc.id, accountId = acc.id,
            holder = (user?.fullName?.ifBlank { null } ?: "CARD HOLDER").uppercase(),
            number = acc.number, expiry = "08/29",
            creditLimit = acc.creditLimit, currentBalance = acc.balance
        )
    }

    override val controls: Flow<CardControls> = store.controls

    override suspend fun updateControls(transform: (CardControls) -> CardControls) {
        val old = store.controls.value
        val new = transform(old)
        store.controls.value = new // optimistic; the database listener confirms it
        val batch = WriteBatch(store)
        batch.set("cardControls", new.toMap())
        if (old.quickLock != new.quickLock) {
            val last4 = store.accounts.value.firstOrNull { it.isCredit }?.number?.takeLast(4) ?: "----"
            batch.notification(
                if (new.quickLock) "Card locked" else "Card unlocked",
                if (new.quickLock) "Your card ending $last4 is locked. New purchases will be declined."
                else "Your card ending $last4 is active again.",
                true, NotificationType.CARD
            )
        }
        batch.commit()
    }
}

@Singleton
class RewardsRepositoryImpl @Inject constructor(private val store: FirebaseStore) : RewardsRepository {
    override val rewards: Flow<RewardsSummary> = store.points.map { RewardsSummary(it) }
    override val offers: Flow<List<Offer>> = store.offers.map { l -> l.sortedBy { it.title } }
    override val redeemOptions: List<RedeemOption> = SeedData.redeemOptions

    override suspend fun toggleOffer(id: String) {
        val offer = store.offers.value.firstOrNull { it.id == id } ?: return
        store.write(mapOf("offers/$id/activated" to !offer.activated))
    }

    override suspend fun redeem(option: RedeemOption, toAccountId: String): Resource<Unit> = store.mutex.withLock {
        if (store.points.value < option.points) return@withLock Resource.Error("You need ${Formatters.points(option.points)} points for this reward")
        val batch = WriteBatch(store)
        val acc = batch.account(toAccountId) ?: return@withLock Resource.Error("Account not found")
        if (acc.isCredit) return@withLock Resource.Error("Choose a checking or savings account")
        batch.set("rewards/points", store.points.value - option.points)
        batch.move(acc.id, option.credit)
        batch.transaction(acc.id, "Rewards Redemption", TxCategory.INCOME, option.credit, "${option.points} points")
        batch.notification("Rewards redeemed", "${Formatters.money(option.credit)} credited to ${acc.name}.", false, NotificationType.PROMO)
        batch.commit()
    }
}

@Singleton
class LoanRepositoryImpl @Inject constructor(private val store: FirebaseStore) : LoanRepository {
    override fun products(): List<LoanProduct> = SeedData.loans()

    override suspend fun apply(productId: String, amount: Double, months: Int): Resource<String> {
        val product = SeedData.loans().firstOrNull { it.id == productId } ?: return Resource.Error("Product not found")
        if (amount <= 0 || amount > product.maxAmount) return Resource.Error("Amount must be up to ${Formatters.money(product.maxAmount)}")
        if (months !in 6..product.maxMonths) return Resource.Error("Term must be between 6 and ${product.maxMonths} months")
        val ref = "LN-" + (100000..999999).random()
        val batch = WriteBatch(store)
        batch.set("loanApplications/${store.newId()}", mapOf(
            "reference" to ref, "product" to product.name, "amount" to amount, "months" to months,
            "status" to "UNDER_REVIEW", "createdAt" to System.currentTimeMillis()
        ))
        batch.notification("Loan application received", "Your ${product.name} application ($ref) for ${Formatters.money(amount)} is under review.", true, NotificationType.PAYMENT)
        return when (val r = batch.commit()) {
            is Resource.Success -> Resource.Success(ref)
            is Resource.Error -> r
        }
    }
}

@Singleton
class NotificationRepositoryImpl @Inject constructor(private val store: FirebaseStore) : NotificationRepository {
    override val notifications: Flow<List<AppNotification>> = store.notifications.map { l -> l.sortedByDescending { it.timestamp } }

    override suspend fun markRead(id: String) { store.write(mapOf("notifications/$id/read" to true)) }

    override suspend fun markAllRead() {
        store.write(store.notifications.value.filter { !it.read }.associate { "notifications/${it.id}/read" to (true as Any?) })
    }
}

@Singleton
class SupportRepositoryImpl @Inject constructor(private val store: FirebaseStore) : SupportRepository {
    override val chat: Flow<List<ChatMessage>> = store.chat
    override fun faqs(): List<Faq> = SeedData.faqs()
    override fun branches(): List<Branch> = SeedData.branches()

    override suspend fun sendChat(text: String) {
        store.write(mapOf("chat/${store.newId()}" to chatMap(text, true)))
        delay(900)
        store.write(mapOf("chat/${store.newId()}" to chatMap(botReply(text), false)))
    }

    override suspend fun reportProblem(text: String): Resource<Unit> {
        if (text.trim().length < 10) return Resource.Error("Please describe the problem in a little more detail")
        val batch = WriteBatch(store)
        batch.set("reports/${store.newId()}", mapOf("text" to text.trim(), "createdAt" to System.currentTimeMillis()))
        batch.notification("Problem reported", "Thanks – our team received your report and will reply within 24 hours.", false, NotificationType.SECURITY)
        return batch.commit()
    }

    private fun chatMap(text: String, fromUser: Boolean): Map<String, Any?> =
        mapOf("text" to text, "fromUser" to fromUser, "timestamp" to System.currentTimeMillis())

    private fun botReply(input: String): String {
        val t = input.lowercase()
        return when {
            Regex("^(hi|hello|hey)\\b").containsMatchIn(t) -> "Hi there! I can help with cards, transfers, bills, loans and branches."
            "lost" in t || "stolen" in t -> "I'm sorry to hear that. Open Cards > Card Controls and switch on Quick Lock right away – it blocks new purchases instantly."
            "card" in t -> "You can lock your card, set purchase limits and manage alerts under Cards > Card Controls."
            "balance" in t -> "Your balances are shown on the Home tab and in Accounts. Pull down to refresh."
            "transfer" in t || "send" in t -> "Go to Payments > Transfer Money, pick the accounts and amount. Transfers to your own accounts are instant."
            "bill" in t -> "Open Payments > Bill Pay to pay or schedule your bills."
            "loan" in t -> "Visit Loans & Credit to compare offers and use the payment calculator."
            "atm" in t || "branch" in t -> "Use the ATM / Branch Locator in the More tab to find the closest location and opening hours."
            "password" in t -> "You can change your password under Profile > Security, or tap Forgot Password on the sign-in screen."
            "account" in t -> "You can add or close accounts from the Accounts tab."
            "thank" in t -> "You're welcome! Is there anything else I can help with?"
            else -> "Thanks for your message. A support specialist will follow up shortly. Meanwhile, you can browse the FAQs in Help & Support."
        }
    }
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(private val prefs: AppPrefs) : SettingsRepository {
    private val state = MutableStateFlow(load())
    override val settings: Flow<AppSettings> = state
    override val current: AppSettings get() = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val new = transform(state.value)
        prefs.pushEnabled = new.pushEnabled
        prefs.locationEnabled = new.locationEnabled
        prefs.biometricEnabled = new.biometricEnabled
        prefs.twoFactorEnabled = new.twoFactorEnabled
        prefs.themeMode = new.themeMode.name
        state.value = new
    }

    override fun devices(): List<DeviceInfo> = listOf(
        DeviceInfo("${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}", "Active now", true)
    )

    override fun loginActivity(): List<LoginRecord> = listOf(
        LoginRecord("This device", "Current session", "Now", true)
    )

    private fun load() = AppSettings(
        pushEnabled = prefs.pushEnabled,
        locationEnabled = prefs.locationEnabled,
        biometricEnabled = prefs.biometricEnabled,
        twoFactorEnabled = prefs.twoFactorEnabled,
        themeMode = runCatching { ThemeMode.valueOf(prefs.themeMode) }.getOrDefault(ThemeMode.SYSTEM)
    )
}
