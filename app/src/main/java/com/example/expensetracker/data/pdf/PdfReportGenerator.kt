package com.example.expensetracker.data.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.expensetracker.data.local.ExpenseEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfReportGenerator(private val context: Context) {

    fun generateAndSharePdfReport(expenses: List<ExpenseEntity>, periodLabel: String) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(0, 77, 64) // Dark Teal
            textSize = 22f
            isFakeBoldText = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.rgb(100, 100, 100)
            textSize = 12f
        }
        val headerPaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 14f
            isFakeBoldText = true
        }
        val textPaint = Paint().apply {
            color = Color.rgb(50, 50, 50)
            textSize = 11f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(200, 200, 200)
            strokeWidth = 1f
        }

        var yPosition = 50f
        val leftMargin = 40f

        // Title
        canvas.drawText("Expense Tracker Financial Report", leftMargin, yPosition, titlePaint)
        yPosition += 22f

        canvas.drawText("Period: $periodLabel | Generated: ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())}", leftMargin, yPosition, subtitlePaint)
        yPosition += 30f

        // Summary calculations
        val totalExpense = expenses.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val totalIncome = expenses.filter { it.type == "INCOME" }.sumOf { it.amount }
        val netBalance = totalIncome - totalExpense

        canvas.drawText("Summary Overview", leftMargin, yPosition, headerPaint)
        yPosition += 18f

        canvas.drawText("Total Expenses: Ksh ${String.format(Locale.getDefault(), "%,.2f", totalExpense)}", leftMargin + 10f, yPosition, textPaint)
        yPosition += 16f
        canvas.drawText("Total Income: Ksh ${String.format(Locale.getDefault(), "%,.2f", totalIncome)}", leftMargin + 10f, yPosition, textPaint)
        yPosition += 16f
        canvas.drawText("Net Balance: Ksh ${String.format(Locale.getDefault(), "%,.2f", netBalance)}", leftMargin + 10f, yPosition, textPaint)
        yPosition += 30f

        // Transactions Table Header
        canvas.drawText("Transaction Details", leftMargin, yPosition, headerPaint)
        yPosition += 20f

        canvas.drawLine(leftMargin, yPosition, 555f, yPosition, linePaint)
        yPosition += 15f

        canvas.drawText("Date", leftMargin, yPosition, headerPaint)
        canvas.drawText("Recipient / Merchant", leftMargin + 100f, yPosition, headerPaint)
        canvas.drawText("Source", leftMargin + 320f, yPosition, headerPaint)
        canvas.drawText("Amount (Ksh)", leftMargin + 430f, yPosition, headerPaint)
        yPosition += 10f
        canvas.drawLine(leftMargin, yPosition, 555f, yPosition, linePaint)
        yPosition += 20f

        val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

        for (expense in expenses.take(30)) { // limit to fit page
            if (yPosition > 780f) break

            val dateStr = dateFormat.format(Date(expense.dateTimestamp))
            val recipient = if (expense.recipient.length > 25) expense.recipient.take(22) + "..." else expense.recipient
            val source = expense.source
            val amountStr = "${if (expense.type == "INCOME") "+" else "-"} ${String.format(Locale.getDefault(), "%,.2f", expense.amount)}"

            canvas.drawText(dateStr, leftMargin, yPosition, textPaint)
            canvas.drawText(recipient, leftMargin + 100f, yPosition, textPaint)
            canvas.drawText(source, leftMargin + 320f, yPosition, textPaint)
            canvas.drawText(amountStr, leftMargin + 430f, yPosition, textPaint)

            yPosition += 20f
        }

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "Expense_Report_${System.currentTimeMillis()}.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share Expense PDF Report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (ignored: Exception) {
            pdfDocument.close()
        }
    }
}
