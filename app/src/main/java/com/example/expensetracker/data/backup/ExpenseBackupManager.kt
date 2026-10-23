package com.example.expensetracker.data.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.expensetracker.data.local.ExpenseEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ExpenseBackupManager(private val context: Context) {

    fun exportToJsonFile(expenses: List<ExpenseEntity>): File {
        val jsonArray = JSONArray()
        for (exp in expenses) {
            val obj = JSONObject().apply {
                put("transactionCode", exp.transactionCode)
                put("amount", exp.amount)
                put("category", exp.category)
                put("recipient", exp.recipient)
                put("type", exp.type)
                put("dateTimestamp", exp.dateTimestamp)
                put("rawSmsBody", exp.rawSmsBody)
                put("parseMethod", exp.parseMethod)
                put("sender", exp.sender)
                put("source", exp.source)
            }
            jsonArray.put(obj)
        }

        val file = File(context.cacheDir, "Expense_Tracker_Backup_${System.currentTimeMillis()}.json")
        FileOutputStream(file).use {
            it.write(jsonArray.toString(2).toByteArray())
        }
        return file
    }

    fun shareBackupFile(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Export Expense JSON Backup").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun parseJsonBackup(jsonString: String): List<ExpenseEntity> {
        val list = mutableListOf<ExpenseEntity>()
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                ExpenseEntity(
                    transactionCode = obj.optString("transactionCode", "MANUAL_${System.currentTimeMillis()}_$i"),
                    amount = obj.optDouble("amount", 0.0),
                    category = obj.optString("category", "General & Other"),
                    recipient = obj.optString("recipient", "Unknown"),
                    type = obj.optString("type", "EXPENSE"),
                    dateTimestamp = obj.optLong("dateTimestamp", System.currentTimeMillis()),
                    rawSmsBody = obj.optString("rawSmsBody", ""),
                    parseMethod = obj.optString("parseMethod", "MANUAL"),
                    sender = obj.optString("sender", "Backup"),
                    source = obj.optString("source", "M-PESA")
                )
            )
        }
        return list
    }
}
