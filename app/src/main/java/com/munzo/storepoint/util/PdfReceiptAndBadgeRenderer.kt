package com.munzo.storepoint.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Log
import android.widget.Toast
import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.data.TransactionItem
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.Locale

/**
 * Renders printable POS transaction receipts and staff ID barcode badges to PDF.
 */
internal object PdfReceiptAndBadgeRenderer {

    fun exportReceiptPdf(
        context: Context,
        storeConfig: StoreConfig,
        transaction: Transaction,
        items: List<TransactionItem>
    ) {
        val document = PdfDocument()
        try {
            val width = 300
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
            canvas.drawText(storeConfig.storeName.uppercase(), (width / 2).toFloat(), y, paintHeader)
            y += 15f
            paintText.textAlign = Paint.Align.CENTER
            canvas.drawText("OFFLINE TRANSACTION RECEIPT", (width / 2).toFloat(), y, paintText)
            y += 15f
            canvas.drawText("Date: ${PdfExportUtil.formatDate(Date(transaction.timestamp))}", (width / 2).toFloat(), y, paintText)
            y += 12f
            canvas.drawText("Cashier: ${transaction.cashierUsername}", (width / 2).toFloat(), y, paintText)
            y += 12f
            if (transaction.customerName.isNotBlank()) {
                canvas.drawText("Customer: ${transaction.customerName}", (width / 2).toFloat(), y, paintText)
                y += 12f
            }
            canvas.drawText("Tx ID: #STP-${transaction.id}", (width / 2).toFloat(), y, paintText)
            y += 15f

            paintText.textAlign = Paint.Align.LEFT
            canvas.drawLine(15f, y, (width - 15).toFloat(), y, paintBold)
            y += 15f

            canvas.drawText("ITEM", 15f, y, paintBold)
            canvas.drawText("QTY", 180f, y, paintBold)
            canvas.drawText("TOTAL", 240f, y, paintBold)
            y += 10f
            canvas.drawLine(15f, y, (width - 15).toFloat(), y, paintText)
            y += 15f

            for (item in items) {
                val itemName = if (item.productName.length > 20) item.productName.substring(0, 18) + ".." else item.productName
                canvas.drawText(itemName, 15f, y, paintText)
                canvas.drawText("${item.quantity} ${item.uomName}", 180f, y, paintText)
                val totalItemPrice = item.price * item.quantity
                canvas.drawText(String.format(Locale.US, "%.2f", totalItemPrice), 240f, y, paintText)
                y += 15f
            }

            y += 5f
            canvas.drawLine(15f, y, (width - 15).toFloat(), y, paintText)
            y += 18f

            canvas.drawText("Subtotal:", 140f, y, paintText)
            canvas.drawText(String.format(Locale.US, "%.2f", transaction.subtotal), 240f, y, paintText)
            y += 15f

            if (transaction.taxAmount > 0) {
                canvas.drawText("Tax (VAT):", 140f, y, paintText)
                canvas.drawText(String.format(Locale.US, "%.2f", transaction.taxAmount), 240f, y, paintText)
                y += 15f
            }

            canvas.drawText("TOTAL:", 140f, y, paintBold)
            canvas.drawText("${storeConfig.currencySymbol}${String.format(Locale.US, "%.2f", transaction.totalAmount)}", 230f, y, paintBold)
            y += 18f

            canvas.drawText("Paid (${transaction.paymentMethod}):", 140f, y, paintText)
            canvas.drawText(String.format(Locale.US, "%.2f", transaction.cashPaid), 240f, y, paintText)
            y += 15f

            canvas.drawText("Change:", 140f, y, paintText)
            canvas.drawText(String.format(Locale.US, "%.2f", transaction.changeAmount), 240f, y, paintText)
            y += 25f

            paintText.textAlign = Paint.Align.CENTER
            canvas.drawText("Thank you for your purchase!", (width / 2).toFloat(), y, paintText)
            y += 12f
            canvas.drawText("Powered by StorePoint POS", (width / 2).toFloat(), y, paintText)

            document.finishPage(page)

            val file = File(context.cacheDir, "receipt_STP_${transaction.id}.pdf")
            FileOutputStream(file).use { fos -> document.writeTo(fos) }
            PdfExportUtil.shareFile(context, file)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to generate Receipt PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            try {
                document.close()
            } catch (e: Exception) {
                Log.e("PdfExportUtil", "Error closing PdfDocument", e)
            }
        }
    }

    fun drawSimpleBarcode(canvas: Canvas, text: String, xStart: Float, yStart: Float, width: Float, height: Float) {
        val paintBar = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val cleaned = text.uppercase().filter { it.isLetterOrDigit() }.ifEmpty { "CASHIER" }
        val binaryStringBuilder = StringBuilder()
        binaryStringBuilder.append("10110110")
        for (char in cleaned) {
            val code = char.code
            for (i in 0..6) {
                if (((code ushr i) and 1) == 1) {
                    binaryStringBuilder.append("11")
                } else {
                    binaryStringBuilder.append("1")
                }
                binaryStringBuilder.append("0")
            }
        }
        binaryStringBuilder.append("10110110")

        val binaryStr = binaryStringBuilder.toString()
        val barWidth = width / binaryStr.length

        for (i in binaryStr.indices) {
            if (binaryStr[i] == '1') {
                val xCurrent = xStart + i * barWidth
                canvas.drawRect(xCurrent, yStart, xCurrent + barWidth, yStart + height, paintBar)
            }
        }
    }

    fun exportCashierBadgePdf(
        context: Context,
        storeConfig: StoreConfig,
        username: String,
        role: String,
        barcodeId: String
    ) {
        val document = PdfDocument()
        try {
            val width = 250
            val height = 350
            val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val paintBorder = Paint().apply {
                color = Color.rgb(30, 41, 59)
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            val paintPrimaryBg = Paint().apply {
                color = Color.rgb(30, 41, 59)
                style = Paint.Style.FILL
            }
            val paintRoleBadge = Paint().apply {
                color = if (role.equals("ADMIN", ignoreCase = true)) Color.rgb(220, 38, 38) else Color.rgb(37, 99, 235)
                style = Paint.Style.FILL
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
            val paintTextDark = Paint().apply {
                color = Color.BLACK
                textSize = 10f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            val centerX = (width / 2).toFloat()
            canvas.drawRoundRect(RectF(10f, 10f, (width - 10).toFloat(), (height - 10).toFloat()), 16f, 16f, paintBorder)

            var y = 35f
            canvas.drawText(storeConfig.storeName.uppercase(), centerX, y, paintHeader)
            y += 14f
            canvas.drawText("OFFICIAL EMPLOYEE ID", centerX, y, paintLabel)
            y += 20f

            val avatarSize = 65f
            val avatarX = (width - avatarSize) / 2f
            val avatarPaint = Paint().apply {
                color = Color.rgb(241, 245, 249)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(RectF(avatarX, y, avatarX + avatarSize, y + avatarSize), 10f, 10f, avatarPaint)

            val iconPaint = Paint().apply {
                color = Color.rgb(148, 163, 184)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(centerX, y + 25f, 12f, iconPaint)
            canvas.drawArc(RectF(centerX - 20f, y + 40f, centerX + 20f, y + 70f), 180f, 180f, true, iconPaint)
            y += avatarSize + 18f

            canvas.drawRect(25f, y, (width - 25).toFloat(), y + 35f, paintPrimaryBg)
            paintTextLight.textSize = 14f
            paintTextLight.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(username.uppercase(), centerX, y + 22f, paintTextLight)
            y += 45f

            canvas.drawRoundRect(50f, y, (width - 50).toFloat(), y + 20f, 6f, 6f, paintRoleBadge)
            paintTextLight.textSize = 9f
            paintTextLight.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(role.uppercase(), centerX, y + 13f, paintTextLight)
            y += 35f

            val targetBarcodeCode = barcodeId.ifEmpty { "CASHIER-${username.hashCode().coerceAtLeast(0) % 9999}" }
            val barcodeWidth = 180f
            val barcodeHeight = 40f
            val barcodeX = (width - barcodeWidth) / 2f
            drawSimpleBarcode(canvas, targetBarcodeCode, barcodeX, y, barcodeWidth, barcodeHeight)
            y += 52f

            canvas.drawText("* $targetBarcodeCode *", centerX, y, paintTextDark)
            y += 15f
            canvas.drawText("Secure scanning badge - StorePoint System", centerX, y, paintLabel)

            document.finishPage(page)

            val cleanName = username.replace("[^a-zA-Z0-9]".toRegex(), "_")
            val file = File(context.cacheDir, "cashier_badge_${cleanName}.pdf")
            FileOutputStream(file).use { fos -> document.writeTo(fos) }
            PdfExportUtil.shareFile(context, file)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to write Badge PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            try {
                document.close()
            } catch (e: Exception) {
                Log.e("PdfExportUtil", "Error closing PdfDocument", e)
            }
        }
    }
}
