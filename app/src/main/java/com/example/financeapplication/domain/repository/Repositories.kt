package com.example.financeapplication.domain.repository

import com.example.financeapplication.core.Resource
import com.example.financeapplication.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    /** Signed in with Firebase (and e-mail verified when verification is required). */
    val isLoggedIn: Boolean
    val onboardingSeen: Boolean
    val currentUser: StateFlow<User?>
    /** E-mail of the sign-up that is waiting for verification. */
    val pendingEmail: String

    fun markOnboardingSeen()
    suspend fun login(email: String, password: String): Resource<User>
    /** Validates and remembers the sign-up details; the Firebase account is created after Terms/Privacy. */
    suspend fun startSignUp(fullName: String, email: String, phone: String, password: String): Resource<Unit>
    /** Creates the Firebase user, seeds their database node and sends the verification e-mail. */
    suspend fun createPendingAccount(): Resource<Unit>
    suspend fun resendVerification(): Resource<Unit>
    /**
     * Reloads the user from Firebase and returns true once the e-mail is verified.
     * [force] additionally refreshes the ID token (used by the manual button).
     */
    suspend fun checkEmailVerified(force: Boolean = false): Resource<Boolean>
    /** Refreshes a cached, not-yet-verified session (e.g. after verifying in the mail app). */
    suspend fun refreshSession(): Boolean
    suspend fun sendResetLink(email: String): Resource<Unit>
    suspend fun updateProfile(fullName: String, phone: String, address: String): Resource<User>
    suspend fun changePassword(oldPassword: String, newPassword: String): Resource<Unit>
    /** Permanently deletes the user's data and Firebase account (re-authenticates with [password]). */
    suspend fun deleteAccount(password: String): Resource<Unit>
    fun logout()
}

interface BankRepository {
    val accounts: Flow<List<Account>>
    val transactions: Flow<List<Transaction>>
    val payees: Flow<List<Payee>>
    val bills: Flow<List<Bill>>
    val scheduled: Flow<List<ScheduledPayment>>

    suspend fun getAccount(id: String): Account?
    suspend fun transfer(fromId: String, toAccountId: String?, payeeId: String?, amount: Double, note: String): Resource<Unit>
    /** [date] in the future schedules the payment instead of paying now. */
    suspend fun makePayment(fromId: String, payeeId: String, amount: Double, memo: String, date: Long): Resource<String>
    suspend fun payBill(billId: String, fromId: String, scheduleFor: Long?): Resource<String>
    suspend fun depositCheck(toId: String, amount: Double): Resource<Unit>
    suspend fun addPayee(name: String, kind: PayeeKind, identifier: String): Resource<Payee>
    suspend fun removePayee(id: String)
    /** [amount] is the opening deposit for checking/savings, or the credit limit for a credit card. */
    suspend fun addAccount(name: String, type: AccountType, amount: Double): Resource<Account>
    /** Only accounts with a zero balance can be closed. */
    suspend fun deleteAccount(id: String): Resource<Unit>
    suspend fun cancelScheduled(id: String)
    suspend fun processScheduled()
    fun statements(): List<Statement>
    suspend fun statementData(statementId: String): Resource<StatementData>
}

interface CardRepository {
    val card: Flow<Card?>
    val controls: Flow<CardControls>
    suspend fun updateControls(transform: (CardControls) -> CardControls)
}

interface RewardsRepository {
    val rewards: Flow<RewardsSummary>
    val offers: Flow<List<Offer>>
    val redeemOptions: List<RedeemOption>
    suspend fun toggleOffer(id: String)
    suspend fun redeem(option: RedeemOption, toAccountId: String): Resource<Unit>
}

interface LoanRepository {
    fun products(): List<LoanProduct>
    suspend fun apply(productId: String, amount: Double, months: Int): Resource<String>
}

interface NotificationRepository {
    val notifications: Flow<List<AppNotification>>
    suspend fun markRead(id: String)
    suspend fun markAllRead()
}

interface SupportRepository {
    val chat: Flow<List<ChatMessage>>
    fun faqs(): List<Faq>
    fun branches(): List<Branch>
    suspend fun sendChat(text: String)
    suspend fun reportProblem(text: String): Resource<Unit>
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    val current: AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
    fun devices(): List<DeviceInfo>
    fun loginActivity(): List<LoginRecord>
}
