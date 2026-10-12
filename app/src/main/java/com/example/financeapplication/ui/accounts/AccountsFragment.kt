package com.example.financeapplication.ui.accounts

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financeapplication.R
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.core.Formatters
import com.example.financeapplication.core.Validators
import com.example.financeapplication.databinding.*
import com.example.financeapplication.core.Resource
import com.example.financeapplication.domain.model.Account
import com.example.financeapplication.domain.model.AccountType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import android.text.InputType
import com.example.financeapplication.domain.model.Transaction
import com.example.financeapplication.domain.repository.BankRepository
import com.example.financeapplication.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(private val bank: BankRepository) : BaseViewModel() {
    val accounts: StateFlow<List<Account>> = bank.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String, type: AccountType, amount: Double) {
        viewModelScope.launch {
            when (val r = bank.addAccount(name, type, amount)) {
                is Resource.Success -> message("${r.data.name} added")
                is Resource.Error -> message(r.message)
            }
        }
    }
}

@AndroidEntryPoint
class AccountsFragment : BaseFragment<FragmentAccountsBinding>(FragmentAccountsBinding::inflate) {
    private val viewModel: AccountsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Accounts", showBack = false)
        binding.toolbar.btnAction.visibility = View.VISIBLE
        binding.toolbar.btnAction.setImageResource(R.drawable.ci_add)
        binding.toolbar.btnAction.contentDescription = "Add account"
        binding.toolbar.btnAction.setOnClickListener { chooseType() }
        val adapter = SimpleAdapter<Account, ItemAccountBinding>(ItemAccountBinding::inflate, { it.id }) { acc ->
            bindAccount(acc) { go(R.id.accountDetailsFragment, bundleOf("accountId" to it.id)) }
        }
        binding.rvAccounts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAccounts.adapter = adapter
        collect(viewModel.accounts) { list ->
            adapter.submitList(list)
            binding.tvTotal.text = Formatters.money(list.filter { !it.isCredit }.sumOf { it.balance })
        }
        collectEvents(viewModel.events)
    }

    private fun chooseType() {
        val types = AccountType.values()
        MaterialAlertDialogBuilder(requireContext()).setTitle("Add account")
            .setItems(arrayOf("Checking account", "Savings account", "Credit card")) { _, which -> showForm(types[which]) }
            .show()
    }

    private fun showForm(type: AccountType) {
        val credit = type == AccountType.CREDIT
        formDialog(
            "New ${type.name.lowercase()} account",
            listOf(
                Triple("Account name", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, ""),
                Triple(if (credit) "Credit limit" else "Opening deposit", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, "")
            ), "Create"
        ) { v ->
            val amount = Validators.parseAmount(v[1])
            when {
                !Validators.isValidName(v[0]) -> "Enter an account name"
                amount == null && !(v[1].isEmpty() && !credit) -> "Enter a valid amount"
                else -> { viewModel.add(v[0], type, amount ?: 0.0); null }
            }
        }
    }
}

data class AccountDetailsState(val account: Account? = null, val transactions: List<Transaction> = emptyList())

@HiltViewModel
class AccountDetailsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val bank: BankRepository
) : BaseViewModel() {
    private val accountId: String = savedState["accountId"] ?: ""
    val state: StateFlow<AccountDetailsState> = combine(bank.accounts, bank.transactions) { accounts, txs ->
        AccountDetailsState(accounts.firstOrNull { it.id == accountId }, txs.filter { it.accountId == accountId }.take(10))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailsState())

    fun delete() {
        viewModelScope.launch {
            when (val r = bank.deleteAccount(accountId)) {
                is Resource.Success -> done("Account closed")
                is Resource.Error -> message(r.message)
            }
        }
    }
}

@AndroidEntryPoint
class AccountDetailsFragment : BaseFragment<FragmentAccountDetailsBinding>(FragmentAccountDetailsBinding::inflate) {
    private val viewModel: AccountDetailsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Account Details")
        val adapter = SimpleAdapter<Transaction, ItemTransactionBinding>(ItemTransactionBinding::inflate, { it.id }) { bindTransaction(it) }
        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter
        binding.btnDelete.setOnClickListener {
            val acc = viewModel.state.value.account ?: return@setOnClickListener
            confirm("Close account", "Close ${acc.name} ${acc.masked}? This also removes its transaction history and cannot be undone.", "Close account") { viewModel.delete() }
        }
        collectEvents(viewModel.events) { msg ->
            snack(msg ?: "Done")
            back()
        }
        collect(viewModel.state) { s ->
            val acc = s.account ?: return@collect
            with(binding) {
                tvAccountName.text = acc.name
                tvAccountNumber.text = acc.masked
                tvBalanceLabel.text = if (acc.isCredit) "Current Balance" else "Available Balance"
                tvBalance.text = Formatters.money(if (acc.isCredit) acc.balance else acc.availableBalance)
                tvSecondary.text = if (acc.isCredit) "Available credit ${Formatters.money(acc.availableBalance)}" else ""
                tvSecondary.isVisible = acc.isCredit
                val args = bundleOf("fromAccountId" to acc.id)
                quick.actTransfer.setOnClickListener { go(R.id.transferFragment, args) }
                quick.actBills.setOnClickListener { go(R.id.billPayFragment) }
                quick.actDeposit.setOnClickListener {
                    if (acc.isCredit) go(R.id.cardFragment) else go(R.id.checkDepositFragment, bundleOf("toAccountId" to acc.id))
                }
                quick.actMore.setOnClickListener { go(R.id.statementsFragment) }
                tvEmpty.isVisible = s.transactions.isEmpty()
            }
            adapter.submitList(s.transactions)
        }
    }
}
