package com.example.financeapplication.ui.payments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financeapplication.R
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.core.Formatters
import com.example.financeapplication.core.Resource
import com.example.financeapplication.core.UiEvent
import com.example.financeapplication.databinding.FragmentStatementsBinding
import com.example.financeapplication.databinding.ItemRowActionBinding
import com.example.financeapplication.domain.model.Account
import com.example.financeapplication.domain.model.Statement
import com.example.financeapplication.domain.repository.BankRepository
import com.example.financeapplication.domain.repository.StatementExporter
import com.example.financeapplication.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatementsViewModel @Inject constructor(
    private val bank: BankRepository,
    private val exporter: StatementExporter
) : BaseViewModel() {
    val accounts: StateFlow<List<Account>> = bank.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val selected = MutableStateFlow<String?>(null)

    val statements: StateFlow<List<Statement>> = combine(accounts, selected) { accs, sel ->
        val id = sel ?: accs.firstOrNull()?.id
        bank.statements().filter { it.accountId == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun select(accountId: String) { selected.value = accountId }

    fun download(statement: Statement) {
        viewModelScope.launch {
            when (val r = bank.statementData(statement.id)) {
                is Resource.Success -> {
                    runCatching { exporter.export(r.data) }
                        .onSuccess { emit(UiEvent.FileReady(it)) }
                        .onFailure { message("Could not create the statement PDF") }
                }
                is Resource.Error -> message(r.message)
            }
        }
    }
}

@AndroidEntryPoint
class StatementsFragment : BaseFragment<FragmentStatementsBinding>(FragmentStatementsBinding::inflate) {
    private val viewModel: StatementsViewModel by viewModels()
    private var accounts: List<Account> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Statements")
        val adapter = SimpleAdapter<Statement, ItemRowActionBinding>(ItemRowActionBinding::inflate, { it.id }) { s ->
            bindRow(
                title = Formatters.monthYear(s.year, s.month), subtitle = s.accountName,
                icon = R.drawable.ci_statement, action = "Download", onAction = { viewModel.download(s) }
            )
        }
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter
        binding.acAccount.setOnItemClickListener { _, _, pos, _ -> accounts.getOrNull(pos)?.let { viewModel.select(it.id) } }
        collect(viewModel.accounts) { list ->
            accounts = list
            binding.acAccount.setItems(list.map { "${it.name} ${it.masked}" }, if (binding.acAccount.text.isNullOrEmpty()) 0 else null)
        }
        collect(viewModel.statements) { adapter.submitList(it) }
        collectEvents(viewModel.events)
    }
}
