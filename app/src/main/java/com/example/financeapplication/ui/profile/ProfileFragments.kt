package com.example.financeapplication.ui.profile

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financeapplication.R
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.core.Resource
import com.example.financeapplication.core.Validators
import com.example.financeapplication.databinding.FragmentProfileBinding
import com.example.financeapplication.databinding.FragmentSecurityBinding
import com.example.financeapplication.databinding.ItemMenuRowBinding
import com.example.financeapplication.domain.model.AppSettings
import com.example.financeapplication.domain.model.User
import com.example.financeapplication.domain.repository.AuthRepository
import com.example.financeapplication.domain.repository.SettingsRepository
import com.example.financeapplication.ui.common.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(private val auth: AuthRepository) : BaseViewModel() {
    val user: StateFlow<User?> = auth.currentUser

    fun update(name: String, phone: String, address: String) {
        viewModelScope.launch {
            when (val r = auth.updateProfile(name, phone, address)) {
                is Resource.Success -> message("Profile updated")
                is Resource.Error -> message(r.message)
            }
        }
    }

    fun logout() { auth.logout(); done() }

    fun deleteAccount(password: String) {
        viewModelScope.launch {
            when (val r = auth.deleteAccount(password)) {
                is Resource.Success -> done("Your account was deleted")
                is Resource.Error -> message(r.message)
            }
        }
    }
}

@AndroidEntryPoint
class ProfileFragment : BaseFragment<FragmentProfileBinding>(FragmentProfileBinding::inflate) {
    private val viewModel: ProfileViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Profile")
        val entries = listOf(
            Triple(1, "Personal Information", R.drawable.ci_person),
            Triple(2, "Addresses", R.drawable.ci_location),
            Triple(3, "Security", R.drawable.ci_shield),
            Triple(4, "Preferences", R.drawable.ci_settings),
            Triple(5, "Help & Support", R.drawable.ci_help)
        )
        val adapter = SimpleAdapter<Triple<Int, String, Int>, ItemMenuRowBinding>(ItemMenuRowBinding::inflate, { it.first }) { e ->
            ivIcon.setImageResource(e.third)
            tvTitle.text = e.second
            tvSubtitle.visibility = View.GONE
            root.setOnClickListener {
                when (e.first) {
                    1 -> editPersonal()
                    2 -> editAddress()
                    3 -> go(R.id.securityFragment)
                    4 -> go(R.id.settingsFragment)
                    else -> go(R.id.helpFragment)
                }
            }
        }
        binding.rvMenu.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMenu.adapter = adapter
        adapter.submitList(entries)
        binding.btnLogout.setOnClickListener {
            confirm("Log out", "Do you want to sign out of CitiBank?", "Log out") { viewModel.logout() }
        }
        binding.btnDelete.setOnClickListener {
            confirm("Delete account", "This permanently deletes your CitiBank profile, accounts and history. This cannot be undone.", "Continue") {
                formDialog("Confirm your password", listOf(Triple("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, "")), "Delete forever") { v ->
                    if (v[0].isEmpty()) "Enter your password" else { viewModel.deleteAccount(v[0]); null }
                }
            }
        }
        collect(viewModel.user) { u ->
            binding.tvAvatar.text = u?.initials.orEmpty()
            binding.tvName.text = u?.fullName.orEmpty()
            binding.tvEmail.text = u?.email.orEmpty()
        }
        collectEvents(viewModel.events) { msg ->
            if (msg != null) snack(msg)
            go(R.id.action_global_login)
        }
    }

    private fun editPersonal() {
        val u = viewModel.user.value ?: return
        formDialog(
            "Personal information",
            listOf(
                Triple("Full name", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, u.fullName),
                Triple("Mobile number", InputType.TYPE_CLASS_PHONE, u.phone)
            ), "Save"
        ) { v ->
            when {
                !Validators.isValidName(v[0]) -> "Enter your full name"
                !Validators.isValidPhone(v[1]) -> "Enter a valid mobile number"
                else -> { viewModel.update(v[0], v[1], u.address); null }
            }
        }
    }

    private fun editAddress() {
        val u = viewModel.user.value ?: return
        formDialog("Address", listOf(Triple("Home address", InputType.TYPE_CLASS_TEXT, u.address)), "Save") { v ->
            if (v[0].length < 5) "Enter a valid address" else { viewModel.update(u.fullName, u.phone, v[0]); null }
        }
    }
}

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val auth: AuthRepository
) : BaseViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsRepo.current)

    fun setTwoFactor(on: Boolean) { viewModelScope.launch { settingsRepo.update { it.copy(twoFactorEnabled = on) } } }
    fun setBiometric(on: Boolean) { viewModelScope.launch { settingsRepo.update { it.copy(biometricEnabled = on) } } }
    fun devices() = settingsRepo.devices()
    fun loginActivity() = settingsRepo.loginActivity()

    fun changePassword(old: String, new: String) {
        viewModelScope.launch {
            when (val r = auth.changePassword(old, new)) {
                is Resource.Success -> message("Password changed successfully")
                is Resource.Error -> message(r.message)
            }
        }
    }
}

@AndroidEntryPoint
class SecurityFragment : BaseFragment<FragmentSecurityBinding>(FragmentSecurityBinding::inflate) {
    private val viewModel: SecurityViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Security Settings")
        binding.rowPassword.setOnClickListener { changePassword() }
        binding.rowDevices.setOnClickListener {
            val items = viewModel.devices().map { "${it.name} — ${it.lastActive}${if (it.isCurrent) " (this device)" else ""}" }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext()).setTitle("Device management").setItems(items, null)
                .setPositiveButton("Close", null).show()
        }
        binding.rowActivity.setOnClickListener {
            val items = viewModel.loginActivity().map { "${if (it.success) "✓" else "✗"} ${it.device} · ${it.location} · ${it.time}" }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext()).setTitle("Login activity").setItems(items, null)
                .setPositiveButton("Close", null).show()
        }
        collect(viewModel.settings) { s ->
            binding.swTwoFactor.bind(s.twoFactorEnabled) { viewModel.setTwoFactor(it) }
            binding.swBiometric.bind(s.biometricEnabled) { on -> onBiometricToggle(on) }
        }
        collectEvents(viewModel.events)
    }

    private fun onBiometricToggle(on: Boolean) {
        if (!on) { viewModel.setBiometric(false); return }
        if (!BiometricHelper.isAvailable(requireContext())) {
            snack("Set up a fingerprint or face unlock in your device settings first")
            binding.swBiometric.bind(false) { onBiometricToggle(it) }
            return
        }
        BiometricHelper.authenticate(requireActivity(), "Enable biometric login", "Confirm to turn this on",
            onSuccess = { viewModel.setBiometric(true) },
            onError = { snack(it); binding.swBiometric.bind(false) { v -> onBiometricToggle(v) } })
        // If the prompt is cancelled the switch is reset on the next settings emission.
        binding.swBiometric.bind(false) { onBiometricToggle(it) }
    }

    private fun changePassword() {
        val pw = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        formDialog(
            "Change password",
            listOf(Triple("Current password", pw, ""), Triple("New password", pw, ""), Triple("Confirm new password", pw, "")),
            "Update"
        ) { v ->
            val err = Validators.passwordError(v[1])
            when {
                v[0].isEmpty() -> "Enter your current password"
                err != null -> err
                v[1] != v[2] -> "Passwords do not match"
                else -> { viewModel.changePassword(v[0], v[1]); null }
            }
        }
    }
}
