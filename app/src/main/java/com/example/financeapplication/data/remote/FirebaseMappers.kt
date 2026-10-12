package com.example.financeapplication.data.remote

import com.example.financeapplication.domain.model.*
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseException

// ---------- snapshot helpers ----------
fun DataSnapshot.str(key: String): String = child(key).value as? String ?: ""
fun DataSnapshot.dbl(key: String): Double = (child(key).value as? Number)?.toDouble() ?: 0.0
fun DataSnapshot.lng(key: String): Long = (child(key).value as? Number)?.toLong() ?: 0L
fun DataSnapshot.optLng(key: String): Long? = (child(key).value as? Number)?.toLong()
fun DataSnapshot.bool(key: String, default: Boolean = false): Boolean = child(key).value as? Boolean ?: default

private inline fun <reified E : Enum<E>> DataSnapshot.enumOf(key: String, default: E): E =
    runCatching { enumValueOf<E>(str(key)) }.getOrDefault(default)

// ---------- snapshot -> model ----------
fun DataSnapshot.toAccount() = Account(
    id = key ?: "", name = str("name"), type = enumOf("type", AccountType.CHECKING),
    number = str("number"), balance = dbl("balance"), creditLimit = dbl("creditLimit")
)

fun DataSnapshot.toTransaction() = Transaction(
    id = key ?: "", accountId = str("accountId"), title = str("title"),
    category = enumOf("category", TxCategory.OTHER), amount = dbl("amount"),
    timestamp = lng("timestamp"), note = str("note")
)

fun DataSnapshot.toPayee() = Payee(key ?: "", str("name"), enumOf("kind", PayeeKind.BANK), str("identifier"))

fun DataSnapshot.toBill() = Bill(
    id = key ?: "", name = str("name"), category = enumOf("category", BillCategory.ELECTRICITY),
    amount = dbl("amount"), dueDate = lng("dueDate"), paid = bool("paid"), scheduledFor = optLng("scheduledFor")
)

fun DataSnapshot.toScheduled() = ScheduledPayment(
    id = key ?: "", title = str("title"), amount = dbl("amount"), date = lng("date"),
    fromAccountId = str("fromAccountId"),
    billId = str("billId").ifEmpty { null }, payeeId = str("payeeId").ifEmpty { null }
)

fun DataSnapshot.toOffer() = Offer(
    key ?: "", str("title"), str("description"), enumOf("category", OfferCategory.CASHBACK), str("badge"), bool("activated")
)

fun DataSnapshot.toNotification() = AppNotification(
    key ?: "", str("title"), str("body"), lng("timestamp"), bool("important"), bool("read"),
    enumOf("type", NotificationType.PROMO)
)

fun DataSnapshot.toChat() = ChatMessage(key ?: "", str("text"), bool("fromUser"), lng("timestamp"))

fun DataSnapshot.toControls() = CardControls(
    quickLock = bool("quickLock"), onlinePurchases = bool("onlinePurchases", true),
    atmWithdrawals = bool("atmWithdrawals", true), international = bool("international"),
    transactionAlerts = bool("transactionAlerts", true)
)

// ---------- model -> map ----------
fun Account.toMap(createdAt: Long = System.currentTimeMillis()): Map<String, Any?> = mapOf(
    "name" to name, "type" to type.name, "number" to number, "balance" to balance,
    "creditLimit" to creditLimit, "createdAt" to createdAt
)

fun transactionMap(accountId: String, title: String, category: TxCategory, amount: Double, timestamp: Long, note: String): Map<String, Any?> =
    mapOf("accountId" to accountId, "title" to title, "category" to category.name, "amount" to amount, "timestamp" to timestamp, "note" to note)

fun notificationMap(title: String, body: String, important: Boolean, type: NotificationType, timestamp: Long): Map<String, Any?> =
    mapOf("title" to title, "body" to body, "important" to important, "read" to false, "type" to type.name, "timestamp" to timestamp)

fun CardControls.toMap(): Map<String, Any?> = mapOf(
    "quickLock" to quickLock, "onlinePurchases" to onlinePurchases, "atmWithdrawals" to atmWithdrawals,
    "international" to international, "transactionAlerts" to transactionAlerts
)

// ---------- errors ----------
fun Throwable.toFriendlyMessage(): String = when (this) {
    is FirebaseAuthWeakPasswordException -> "Choose a stronger password"
    is FirebaseAuthInvalidUserException, is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password"
    is FirebaseAuthUserCollisionException -> "An account with this email already exists"
    is FirebaseAuthRecentLoginRequiredException -> "Please sign in again and retry"
    is FirebaseTooManyRequestsException -> "Too many attempts. Please try again later"
    is FirebaseNetworkException -> "No internet connection. Check your network and try again"
    is FirebaseAuthException -> message ?: "Authentication failed"
    is DatabaseException -> "Database error: ${message ?: "unknown"}"
    else -> message ?: "Something went wrong"
}

@Suppress("unused")
fun <T> Task<T>.describe(): String = if (isSuccessful) "ok" else exception?.message.orEmpty()
