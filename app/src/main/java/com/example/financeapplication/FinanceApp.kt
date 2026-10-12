package com.example.financeapplication

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.financeapplication.domain.model.ThemeMode
import com.example.financeapplication.domain.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FinanceApp : Application() {

    @Inject lateinit var settings: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(settings.current.themeMode.toNightMode())
    }
}

fun ThemeMode.toNightMode(): Int = when (this) {
    ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
    ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
    ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
}
