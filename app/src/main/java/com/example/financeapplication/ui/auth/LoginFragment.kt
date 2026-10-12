package com.example.financeapplication.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import com.example.financeapplication.R
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.core.DemoAccount
import com.example.financeapplication.core.Resource
import com.example.financeapplication.core.Validators
import com.example.financeapplication.databinding.FragmentLoginBinding
import com.example.financeapplication.domain.repository.AuthRepository
import com.example.financeapplication.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val loading: Boolean = false,
    val identifierError: String? = null,
    val passwordError: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(private val auth: AuthRepository) : BaseViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun login(identifier: String, password: String) {
        val idError = if (Validators.isValidEmail(identifier)) null else "Enter a valid email address"
        val pwError = if (password.isEmpty()) "Enter your password" else null
        _state.update { it.copy(identifierError = idError, passwordError = pwError) }
        if (idError != null || pwError != null) return
        execute { auth.login(identifier, password) }
    }

    private fun <T> execute(block: suspend () -> Resource<T>) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val r = block()) {
                is Resource.Success -> done()
                is Resource.Error -> message(r.message)
            }
            _state.update { it.copy(loading = false) }
        }
    }

    fun googleNotConfigured() = message("Google sign-in is not configured in this demo build. Use email or mobile number.")
}

@AndroidEntryPoint
class LoginFragment : BaseFragment<FragmentLoginBinding>(FragmentLoginBinding::inflate) {
    private val viewModel: LoginViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            tilIdentifier.clearErrorOnEdit()
            tilPassword.clearErrorOnEdit()
            btnSignIn.setOnClickListener {
                hideKeyboard()
                viewModel.login(etIdentifier.value(), etPassword.text?.toString().orEmpty())
            }
            etPassword.setOnEditorActionListener { _, _, _ -> btnSignIn.performClick(); true }
            btnForgot.setOnClickListener { go(R.id.forgotPasswordFragment) }
            btnSignUp.setOnClickListener { go(R.id.signUpFragment) }
            btnGoogle.setOnClickListener { viewModel.googleNotConfigured() }
            btnDemo.setOnClickListener {
                etIdentifier.setText(DemoAccount.EMAIL)
                etPassword.setText(DemoAccount.PASSWORD)
                hideKeyboard()
                viewModel.login(DemoAccount.EMAIL, DemoAccount.PASSWORD)
            }
        }
        collect(viewModel.state) { s ->
            with(binding) {
                tilIdentifier.error = s.identifierError
                tilPassword.error = s.passwordError
                btnSignIn.isEnabled = !s.loading
                btnDemo.isEnabled = !s.loading
                btnSignIn.text = if (s.loading) "Signing in…" else "Sign In"
            }
        }
        collectEvents(viewModel.events) { go(R.id.action_login_to_home) }
    }
}
