package com.example.financeapplication.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.findNavController
import com.example.financeapplication.R
import com.example.financeapplication.databinding.FragmentSplashBinding
import com.example.financeapplication.domain.repository.AuthRepository
import com.example.financeapplication.domain.repository.SettingsRepository
import com.example.financeapplication.ui.common.BiometricHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.financeapplication.ui.common.BaseFragment
import com.example.financeapplication.ui.common.collect
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

enum class SplashRoute { ONBOARDING, LOGIN, HOME }

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settings: SettingsRepository
) : ViewModel() {
    /** Biometric app-lock: asked again every time a signed-in user opens the app. */
    val biometricLock: Boolean get() = settings.current.biometricEnabled
    fun signOut() = auth.logout()

    val route: Flow<SplashRoute> = flow {
        delay(1200)
        emit(
            when {
                !auth.onboardingSeen -> SplashRoute.ONBOARDING
                runCatching { auth.refreshSession() }.getOrDefault(auth.isLoggedIn) -> SplashRoute.HOME
                else -> SplashRoute.LOGIN
            }
        )
    }
}

@AndroidEntryPoint
class SplashFragment : BaseFragment<FragmentSplashBinding>(FragmentSplashBinding::inflate) {
    private val viewModel: SplashViewModel by viewModels()
    private var routed = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.logoGroup.animate().alpha(1f).setDuration(700).start()
        collect(viewModel.route) { route ->
            if (routed) return@collect
            routed = true
            when (route) {
                SplashRoute.ONBOARDING -> findNavController().navigate(R.id.action_splash_to_onboarding)
                SplashRoute.LOGIN -> findNavController().navigate(R.id.action_splash_to_login)
                SplashRoute.HOME -> unlockThenHome()
            }
        }
    }

    private fun unlockThenHome() {
        if (!viewModel.biometricLock || !BiometricHelper.isAvailable(requireContext())) {
            findNavController().navigate(R.id.action_splash_to_home)
            return
        }
        BiometricHelper.authenticate(
            requireActivity(), "Unlock CitiBank", "Confirm it's you to continue",
            onSuccess = { findNavController().navigate(R.id.action_splash_to_home) },
            onError = { retryOrSignOut() },
            onCancelled = { retryOrSignOut() }
        )
    }

    private fun retryOrSignOut() {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("CitiBank is locked")
            .setMessage("Unlock with your fingerprint or face to continue, or sign out.")
            .setCancelable(false)
            .setPositiveButton("Try again") { _, _ -> unlockThenHome() }
            .setNegativeButton("Sign out") { _, _ ->
                viewModel.signOut()
                findNavController().navigate(R.id.action_splash_to_login)
            }.show()
    }
}
