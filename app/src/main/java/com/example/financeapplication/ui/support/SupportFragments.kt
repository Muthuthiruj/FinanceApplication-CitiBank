package com.example.financeapplication.ui.support

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financeapplication.R
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.core.Resource
import com.example.financeapplication.databinding.*
import com.example.financeapplication.domain.model.ChatMessage
import com.example.financeapplication.domain.model.Faq
import com.example.financeapplication.domain.repository.CardRepository
import com.example.financeapplication.domain.repository.SupportRepository
import com.example.financeapplication.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FaqRow(val faq: Faq, val expanded: Boolean)

@HiltViewModel
class HelpViewModel @Inject constructor(
    private val support: SupportRepository,
    private val cards: CardRepository
) : BaseViewModel() {
    private val query = MutableStateFlow("")
    private val expanded = MutableStateFlow(emptySet<String>())

    val faqs: StateFlow<List<FaqRow>> = combine(query, expanded) { q, open ->
        support.faqs().filter { q.isBlank() || it.question.contains(q.trim(), true) || it.answer.contains(q.trim(), true) }
            .map { FaqRow(it, it.id in open) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { query.value = q }
    fun toggle(id: String) { expanded.update { if (id in it) it - id else it + id } }

    fun report(text: String) {
        viewModelScope.launch {
            when (val r = support.reportProblem(text)) {
                is Resource.Success -> message("Report sent. We'll get back to you within 24 hours.")
                is Resource.Error -> message(r.message)
            }
        }
    }

    fun lockCard() {
        viewModelScope.launch {
            cards.updateControls { it.copy(quickLock = true) }
            message("Your card is locked. Call +1 (555) 010-9000 to order a replacement.")
        }
    }

    fun branchHours(): String = support.branches().joinToString("\n\n") { "${it.name}\n${it.hours}" }
}

@AndroidEntryPoint
class HelpFragment : BaseFragment<FragmentHelpBinding>(FragmentHelpBinding::inflate) {
    private val viewModel: HelpViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Help & Support")

        val quick = listOf(
            Triple(1, "FAQs", R.drawable.ci_help),
            Triple(2, "Contact Us", R.drawable.ci_phone),
            Triple(3, "Live Chat", R.drawable.ci_chat),
            Triple(4, "Report a Problem", R.drawable.ci_report),
            Triple(5, "Lost or Stolen Card", R.drawable.ci_card),
            Triple(6, "Branch Hours", R.drawable.ci_clock)
        )
        val quickAdapter = SimpleAdapter<Triple<Int, String, Int>, ItemMenuRowBinding>(ItemMenuRowBinding::inflate, { it.first }) { q ->
            ivIcon.setImageResource(q.third)
            tvTitle.text = q.second
            tvSubtitle.visibility = View.GONE
            root.setOnClickListener {
                when (q.first) {
                    1 -> binding.etSearch.requestFocus()
                    2 -> info("Contact us", "Phone: +1 (555) 010-9000 (24/7)\nEmail: support@citibank.demo")
                    3 -> go(R.id.chatFragment)
                    4 -> reportProblem()
                    5 -> confirm("Lost or stolen card", "Lock your card now? New purchases will be declined until you unlock it.", "Lock card") { viewModel.lockCard() }
                    else -> info("Branch hours", viewModel.branchHours())
                }
            }
        }
        binding.rvQuick.layoutManager = LinearLayoutManager(requireContext())
        binding.rvQuick.adapter = quickAdapter
        quickAdapter.submitList(quick)

        val faqAdapter = SimpleAdapter<FaqRow, ItemFaqBinding>(ItemFaqBinding::inflate, { it.faq.id }) { row ->
            tvQuestion.text = row.faq.question
            tvAnswer.text = row.faq.answer
            tvAnswer.isVisible = row.expanded
            ivExpand.rotation = if (row.expanded) 180f else 0f
            root.setOnClickListener { viewModel.toggle(row.faq.id) }
        }
        binding.rvFaq.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFaq.adapter = faqAdapter
        binding.etSearch.doAfterTextChanged { viewModel.setQuery(it?.toString().orEmpty()) }
        collect(viewModel.faqs) {
            faqAdapter.submitList(it)
            binding.tvNoFaq.isVisible = it.isEmpty()
        }
        collectEvents(viewModel.events)
    }

    private fun reportProblem() {
        formDialog("Report a problem", listOf(Triple("Describe what went wrong", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, "")), "Send") { v ->
            if (v[0].length < 10) "Please add a little more detail" else { viewModel.report(v[0]); null }
        }
    }
}

@HiltViewModel
class ChatViewModel @Inject constructor(private val support: SupportRepository) : BaseViewModel() {
    val messages: StateFlow<List<ChatMessage>> = support.chat.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _typing = MutableStateFlow(false)
    val typing: StateFlow<Boolean> = _typing.asStateFlow()

    fun send(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _typing.value = true
            support.sendChat(text.trim())
            _typing.value = false
        }
    }
}

@AndroidEntryPoint
class ChatFragment : BaseFragment<FragmentChatBinding>(FragmentChatBinding::inflate) {
    private val viewModel: ChatViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Support Chat")
        val adapter = SimpleAdapter<ChatMessage, ItemChatBinding>(ItemChatBinding::inflate, { it.id }) { m ->
            tvMessage.text = m.text
            tvMessage.setBackgroundResource(if (m.fromUser) R.drawable.bg_chat_user else R.drawable.bg_chat_bot)
            tvMessage.setTextColor(ContextCompat.getColor(root.context, if (m.fromUser) R.color.white else R.color.text_primary))
            (tvMessage.layoutParams as FrameLayout.LayoutParams).gravity = if (m.fromUser) Gravity.END else Gravity.START
        }
        binding.rvChat.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        binding.rvChat.adapter = adapter
        fun send() {
            val text = binding.etMessage.value()
            if (text.isNotEmpty()) { viewModel.send(text); binding.etMessage.setText("") }
        }
        binding.btnSend.setOnClickListener { send() }
        binding.etMessage.setOnEditorActionListener { _, _, _ -> send(); true }
        collect(viewModel.messages) { list ->
            adapter.submitList(list) { if (list.isNotEmpty()) binding.rvChat.scrollToPosition(list.lastIndex) }
        }
        collect(viewModel.typing) { binding.tvTyping.isVisible = it }
    }
}
