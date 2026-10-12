package com.example.financeapplication.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financeapplication.R
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.core.Formatters
import com.example.financeapplication.databinding.FragmentHomeBinding
import com.example.financeapplication.databinding.ItemTransactionBinding
import com.example.financeapplication.domain.model.Account
import com.example.financeapplication.domain.model.Transaction
import com.example.financeapplication.domain.repository.AuthRepository
import com.example.financeapplication.domain.repository.BankRepository
import com.example.financeapplication.domain.repository.NotificationRepository
import com.example.financeapplication.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val greeting: String = "",
    val name: String = "",
    val initials: String = "",
    val totalBalance: Double = 0.0,
    val recent: List<Transaction> = emptyList(),
    val hasUnread: Boolean = false,
    val balanceHidden: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    auth: AuthRepository,
    private val bank: BankRepository,
    notifications: NotificationRepository
) : BaseViewModel() {

    private val hidden = MutableStateFlow(false)
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val state: StateFlow<HomeUiState> = combine(
        auth.currentUser, bank.accounts, bank.transactions, notifications.notifications, hidden
    ) { user, accounts, txs, notes, isHidden ->
        HomeUiState(
            greeting = Formatters.greeting(),
            name = user?.fullName.orEmpty(),
            initials = user?.initials.orEmpty(),
            totalBalance = accounts.filter { !it.isCredit }.sumOf { it.balance },
            recent = txs.take(5),
            hasUnread = notes.any { !it.read },
            balanceHidden = isHidden
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init { refresh() }

    fun toggleBalance() { hidden.value = !hidden.value }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            bank.processScheduled()
            delay(500)
            _refreshing.value = false
        }
    }
}

@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {
    private val viewModel: HomeViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = SimpleAdapter<Transaction, ItemTransactionBinding>(ItemTransactionBinding::inflate, { it.id }) { bindTransaction(it) }
        with(binding) {
            rvTransactions.layoutManager = LinearLayoutManager(requireContext())
            rvTransactions.adapter = adapter
            swipe.setOnRefreshListener { viewModel.refresh() }
            btnBell.setOnClickListener { go(R.id.notificationsFragment) }
            tvAvatar.setOnClickListener { go(R.id.profileFragment) }
            btnEye.setOnClickListener { viewModel.toggleBalance() }
            btnSeeAll.setOnClickListener { go(R.id.transactionsFragment) }
            quick.actTransfer.setOnClickListener { go(R.id.transferFragment) }
            quick.actBills.setOnClickListener { go(R.id.billPayFragment) }
            quick.actDeposit.setOnClickListener { go(R.id.checkDepositFragment) }
            quick.actMore.setOnClickListener { go(R.id.moreFragment) }
        }
        collect(viewModel.state) { s ->
            with(binding) {
                tvGreeting.text = s.greeting + ","
                tvName.text = s.name
                tvAvatar.text = s.initials
                tvBalance.text = if (s.balanceHidden) "••••••" else Formatters.money(s.totalBalance)
                badge.isVisible = s.hasUnread
            }
            adapter.submitList(s.recent)
        }
        collect(viewModel.refreshing) { binding.swipe.isRefreshing = it }
    }
}
