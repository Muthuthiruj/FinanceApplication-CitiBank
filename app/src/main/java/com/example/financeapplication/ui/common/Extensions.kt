package com.example.financeapplication.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.EditText
import androidx.annotation.IdRes
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.financeapplication.core.UiEvent
import com.example.financeapplication.databinding.ViewToolbarBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

/** Collects [flow] while the fragment's view is STARTED. */
fun <T> Fragment.collect(flow: Flow<T>, action: suspend (T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { action(it) }
        }
    }
}

/** Standard handling of one-shot events: messages -> snackbar, files -> open PDF. */
fun Fragment.collectEvents(events: Flow<UiEvent>, onDone: (String?) -> Unit = {}) {
    collect(events) { event ->
        when (event) {
            is UiEvent.Message -> snack(event.text)
            is UiEvent.Done -> onDone(event.message)
            is UiEvent.FileReady -> openPdf(event.file)
        }
    }
}

/** Snackbar anchored to the activity so it survives navigation. */
fun Fragment.snack(message: String) {
    val host = activity?.findViewById<View>(android.R.id.content) ?: return
    Snackbar.make(host, message, Snackbar.LENGTH_LONG).show()
}

fun Fragment.hideKeyboard() {
    val v = activity?.currentFocus ?: view ?: return
    (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
        .hideSoftInputFromWindow(v.windowToken, 0)
}

/**
 * Navigates to a destination (with slide animation) or fires a nav-graph action by id.
 * Actions keep the options declared in the graph (popUpTo etc.), so no extra options are passed for them.
 */
fun Fragment.go(@IdRes destination: Int, args: Bundle? = null) {
    val isAction = resources.getResourceEntryName(destination).startsWith("action_")
    if (isAction) {
        findNavController().navigate(destination, args)
    } else {
        findNavController().navigate(destination, args, androidx.navigation.navOptions {
            anim {
                enter = com.example.financeapplication.R.anim.slide_in_right
                exit = com.example.financeapplication.R.anim.slide_out_left
                popEnter = com.example.financeapplication.R.anim.slide_in_left
                popExit = com.example.financeapplication.R.anim.slide_out_right
            }
        })
    }
}

fun Fragment.back() { findNavController().navigateUp() }

fun Fragment.confirm(title: String, message: String, positive: String = "Confirm", onConfirm: () -> Unit) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(title).setMessage(message)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(positive) { _, _ -> onConfirm() }
        .show()
}

fun Fragment.info(title: String, message: String, onOk: (() -> Unit)? = null) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(title).setMessage(message)
        .setPositiveButton("OK") { _, _ -> onOk?.invoke() }
        .setCancelable(onOk == null)
        .show()
}

fun Fragment.openPdf(file: File) {
    val ctx = requireContext()
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        startActivity(view)
    } catch (e: ActivityNotFoundException) {
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "Share statement"))
    }
}

fun ViewToolbarBinding.setup(fragment: Fragment, title: String, showBack: Boolean = true) {
    tvTitle.text = title
    btnBack.isVisible = showBack
    btnBack.setOnClickListener { fragment.hideKeyboard(); fragment.back() }
}

/** Sets the checked state without triggering the listener, then installs [onChange]. */
fun CompoundButton.bind(checked: Boolean, onChange: (Boolean) -> Unit) {
    setOnCheckedChangeListener(null)
    isChecked = checked
    setOnCheckedChangeListener { _, value -> onChange(value) }
}

fun MaterialAutoCompleteTextView.setItems(items: List<String>, selectedIndex: Int? = null) {
    setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, items))
    if (selectedIndex != null && selectedIndex in items.indices) setText(items[selectedIndex], false)
}

fun MaterialAutoCompleteTextView.selectedIndex(items: List<String>): Int = items.indexOf(text?.toString().orEmpty())

fun EditText.value(): String = text?.toString().orEmpty().trim()

fun TextInputLayout.clearErrorOnEdit() {
    editText?.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { error = null }
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    })
}

fun Activity.isNightMode(): Boolean =
    resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
