package com.example.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.local.ExpenseEntity
import com.example.expensetracker.data.local.SpecialCategories
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TransactionItem(
    expense: ExpenseEntity,
    onDelete: (ExpenseEntity) -> Unit,
    onEdit: (ExpenseEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = getCategoryColor(expense.category)
    val icon = getCategoryIcon(expense.category)
    val formattedDate = rememberFormattedDate(expense.dateTimestamp)
    var showEditDialog by remember { mutableStateOf(false) }

    val displayRecipient = when {
        expense.category == SpecialCategories.BANK_TRANSFER && (expense.recipient.isBlank() || expense.recipient.contains("Bank Transfer", ignoreCase = true)) -> {
            if (expense.direction == "OUT") "Self Transfer (Out from ${expense.source})" else "Self Transfer (In to ${expense.source})"
        }
        else -> expense.recipient
    }

    val detailText = "${expense.category} • $formattedDate"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = expense.category,
                    tint = categoryColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = displayRecipient,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = detailText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    SourceBadge(source = expense.source)

                    Spacer(modifier = Modifier.width(4.dp))

                    ParseBadge(method = expense.parseMethod)
                }
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                val isIncome = expense.type == "INCOME"
                val isTransfer = expense.type == "TRANSFER"
                val isReversed = expense.type == "REVERSED"
                val amountPrefix = when {
                    isIncome -> "+ Ksh "
                    isTransfer -> "⇄ Ksh "
                    isReversed -> "↺ Ksh "
                    else -> "- Ksh "
                }
                val amountColor = when {
                    isIncome -> Color(0xFF2E7D32)
                    isTransfer -> Color(0xFF0288D1)
                    isReversed -> Color(0xFF757575)
                    else -> MaterialTheme.colorScheme.error
                }

                Text(
                    text = "$amountPrefix${String.format(Locale.getDefault(), "%,.2f", expense.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    IconButton(
                        onClick = { onDelete(expense) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        EditTransactionDialog(
            expense = expense,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                onEdit(updated)
                showEditDialog = false
            }
        )
    }
}

@Composable
fun SourceBadge(source: String) {
    val sourceUpper = source.uppercase()
    val (bgColor, textColor) = when {
        sourceUpper.contains("EQUITY") -> Pair(Color(0xFFE3F2FD), Color(0xFF0D47A1))
        sourceUpper.contains("KCB") -> Pair(Color(0xFFFFFDE7), Color(0xFFF57F17))
        sourceUpper.contains("CO-OP") || sourceUpper.contains("COOP") -> Pair(Color(0xFFEDE7F6), Color(0xFF4527A0))
        sourceUpper.contains("MPESA") || sourceUpper.contains("M-PESA") -> Pair(Color(0xFFE8F5E9), Color(0xFF1B5E20))
        else -> Pair(Color(0xFFECEFF1), Color(0xFF37474F))
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor
    ) {
        Text(
            text = sourceUpper,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun ParseBadge(method: String) {
    val (bgColor, textColor, label, icon) = when (method.uppercase()) {
        "LLM" -> Quadruple(
            Color(0xFFE8EAF6),
            Color(0xFF3F51B5),
            "AI",
            Icons.Default.AutoAwesome
        )
        "REGEX" -> Quadruple(
            Color(0xFFE1F5FE),
            Color(0xFF0288D1),
            "AUTO",
            Icons.Default.Sync
        )
        else -> Quadruple(
            Color(0xFFF3E5F5),
            Color(0xFF7B1FA2),
            "MANUAL",
            Icons.Default.Edit
        )
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = textColor,
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun getCategoryIcon(category: String): ImageVector {
    return when (category) {
        "Shopping" -> Icons.Default.ShoppingBag
        "Food & Dining" -> Icons.Default.Fastfood
        "Utilities & Bills" -> Icons.Default.ReceiptLong
        "Bank Transfer" -> Icons.Default.SwapHoriz
        "Investments & Savings" -> Icons.Default.AutoAwesome
        "Transfers & Out" -> Icons.Default.SwapHoriz
        "Travel & Transport" -> Icons.Default.DirectionsCar
        "Health & Medical" -> Icons.Default.MedicalServices
        "Entertainment" -> Icons.Default.Movie
        "Income" -> Icons.Default.AttachMoney
        else -> Icons.Default.ReceiptLong
    }
}

@Composable
private fun rememberFormattedDate(timestamp: Long): String {
    val date = Date(timestamp)
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val txCal = Calendar.getInstance().apply { time = date }
    val txYear = txCal.get(Calendar.YEAR)

    val pattern = if (txYear == currentYear) "dd MMM, HH:mm" else "dd MMM yyyy, HH:mm"
    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
    return sdf.format(date)
}
