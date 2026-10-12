package com.example.financeapplication.ui.cards

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financeapplication.R
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.core.Formatters
import com.example.financeapplication.core.Resource
import com.example.financeapplication.data.seed.SeedData
import com.example.financeapplication.databinding.FragmentCardBinding
import com.example.financeapplication.databinding.FragmentCardControlsBinding
import com.example.financeapplication.databinding.ItemTransactionBinding
import com.example.financeapplication.domain.model.AccountType
import com.example.financeapplication.domain.model.Card
import com.example.financeapplication.domain.model.CardControls
import com.example.financeapplication.domain.model.Transaction
import com.example.financeapplication.domain.repository.BankRepository
import com.example.financeapplication.domain.repository.CardRepository
import com.example.financeapplication.domain.usecase.TransferMoneyUseCase
import com.example.financeapplication.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CardUiState(
    val card: Card? = null,
    val controls: CardControls = CardControls(),
    val transactions: List<Transaction> = emptyList()
)

@HiltViewModel
class CardViewModel @Inject constructor(
    private val cards: CardRepository,
    private val bank: BankRepository,
    private val transfer: TransferMoneyUseCase
) : BaseViewModel() {

    val state: StateFlow<CardUiState> = combine(cards.card, cards.controls, bank.transactions) { card, controls, txs ->
        CardUiState(card, controls, txs.filter { it.accountId == card!!.accountId }.take(8))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CardUiState())

    fun payCard(amount: Double) {
        viewModelScope.launch {
            val card = state.value.card ?: return@launch
            val from = bank.accounts.first().firstOrNull { it.type == AccountType.CHECKING }
            when (val r = transfer(from?.id, card.accountId, null, amount, "Credit card payment")) {
                is Resource.Success -> message("Payment of ${Formatters.money(amount)} sent to your card")
                is Resource.Error -> message(r.message)
            }
        }
    }

    fun update(transform: (CardControls) -> CardControls) {
        viewModelScope.launch { cards.updateControls(transform) }
    }
}

@AndroidEntryPoint
class CardFragment : BaseFragment<FragmentCardBinding>(FragmentCardBinding::inflate) {
    private val viewModel: CardViewModel by viewModels()
    private var revealed = false
    private var lastCard: Card? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Credit Card")
        val adapter = SimpleAdapter<Transaction, ItemTransactionBinding>(ItemTransactionBinding::inflate, { it.id }) { bindTransaction(it) }
        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter
        binding.btnControls.setOnClickListener { go(R.id.cardControlsFragment) }
        binding.btnPay.setOnClickListener {
            promptAmount("Pay credit card", "Amount from Checking") { viewModel.payCard(it) }
        }
        binding.btnReveal.setOnClickListener { revealed = !revealed; renderNumber() }
        collect(viewModel.state) { s ->
            val card = s.card
            binding.btnPay.isEnabled = card != null
            binding.btnControls.isEnabled = card != null
            if (card == null) {
                lastCard = null
                binding.tvNumber.text = "No credit card"
                binding.tvHolder.text = "Add a Credit Card account from the Accounts tab"
                binding.tvExpiry.text = ""
                binding.tvAvailable.text = "-"
                binding.tvCurrent.text = "-"
                adapter.submitList(emptyList())
                return@collect
            }
            lastCard = card
            with(binding) {
                tvHolder.text = card.holder
                tvExpiry.text = "Expires ${card.expiry}"
                tvAvailable.text = Formatters.money(card.availableCredit)
                tvCurrent.text = Formatters.money(card.currentBalance)
                tvLocked.isVisible = s.controls.quickLock
            }
            renderNumber()
            adapter.submitList(s.transactions)
        }
        collectEvents(viewModel.events)
    }

    private fun renderNumber() {
        val card = lastCard ?: return
        binding.tvNumber.text = if (revealed) card.spaced else card.masked
        binding.btnReveal.text = if (revealed) "Hide card number" else "Show card number"
    }
}

@AndroidEntryPoint
class CardControlsFragment : BaseFragment<FragmentCardControlsBinding>(FragmentCardControlsBinding::inflate) {
    private val viewModel: CardViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Card Controls")
        collect(viewModel.state) { s ->
            val c = s.controls
            with(binding) {
                tvStatus.text = if (c.quickLock) "Your card is locked. New purchases are declined." else "Your card is active."
                swQuickLock.bind(c.quickLock) { v -> viewModel.update { it.copy(quickLock = v) } }
                swOnline.bind(c.onlinePurchases) { v -> viewModel.update { it.copy(onlinePurchases = v) } }
                swAtm.bind(c.atmWithdrawals) { v -> viewModel.update { it.copy(atmWithdrawals = v) } }
                swInternational.bind(c.international) { v -> viewModel.update { it.copy(international = v) } }
                swAlerts.bind(c.transactionAlerts) { v -> viewModel.update { it.copy(transactionAlerts = v) } }
                // While locked the other switches have no effect
                listOf(swOnline, swAtm, swInternational).forEach { it.isEnabled = !c.quickLock }
            }
        }
    }
}
