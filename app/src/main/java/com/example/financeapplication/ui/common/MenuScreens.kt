package com.example.financeapplication.ui.common

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financeapplication.R
import com.example.financeapplication.databinding.FragmentMenuBinding
import com.example.financeapplication.databinding.ItemMenuRowBinding
import dagger.hilt.android.AndroidEntryPoint

data class MenuEntry(val id: Int, val title: String, val subtitle: String, val icon: Int, val destination: Int)

abstract class MenuFragment : BaseFragment<FragmentMenuBinding>(FragmentMenuBinding::inflate) {
    protected abstract val screenTitle: String
    protected abstract val entries: List<MenuEntry>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, screenTitle, showBack = false)
        val adapter = SimpleAdapter<MenuEntry, ItemMenuRowBinding>(ItemMenuRowBinding::inflate, { it.id }) { e ->
            ivIcon.setImageResource(e.icon)
            tvTitle.text = e.title
            tvSubtitle.text = e.subtitle
            root.setOnClickListener { go(e.destination) }
        }
        binding.rvMenu.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMenu.adapter = adapter
        adapter.submitList(entries)
    }
}

@AndroidEntryPoint
class PaymentsHubFragment : MenuFragment() {
    override val screenTitle = "Payments"
    override val entries = listOf(
        MenuEntry(1, "Make a Payment", "Pay someone from your account", R.drawable.ci_send, R.id.makePaymentFragment),
        MenuEntry(2, "Bill Pay", "Pay or schedule your bills", R.drawable.ci_bolt, R.id.billPayFragment),
        MenuEntry(3, "Transfer Money", "Between accounts or to a recipient", R.drawable.ci_payment, R.id.transferFragment),
        MenuEntry(4, "Recipients / Payees", "Manage who you pay", R.drawable.ci_person, R.id.recipientsFragment),
        MenuEntry(5, "Mobile Check Deposit", "Deposit a check with your camera", R.drawable.ci_camera, R.id.checkDepositFragment),
        MenuEntry(6, "Transaction History", "Search and filter activity", R.drawable.ci_statement, R.id.transactionsFragment)
    )
}

@AndroidEntryPoint
class MoreFragment : MenuFragment() {
    override val screenTitle = "More"
    override val entries = listOf(
        MenuEntry(1, "Credit Card", "Balance, activity and payments", R.drawable.ci_card, R.id.cardFragment),
        MenuEntry(2, "Card Controls", "Lock your card and set limits", R.drawable.ci_lock, R.id.cardControlsFragment),
        MenuEntry(3, "Statements", "Download monthly statements", R.drawable.ci_download, R.id.statementsFragment),
        MenuEntry(4, "Rewards", "Points, redeem and offers", R.drawable.ci_star, R.id.rewardsFragment),
        MenuEntry(5, "Loans & Credit", "Compare products and apply", R.drawable.ci_coins, R.id.loansFragment),
        MenuEntry(6, "Offers", "Exclusive offers for you", R.drawable.ci_gift, R.id.offersFragment),
        MenuEntry(7, "ATM / Branch Locator", "Find us nearby", R.drawable.ci_location, R.id.locatorFragment),
        MenuEntry(8, "Notifications", "Alerts and updates", R.drawable.ci_bell, R.id.notificationsFragment),
        MenuEntry(9, "Help & Support", "FAQs and live chat", R.drawable.ci_help, R.id.helpFragment),
        MenuEntry(10, "Profile", "Personal info and security", R.drawable.ci_person, R.id.profileFragment),
        MenuEntry(11, "Settings", "Appearance and preferences", R.drawable.ci_settings, R.id.settingsFragment)
    )
}
