package com.example.financeapplication.ui.payments

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.databinding.FragmentTransactionsBinding
import com.example.financeapplication.databinding.ItemTransactionBinding
import com.example.financeapplication.domain.model.Transaction
import com.example.financeapplication.domain.repository.BankRepository
import com.example.financeapplication.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

enum class TxFilter { ALL, DEPOSITS, WITHDRAWALS }

@HiltViewModel
class TransactionsViewModel @Inject constructor(bank: BankRepository) : BaseViewModel() {
    private val filter = MutableStateFlow(TxFilter.ALL)
    private val query = MutableStateFlow("")

    val items: StateFlow<List<Transaction>> = combine(bank.transactions, filter, query) { txs, f, q ->
        txs.filter { t ->
            val typeOk = when (f) {
                TxFilter.ALL -> true
                TxFilter.DEPOSITS -> t.amount > 0
                TxFilter.WITHDRAWALS -> t.amount < 0
            }
            typeOk && (q.isBlank() || t.title.contains(q.trim(), ignoreCase = true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(f: TxFilter) { filter.value = f }
    fun setQuery(q: String) { query.value = q }
}

@AndroidEntryPoint
class TransactionsFragment : BaseFragment<FragmentTransactionsBinding>(FragmentTransactionsBinding::inflate) {
    private val viewModel: TransactionsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Transaction History")
        val adapter = SimpleAdapter<Transaction, ItemTransactionBinding>(ItemTransactionBinding::inflate, { it.id }) { bindTransaction(it) }
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter
        binding.etSearch.doAfterTextChanged { viewModel.setQuery(it?.toString().orEmpty()) }
        binding.segments.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            viewModel.setFilter(when (id) { binding.btnDeposits.id -> TxFilter.DEPOSITS; binding.btnWithdrawals.id -> TxFilter.WITHDRAWALS; else -> TxFilter.ALL })
        }
        collect(viewModel.items) {
            adapter.submitList(it)
            binding.tvEmpty.isVisible = it.isEmpty()
            binding.tvEmpty.text = "No transactions found"
        }
    }
}
