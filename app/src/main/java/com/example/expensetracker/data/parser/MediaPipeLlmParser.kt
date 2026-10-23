package com.example.expensetracker.data.parser

import android.content.Context
import com.example.expensetracker.data.local.ExpenseEntity
import com.example.expensetracker.data.sms.RawSmsMessage
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaPipeLlmParser(private val context: Context) {

    private var llmInference: LlmInference? = null
    private val downloadManager = ModelDownloadManager(context)

    fun isModelReady(): Boolean = downloadManager.isModelDownloaded()

    suspend fun parse(message: RawSmsMessage): ExpenseEntity? = withContext(Dispatchers.IO) {
        if (!isModelReady()) return@withContext null

        try {
            if (llmInference == null) {
                val modelFile = downloadManager.getModelFile()
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(256)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
            }

            val inference = llmInference ?: return@withContext null

            val prompt = """
                Extract financial fields from SMS:
                "${message.body}"
                CRITICAL RULE: If the SMS is about sending money, paying, or staking on a betting platform (SportPesa, Betika, Mozzart, Odibets, 1xBet, Betway, KwikBet, etc.), Type MUST be EXPENSE and Category MUST be Entertainment. Never classify betting as INCOME.
                Output format: Amount: <number> | Recipient: <name> | Category: <category> | Type: <EXPENSE/INCOME/TRANSFER>
            """.trimIndent()

            val response = inference.generateResponse(prompt)
            if (response.isBlank()) return@withContext null

            parseLlmTextResponse(response, message)
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

    private fun parseLlmTextResponse(raw: String, message: RawSmsMessage): ExpenseEntity? {
        val amount = Regex("(?i)Amount:\\s*([0-9,]+\\.[0-9]{2}|[0-9,]+)")
            .find(raw)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: return null

        val recipient = Regex("(?i)Recipient:\\s*([^|\\n]+)").find(raw)?.groupValues?.get(1)?.trim() ?: "Merchant"
        var category = Regex("(?i)Category:\\s*([^|\\n]+)").find(raw)?.groupValues?.get(1)?.trim() ?: "General & Other"
        var type = Regex("(?i)Type:\\s*([A-Z]+)").find(raw)?.groupValues?.get(1)?.uppercase() ?: "EXPENSE"

        // Deterministic safety guardrail for betting platforms
        if (isBettingPlatform(recipient, message.body)) {
            type = "EXPENSE"
            category = "Entertainment"
        }

        return ExpenseEntity(
            transactionCode = "TX_${Math.abs((message.sender + message.body + message.timestamp).hashCode()).toString(16).uppercase()}",
            amount = amount,
            category = category,
            recipient = recipient,
            type = type,
            dateTimestamp = message.timestamp,
            rawSmsBody = message.body,
            parseMethod = "LLM",
            sender = message.sender,
            source = if (message.sender.contains("MPESA", ignoreCase = true)) "M-PESA" else "BANK"
        )
    }

    fun close() {
        try {
            llmInference?.close()
            llmInference = null
        } catch (e: Exception) {
            // Ignore
        }
    }
}
