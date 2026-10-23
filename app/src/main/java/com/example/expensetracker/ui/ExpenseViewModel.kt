package com.example.expensetracker.ui

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.backup.ExpenseBackupManager
import com.example.expensetracker.data.local.CategoryTotal
import com.example.expensetracker.data.local.ExpenseDatabase
import com.example.expensetracker.data.local.ExpenseEntity
import com.example.expensetracker.data.local.PreferencesManager
import com.example.expensetracker.data.repository.ExpenseRepository
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChartType {
    DONUT, BAR, LINE
}

enum class TimePeriod(val label: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

data class ExpenseUiState(
    val expenses: List<ExpenseEntity> = emptyList(),
    val filteredExpenses: List<ExpenseEntity> = emptyList(),
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val totalSpending: Double = 0.0,
    val totalIncome: Double = 0.0,
    val mpesaBalance: Double? = null,
    val syncProgressText: String? = null,
    val isSyncing: Boolean = false,
    val isLlmAvailable: Boolean = false,
    val selectedCategory: String? = null,
    val selectedTypeFilter: String? = null,
    val selectedTimePeriod: TimePeriod = TimePeriod.MONTHLY,
    val searchQuery: String = "",
    val syncMessage: String? = null,
    val chartType: ChartType = ChartType.DONUT,
    val isPrivacyAccepted: Boolean = false,
    val isAppOnboardingShown: Boolean = false
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExpenseRepository
    private val preferencesManager = PreferencesManager(application)
    private val backupManager = ExpenseBackupManager(application)
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(application)

    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _selectedTypeFilter = MutableStateFlow<String?>(null)
    private val _selectedTimePeriod = MutableStateFlow(TimePeriod.MONTHLY)
    private val _searchQuery = MutableStateFlow("")
    private val _isSyncing = MutableStateFlow(false)
    private val _syncProgressText = MutableStateFlow<String?>(null)
    private val _isLlmAvailable = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)
    private val _chartType = MutableStateFlow(ChartType.DONUT)
    private val _isPrivacyAccepted = MutableStateFlow(preferencesManager.isPrivacyPolicyAccepted)
    private val _isAppOnboardingShown = MutableStateFlow(preferencesManager.isAppOnboardingShown)

    val uiState: StateFlow<ExpenseUiState>

    init {
        val database = ExpenseDatabase.getInstance(application)
        repository = ExpenseRepository(application, database.expenseDao())

        checkLlmStatus()

        uiState = combine(
            repository.allExpenses,
            repository.mpesaBalance,
            _syncProgressText,
            _selectedCategory,
            _selectedTypeFilter,
            _selectedTimePeriod,
            _searchQuery,
            _isSyncing,
            _isLlmAvailable,
            _syncMessage,
            _chartType,
            _isPrivacyAccepted,
            _isAppOnboardingShown
        ) { args ->
            val allList = args[0] as List<ExpenseEntity>
            val latestMpesaBal = args[1] as? Double
            val syncProgress = args[2] as? String
            val selCat = args[3] as? String
            val selType = args[4] as? String
            val timePeriod = args[5] as TimePeriod
            val query = args[6] as String
            val syncing = args[7] as Boolean
            val llmAvail = args[8] as Boolean
            val message = args[9] as? String
            val chart = args[10] as ChartType
            val privacyAccepted = args[11] as Boolean
            val onboardingShown = args[12] as Boolean

            val cutoffTime = getCutoffTimestamp(timePeriod)
            val periodExpenses = if (cutoffTime <= 0) {
                allList
            } else {
                allList.filter { it.dateTimestamp >= cutoffTime }
            }

            val totalSpend = periodExpenses.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val totalInc = periodExpenses.filter { it.type == "INCOME" }.sumOf { it.amount }

            val catTotals = periodExpenses
                .filter { it.type == "EXPENSE" }
                .groupBy { it.category }
                .map { (cat, list) ->
                    CategoryTotal(category = cat, totalAmount = list.sumOf { it.amount }, count = list.size)
                }
                .sortedByDescending { it.totalAmount }

            val typeFiltered = if (selType.isNullOrBlank()) {
                periodExpenses
            } else {
                periodExpenses.filter { it.type.equals(selType, ignoreCase = true) }
            }

            val categoryFiltered = if (selCat.isNullOrBlank()) {
                typeFiltered
            } else {
                typeFiltered.filter { it.category.equals(selCat, ignoreCase = true) }
            }

            val finalFiltered = if (query.isBlank()) {
                categoryFiltered
            } else {
                categoryFiltered.filter {
                    it.recipient.contains(query, ignoreCase = true) ||
                            it.category.contains(query, ignoreCase = true) ||
                            it.source.contains(query, ignoreCase = true) ||
                            it.rawSmsBody.contains(query, ignoreCase = true) ||
                            it.amount.toString().contains(query)
                }
            }

            ExpenseUiState(
                expenses = allList,
                filteredExpenses = finalFiltered,
                categoryTotals = catTotals,
                totalSpending = totalSpend,
                totalIncome = totalInc,
                mpesaBalance = latestMpesaBal,
                syncProgressText = syncProgress,
                isSyncing = syncing,
                isLlmAvailable = llmAvail,
                selectedCategory = selCat,
                selectedTypeFilter = selType,
                selectedTimePeriod = timePeriod,
                searchQuery = query,
                syncMessage = message,
                chartType = chart,
                isPrivacyAccepted = privacyAccepted,
                isAppOnboardingShown = onboardingShown
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ExpenseUiState(
                isPrivacyAccepted = preferencesManager.isPrivacyPolicyAccepted,
                isAppOnboardingShown = preferencesManager.isAppOnboardingShown
            )
        )
    }

    private fun getCutoffTimestamp(period: TimePeriod): Long {
        val cal = Calendar.getInstance()
        return when (period) {
            TimePeriod.WEEKLY -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.timeInMillis
            }
            TimePeriod.MONTHLY -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
                cal.timeInMillis
            }
            TimePeriod.YEARLY -> {
                cal.add(Calendar.DAY_OF_YEAR, -365)
                cal.timeInMillis
            }
        }
    }

    fun selectTimePeriod(period: TimePeriod) {
        _selectedTimePeriod.value = period
        firebaseAnalytics.logEvent("time_period_changed", Bundle().apply {
            putString("period", period.name)
        })
    }

    fun completeOnboarding(fullName: String = "", phoneNumber: String = "") {
        if (fullName.isNotBlank()) preferencesManager.userFullName = fullName.trim()
        if (phoneNumber.isNotBlank()) preferencesManager.userPhoneNumber = phoneNumber.trim()
        preferencesManager.isAppOnboardingShown = true
        _isAppOnboardingShown.value = true
        firebaseAnalytics.logEvent("onboarding_completed", null)
    }

    fun selectTypeFilter(type: String?) {
        _selectedTypeFilter.value = if (_selectedTypeFilter.value == type) null else type
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun exportBackup() {
        viewModelScope.launch {
            try {
                val file = backupManager.exportToJsonFile(uiState.value.expenses)
                backupManager.shareBackupFile(file)
                _syncMessage.value = "Backup exported successfully!"
                firebaseAnalytics.logEvent("backup_exported", null)
            } catch (e: Exception) {
                _syncMessage.value = "Export failed: ${e.localizedMessage}"
            }
        }
    }

    fun importBackup(jsonString: String) {
        viewModelScope.launch {
            try {
                val importedList = backupManager.parseJsonBackup(jsonString)
                if (importedList.isNotEmpty()) {
                    repository.insertExpenses(importedList)
                    _syncMessage.value = "Successfully restored ${importedList.size} records!"
                    firebaseAnalytics.logEvent("backup_imported", Bundle().apply {
                        putInt("record_count", importedList.size)
                    })
                }
            } catch (e: Exception) {
                _syncMessage.value = "Import failed: ${e.localizedMessage}"
            }
        }
    }

    fun acceptPrivacyPolicy() {
        preferencesManager.isPrivacyPolicyAccepted = true
        _isPrivacyAccepted.value = true
        firebaseAnalytics.logEvent("privacy_policy_accepted", null)
    }

    fun dismissPrivacyPolicy() {
        preferencesManager.isPrivacyPolicyAccepted = true
        _isPrivacyAccepted.value = true
        firebaseAnalytics.logEvent("privacy_policy_dismissed", null)
    }

    fun syncSms(forceFullSync: Boolean = false) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncProgressText.value = "Scanning SMS inbox in background..."
            firebaseAnalytics.logEvent("sms_sync_triggered", null)
            try {
                // Enqueue in WorkManager so parsing continues running even if the user minimizes or exits the app UI
                com.example.expensetracker.data.sms.SmsSyncWorker.enqueueImmediateSync(getApplication(), forceFullSync)

                val since = if (forceFullSync) 0L else preferencesManager.lastSyncTimestamp
                val syncTime = System.currentTimeMillis()
                val result = repository.syncSms(sinceTimestamp = since) { current, total ->
                    _syncProgressText.value = "Parsing message $current of $total..."
                }
                preferencesManager.lastSyncTimestamp = syncTime
                val base = if (result.newCount > 0) "Successfully imported ${result.newCount} new transactions!" else "No new SMS transactions found."
                val notes = buildList {
                    if (result.balanceGaps > 0) add("${result.balanceGaps} possible missing M-Pesa SMS (balance mismatch)")
                    if (result.needsReview > 0) add("${result.needsReview} to review")
                }
                _syncMessage.value = if (notes.isEmpty()) base else "$base ${notes.joinToString("; ")}."
                firebaseAnalytics.logEvent("sms_sync_success", Bundle().apply {
                    putInt("new_expenses", result.newCount)
                })
            } catch (e: Exception) {
                _syncMessage.value = "Error syncing SMS: ${e.localizedMessage}"
                firebaseAnalytics.logEvent("sms_sync_error", Bundle().apply {
                    putString("error_message", e.localizedMessage ?: "unknown")
                })
            } finally {
                _isSyncing.value = false
                _syncProgressText.value = null
            }
        }
    }

    fun checkLlmStatus() {
        viewModelScope.launch {
            _isLlmAvailable.value = repository.isLlmAvailable()
        }
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun toggleChartType() {
        _chartType.update {
            when (it) {
                ChartType.DONUT -> ChartType.BAR
                ChartType.BAR -> ChartType.LINE
                ChartType.LINE -> ChartType.DONUT
            }
        }
    }

    fun addManualExpense(amount: Double, category: String, recipient: String, type: String, notes: String) {
        viewModelScope.launch {
            repository.addManualExpense(amount, category, recipient, type, notes)
            _syncMessage.value = "Manual expense logged successfully."
            firebaseAnalytics.logEvent("manual_expense_added", Bundle().apply {
                putString("category", category)
                putDouble("amount", amount)
            })
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            firebaseAnalytics.logEvent("expense_deleted", null)
        }
    }

    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.updateExpense(expense)
            _syncMessage.value = "Transaction updated successfully."
            firebaseAnalytics.logEvent("expense_updated", null)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            _syncMessage.value = "All transaction data cleared successfully."
            firebaseAnalytics.logEvent("all_data_cleared", null)
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }
}
