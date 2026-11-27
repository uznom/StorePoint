package com.munzo.storepoint.util

import com.munzo.storepoint.data.Category
import com.munzo.storepoint.data.Product
import java.util.Locale

data class CatalogCsvRow(
    val rowNumber: Int,
    val name: String,
    val barcode: String,
    val categoryName: String,
    val price: Double,
    val cost: Double,
    val stockCount: Int,
    val baseUom: String,
    val expirationDate: String?,
    val isQuickSellPinned: Boolean
)

data class CatalogCsvSkippedRow(
    val rowNumber: Int,
    val rawSnippet: String,
    val reason: String
)

data class CatalogCsvImportPreview(
    val rowsToInsert: List<CatalogCsvRow>,
    val rowsToUpdate: List<CatalogCsvRow>,
    val skippedRows: List<CatalogCsvSkippedRow>
) {
    val validRows: List<CatalogCsvRow> get() = rowsToInsert + rowsToUpdate
    val totalProcessed: Int get() = rowsToInsert.size + rowsToUpdate.size + skippedRows.size
}

/**
 * RFC-4180 compliant offline CSV exporter & importer for the StorePoint product catalog.
 * Handles quoted commas, escaped double quotes (`""`), UTF-8 BOM, and flexible header aliases.
 */
object CatalogCsvCodec {

    private val EXPORT_HEADERS = listOf(
        "name",
        "barcode",
        "category",
        "price",
        "cost",
        "stock",
        "base_uom",
        "expiration_date",
        "quick_sell_pinned"
    )

    fun exportProductsToCsv(
        products: List<Product>,
        categories: List<Category>
    ): String {
        val categoryMap = categories.associateBy { it.id }
        val sb = StringBuilder()
        sb.append(EXPORT_HEADERS.joinToString(",")).append("\r\n")
        for (p in products) {
            val catName = categoryMap[p.categoryId]?.name ?: "General"
            val fields = listOf(
                p.name,
                p.barcode,
                catName,
                String.format(Locale.US, "%.2f", p.price),
                String.format(Locale.US, "%.2f", p.cost),
                p.stockCount.toString(),
                p.baseUom,
                p.expirationDate.orEmpty(),
                if (p.isQuickSellPinned) "true" else "false"
            )
            sb.append(fields.joinToString(",") { escapeCsvField(it) }).append("\r\n")
        }
        return sb.toString()
    }

    fun parseAndPreview(
        csvContent: String,
        existingProducts: List<Product>
    ): CatalogCsvImportPreview {
        val cleaned = csvContent.removePrefix("\uFEFF")
        val records = parseRfc4180(cleaned)
        if (records.isEmpty()) {
            return CatalogCsvImportPreview(emptyList(), emptyList(), emptyList())
        }

        val headerRow = records.first().map { normalizeHeader(it) }
        val nameIdx = headerRow.indexOfFirst { it in setOf("name", "productname", "product", "item", "itemname") }
        val barcodeIdx = headerRow.indexOfFirst { it in setOf("barcode", "sku", "ean", "upc", "code") }
        val categoryIdx = headerRow.indexOfFirst { it in setOf("category", "categoryname", "group", "department") }
        val priceIdx = headerRow.indexOfFirst { it in setOf("price", "sellingprice", "retailprice", "unitprice") }
        val costIdx = headerRow.indexOfFirst { it in setOf("cost", "unitcost", "costprice", "purchasecost") }
        val stockIdx = headerRow.indexOfFirst { it in setOf("stock", "stockcount", "qty", "quantity", "inventory") }
        val uomIdx = headerRow.indexOfFirst { it in setOf("baseuom", "uom", "unit") }
        val expiryIdx = headerRow.indexOfFirst { it in setOf("expirationdate", "expiry", "expirydate", "expdate") }
        val pinnedIdx = headerRow.indexOfFirst { it in setOf("quicksellpinned", "pinned", "quicksell", "favorite") }

        val hasHeader = nameIdx != -1 || priceIdx != -1
        val startIndex = if (hasHeader) 1 else 0

        val resolvedNameIdx = if (hasHeader) nameIdx else 0
        val resolvedBarcodeIdx = if (hasHeader) barcodeIdx else 1
        val resolvedCategoryIdx = if (hasHeader) categoryIdx else 2
        val resolvedPriceIdx = if (hasHeader) priceIdx else 3
        val resolvedCostIdx = if (hasHeader) costIdx else 4
        val resolvedStockIdx = if (hasHeader) stockIdx else 5
        val resolvedUomIdx = if (hasHeader) uomIdx else 6
        val resolvedExpiryIdx = if (hasHeader) expiryIdx else 7
        val resolvedPinnedIdx = if (hasHeader) pinnedIdx else 8

        val existingBarcodes = existingProducts
            .map { it.barcode.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        val existingNames = existingProducts
            .map { it.name.trim().lowercase() }
            .toSet()

        val toInsert = mutableListOf<CatalogCsvRow>()
        val toUpdate = mutableListOf<CatalogCsvRow>()
        val skipped = mutableListOf<CatalogCsvSkippedRow>()
        val seenNamesInFile = mutableSetOf<String>()
        val seenBarcodesInFile = mutableSetOf<String>()

        for (i in startIndex until records.size) {
            val cols = records[i]
            val rowNum = i + 1
            if (cols.all { it.isBlank() }) continue

            val name = cols.getOrNull(resolvedNameIdx)?.trim().orEmpty()
            val barcode = cols.getOrNull(resolvedBarcodeIdx)?.trim().orEmpty()
            val category = cols.getOrNull(resolvedCategoryIdx)?.trim().orEmpty().ifBlank { "General" }
            val priceStr = cols.getOrNull(resolvedPriceIdx)?.trim().orEmpty()
            val costStr = cols.getOrNull(resolvedCostIdx)?.trim().orEmpty()
            val stockStr = cols.getOrNull(resolvedStockIdx)?.trim().orEmpty()
            val baseUom = cols.getOrNull(resolvedUomIdx)?.trim().orEmpty().ifBlank { "pc" }
            val expiry = cols.getOrNull(resolvedExpiryIdx)?.trim()?.takeIf { it.isNotEmpty() }
            val pinnedRaw = cols.getOrNull(resolvedPinnedIdx)?.trim().orEmpty()

            if (name.isBlank()) {
                skipped.add(CatalogCsvSkippedRow(rowNum, cols.joinToString(",").take(60), "Missing product name"))
                continue
            }
            val price = parseNumeric(priceStr)
            if (price == null || price < 0.0) {
                skipped.add(CatalogCsvSkippedRow(rowNum, name, "Invalid price '$priceStr'"))
                continue
            }
            val cost = if (costStr.isBlank()) 0.0 else parseNumeric(costStr)
            if (cost == null || cost < 0.0) {
                skipped.add(CatalogCsvSkippedRow(rowNum, name, "Invalid cost '$costStr'"))
                continue
            }
            val stock = if (stockStr.isBlank()) 0 else stockStr.toDoubleOrNull()?.toInt()
            if (stock == null || stock < 0) {
                skipped.add(CatalogCsvSkippedRow(rowNum, name, "Invalid stock '$stockStr'"))
                continue
            }

            val isPinned = pinnedRaw.equals("true", ignoreCase = true) ||
                pinnedRaw == "1" ||
                pinnedRaw.equals("yes", ignoreCase = true)

            val parsedRow = CatalogCsvRow(
                rowNumber = rowNum,
                name = name,
                barcode = barcode,
                categoryName = category,
                price = price,
                cost = cost,
                stockCount = stock,
                baseUom = baseUom,
                expirationDate = expiry,
                isQuickSellPinned = isPinned
            )

            val barcodeKey = barcode.lowercase()
            val nameKey = name.lowercase()
            val matchesExisting = (barcodeKey.isNotEmpty() && barcodeKey in existingBarcodes) ||
                (nameKey in existingNames) ||
                (barcodeKey.isNotEmpty() && barcodeKey in seenBarcodesInFile) ||
                (nameKey in seenNamesInFile)

            if (matchesExisting) {
                toUpdate.add(parsedRow)
            } else {
                toInsert.add(parsedRow)
            }
            seenNamesInFile.add(nameKey)
            if (barcodeKey.isNotEmpty()) seenBarcodesInFile.add(barcodeKey)
        }

        return CatalogCsvImportPreview(
            rowsToInsert = toInsert,
            rowsToUpdate = toUpdate,
            skippedRows = skipped
        )
    }

    internal fun escapeCsvField(value: String): String {
        val needsQuoting = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuoting) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    internal fun parseRfc4180(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < input.length) {
            val ch = input[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < input.length && input[i + 1] == '"') {
                        field.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(field.toString())
                        field.clear()
                    }
                    '\r' -> {
                        currentRow.add(field.toString())
                        field.clear()
                        rows.add(currentRow.toList())
                        currentRow.clear()
                        if (i + 1 < input.length && input[i + 1] == '\n') i++
                    }
                    '\n' -> {
                        currentRow.add(field.toString())
                        field.clear()
                        rows.add(currentRow.toList())
                        currentRow.clear()
                    }
                    else -> field.append(ch)
                }
            }
            i++
        }

        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            rows.add(currentRow.toList())
        }
        return rows
    }

    private fun normalizeHeader(raw: String): String =
        raw.trim().lowercase().replace("_", "").replace("-", "").replace(" ", "")

    private fun parseNumeric(raw: String): Double? {
        val cleaned = raw.replace("₱", "").replace("$", "").replace(",", "").trim()
        return cleaned.toDoubleOrNull()
    }
}
