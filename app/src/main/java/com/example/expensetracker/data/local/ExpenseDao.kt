package com.example.expensetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpenses(expenses: List<ExpenseEntity>)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses ORDER BY dateTimestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE type = :type ORDER BY dateTimestamp DESC")
    fun getExpensesByType(type: String): Flow<List<ExpenseEntity>>

    @Query("SELECT category, SUM(amount) as totalAmount, COUNT(*) as count FROM expenses WHERE type = 'EXPENSE' GROUP BY category ORDER BY totalAmount DESC")
    fun getCategoryTotals(): Flow<List<CategoryTotal>>

    @Query("SELECT SUM(amount) FROM expenses WHERE type = 'EXPENSE'")
    fun getTotalSpending(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE type = 'INCOME'")
    fun getTotalIncome(): Flow<Double?>

    @Query("SELECT transactionCode FROM expenses")
    suspend fun getAllTransactionCodes(): List<String>

    @Query("SELECT balanceAfter FROM expenses WHERE source = 'M-PESA' AND balanceAfter IS NOT NULL ORDER BY dateTimestamp DESC, id DESC LIMIT 1")
    fun getLatestMpesaBalance(): Flow<Double?>

    /** Rows not yet paired as transfers, candidates for matching. */
    @Query("SELECT * FROM expenses WHERE linkedCode IS NULL AND parseMethod != 'MANUAL' AND dateTimestamp >= :since")
    suspend fun getUnlinkedSince(since: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE transactionCode IN (:codes)")
    suspend fun getByCodes(codes: List<String>): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE source = :source AND balanceAfter IS NOT NULL ORDER BY dateTimestamp ASC, id ASC")
    suspend fun getWithBalance(source: String): List<ExpenseEntity>

    @Query("SELECT COUNT(*) FROM expenses WHERE needsReview = 1")
    suspend fun countNeedsReview(): Int

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses")
    suspend fun clearAll()
}
