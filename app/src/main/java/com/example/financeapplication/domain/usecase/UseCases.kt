package com.example.financeapplication.domain.usecase

import com.example.financeapplication.core.Formatters
import com.example.financeapplication.core.Resource
import com.example.financeapplication.domain.model.PayeeKind
import com.example.financeapplication.domain.repository.BankRepository
import javax.inject.Inject

private const val MAX_TRANSFER = 10_000.0
private const val MAX_CHECK_DEPOSIT = 5_000.0
private const val MAX_PAYMENT = 25_000.0

private fun invalidAmount(amount: Double, max: Double): Resource.Error? = when {
    amount <= 0.0 -> Resource.Error("Enter an amount greater than zero")
    amount > max -> Resource.Error("Amount exceeds the ${Formatters.money(max)} limit")
    else -> null
}

class TransferMoneyUseCase @Inject constructor(private val bank: BankRepository) {
    suspend operator fun invoke(
        fromId: String?, toAccountId: String?, payeeId: String?, amount: Double, note: String
    ): Resource<Unit> {
        if (fromId == null) return Resource.Error("Select the account to transfer from")
        if (toAccountId == null && payeeId == null) return Resource.Error("Select who you are sending money to")
        invalidAmount(amount, MAX_TRANSFER)?.let { return it }
        if (toAccountId != null && toAccountId == fromId) return Resource.Error("Choose two different accounts")
        return bank.transfer(fromId, toAccountId, payeeId, amount, note.trim())
    }
}

class MakePaymentUseCase @Inject constructor(private val bank: BankRepository) {
    suspend operator fun invoke(fromId: String?, payeeId: String?, amount: Double, memo: String, date: Long): Resource<String> {
        if (payeeId == null) return Resource.Error("Select who you want to pay")
        if (fromId == null) return Resource.Error("Select the account to pay from")
        invalidAmount(amount, MAX_PAYMENT)?.let { return it }
        return bank.makePayment(fromId, payeeId, amount, memo.trim(), date)
    }
}

class PayBillUseCase @Inject constructor(private val bank: BankRepository) {
    suspend operator fun invoke(billId: String, fromId: String?, scheduleFor: Long?): Resource<String> {
        if (fromId == null) return Resource.Error("No account available to pay from")
        return bank.payBill(billId, fromId, scheduleFor)
    }
}

class DepositCheckUseCase @Inject constructor(private val bank: BankRepository) {
    suspend operator fun invoke(toId: String?, amount: Double, hasFront: Boolean, hasBack: Boolean): Resource<Unit> {
        if (!hasFront || !hasBack) return Resource.Error("Capture both sides of the check")
        if (toId == null) return Resource.Error("Select the account to deposit into")
        invalidAmount(amount, MAX_CHECK_DEPOSIT)?.let { return it }
        return bank.depositCheck(toId, amount)
    }
}

class AddPayeeUseCase @Inject constructor(private val bank: BankRepository) {
    suspend operator fun invoke(name: String, kind: PayeeKind, identifier: String) =
        bank.addPayee(name.trim(), kind, identifier.trim())
}
