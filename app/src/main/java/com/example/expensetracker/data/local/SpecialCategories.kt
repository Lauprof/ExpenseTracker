package com.example.expensetracker.data.local

/** Category labels with special meaning for transfer matching and totals. */
object SpecialCategories {
    const val FEES = "Fees & Charges"
    const val CASH = "Cash Withdrawal"
    const val FULIZA = "Fuliza"
    const val REVERSAL = "Reversal"
    const val REVERSED = "Reversed"
    const val BANK_TRANSFER = "Bank Transfer"
    const val INVESTMENTS = "Investments & Savings"

    /** Rows in these categories never take part in transfer pairing. */
    val NOT_PAIRABLE = setOf(FEES, CASH, FULIZA, REVERSAL, REVERSED, INVESTMENTS)
}
