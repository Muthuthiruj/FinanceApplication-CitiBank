package com.example.financeapplication.ui.common

import android.text.format.DateUtils
import androidx.core.content.ContextCompat
import com.example.financeapplication.R
import com.example.financeapplication.core.Formatters
import com.example.financeapplication.databinding.ItemAccountBinding
import com.example.financeapplication.databinding.ItemTransactionBinding
import com.example.financeapplication.domain.model.*

fun TxCategory.iconRes(): Int = when (this) {
    TxCategory.SHOPPING -> R.drawable.ci_cart
    TxCategory.FOOD -> R.drawable.ci_restaurant
    TxCategory.TRANSPORT -> R.drawable.ci_car
    TxCategory.INCOME -> R.drawable.ci_arrow_down
    TxCategory.UTILITIES -> R.drawable.ci_bolt
    TxCategory.TRANSFER -> R.drawable.ci_payment
    TxCategory.DEPOSIT -> R.drawable.ci_arrow_down
    TxCategory.ENTERTAINMENT -> R.drawable.ci_star
    TxCategory.TRAVEL -> R.drawable.ci_flight
    TxCategory.OTHER -> R.drawable.ci_card
}

fun BillCategory.iconRes(): Int = when (this) {
    BillCategory.ELECTRICITY -> R.drawable.ci_bolt
    BillCategory.INTERNET -> R.drawable.ci_wifi
    BillCategory.MOBILE -> R.drawable.ci_phone_android
    BillCategory.WATER -> R.drawable.ci_water
}

fun OfferCategory.iconRes(): Int = when (this) {
    OfferCategory.TRAVEL -> R.drawable.ci_flight
    OfferCategory.DINING -> R.drawable.ci_restaurant
    OfferCategory.SHOPPING -> R.drawable.ci_cart
    OfferCategory.CASHBACK -> R.drawable.ci_coins
}

fun AccountType.iconRes(): Int = when (this) {
    AccountType.CHECKING -> R.drawable.ci_accounts
    AccountType.SAVINGS -> R.drawable.ci_coins
    AccountType.CREDIT -> R.drawable.ci_card
}

fun ItemTransactionBinding.bindTransaction(t: Transaction, onClick: ((Transaction) -> Unit)? = null) {
    ivIcon.setImageResource(t.category.iconRes())
    tvTitle.text = t.title
    tvDate.text = Formatters.date(t.timestamp)
    tvAmount.text = Formatters.signedMoney(t.amount)
    tvAmount.setTextColor(ContextCompat.getColor(root.context, if (t.isCredit) R.color.success else R.color.text_primary))
    if (onClick != null) {
        root.setOnClickListener { onClick(t) }
    } else {
        root.setOnClickListener(null)
        root.isClickable = false
    }
}

fun ItemAccountBinding.bindAccount(a: Account, onClick: (Account) -> Unit) {
    ivIcon.setImageResource(a.type.iconRes())
    tvName.text = a.name
    tvNumber.text = a.masked
    tvBalance.text = Formatters.money(a.balance)
    tvBalanceLabel.text = if (a.isCredit) "Balance owed" else "Available"
    root.setOnClickListener { onClick(a) }
}

fun relativeTime(ts: Long): CharSequence =
    DateUtils.getRelativeTimeSpanString(ts, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)

/** Generic card row: icon or initials, two text lines, optional amount and action button. */
fun com.example.financeapplication.databinding.ItemRowActionBinding.bindRow(
    title: String,
    subtitle: String,
    amount: String? = null,
    icon: Int? = null,
    initials: String? = null,
    action: String? = null,
    bold: Boolean = false,
    onAction: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    ivIcon.visibility = if (icon != null) android.view.View.VISIBLE else android.view.View.GONE
    if (icon != null) ivIcon.setImageResource(icon)
    tvInitials.visibility = if (initials != null) android.view.View.VISIBLE else android.view.View.GONE
    tvInitials.text = initials
    tvTitle.text = title
    tvTitle.setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    tvSubtitle.text = subtitle
    tvAmount.visibility = if (amount != null) android.view.View.VISIBLE else android.view.View.GONE
    tvAmount.text = amount
    btnAction.visibility = if (action != null) android.view.View.VISIBLE else android.view.View.GONE
    btnAction.text = action
    btnAction.setOnClickListener { onAction?.invoke() }
    root.setOnClickListener(if (onClick != null) android.view.View.OnClickListener { onClick() } else null)
    root.isClickable = onClick != null
}
