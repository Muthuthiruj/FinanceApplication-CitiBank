package com.example.financeapplication.ui.notifications

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financeapplication.R
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.databinding.FragmentNotificationsBinding
import com.example.financeapplication.databinding.ItemRowActionBinding
import com.example.financeapplication.domain.model.AppNotification
import com.example.financeapplication.domain.model.NotificationType
import com.example.financeapplication.domain.repository.NotificationRepository
import com.example.financeapplication.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(private val repo: NotificationRepository) : BaseViewModel() {
    private val onlyImportant = MutableStateFlow(false)
    val items: StateFlow<List<AppNotification>> = combine(repo.notifications, onlyImportant) { list, imp ->
        if (imp) list.filter { it.important } else list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setImportant(value: Boolean) { onlyImportant.value = value }
    fun markRead(id: String) { viewModelScope.launch { repo.markRead(id) } }
    fun markAllRead() { viewModelScope.launch { repo.markAllRead(); message("All notifications marked as read") } }
}

@AndroidEntryPoint
class NotificationsFragment : BaseFragment<FragmentNotificationsBinding>(FragmentNotificationsBinding::inflate) {
    private val viewModel: NotificationsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "Notifications")
        binding.toolbar.btnAction.isVisible = true
        binding.toolbar.btnAction.setImageResource(R.drawable.ci_check)
        binding.toolbar.btnAction.contentDescription = "Mark all as read"
        binding.toolbar.btnAction.setOnClickListener { viewModel.markAllRead() }

        val adapter = SimpleAdapter<AppNotification, ItemRowActionBinding>(ItemRowActionBinding::inflate, { it.id }) { n ->
            bindRow(
                title = n.title, subtitle = n.body, amount = relativeTime(n.timestamp).toString(),
                icon = when (n.type) {
                    NotificationType.PAYMENT -> R.drawable.ci_payment
                    NotificationType.CARD -> R.drawable.ci_card
                    NotificationType.SECURITY -> R.drawable.ci_shield
                    NotificationType.PROMO -> R.drawable.ci_gift
                },
                bold = !n.read, onClick = { if (!n.read) viewModel.markRead(n.id) }
            )
        }
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter
        binding.segments.addOnButtonCheckedListener { _, id, checked ->
            if (checked) viewModel.setImportant(id == binding.btnImportant.id)
        }
        collect(viewModel.items) {
            adapter.submitList(it)
            binding.tvEmpty.isVisible = it.isEmpty()
            binding.tvEmpty.text = "You're all caught up"
        }
        collectEvents(viewModel.events)
    }
}
