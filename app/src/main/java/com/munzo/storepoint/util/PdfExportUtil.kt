package com.munzo.storepoint.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.data.TransactionItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExportUtil {

    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private fun formatDate(date: Date): String = synchronized(df) {
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

    /**
     * Generates a printable POS Receipt as PDF and triggers the sharing interface.
     */
    fun exportReceiptPdf(
        context: Context,
        storeConfig: StoreConfig,
        transaction: Transaction,
        items: List<TransactionItem>
    ) {
        val document = PdfDocument()
        try {
            val width = 300 // Standard thermal printer width in points
            val baseHeight = 220
            val dynamicHeight = baseHeight + (items.size * 25)
            val pageInfo = PdfDocument.PageInfo.Builder(width, dynamicHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val paintText = Paint().apply {
                color = Color.BLACK
                textSize = 10f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }

            val paintBold = Paint().apply {
                color = Color.BLACK
                textSize = 11f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }

            val paintHeader = Paint().apply {
                color = Color.BLACK
                textSize = 14f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            var y = 25f

            // Draw Header
            canvas.drawText(storeConfig.storeName.uppercase(), (width / 2).toFloat(), y, paintHeader)
            y += 15f
            paintText.textAlign = Paint.Align.CENTER
            canvas.drawText("OFFLINE TRANSACTION RECEIPT", (width / 2).toFloat(), y, paintText)
            y += 15f
            canvas.drawText("Date: ${formatDate(Date(transaction.timestamp))}", (width / 2).toFloat(), y, paintText)
            y += 12f
            canvas.drawText("Cashier: ${transaction.cashierUsername}", (width / 2).toFloat(), y, paintText)
            y += 12f
            if (transaction.customerName.isNotBlank()) {
                canvas.drawText("Customer: ${transaction.customerName}", (width / 2).toFloat(), y, paintText)
                y += 12f
            }
            canvas.drawText("Tx ID: #STP-${transaction.id}", (width / 2).toFloat(), y, paintText)
            y += 15f

            // Draw Separator Line
            paintText.textAlign = Paint.Align.LEFT
            canvas.drawText("----------------------------------------", 10f, y, paintText)
            y += 12f

            // Table Header
            paintBold.textAlign = Paint.Align.LEFT
            canvas.drawText("Item Details", 15f, y, paintBold)
            paintBold.textAlign = Paint.Align.RIGHT
            canvas.drawText("Qty   Price", (width - 15).toFloat(), y, paintBold)
            y += 15f

            canvas.drawText("----------------------------------------", 10f, y, paintText)
            y += 15f

            // Draw Items
            paintText.textAlign = Paint.Align.LEFT
            for (item in items) {
                val nameText = if (item.productName.length > 20) item.productName.substring(0, 18) + ".." else item.productName
                canvas.drawText(nameText, 15f, y, paintText)
                
                val rightText = "${item.quantity} x ${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", item.price)}"
                paintText.textAlign = Paint.Align.RIGHT
                canvas.drawText(rightText, (width - 15).toFloat(), y, paintText)
                paintText.textAlign = Paint.Align.LEFT
                y += 20f
            }

            canvas.drawText("----------------------------------------", 10f, y, paintText)
            y += 15f

            // Summary Calculations
            paintText.textAlign = Paint.Align.LEFT
            canvas.drawText("VATable Sales (Net):", 30f, y, paintText)
            paintText.textAlign = Paint.Align.RIGHT
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", transaction.subtotal)}", (width - 30).toFloat(), y, paintText)
            y += 14f

            paintText.textAlign = Paint.Align.LEFT
            canvas.drawText("12% VAT (Inc.):", 30f, y, paintText)
            paintText.textAlign = Paint.Align.RIGHT
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", transaction.taxAmount)}", (width - 30).toFloat(), y, paintText)
            y += 14f

            paintBold.textAlign = Paint.Align.LEFT
            canvas.drawText("TOTAL AMOUNT (VAT Inc.):", 30f, y, paintBold)
            paintBold.textAlign = Paint.Align.RIGHT
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", transaction.totalAmount)}", (width - 30).toFloat(), y, paintBold)
            y += 18f

            paintText.textAlign = Paint.Align.LEFT
            canvas.drawText("Paid via ${transaction.paymentMethod}:", 30f, y, paintText)
            paintText.textAlign = Paint.Align.RIGHT
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", transaction.cashPaid)}", (width - 30).toFloat(), y, paintText)
            y += 14f

            paintText.textAlign = Paint.Align.LEFT
            canvas.drawText("Change Returned:", 30f, y, paintText)
            paintText.textAlign = Paint.Align.RIGHT
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", transaction.changeAmount)}", (width - 30).toFloat(), y, paintText)
            y += 20f

            // Thank you footer
            paintText.textAlign = Paint.Align.CENTER
            canvas.drawText("TY for shopping at ${storeConfig.storeName}!", (width / 2).toFloat(), y, paintText)
            y += 12f
            canvas.drawText("Powered by StorePoint POS (Room DB)", (width / 2).toFloat(), y, paintText)

            document.finishPage(page)

            // Save PDF to cache and open it
            val file = File(context.cacheDir, "receipt_STP_${transaction.id}.pdf")
            FileOutputStream(file).use { fos ->
                document.writeTo(fos)
            }
            shareFile(context, file)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to write PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            try {
                document.close()
            } catch (e: Exception) {
                android.util.Log.e("PdfExportUtil", "Error closing PdfDocument", e)
            }
        }
    }

    /**
     * Exports a comprehensive Sales Analytics Report for store management.
     */
    fun exportSalesAndInventoryReportPdf(
        context: Context,
        storeConfig: StoreConfig,
        transactions: List<Transaction>,
        productInventory: List<Product>
    ) {
        val document = PdfDocument()
        try {
            val width = 595 // Standard A4 page width in points
            val height = 842 // Standard A4 page height in points
            val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val paintTitle = Paint().apply {
                color = Color.rgb(15, 23, 42) // Dark midnight blue
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val paintSubtitle = Paint().apply {
                color = Color.GRAY
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            val paintHeading = Paint().apply {
                color = Color.rgb(51, 65, 85)
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val paintText = Paint().apply {
                color = Color.BLACK
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            val paintBold = Paint().apply {
                color = Color.BLACK
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            var y = 50f

            // Title Header
            canvas.drawText("StorePoint POS Management System", 40f, y, paintTitle)
            y += 20f
            canvas.drawText("Operational Analytics & Inventory Report - ${storeConfig.storeName}", 40f, y, paintSubtitle)
            y += 12f
            canvas.drawText("Generated at: ${formatDate(Date())} | Currency: ${storeConfig.currencySymbol}", 40f, y, paintSubtitle)
            y += 30f

        // Draw Section: Key Financial Metrics
        canvas.drawText("1. OVERALL FINANCIAL METRICS", 40f, y, paintHeading)
        y += 20f

        val totalTransactionsCount = transactions.size
        val totalRevenue = transactions.sumOf { it.totalAmount }
        val taxCollected = transactions.sumOf { it.taxAmount }
        val cashRevenue = transactions.filter { it.paymentMethod == "CASH" }.sumOf { it.totalAmount }
        val cardRevenue = transactions.filter { it.paymentMethod == "CARD" }.sumOf { it.totalAmount }

        canvas.drawText("Total Transactions processed:", 50f, y, paintText)
        canvas.drawText("$totalTransactionsCount transactions", 250f, y, paintBold)
        y += 16f

        canvas.drawText("Gross Revenue streams:", 50f, y, paintText)
        canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", totalRevenue)}", 250f, y, paintBold)
        y += 16f

        canvas.drawText("Sales Tax (VAT) collected:", 50f, y, paintText)
        canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", taxCollected)}", 250f, y, paintBold)
        y += 16f

        canvas.drawText("Cash checkout revenue:", 50f, y, paintText)
        canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", cashRevenue)}", 250f, y, paintBold)
        y += 16f

        canvas.drawText("Card checkout revenue:", 50f, y, paintText)
        canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", cardRevenue)}", 250f, y, paintBold)
        y += 30f

        // Draw Section: Inventory Auditing Block
        canvas.drawText("2. STOCK AUDIT & PRODUCT AVAILABILITY", 40f, y, paintHeading)
        y += 20f

        val totalProductsInStore = productInventory.size
        val totalStocksOnHand = productInventory.sumOf { it.stockCount }
        val outOfStockItems = productInventory.filter { it.stockCount == 0 }
        val lowStockItems = productInventory.filter { it.stockCount in 1..5 }

        canvas.drawText("Total registered Products catalog:", 50f, y, paintText)
        canvas.drawText("$totalProductsInStore unique items", 250f, y, paintBold)
        y += 16f

        canvas.drawText("Total cumulative Stock pieces:", 50f, y, paintText)
        canvas.drawText("$totalStocksOnHand units on hand", 250f, y, paintBold)
        y += 16f

        canvas.drawText("Out Of Stock Items (0 units):", 50f, y, paintText)
        canvas.drawText("${outOfStockItems.size} products critical warning", 250f, y, if (outOfStockItems.isNotEmpty()) paintBold.apply { color = Color.RED } else paintBold)
        paintBold.apply { color = Color.BLACK }
        y += 16f

        canvas.drawText("Low Stock Warn levels (1-5 units):", 50f, y, paintText)
        canvas.drawText("${lowStockItems.size} products flagged warning", 250f, y, paintBold)
        y += 30f

        // Table for critical status
        canvas.drawText("3. REAL-TIME DETAILED STOCK REGISTER", 40f, y, paintHeading)
        y += 20f

        // Table Header
        canvas.drawText("Product SKU / ID", 45f, y, paintBold)
        canvas.drawText("Name Details", 140f, y, paintBold)
        canvas.drawText("Price", 350f, y, paintBold)
        canvas.drawText("Stock", 450f, y, paintBold)
        canvas.drawText("Barcode", 510f, y, paintBold)
        y += 8f
        canvas.drawLine(40f, y, 555f, y, paintText)
        y += 15f

        val listToDraw = productInventory.take(15) // Limit to top 15 in summary page
        for (product in listToDraw) {
            val priceStr = "${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", product.price)}"
            val stockStr = "${product.stockCount} left"

            canvas.drawText("#${product.id}", 45f, y, paintText)
            val pName = if (product.name.length > 25) product.name.substring(0, 23) + ".." else product.name
            canvas.drawText(pName, 140f, y, paintText)
            canvas.drawText(priceStr, 350f, y, paintText)
            
            if (product.stockCount == 0) {
                paintBold.color = Color.RED
                canvas.drawText("OUT OF STOCK", 450f, y, paintBold)
                paintBold.color = Color.BLACK
            } else {
                canvas.drawText(stockStr, 450f, y, paintText)
            }
            
            canvas.drawText(product.barcode.ifEmpty { "N/A" }, 510f, y, paintText)
            y += 18f
        }

        if (productInventory.size > 15) {
            canvas.drawText("(and ${productInventory.size - 15} more catalog entries preserved in local SQLite database)", 45f, y, paintSubtitle)
            y += 15f
        }

        // Footer lines
        y = 800f
        canvas.drawLine(40f, y, 555f, y, paintText)
        y += 15f
        paintSubtitle.textAlign = Paint.Align.CENTER
        canvas.drawText("Report produced cleanly as PDF. StorePoint POS - uznom (github.com/uznom).", (width / 2).toFloat(), y, paintSubtitle)

        document.finishPage(page)

        try {
            val file = File(context.cacheDir, "storepoint_operational_report.pdf")
            FileOutputStream(file).use { fos ->
                document.writeTo(fos)
            }
            shareFile(context, file)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to write Sales Report PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            try {
                document.close()
            } catch (e: Exception) {
                android.util.Log.e("PdfExportUtil", "Error closing PdfDocument", e)
            }
        }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to generate Sales Report PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            try {
                document.close()
            } catch (ce: Exception) {
                android.util.Log.e("PdfExportUtil", "Error closing PdfDocument", ce)
            }
        }
    }

    /**
     * Draws a very simple barcode-like series of lines representing the barcodeId string.
     */
    fun drawSimpleBarcode(canvas: Canvas, text: String, xStart: Float, yStart: Float, width: Float, height: Float) {
        val paintBar = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        
        val cleaned = text.uppercase().filter { it.isLetterOrDigit() }.ifEmpty { "CASHIER" }
        // Simple hashing or mapping of chars to binary stripes for a pseudo Code-39 or simple representation
        // To make it look incredibly realistic and functional, let's map each char to 7 stripes.
        val binaryStringBuilder = StringBuilder()
        // Let's add a start trigger
        binaryStringBuilder.append("10110110") // Start guard
        for (char in cleaned) {
            val code = char.code
            // Convert to a 7-bit binary representation
            for (i in 0..6) {
                if (((code ushr i) and 1) == 1) {
                    binaryStringBuilder.append("11") // thick bar
                } else {
                    binaryStringBuilder.append("1") // thin bar
                }
                binaryStringBuilder.append("0") // space
            }
        }
        binaryStringBuilder.append("10110110") // End guard
        
        val binaryStr = binaryStringBuilder.toString()
        val totalBars = binaryStr.length
        val barWidth = width / totalBars
        
        for (i in binaryStr.indices) {
            if (binaryStr[i] == '1') {
                val xCurrent = xStart + i * barWidth
                canvas.drawRect(xCurrent, yStart, xCurrent + barWidth, yStart + height, paintBar)
            }
        }
    }

    /**
     * Generates a printable Staff ID Badge PDF with custom pseudo-rendered 1D scan code
     */
    fun exportCashierBadgePdf(
        context: Context,
        storeConfig: StoreConfig,
        username: String,
        role: String,
        barcodeId: String
    ) {
        val document = PdfDocument()
        try {
            val width = 250 // Custom card width in points (about 3.5 inches)
            val height = 350 // Custom card height in points (about 4.8 inches)
            val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val paintBorder = Paint().apply {
                color = Color.rgb(30, 41, 59) // Slate 800
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }

            val paintPrimaryBg = Paint().apply {
                color = Color.rgb(30, 41, 59) // Slate 800 name area
                style = Paint.Style.FILL
            }

            val paintRoleBadge = Paint().apply {
                color = if (role.equals("ADMIN", ignoreCase = true)) Color.rgb(220, 38, 38) else Color.rgb(37, 99, 235)
                style = Paint.Style.FILL
            }

            val paintTextDark = Paint().apply {
                color = Color.BLACK
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }

            val paintTextLight = Paint().apply {
                color = Color.WHITE
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            val paintHeader = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            val paintLabel = Paint().apply {
                color = Color.GRAY
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }

            val centerX = (width / 2).toFloat()

            // 1) Outer Badge Frame with Rounded Corners
            val rectFrame = android.graphics.RectF(10f, 10f, (width - 10).toFloat(), (height - 10).toFloat())
            canvas.drawRoundRect(rectFrame, 16f, 16f, paintBorder)

            var y = 35f

            // 2) Store Name & Subtitle
            canvas.drawText(storeConfig.storeName.uppercase(), centerX, y, paintHeader)
            y += 14f
            canvas.drawText("OFFICIAL EMPLOYEE ID", centerX, y, paintLabel)
            y += 20f

            // 3) Photo/Avatar placeholder icon box
            val avatarSize = 65f
            val avatarX = (width - avatarSize) / 2f
            val avatarPaint = Paint().apply {
                color = Color.rgb(241, 245, 249) // Slate 100
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(android.graphics.RectF(avatarX, y, avatarX + avatarSize, y + avatarSize), 10f, 10f, avatarPaint)
            
            // Draw simple user silhouette in avatar box
            val iconPaint = Paint().apply {
                color = Color.rgb(148, 163, 184) // Slate 400
                style = Paint.Style.FILL
            }
            // Head
            canvas.drawCircle(centerX, y + 25f, 12f, iconPaint)
            // Shoulders
            canvas.drawArc(android.graphics.RectF(centerX - 20f, y + 40f, centerX + 20f, y + 70f), 180f, 180f, true, iconPaint)
            
            y += avatarSize + 18f

            // 4) Staff Username Block
            canvas.drawRect(25f, y, (width - 25).toFloat(), y + 35f, paintPrimaryBg)
            paintTextLight.textSize = 14f
            paintTextLight.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(username.uppercase(), centerX, y + 22f, paintTextLight)
            y += 45f

            // 5) Role Badge
            canvas.drawRoundRect(50f, y, (width - 50).toFloat(), y + 20f, 6f, 6f, paintRoleBadge)
            paintTextLight.textSize = 9f
            paintTextLight.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(role.uppercase(), centerX, y + 13f, paintTextLight)
            y += 35f

            // 6) Custom 1D Barcode Drawing representing the barcodeId code
            val targetBarcodeCode = barcodeId.ifEmpty { "CASHIER-${username.hashCode().coerceAtLeast(0) % 9999}" }
            val barcodeWidth = 180f
            val barcodeHeight = 40f
            val barcodeX = (width - barcodeWidth) / 2f
            drawSimpleBarcode(canvas, targetBarcodeCode, barcodeX, y, barcodeWidth, barcodeHeight)
            y += 52f

            // 7) Printed key text under barcode
            paintTextDark.textSize = 10f
            paintTextDark.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            canvas.drawText("* $targetBarcodeCode *", centerX, y, paintTextDark)
            y += 15f

            // Security footer
            paintLabel.textSize = 8f
            canvas.drawText("Secure scanning badge - StorePoint System", centerX, y, paintLabel)

            document.finishPage(page)

            val cleanName = username.replace("[^a-zA-Z0-9]".toRegex(), "_")
            val file = File(context.cacheDir, "cashier_badge_${cleanName}.pdf")
            FileOutputStream(file).use { fos ->
                document.writeTo(fos)
            }
            shareFile(context, file)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to write Badge PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            try {
                document.close()
            } catch (e: Exception) {
                android.util.Log.e("PdfExportUtil", "Error closing PdfDocument", e)
            }
        }
    }
}
