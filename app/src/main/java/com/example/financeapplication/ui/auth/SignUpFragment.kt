package com.example.financeapplication.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import com.example.financeapplication.R
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.core.Resource
import com.example.financeapplication.core.Validators
import com.example.financeapplication.databinding.FragmentSignUpBinding
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

data class SignUpUiState(
    val loading: Boolean = false,
    val nameError: String? = null,
    val emailError: String? = null,
    val phoneError: String? = null,
    val passwordError: String? = null
)

@HiltViewModel
class SignUpViewModel @Inject constructor(private val auth: AuthRepository) : BaseViewModel() {
    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    fun submit(name: String, email: String, phone: String, password: String) {
        val s = SignUpUiState(
            nameError = if (Validators.isValidName(name)) null else "Enter your full name",
            emailError = if (Validators.isValidEmail(email)) null else "Enter a valid email address",
            phoneError = if (Validators.isValidPhone(phone)) null else "Enter a valid mobile number",
            passwordError = Validators.passwordError(password)
        )
        _state.value = s
        if (listOf(s.nameError, s.emailError, s.phoneError, s.passwordError).any { it != null }) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val r = auth.startSignUp(name, email, phone, password)) {
                is Resource.Success -> done()
                is Resource.Error -> message(r.message)
            }
            _state.update { it.copy(loading = false) }
        }
    }

    fun googleNotConfigured() = message("Google sign-in is not configured in this demo build.")
}

@AndroidEntryPoint
class SignUpFragment : BaseFragment<FragmentSignUpBinding>(FragmentSignUpBinding::inflate) {
    private val viewModel: SignUpViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            toolbar.setup(this@SignUpFragment, "Sign Up")
            listOf(tilName, tilEmail, tilPhone, tilPassword).forEach { it.clearErrorOnEdit() }
            btnCreate.setOnClickListener {
                hideKeyboard()
                viewModel.submit(etName.value(), etEmail.value(), etPhone.value(), etPassword.text?.toString().orEmpty())
            }
            etPassword.setOnEditorActionListener { _, _, _ -> btnCreate.performClick(); true }
            btnGoogle.setOnClickListener { viewModel.googleNotConfigured() }
            btnSignIn.setOnClickListener { back() }
        }
        collect(viewModel.state) { s ->
            with(binding) {
                tilName.error = s.nameError
                tilEmail.error = s.emailError
                tilPhone.error = s.phoneError
                tilPassword.error = s.passwordError
                btnCreate.isEnabled = !s.loading
                btnCreate.text = if (s.loading) "Creating account…" else "Create Account"
            }
        }
        collectEvents(viewModel.events) { go(R.id.action_signUp_to_terms) }
    }
}
