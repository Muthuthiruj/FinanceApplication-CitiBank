package com.example.financeapplication.ui.common

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

class BindingHolder<VB : ViewBinding>(val binding: VB) : RecyclerView.ViewHolder(binding.root)

/**
 * Tiny generic adapter so each screen only supplies a binding + bind lambda.
 * Items must be data classes (equality is used for content diffing).
 */
class SimpleAdapter<T : Any, VB : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> VB,
    idOf: (T) -> Any,
    private val onBind: VB.(T) -> Unit
) : ListAdapter<T, BindingHolder<VB>>(diffBy(idOf)) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        BindingHolder(inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: BindingHolder<VB>, position: Int) {
        holder.binding.onBind(getItem(position))
    }
}

fun <T : Any> diffBy(idOf: (T) -> Any) = object : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T, newItem: T) = idOf(oldItem) == idOf(newItem)
    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: T, newItem: T) = oldItem == newItem
}
