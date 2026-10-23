package com.example.expensetracker.data.parser

import com.example.expensetracker.data.local.ExpenseEntity
import com.example.expensetracker.data.local.SpecialCategories
import java.util.regex.Pattern

data class SmsFields(
    val fee: Double,
    val balanceAfter: Double?,
    val direction: String,
    val isWithdrawal: Boolean,
    val isReversal: Boolean,
    val reversedCode: String?,
    val isFulizaRepayment: Boolean
)

/**
 * Pulls fee, balance, direction and special-case flags out of an SMS body.
 * Used for both regex- and LLM-parsed rows so they end up with the same fields.
 */
object SmsFieldExtractor {

    private const val KSH = "(?:Ksh|KES|Kshs)\\.?\\s*"
    private const val NUM = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"

    private val feePattern = Pattern.compile(
        "(?:transaction\\s+cost|transaction\\s+fee|access\\s+fee(?:\\s+charged)?|interest\\s+charged|charges?|fee)" +
            "\\s*[,:]?\\s*(?:of\\s+)?$KSH$NUM",
        Pattern.CASE_INSENSITIVE
    )

    private val balancePattern = Pattern.compile(
        "(?:m-?pesa\\s+balance|new\\s+balance|available\\s+balance|avail(?:able)?\\.?\\s*bal(?:ance)?|actual\\s+balance|balance)" +
            "\\s*(?:is|:)?\\s*$KSH$NUM",
        Pattern.CASE_INSENSITIVE
    )

    private val reversedCodePattern = Pattern.compile("(?i:reversal\\s+of\\s+transaction)\\s+([A-Z0-9]{10})\\b")

    private fun toDouble(s: String?): Double? = s?.replace(",", "")?.trimEnd('.')?.toDoubleOrNull()

    fun extract(body: String): SmsFields {
        val lower = body.lowercase()

        var fee = 0.0
        val feeMatcher = feePattern.matcher(body)
        while (feeMatcher.find()) fee += toDouble(feeMatcher.group(1)) ?: 0.0

        val balMatcher = balancePattern.matcher(body)
        val balance = if (balMatcher.find()) toDouble(balMatcher.group(1)) else null

        val isReversal = lower.contains("reversal of transaction") || lower.contains("has been reversed") ||
            lower.contains("successfully reversed")
        val reversedCode = if (isReversal) {
            reversedCodePattern.matcher(body).let { if (it.find()) it.group(1) else null }
        } else null

        val direction = when {
            isReversal -> "IN"
            lower.contains("you have received") || lower.contains("received ksh") ||
                lower.contains("credited") || lower.contains("deposited") -> "IN"
            else -> "OUT"
        }

        return SmsFields(
            fee = fee,
            balanceAfter = balance,
            direction = direction,
            isWithdrawal = lower.contains("withdraw") || lower.contains("give ksh"),
            isReversal = isReversal,
            reversedCode = reversedCode,
            isFulizaRepayment = lower.contains("pay your outstanding fuliza")
        )
    }

    /**
     * Fills the new fields and applies the special cases. Precedence:
     * reversal > Fuliza repayment > agent/ATM withdrawal.
     * Safe to call twice on the same row.
     */
    fun enrich(e: ExpenseEntity): ExpenseEntity {
        val f = extract(e.rawSmsBody)
        val base = e.copy(fee = f.fee, balanceAfter = f.balanceAfter, direction = f.direction)

        val upper = e.rawSmsBody.uppercase()
        val bankMentioningMpesa = e.source != "M-PESA" && (upper.contains("MPESA") || upper.contains("M-PESA"))

        return when {
            f.isReversal -> base.copy(
                type = "REVERSED", category = SpecialCategories.REVERSAL,
                direction = "IN", linkedCode = f.reversedCode
            )
            f.isFulizaRepayment -> base.copy(
                type = "TRANSFER", category = SpecialCategories.FULIZA, needsReview = true
            )
            f.isWithdrawal && !bankMentioningMpesa -> base.copy(
                type = "TRANSFER", category = SpecialCategories.CASH
            )
            else -> base
        }
    }
}
