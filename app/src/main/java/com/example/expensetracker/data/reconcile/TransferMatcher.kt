package com.example.expensetracker.data.reconcile

import com.example.expensetracker.data.local.ExpenseEntity
import com.example.expensetracker.data.local.SpecialCategories
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Pairs the two sides of a move between the user's own accounts
 * (M-Pesa out + bank in, or bank out + M-Pesa in) so neither counts as spend or income.
 */
object TransferMatcher {

    const val WINDOW_MS = 10 * 60 * 1000L

    private val ELIGIBLE_TYPES = setOf("EXPENSE", "INCOME", "TRANSFER")

    private fun cents(amount: Double) = (amount * 100).roundToLong()

    /** Names a source's SMS uses for itself. Tune these against real messages. */
    private fun keys(source: String): List<String> = when {
        source.contains("M-PESA") -> listOf("M-PESA", "MPESA")
        source.contains("CO-OP") -> listOf("CO-OP", "COOP", "CO-OPERATIVE")
        else -> listOf(source.removeSuffix(" BANK").trim()).filter { it.isNotBlank() && it != "BANK" }
    }

    private fun mentions(a: ExpenseEntity, b: ExpenseEntity): Boolean {
        val bodyA = a.rawSmsBody.uppercase()
        val bodyB = b.rawSmsBody.uppercase()
        return keys(b.source).any { bodyA.contains(it) } || keys(a.source).any { bodyB.contains(it) }
    }

    private fun isEligible(e: ExpenseEntity) =
        e.linkedCode == null && e.parseMethod != "MANUAL" &&
            e.type in ELIGIBLE_TYPES && e.category !in SpecialCategories.NOT_PAIRABLE

    /** Returns the rows that changed (both sides of every pair found). */
    fun match(rows: List<ExpenseEntity>): List<ExpenseEntity> {
        val eligible = rows.filter(::isEligible)
        val outs = eligible.filter { it.direction == "OUT" }.sortedBy { it.dateTimestamp }
        val insByAmount = eligible.filter { it.direction == "IN" }
            .groupBy { cents(it.amount) }
            .mapValues { it.value.toMutableList() }

        val changed = mutableListOf<ExpenseEntity>()
        for (out in outs) {
            val bucket = insByAmount[cents(out.amount)] ?: continue
            val best = bucket
                .filter {
                    it.source != out.source &&
                        abs(it.dateTimestamp - out.dateTimestamp) <= WINDOW_MS &&
                        mentions(out, it)
                }
                .minByOrNull { abs(it.dateTimestamp - out.dateTimestamp) } ?: continue
            bucket.remove(best)
            changed += out.copy(
                type = "TRANSFER", category = SpecialCategories.BANK_TRANSFER,
                linkedCode = best.transactionCode, needsReview = false
            )
            changed += best.copy(
                type = "TRANSFER", category = SpecialCategories.BANK_TRANSFER,
                linkedCode = out.transactionCode, needsReview = false
            )
        }
        return changed
    }

    /**
     * Rows that clearly reference another of the user's institutions but found no
     * counterpart after the window has passed. Returned with needsReview = true.
     */
    fun flagUnmatched(unmatched: List<ExpenseEntity>, now: Long): List<ExpenseEntity> =
        unmatched.filter(::isEligible).filter { e ->
            !e.needsReview &&
                now - e.dateTimestamp > WINDOW_MS &&
                e.type != "TRANSFER" &&
                referencesOtherInstitution(e)
        }.map { it.copy(needsReview = true) }

    private fun referencesOtherInstitution(e: ExpenseEntity): Boolean {
        val body = e.rawSmsBody.uppercase()
        val others = listOf("EQUITY", "KCB", "CO-OP", "COOP", "ABSA", "STANBIC", "NCBA", "M-PESA", "MPESA")
            .filterNot { k -> keys(e.source).contains(k) }
        return others.any { body.contains(it) }
    }
}
