package com.example.expensetracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["transactionCode"], unique = true)]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionCode: String,
    val amount: Double,
    val category: String,
    val recipient: String,
    val type: String, // "EXPENSE", "INCOME", "TRANSFER", "REVERSED"
    val dateTimestamp: Long,
    val rawSmsBody: String,
    val parseMethod: String, // "REGEX", "LLM", "MANUAL"
    val sender: String,
    val source: String = "M-PESA", // "M-PESA", "EQUITY BANK", "KCB BANK", "CO-OP BANK", etc.
    @ColumnInfo(defaultValue = "0")
    val fee: Double = 0.0,                 // transaction cost taken from the same SMS
    val balanceAfter: Double? = null,      // balance reported in the SMS, if any
    @ColumnInfo(defaultValue = "'OUT'")
    val direction: String = "OUT",         // "IN" or "OUT", independent of type
    val linkedCode: String? = null,        // transfer counterpart / reversed original
    @ColumnInfo(defaultValue = "0")
    val needsReview: Boolean = false
)

data class CategoryTotal(
    val category: String,
    val totalAmount: Double,
    val count: Int
)
