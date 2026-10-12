package com.example.financeapplication.data.repository

import com.example.financeapplication.core.Formatters
import com.example.financeapplication.core.Resource
import com.example.financeapplication.core.Validators
import com.example.financeapplication.data.remote.FirebaseStore
import com.example.financeapplication.data.remote.UserSeeder
import com.example.financeapplication.data.remote.WriteBatch
import com.example.financeapplication.data.remote.toMap
import com.example.financeapplication.data.seed.SeedData
import com.example.financeapplication.domain.model.*
import com.example.financeapplication.domain.repository.BankRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BankRepositoryImpl @Inject constructor(private val store: FirebaseStore) : BankRepository {

    override val accounts: Flow<List<Account>> = store.accounts.map { l -> l.sortedBy { it.type.ordinal } }
    override val transactions: Flow<List<Transaction>> =
        store.transactions.map { list -> list.sortedByDescending { it.timestamp } }
    override val payees: Flow<List<Payee>> = store.payees.map { l -> l.sortedBy { it.name.lowercase() } }
    override val bills: Flow<List<Bill>> = store.bills.map { list -> list.sortedBy { it.dueDate } }
    override val scheduled: Flow<List<ScheduledPayment>> = store.scheduled.map { list -> list.sortedBy { it.date } }

    override suspend fun getAccount(id: String): Account? = store.accounts.value.firstOrNull { it.id == id }

    // ---------------------------------------------------------------- money movement

    override suspend fun transfer(
        fromId: String, toAccountId: String?, payeeId: String?, amount: Double, note: String
    ): Resource<Unit> = store.mutex.withLock {
        val batch = WriteBatch(store)
        val from = batch.account(fromId) ?: return@withLock Resource.Error("Account not found")
        if (from.isCredit) return@withLock Resource.Error("Transfers from a credit card are not supported")
        if (batch.available(from) + EPS < amount) return@withLock Resource.Error("Insufficient funds in ${from.name}")

        val payee = payeeId?.let { id -> store.payees.value.firstOrNull { it.id == id } }
        if (payeeId != null && payee == null) return@withLock Resource.Error("Recipient not found")

        val dest: Account? = when {
            toAccountId != null -> batch.account(toAccountId)
            payee != null -> store.accounts.value.firstOrNull { it.number == payee.identifier && it.id != from.id }
            else -> null
        }
        if (toAccountId != null && dest == null) return@withLock Resource.Error("Destination account not found")
        if (dest != null && dest.isCredit && amount > batch.balance(dest.id) + EPS) {
            return@withLock Resource.Error("Payment exceeds the card balance of ${Formatters.money(batch.balance(dest.id))}")
        }

        val label = payee?.name ?: dest?.name ?: "recipient"
        batch.move(from.id, -amount)
        batch.transaction(from.id, "Transfer to $label", TxCategory.TRANSFER, -amount, note)
        if (dest != null) {
            batch.move(dest.id, amount)
            batch.transaction(dest.id, "Transfer from ${from.name}", TxCategory.TRANSFER, amount, note)
        }
        batch.notification("Transfer successful", "${Formatters.money(amount)} sent to $label from ${from.name} ${from.masked}.", false, NotificationType.PAYMENT)
        batch.commit()
    }

    override suspend fun makePayment(
        fromId: String, payeeId: String, amount: Double, memo: String, date: Long
    ): Resource<String> = store.mutex.withLock {
        val batch = WriteBatch(store)
        val from = batch.account(fromId) ?: return@withLock Resource.Error("Account not found")
        val payee = store.payees.value.firstOrNull { it.id == payeeId } ?: return@withLock Resource.Error("Payee not found")
        if (from.isCredit) return@withLock Resource.Error("Choose a checking or savings account")

        if (date > System.currentTimeMillis() + SCHEDULE_THRESHOLD) {
            batch.set("scheduled/${store.newId()}", scheduledMap("Payment to ${payee.name}", amount, date, from.id, null, payee.id))
            return@withLock batch.commit().toMessage("Payment of ${Formatters.money(amount)} scheduled for ${Formatters.date(date)}")
        }
        if (batch.available(from) + EPS < amount) return@withLock Resource.Error("Insufficient funds in ${from.name}")
        batch.move(from.id, -amount)
        batch.transaction(from.id, "Payment to ${payee.name}", TxCategory.TRANSFER, -amount, memo)
        batch.notification("Payment sent", "${Formatters.money(amount)} paid to ${payee.name}.", false, NotificationType.PAYMENT)
        batch.commit().toMessage("Payment of ${Formatters.money(amount)} sent to ${payee.name}")
    }

    override suspend fun payBill(billId: String, fromId: String, scheduleFor: Long?): Resource<String> = store.mutex.withLock {
        val batch = WriteBatch(store)
        val bill = store.bills.value.firstOrNull { it.id == billId } ?: return@withLock Resource.Error("Bill not found")
        if (bill.paid) return@withLock Resource.Error("This bill is already paid")
        val from = batch.account(fromId) ?: return@withLock Resource.Error("Account not found")
        if (from.isCredit) return@withLock Resource.Error("Choose a checking or savings account")

        if (scheduleFor != null && scheduleFor > System.currentTimeMillis() + SCHEDULE_THRESHOLD) {
            store.scheduled.value.filter { it.billId == billId }.forEach { batch.set("scheduled/${it.id}", null) }
            batch.set("scheduled/${store.newId()}", scheduledMap(bill.name, bill.amount, scheduleFor, from.id, bill.id, null))
            batch.set("bills/${bill.id}/scheduledFor", scheduleFor)
            return@withLock batch.commit().toMessage("${bill.name} scheduled for ${Formatters.date(scheduleFor)}")
        }
        if (batch.available(from) + EPS < bill.amount) return@withLock Resource.Error("Insufficient funds in ${from.name}")
        settleBill(batch, bill, from)
        batch.commit().toMessage("${bill.name} paid – ${Formatters.money(bill.amount)}")
    }

    private fun settleBill(batch: WriteBatch, bill: Bill, from: Account) {
        batch.move(from.id, -bill.amount)
        batch.transaction(from.id, bill.name, TxCategory.UTILITIES, -bill.amount, "Bill payment")
        batch.set("bills/${bill.id}/paid", true)
        batch.set("bills/${bill.id}/scheduledFor", null)
        store.scheduled.value.filter { it.billId == bill.id }.forEach { batch.set("scheduled/${it.id}", null) }
        batch.notification("Bill paid", "${bill.name} – ${Formatters.money(bill.amount)} paid from ${from.name}.", false, NotificationType.PAYMENT)
    }

    override suspend fun depositCheck(toId: String, amount: Double): Resource<Unit> = store.mutex.withLock {
        val batch = WriteBatch(store)
        val to = batch.account(toId) ?: return@withLock Resource.Error("Account not found")
        if (to.isCredit) return@withLock Resource.Error("Checks can only be deposited to checking or savings")
        batch.move(to.id, amount)
        batch.transaction(to.id, "Mobile Check Deposit", TxCategory.DEPOSIT, amount)
        batch.notification("Check deposited", "${Formatters.money(amount)} was deposited to ${to.name} ${to.masked}.", true, NotificationType.PAYMENT)
        batch.commit()
    }

    override suspend fun processScheduled() {
        store.mutex.withLock {
            val now = System.currentTimeMillis()
            val due = store.scheduled.value.filter { it.date <= now }
            if (due.isEmpty()) return@withLock
            val batch = WriteBatch(store)
            for (sp in due) {
                val from = batch.account(sp.fromAccountId)
                val bill = sp.billId?.let { id -> store.bills.value.firstOrNull { it.id == id } }
                if (from != null && batch.available(from) + EPS >= sp.amount) {
                    if (bill != null) {
                        settleBill(batch, bill, from)
                    } else {
                        batch.move(from.id, -sp.amount)
                        batch.transaction(from.id, sp.title, TxCategory.TRANSFER, -sp.amount, "Scheduled payment")
                        batch.notification("Scheduled payment sent", "${sp.title} – ${Formatters.money(sp.amount)}.", false, NotificationType.PAYMENT)
                    }
                } else {
                    batch.notification("Scheduled payment failed", "${sp.title} could not be paid because of insufficient funds.", true, NotificationType.PAYMENT)
                    if (bill != null) batch.set("bills/${bill.id}/scheduledFor", null)
                }
                batch.set("scheduled/${sp.id}", null)
            }
            batch.commit()
        }
    }

    override suspend fun cancelScheduled(id: String) {
        store.mutex.withLock {
            val sp = store.scheduled.value.firstOrNull { it.id == id } ?: return@withLock
            val batch = WriteBatch(store)
            batch.set("scheduled/$id", null)
            sp.billId?.let { batch.set("bills/$it/scheduledFor", null) }
            batch.commit()
        }
    }

    // ---------------------------------------------------------------- accounts

    override suspend fun addAccount(name: String, type: AccountType, amount: Double): Resource<Account> = store.mutex.withLock {
        if (!Validators.isValidName(name)) return@withLock Resource.Error("Enter an account name")
        if (store.accounts.value.size >= MAX_ACCOUNTS) return@withLock Resource.Error("You can have up to $MAX_ACCOUNTS accounts")
        if (store.accounts.value.any { it.name.equals(name.trim(), ignoreCase = true) }) {
            return@withLock Resource.Error("You already have an account with this name")
        }
        val isCredit = type == AccountType.CREDIT
        if (isCredit && amount !in 500.0..50_000.0) return@withLock Resource.Error("Credit limit must be between \$500 and \$50,000")
        if (!isCredit && (amount < 0 || amount > 10_000.0)) return@withLock Resource.Error("Opening deposit must be between \$0 and \$10,000")

        val id = store.newId()
        val account = Account(
            id, name.trim(), type, UserSeeder.generateNumber(isCredit),
            balance = if (isCredit) 0.0 else amount, creditLimit = if (isCredit) amount else 0.0
        )
        val batch = WriteBatch(store)
        batch.set("accounts/$id", account.toMap())
        if (!isCredit && amount > 0) batch.transaction(id, "Opening deposit", TxCategory.DEPOSIT, amount)
        batch.notification("Account added", "${account.name} ${account.masked} is ready to use.", false, NotificationType.PAYMENT)
        when (val r = batch.commit()) {
            is Resource.Success -> Resource.Success(account)
            is Resource.Error -> r
        }
    }

    override suspend fun deleteAccount(id: String): Resource<Unit> = store.mutex.withLock {
        val acc = store.accounts.value.firstOrNull { it.id == id } ?: return@withLock Resource.Error("Account not found")
        if (Math.abs(acc.balance) > 0.004) {
            val what = if (acc.isCredit) "Pay off the card balance" else "Transfer the remaining balance to another account"
            return@withLock Resource.Error("$what of ${Formatters.money(acc.balance)} before closing ${acc.name}.")
        }
        if (!acc.isCredit && store.accounts.value.count { !it.isCredit } <= 1) {
            return@withLock Resource.Error("You need at least one checking or savings account.")
        }
        val batch = WriteBatch(store)
        batch.set("accounts/$id", null)
        store.transactions.value.filter { it.accountId == id }.forEach { batch.set("transactions/${it.id}", null) }
        store.scheduled.value.filter { it.fromAccountId == id }.forEach { sp ->
            batch.set("scheduled/${sp.id}", null)
            sp.billId?.let { batch.set("bills/$it/scheduledFor", null) }
        }
        batch.notification("Account closed", "${acc.name} ${acc.masked} was closed.", true, NotificationType.SECURITY)
        batch.commit()
    }

    // ---------------------------------------------------------------- payees

    override suspend fun addPayee(name: String, kind: PayeeKind, identifier: String): Resource<Payee> {
        if (!Validators.isValidName(name)) return Resource.Error("Enter the recipient's name")
        when (kind) {
            PayeeKind.BANK -> if (!Validators.isValidAccountNumber(identifier)) return Resource.Error("Enter a valid account number (8–17 digits)")
            PayeeKind.MOBILE -> if (!Validators.isValidPhone(identifier)) return Resource.Error("Enter a valid mobile number")
        }
        if (store.payees.value.any { it.identifier.filter(Char::isLetterOrDigit) == identifier.filter(Char::isLetterOrDigit) }) {
            return Resource.Error("This recipient already exists")
        }
        val payee = Payee(store.newId(), name.trim(), kind, identifier.trim())
        val result = store.write(mapOf("payees/${payee.id}" to mapOf("name" to payee.name, "kind" to kind.name, "identifier" to payee.identifier)))
        return when (result) {
            is Resource.Success -> Resource.Success(payee)
            is Resource.Error -> result
        }
    }

    override suspend fun removePayee(id: String) {
        store.write(mapOf("payees/$id" to null))
    }

    // ---------------------------------------------------------------- statements

    override fun statements(): List<Statement> =
        store.accounts.value.sortedBy { it.type.ordinal }.flatMap { acc ->
            (1..6).map { ago ->
                val cal = SeedData.monthStart(ago)
                Statement("${acc.id}_${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH)}", acc.id, acc.name, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
            }
        }

    override suspend fun statementData(statementId: String): Resource<StatementData> {
        val statement = statements().firstOrNull { it.id == statementId } ?: return Resource.Error("Statement not found")
        val account = getAccount(statement.accountId) ?: return Resource.Error("Account not found")
        val start = Calendar.getInstance().apply { clear(); set(statement.year, statement.month, 1) }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        val all = store.transactions.value.filter { it.accountId == account.id }
        val inPeriod = all.filter { it.timestamp >= start.timeInMillis && it.timestamp < end.timeInMillis }.sortedBy { it.timestamp }
        fun delta(t: Transaction) = if (account.isCredit) -t.amount else t.amount
        val after = all.filter { it.timestamp >= end.timeInMillis }.sumOf { delta(it) }
        val closing = account.balance - after
        val opening = closing - inPeriod.sumOf { delta(it) }
        return Resource.Success(StatementData(statement, account, opening, closing, inPeriod))
    }

    // ---------------------------------------------------------------- helpers

    private fun scheduledMap(title: String, amount: Double, date: Long, fromId: String, billId: String?, payeeId: String?): Map<String, Any?> =
        mapOf("title" to title, "amount" to amount, "date" to date, "fromAccountId" to fromId, "billId" to billId, "payeeId" to payeeId)

    private fun Resource<Unit>.toMessage(success: String): Resource<String> = when (this) {
        is Resource.Success -> Resource.Success(success)
        is Resource.Error -> this
    }

    private companion object {
        const val EPS = 0.001
        const val MAX_ACCOUNTS = 8
        const val SCHEDULE_THRESHOLD = 6 * 60 * 60 * 1000L
    }
}
