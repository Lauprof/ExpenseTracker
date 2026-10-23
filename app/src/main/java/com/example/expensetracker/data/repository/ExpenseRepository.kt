package com.example.expensetracker.data.repository

import android.content.Context
import com.example.expensetracker.data.local.CategoryTotal
import com.example.expensetracker.data.local.ExpenseDao
import com.example.expensetracker.data.local.ExpenseEntity
import com.example.expensetracker.data.local.SpecialCategories
import com.example.expensetracker.data.parser.HybridTransactionParser
import com.example.expensetracker.data.reconcile.BalanceChecker
import com.example.expensetracker.data.reconcile.TransferMatcher
import com.example.expensetracker.data.sms.SmsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ExpenseRepository(
    context: Context,
    private val expenseDao: ExpenseDao
) {
    private val smsReader = SmsReader(context)
    private val hybridParser = HybridTransactionParser(context)

    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
    val categoryTotals: Flow<List<CategoryTotal>> = expenseDao.getCategoryTotals()
    val totalSpending: Flow<Double?> = expenseDao.getTotalSpending()
    val totalIncome: Flow<Double?> = expenseDao.getTotalIncome()
    val mpesaBalance: Flow<Double?> = expenseDao.getLatestMpesaBalance()

    suspend fun syncSms(sinceTimestamp: Long = 0L, onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }): SyncResult = withContext(Dispatchers.IO) {
        val messages = smsReader.readFinancialSms(sinceTimestamp)
        if (messages.isEmpty()) return@withContext SyncResult(0, 0, 0)

        val seenCodes = expenseDao.getAllTransactionCodes().toMutableSet()
        val batch = mutableListOf<ExpenseEntity>()
        var newCount = 0
        val total = messages.size

        for ((index, msg) in messages.withIndex()) {
            onProgress(index + 1, total)
            val parsed = hybridParser.parseMessage(msg) ?: continue
            if (!seenCodes.add(parsed.transactionCode)) continue

            batch.add(parsed)
            newCount++

            // The transaction cost is a real expense even when the main row is a transfer
            if (parsed.fee > 0) {
                val feeCode = "${parsed.transactionCode}_FEE"
                if (seenCodes.add(feeCode)) batch.add(feeRow(parsed, feeCode))
            }
        }

        if (batch.isNotEmpty()) {
            expenseDao.insertExpenses(batch)
            reconcile(batch)
        }

        val gaps = BalanceChecker.findGaps(expenseDao.getWithBalance("M-PESA")).size
        SyncResult(newCount, gaps, expenseDao.countNeedsReview())
    }

    private fun feeRow(parent: ExpenseEntity, code: String) = ExpenseEntity(
        transactionCode = code,
        amount = parent.fee,
        category = SpecialCategories.FEES,
        recipient = "${parent.source} charges",
        type = "EXPENSE",
        dateTimestamp = parent.dateTimestamp,
        rawSmsBody = parent.rawSmsBody,
        parseMethod = parent.parseMethod,
        sender = parent.sender,
        source = parent.source,
        direction = "OUT"
    )

    /** Cancels reversed originals, then pairs transfers between the user's own accounts. */
    private suspend fun reconcile(newRows: List<ExpenseEntity>) {
        applyReversals(newRows)

        val since = newRows.minOf { it.dateTimestamp } - TransferMatcher.WINDOW_MS
        val candidates = expenseDao.getUnlinkedSince(since)

        val paired = TransferMatcher.match(candidates)
        if (paired.isNotEmpty()) expenseDao.updateExpenses(paired)

        val pairedCodes = paired.map { it.transactionCode }.toSet()
        val flagged = TransferMatcher.flagUnmatched(
            candidates.filter { it.transactionCode !in pairedCodes },
            System.currentTimeMillis()
        )
        if (flagged.isNotEmpty()) expenseDao.updateExpenses(flagged)
    }

    private suspend fun applyReversals(newRows: List<ExpenseEntity>) {
        val reversals = newRows.filter { it.category == SpecialCategories.REVERSAL && it.linkedCode != null }
        if (reversals.isEmpty()) return

        val reversalByOriginal = reversals.associateBy { it.linkedCode!! }
        val originals = reversalByOriginal.keys.toList().chunked(500)
            .flatMap { expenseDao.getByCodes(it) }

        val updated = originals.map {
            it.copy(
                type = "REVERSED",
                category = SpecialCategories.REVERSED,
                linkedCode = reversalByOriginal[it.transactionCode]?.transactionCode
            )
        }
        if (updated.isNotEmpty()) expenseDao.updateExpenses(updated)
    }

    suspend fun insertExpenses(expenses: List<ExpenseEntity>) = withContext(Dispatchers.IO) {
        expenseDao.insertExpenses(expenses)
    }

    suspend fun isLlmAvailable(): Boolean {
        return hybridParser.isLlmAvailable()
    }

    suspend fun addManualExpense(
        amount: Double,
        category: String,
        recipient: String,
        type: String,
        rawNotes: String
    ) = withContext(Dispatchers.IO) {
        val entity = ExpenseEntity(
            transactionCode = "MANUAL_${System.currentTimeMillis()}",
            amount = amount,
            category = category,
            recipient = recipient,
            type = type,
            dateTimestamp = System.currentTimeMillis(),
            rawSmsBody = rawNotes,
            parseMethod = "MANUAL",
            sender = "User Input",
            direction = if (type == "INCOME") "IN" else "OUT"
        )
        expenseDao.insertExpense(entity)
    }

    suspend fun deleteExpense(expense: ExpenseEntity) = withContext(Dispatchers.IO) {
        expenseDao.deleteExpense(expense)
    }

    suspend fun updateExpense(expense: ExpenseEntity) = withContext(Dispatchers.IO) {
        expenseDao.updateExpense(expense)
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        expenseDao.clearAll()
    }
}

data class SyncResult(
    val newCount: Int,
    val balanceGaps: Int,
    val needsReview: Int
)
