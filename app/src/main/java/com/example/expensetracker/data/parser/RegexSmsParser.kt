package com.example.expensetracker.data.parser

import android.content.Context
import com.example.expensetracker.data.local.ExpenseEntity
import com.example.expensetracker.data.local.PreferencesManager
import com.example.expensetracker.data.local.SpecialCategories
import com.example.expensetracker.data.sms.RawSmsMessage
import java.util.regex.Pattern

class RegexSmsParser(private val context: Context? = null) {

    private val amountPattern = Pattern.compile("(?:Ksh|KES|Kshs)\\.?\\s*([0-9,]+\\.[0-9]{2}|[0-9,]+)", Pattern.CASE_INSENSITIVE)
    private val txCodePattern = Pattern.compile("^([A-Z0-9]{10})\\s*Confirmed", Pattern.CASE_INSENSITIVE)
    private val fallbackTxCodePattern = Pattern.compile("\\b([A-Z0-9]{10})\\b")

    fun parse(message: RawSmsMessage): ExpenseEntity? {
        val body = message.body
        if (body.isBlank()) return null

        // Filter out non-transactional informational messages, marketing, promos, and spam
        if (TransactionFilter.isNonTransactionMessage(body)) return null

        // Strip balance phrases first so amountPattern doesn't extract the balance as the transaction amount
        val bodyWithoutBalance = body.replace(
            Regex("(?i)(?:m-?pesa\\s+balance|new\\s+balance|available\\s+balance|avail\\.?\\s*bal(?:ance)?|actual\\s+balance|balance)\\s*(?:is|:)?\\s*(?:Ksh|KES|Kshs)?\\.?\\s*[0-9,]+(?:\\.[0-9]{2})?"),
            ""
        )

        val amountMatcher = amountPattern.matcher(bodyWithoutBalance)
        if (!amountMatcher.find()) return null

        val rawAmount = amountMatcher.group(1)?.replace(",", "") ?: return null
        val amount = rawAmount.toDoubleOrNull() ?: return null

        // Deterministic transaction code to prevent duplicate creation when SMS is backed up & restored
        val txCodeMatcher = txCodePattern.matcher(body)
        val transactionCode = if (txCodeMatcher.find()) {
            txCodeMatcher.group(1) ?: ""
        } else {
            val fallbackMatcher = fallbackTxCodePattern.matcher(body)
            if (fallbackMatcher.find()) {
                fallbackMatcher.group(1) ?: generateDeterministicTxCode(message)
            } else {
                generateDeterministicTxCode(message)
            }
        }

        val source = determineSource(message.sender, body)
        val recipient = extractRecipient(body, source)

        val lowerBody = body.lowercase()
        val isIncoming = lowerBody.contains("you have received") || lowerBody.contains("received") ||
                lowerBody.contains("credited") || lowerBody.contains("deposited") ||
                (lowerBody.contains("transferred from") && (lowerBody.contains("to m-pesa") || lowerBody.contains("to your mpesa")))

        val isBetting = isBettingPlatform(recipient, body)
        val isGlobalPay = isGlobalPayOrVirtualCard(recipient, body)
        val isInvestmentOut = !isIncoming && !isBetting && !isGlobalPay && isInvestmentOrMmf(recipient, body)

        val type = when {
            isIncoming -> "INCOME"
            isBetting -> "EXPENSE"
            isGlobalPay || isInvestmentOut -> "TRANSFER"
            else -> determineTransactionType(body, recipient)
        }

        val category = when {
            isIncoming -> "Income"
            isBetting -> "Entertainment"
            isGlobalPay || isInvestmentOut -> SpecialCategories.INVESTMENTS
            else -> determineCategory(recipient, body, type)
        }

        val entity = ExpenseEntity(
            transactionCode = transactionCode,
            amount = amount,
            category = category,
            recipient = recipient,
            type = type,
            dateTimestamp = message.timestamp,
            rawSmsBody = body,
            parseMethod = "REGEX",
            sender = message.sender,
            source = source
        )

        // Adds fee, balance, direction and handles withdrawals, reversals and Fuliza repayments
        return SmsFieldExtractor.enrich(entity)
    }

    private fun generateDeterministicTxCode(message: RawSmsMessage): String {
        val content = "${message.sender.trim().uppercase()}_${message.body.trim()}_${message.timestamp}"
        val hash = Math.abs(content.hashCode()).toString(16).uppercase()
        return "TX_$hash"
    }

    private fun isBettingPlatform(recipient: String, body: String): Boolean {
        val combined = "$recipient $body".lowercase()
        val keywords = listOf(
            "sportpesa", "betika", "mozzart", "odibets", "odi bet", "1xbet",
            "kwikbet", "shabiki", "betway", "premierbet", "bangbet", "parimatch",
            "betafriq", "22bet", "mcheza", "casino", "lotto", "lottery", "betpawa"
        )
        return keywords.any { combined.contains(it) }
    }

    private fun isGlobalPayOrVirtualCard(recipient: String, body: String): Boolean {
        val combined = "$recipient $body".lowercase()
        val keywords = listOf(
            "global pay", "virtual card", "equity global", "kcb card",
            "visa card", "mastercard top-up", "card topup", "card top up"
        )
        return keywords.any { combined.contains(it) }
    }

    private fun isInvestmentOrMmf(recipient: String, body: String): Boolean {
        val combined = "$recipient $body".lowercase()
        val keywords = listOf(
            "ziidi", "zidi", "mmf", "money market", "sanlam", "cic", "cytonn", "nabo", "zimele", "britam",
            "etica", "mansa x", "mansax", "kuza", "madison", "ndovu", "mali", "sacco",
            "unit trust", "investment", "genghis", "apollo", "old mutual", "icea lion", "stanlib"
        )
        return keywords.any { combined.contains(it) }
    }

    private fun determineSource(sender: String, body: String): String {
        val combined = "$sender $body".uppercase()
        return when {
            combined.contains("EQUITY") -> "EQUITY BANK"
            combined.contains("KCB") -> "KCB BANK"
            combined.contains("COOP") || combined.contains("CO-OP") -> "CO-OP BANK"
            combined.contains("ABSA") -> "ABSA BANK"
            combined.contains("STANBIC") -> "STANBIC BANK"
            combined.contains("NCBA") -> "NCBA BANK"
            combined.contains("MPESA") || combined.contains("M-PESA") -> "M-PESA"
            else -> "BANK"
        }
    }

    private fun extractRecipient(body: String, source: String): String {
        val lower = body.lowercase()
        val rawRecipient = when {
            lower.contains("to mpesa for ") || lower.contains("to m-pesa for ") -> {
                body.afterIgnoreCase("for ")
            }
            lower.contains("paid to ") -> body.afterIgnoreCase("paid to ")
            lower.contains("sent to ") -> body.afterIgnoreCase("sent to ")
            lower.contains("transferred to ") -> body.afterIgnoreCase("transferred to ")
            lower.contains("transferred ") && lower.contains(" for ") -> body.afterIgnoreCase(" for ")
            lower.contains("transferred ") && lower.contains(" to ") -> body.afterIgnoreCase(" to ")
            lower.contains("bought ") -> body.afterIgnoreCase("bought ")
            lower.contains("from ") -> body.afterIgnoreCase("from ")
            lower.contains("to account ") -> body.afterIgnoreCase("to account ")
            else -> ""
        }

        if (rawRecipient.isNotBlank()) {
            val cleaned = rawRecipient
                .beforeIgnoreCase(" MPESA REF:")
                .beforeIgnoreCase(" M-PESA REF:")
                .beforeIgnoreCase(". Queries")
                .beforeIgnoreCase(". Cost:")
                .beforeIgnoreCase(" on ")
                .beforeIgnoreCase(" at ")
                .beforeIgnoreCase(". New")
                .beforeIgnoreCase(", New")
                .beforeIgnoreCase(" Balance")
                .beforeIgnoreCase(" balance")
                .beforeIgnoreCase(".")
                .trim()
            if (cleaned.isNotBlank() && cleaned.length < 50) {
                return cleaned
            }
        }

        val upper = body.uppercase()
        return when {
            upper.contains("KPLC") || upper.contains("PREPAID") -> "KPLC Prepaid"
            upper.contains("NAIVAS") -> "Naivas Supermarket"
            upper.contains("CARREFOUR") -> "Carrefour"
            upper.contains("QUICKMART") -> "Quickmart"
            upper.contains("ZUKU") -> "Zuku Fiber"
            upper.contains("DSTV") || upper.contains("GOTV") -> "MultiChoice / DStv"
            upper.contains("WATER") -> "Water Utility"
            upper.contains("AIRTIME") -> "Safaricom Airtime"
            upper.contains("UBER") -> "Uber Ride"
            upper.contains("BOLT") -> "Bolt Ride"
            else -> "Bank Transfer / Account"
        }
    }

    private fun String.afterIgnoreCase(delimiter: String): String {
        val idx = indexOf(delimiter, ignoreCase = true)
        return if (idx != -1) substring(idx + delimiter.length) else this
    }

    private fun String.beforeIgnoreCase(delimiter: String): String {
        val idx = indexOf(delimiter, ignoreCase = true)
        return if (idx != -1) substring(0, idx) else this
    }

    private fun determineTransactionType(body: String, recipient: String): String {
        val lower = body.lowercase()
        val prefs = context?.let { PreferencesManager(it) }
        val userPhone = prefs?.userPhoneNumber?.trim() ?: ""
        val userName = prefs?.userFullName?.trim() ?: ""

        val phoneTail = userPhone.filter { it.isDigit() }.takeLast(9)
        val phoneMatchRecipient = phoneTail.length == 9 && recipient.filter { it.isDigit() }.endsWith(phoneTail)
        val phoneMatchBody = phoneTail.length == 9 && body.filter { it.isDigit() }.contains(phoneTail)

        val nameTokens = userName.split(" ").filter { it.length > 1 }
        val nameMatchRecipient = nameTokens.size >= 2 && nameTokens.all { recipient.contains(it, ignoreCase = true) }
        val nameMatchBody = nameTokens.size >= 2 && nameTokens.all { body.contains(it, ignoreCase = true) }

        val isSelfTransfer = phoneMatchRecipient || phoneMatchBody ||
                nameMatchRecipient || nameMatchBody ||
                lower.contains("to my mpesa") || lower.contains("to my m-pesa") ||
                lower.contains("own account") || lower.contains("to mpesa for") || lower.contains("to m-pesa for")

        if (isSelfTransfer) return "TRANSFER"

        return when {
            lower.contains("you have received") || lower.contains("received") || lower.contains("credited") -> "INCOME"
            lower.contains("paid to") || lower.contains("sent to") || lower.contains("bought") ||
                    lower.contains("withdraw") || lower.contains("debit") || lower.contains("transferred") -> "EXPENSE"
            else -> "EXPENSE"
        }
    }

    private fun determineCategory(recipient: String, body: String, type: String): String {
        if (type == "TRANSFER") return SpecialCategories.BANK_TRANSFER
        if (type == "INCOME") return "Income"

        val combined = "$recipient $body".lowercase()
        return when {
            combined.contains("supermarket") || combined.contains("carrefour") || combined.contains("naivas") ||
                    combined.contains("quickmart") || combined.contains("jumia") || combined.contains("mart") -> "Shopping"

            combined.contains("restaurant") || combined.contains("cafe") || combined.contains("food") ||
                    combined.contains("java") || combined.contains("kfc") || combined.contains("bakery") || combined.contains("pizza") -> "Food & Dining"

            combined.contains("kplc") || combined.contains("token") || combined.contains("power") ||
                    combined.contains("water") || combined.contains("internet") || combined.contains("zuku") ||
                    combined.contains("safaricom") || combined.contains("airtime") -> "Utilities & Bills"

            combined.contains("fuel") || combined.contains("total") || combined.contains("shell") ||
                    combined.contains("rubis") || combined.contains("uber") || combined.contains("bolt") || combined.contains("matatu") -> "Travel & Transport"

            combined.contains("hospital") || combined.contains("pharmacy") || combined.contains("clinic") ||
                    combined.contains("chemist") || combined.contains("medicare") -> "Health & Medical"

            combined.contains("sportpesa") || combined.contains("betika") || combined.contains("mozzart") ||
                    combined.contains("odibets") || combined.contains("1xbet") || combined.contains("cinema") ||
                    combined.contains("netflix") || combined.contains("spotify") || combined.contains("game") ||
                    combined.contains("park") -> "Entertainment"

            combined.contains("sent to") || combined.contains("transfer") -> "Transfers & Out"

            else -> "General & Other"
        }
    }
}
