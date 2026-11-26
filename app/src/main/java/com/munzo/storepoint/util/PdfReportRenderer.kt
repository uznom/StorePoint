package com.munzo.storepoint.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Log
import android.widget.Toast
import com.munzo.storepoint.data.CashierSession
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.data.StorePointRepository
import com.munzo.storepoint.data.Transaction
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders A4 executive sales, register journal, and inventory valuation audit reports to PDF.
 */
internal object PdfReportRenderer {

    private data class ReportPaints(
        val title: Paint,
        val subtitle: Paint,
        val heading: Paint,
        val text: Paint,
        val bold: Paint
    )

    private fun createReportPaints(titleSize: Float, headingSize: Float, subtitleSize: Float): ReportPaints =
        ReportPaints(
            title = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = titleSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            },
            subtitle = Paint().apply {
                color = Color.GRAY
                textSize = subtitleSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            },
            heading = Paint().apply {
                color = Color.rgb(51, 65, 85)
                textSize = headingSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            },
            text = Paint().apply {
                color = Color.BLACK
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            },
            bold = Paint().apply {
                color = Color.BLACK
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        )

    fun exportSalesAndInventoryReportPdf(
        context: Context,
        storeConfig: StoreConfig,
        transactions: List<Transaction>,
        productInventory: List<Product>
    ) {
        val document = PdfDocument()
        try {
            val width = 595
            val height = 842
            val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
            val canvas: Canvas = page.canvas
            val p = createReportPaints(titleSize = 20f, headingSize = 14f, subtitleSize = 11f)

            var y = 50f
            canvas.drawText("StorePoint POS Management System", 40f, y, p.title)
            y += 20f
            canvas.drawText("Executive Sales Performance & Operational Audit - ${storeConfig.storeName}", 40f, y, p.subtitle)
            y += 12f
            canvas.drawText("Generated at: ${PdfExportUtil.formatDate(Date())} | Currency: ${storeConfig.currencySymbol}", 40f, y, p.subtitle)
            y += 30f

            canvas.drawText("1. EXECUTIVE FINANCIAL PERFORMANCE", 40f, y, p.heading)
            y += 20f

            val totalTransactionsCount = transactions.size
            val totalRevenue = transactions.sumOf { it.totalAmount }
            val taxCollected = transactions.sumOf { it.taxAmount }
            val cashRevenue = transactions.filter { it.paymentMethod == "CASH" }.sumOf { it.totalAmount }
            val gcashRevenue = transactions.filter { it.paymentMethod.contains("GCASH", ignoreCase = true) }.sumOf { it.totalAmount }
            val mayaRevenue = transactions.filter { it.paymentMethod.contains("MAYA", ignoreCase = true) }.sumOf { it.totalAmount }
            val avgBasket = if (totalTransactionsCount > 0) totalRevenue / totalTransactionsCount else 0.0

            canvas.drawText("Total Transactions Processed:", 50f, y, p.text)
            canvas.drawText("$totalTransactionsCount transactions", 260f, y, p.bold)
            y += 16f

            canvas.drawText("Daily Gross Sales Revenue:", 50f, y, p.text)
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", totalRevenue)}", 260f, y, p.bold)
            y += 16f

            canvas.drawText("Average Basket Ticket Size:", 50f, y, p.text)
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", avgBasket)}", 260f, y, p.bold)
            y += 16f

            canvas.drawText("Cash Settlements:", 50f, y, p.text)
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", cashRevenue)}", 260f, y, p.bold)
            y += 16f

            canvas.drawText("GCash Settlements:", 50f, y, p.text)
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", gcashRevenue)}", 260f, y, p.bold)
            y += 16f

            canvas.drawText("Maya / Card Settlements:", 50f, y, p.text)
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", mayaRevenue)}", 260f, y, p.bold)
            y += 16f

            canvas.drawText("Tax (VAT) Collected:", 50f, y, p.text)
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", taxCollected)}", 260f, y, p.bold)
            y += 30f

            canvas.drawText("2. STOCK AUDIT & PRODUCT AVAILABILITY", 40f, y, p.heading)
            y += 20f

            val totalProductsInStore = productInventory.size
            val totalStocksOnHand = productInventory.sumOf { it.stockCount }
            val outOfStockItems = productInventory.filter { it.stockCount == 0 }
            val lowStockItems = productInventory.filter { it.stockCount in 1..5 }

            canvas.drawText("Total registered Products catalog:", 50f, y, p.text)
            canvas.drawText("$totalProductsInStore unique items", 260f, y, p.bold)
            y += 16f

            canvas.drawText("Total cumulative Stock pieces:", 50f, y, p.text)
            canvas.drawText("$totalStocksOnHand units on hand", 260f, y, p.bold)
            y += 16f

            canvas.drawText("Out Of Stock Items (0 units):", 50f, y, p.text)
            if (outOfStockItems.isNotEmpty()) p.bold.color = Color.RED
            canvas.drawText("${outOfStockItems.size} products critical warning", 260f, y, p.bold)
            p.bold.color = Color.BLACK
            y += 16f

            canvas.drawText("Low Stock Warning (1-5 units):", 50f, y, p.text)
            canvas.drawText("${lowStockItems.size} products flagged", 260f, y, p.bold)
            y += 30f

            canvas.drawText("3. DETAILED PRODUCT CATALOG STATUS", 40f, y, p.heading)
            y += 20f

            canvas.drawText("Product SKU / ID", 45f, y, p.bold)
            canvas.drawText("Product Label", 150f, y, p.bold)
            canvas.drawText("Price", 350f, y, p.bold)
            canvas.drawText("Stock", 440f, y, p.bold)
            canvas.drawText("Barcode", 510f, y, p.bold)
            y += 8f
            canvas.drawLine(40f, y, 555f, y, p.text)
            y += 15f

            for (product in productInventory.take(15)) {
                val priceStr = "${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", product.price)}"
                val stockStr = "${product.stockCount} ${product.baseUom}"
                canvas.drawText("#${product.id}", 45f, y, p.text)
                val pName = if (product.name.length > 25) product.name.substring(0, 23) + ".." else product.name
                canvas.drawText(pName, 150f, y, p.text)
                canvas.drawText(priceStr, 350f, y, p.text)
                if (product.stockCount == 0) {
                    p.bold.color = Color.RED
                    canvas.drawText("OUT OF STOCK", 440f, y, p.bold)
                    p.bold.color = Color.BLACK
                } else {
                    canvas.drawText(stockStr, 440f, y, p.text)
                }
                canvas.drawText(product.barcode.ifEmpty { "N/A" }, 510f, y, p.text)
                y += 18f
            }

            if (productInventory.size > 15) {
                canvas.drawText("(and ${productInventory.size - 15} more catalog entries in database)", 45f, y, p.subtitle)
            }

            y = 800f
            canvas.drawLine(40f, y, 555f, y, p.text)
            y += 15f
            p.subtitle.textAlign = Paint.Align.CENTER
            canvas.drawText("StorePoint POS Management System - Confidential Store Report", (width / 2).toFloat(), y, p.subtitle)

            document.finishPage(page)

            val file = File(context.cacheDir, "storepoint_sales_report.pdf")
            FileOutputStream(file).use { fos -> document.writeTo(fos) }
            PdfExportUtil.shareFile(context, file)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to generate Sales Report PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            try {
                document.close()
            } catch (e: Exception) {
                Log.e("PdfExportUtil", "Error closing PdfDocument", e)
            }
        }
    }

    fun exportDailyRegisterJournalPdf(
        context: Context,
        storeConfig: StoreConfig,
        sessions: List<CashierSession>
    ) {
        val document = PdfDocument()
        try {
            val width = 595
            val height = 842
            val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
            val canvas: Canvas = page.canvas
            val p = createReportPaints(titleSize = 18f, headingSize = 13f, subtitleSize = 10f)

            var y = 50f
            canvas.drawText("${storeConfig.storeName.uppercase()} - DAILY REGISTER JOURNAL AUDIT", 40f, y, p.title)
            y += 18f
            canvas.drawText("Generated at: ${PdfExportUtil.formatDate(Date())} | Currency: ${storeConfig.currencySymbol}", 40f, y, p.subtitle)
            y += 28f

            canvas.drawText("1. REGISTER SESSION SUMMARY", 40f, y, p.heading)
            y += 18f

            val totalSessions = sessions.size
            val activeSessions = sessions.count { it.status == "ACTIVE" }
            val closedSessions = sessions.count { it.status != "ACTIVE" }
            val totalStartFloat = sessions.sumOf { it.startingCash }
            val totalEndCash = sessions.sumOf { it.endingCash ?: 0.0 }

            canvas.drawText("Total Cashier Sessions:", 50f, y, p.text)
            canvas.drawText("$totalSessions ($closedSessions closed, $activeSessions active)", 250f, y, p.bold)
            y += 16f

            canvas.drawText("Total Starting Floats Allocated:", 50f, y, p.text)
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", totalStartFloat)}", 250f, y, p.bold)
            y += 16f

            canvas.drawText("Total Ending Drawer Cash Counted:", 50f, y, p.text)
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", totalEndCash)}", 250f, y, p.bold)
            y += 28f

            canvas.drawText("2. CASHIER SHIFT AUDIT TRAIL", 40f, y, p.heading)
            y += 18f

            canvas.drawText("Session", 45f, y, p.bold)
            canvas.drawText("Cashier", 110f, y, p.bold)
            canvas.drawText("Start Float", 210f, y, p.bold)
            canvas.drawText("End Cash", 310f, y, p.bold)
            canvas.drawText("Time Window", 410f, y, p.bold)
            canvas.drawText("Status", 510f, y, p.bold)
            y += 8f
            canvas.drawLine(40f, y, 555f, y, p.text)
            y += 16f

            for (s in sessions.take(18)) {
                val startStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(s.startTime))
                val endStr = if (s.endTime != null) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(s.endTime)) else "Active"

                canvas.drawText("#${s.id}", 45f, y, p.text)
                canvas.drawText(s.cashierUsername, 110f, y, p.text)
                canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", s.startingCash)}", 210f, y, p.text)
                canvas.drawText(if (s.endingCash != null) "${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", s.endingCash)}" else "Pending", 310f, y, p.text)
                canvas.drawText("$startStr-$endStr", 410f, y, p.text)
                canvas.drawText(s.status, 510f, y, p.bold)
                y += 18f
            }

            y = 800f
            canvas.drawLine(40f, y, 555f, y, p.text)
            y += 15f
            p.subtitle.textAlign = Paint.Align.CENTER
            canvas.drawText("Daily Register Journal Audit - StorePoint POS", (width / 2).toFloat(), y, p.subtitle)

            document.finishPage(page)

            val file = File(context.cacheDir, "storepoint_register_journal.pdf")
            FileOutputStream(file).use { fos -> document.writeTo(fos) }
            PdfExportUtil.shareFile(context, file)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to generate Register Journal PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            try {
                document.close()
            } catch (e: Exception) {
                Log.e("PdfExportUtil", "Error closing PdfDocument", e)
            }
        }
    }

    fun exportInventoryValuationPdf(
        context: Context,
        storeConfig: StoreConfig,
        products: List<Product>
    ) {
        val document = PdfDocument()
        try {
            val width = 595
            val height = 842
            val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
            val canvas: Canvas = page.canvas
            val p = createReportPaints(titleSize = 18f, headingSize = 13f, subtitleSize = 10f)

            var y = 50f
            canvas.drawText("${storeConfig.storeName.uppercase()} - INVENTORY VALUATION REPORT", 40f, y, p.title)
            y += 18f
            canvas.drawText("Generated at: ${PdfExportUtil.formatDate(Date())} | Valuation Basis: WAC / COGS", 40f, y, p.subtitle)
            y += 28f

            canvas.drawText("1. INVENTORY ASSET VALUATION SUMMARY", 40f, y, p.heading)
            y += 18f

            val totalProducts = products.size
            val totalUnits = products.sumOf { it.stockCount }
            val totalCostValuation = StorePointRepository.roundMoney(products.sumOf { it.cost * it.stockCount })
            val totalRetailValuation = StorePointRepository.roundMoney(products.sumOf { it.price * it.stockCount })
            val potentialGrossProfit = StorePointRepository.roundMoney(totalRetailValuation - totalCostValuation)
            val projectedMarginPct = if (totalRetailValuation > 0) (potentialGrossProfit / totalRetailValuation) * 100.0 else 0.0

            canvas.drawText("Total Catalog SKUs:", 50f, y, p.text)
            canvas.drawText("$totalProducts items ($totalUnits total units on hand)", 260f, y, p.bold)
            y += 16f

            canvas.drawText("Total Cost Valuation (COGS Asset):", 50f, y, p.text)
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", totalCostValuation)}", 260f, y, p.bold)
            y += 16f

            canvas.drawText("Total Retail Valuation (Sales Potential):", 50f, y, p.text)
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", totalRetailValuation)}", 260f, y, p.bold)
            y += 16f

            canvas.drawText("Projected Gross Profit / Margin:", 50f, y, p.text)
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", potentialGrossProfit)} (${String.format(Locale.US, "%.1f", projectedMarginPct)}%)", 260f, y, p.bold)
            y += 28f

            canvas.drawText("2. DETAILED INVENTORY VALUATION REGISTER", 40f, y, p.heading)
            y += 18f

            canvas.drawText("SKU", 45f, y, p.bold)
            canvas.drawText("Product Label", 110f, y, p.bold)
            canvas.drawText("Stock", 260f, y, p.bold)
            canvas.drawText("Unit Cost", 330f, y, p.bold)
            canvas.drawText("Retail", 410f, y, p.bold)
            canvas.drawText("Total Asset Value", 480f, y, p.bold)
            y += 8f
            canvas.drawLine(40f, y, 555f, y, p.text)
            y += 16f

            for (prod in products.take(18)) {
                val assetVal = StorePointRepository.roundMoney(prod.cost * prod.stockCount)
                val pName = if (prod.name.length > 22) prod.name.substring(0, 20) + ".." else prod.name
                canvas.drawText("#${prod.id}", 45f, y, p.text)
                canvas.drawText(pName, 110f, y, p.text)
                canvas.drawText("${prod.stockCount} ${prod.baseUom}", 260f, y, p.text)
                canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", prod.cost)}", 330f, y, p.text)
                canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", prod.price)}", 410f, y, p.text)
                canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", assetVal)}", 480f, y, p.bold)
                y += 18f
            }

            if (products.size > 18) {
                canvas.drawText("(and ${products.size - 18} more items in full database)", 45f, y, p.subtitle)
            }

            y = 800f
            canvas.drawLine(40f, y, 555f, y, p.text)
            y += 15f
            p.subtitle.textAlign = Paint.Align.CENTER
            canvas.drawText("Inventory Valuation Audit Report - StorePoint POS", (width / 2).toFloat(), y, p.subtitle)

            document.finishPage(page)

            val file = File(context.cacheDir, "storepoint_inventory_valuation.pdf")
            FileOutputStream(file).use { fos -> document.writeTo(fos) }
            PdfExportUtil.shareFile(context, file)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to generate Inventory Valuation PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            try {
                document.close()
            } catch (e: Exception) {
                Log.e("PdfExportUtil", "Error closing PdfDocument", e)
            }
        }
    }
}
