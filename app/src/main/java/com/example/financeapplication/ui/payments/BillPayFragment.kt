package com.example.financeapplication.ui.payments

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.core.Formatters
import com.example.financeapplication.core.Resource
import com.example.financeapplication.databinding.FragmentBillPayBinding
import com.example.financeapplication.databinding.ItemRowActionBinding
import com.example.financeapplication.domain.repository.BankRepository
import com.example.financeapplication.domain.usecase.PayBillUseCase
import com.example.financeapplication.ui.common.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BillRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val amount: String,
    val icon: Int,
    val action: String?,
    val isScheduledItem: Boolean
)

@HiltViewModel
class BillPayViewModel @Inject constructor(
    private val bank: BankRepository,
    private val payBill: PayBillUseCase
) : BaseViewModel() {
    private val tab = MutableStateFlow(0)

    val rows: StateFlow<List<BillRow>> = combine(bank.bills, bank.scheduled, tab) { bills, scheduled, t ->
        if (t == 0) {
            bills.sortedBy { it.paid }.map { b ->
                BillRow(
                    b.id, b.name,
                    when {
                        b.paid -> "Paid"
                        b.scheduledFor != null -> "Scheduled for ${Formatters.date(b.scheduledFor)}"
                        else -> "Due ${Formatters.date(b.dueDate)}"
                    },
                    Formatters.money(b.amount), b.category.iconRes(),
                    if (b.paid) null else "Pay", false
                )
            }
        } else {
            scheduled.map { s ->
                BillRow(s.id, s.title, "On ${Formatters.date(s.date)}", Formatters.money(s.amount), com.example.financeapplication.R.drawable.ci_clock, "Cancel", true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentTab: StateFlow<Int> = tab.asStateFlow()
    fun selectTab(index: Int) { tab.value = index }

    fun pay(billId: String, scheduleFor: Long?) {
        viewModelScope.launch {
            val from = bank.accounts.first().firstOrNull { !it.isCredit }
            when (val r = payBill(billId, from?.id, scheduleFor)) {
                is Resource.Success -> message(r.data)
                is Resource.Error -> message(r.message)
            }
        }
    }

    fun cancel(id: String) { viewModelScope.launch { bank.cancelScheduled(id); message("Scheduled payment cancelled") } }
}

@AndroidEntryPoint
class BillPayFragment : BaseFragment<FragmentBillPayBinding>(FragmentBillPayBinding::inflate) {
    private val viewModel: BillPayViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Bill Pay")
        val adapter = SimpleAdapter<BillRow, ItemRowActionBinding>(ItemRowActionBinding::inflate, { it.id }) { row ->
            bindRow(
                title = row.title, subtitle = row.subtitle, amount = row.amount, icon = row.icon, action = row.action,
                onAction = { if (row.isScheduledItem) cancelDialog(row) else payDialog(row) }
            )
        }
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter
        binding.segments.addOnButtonCheckedListener { _, id, checked ->
            if (checked) viewModel.selectTab(if (id == binding.btnAll.id) 0 else 1)
        }
        collect(viewModel.rows) { rows ->
            adapter.submitList(rows)
            binding.tvEmpty.isVisible = rows.isEmpty()
            binding.tvEmpty.text = if (viewModel.currentTab.value == 0) "No bills" else "No scheduled payments"
        }
        collectEvents(viewModel.events)
    }

    private fun payDialog(row: BillRow) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Pay ${row.title}")
            .setMessage("${row.amount} will be paid from your checking account.")
            .setPositiveButton("Pay now") { _, _ -> viewModel.pay(row.id, null) }
            .setNeutralButton("Schedule") { _, _ -> pickFutureDate { viewModel.pay(row.id, it) } }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun cancelDialog(row: BillRow) {
        confirm("Cancel payment", "Cancel the scheduled payment for ${row.title}?", "Cancel payment") { viewModel.cancel(row.id) }
    }
}
