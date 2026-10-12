package com.example.financeapplication.data.remote

import android.util.Log
import com.example.financeapplication.core.Resource
import com.example.financeapplication.domain.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Realtime-Database backed store. While a user is signed in it keeps listeners on
 * `users/{uid}/...` and publishes the data as StateFlows, so every screen updates live
 * (also from other devices). Writes are sent as one atomic multi-path update.
 */
@Singleton
class FirebaseStore @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseDatabase
) {
    val mutex = Mutex()

    val accounts = MutableStateFlow<List<Account>>(emptyList())
    val transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val payees = MutableStateFlow<List<Payee>>(emptyList())
    val bills = MutableStateFlow<List<Bill>>(emptyList())
    val scheduled = MutableStateFlow<List<ScheduledPayment>>(emptyList())
    val controls = MutableStateFlow(CardControls())
    val points = MutableStateFlow(0)
    val offers = MutableStateFlow<List<Offer>>(emptyList())
    val notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val chat = MutableStateFlow<List<ChatMessage>>(emptyList())

    private val attached = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()
    private var attachedUid: String? = null

    init {
        auth.addAuthStateListener { fa ->
            val uid = fa.currentUser?.uid
            if (uid != attachedUid) {
                detach()
                if (uid != null) attach(uid)
            }
        }
    }

    fun newId(): String = UUID.randomUUID().toString()

    private fun attach(uid: String) {
        attachedUid = uid
        listen("accounts") { s -> accounts.value = s.children.map { it.toAccount() } }
        listen("transactions") { s -> transactions.value = s.children.map { it.toTransaction() } }
        listen("payees") { s -> payees.value = s.children.map { it.toPayee() } }
        listen("bills") { s -> bills.value = s.children.map { it.toBill() } }
        listen("scheduled") { s -> scheduled.value = s.children.map { it.toScheduled() } }
        listen("cardControls") { s -> controls.value = if (s.exists()) s.toControls() else CardControls() }
        listen("rewards/points") { s -> points.value = (s.value as? Number)?.toInt() ?: 0 }
        listen("offers") { s -> offers.value = s.children.map { it.toOffer() } }
        listen("notifications") { s -> notifications.value = s.children.map { it.toNotification() } }
        listen("chat") { s -> chat.value = s.children.map { it.toChat() }.sortedBy { it.timestamp } }
    }

    private fun detach() {
        attached.forEach { (ref, l) -> ref.removeEventListener(l) }
        attached.clear()
        attachedUid = null
        accounts.value = emptyList(); transactions.value = emptyList(); payees.value = emptyList()
        bills.value = emptyList(); scheduled.value = emptyList(); controls.value = CardControls()
        points.value = 0; offers.value = emptyList(); notifications.value = emptyList(); chat.value = emptyList()
    }

    private fun listen(path: String, onData: (DataSnapshot) -> Unit) {
        val ref = db.getReference("users/$attachedUid/$path")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = onData(snapshot)
            override fun onCancelled(error: DatabaseError) { Log.w(TAG, "Listener for $path cancelled: ${error.message}") }
        }
        ref.addValueEventListener(listener)
        attached += ref to listener
    }

    /**
     * Applies [updates] (paths relative to `users/{uid}`; a null value deletes the path) atomically.
     * If the device is offline the write stays queued in the local cache and is synced later, so a
     * timeout is treated as success instead of blocking the UI.
     */
    suspend fun write(updates: Map<String, Any?>): Resource<Unit> {
        val uid = auth.currentUser?.uid ?: return Resource.Error("You are signed out")
        return try {
            withTimeoutOrNull(WRITE_TIMEOUT_MS) { db.getReference("users/$uid").updateChildren(updates).await() }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.toFriendlyMessage())
        }
    }

    companion object {
        private const val TAG = "FirebaseStore"
        private const val WRITE_TIMEOUT_MS = 6_000L
    }
}

/** Collects balance changes / new rows against a consistent view of the current balances. */
class WriteBatch(private val store: FirebaseStore) {
    val updates = linkedMapOf<String, Any?>()
    private val balances = store.accounts.value.associate { it.id to it.balance }.toMutableMap()

    fun account(id: String): Account? = store.accounts.value.firstOrNull { it.id == id }
    fun balance(id: String): Double = balances[id] ?: 0.0

    fun available(acc: Account): Double = if (acc.isCredit) acc.creditLimit - balance(acc.id) else balance(acc.id)

    /** [signed] > 0 moves money into the account (reduces debt on credit accounts). */
    fun move(accountId: String, signed: Double) {
        val acc = account(accountId) ?: return
        val current = balance(accountId)
        val next = Math.round((if (acc.isCredit) current - signed else current + signed) * 100) / 100.0
        balances[accountId] = next
        updates["accounts/$accountId/balance"] = next
    }

    fun transaction(accountId: String, title: String, category: TxCategory, amount: Double, note: String = "", time: Long = System.currentTimeMillis()) {
        updates["transactions/${store.newId()}"] = transactionMap(accountId, title, category, amount, time, note)
    }

    fun notification(title: String, body: String, important: Boolean, type: NotificationType) {
        updates["notifications/${store.newId()}"] = notificationMap(title, body, important, type, System.currentTimeMillis())
    }

    fun set(path: String, value: Any?) { updates[path] = value }

    suspend fun commit(): Resource<Unit> = store.write(updates)
}
