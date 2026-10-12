package com.example.financeapplication.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Thin wrapper over SharedPreferences (session, onboarding flag, settings, local users). */
@Singleton
class AppPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val sp = context.getSharedPreferences("citibank_prefs", Context.MODE_PRIVATE)

    var onboardingSeen: Boolean
        get() = sp.getBoolean("onboarding_seen", false)
        set(v) = sp.edit { putBoolean("onboarding_seen", v) }

    var sessionEmail: String?
        get() = sp.getString("session_email", null)
        set(v) = sp.edit { if (v == null) remove("session_email") else putString("session_email", v) }

    var lastEmail: String?
        get() = sp.getString("last_email", null)
        set(v) = sp.edit { if (v == null) remove("last_email") else putString("last_email", v) }

    var usersJson: String?
        get() = sp.getString("users_json", null)
        set(v) = sp.edit { if (v == null) remove("users_json") else putString("users_json", v) }

    var pushEnabled: Boolean
        get() = sp.getBoolean("push", true)
        set(v) = sp.edit { putBoolean("push", v) }

    var locationEnabled: Boolean
        get() = sp.getBoolean("location", false)
        set(v) = sp.edit { putBoolean("location", v) }

    var biometricEnabled: Boolean
        get() = sp.getBoolean("biometric", false)
        set(v) = sp.edit { putBoolean("biometric", v) }

    var twoFactorEnabled: Boolean
        get() = sp.getBoolean("two_factor", true)
        set(v) = sp.edit { putBoolean("two_factor", v) }

    var themeMode: String
        get() = sp.getString("theme", "SYSTEM") ?: "SYSTEM"
        set(v) = sp.edit { putString("theme", v) }
}

/** Persisted account credentials – DEMO ONLY (no server). Passwords are salted + hashed. */
data class StoredUser(
    val id: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val address: String,
    val salt: String,
    val passwordHash: String
)
