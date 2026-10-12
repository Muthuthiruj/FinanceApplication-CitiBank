package com.example.financeapplication.ui.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import com.example.financeapplication.R
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.databinding.FragmentSettingsBinding
import com.example.financeapplication.domain.model.AppSettings
import com.example.financeapplication.domain.model.ThemeMode
import com.example.financeapplication.domain.repository.SettingsRepository
import com.example.financeapplication.toNightMode
import com.example.financeapplication.ui.common.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository) : BaseViewModel() {
    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, repo.current)
    fun update(transform: (AppSettings) -> AppSettings) { viewModelScope.launch { repo.update(transform) } }
}

@AndroidEntryPoint
class SettingsFragment : BaseFragment<FragmentSettingsBinding>(FragmentSettingsBinding::inflate) {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Settings")
        val version = runCatching {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        }.getOrNull() ?: "1.0"
        binding.tvVersion.text = "v$version"
        binding.tvLanguage.text = "English"

        binding.rowLanguage.setOnClickListener { snack("English is the only language available right now") }
        binding.rowAppearance.setOnClickListener { chooseAppearance() }
        binding.rowPrivacy.setOnClickListener { go(R.id.privacyFragment, bundleOf("readOnly" to true)) }
        binding.rowAbout.setOnClickListener {
            info("About CitiBank", "Version $version\nA demo banking app built with Clean Architecture, MVVM and Hilt. No real money or accounts are involved.")
        }
        collect(viewModel.settings) { s ->
            binding.swNotifications.bind(s.pushEnabled) { v -> viewModel.update { it.copy(pushEnabled = v) } }
            binding.swLocation.bind(s.locationEnabled) { v -> viewModel.update { it.copy(locationEnabled = v) } }
            binding.tvAppearance.text = s.themeMode.label()
        }
    }

    private fun chooseAppearance() {
        val modes = ThemeMode.values()
        val current = viewModel.settings.value.themeMode
        MaterialAlertDialogBuilder(requireContext()).setTitle("Appearance")
            .setSingleChoiceItems(modes.map { it.label() }.toTypedArray(), modes.indexOf(current)) { dialog, which ->
                val mode = modes[which]
                viewModel.update { it.copy(themeMode = mode) }
                AppCompatDelegate.setDefaultNightMode(mode.toNightMode())
                dialog.dismiss()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun ThemeMode.label() = when (this) {
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
        ThemeMode.SYSTEM -> "System default"
    }
}
