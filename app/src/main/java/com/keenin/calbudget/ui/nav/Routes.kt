package com.keenin.calbudget.ui.nav

object Routes {
    const val HOME = "home"
    const val BILLS = "bills"
    const val CARDS = "cards"
    const val MORTGAGE = "mortgage"
    const val PAY = "pay"
    const val SETTINGS = "settings"
    const val BILL_EDIT = "bills/edit/{id}"
    const val CARD_EDIT = "cards/edit/{id}"
    const val MORTGAGE_EDIT = "mortgage/edit/{id}"
    const val PAY_EDIT = "pay/edit/{id}"
    const val WINDOW_UNTIL = "until"
    const val WINDOW_NEXT = "next"
    const val BREAKDOWN = "breakdown/{window}"

    fun billEdit(id: Long?) = "bills/edit/${id ?: "new"}"
    fun cardEdit(id: Long?) = "cards/edit/${id ?: "new"}"
    fun mortgageEdit(id: Long?) = "mortgage/edit/${id ?: "new"}"
    fun payEdit(id: Long?) = "pay/edit/${id ?: "new"}"
    fun breakdown(window: String) = "breakdown/$window"
}
