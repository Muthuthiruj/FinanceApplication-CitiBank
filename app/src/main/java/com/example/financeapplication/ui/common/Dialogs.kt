package com.example.financeapplication.ui.common

import android.text.InputType
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.example.financeapplication.core.Validators
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Builds a vertical form with one TextInputLayout per field and returns the container + edit texts. */
fun Fragment.formDialog(
    title: String,
    fields: List<Triple<String, Int, String>>, // hint, inputType, initial text
    positive: String,
    onSubmit: (List<String>) -> String?        // return error message to keep dialog open, null to close
) {
    val ctx = requireContext()
    val pad = (20 * resources.displayMetrics.density).toInt()
    val container = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
    }
    val inputs = fields.map { (hint, type, initial) ->
        val til = TextInputLayout(ctx).apply {
            this.hint = hint
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = pad / 4 }
        }
        val et = TextInputEditText(til.context).apply { inputType = type; setText(initial); setSingleLine() }
        til.addView(et)
        container.addView(til)
        til to et
    }
    val dialog = MaterialAlertDialogBuilder(ctx).setTitle(title).setView(container)
        .setNegativeButton("Cancel", null).setPositiveButton(positive, null).create()
    dialog.setOnShowListener {
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val error = onSubmit(inputs.map { it.second.text?.toString().orEmpty().trim() })
            if (error == null) dialog.dismiss() else inputs.first().first.error = error
        }
    }
    dialog.show()
}

fun Fragment.promptAmount(title: String, hint: String = "Amount", onSubmit: (Double) -> Unit) {
    formDialog(title, listOf(Triple(hint, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, "")), "Continue") { values ->
        val amount = Validators.parseAmount(values[0])
        if (amount == null || amount <= 0) "Enter a valid amount" else { onSubmit(amount); null }
    }
}

/** Date picker restricted to dates after today. Returns a timestamp around noon of the chosen day. */
fun Fragment.pickFutureDate(onPicked: (Long) -> Unit) {
    val tomorrowUtc = com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds() + 86_400_000L
    val constraints = com.google.android.material.datepicker.CalendarConstraints.Builder()
        .setStart(tomorrowUtc)
        .setValidator(com.google.android.material.datepicker.DateValidatorPointForward.from(tomorrowUtc))
        .build()
    val picker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
        .setTitleText("Select date").setSelection(tomorrowUtc).setCalendarConstraints(constraints).build()
    picker.addOnPositiveButtonClickListener { onPicked(it + 12 * 3_600_000L) }
    picker.show(childFragmentManager, "date_picker")
}
