package com.example.financeapplication.ui.rewards

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financeapplication.R
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.core.Formatters
import com.example.financeapplication.core.Resource
import com.example.financeapplication.databinding.FragmentLoansBinding
import com.example.financeapplication.databinding.FragmentOffersBinding
import com.example.financeapplication.databinding.FragmentRewardsBinding
import com.example.financeapplication.databinding.ItemRowActionBinding
import com.example.financeapplication.databinding.DialogLoanBinding
import com.example.financeapplication.core.LoanCalculator
import com.example.financeapplication.core.Validators
import com.example.financeapplication.domain.model.LoanProduct
import com.example.financeapplication.domain.model.Offer
import com.example.financeapplication.domain.model.RedeemOption
import com.example.financeapplication.domain.model.RewardsSummary
import com.example.financeapplication.domain.repository.BankRepository
import com.example.financeapplication.domain.repository.LoanRepository
import com.example.financeapplication.domain.repository.RewardsRepository
import com.example.financeapplication.ui.common.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RewardsViewModel @Inject constructor(
    private val repo: RewardsRepository,
    private val bank: BankRepository
) : BaseViewModel() {
    val rewards: StateFlow<RewardsSummary> = repo.rewards.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RewardsSummary(0))
    val offers: StateFlow<List<Offer>> = repo.offers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val options: List<RedeemOption> get() = repo.redeemOptions

    fun toggle(offer: Offer) {
        viewModelScope.launch {
            repo.toggleOffer(offer.id)
            message(if (offer.activated) "${offer.title} deactivated" else "${offer.title} activated")
        }
    }

    fun redeem(option: RedeemOption) {
        viewModelScope.launch {
            val to = bank.accounts.first().firstOrNull { !it.isCredit }
            when (val r = repo.redeem(option, to?.id ?: "")) {
                is Resource.Success -> message("${Formatters.money(option.credit)} credited to your account")
                is Resource.Error -> message(r.message)
            }
        }
    }
}

private fun offerAdapter(onToggle: (Offer) -> Unit) =
    SimpleAdapter<Offer, ItemRowActionBinding>(ItemRowActionBinding::inflate, { it.id }) { o ->
        bindRow(
            title = o.title, subtitle = o.description, amount = o.badge, icon = o.category.iconRes(),
            action = if (o.activated) "Activated" else "Activate", onAction = { onToggle(o) }
        )
    }

@AndroidEntryPoint
class RewardsFragment : BaseFragment<FragmentRewardsBinding>(FragmentRewardsBinding::inflate) {
    private val viewModel: RewardsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Rewards")
        val adapter = offerAdapter { viewModel.toggle(it) }
        binding.rvOffers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOffers.adapter = adapter
        binding.btnViewAll.setOnClickListener { go(R.id.offersFragment) }
        binding.btnRedeem.setOnClickListener { showRedeem() }
        collect(viewModel.rewards) {
            binding.tvPoints.text = Formatters.points(it.points)
            binding.tvTier.text = "${it.tier} Member"
        }
        collect(viewModel.offers) { adapter.submitList(it.take(2)) }
        collectEvents(viewModel.events)
    }

    private fun showRedeem() {
        val options = viewModel.options
        val labels = options.map { "${Formatters.money(it.credit)} statement credit – ${Formatters.points(it.points)} pts" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext()).setTitle("Redeem points")
            .setItems(labels) { _, which -> viewModel.redeem(options[which]) }
            .setNegativeButton("Cancel", null).show()
    }
}

@AndroidEntryPoint
class OffersFragment : BaseFragment<FragmentOffersBinding>(FragmentOffersBinding::inflate) {
    private val viewModel: RewardsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Offers")
        val adapter = offerAdapter { viewModel.toggle(it) }
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter
        collect(viewModel.offers) { adapter.submitList(it) }
        collectEvents(viewModel.events)
    }
}

@HiltViewModel
class LoansViewModel @Inject constructor(private val repo: LoanRepository) : BaseViewModel() {
    val products: List<LoanProduct> = repo.products()

    fun apply(product: LoanProduct, amount: Double, months: Int) {
        viewModelScope.launch {
            when (val r = repo.apply(product.id, amount, months)) {
                is Resource.Success -> message("Application ${r.data} submitted. We'll notify you of the decision.")
                is Resource.Error -> message(r.message)
            }
        }
    }
}

@AndroidEntryPoint
class LoansFragment : BaseFragment<FragmentLoansBinding>(FragmentLoansBinding::inflate) {
    private val viewModel: LoansViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Loans & Credit")
        val adapter = SimpleAdapter<LoanProduct, ItemRowActionBinding>(ItemRowActionBinding::inflate, { it.id }) { p ->
            bindRow(
                title = p.name, subtitle = p.description, amount = "from ${p.annualRatePercent}%",
                icon = when (p.id) { "l2" -> R.drawable.ci_car; "l3" -> R.drawable.ci_house; else -> R.drawable.ci_coins },
                action = "Learn More", onAction = { showCalculator(p) }, onClick = { showCalculator(p) }
            )
        }
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter
        adapter.submitList(viewModel.products)
        collectEvents(viewModel.events) { }
    }

    private fun showCalculator(p: LoanProduct) {
        val d = DialogLoanBinding.inflate(layoutInflater)
        d.tvDesc.text = "${p.description}\nRate ${p.annualRatePercent}% APR · up to ${Formatters.money(p.maxAmount)} · up to ${p.maxMonths} months"
        fun recalc() {
            val amount = Validators.parseAmount(d.etAmount.value())
            val months = d.etMonths.value().toIntOrNull()
            if (amount != null && amount > 0 && months != null && months > 0) {
                val monthly = LoanCalculator.monthlyPayment(amount, p.annualRatePercent, months)
                d.tvResult.text = "Estimated payment: ${Formatters.money(monthly)} / month"
                d.tvResultSub.text = "Total repayment ${Formatters.money(monthly * months)}"
            } else {
                d.tvResult.text = "Enter an amount and term to see your payment"
                d.tvResultSub.text = ""
            }
        }
        d.etAmount.addTextChangedListener(simpleWatcher { recalc() })
        d.etMonths.addTextChangedListener(simpleWatcher { recalc() })
        recalc()
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(p.name).setView(d.root)
            .setNegativeButton("Close", null).setPositiveButton("Apply now", null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val amount = Validators.parseAmount(d.etAmount.value())
                val months = d.etMonths.value().toIntOrNull()
                d.tilAmount.error = null; d.tilMonths.error = null
                when {
                    amount == null || amount <= 0 || amount > p.maxAmount -> d.tilAmount.error = "Enter up to ${Formatters.money(p.maxAmount)}"
                    months == null || months !in 6..p.maxMonths -> d.tilMonths.error = "Enter 6 to ${p.maxMonths} months"
                    else -> { viewModel.apply(p, amount, months); dialog.dismiss() }
                }
            }
        }
        dialog.show()
    }

    private fun simpleWatcher(onChange: () -> Unit) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = onChange()
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }
}
