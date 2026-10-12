package com.example.financeapplication.ui.auth

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import com.example.financeapplication.R
import com.example.financeapplication.databinding.FragmentLegalBinding
import com.example.financeapplication.ui.common.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

object LegalTexts {
    const val TERMS = """1. Acceptance
By creating an account you agree to these Terms & Conditions.

2. Accounts
You are responsible for keeping your credentials confidential and for all activity under your account.

3. Payments & transfers
Transfers and payments are processed subject to available balance and daily limits. Scheduled payments that fail for insufficient funds are cancelled.

4. Fees
Standard account fees apply as shown in your fee schedule.

5. Changes
We may update these Terms from time to time. Continued use of the app means you accept the updated Terms.

This is sample text for a demo application and is not a legal agreement."""

    const val PRIVACY = """We collect only the information needed to provide your banking services: identity and contact details, account activity and device information used for security.

We never sell your personal information. We share it only with service providers who help us run the app, and where required by law.

You can review and update your details under Profile, and request deletion of your account by contacting Support.

This is sample text for a demo application and is not a legal policy."""
}

abstract class LegalFragment : BaseFragment<FragmentLegalBinding>(FragmentLegalBinding::inflate) {
    protected abstract val screenTitle: String
    protected abstract val intro: String
    protected abstract val agreeText: String
    protected abstract val nextDestination: Int
    /** Settings > Privacy opens the same screen read-only. */
    protected open val readOnly: Boolean get() = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            toolbar.setup(this@LegalFragment, screenTitle)
            tvIntro.text = intro
            cbAgree.text = agreeText
            cbAgree.isVisible = !readOnly
            btnContinue.isVisible = !readOnly
            rowTerms.setOnClickListener { show("Terms & Conditions", LegalTexts.TERMS) }
            rowPrivacy.setOnClickListener { show("Privacy Policy", LegalTexts.PRIVACY) }
            cbAgree.setOnCheckedChangeListener { _, checked -> btnContinue.isEnabled = checked }
            btnContinue.setOnClickListener { go(nextDestination) }
        }
    }

    private fun show(title: String, body: String) {
        MaterialAlertDialogBuilder(requireContext()).setTitle(title).setMessage(body)
            .setPositiveButton("Close", null).show()
    }
}

@AndroidEntryPoint
class TermsFragment : LegalFragment() {
    override val screenTitle = "Terms & Conditions"
    override val intro = "Please read and accept our Terms & Conditions to continue."
    override val agreeText = "I agree to the Terms & Conditions"
    override val nextDestination = R.id.action_terms_to_privacy
}

@AndroidEntryPoint
class PrivacyFragment : LegalFragment() {
    override val screenTitle = "Privacy Policy"
    override val intro = "Your privacy is important to us. We protect your personal information and data."
    override val agreeText = "I agree to the Privacy Policy"
    override val nextDestination = R.id.action_privacy_to_otp
    override val readOnly: Boolean get() = arguments?.getBoolean("readOnly", false) == true
}
