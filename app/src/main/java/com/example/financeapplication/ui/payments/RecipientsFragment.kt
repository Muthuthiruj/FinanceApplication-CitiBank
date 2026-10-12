package com.example.financeapplication.ui.payments

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financeapplication.R
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.core.Resource
import com.example.financeapplication.databinding.FragmentRecipientsBinding
import com.example.financeapplication.databinding.ItemRowActionBinding
import com.example.financeapplication.domain.model.Payee
import com.example.financeapplication.domain.model.PayeeKind
import com.example.financeapplication.domain.repository.BankRepository
import com.example.financeapplication.domain.usecase.AddPayeeUseCase
import com.example.financeapplication.ui.common.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecipientsViewModel @Inject constructor(
    private val bank: BankRepository,
    private val addPayee: AddPayeeUseCase
) : BaseViewModel() {
    private val filter = MutableStateFlow<PayeeKind?>(null)
    val payees: StateFlow<List<Payee>> = combine(bank.payees, filter) { list, f -> list.filter { f == null || it.kind == f } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(kind: PayeeKind?) { filter.value = kind }

    /** @return error message or null on success – used to keep the dialog open on failure. */
    fun add(name: String, kind: PayeeKind, identifier: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            when (val r = addPayee(name, kind, identifier)) {
                is Resource.Success -> { message("${r.data.name} added"); onResult(null) }
                is Resource.Error -> onResult(r.message)
            }
        }
    }

    fun remove(payee: Payee) { viewModelScope.launch { bank.removePayee(payee.id); message("${payee.name} removed") } }
}

@AndroidEntryPoint
class RecipientsFragment : BaseFragment<FragmentRecipientsBinding>(FragmentRecipientsBinding::inflate) {
    private val viewModel: RecipientsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Recipients / Payees")
        val adapter = SimpleAdapter<Payee, ItemRowActionBinding>(ItemRowActionBinding::inflate, { it.id }) { p ->
            bindRow(
                title = p.name, subtitle = p.subtitle,
                initials = p.name.first().uppercase(),
                action = "Remove",
                onAction = { confirm("Remove recipient", "Remove ${p.name} from your recipients?", "Remove") { viewModel.remove(p) } },
                onClick = { go(R.id.transferFragment, bundleOf("payeeId" to p.id)) }
            )
        }
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter
        binding.segments.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            viewModel.setFilter(when (id) { binding.btnBank.id -> PayeeKind.BANK; binding.btnMobile.id -> PayeeKind.MOBILE; else -> null })
        }
        binding.btnAdd.setOnClickListener { chooseKind() }
        collect(viewModel.payees) {
            adapter.submitList(it)
            binding.tvEmpty.isVisible = it.isEmpty()
            binding.tvEmpty.text = "No recipients yet"
        }
        collectEvents(viewModel.events)
    }

    private fun chooseKind() {
        MaterialAlertDialogBuilder(requireContext()).setTitle("Add recipient")
            .setItems(arrayOf("Bank account", "Mobile number")) { _, which ->
                showForm(if (which == 0) PayeeKind.BANK else PayeeKind.MOBILE)
            }.show()
    }

    private fun showForm(kind: PayeeKind) {
        val idHint = if (kind == PayeeKind.BANK) "Account number" else "Mobile number"
        val idType = if (kind == PayeeKind.BANK) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_PHONE
        formDialog(
            "New ${if (kind == PayeeKind.BANK) "bank" else "mobile"} recipient",
            listOf(
                Triple("Full name", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, ""),
                Triple(idHint, idType, "")
            ), "Add"
        ) { values ->
            // The dialog closes immediately on null; failures are reported through a snackbar instead.
            viewModel.add(values[0], kind, values[1]) { error -> if (error != null) snack(error) }
            null
        }
    }
}
