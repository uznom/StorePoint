package com.munzo.storepoint.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Domain delegate handling suppliers, purchase orders, payment schedules, BOMs, and production runs.
 */
internal class PurchasingProductionRepository(private val db: AppDatabase) {

    private val supplierDao = db.supplierDao
    private val purchaseOrderDao = db.purchaseOrderDao
    private val paymentScheduleDao = db.paymentScheduleDao
    private val productionDao = db.productionDao
    private val inventoryMovementDao = db.inventoryMovementDao

    // --- Payment Schedules ---
    val allPaymentSchedules: Flow<List<PaymentSchedule>> = paymentScheduleDao.getAllPaymentSchedules()

    suspend fun savePaymentSchedule(schedule: PaymentSchedule) {
        paymentScheduleDao.insertPaymentSchedule(schedule)
    }

    suspend fun updatePaymentSchedule(schedule: PaymentSchedule) {
        paymentScheduleDao.updatePaymentSchedule(schedule)
    }

    suspend fun deletePaymentSchedule(schedule: PaymentSchedule) {
        paymentScheduleDao.deletePaymentSchedule(schedule)
    }

    suspend fun deletePaymentScheduleById(id: Int) {
        paymentScheduleDao.deletePaymentScheduleById(id)
    }

    // --- Suppliers & Vendors ---
    val allSuppliers: Flow<List<Supplier>> = supplierDao.getAllSuppliers()
    val allSuppliersWithOrders: Flow<List<SupplierWithPurchaseOrders>> = supplierDao.getAllSuppliersWithOrders()

    suspend fun getAllSuppliersSync(): List<Supplier> = supplierDao.getAllSuppliersSync()

    suspend fun getSupplierById(id: Int): Supplier? = supplierDao.getSupplierById(id)

    suspend fun saveSupplier(supplier: Supplier): Long =
        if (supplier.id > 0) {
            supplierDao.updateSupplier(supplier)
            supplier.id.toLong()
        } else {
            supplierDao.insertSupplier(supplier)
        }

    suspend fun updateSupplier(supplier: Supplier) {
        supplierDao.updateSupplier(supplier)
    }

    suspend fun deleteSupplier(id: Int) {
        supplierDao.deleteSupplier(id)
    }

    // --- Purchase Orders (PO) & Receiving ---
    val allPurchaseOrders: Flow<List<PurchaseOrder>> = purchaseOrderDao.getAllPurchaseOrders()
    val allPurchaseOrdersWithItems: Flow<List<PurchaseOrderWithItems>> =
        purchaseOrderDao.getAllPurchaseOrdersWithItems()

    suspend fun getAllPurchaseOrdersSync(): List<PurchaseOrder> =
        purchaseOrderDao.getAllPurchaseOrdersSync()

    suspend fun getPurchaseOrderById(id: Int): PurchaseOrder? =
        purchaseOrderDao.getPurchaseOrderById(id)

    fun getPurchaseOrderItems(poId: Int): Flow<List<PurchaseOrderItem>> =
        purchaseOrderDao.getPurchaseOrderItems(poId)

    suspend fun getPurchaseOrderItemsSync(poId: Int): List<PurchaseOrderItem> =
        purchaseOrderDao.getPurchaseOrderItemsSync(poId)

    suspend fun getPurchaseOrderWithItems(id: Int): PurchaseOrderWithItems? =
        purchaseOrderDao.getPurchaseOrderWithItems(id)

    suspend fun savePurchaseOrder(po: PurchaseOrder): Long =
        if (po.id > 0) {
            purchaseOrderDao.updatePurchaseOrder(po)
            po.id.toLong()
        } else {
            purchaseOrderDao.insertPurchaseOrder(po)
        }

    suspend fun updatePurchaseOrder(po: PurchaseOrder) {
        purchaseOrderDao.updatePurchaseOrder(po)
    }

    suspend fun savePurchaseOrderItem(item: PurchaseOrderItem) {
        purchaseOrderDao.insertPurchaseOrderItem(item)
    }

    suspend fun markPurchaseOrderReceived(
        id: Int,
        status: String,
        receivedDate: Long,
        paymentStatus: String
    ) {
        purchaseOrderDao.markPurchaseOrderReceived(id, status, receivedDate, paymentStatus)
    }

    suspend fun updatePurchaseOrderStatus(id: Int, status: String) {
        purchaseOrderDao.updatePurchaseOrderStatus(id, status)
    }

    suspend fun deletePurchaseOrder(id: Int) = withContext(Dispatchers.IO) {
        db.withTransaction {
            purchaseOrderDao.deletePurchaseOrderItems(id)
            purchaseOrderDao.deletePurchaseOrder(id)
        }
    }

    suspend fun createPurchaseOrderAtomic(
        po: PurchaseOrder,
        items: List<PurchaseOrderItem>
    ): PurchaseOrder = withContext(Dispatchers.IO) {
        var createdPoId = 0
        db.withTransaction {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO purchase_orders (poNumber, supplierId, supplierName, orderDate, expectedDeliveryDate, totalCost, status, paymentStatus, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    po.poNumber, po.supplierId, po.supplierName, po.orderDate,
                    po.expectedDeliveryDate, po.totalCost, po.status, po.paymentStatus, po.notes
                )
            )
            createdPoId = lastInsertRowId()
            for (item in items) {
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO purchase_order_items (purchaseOrderId, productId, productName, quantityOrdered, quantityReceived, unitCost, totalCost, uomName) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        createdPoId, item.productId, item.productName, item.quantityOrdered,
                        item.quantityReceived, item.unitCost, item.totalCost, item.uomName
                    )
                )
            }
        }
        po.copy(id = createdPoId)
    }

    suspend fun receivePurchaseOrderAtomic(
        poId: Int,
        receivedItems: List<StorePointRepository.PurchaseOrderReceiveLine>,
        receivedDate: Long,
        paymentStatus: String
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val existingPo = purchaseOrderDao.getPurchaseOrderById(poId)
                ?: throw IllegalArgumentException("Purchase order #$poId not found.")
            if (existingPo.status.equals("RECEIVED", ignoreCase = true)) {
                throw IllegalStateException("Purchase order ${existingPo.poNumber} has already been received.")
            }
            if (existingPo.status.equals("CANCELED", ignoreCase = true) ||
                existingPo.status.equals("CANCELLED", ignoreCase = true)
            ) {
                throw IllegalStateException("Cannot receive a canceled purchase order (${existingPo.poNumber}).")
            }

            for (line in receivedItems) {
                if (line.quantityReceived <= 0) continue
                require(!line.newUnitCost.isNaN() && !line.newUnitCost.isInfinite() && line.newUnitCost >= 0.0) {
                    "Invalid unit cost for product #${line.productId}."
                }
                val safeCost = StorePointRepository.roundMoney(line.newUnitCost)
                val stockBefore = queryStock(line.productId)
                val productName = queryProductName(line.productId)
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE products SET stockCount = stockCount + ? WHERE id = ?",
                    arrayOf<Any?>(line.quantityReceived, line.productId)
                )
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE products SET cost = ? WHERE id = ?",
                    arrayOf<Any?>(safeCost, line.productId)
                )
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE purchase_order_items SET quantityReceived = ? WHERE purchaseOrderId = ? AND productId = ?",
                    arrayOf<Any?>(line.quantityReceived, poId, line.productId)
                )
                val stockAfter = queryStock(line.productId)
                inventoryMovementDao.insertMovementBlocking(
                    InventoryMovementEntity(
                        productId = line.productId,
                        productName = productName,
                        timestamp = receivedDate,
                        actorUsername = "purchasing",
                        movementType = "PO_RECEIVE",
                        quantityDelta = line.quantityReceived.toDouble(),
                        stockBefore = stockBefore,
                        stockAfter = stockAfter,
                        reference = existingPo.poNumber,
                        notes = "Received from ${existingPo.supplierName} (Unit cost: $safeCost)"
                    )
                )
            }
            db.openHelper.writableDatabase.execSQL(
                "UPDATE purchase_orders SET status = ?, receivedDate = ?, paymentStatus = ? WHERE id = ?",
                arrayOf<Any?>("RECEIVED", receivedDate, paymentStatus, poId)
            )
        }
    }

    // --- Production & BOM Engine ---
    val allProductionRuns: Flow<List<ProductionLogEntity>> = productionDao.getAllProductionRuns()
    val allBOMs: Flow<List<BillOfMaterials>> = productionDao.getAllBOMs()

    suspend fun getAllProductionRunsSync(): List<ProductionLogEntity> = withContext(Dispatchers.IO) {
        productionDao.getAllProductionRunsSync()
    }

    suspend fun getBOMsForRawProduct(rawId: Int): List<BillOfMaterials> = withContext(Dispatchers.IO) {
        productionDao.getBOMsForRawProduct(rawId)
    }

    suspend fun saveBOM(bom: BillOfMaterials): Long = withContext(Dispatchers.IO) {
        productionDao.insertBOM(bom)
    }

    suspend fun deleteBOM(id: Int) = withContext(Dispatchers.IO) {
        productionDao.deleteBOM(id)
    }

    suspend fun executeProductionRunAtomic(
        productionRun: ProductionLogEntity,
        saveAsBom: Boolean = false
    ): ProductionLogEntity = withContext(Dispatchers.IO) {
        val rawQtyInt = productionRun.rawMaterialQtyUsed.toInt()
        val producedQtyInt = productionRun.targetRetailQtyProduced.toInt()
        require(rawQtyInt > 0) { "Raw material quantity used must be greater than zero." }
        require(producedQtyInt > 0) { "Target retail quantity produced must be greater than zero." }
        require(productionRun.rawMaterialProductId != productionRun.targetRetailProductId) {
            "Raw material and target retail product must be different products."
        }
        require(
            !productionRun.derivedRetailUnitCost.isNaN() &&
                !productionRun.derivedRetailUnitCost.isInfinite() &&
                productionRun.derivedRetailUnitCost >= 0.0
        ) {
            "Derived retail unit cost must be a valid non-negative number."
        }
        require(
            !productionRun.retailSellingPrice.isNaN() &&
                !productionRun.retailSellingPrice.isInfinite() &&
                productionRun.retailSellingPrice >= 0.0
        ) {
            "Retail selling price must be a valid non-negative number."
        }

        var savedRunId = 0L
        db.withTransaction {
            val rawStockCurrent = queryStock(productionRun.rawMaterialProductId)

            db.openHelper.writableDatabase.execSQL(
                "UPDATE products SET stockCount = stockCount - ? WHERE id = ? AND stockCount >= ?",
                arrayOf<Any?>(rawQtyInt, productionRun.rawMaterialProductId, rawQtyInt)
            )
            if (queryChanges() == 0) {
                throw IllegalStateException(
                    "Insufficient raw material stock: Available $rawStockCurrent, required $rawQtyInt."
                )
            }

            val rawStockAfter = queryStock(productionRun.rawMaterialProductId)

            inventoryMovementDao.insertMovementBlocking(
                InventoryMovementEntity(
                    productId = productionRun.rawMaterialProductId,
                    productName = productionRun.rawMaterialName,
                    timestamp = productionRun.timestamp,
                    actorUsername = productionRun.operatorUsername,
                    movementType = "REPACK_RAW_DECREMENT",
                    quantityDelta = -productionRun.rawMaterialQtyUsed,
                    stockBefore = rawStockCurrent,
                    stockAfter = rawStockAfter,
                    reference = productionRun.productionCode,
                    notes = "Consumed in repackaging run ${productionRun.productionCode}"
                )
            )

            val targetStockCurrent = queryStock(productionRun.targetRetailProductId)
            val producedQtyInt = productionRun.targetRetailQtyProduced.toInt()
            val targetStockAfter = targetStockCurrent + producedQtyInt

            db.openHelper.writableDatabase.execSQL(
                "UPDATE products SET stockCount = ?, cost = ? WHERE id = ?",
                arrayOf<Any?>(
                    targetStockAfter,
                    productionRun.derivedRetailUnitCost,
                    productionRun.targetRetailProductId
                )
            )

            if (productionRun.retailSellingPrice > 0.0) {
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE products SET price = ? WHERE id = ?",
                    arrayOf<Any?>(productionRun.retailSellingPrice, productionRun.targetRetailProductId)
                )
            }

            inventoryMovementDao.insertMovementBlocking(
                InventoryMovementEntity(
                    productId = productionRun.targetRetailProductId,
                    productName = productionRun.targetRetailProductName,
                    timestamp = productionRun.timestamp,
                    actorUsername = productionRun.operatorUsername,
                    movementType = "REPACK_PRODUCED_INCREMENT",
                    quantityDelta = productionRun.targetRetailQtyProduced,
                    stockBefore = targetStockCurrent,
                    stockAfter = targetStockAfter,
                    reference = productionRun.productionCode,
                    notes = "Produced from ${productionRun.rawMaterialName} (waste loss: ${productionRun.wasteLossQty})"
                )
            )

            savedRunId = productionDao.insertProductionRunBlocking(productionRun)

            if (saveAsBom) {
                val existingBom = productionDao.getBOMForPair(
                    productionRun.rawMaterialProductId,
                    productionRun.targetRetailProductId
                )
                val wastePct = if (productionRun.targetRetailQtyProduced > 0) {
                    (productionRun.wasteLossQty / (productionRun.targetRetailQtyProduced + productionRun.wasteLossQty)) * 100.0
                } else 0.0

                val bomToSave = existingBom?.copy(
                    rawQuantityNeeded = productionRun.rawMaterialQtyUsed,
                    producedRetailYield = productionRun.targetRetailQtyProduced,
                    wasteLossPercentage = wastePct,
                    notes = "Saved from production run ${productionRun.productionCode}"
                ) ?: BillOfMaterials(
                    rawMaterialProductId = productionRun.rawMaterialProductId,
                    targetRetailProductId = productionRun.targetRetailProductId,
                    rawQuantityNeeded = productionRun.rawMaterialQtyUsed,
                    producedRetailYield = productionRun.targetRetailQtyProduced,
                    wasteLossPercentage = wastePct,
                    notes = "Saved from production run ${productionRun.productionCode}"
                )
                productionDao.insertBOMBlocking(bomToSave)
            }
        }
        productionRun.copy(id = savedRunId.toInt())
    }

    private fun queryStock(productId: Int): Int {
        val cursor = db.query("SELECT stockCount AS s FROM products WHERE id = ?", arrayOf<Any?>(productId))
        return cursor.use {
            if (it.moveToNext()) it.getInt(it.getColumnIndexOrThrow("s")) else 0
        }
    }

    private fun queryProductName(productId: Int): String {
        val cursor = db.query("SELECT name AS n FROM products WHERE id = ?", arrayOf<Any?>(productId))
        return cursor.use {
            if (it.moveToNext()) it.getString(it.getColumnIndexOrThrow("n")) else "Product #$productId"
        }
    }

    private fun lastInsertRowId(): Int =
        db.openHelper.writableDatabase.compileStatement("SELECT last_insert_rowid()").simpleQueryForLong().toInt()

    private fun queryChanges(): Int =
        db.openHelper.writableDatabase.compileStatement("SELECT changes()").simpleQueryForLong().toInt()
}
