package com.example.financeapplication.ui.locator

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financeapplication.core.BaseViewModel
import com.example.financeapplication.databinding.FragmentLocatorBinding
import com.example.financeapplication.databinding.ItemBranchBinding
import com.example.financeapplication.domain.model.Branch
import com.example.financeapplication.domain.model.BranchType
import com.example.financeapplication.domain.repository.SupportRepository
import com.example.financeapplication.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class LocatorState(val items: List<Branch> = emptyList(), val selectedId: String? = null)

@HiltViewModel
class LocatorViewModel @Inject constructor(support: SupportRepository) : BaseViewModel() {
    private val all = support.branches().sortedBy { it.distanceMiles }
    private val type = MutableStateFlow(BranchType.ATM)
    private val selected = MutableStateFlow<String?>(all.firstOrNull { it.type == BranchType.ATM }?.id)

    val state: StateFlow<LocatorState> = combine(type, selected) { t, sel ->
        LocatorState(all.filter { it.type == t }, sel)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocatorState())

    fun setType(t: BranchType) {
        type.value = t
        selected.value = all.firstOrNull { it.type == t }?.id
    }

    fun select(id: String) { selected.value = id }
}

@AndroidEntryPoint
class LocatorFragment : BaseFragment<FragmentLocatorBinding>(FragmentLocatorBinding::inflate) {
    private val viewModel: LocatorViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setup(this, "ATM / Branch Locator")
        val adapter = SimpleAdapter<Branch, ItemBranchBinding>(ItemBranchBinding::inflate, { it.id }) { b ->
            tvName.text = b.name
            tvDistance.text = "${b.distanceMiles} mi"
            tvAddress.text = b.address
            tvHours.text = b.hours
            btnCall.setOnClickListener { dial(b) }
            btnDirections.setOnClickListener { directions(b) }
            root.setOnClickListener { viewModel.select(b.id) }
        }
        binding.rvList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvList.adapter = adapter
        binding.segments.addOnButtonCheckedListener { _, id, checked ->
            if (checked) viewModel.setType(if (id == binding.btnAtms.id) BranchType.ATM else BranchType.BRANCH)
        }
        binding.map.onPinClick = { id ->
            viewModel.select(id)
            val index = adapter.currentList.indexOfFirst { it.id == id }
            if (index >= 0) binding.rvList.smoothScrollToPosition(index)
        }
        collect(viewModel.state) { s ->
            adapter.submitList(s.items)
            binding.map.setPins(s.items, s.selectedId)
        }
    }

    private fun dial(b: Branch) {
        val number = b.phone.filter { it.isDigit() || it == '+' }
        try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
        catch (e: ActivityNotFoundException) { snack("No dialer app available") }
    }

    private fun directions(b: Branch) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(b.address)}"))) }
        catch (e: ActivityNotFoundException) { snack("No maps app available") }
    }
}
