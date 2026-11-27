package com.munzo.storepoint.data

import androidx.room.withTransaction
import com.munzo.storepoint.util.CatalogCsvRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Domain delegate handling offline-first retail POS extensions:
 *  - Suki Tab (Customer Credit / Utang) ledger and repayments
 *  - Persistent ESC/POS thermal print retry queue
 *  - Mid-shift non-resetting X-Reading aggregation
 *  - Atomic CSV bulk product import
 */
internal class OfflineExtensionRepository(private val db: AppDatabase) {

    private val customerCreditDao = db.customerCreditDao
    private val pendingPrintJobDao = db.pendingPrintJobDao
    private val categoryDao = db.categoryDao
    private val productDao = db.productDao
    private val cashierSessionDao = db.cashierSessionDao
    private val drawerTransactionDao = db.drawerTransactionDao

    // --- Suki Tab (Customer Credit / Utang) ---
    val allActiveCustomers: Flow<List<CustomerAccount>> = customerCreditDao.getAllActiveCustomers()
    val allCustomers: Flow<List<CustomerAccount>> = customerCreditDao.getAllCustomers()

    suspend fun getAllCustomersSync(): List<CustomerAccount> = customerCreditDao.getAllCustomersSync()

    suspend fun getCustomerByIdSync(id: Int): CustomerAccount? = customerCreditDao.getCustomerByIdSync(id)

    fun getLedgerForCustomer(customerId: Int): Flow<List<CustomerCreditEntry>> =
        customerCreditDao.getLedgerForCustomer(customerId)

    suspend fun getLedgerForCustomerSync(customerId: Int): List<CustomerCreditEntry> =
        customerCreditDao.getLedgerForCustomerSync(customerId)

    suspend fun saveCustomerAccount(account: CustomerAccount): Long = withContext(Dispatchers.IO) {
        require(account.name.isNotBlank()) { "Customer name is required." }
        require(!account.creditLimit.isNaN() && !account.creditLimit.isInfinite() && account.creditLimit >= 0.0) {
            "Credit limit must be a valid non-negative amount."
        }
        val now = System.currentTimeMillis()
        var savedId = account.id.toLong()
        db.withTransaction {
            if (account.id > 0) {
                val existing = customerCreditDao.getCustomerByIdSync(account.id)
                if (existing != null) {
                    // CRITICAL: Use @Update instead of INSERT OR REPLACE so SQLite does NOT
                    // fire ON DELETE CASCADE on customer_credit_ledger, and preserve the live
                    // currentBalance so stale UI snapshots cannot overwrite active debt.
                    val updated = existing.copy(
                        name = account.name.trim(),
                        phone = account.phone.trim(),
                        address = account.address.trim(),
                        creditLimit = StorePointRepository.roundMoney(account.creditLimit.coerceAtLeast(0.0)),
                        notes = account.notes.trim(),
                        isActive = account.isActive,
                        updatedAt = now
                    )
                    customerCreditDao.updateCustomer(updated)
                    savedId = existing.id.toLong()
                    return@withTransaction
                }
            }
            val safeBalance = StorePointRepository.roundMoney(
                if (account.currentBalance.isNaN() || account.currentBalance.isInfinite()) 0.0
                else account.currentBalance.coerceAtLeast(0.0)
            )
            val normalized = account.copy(
                id = 0,
                name = account.name.trim(),
                phone = account.phone.trim(),
                address = account.address.trim(),
                creditLimit = StorePointRepository.roundMoney(account.creditLimit.coerceAtLeast(0.0)),
                currentBalance = safeBalance,
                notes = account.notes.trim(),
                updatedAt = now,
                createdAt = now
            )
            savedId = customerCreditDao.insertCustomer(normalized)
            if (safeBalance > 0.0) {
                customerCreditDao.insertCreditEntry(
                    CustomerCreditEntry(
                        customerId = savedId.toInt(),
                        timestamp = now,
                        actorUsername = "system",
                        type = "OPENING_BALANCE",
                        amount = safeBalance,
                        balanceAfter = safeBalance,
                        paymentMethod = "SUKI_CREDIT",
                        transactionId = null,
                        notes = "Opening Suki Tab balance"
                    )
                )
            }
        }
        savedId
    }

    suspend fun archiveCustomerAccount(id: Int) = withContext(Dispatchers.IO) {
        customerCreditDao.archiveCustomer(id, System.currentTimeMillis())
    }

    suspend fun deleteCustomerAccount(id: Int) = withContext(Dispatchers.IO) {
        customerCreditDao.deleteCustomer(id)
    }

    /**
     * Atomically records a Suki credit repayment (full or partial).
     * When paid in CASH during an active shift, also records a positive `CASH_IN`
     * entry in `drawer_transactions` so shift cash drawer reconciliation stays accurate.
     */
    suspend fun recordCustomerPayment(
        customerId: Int,
        amount: Double,
        paymentMethod: String,
        actorUsername: String,
        notes: String = ""
    ): CustomerCreditEntry = withContext(Dispatchers.IO) {
        val normAmount = StorePointRepository.roundMoney(amount)
        require(normAmount > 0.0) { "Payment amount must be greater than zero." }
        val now = System.currentTimeMillis()
        lateinit var createdEntry: CustomerCreditEntry

        db.withTransaction {
            val customer = customerCreditDao.getCustomerByIdSync(customerId)
                ?: throw IllegalArgumentException("Customer account #$customerId not found.")
            if (customer.currentBalance <= 0.0) {
                throw IllegalStateException("${customer.name} has no outstanding balance to pay.")
            }
            val appliedAmount = StorePointRepository.roundMoney(normAmount.coerceAtMost(customer.currentBalance))
            val newBalance = StorePointRepository.roundMoney((customer.currentBalance - appliedAmount).coerceAtLeast(0.0))

            customerCreditDao.updateCustomer(
                customer.copy(
                    currentBalance = newBalance,
                    updatedAt = now
                )
            )
            val entry = CustomerCreditEntry(
                customerId = customer.id,
                timestamp = now,
                actorUsername = actorUsername,
                type = "PAYMENT",
                amount = appliedAmount,
                balanceAfter = newBalance,
                paymentMethod = paymentMethod.uppercase(),
                transactionId = null,
                notes = notes.trim().ifBlank { "Suki Tab repayment (${paymentMethod.uppercase()})" }
            )
            val entryId = customerCreditDao.insertCreditEntry(entry).toInt()
            createdEntry = entry.copy(id = entryId)

            if (paymentMethod.equals("CASH", ignoreCase = true)) {
                val activeSession = cashierSessionDao.getActiveSessionSync()
                if (activeSession != null) {
                    drawerTransactionDao.insertDrawerTransaction(
                        DrawerTransaction(
                            sessionId = activeSession.id,
                            timestamp = now,
                            cashierUsername = actorUsername,
                            amount = appliedAmount,
                            type = "CASH_IN",
                            reason = "Suki Tab Repayment: ${customer.name}"
                        )
                    )
                }
            }
        }
        createdEntry
    }

    // --- Offline Thermal Print Queue ---
    val allPendingPrintJobs: Flow<List<PendingPrintJob>> = pendingPrintJobDao.getAllPendingJobs()

    suspend fun getAllPendingPrintJobsSync(): List<PendingPrintJob> =
        pendingPrintJobDao.getAllPendingJobsSync()

    suspend fun enqueuePrintJob(
        jobType: String,
        label: String,
        payloadBase64: String,
        error: String
    ): Long = withContext(Dispatchers.IO) {
        pendingPrintJobDao.insertJob(
            PendingPrintJob(
                createdAt = System.currentTimeMillis(),
                jobType = jobType,
                label = label,
                payloadBase64 = payloadBase64,
                lastError = error.take(240),
                retryCount = 0
            )
        )
    }

    suspend fun markPrintJobRetryFailed(jobId: Int, error: String) = withContext(Dispatchers.IO) {
        pendingPrintJobDao.markRetryFailed(jobId, error.take(240))
    }

    suspend fun deletePrintJob(jobId: Int) = withContext(Dispatchers.IO) {
        pendingPrintJobDao.deleteJob(jobId)
    }

    suspend fun clearAllPrintJobs() = withContext(Dispatchers.IO) {
        pendingPrintJobDao.clearAllJobs()
    }

    // --- Mid-Shift Live X-Reading (Non-Resetting) ---
    suspend fun computeActiveShiftXReading(): ShiftXReadingSummary? = withContext(Dispatchers.IO) {
        val session = cashierSessionDao.getActiveSessionSync() ?: return@withContext null
        val now = System.currentTimeMillis()
        val sdb = db.openHelper.readableDatabase

        var txCount = 0
        var grossSales = 0.0
        var totalTax = 0.0
        var cashSales = 0.0
        var gcashSales = 0.0
        var mayaSales = 0.0
        var cardSales = 0.0
        var sukiCreditSales = 0.0
        var otherDigitalSales = 0.0

        sdb.query(
            "SELECT totalAmount, taxAmount, paymentMethod FROM transactions WHERE timestamp >= ? AND status NOT IN ('VOIDED', 'CANCELLED')",
            arrayOf<Any>(session.startTime)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val total = cursor.getDouble(0)
                val tax = cursor.getDouble(1)
                val method = cursor.getString(2).uppercase()
                txCount++
                grossSales += total
                totalTax += tax
                when {
                    method == "CASH" -> cashSales += total
                    method == "SUKI_CREDIT" -> sukiCreditSales += total
                    method.contains("GCASH") -> gcashSales += total
                    method.contains("MAYA") -> mayaSales += total
                    method == "CARD" -> cardSales += total
                    else -> otherDigitalSales += total
                }
            }
        }

        val totalDiscounts = sdb.query(
            """
            SELECT COALESCE(SUM(ti.discountAmount), 0.0)
            FROM transaction_items ti
            INNER JOIN transactions t ON t.id = ti.transactionId
            WHERE t.timestamp >= ? AND t.status NOT IN ('VOIDED', 'CANCELLED')
            """.trimIndent(),
            arrayOf<Any>(session.startTime)
        ).use { c -> if (c.moveToFirst()) c.getDouble(0) else 0.0 }

        var drawerCashIn = 0.0
        var drawerPayouts = 0.0
        var cashRefunds = 0.0
        val drawerTxs = drawerTransactionDao.getDrawerTransactionsBySessionSync(session.id)
        for (d in drawerTxs) {
            when {
                d.amount > 0.0 -> drawerCashIn += d.amount
                d.reason.startsWith("Refund Return", ignoreCase = true) -> cashRefunds += kotlin.math.abs(d.amount)
                else -> drawerPayouts += kotlin.math.abs(d.amount)
            }
        }

        val expectedCash = StorePointRepository.roundMoney(
            session.startingCash + cashSales + drawerCashIn - drawerPayouts - cashRefunds
        )

        ShiftXReadingSummary(
            sessionId = session.id,
            cashierUsername = session.cashierUsername,
            shiftStartTime = session.startTime,
            generatedAt = now,
            startingCash = StorePointRepository.roundMoney(session.startingCash),
            transactionCount = txCount,
            grossSales = StorePointRepository.roundMoney(grossSales),
            totalDiscounts = StorePointRepository.roundMoney(totalDiscounts),
            totalTax = StorePointRepository.roundMoney(totalTax),
            cashSales = StorePointRepository.roundMoney(cashSales),
            gcashSales = StorePointRepository.roundMoney(gcashSales),
            mayaSales = StorePointRepository.roundMoney(mayaSales),
            cardSales = StorePointRepository.roundMoney(cardSales),
            sukiCreditSales = StorePointRepository.roundMoney(sukiCreditSales),
            otherDigitalSales = StorePointRepository.roundMoney(otherDigitalSales),
            drawerCashIn = StorePointRepository.roundMoney(drawerCashIn),
            drawerPayouts = StorePointRepository.roundMoney(drawerPayouts),
            cashRefunds = StorePointRepository.roundMoney(cashRefunds),
            expectedCashInDrawer = expectedCash
        )
    }

    // --- Offline CSV Bulk Product Upsert ---
    suspend fun applyCatalogCsvImport(rows: List<CatalogCsvRow>): CsvCatalogImportResult =
        withContext(Dispatchers.IO) {
            var insertedCount = 0
            var updatedCount = 0
            var createdCategoriesCount = 0

            db.withTransaction {
                val categoryByName = categoryDao.getAllCategoriesIncludingArchivedSync()
                    .associateBy { it.name.trim().lowercase() }
                    .toMutableMap()

                val existingProducts = productDao.getAllProductsIncludingArchivedSync()
                val existingByBarcode = existingProducts
                    .filter { it.barcode.isNotBlank() }
                    .associateBy { it.barcode.trim().lowercase() }
                    .toMutableMap()
                val existingByName = existingProducts
                    .associateBy { it.name.trim().lowercase() }
                    .toMutableMap()

                for (row in rows) {
                    val catKey = row.categoryName.trim().ifBlank { "General" }.lowercase()
                    val category = categoryByName[catKey]?.let { cat ->
                        if (cat.isArchived) {
                            categoryDao.unarchiveCategory(cat.id)
                            val activeCat = cat.copy(isArchived = false)
                            categoryByName[catKey] = activeCat
                            activeCat
                        } else {
                            cat
                        }
                    } ?: run {
                        val displayName = row.categoryName.trim().ifBlank { "General" }
                        categoryDao.insertCategory(Category(name = displayName))
                        val fresh = categoryDao.getAllCategoriesIncludingArchivedSync()
                            .firstOrNull { it.name.trim().equals(displayName, ignoreCase = true) }
                            ?: Category(id = 1, name = displayName)
                        categoryByName[catKey] = fresh
                        createdCategoriesCount++
                        fresh
                    }

                    val barcodeKey = row.barcode.trim().lowercase()
                    val nameKey = row.name.trim().lowercase()
                    val matched = (if (barcodeKey.isNotEmpty()) existingByBarcode[barcodeKey] else null)
                        ?: existingByName[nameKey]

                    if (matched != null) {
                        val updated = matched.copy(
                            name = row.name.trim(),
                            categoryId = category.id,
                            price = StorePointRepository.roundMoney(row.price.coerceAtLeast(0.0)),
                            cost = StorePointRepository.roundMoney(row.cost.coerceAtLeast(0.0)),
                            stockCount = row.stockCount.coerceAtLeast(0),
                            barcode = row.barcode.trim().ifBlank { matched.barcode },
                            baseUom = row.baseUom.trim().ifBlank { matched.baseUom },
                            expirationDate = row.expirationDate?.trim()?.takeIf { it.isNotEmpty() }
                                ?: matched.expirationDate,
                            isQuickSellPinned = row.isQuickSellPinned,
                            isArchived = false
                        )
                        // CRITICAL: Use @Update so existing product_variants & bill_of_materials
                        // are NEVER cascade-deleted during CSV re-imports.
                        productDao.updateProduct(updated)
                        existingByName[nameKey] = updated
                        if (updated.barcode.isNotBlank()) {
                            existingByBarcode[updated.barcode.lowercase()] = updated
                        }
                        updatedCount++
                    } else {
                        val newProduct = Product(
                            name = row.name.trim(),
                            categoryId = category.id,
                            price = StorePointRepository.roundMoney(row.price.coerceAtLeast(0.0)),
                            stockCount = row.stockCount.coerceAtLeast(0),
                            barcode = row.barcode.trim(),
                            expirationDate = row.expirationDate?.trim()?.takeIf { it.isNotEmpty() },
                            cost = StorePointRepository.roundMoney(row.cost.coerceAtLeast(0.0)),
                            baseUom = row.baseUom.trim().ifBlank { "pc" },
                            isQuickSellPinned = row.isQuickSellPinned
                        )
                        val newId = productDao.insertProductReturningId(newProduct).toInt()
                        val persisted = newProduct.copy(id = newId)
                        existingByName[nameKey] = persisted
                        if (persisted.barcode.isNotBlank()) {
                            existingByBarcode[persisted.barcode.lowercase()] = persisted
                        }
                        insertedCount++
                    }
                }
            }

            CsvCatalogImportResult(
                insertedCount = insertedCount,
                updatedCount = updatedCount,
                createdCategoriesCount = createdCategoriesCount
            )
        }
}
