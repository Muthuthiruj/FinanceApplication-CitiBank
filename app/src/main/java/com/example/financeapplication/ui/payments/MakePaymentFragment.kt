package com.example.financeapplication.ui.payments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.core.Formatters
import com.example.financeapplication.core.Resource
import com.example.financeapplication.core.Validators
import com.example.financeapplication.databinding.FragmentMakePaymentBinding
import com.example.financeapplication.domain.model.Account
import com.example.financeapplication.domain.model.Payee
import com.example.financeapplication.domain.repository.BankRepository
import com.example.financeapplication.domain.usecase.MakePaymentUseCase
import com.example.financeapplication.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MakePaymentViewModel @Inject constructor(
    bank: BankRepository,
    private val makePayment: MakePaymentUseCase
) : BaseViewModel() {
    val payees: StateFlow<List<Payee>> = bank.payees.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts: StateFlow<List<Account>> = bank.accounts.map { l -> l.filter { !it.isCredit } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun pay(fromId: String?, payeeId: String?, amount: Double, memo: String, date: Long) {
        viewModelScope.launch {
            _loading.value = true
            when (val r = makePayment(fromId, payeeId, amount, memo, date)) {
                is Resource.Success -> done(r.data)
                is Resource.Error -> message(r.message)
            }
            _loading.value = false
        }
    }
}

@AndroidEntryPoint
class MakePaymentFragment : BaseFragment<FragmentMakePaymentBinding>(FragmentMakePaymentBinding::inflate) {
    private val viewModel: MakePaymentViewModel by viewModels()
    private var payees: List<Payee> = emptyList()
    private var accounts: List<Account> = emptyList()
    private var date = System.currentTimeMillis()
    private val whenOptions = listOf("Today", "Pick a date…")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            toolbar.setup(this@MakePaymentFragment, "Make a Payment")
            tilAmount.clearErrorOnEdit()
            acWhen.setItems(whenOptions, 0)
            acWhen.setOnItemClickListener { _, _, position, _ ->
                if (position == 0) {
                    date = System.currentTimeMillis()
                } else {
                    pickFutureDate {
                        date = it
                        acWhen.setText("Scheduled: ${Formatters.date(it)}", false)
                    }
                    acWhen.setText(whenOptions[0], false)
                }
            }
            btnContinue.setOnClickListener { onContinue() }
        }
        collect(viewModel.payees) { list ->
            payees = list
            binding.acPayTo.setItems(list.map { "${it.name} · ${it.subtitle}" })
            binding.tvHint.text = if (list.isEmpty()) "You have no payees yet. Add one under Recipients." else ""
        }
        collect(viewModel.accounts) { list ->
            accounts = list
            val keep = binding.acFrom.text?.isNotEmpty() == true
            binding.acFrom.setItems(list.map { "${it.name} ${it.masked} · ${Formatters.money(it.balance)}" }, if (keep) null else 0)
        }
        collect(viewModel.loading) {
            binding.btnContinue.isEnabled = !it
            binding.btnContinue.text = if (it) "Processing…" else "Continue"
        }
        collectEvents(viewModel.events) { msg -> info("Payment", msg ?: "Done") { back() } }
    }

    private fun onContinue() {
        hideKeyboard()
        val payee = payees.getOrNull(binding.acPayTo.selectedIndex(binding.acPayTo.adapterItems()))
        val from = accounts.getOrNull(binding.acFrom.selectedIndex(binding.acFrom.adapterItems()))
        val amount = Validators.parseAmount(binding.etAmount.value())
        when {
            payee == null -> { snack("Select who you want to pay"); return }
            from == null -> { snack("Select the account to pay from"); return }
            amount == null || amount <= 0 -> { binding.tilAmount.error = "Enter a valid amount"; return }
        }
        val whenText = if (date > System.currentTimeMillis() + 6 * 3_600_000L) "on ${Formatters.date(date)}" else "today"
        confirm("Confirm payment", "Pay ${Formatters.money(amount!!)} to ${payee!!.name} from ${from!!.name} $whenText?", "Pay") {
            viewModel.pay(from.id, payee.id, amount, binding.etMemo.value(), date)
        }
    }
}

/** Items currently shown by an exposed-dropdown, used to resolve the selected index. */
fun com.google.android.material.textfield.MaterialAutoCompleteTextView.adapterItems(): List<String> {
    val a = adapter ?: return emptyList()
    return (0 until a.count).map { a.getItem(it).toString() }
}
