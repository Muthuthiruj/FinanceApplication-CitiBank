package com.example.financeapplication.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.core.Resource
import com.example.financeapplication.core.Validators
import com.example.financeapplication.databinding.FragmentForgotPasswordBinding
import com.example.financeapplication.domain.repository.AuthRepository
import com.example.financeapplication.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForgotUiState(val loading: Boolean = false, val emailError: String? = null)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(private val auth: AuthRepository) : BaseViewModel() {
    private val _state = MutableStateFlow(ForgotUiState())
    val state: StateFlow<ForgotUiState> = _state.asStateFlow()

    fun send(email: String) {
        if (!Validators.isValidEmail(email)) {
            _state.value = ForgotUiState(emailError = "Enter a valid email address")
            return
        }
        viewModelScope.launch {
            _state.value = ForgotUiState(loading = true)
            when (val r = auth.sendResetLink(email)) {
                is Resource.Success -> done(email)
                is Resource.Error -> message(r.message)
            }
            _state.value = ForgotUiState()
        }
    }
}

@AndroidEntryPoint
class ForgotPasswordFragment : BaseFragment<FragmentForgotPasswordBinding>(FragmentForgotPasswordBinding::inflate) {
    private val viewModel: ForgotPasswordViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            toolbar.setup(this@ForgotPasswordFragment, "Forgot Password")
            tilEmail.clearErrorOnEdit()
            btnSend.setOnClickListener { hideKeyboard(); viewModel.send(etEmail.value()) }
            btnBackToLogin.setOnClickListener { back() }
        }
        collect(viewModel.state) { s ->
            binding.tilEmail.error = s.emailError
            binding.btnSend.isEnabled = !s.loading
            binding.btnSend.text = if (s.loading) "Sending…" else "Send Reset Link"
        }
        collectEvents(viewModel.events) { email ->
            info("Check your inbox", "If an account exists for $email, you will receive a password reset link shortly.") { back() }
        }
    }
}
