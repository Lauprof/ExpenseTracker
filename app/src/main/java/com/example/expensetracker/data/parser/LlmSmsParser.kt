package com.example.expensetracker.data.parser

import android.content.Context
import com.example.expensetracker.data.local.ExpenseEntity
import com.example.expensetracker.data.sms.RawSmsMessage
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateTypedContentRequest
import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Generable
data class ParsedTransaction(
    @param:Guide(description = "Transaction code or reference ID e.g. SAB1234567, or TX_001 if absent")
    var transactionCode: String = "",

    @param:Guide(description = "Numeric amount in Kenya Shillings without currency symbols e.g. 1250.0")
    var amount: Double = 0.0,

    @param:Guide(description = "Exact Category choice: Shopping, Food & Dining, Utilities & Bills, Bank Transfer, Transfers & Out, Travel & Transport, Health & Medical, Entertainment, Income, or General & Other")
    var category: String = "General & Other",

    @param:Guide(description = "Merchant, paybill, person, or recipient name")
    var recipient: String = "General Merchant",

    @param:Guide(description = "Transaction type: EXPENSE, INCOME, or TRANSFER")
    var type: String = "EXPENSE",

    @param:Guide(description = "Data source name e.g. M-PESA, EQUITY BANK, KCB BANK, CO-OP BANK, ABSA BANK")
    var source: String = "M-PESA"
)

class LlmSmsParser(private val context: Context) {

    private var generativeModel: GenerativeModel? = null

    suspend fun isModelAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (generativeModel == null) {
                generativeModel = Generation.getClient()
            }
            val status = generativeModel?.checkStatus()
            status == FeatureStatus.AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    suspend fun parse(message: RawSmsMessage): ExpenseEntity? = withContext(Dispatchers.IO) {
        try {
            if (generativeModel == null) {
                generativeModel = Generation.getClient()
            }
            val model = generativeModel ?: return@withContext null

            val prompt = """
                Extract financial details from this SMS:
                "${message.body}"
                CRITICAL RULE: If the SMS is about sending money, paying, or staking on a betting platform (SportPesa, Betika, Mozzart, Odibets, 1xBet, Betway, KwikBet, etc.), Type MUST be EXPENSE and Category MUST be Entertainment. Never classify betting as INCOME.
                Identify transaction code, numerical amount, category, recipient name, type (EXPENSE, INCOME, TRANSFER), and source (M-PESA or Bank name).
            """.trimIndent()

            val baseRequest = GenerateContentRequest.Builder(TextPart(prompt)).build()
            val typedRequest = generateTypedContentRequest(baseRequest, ParsedTransaction::class)
            val typedResponse = model.generateContent(typedRequest)

            val candidate = typedResponse.candidates.firstOrNull()?.response ?: return@withContext null

            val txCode = if (candidate.transactionCode.isNotBlank()) candidate.transactionCode else "TX_${message.id}"
            val amount = if (candidate.amount > 0) candidate.amount else return@withContext null

            var type = candidate.type.uppercase()
            var category = normalizeCategory(candidate.category)
            val recipient = if (candidate.recipient.isNotBlank()) candidate.recipient else "General Merchant"

            if (isBettingPlatform(recipient, message.body)) {
                type = "EXPENSE"
                category = "Entertainment"
            }

            ExpenseEntity(
                transactionCode = txCode,
                amount = amount,
                category = category,
                recipient = recipient,
                type = type,
                dateTimestamp = message.timestamp,
                rawSmsBody = message.body,
                parseMethod = "LLM",
                sender = message.sender,
                source = if (candidate.source.isNotBlank()) candidate.source.uppercase() else "M-PESA"
            )
        } catch (e: Exception) {
            null
        }
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

    private fun normalizeCategory(rawCategory: String): String {
        val validCategories = listOf(
            "Shopping", "Food & Dining", "Utilities & Bills", "Bank Transfer", "Transfers & Out",
            "Travel & Transport", "Health & Medical", "Entertainment", "Income", "General & Other"
        )
        return validCategories.firstOrNull { it.equals(rawCategory.trim(), ignoreCase = true) }
            ?: "General & Other"
    }

    fun close() {
        try {
            generativeModel?.close()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
