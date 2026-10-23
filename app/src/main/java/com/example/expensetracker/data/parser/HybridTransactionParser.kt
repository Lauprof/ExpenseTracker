package com.example.expensetracker.data.parser

import android.content.Context
import com.example.expensetracker.data.local.ExpenseEntity
import com.example.expensetracker.data.sms.RawSmsMessage

class HybridTransactionParser(context: Context) {

    private val regexParser = RegexSmsParser(context)
    private val llmParser = LlmSmsParser(context)
    private val mediaPipeParser = MediaPipeLlmParser(context)

    suspend fun parseMessage(message: RawSmsMessage): ExpenseEntity? {
        val body = message.body
        if (body.isBlank() || TransactionFilter.isNonTransactionMessage(body)) return null

        // Prioritize on-device local AI model if downloaded and ready
        if (mediaPipeParser.isModelReady()) {
            val mpResult = mediaPipeParser.parse(message)
            if (mpResult != null && mpResult.amount > 0) {
                return SmsFieldExtractor.enrich(mpResult)
            }
        }

        // Prioritize system Gemini Nano ML Kit API if available
        if (llmParser.isModelAvailable()) {
            val llmResult = llmParser.parse(message)
            if (llmResult != null && llmResult.amount > 0) {
                return SmsFieldExtractor.enrich(llmResult)
            }
        }

        // Fall back to Regex rules engine
        return regexParser.parse(message)
    }

    suspend fun isLlmAvailable(): Boolean {
        return mediaPipeParser.isModelReady() || llmParser.isModelAvailable()
    }

    fun close() {
        llmParser.close()
        mediaPipeParser.close()
    }
}
