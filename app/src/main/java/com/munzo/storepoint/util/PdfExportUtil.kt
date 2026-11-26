package com.munzo.storepoint.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.munzo.storepoint.data.CashierSession
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.data.StorePointRepository
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.data.TransactionItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExportUtil {

    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun formatDate(date: Date): String = synchronized(df) {
        df.format(date)
    }

    fun shareFile(context: Context, file: File, mimeType: String = "application/pdf") {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Document"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // =========================================================================
    // CSV EXPORT ENGINE
    // =========================================================================

    fun buildSalesSummaryCsv(transactions: List<Transaction>): String {
        val sb = StringBuilder()
        sb.append("Transaction ID,Timestamp,Date Time,Cashier,Customer,Payment Method,Subtotal,Tax Amount,Total Amount,Status\n")
        transactions.forEach { t ->
            val dateStr = formatDate(Date(t.timestamp))
            sb.append("#${t.id},${t.timestamp},\"$dateStr\",${t.cashierUsername},\"${t.customerName.replace("\"", "\"\"")}\",${t.paymentMethod},${String.format(Locale.US, "%.2f", t.subtotal)},${String.format(Locale.US, "%.2f", t.taxAmount)},${String.format(Locale.US, "%.2f", t.totalAmount)},${t.status}\n")
        }
        return sb.toString()
    }

    fun exportSalesSummaryCsv(context: Context, storeConfig: StoreConfig, transactions: List<Transaction>) {
        try {
            val csvContent = buildSalesSummaryCsv(transactions)
            val file = File(context.cacheDir, "storepoint_sales_summary.csv")
            file.writeText(csvContent, Charsets.UTF_8)
            shareFile(context, file, "text/csv")
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to export Sales CSV: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun buildRegisterJournalCsv(sessions: List<CashierSession>): String {
        val sb = StringBuilder()
        sb.append("Session ID,Cashier Username,Start Time,End Time,Starting Cash,Ending Cash,Status\n")
        sessions.forEach { s ->
            val startStr = formatDate(Date(s.startTime))
            val endStr = if (s.endTime != null) formatDate(Date(s.endTime)) else "ACTIVE"
            val endCashStr = if (s.endingCash != null) String.format(Locale.US, "%.2f", s.endingCash) else "N/A"
            sb.append("#${s.id},${s.cashierUsername},\"$startStr\",\"$endStr\",${String.format(Locale.US, "%.2f", s.startingCash)},$endCashStr,${s.status}\n")
        }
        return sb.toString()
    }

    fun exportDailyRegisterJournalCsv(context: Context, storeConfig: StoreConfig, sessions: List<CashierSession>) {
        try {
            val csvContent = buildRegisterJournalCsv(sessions)
            val file = File(context.cacheDir, "storepoint_register_journal.csv")
            file.writeText(csvContent, Charsets.UTF_8)
            shareFile(context, file, "text/csv")
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to export Register Journal CSV: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun buildInventoryValuationCsv(products: List<Product>): String {
        val sb = StringBuilder()
        sb.append("Product ID,Barcode,Product Name,Stock Count,Base UOM,Cost Price,Retail Price,Total Cost Valuation,Total Retail Valuation,Projected Profit,Track Inventory\n")
        products.forEach { p ->
            val totalCost = StorePointRepository.roundMoney(p.cost * p.stockCount)
            val totalRetail = StorePointRepository.roundMoney(p.price * p.stockCount)
            val potentialProfit = StorePointRepository.roundMoney((p.price - p.cost) * p.stockCount)
            sb.append("#${p.id},\"${p.barcode}\",\"${p.name.replace("\"", "\"\"")}\",${p.stockCount},\"${p.baseUom}\",${String.format(Locale.US, "%.2f", p.cost)},${String.format(Locale.US, "%.2f", p.price)},${String.format(Locale.US, "%.2f", totalCost)},${String.format(Locale.US, "%.2f", totalRetail)},${String.format(Locale.US, "%.2f", potentialProfit)},${p.trackInventory}\n")
        }
        return sb.toString()
    }

    fun exportInventoryValuationCsv(context: Context, storeConfig: StoreConfig, products: List<Product>) {
        try {
            val csvContent = buildInventoryValuationCsv(products)
            val file = File(context.cacheDir, "storepoint_inventory_valuation.csv")
            file.writeText(csvContent, Charsets.UTF_8)
            shareFile(context, file, "text/csv")
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to export Inventory Valuation CSV: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // =========================================================================
    // PDF EXPORT ENGINE (Delegated Renderers)
    // =========================================================================

    fun exportReceiptPdf(
        context: Context,
        storeConfig: StoreConfig,
        transaction: Transaction,
        items: List<TransactionItem>
    ) = PdfReceiptAndBadgeRenderer.exportReceiptPdf(context, storeConfig, transaction, items)

    fun exportSalesAndInventoryReportPdf(
        context: Context,
        storeConfig: StoreConfig,
        transactions: List<Transaction>,
        productInventory: List<Product>
    ) = PdfReportRenderer.exportSalesAndInventoryReportPdf(context, storeConfig, transactions, productInventory)

    fun exportDailyRegisterJournalPdf(
        context: Context,
        storeConfig: StoreConfig,
        sessions: List<CashierSession>
    ) = PdfReportRenderer.exportDailyRegisterJournalPdf(context, storeConfig, sessions)

    fun exportInventoryValuationPdf(
        context: Context,
        storeConfig: StoreConfig,
        products: List<Product>
    ) = PdfReportRenderer.exportInventoryValuationPdf(context, storeConfig, products)

    fun drawSimpleBarcode(
        canvas: Canvas,
        text: String,
        xStart: Float,
        yStart: Float,
        width: Float,
        height: Float
    ) = PdfReceiptAndBadgeRenderer.drawSimpleBarcode(canvas, text, xStart, yStart, width, height)

    fun exportCashierBadgePdf(
        context: Context,
        storeConfig: StoreConfig,
        username: String,
        role: String,
        barcodeId: String
    ) = PdfReceiptAndBadgeRenderer.exportCashierBadgePdf(context, storeConfig, username, role, barcodeId)
}
