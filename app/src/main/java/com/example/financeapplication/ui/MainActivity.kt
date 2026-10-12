package com.example.financeapplication.ui

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.navigation.navOptions
import com.example.financeapplication.databinding.DialogExitBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import com.example.financeapplication.R
import com.example.financeapplication.databinding.ActivityMainBinding
import com.example.financeapplication.ui.common.isNightMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var navBarInset = 0
    private var imeInset = 0
    private var bottomNavVisible = false

    private val tabDestinations = setOf(R.id.homeFragment, R.id.accountsFragment, R.id.paymentsHubFragment, R.id.moreFragment)
    private val navyTopDestinations = setOf(R.id.splashFragment, R.id.loginFragment, R.id.homeFragment)
    private val navyBottomDestinations = setOf(R.id.splashFragment, R.id.loginFragment)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            navBarInset = bars.bottom
            imeInset = ime.bottom
            applyBottomInset()
            insets
        }

        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        val navController = navHost.navController

        // Tabs keep a flat back stack: [home, tab]. Switching tabs never piles up duplicates.
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.homeFragment) {
                if (!navController.popBackStack(R.id.homeFragment, false)) navController.navigate(R.id.homeFragment)
            } else if (navController.currentDestination?.id != item.itemId) {
                navController.navigate(item.itemId, null, navOptions {
                    launchSingleTop = true
                    popUpTo(R.id.homeFragment) { inclusive = false }
                })
            }
            true
        }
        binding.bottomNav.setOnItemReselectedListener { }

        // Back: any tab -> Home, Home -> custom exit dialog, everything else -> normal "up".
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (navController.currentDestination?.id) {
                    R.id.accountsFragment, R.id.paymentsHubFragment, R.id.moreFragment ->
                        if (!navController.popBackStack(R.id.homeFragment, false)) navController.navigate(R.id.homeFragment)
                    R.id.homeFragment -> showExitDialog()
                    else -> if (!navController.popBackStack()) finish()
                }
            }
        })

        val controller = WindowCompat.getInsetsController(window, binding.root)
        val night = isNightMode()

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showNav = destination.id in tabDestinations
            binding.bottomNav.menu.findItem(destination.id)?.isChecked = true
            binding.bottomNav.isVisible = showNav
            bottomNavVisible = showNav
            applyBottomInset()

            val navyTop = destination.id in navyTopDestinations
            val navyBottom = destination.id in navyBottomDestinations
            binding.root.setBackgroundColor(getColor(if (navyTop) R.color.navy else R.color.background))
            controller.isAppearanceLightStatusBars = !navyTop && !night
            controller.isAppearanceLightNavigationBars = !navyBottom && !night
        }
    }

    private fun showExitDialog() {
        if (exitDialog?.isShowing == true) return
        val d = DialogExitBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this).setView(d.root).create()
        d.btnStay.setOnClickListener { dialog.dismiss() }
        d.btnExit.setOnClickListener { dialog.dismiss(); finish() }
        dialog.show()
        exitDialog = dialog
    }

    private var exitDialog: androidx.appcompat.app.AlertDialog? = null

    private fun applyBottomInset() {
        // The bottom nav view pads itself for the nav bar; otherwise the content container does.
        binding.navHost.updatePadding(bottom = if (bottomNavVisible) 0 else maxOf(navBarInset, imeInset))
    }
}
