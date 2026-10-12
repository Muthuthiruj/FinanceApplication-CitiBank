package com.example.financeapplication.ui.payments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.core.Formatters
import com.example.financeapplication.core.Resource
import com.example.financeapplication.core.Validators
import com.example.financeapplication.databinding.FragmentTransferBinding
import com.example.financeapplication.domain.model.Account
import com.example.financeapplication.domain.repository.BankRepository
import com.example.financeapplication.domain.usecase.TransferMoneyUseCase
import com.example.financeapplication.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Destination(val label: String, val accountId: String?, val payeeId: String?)
data class TransferForm(val accounts: List<Account> = emptyList(), val destinations: List<Destination> = emptyList())

@HiltViewModel
class TransferViewModel @Inject constructor(
    savedState: SavedStateHandle,
    bank: BankRepository,
    private val transfer: TransferMoneyUseCase
) : BaseViewModel() {
    val initialFromId: String? = savedState["fromAccountId"]
    val initialPayeeId: String? = savedState["payeeId"]

    val form: StateFlow<TransferForm> = combine(bank.accounts, bank.payees) { accounts, payees ->
        TransferForm(
            accounts = accounts.filter { !it.isCredit },
            destinations = accounts.map { Destination("${it.name} ${it.masked}", it.id, null) } +
                payees.map { Destination("${it.name} · ${it.subtitle}", null, it.id) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransferForm())

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun send(from: String?, dest: Destination?, amount: Double, note: String) {
        viewModelScope.launch {
            _loading.value = true
            when (val r = transfer(from, dest?.accountId, dest?.payeeId, amount, note)) {
                is Resource.Success -> done("Transfer of ${Formatters.money(amount)} completed")
                is Resource.Error -> message(r.message)
            }
            _loading.value = false
        }
    }
}

@AndroidEntryPoint
class TransferFragment : BaseFragment<FragmentTransferBinding>(FragmentTransferBinding::inflate) {
    private val viewModel: TransferViewModel by viewModels()
    private var form = TransferForm()
    private var fromIndex = 0
    private var shownDestinations: List<Destination> = emptyList()
    private var destIndex: Int? = null
    private var initialised = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            toolbar.setup(this@TransferFragment, "Transfer Money")
            tilAmount.clearErrorOnEdit()
            acFrom.setOnItemClickListener { _, _, pos, _ -> fromIndex = pos; refreshFrom(); refreshDestinations(keepSelection = true) }
            acTo.setOnItemClickListener { _, _, pos, _ -> destIndex = pos }
            btnTransfer.setOnClickListener { onTransfer() }
        }
        collect(viewModel.form) { f ->
            form = f
            if (f.accounts.isEmpty()) return@collect
            binding.acFrom.setItems(f.accounts.map { "${it.name} ${it.masked}" }, if (initialised) null else 0)
            if (!initialised) {
                initialised = true
                fromIndex = f.accounts.indexOfFirst { it.id == viewModel.initialFromId }.coerceAtLeast(0)
                binding.acFrom.setText("${f.accounts[fromIndex].name} ${f.accounts[fromIndex].masked}", false)
                refreshDestinations(keepSelection = false)
                viewModel.initialPayeeId?.let { pid ->
                    val i = shownDestinations.indexOfFirst { it.payeeId == pid }
                    if (i >= 0) { destIndex = i; binding.acTo.setText(shownDestinations[i].label, false) }
                }
            } else {
                refreshDestinations(keepSelection = true)
            }
            refreshFrom()
        }
        collect(viewModel.loading) {
            binding.btnTransfer.isEnabled = !it
            binding.btnTransfer.text = if (it) "Sending…" else "Transfer"
        }
        collectEvents(viewModel.events) { msg -> info("Transfer", msg ?: "Done") { back() } }
    }

    private fun refreshFrom() {
        val acc = form.accounts.getOrNull(fromIndex) ?: return
        binding.tvAvailable.text = "Available: ${Formatters.money(acc.availableBalance)}"
    }

    private fun refreshDestinations(keepSelection: Boolean) {
        val fromId = form.accounts.getOrNull(fromIndex)?.id
        val previous = if (keepSelection) destIndex?.let { shownDestinations.getOrNull(it) } else null
        shownDestinations = form.destinations.filter { it.accountId == null || it.accountId != fromId }
        binding.acTo.setItems(shownDestinations.map { it.label })
        val newIndex = previous?.let { p -> shownDestinations.indexOf(p).takeIf { it >= 0 } }
        destIndex = newIndex
        if (newIndex != null) binding.acTo.setText(shownDestinations[newIndex].label, false) else if (!keepSelection || previous != null) binding.acTo.setText("", false)
    }

    private fun onTransfer() {
        hideKeyboard()
        val from = form.accounts.getOrNull(fromIndex)
        val dest = destIndex?.let { shownDestinations.getOrNull(it) }
        val amount = Validators.parseAmount(binding.etAmount.value())
        if (dest == null) { snack("Select who you are sending money to"); return }
        if (amount == null || amount <= 0) { binding.tilAmount.error = "Enter a valid amount"; return }
        confirm("Confirm transfer", "Send ${Formatters.money(amount)} from ${from?.name} to ${dest.label}?", "Send") {
            viewModel.send(from?.id, dest, amount, binding.etNote.value())
        }
    }
}
