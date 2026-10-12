package com.example.financeapplication.ui.payments

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.core.Formatters
import com.example.financeapplication.core.Resource
import com.example.financeapplication.core.Validators
import com.example.financeapplication.databinding.FragmentCheckDepositBinding
import com.example.financeapplication.domain.model.Account
import com.example.financeapplication.domain.repository.BankRepository
import com.example.financeapplication.domain.usecase.DepositCheckUseCase
import com.example.financeapplication.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CheckDepositViewModel @Inject constructor(
    savedState: SavedStateHandle,
    bank: BankRepository,
    private val deposit: DepositCheckUseCase
) : BaseViewModel() {
    val initialAccountId: String? = savedState["toAccountId"]
    val accounts: StateFlow<List<Account>> = bank.accounts.map { l -> l.filter { !it.isCredit } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _front = MutableStateFlow<Bitmap?>(null)
    private val _back = MutableStateFlow<Bitmap?>(null)
    private val _loading = MutableStateFlow(false)
    val front: StateFlow<Bitmap?> = _front.asStateFlow()
    val back: StateFlow<Bitmap?> = _back.asStateFlow()
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun setFront(b: Bitmap) { _front.value = b }
    fun setBack(b: Bitmap) { _back.value = b }

    fun submit(toId: String?, amount: Double) {
        viewModelScope.launch {
            _loading.value = true
            when (val r = deposit(toId, amount, _front.value != null, _back.value != null)) {
                is Resource.Success -> done("${Formatters.money(amount)} deposited successfully")
                is Resource.Error -> message(r.message)
            }
            _loading.value = false
        }
    }
}

@AndroidEntryPoint
class CheckDepositFragment : BaseFragment<FragmentCheckDepositBinding>(FragmentCheckDepositBinding::inflate) {
    private val viewModel: CheckDepositViewModel by viewModels()
    private var accounts: List<Account> = emptyList()

    private val captureFront = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) viewModel.setFront(bmp)
    }
    private val captureBack = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) viewModel.setBack(bmp)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            toolbar.setup(this@CheckDepositFragment, "Mobile Check Deposit")
            tilAmount.clearErrorOnEdit()
            tvLimit.text = "Deposit limit: ${Formatters.money(5000.0)} per check. Funds are available immediately in this demo."
            boxFront.setOnClickListener { launchCamera(true) }
            boxBack.setOnClickListener { launchCamera(false) }
            btnContinue.setOnClickListener { onContinue() }
        }
        collect(viewModel.accounts) { list ->
            accounts = list
            val first = binding.acTo.text.isNullOrEmpty()
            val idx = list.indexOfFirst { it.id == viewModel.initialAccountId }.coerceAtLeast(0)
            binding.acTo.setItems(list.map { "${it.name} ${it.masked}" }, if (first) idx else null)
        }
        collect(viewModel.front) { showPreview(it, binding.ivFront, binding.phFront) }
        collect(viewModel.back) { showPreview(it, binding.ivBack, binding.phBack) }
        collect(viewModel.loading) {
            binding.btnContinue.isEnabled = !it
            binding.btnContinue.text = if (it) "Depositing…" else "Continue"
        }
        collectEvents(viewModel.events) { msg -> info("Deposit", msg ?: "Done") { back() } }
    }

    private fun showPreview(bmp: Bitmap?, image: android.widget.ImageView, placeholder: View) {
        image.setImageBitmap(bmp)
        image.isVisible = bmp != null
        placeholder.isVisible = bmp == null
    }

    private fun launchCamera(front: Boolean) {
        try {
            if (front) captureFront.launch(null) else captureBack.launch(null)
        } catch (e: ActivityNotFoundException) {
            snack("No camera app is available on this device")
        }
    }

    private fun onContinue() {
        hideKeyboard()
        val acc = accounts.getOrNull(binding.acTo.selectedIndex(binding.acTo.adapterItems()))
        val amount = Validators.parseAmount(binding.etAmount.value())
        if (amount == null || amount <= 0) { binding.tilAmount.error = "Enter the check amount"; return }
        viewModel.submit(acc?.id, amount)
    }
}
