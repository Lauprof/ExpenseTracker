package com.example.expensetracker.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.expensetracker.data.local.ExpenseDatabase
import com.example.expensetracker.data.parser.HybridTransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = ExpenseDatabase.getInstance(context)
                val dao = database.expenseDao()
                val parser = HybridTransactionParser(context)

                val financialKeywords = listOf(
                    "mpesa", "m-pesa", "ksh", "paid to", "sent to", "received", "transferred",
                    "equity", "kcb", "coop", "absa", "stanbic", "ncba", "bank", "account", "debit", "credit"
                )

                for (sms in messages) {
                    val body = sms.messageBody ?: continue
                    val sender = sms.originatingAddress ?: "UNKNOWN"
                    val timestamp = sms.timestampMillis

                    val bodyLower = body.lowercase()
                    val senderLower = sender.lowercase()

                    val isFinancial = financialKeywords.any { keyword ->
                        senderLower.contains(keyword) || bodyLower.contains(keyword)
                    }

                    if (isFinancial) {
                        val rawSms = RawSmsMessage(
                            id = timestamp.toString(),
                            sender = sender,
                            body = body,
                            timestamp = timestamp
                        )

                        val parsedExpense = parser.parseMessage(rawSms)
                        if (parsedExpense != null) {
                            dao.insertExpense(parsedExpense)
                        }
                    }
                }
            } catch (e: Exception) {
                // Log or ignore
            } finally {
                pendingResult.finish()
            }
        }
    }
}
