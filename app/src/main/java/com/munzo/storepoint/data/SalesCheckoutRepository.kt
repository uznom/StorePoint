package com.munzo.storepoint.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Domain delegate handling checkout, transactions, parked orders, and atomic returns/refunds.
 */
internal class SalesCheckoutRepository(private val db: AppDatabase) {

    private val transactionDao = db.transactionDao
    private val parkedTransactionDao = db.parkedTransactionDao
    private val returnDao = db.returnDao
    private val walletLedgerDao = db.walletLedgerDao

    // --- Transactions ---
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val allTransactionItems: Flow<List<TransactionItem>> = transactionDao.getAllTransactionItems()
    val allTransactionsWithItems: Flow<List<TransactionWithItems>> = transactionDao.getAllTransactionsWithItems()

    fun getTransactionsByCashier(cashier: String): Flow<List<Transaction>> =
        transactionDao.getTransactionsByCashier(cashier)

    fun getTransactionItems(transactionId: Int): Flow<List<TransactionItem>> =
        transactionDao.getTransactionItems(transactionId)

    suspend fun updateTransactionStatus(txId: Int, status: String) {
        transactionDao.updateTransactionStatus(txId, status)
    }

    suspend fun getTransactionById(txId: Int): Transaction? =
        transactionDao.getTransactionById(txId)

    suspend fun getTransactionItemsSync(txId: Int): List<TransactionItem> =
        transactionDao.getTransactionItemsSync(txId)

    suspend fun getTransactionWithItems(id: Int): TransactionWithItems? =
        transactionDao.getTransactionWithItems(id)

    suspend fun checkout(
        cashierUsername: String,
        cartItems: List<CartItemDetails>,
        paymentMethod: String,
        cashPaid: Double,
        subtotal: Double,
        taxAmount: Double,
        totalAmount: Double,
        gcashDelta: Double = 0.0,
        smartLoadDelta: Double = 0.0,
        globeLoadDelta: Double = 0.0,
        customerName: String = "",
        gcashNotes: String? = null,
        smartLoadNotes: String? = null,
        globeLoadNotes: String? = null,
        customerId: Int? = null
    ): Transaction = withContext(Dispatchers.IO) {
        require(cartItems.isNotEmpty()) { "Cannot checkout with an empty cart." }
        require(
            !subtotal.isNaN() && !subtotal.isInfinite() && subtotal >= 0.0 &&
                !taxAmount.isNaN() && !taxAmount.isInfinite() && taxAmount >= 0.0 &&
                !totalAmount.isNaN() && !totalAmount.isInfinite() && totalAmount >= 0.0 &&
                !cashPaid.isNaN() && !cashPaid.isInfinite() && cashPaid >= 0.0
        ) {
            "Checkout monetary amounts must be valid non-negative numbers."
        }
        require(
            !gcashDelta.isNaN() && !gcashDelta.isInfinite() &&
                !smartLoadDelta.isNaN() && !smartLoadDelta.isInfinite() &&
                !globeLoadDelta.isNaN() && !globeLoadDelta.isInfinite()
        ) {
            "Wallet movement deltas must be finite numbers."
        }

        val timestamp = System.currentTimeMillis()
        var createdTransactionId = 0

        val normSubtotal = StorePointRepository.roundMoney(subtotal)
        val normTax = StorePointRepository.roundMoney(taxAmount)
        val normTotal = StorePointRepository.roundMoney(totalAmount)
        val normCashPaid = StorePointRepository.roundMoney(cashPaid)
        val normGcashDelta = StorePointRepository.roundMoney(gcashDelta)
        val normSmartDelta = StorePointRepository.roundMoney(smartLoadDelta)
        val normGlobeDelta = StorePointRepository.roundMoney(globeLoadDelta)
        val normChange = if (paymentMethod == "CASH") {
            StorePointRepository.roundMoney(normCashPaid - normTotal)
        } else 0.0
        var resolvedCustomerName = customerName

        db.withTransaction {
            if (paymentMethod == "CASH" && normTotal > 0.0 && normCashPaid < normTotal) {
                throw IllegalArgumentException(
                    "Insufficient payment: received $normCashPaid but the total is $normTotal."
                )
            }

            var sukiCustomerBalanceBefore = 0.0
            if (paymentMethod.equals("SUKI_CREDIT", ignoreCase = true)) {
                val cid = customerId
                    ?: throw IllegalArgumentException("Select a Suki Tab customer account before charging credit.")
                val account = db.customerCreditDao.getCustomerByIdSync(cid)
                    ?: throw IllegalArgumentException("Suki Tab customer account #$cid not found.")
                if (!account.isActive) {
                    throw IllegalStateException("Suki Tab account for ${account.name} is inactive.")
                }
                if (account.creditLimit <= 0.0) {
                    throw IllegalStateException("${account.name} has no credit limit configured.")
                }
                val projectedBalance = StorePointRepository.roundMoney(account.currentBalance + normTotal)
                if (projectedBalance > StorePointRepository.roundMoney(account.creditLimit) + 0.001) {
                    throw IllegalStateException(
                        "Credit limit exceeded for ${account.name}: balance ${account.currentBalance} + $normTotal exceeds limit ${account.creditLimit}."
                    )
                }
                sukiCustomerBalanceBefore = account.currentBalance
                if (resolvedCustomerName.isBlank()) {
                    resolvedCustomerName = account.name
                }
            }

            for (item in cartItems) {
                if (item.quantity <= 0 || item.multiplier <= 0) {
                    throw IllegalArgumentException("Invalid quantity for ${item.product.name}.")
                }
                if (item.selectedPrice.isNaN() || item.selectedPrice.isInfinite() || item.selectedPrice < 0.0) {
                    throw IllegalArgumentException("Invalid unit price for ${item.product.name}.")
                }
                if (item.discountPerUnit.isNaN() || item.discountPerUnit.isInfinite() || item.discountPerUnit < 0.0) {
                    throw IllegalArgumentException("Invalid discount for ${item.product.name}.")
                }
                val effectiveQty = item.quantity * item.multiplier
                if (effectiveQty <= 0) {
                    throw IllegalArgumentException("Invalid quantity for ${item.product.name}.")
                }
            }

            if (normGcashDelta != 0.0) {
                val balance = queryGcashBalance()
                if (StorePointRepository.roundMoney(balance + normGcashDelta) < 0.0) {
                    throw IllegalStateException(
                        "Insufficient GCash wallet balance for this transaction (balance: $balance, required: ${-normGcashDelta})."
                    )
                }
            }

            if (normSmartDelta != 0.0) {
                val balance = querySmartLoadBalance()
                if (StorePointRepository.roundMoney(balance + normSmartDelta) < 0.0) {
                    throw IllegalStateException(
                        "Insufficient Smart Load wallet balance for this transaction (balance: $balance, required: ${-normSmartDelta})."
                    )
                }
            }

            if (normGlobeDelta != 0.0) {
                val balance = queryGlobeLoadBalance()
                if (StorePointRepository.roundMoney(balance + normGlobeDelta) < 0.0) {
                    throw IllegalStateException(
                        "Insufficient Globe Load wallet balance for this transaction (balance: $balance, required: ${-normGlobeDelta})."
                    )
                }
            }

            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO transactions (timestamp, cashierUsername, subtotal, taxAmount, totalAmount, paymentMethod, cashPaid, changeAmount, status, customerName) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    timestamp, cashierUsername, normSubtotal, normTax, normTotal,
                    paymentMethod, normCashPaid, normChange, "COMPLETED", resolvedCustomerName
                )
            )
            createdTransactionId = lastInsertRowId()

            if (paymentMethod.equals("SUKI_CREDIT", ignoreCase = true) && customerId != null) {
                val newBalance = StorePointRepository.roundMoney(sukiCustomerBalanceBefore + normTotal)
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE customer_accounts SET currentBalance = ?, updatedAt = ? WHERE id = ?",
                    arrayOf<Any?>(newBalance, timestamp, customerId)
                )
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO customer_credit_ledger (customerId, timestamp, actorUsername, type, amount, balanceAfter, paymentMethod, transactionId, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        customerId, timestamp, cashierUsername, "CHARGE", normTotal,
                        newBalance, "SUKI_CREDIT", createdTransactionId,
                        "POS Sale #$createdTransactionId"
                    )
                )
            }

            for (item in cartItems) {
                val product = item.product
                val uomName = item.uomName
                val productName = if (
                    uomName.equals("Base Unit", ignoreCase = true) ||
                    uomName.equals("Piece", ignoreCase = true) ||
                    uomName.equals("Stick", ignoreCase = true)
                ) {
                    product.name
                } else {
                    "${product.name} ($uomName)"
                }
                val unitDiscount = StorePointRepository.roundMoney(
                    item.discountPerUnit.coerceAtLeast(0.0).coerceAtMost(item.selectedPrice)
                )
                val normItemPrice = StorePointRepository.roundMoney(
                    (item.selectedPrice - unitDiscount).coerceAtLeast(0.0)
                )
                val lineDiscountTotal = StorePointRepository.roundMoney(unitDiscount * item.quantity)
                val normItemCost = StorePointRepository.roundMoney(product.cost * item.multiplier)
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO transaction_items (transactionId, productId, productName, price, quantity, cost, uomName, discountAmount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        createdTransactionId, product.id, productName,
                        normItemPrice, item.quantity, normItemCost, uomName, lineDiscountTotal
                    )
                )
                if (product.id > 0 && product.trackInventory) {
                    val effectiveQty = item.quantity * item.multiplier
                    db.openHelper.writableDatabase.execSQL(
                        "UPDATE products SET stockCount = stockCount - ? WHERE id = ? AND stockCount >= ?",
                        arrayOf<Any?>(effectiveQty, product.id, effectiveQty)
                    )
                    if (queryChanges() == 0) {
                        val currentStock = queryStock(product.id)
                        throw IllegalStateException(
                            "Insufficient stock for ${product.name}: requires $effectiveQty base units, only $currentStock available."
                        )
                    }
                }
            }

            var resolvedGcashNotes = gcashNotes
            var resolvedSmartNotes = smartLoadNotes
            var resolvedGlobeNotes = globeLoadNotes

            if (resolvedGcashNotes == null || resolvedSmartNotes == null || resolvedGlobeNotes == null) {
                for (ci in cartItems) {
                    val pName = ci.product.name
                    val feeMatch = """Fee:\s*([\d.]+)""".toRegex(RegexOption.IGNORE_CASE).find(pName)
                    val feeAmt = feeMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                    val amtMatch = """(?:Amt|Amount):\s*([\d.]+)""".toRegex(RegexOption.IGNORE_CASE).find(pName)
                    val principalAmt = amtMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

                    if (pName.contains("GCash", ignoreCase = true) || pName.contains("Maya", ignoreCase = true)) {
                        if (resolvedGcashNotes == null) {
                            val action = if (pName.contains("Cash Out", ignoreCase = true)) "Cash-Out" else "Cash-In"
                            val svc = if (pName.contains("Maya", ignoreCase = true)) "Maya" else "GCash"
                            resolvedGcashNotes = "Fee Profit: ₱${String.format(Locale.US, "%.2f", feeAmt)} | $svc $action ₱${String.format(Locale.US, "%.2f", principalAmt)}"
                        }
                    }
                    if (pName.contains("Load", ignoreCase = true)) {
                        val isSmartOrDito = pName.contains("Smart", ignoreCase = true) ||
                            pName.contains("TNT", ignoreCase = true) ||
                            pName.contains("DITO", ignoreCase = true)
                        val netProfit = feeAmt + (principalAmt * 0.02)
                        val profitStr = "Fee Profit: ₱${String.format(Locale.US, "%.2f", netProfit)} (Markup: ₱${String.format(Locale.US, "%.2f", feeAmt)} + 2% Rebate) | Load ₱${String.format(Locale.US, "%.2f", principalAmt)}"
                        if (isSmartOrDito && resolvedSmartNotes == null) {
                            resolvedSmartNotes = profitStr
                        } else if (!isSmartOrDito && resolvedGlobeNotes == null) {
                            resolvedGlobeNotes = profitStr
                        }
                    }
                }
            }

            if (normGcashDelta != 0.0) {
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE store_config SET gcashBalance = ROUND(gcashBalance + ?, 2) WHERE id = 1",
                    arrayOf<Any?>(normGcashDelta)
                )
                logWalletConsumptionSync(
                    StorePointRepository.WalletType.GCASH,
                    normGcashDelta,
                    cashierUsername,
                    createdTransactionId,
                    resolvedGcashNotes
                )
            }

            if (normSmartDelta != 0.0) {
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE store_config SET smartLoadBalance = ROUND(smartLoadBalance + ?, 2) WHERE id = 1",
                    arrayOf<Any?>(normSmartDelta)
                )
                logWalletConsumptionSync(
                    StorePointRepository.WalletType.SMART,
                    normSmartDelta,
                    cashierUsername,
                    createdTransactionId,
                    resolvedSmartNotes
                )
            }

            if (normGlobeDelta != 0.0) {
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE store_config SET globeLoadBalance = ROUND(globeLoadBalance + ?, 2) WHERE id = 1",
                    arrayOf<Any?>(normGlobeDelta)
                )
                logWalletConsumptionSync(
                    StorePointRepository.WalletType.GLOBE,
                    normGlobeDelta,
                    cashierUsername,
                    createdTransactionId,
                    resolvedGlobeNotes
                )
            }
        }

        Transaction(
            id = createdTransactionId,
            timestamp = timestamp,
            cashierUsername = cashierUsername,
            subtotal = normSubtotal,
            taxAmount = normTax,
            totalAmount = normTotal,
            paymentMethod = paymentMethod,
            cashPaid = normCashPaid,
            changeAmount = normChange,
            status = "COMPLETED",
            customerName = resolvedCustomerName
        )
    }

    // --- Parked / Held Transactions ---
    val allParkedTransactions: Flow<List<ParkedTransaction>> = parkedTransactionDao.getAllParkedTransactions()
    val allParkedWithItems: Flow<List<ParkedTransactionWithItems>> = parkedTransactionDao.getAllParkedWithItems()

    suspend fun getParkedItems(parkedId: Int): List<ParkedTransactionItem> =
        parkedTransactionDao.getParkedItems(parkedId)

    suspend fun parkTransaction(note: String, items: List<Pair<Product, Int>>) {
        parkTransactionWithDetails(
            note = note,
            items = items.map { (product, qty) ->
                CartItemDetails(
                    product = product,
                    quantity = qty,
                    uomName = "Base Unit",
                    multiplier = 1,
                    selectedPrice = product.price,
                    discountPerUnit = 0.0
                )
            }
        )
    }

    suspend fun parkTransactionWithDetails(
        note: String,
        items: List<CartItemDetails>
    ) = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        db.withTransaction {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO parked_transactions (timestamp, note) VALUES (?, ?)",
                arrayOf<Any?>(timestamp, note)
            )
            val parkedId = lastInsertRowId()
            for (item in items) {
                val product = item.product
                val normPrice = StorePointRepository.roundMoney(item.selectedPrice)
                val normDiscount = StorePointRepository.roundMoney(item.discountPerUnit.coerceAtLeast(0.0))
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO parked_transaction_items (parkedTransactionId, productId, productName, price, quantity, uomName, uomMultiplier, discountAmount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        parkedId, product.id, product.name, normPrice, item.quantity,
                        item.uomName, item.multiplier.coerceAtLeast(1), normDiscount
                    )
                )
            }
        }
    }

    suspend fun deleteParkedTransaction(parkedId: Int) = withContext(Dispatchers.IO) {
        db.withTransaction {
            parkedTransactionDao.deleteParkedItems(parkedId)
            parkedTransactionDao.deleteParked(parkedId)
        }
    }

    // --- Returns & Refunds ---
    val allReturns: Flow<List<ReturnTransaction>> = returnDao.getAllReturns()
    val allReturnsWithItems: Flow<List<ReturnTransactionWithItems>> = returnDao.getAllReturnsWithItems()

    suspend fun getAllReturnsSync(): List<ReturnTransaction> = returnDao.getAllReturnsSync()

    fun getReturnsForTransaction(transactionId: Int): Flow<List<ReturnTransaction>> =
        returnDao.getReturnsForTransaction(transactionId)

    fun getReturnItems(returnTxId: Int): Flow<List<ReturnItem>> =
        returnDao.getReturnItems(returnTxId)

    suspend fun getReturnItemsSync(returnTxId: Int): List<ReturnItem> =
        returnDao.getReturnItemsSync(returnTxId)

    suspend fun getReturnWithItems(id: Int): ReturnTransactionWithItems? =
        returnDao.getReturnWithItems(id)

    suspend fun insertReturnTransaction(returnTx: ReturnTransaction): Long =
        returnDao.insertReturnTransaction(returnTx)

    suspend fun insertReturnItem(item: ReturnItem) {
        returnDao.insertReturnItem(item)
    }

    suspend fun processReturnAtomic(
        returnTx: ReturnTransaction,
        items: List<ReturnItem>,
        cashRefund: Boolean,
        sessionId: Int?,
        newOriginalStatus: String
    ): ReturnTransaction = withContext(Dispatchers.IO) {
        require(items.isNotEmpty()) { "At least one item is required to process a return." }
        require(
            !returnTx.totalRefundAmount.isNaN() &&
                !returnTx.totalRefundAmount.isInfinite() &&
                returnTx.totalRefundAmount > 0.0
        ) {
            "Refund amount must be greater than zero."
        }
        for (item in items) {
            require(item.quantity > 0) { "Return quantity for ${item.productName} must be greater than zero." }
            require(!item.unitPrice.isNaN() && !item.unitPrice.isInfinite() && item.unitPrice >= 0.0) {
                "Invalid unit refund price for ${item.productName}."
            }
            require(!item.refundAmount.isNaN() && !item.refundAmount.isInfinite() && item.refundAmount >= 0.0) {
                "Invalid refund line amount for ${item.productName}."
            }
        }

        var createdReturnId = 0
        val normTotalRefund = StorePointRepository.roundMoney(returnTx.totalRefundAmount)
        db.withTransaction {
            val origTx = transactionDao.getTransactionById(returnTx.originalTransactionId)
                ?: throw IllegalArgumentException("Original transaction #${returnTx.originalTransactionId} not found.")
            if (origTx.status.equals("VOIDED", ignoreCase = true) ||
                origTx.status.equals("CANCELLED", ignoreCase = true)
            ) {
                throw IllegalStateException("Cannot refund a voided or cancelled transaction (#${origTx.id}).")
            }

            // Enforce strict atomic anti-over-refund bounds against original lines & prior returns.
            val origItems = transactionDao.getTransactionItemsSync(origTx.id)
            val boughtQtyByProduct = mutableMapOf<Int, Int>()
            for (oi in origItems) {
                boughtQtyByProduct[oi.productId] = (boughtQtyByProduct[oi.productId] ?: 0) + oi.quantity
            }
            val priorReturnedQtyByProduct = queryPriorReturnedQtyByProduct(origTx.id)
            val priorRefundedTotal = queryPriorRefundedTotal(origTx.id)

            if (StorePointRepository.roundMoney(priorRefundedTotal + normTotalRefund) >
                StorePointRepository.roundMoney(origTx.totalAmount) + 0.01
            ) {
                val remainingRefundable = StorePointRepository.roundMoney(
                    (origTx.totalAmount - priorRefundedTotal).coerceAtLeast(0.0)
                )
                throw IllegalStateException(
                    "Refund exceeds original sale amount: remaining refundable is $remainingRefundable, requested $normTotalRefund."
                )
            }

            val requestedQtyByProduct = mutableMapOf<Int, Int>()
            for (item in items) {
                requestedQtyByProduct[item.productId] = (requestedQtyByProduct[item.productId] ?: 0) + item.quantity
            }
            for ((prodId, reqQty) in requestedQtyByProduct) {
                val boughtQty = boughtQtyByProduct[prodId] ?: 0
                val priorQty = priorReturnedQtyByProduct[prodId] ?: 0
                if (boughtQty <= 0) {
                    throw IllegalArgumentException("Product #$prodId was not part of transaction #${origTx.id}.")
                }
                if (priorQty + reqQty > boughtQty) {
                    val remaining = (boughtQty - priorQty).coerceAtLeast(0)
                    throw IllegalStateException(
                        "Cannot return $reqQty unit(s) of product #$prodId: only $remaining returnable unit(s) remain."
                    )
                }
            }

            val totalOrigQty = boughtQtyByProduct.values.sum()
            val totalReturnedAfter = priorReturnedQtyByProduct.values.sum() + requestedQtyByProduct.values.sum()
            val authoritativeStatus = when {
                totalOrigQty > 0 && totalReturnedAfter >= totalOrigQty -> "REFUNDED"
                totalReturnedAfter > 0 -> "PARTIALLY_REFUNDED"
                else -> newOriginalStatus
            }

            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO return_transactions (originalTransactionId, timestamp, cashierUsername, totalRefundAmount, refundMethod, returnReason, customerName, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    returnTx.originalTransactionId, returnTx.timestamp, returnTx.cashierUsername,
                    normTotalRefund, returnTx.refundMethod, returnTx.returnReason,
                    returnTx.customerName, returnTx.notes
                )
            )
            createdReturnId = lastInsertRowId()

            for (item in items) {
                val normUnitPrice = StorePointRepository.roundMoney(item.unitPrice)
                val normRefundAmt = StorePointRepository.roundMoney(item.refundAmount)
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO return_items (returnTransactionId, productId, productName, quantity, unitPrice, refundAmount, uomName, restockToInventory) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        createdReturnId, item.productId, item.productName, item.quantity,
                        normUnitPrice, normRefundAmt, item.uomName,
                        if (item.restockToInventory) 1 else 0
                    )
                )
                if (item.restockToInventory && item.productId > 0) {
                    val uomMultiplier = resolveUomMultiplier(item.productId, item.uomName)
                    val baseUnitsToRestock = item.quantity * uomMultiplier
                    val stockBefore = queryStock(item.productId)
                    db.openHelper.writableDatabase.execSQL(
                        "UPDATE products SET stockCount = stockCount + ? WHERE id = ?",
                        arrayOf<Any?>(baseUnitsToRestock, item.productId)
                    )
                    if (queryChanges() > 0) {
                        val stockAfter = queryStock(item.productId)
                        db.inventoryMovementDao.insertMovementBlocking(
                            InventoryMovementEntity(
                                productId = item.productId,
                                productName = item.productName,
                                timestamp = returnTx.timestamp,
                                actorUsername = returnTx.cashierUsername,
                                movementType = "RETURN_RESTOCK",
                                quantityDelta = baseUnitsToRestock.toDouble(),
                                stockBefore = stockBefore,
                                stockAfter = stockAfter,
                                reference = "RET-$createdReturnId",
                                notes = "Customer return restock from Tx #${origTx.id}"
                            )
                        )
                    }
                }
            }

            if (cashRefund && sessionId != null) {
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO drawer_transactions (sessionId, timestamp, cashierUsername, amount, type, reason) VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        sessionId, System.currentTimeMillis(), returnTx.cashierUsername,
                        -normTotalRefund, "PAYOUT",
                        "Refund Return #$createdReturnId (Tx #${returnTx.originalTransactionId})"
                    )
                )
            }

            // Suki Tab Credit Reversal: if the original sale was charged to Suki Tab (or refunded
            // back to Suki credit), credit back the customer's balance and record an auditable REFUND entry.
            if (origTx.paymentMethod.equals("SUKI_CREDIT", ignoreCase = true) ||
                returnTx.refundMethod.equals("SUKI_CREDIT", ignoreCase = true)
            ) {
                val sukiCustomerId = querySukiCustomerIdForTransaction(origTx.id)
                if (sukiCustomerId != null) {
                    val customer = db.customerCreditDao.getCustomerByIdSync(sukiCustomerId)
                    if (customer != null && customer.currentBalance > 0.0) {
                        val creditReduction = StorePointRepository.roundMoney(
                            normTotalRefund.coerceAtMost(customer.currentBalance)
                        )
                        val newBalance = StorePointRepository.roundMoney(
                            (customer.currentBalance - creditReduction).coerceAtLeast(0.0)
                        )
                        val now = System.currentTimeMillis()
                        db.openHelper.writableDatabase.execSQL(
                            "UPDATE customer_accounts SET currentBalance = ?, updatedAt = ? WHERE id = ?",
                            arrayOf<Any?>(newBalance, now, sukiCustomerId)
                        )
                        db.openHelper.writableDatabase.execSQL(
                            "INSERT INTO customer_credit_ledger (customerId, timestamp, actorUsername, type, amount, balanceAfter, paymentMethod, transactionId, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf<Any?>(
                                sukiCustomerId, now, returnTx.cashierUsername, "REFUND",
                                creditReduction, newBalance, "SUKI_CREDIT", origTx.id,
                                "Refund Return #$createdReturnId (Tx #${origTx.id})"
                            )
                        )
                    }
                }
            }

            db.openHelper.writableDatabase.execSQL(
                "UPDATE transactions SET status = ? WHERE id = ?",
                arrayOf<Any?>(authoritativeStatus, returnTx.originalTransactionId)
            )
        }
        returnTx.copy(id = createdReturnId, totalRefundAmount = normTotalRefund)
    }

    private fun queryPriorReturnedQtyByProduct(originalTxId: Int): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        val cursor = db.query(
            """
            SELECT ri.productId AS pid, COALESCE(SUM(ri.quantity), 0) AS qty
            FROM return_items ri
            INNER JOIN return_transactions rt ON rt.id = ri.returnTransactionId
            WHERE rt.originalTransactionId = ?
            GROUP BY ri.productId
            """.trimIndent(),
            arrayOf<Any?>(originalTxId)
        )
        cursor.use {
            while (it.moveToNext()) {
                result[it.getInt(it.getColumnIndexOrThrow("pid"))] = it.getInt(it.getColumnIndexOrThrow("qty"))
            }
        }
        return result
    }

    private fun queryPriorRefundedTotal(originalTxId: Int): Double {
        val cursor = db.query(
            "SELECT COALESCE(SUM(totalRefundAmount), 0.0) AS total FROM return_transactions WHERE originalTransactionId = ?",
            arrayOf<Any?>(originalTxId)
        )
        return cursor.use {
            if (it.moveToNext()) StorePointRepository.roundMoney(it.getDouble(it.getColumnIndexOrThrow("total"))) else 0.0
        }
    }

    private fun querySukiCustomerIdForTransaction(originalTxId: Int): Int? {
        val cursor = db.query(
            "SELECT customerId FROM customer_credit_ledger WHERE transactionId = ? AND type = 'CHARGE' LIMIT 1",
            arrayOf<Any?>(originalTxId)
        )
        return cursor.use {
            if (it.moveToNext()) it.getInt(0) else null
        }
    }

    // --- Raw transactional helpers ---

    private fun queryStock(productId: Int): Int {
        val cursor = db.query("SELECT stockCount AS s FROM products WHERE id = ?", arrayOf<Any?>(productId))
        return cursor.use {
            if (it.moveToNext()) it.getInt(it.getColumnIndexOrThrow("s")) else 0
        }
    }

    private fun queryGcashBalance(): Double {
        val cursor = db.query("SELECT gcashBalance AS bal FROM store_config WHERE id = 1", null)
        return cursor.use {
            if (it.moveToNext()) it.getDouble(it.getColumnIndexOrThrow("bal")) else 0.0
        }
    }

    private fun querySmartLoadBalance(): Double {
        val cursor = db.query("SELECT smartLoadBalance AS bal FROM store_config WHERE id = 1", null)
        return cursor.use {
            if (it.moveToNext()) it.getDouble(it.getColumnIndexOrThrow("bal")) else 0.0
        }
    }

    private fun queryGlobeLoadBalance(): Double {
        val cursor = db.query("SELECT globeLoadBalance AS bal FROM store_config WHERE id = 1", null)
        return cursor.use {
            if (it.moveToNext()) it.getDouble(it.getColumnIndexOrThrow("bal")) else 0.0
        }
    }

    private fun lastInsertRowId(): Int =
        db.openHelper.writableDatabase.compileStatement("SELECT last_insert_rowid()").simpleQueryForLong().toInt()

    private fun queryChanges(): Int =
        db.openHelper.writableDatabase.compileStatement("SELECT changes()").simpleQueryForLong().toInt()

    private fun resolveUomMultiplier(productId: Int, uomName: String): Int {
        if (uomName.equals("pc", ignoreCase = true) ||
            uomName.equals("Piece", ignoreCase = true) ||
            uomName.equals("Stick", ignoreCase = true) ||
            uomName.equals("Base Unit", ignoreCase = true)
        ) {
            return 1
        }

        val cursor = db.query(
            """SELECT hasStick10s, hasStick20s, hasReam, hasMasterCase,
                      hasCustomUom, customUomName, customUomMultiplier
               FROM products WHERE id = ?""",
            arrayOf<Any?>(productId)
        )
        cursor.use {
            if (it.moveToNext()) {
                if (it.getInt(it.getColumnIndexOrThrow("hasStick10s")) == 1 &&
                    uomName.equals("10s Pack", ignoreCase = true)
                ) return 10
                if (it.getInt(it.getColumnIndexOrThrow("hasStick20s")) == 1 &&
                    uomName.equals("20s Pack", ignoreCase = true)
                ) return 20
                if (it.getInt(it.getColumnIndexOrThrow("hasReam")) == 1 &&
                    uomName.equals("Ream", ignoreCase = true)
                ) return 200
                if (it.getInt(it.getColumnIndexOrThrow("hasMasterCase")) == 1 &&
                    uomName.equals("Master Case", ignoreCase = true)
                ) return 10000
                if (it.getInt(it.getColumnIndexOrThrow("hasCustomUom")) == 1) {
                    val customName = it.getString(it.getColumnIndexOrThrow("customUomName"))
                    if (uomName.equals(customName, ignoreCase = true)) {
                        return it.getInt(it.getColumnIndexOrThrow("customUomMultiplier"))
                    }
                }
            }
        }

        val variantCursor = db.query(
            "SELECT multiplier FROM product_variants WHERE productId = ? AND variantName = ? LIMIT 1",
            arrayOf<Any?>(productId, uomName)
        )
        variantCursor.use {
            if (it.moveToNext()) {
                return it.getInt(it.getColumnIndexOrThrow("multiplier"))
            }
        }

        return 1
    }

    private fun logWalletConsumptionSync(
        walletType: String,
        delta: Double,
        cashierUsername: String,
        transactionId: Int,
        customNotes: String? = null,
        customType: String? = null
    ) {
        val balanceColumn = when (walletType) {
            StorePointRepository.WalletType.GCASH -> "gcashBalance"
            StorePointRepository.WalletType.SMART -> "smartLoadBalance"
            StorePointRepository.WalletType.GLOBE -> "globeLoadBalance"
            else -> return
        }
        val balanceAfter = db.openHelper.readableDatabase.query(
            "SELECT $balanceColumn FROM store_config WHERE id = 1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getDouble(0) else 0.0
        }
        val resolvedType = customType ?: if (delta > 0) {
            StorePointRepository.WalletEntryType.INFLOW
        } else {
            StorePointRepository.WalletEntryType.CONSUMED
        }
        val resolvedNotes = customNotes ?: if (delta > 0) {
            "Digital float cash-in inflow"
        } else {
            "Customer load sale"
        }
        walletLedgerDao.insertWalletEntryBlocking(
            WalletLedgerEntry(
                walletType = walletType,
                timestamp = System.currentTimeMillis(),
                actorUsername = cashierUsername,
                type = resolvedType,
                delta = StorePointRepository.roundMoney(delta),
                balanceAfter = StorePointRepository.roundMoney(balanceAfter),
                reference = "TX-$transactionId",
                notes = resolvedNotes
            )
        )
    }
}
