package com.example.financeapplication.ui.auth

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.example.financeapplication.R
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.core.Resource
import com.example.financeapplication.databinding.FragmentOtpBinding
import com.example.financeapplication.domain.repository.AuthRepository
import com.example.financeapplication.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val RESEND_SECONDS = 60
private const val POLL_MS = 3_000L

data class VerifyUiState(
    val busy: Boolean = true,          // creating the account / checking verification
    val ready: Boolean = false,        // account created and e-mail sent
    val error: String? = null,
    val secondsLeft: Int = 0
)

/**
 * Third step of sign-up: creates the Firebase account (after Terms/Privacy), sends the verification
 * e-mail and waits until the user confirms it.
 */
@HiltViewModel
class EmailVerifyViewModel @Inject constructor(private val auth: AuthRepository) : BaseViewModel() {
    val email: String = auth.pendingEmail
    private val _state = MutableStateFlow(VerifyUiState())
    val state: StateFlow<VerifyUiState> = _state.asStateFlow()
    private var timer: Job? = null
    private var poller: Job? = null
    private var finished = false

    init { createAccount() }

    private fun createAccount() {
        viewModelScope.launch {
            _state.value = VerifyUiState(busy = true)
            when (val r = auth.createPendingAccount()) {
                is Resource.Success -> { _state.value = VerifyUiState(busy = false, ready = true); startTimer(); startPolling() }
                is Resource.Error -> _state.value = VerifyUiState(busy = false, error = r.message)
            }
        }
    }

    fun retry() = createAccount()

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch {
            for (s in RESEND_SECONDS downTo 0) {
                _state.update { it.copy(secondsLeft = s) }
                delay(1000)
            }
        }
    }

    /** Checks every few seconds so the screen moves on by itself right after the link is opened. */
    private fun startPolling() {
        poller?.cancel()
        poller = viewModelScope.launch {
            while (!finished) {
                delay(POLL_MS)
                if (!_state.value.busy) silentCheck()
            }
        }
    }

    private suspend fun silentCheck() {
        val r = auth.checkEmailVerified(force = false)
        if (r is Resource.Success && r.data) finish()
    }

    private fun finish() {
        if (finished) return
        finished = true
        poller?.cancel()
        done()
    }

    /** Called when the app returns to the foreground (user comes back from the mail app). */
    fun pollNow() {
        if (_state.value.ready && !finished) viewModelScope.launch { silentCheck() }
    }

    fun check() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (val r = auth.checkEmailVerified(force = true)) {
                is Resource.Success -> if (r.data) finish() else message("Your email isn't verified yet. Open the link we sent, then try again.")
                is Resource.Error -> message(r.message)
            }
            _state.update { it.copy(busy = false) }
        }
    }

    fun resend() {
        if (_state.value.secondsLeft > 0) return
        viewModelScope.launch {
            when (val r = auth.resendVerification()) {
                is Resource.Success -> { message("Verification email sent to $email"); startTimer() }
                is Resource.Error -> message(r.message)
            }
        }
    }
}

@AndroidEntryPoint
class OtpFragment : BaseFragment<FragmentOtpBinding>(FragmentOtpBinding::inflate) {
    private val viewModel: EmailVerifyViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            toolbar.setup(this@OtpFragment, "Verify")
            btnVerify.setOnClickListener { viewModel.check() }
            tvResend.setOnClickListener { viewModel.resend() }
            btnTrouble.setOnClickListener {
                findNavController().popBackStack(R.id.signUpFragment, false)
            }
        }
        collect(viewModel.state) { s ->
            with(binding) {
                progress.isVisible = s.busy
                tvSubtitle.text = when {
                    s.error != null -> s.error
                    s.ready -> "We've sent a verification link to ${viewModel.email}.\nOpen it and come back – this screen continues automatically."
                    else -> "Creating your account…"
                }
                btnVerify.isEnabled = s.ready && !s.busy
                btnVerify.text = if (s.error != null) "Try again" else "I've verified my email"
                if (s.error != null) {
                    btnVerify.isEnabled = !s.busy
                    btnVerify.setOnClickListener { viewModel.retry() }
                } else {
                    btnVerify.setOnClickListener { viewModel.check() }
                }
                tvResend.isVisible = s.ready
                tvResend.text = if (s.secondsLeft > 0) "Resend email in 00:%02d".format(s.secondsLeft) else "Resend email"
                tvResend.setTextColor(ContextCompat.getColor(requireContext(), if (s.secondsLeft > 0) R.color.text_secondary else R.color.primary))
                tvResend.isEnabled = s.secondsLeft == 0
            }
        }
        // Returning from the mail app triggers an immediate check instead of waiting for the next poll.
        viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.pollNow()
        })
        collectEvents(viewModel.events) { go(R.id.action_otp_to_home) }
    }
}
