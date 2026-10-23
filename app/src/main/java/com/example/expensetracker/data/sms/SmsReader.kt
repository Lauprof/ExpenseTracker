package com.example.expensetracker.data.sms

import android.content.Context
import android.net.Uri
import android.provider.Telephony

data class RawSmsMessage(
    val id: String,
    val sender: String,
    val body: String,
    val timestamp: Long
)

class SmsReader(private val context: Context) {

    fun readFinancialSms(sinceTimestamp: Long = 0L): List<RawSmsMessage> {
        val messages = mutableListOf<RawSmsMessage>()
        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        val selection = if (sinceTimestamp > 0L) "${Telephony.Sms.DATE} > ?" else null
        val selectionArgs = if (sinceTimestamp > 0L) arrayOf(sinceTimestamp.toString()) else null

        val financialKeywords = listOf(
            "mpesa", "m-pesa", "airtel", "t-kash", "ksh", "kes", "paid to", "sent to", "received", "transferred",
            "equity", "kcb", "coop", "co-op", "absa", "stanbic", "ncba", "family bank", "dtb", "standard chartered",
            "sacco", "bank", "account", "debit", "credit", "deposit", "withdrawal", "balance", "paid", "spent", "pesalink", "rtgs", "eft"
        )

        val sortOrder = "${Telephony.Sms.DATE} DESC"

        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex) ?: ""
                val address = cursor.getString(addressIndex) ?: "UNKNOWN"
                val body = cursor.getString(bodyIndex) ?: ""
                val date = cursor.getLong(dateIndex)

                val bodyLower = body.lowercase()
                val addressLower = address.lowercase()

                val isFinancial = financialKeywords.any { keyword ->
                    addressLower.contains(keyword) || bodyLower.contains(keyword)
                }

                if (isFinancial) {
                    messages.add(
                        RawSmsMessage(
                            id = id,
                            sender = address,
                            body = body,
                            timestamp = date
                        )
                    )
                }
            }
        }
        return messages
    }
}
