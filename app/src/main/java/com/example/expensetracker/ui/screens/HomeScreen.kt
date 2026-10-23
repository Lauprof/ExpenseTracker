package com.example.expensetracker.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MultilineChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.local.PreferencesManager
import com.example.expensetracker.data.parser.ModelDownloadManager
import com.example.expensetracker.data.pdf.PdfReportGenerator
import com.example.expensetracker.ui.ChartType
import com.example.expensetracker.ui.ExpenseViewModel
import com.example.expensetracker.ui.TimePeriod
import com.example.expensetracker.ui.components.AddExpenseDialog
import com.example.expensetracker.ui.components.CategoryBarChart
import com.example.expensetracker.ui.components.CategoryPieChart
import com.example.expensetracker.ui.components.ModelDownloadDialog
import com.example.expensetracker.ui.components.PrivacyPolicyDialog
import com.example.expensetracker.ui.components.ProfileSettingsDialog
import com.example.expensetracker.ui.components.SpendingLineChart
import com.example.expensetracker.ui.components.TransactionItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ExpenseViewModel,
    hasSmsPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyModal by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showModelDownloadDialog by remember { mutableStateOf(false) }
    var showClearAllConfirmation by remember { mutableStateOf(false) }
    val modelDownloadManager = remember { ModelDownloadManager(context) }
    var showAiBanner by remember { mutableStateOf(!modelDownloadManager.isModelDownloaded()) }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val jsonString = stream.bufferedReader().use { reader -> reader.readText() }
                    viewModel.importBackup(jsonString)
                }
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    LaunchedEffect(uiState.syncMessage) {
        uiState.syncMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSyncMessage()
        }
    }
    var showOverflowMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "Expense Tracker",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "M-Pesa & Bank Records",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        LlmStatusChip(
                            isAvailable = uiState.isLlmAvailable,
                            onClick = { showModelDownloadDialog = true }
                        )
                    }
                },
                actions = {
                    // Sync (primary action)
                    IconButton(
                        onClick = {
                            if (!uiState.isPrivacyAccepted) {
                                showPrivacyPolicyModal = true
                            } else if (hasSmsPermission) {
                                viewModel.syncSms()
                            } else {
                                onRequestPermission()
                            }
                        },
                        enabled = !uiState.isSyncing
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Sync SMS")
                        }
                    }

                    // Profile
                    IconButton(onClick = { showProfileDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "My Profile Details",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Overflow menu
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export PDF Report") },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    PdfReportGenerator(context).generateAndSharePdfReport(
                                        uiState.filteredExpenses,
                                        uiState.selectedTimePeriod.label
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Backup JSON Data") },
                                leadingIcon = { Icon(Icons.Default.Save, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.exportBackup()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Restore JSON Backup") },
                                leadingIcon = { Icon(Icons.Default.Restore, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    restoreLauncher.launch(arrayOf("application/json", "*/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Full Rescan SMS History") },
                                leadingIcon = { Icon(Icons.Default.Sync, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    if (hasSmsPermission) {
                                        viewModel.syncSms(forceFullSync = true)
                                    } else {
                                        onRequestPermission()
                                    }
                                }
                            )
                            HorizontalDivider()
                            val prefsManager = remember { PreferencesManager(context) }
                            var appLockEnabled by remember { mutableStateOf(prefsManager.isAppLockEnabled) }
                            DropdownMenuItem(
                                text = { Text(if (appLockEnabled) "Disable Biometric Lock" else "Enable Biometric Lock") },
                                leadingIcon = { Icon(Icons.Default.Security, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    appLockEnabled = !appLockEnabled
                                    prefsManager.isAppLockEnabled = appLockEnabled
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear All Data") },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showOverflowMenu = false
                                    showClearAllConfirmation = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("On-Device AI Model (~350 MB)") },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showModelDownloadDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Privacy Policy") },
                                leadingIcon = { Icon(Icons.Default.Security, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showPrivacyPolicyModal = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Log Expense"
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.isSyncing) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = uiState.syncProgressText ?: "Parsing messages...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (showAiBanner && !modelDownloadManager.isModelDownloaded()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🚀 Enable Offline AI Parser",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Download the ~350 MB local AI model for 100% private, smart receipt parsing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Button(onClick = { showModelDownloadDialog = true }) {
                                    Text(text = "Download", fontSize = 12.sp)
                                }
                                TextButton(onClick = { showAiBanner = false }) {
                                    Text(text = "Dismiss", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (!hasSmsPermission) {
                item {
                    PermissionBanner(onRequestPermission = {
                        if (!uiState.isPrivacyAccepted) {
                            showPrivacyPolicyModal = true
                        } else {
                            onRequestPermission()
                        }
                    })
                }
            }

            // Search Bar for transactions
            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search merchant, name, amount...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Time Period Selector Row (Weekly, Monthly, Yearly)
            item {
                Column {
                    TimePeriodSelectorRow(
                        selectedPeriod = uiState.selectedTimePeriod,
                        onPeriodSelected = { viewModel.selectTimePeriod(it) }
                    )
                    val dateRangeText = rememberDateRangeText(uiState.selectedTimePeriod)
                    Text(
                        text = dateRangeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp)
                    )
                }
            }

            // Overview Summary Cards (Clickable to filter Expenses or Income)
            item {
                SummarySection(
                    totalSpending = uiState.totalSpending,
                    totalIncome = uiState.totalIncome,
                    mpesaBalance = uiState.mpesaBalance,
                    periodLabel = uiState.selectedTimePeriod.label,
                    selectedType = uiState.selectedTypeFilter,
                    onSelectType = { viewModel.selectTypeFilter(it) }
                )
            }

            // Chart Section
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (uiState.chartType) {
                                ChartType.DONUT -> "Category Breakdown (${uiState.selectedTimePeriod.label})"
                                ChartType.BAR -> "Category Comparison (${uiState.selectedTimePeriod.label})"
                                ChartType.LINE -> "Market Spending Trend (${uiState.selectedTimePeriod.label})"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(onClick = { viewModel.toggleChartType() }) {
                            Icon(
                                imageVector = when (uiState.chartType) {
                                    ChartType.DONUT -> Icons.Default.BarChart
                                    ChartType.BAR -> Icons.AutoMirrored.Filled.MultilineChart
                                    ChartType.LINE -> Icons.Default.PieChart
                                },
                                contentDescription = "Toggle Chart Type",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    when (uiState.chartType) {
                        ChartType.DONUT -> {
                            CategoryPieChart(
                                categoryTotals = uiState.categoryTotals,
                                totalSpend = uiState.totalSpending,
                                selectedCategory = uiState.selectedCategory,
                                onCategorySelect = { viewModel.selectCategory(it) }
                            )
                        }
                        ChartType.BAR -> {
                            CategoryBarChart(
                                categoryTotals = uiState.categoryTotals,
                                selectedCategory = uiState.selectedCategory,
                                onCategorySelect = { viewModel.selectCategory(it) }
                            )
                        }
                        ChartType.LINE -> {
                            SpendingLineChart(
                                expenses = uiState.filteredExpenses
                            )
                        }
                    }
                }
            }

            // Category Filter Chips
            item {
                CategoryFilterRow(
                    selectedCategory = uiState.selectedCategory,
                    onSelectCategory = { viewModel.selectCategory(it) }
                )
            }

            // List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val filterTitle = when (uiState.selectedTypeFilter) {
                        "EXPENSE" -> "Expenses Only"
                        "INCOME" -> "Income Only"
                        else -> "Transactions"
                    }
                    Text(
                        text = "$filterTitle (${uiState.filteredExpenses.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (uiState.selectedCategory != null || uiState.searchQuery.isNotEmpty() || uiState.selectedTypeFilter != null) {
                        Text(
                            text = "Clear filters",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    viewModel.selectCategory(null)
                                    viewModel.setSearchQuery("")
                                    viewModel.selectTypeFilter(uiState.selectedTypeFilter) // toggles off
                                }
                        )
                    }
                }
            }

            // Transaction List
            if (uiState.filteredExpenses.isEmpty()) {
                item {
                    EmptyStateView(
                        hasSmsPermission = hasSmsPermission,
                        onSyncClick = {
                            if (!uiState.isPrivacyAccepted) {
                                showPrivacyPolicyModal = true
                            } else if (hasSmsPermission) {
                                viewModel.syncSms()
                            } else {
                                onRequestPermission()
                            }
                        }
                    )
                }
            } else {
                items(
                    items = uiState.filteredExpenses,
                    key = { it.id }
                ) { expense ->
                    TransactionItem(
                        expense = expense,
                        onDelete = { viewModel.deleteExpense(it) },
                        onEdit = { viewModel.updateExpense(it) }
                    )
                }
            }
        }

        if (showProfileDialog) {
            ProfileSettingsDialog(
                preferencesManager = remember { PreferencesManager(context) },
                onDismiss = { showProfileDialog = false }
            )
        }

        if (showModelDownloadDialog) {
            ModelDownloadDialog(
                downloadManager = modelDownloadManager,
                onDismiss = {
                    showModelDownloadDialog = false
                    viewModel.checkLlmStatus()
                }
            )
        }

        if (showClearAllConfirmation) {
            AlertDialog(
                onDismissRequest = { showClearAllConfirmation = false },
                title = { Text("Clear All Data") },
                text = { Text("Are you sure you want to delete all loaded transaction records? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearAllConfirmation = false
                            viewModel.clearAllData()
                        }
                    ) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // First time launch or manually opened Privacy Policy Dialog
        if (!uiState.isPrivacyAccepted || showPrivacyPolicyModal) {
            PrivacyPolicyDialog(
                onAccept = {
                    viewModel.acceptPrivacyPolicy()
                    showPrivacyPolicyModal = false
                    if (!hasSmsPermission) {
                        onRequestPermission()
                    }
                },
                onDecline = {
                    viewModel.dismissPrivacyPolicy()
                    showPrivacyPolicyModal = false
                },
                showDeclineButton = !uiState.isPrivacyAccepted
            )
        }

        if (showAddDialog) {
            AddExpenseDialog(
                onDismiss = { showAddDialog = false },
                onAddExpense = { amount, category, recipient, type, notes ->
                    viewModel.addManualExpense(amount, category, recipient, type, notes)
                }
            )
        }
    }
}

@Composable
fun rememberDateRangeText(period: TimePeriod): String {
    val cal = Calendar.getInstance()
    val endDate = Date(cal.timeInMillis)
    val endStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(endDate)

    cal.time = Date()
    when (period) {
        TimePeriod.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, -7)
        TimePeriod.MONTHLY -> cal.add(Calendar.DAY_OF_YEAR, -30)
        TimePeriod.YEARLY -> cal.add(Calendar.DAY_OF_YEAR, -365)
    }
    val startDate = Date(cal.timeInMillis)
    val startStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(startDate)
    return "📅 Period: $startStr – $endStr"
}

@Composable
fun TimePeriodSelectorRow(
    selectedPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth()
    ) {
        TimePeriod.entries.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selectedPeriod,
                onClick = { onPeriodSelected(period) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = TimePeriod.entries.size)
            ) {
                Text(text = period.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun LlmStatusChip(
    isAvailable: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isAvailable) Color(0xFFE8F5E9) else Color(0xFFE3F2FD)
    val contentColor = if (isAvailable) Color(0xFF2E7D32) else Color(0xFF1565C0)
    val label = if (isAvailable) "On-Device AI" else "Local Engine"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = Modifier
            .padding(end = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(contentColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
fun PermissionBanner(onRequestPermission: () -> Unit) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Restricted Settings (Android 13/14/15/16)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "To enable SMS: Tap 'Settings' -> Tap the 3 dots (top-right) -> 'Allow restricted settings' -> then allow SMS.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = onRequestPermission) {
                    Text(text = "Grant", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text(text = "Settings", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun SummarySection(
    totalSpending: Double,
    totalIncome: Double,
    mpesaBalance: Double?,
    periodLabel: String,
    selectedType: String?,
    onSelectType: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (mpesaBalance != null && mpesaBalance > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE8F5E9)
                ),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Latest M-Pesa Balance",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF1B5E20),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ksh ${String.format(Locale.getDefault(), "%,.2f", mpesaBalance)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFC8E6C9)
                    ) {
                        Text(
                            text = "M-PESA WALLET",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectType("EXPENSE") },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedType == "EXPENSE") Color(0xFFFFCDD2) else Color(0xFFFFEBEE)
                ),
                elevation = CardDefaults.cardElevation(if (selectedType == "EXPENSE") 4.dp else 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "$periodLabel Expenses",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFC62828)
                    )
                    Text(
                        text = "Ksh ${String.format(Locale.getDefault(), "%,.0f", totalSpending)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB71C1C)
                    )
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectType("INCOME") },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedType == "INCOME") Color(0xFFC8E6C9) else Color(0xFFE8F5E9)
                ),
                elevation = CardDefaults.cardElevation(if (selectedType == "INCOME") 4.dp else 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "$periodLabel Income",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF2E7D32)
                    )
                    Text(
                        text = "Ksh ${String.format(Locale.getDefault(), "%,.0f", totalIncome)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryFilterRow(
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit
) {
    val categories = listOf(
        "All", "Shopping", "Food & Dining", "Utilities & Bills",
        "Investments & Savings", "Bank Transfer", "Transfers & Out", "Travel & Transport", "Health & Medical", "Entertainment"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        categories.forEach { cat ->
            val isSelected = (cat == "All" && selectedCategory == null) || (cat == selectedCategory)
            FilterChip(
                selected = isSelected,
                onClick = {
                    onSelectCategory(if (cat == "All") null else cat)
                },
                label = { Text(text = cat, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
fun EmptyStateView(
    hasSmsPermission: Boolean,
    onSyncClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No expenses parsed yet",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hasSmsPermission) "Tap 'Sync SMS' above to scan your inbox." else "Grant SMS permission to import M-Pesa & Bank receipts.",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onSyncClick) {
                Text(text = if (hasSmsPermission) "Sync SMS Inbox" else "Grant Permission")
            }
        }
    }
}
