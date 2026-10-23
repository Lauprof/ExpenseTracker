package com.example.expensetracker.data.reconcile

import com.example.expensetracker.data.local.ExpenseEntity
import com.example.expensetracker.data.local.SpecialCategories
import kotlin.math.abs

data class BalanceGap(
    val previousCode: String,
    val code: String,
    val expectedBalance: Double,
    val actualBalance: Double,
    val timestamp: Long
)

/**
 * For one account's rows, checks that each reported balance equals the previous
 * balance +/- amount (and fee). A mismatch means an SMS is missing in between.
 */
object BalanceChecker {

    fun findGaps(rows: List<ExpenseEntity>, tolerance: Double = 0.5): List<BalanceGap> {
        val ordered = rows
            .filter { it.balanceAfter != null }
            .sortedWith(compareBy({ it.dateTimestamp }, { it.id }))

        val gaps = mutableListOf<BalanceGap>()
        for (i in 1 until ordered.size) {
            val prev = ordered[i - 1]
            val cur = ordered[i]
            // Fuliza moves the balance in ways the SMS doesn't spell out: use as baseline only.
            if (isFuliza(cur)) continue

            val expected = if (cur.direction == "IN") {
                prev.balanceAfter!! + cur.amount
            } else {
                prev.balanceAfter!! - cur.amount - cur.fee
            }
            if (abs(expected - cur.balanceAfter!!) > tolerance) {
                gaps += BalanceGap(prev.transactionCode, cur.transactionCode, expected, cur.balanceAfter, cur.dateTimestamp)
            }
        }
        return gaps
    }

    private fun isFuliza(e: ExpenseEntity) =
        e.category == SpecialCategories.FULIZA || e.rawSmsBody.contains("fuliza", ignoreCase = true)
}
