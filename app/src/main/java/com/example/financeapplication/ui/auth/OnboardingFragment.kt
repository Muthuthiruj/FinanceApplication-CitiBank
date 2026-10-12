package com.example.financeapplication.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.example.financeapplication.R
import com.example.financeapplication.databinding.FragmentOnboardingBinding
import com.example.financeapplication.databinding.ItemOnboardingPageBinding
import com.example.financeapplication.domain.repository.AuthRepository
import com.example.financeapplication.ui.common.BaseFragment
import com.example.financeapplication.ui.common.SimpleAdapter
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class OnboardingPage(val title: String, val subtitle: String, val illustration: Int)

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    val pages = listOf(
        OnboardingPage("Your Goals. Our Support.", "Manage your money, achieve your goals, all in one place.", R.drawable.ill_goals),
        OnboardingPage("Secure & Convenient", "Enjoy safe, fast and easy banking anytime, anywhere.", R.drawable.ill_secure),
        OnboardingPage("Build a Brighter Financial Future", "More features. More control. More for you.", R.drawable.ill_rewards)
    )

    fun finish() = auth.markOnboardingSeen()
}

@AndroidEntryPoint
class OnboardingFragment : BaseFragment<FragmentOnboardingBinding>(FragmentOnboardingBinding::inflate) {
    private val viewModel: OnboardingViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = SimpleAdapter<OnboardingPage, ItemOnboardingPageBinding>(
            ItemOnboardingPageBinding::inflate, { it.title }
        ) { page ->
            ivIllustration.setImageResource(page.illustration)
            tvTitle.text = page.title
            tvSubtitle.text = page.subtitle
        }
        adapter.submitList(viewModel.pages)
        binding.pager.adapter = adapter
        TabLayoutMediator(binding.dots, binding.pager) { _, _ -> }.attach()

        val last = viewModel.pages.lastIndex
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.btnNext.text = if (position == last) "Get Started" else "Next"
                binding.btnSkip.visibility = if (position == last) View.INVISIBLE else View.VISIBLE
            }
        })
        binding.btnNext.setOnClickListener {
            if (binding.pager.currentItem < last) binding.pager.currentItem += 1 else finish()
        }
        binding.btnSkip.setOnClickListener { finish() }
    }

    private fun finish() {
        viewModel.finish()
        findNavController().navigate(R.id.action_onboarding_to_login)
    }
}
