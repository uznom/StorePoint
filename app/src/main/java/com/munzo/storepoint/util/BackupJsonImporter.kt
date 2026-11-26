package com.munzo.storepoint.util

import androidx.sqlite.db.SupportSQLiteDatabase
import com.munzo.storepoint.data.AppDatabase
import com.munzo.storepoint.data.StoreConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Restores StorePoint JSON backup payloads atomically into Room SQLite.
 */
internal object BackupJsonImporter {

    suspend fun importAndRestore(
        db: AppDatabase,
        jsonContent: String
    ): DatabaseBackupManager.RestoreSummary = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonContent)
            if (!root.has("tables")) {
                return@withContext DatabaseBackupManager.RestoreSummary(
                    false,
                    "Invalid backup format: missing 'tables' payload."
                )
            }

            val tables = root.getJSONObject("tables")
            val backupDate = root.optString("dateTime", "Unknown Date")

            var importedProducts = 0
            var importedTransactions = 0
            var importedUsers = 0

            db.runInTransaction {
                val sqlDb = db.openHelper.writableDatabase
                try {
                    sqlDb.execSQL("PRAGMA foreign_keys = OFF;")
                } catch (_: Exception) {}

                try {
                    db.clearAllTables()
                    restoreStoreConfig(sqlDb, tables)
                    importedUsers = restoreUsers(sqlDb, tables)
                    restoreCategories(sqlDb, tables)
                    importedProducts = restoreProducts(sqlDb, tables)
                    restoreCashierSessions(sqlDb, tables)
                    importedTransactions = restoreTransactions(sqlDb, tables)
                    restoreTransactionItems(sqlDb, tables)
                    restoreDrawerTransactions(sqlDb, tables)
                    restorePaymentSchedules(sqlDb, tables)
                    restoreProductVariants(sqlDb, tables)
                    restoreReturns(sqlDb, tables)
                    restoreSuppliersAndPurchaseOrders(sqlDb, tables)
                    restoreParkedTransactions(sqlDb, tables)
                } finally {
                    try {
                        sqlDb.execSQL("PRAGMA foreign_keys = ON;")
                    } catch (_: Exception) {}
                }
            }

            val exportErrCount = root.optJSONArray("exportErrors")?.length() ?: 0
            val exportWarning = if (exportErrCount > 0) {
                " WARNING: this backup was created with $exportErrCount table export error(s) and may be incomplete."
            } else {
                ""
            }

            DatabaseBackupManager.RestoreSummary(
                success = true,
                message = "Database successfully restored from backup created on $backupDate$exportWarning",
                productsCount = importedProducts,
                transactionsCount = importedTransactions,
                usersCount = importedUsers,
                backupDate = backupDate
            )
        } catch (e: Exception) {
            DatabaseBackupManager.RestoreSummary(
                success = false,
                message = "Database restore failed: ${e.localizedMessage}"
            )
        }
    }

    private fun restoreStoreConfig(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        if (!tables.has("store_config")) return
        val cfgObj = tables.getJSONObject("store_config")
        val cfg = StoreConfig(
            id = cfgObj.optInt("id", 1),
            storeName = cfgObj.getString("storeName"),
            currencySymbol = cfgObj.optString("currencySymbol", "₱"),
            taxPercentage = cfgObj.optDouble("taxPercentage", 0.0),
            setupCompleted = cfgObj.optBoolean("setupCompleted", true),
            gcashBalance = cfgObj.optDouble("gcashBalance", 0.0),
            hasGCash = cfgObj.optBoolean("hasGCash", true),
            hasMaya = cfgObj.optBoolean("hasMaya", true),
            hasLoad = cfgObj.optBoolean("hasLoad", true),
            smartLoadBalance = cfgObj.optDouble("smartLoadBalance", 0.0),
            globeLoadBalance = cfgObj.optDouble("globeLoadBalance", 0.0)
        )
        sqlDb.execSQL(
            "INSERT OR REPLACE INTO store_config (id, storeName, currencySymbol, taxPercentage, setupCompleted, gcashBalance, hasGCash, hasMaya, hasLoad, smartLoadBalance, globeLoadBalance) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                cfg.id, cfg.storeName, cfg.currencySymbol, cfg.taxPercentage,
                if (cfg.setupCompleted) 1 else 0, cfg.gcashBalance,
                if (cfg.hasGCash) 1 else 0, if (cfg.hasMaya) 1 else 0,
                if (cfg.hasLoad) 1 else 0, cfg.smartLoadBalance, cfg.globeLoadBalance
            )
        )
    }

    private fun restoreUsers(sqlDb: SupportSQLiteDatabase, tables: JSONObject): Int {
        if (!tables.has("users")) return 0
        val usersArr = tables.getJSONArray("users")
        for (i in 0 until usersArr.length()) {
            val u = usersArr.getJSONObject(i)
            val storedPinHash = if (u.has("pinHash")) u.getString("pinHash") else u.optString("passwordHash", "")
            sqlDb.execSQL(
                "INSERT OR REPLACE INTO user_accounts (username, pinHash, role, barcodeId) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(u.getString("username"), storedPinHash, u.getString("role"), u.optString("barcodeId", ""))
            )
        }
        return usersArr.length()
    }

    private fun restoreCategories(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        if (!tables.has("categories")) return
        val catArr = tables.getJSONArray("categories")
        for (i in 0 until catArr.length()) {
            val c = catArr.getJSONObject(i)
            sqlDb.execSQL(
                "INSERT OR REPLACE INTO categories (id, name) VALUES (?, ?)",
                arrayOf<Any?>(c.getInt("id"), c.getString("name"))
            )
        }
    }

    private fun restoreProducts(sqlDb: SupportSQLiteDatabase, tables: JSONObject): Int {
        if (!tables.has("products")) return 0
        val prodArr = tables.getJSONArray("products")
        for (i in 0 until prodArr.length()) {
            val p = prodArr.getJSONObject(i)
            sqlDb.execSQL(
                """
                INSERT OR REPLACE INTO products (
                    id, name, categoryId, price, stockCount, barcode, expirationDate, cost,
                    hasStick10s, priceStick10s, hasStick20s, priceStick20s, hasReam, priceReam, hasMasterCase, priceMasterCase,
                    hasCustomUom, customUomName, customUomMultiplier, customUomPrice,
                    barcode10s, barcode20s, barcodeReam, barcodeMasterCase, barcodeCustomUom, baseUom
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    p.getInt("id"),
                    p.getString("name"),
                    p.getInt("categoryId"),
                    p.getDouble("price"),
                    p.getInt("stockCount"),
                    p.optString("barcode", ""),
                    p.optString("expirationDate").takeIf { it.isNotEmpty() },
                    p.optDouble("cost", 0.0),
                    if (p.optBoolean("hasStick10s", false)) 1 else 0,
                    p.optDouble("priceStick10s", 0.0),
                    if (p.optBoolean("hasStick20s", false)) 1 else 0,
                    p.optDouble("priceStick20s", 0.0),
                    if (p.optBoolean("hasReam", false)) 1 else 0,
                    p.optDouble("priceReam", 0.0),
                    if (p.optBoolean("hasMasterCase", false)) 1 else 0,
                    p.optDouble("priceMasterCase", 0.0),
                    if (p.optBoolean("hasCustomUom", false)) 1 else 0,
                    p.optString("customUomName", "Pack"),
                    p.optInt("customUomMultiplier", 1),
                    p.optDouble("customUomPrice", 0.0),
                    p.optString("barcode10s", ""),
                    p.optString("barcode20s", ""),
                    p.optString("barcodeReam", ""),
                    p.optString("barcodeMasterCase", ""),
                    p.optString("barcodeCustomUom", ""),
                    p.optString("baseUom", "pc")
                )
            )
        }
        return prodArr.length()
    }

    private fun restoreCashierSessions(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        if (!tables.has("cashier_sessions")) return
        val sArr = tables.getJSONArray("cashier_sessions")
        for (i in 0 until sArr.length()) {
            val s = sArr.getJSONObject(i)
            sqlDb.execSQL(
                "INSERT OR REPLACE INTO cashier_sessions (id, cashierUsername, startTime, endTime, startingCash, endingCash, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    s.getInt("id"),
                    s.getString("cashierUsername"),
                    s.getLong("startTime"),
                    if (s.has("endTime")) s.getLong("endTime") else null,
                    s.getDouble("startingCash"),
                    if (s.has("endingCash")) s.getDouble("endingCash") else null,
                    s.getString("status")
                )
            )
        }
    }

    private fun restoreTransactions(sqlDb: SupportSQLiteDatabase, tables: JSONObject): Int {
        if (!tables.has("transactions")) return 0
        val txArr = tables.getJSONArray("transactions")
        for (i in 0 until txArr.length()) {
            val tx = txArr.getJSONObject(i)
            sqlDb.execSQL(
                "INSERT OR REPLACE INTO transactions (id, timestamp, cashierUsername, subtotal, taxAmount, totalAmount, paymentMethod, cashPaid, changeAmount, status, customerName) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    tx.getInt("id"),
                    tx.getLong("timestamp"),
                    tx.getString("cashierUsername"),
                    tx.getDouble("subtotal"),
                    tx.getDouble("taxAmount"),
                    tx.getDouble("totalAmount"),
                    tx.getString("paymentMethod"),
                    tx.getDouble("cashPaid"),
                    tx.getDouble("changeAmount"),
                    tx.optString("status", "COMPLETED"),
                    tx.optString("customerName", "")
                )
            )
        }
        return txArr.length()
    }

    private fun restoreTransactionItems(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        if (!tables.has("transaction_items")) return
        val itemArr = tables.getJSONArray("transaction_items")
        for (i in 0 until itemArr.length()) {
            val item = itemArr.getJSONObject(i)
            sqlDb.execSQL(
                "INSERT OR REPLACE INTO transaction_items (id, transactionId, productId, productName, price, quantity, cost, uomName) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    item.getInt("id"),
                    item.getInt("transactionId"),
                    item.getInt("productId"),
                    item.getString("productName"),
                    item.getDouble("price"),
                    item.getInt("quantity"),
                    item.optDouble("cost", 0.0),
                    item.optString("uomName", "pc")
                )
            )
        }
    }

    private fun restoreDrawerTransactions(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        if (!tables.has("drawer_transactions")) return
        val dArr = tables.getJSONArray("drawer_transactions")
        for (i in 0 until dArr.length()) {
            val d = dArr.getJSONObject(i)
            sqlDb.execSQL(
                "INSERT OR REPLACE INTO drawer_transactions (id, sessionId, timestamp, cashierUsername, amount, type, reason) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    d.getInt("id"),
                    d.getInt("sessionId"),
                    d.getLong("timestamp"),
                    d.getString("cashierUsername"),
                    d.getDouble("amount"),
                    d.getString("type"),
                    d.getString("reason")
                )
            )
        }
    }

    private fun restorePaymentSchedules(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        if (!tables.has("payment_schedules")) return
        val pArr = tables.getJSONArray("payment_schedules")
        for (i in 0 until pArr.length()) {
            val ps = pArr.getJSONObject(i)
            sqlDb.execSQL(
                "INSERT OR REPLACE INTO payment_schedules (id, title, amount, dueDate, category, priority, isPaid, paymentMethod) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    ps.getInt("id"),
                    ps.getString("title"),
                    ps.getDouble("amount"),
                    ps.getString("dueDate"),
                    ps.getString("category"),
                    ps.getString("priority"),
                    if (ps.optBoolean("isPaid", false)) 1 else 0,
                    ps.optString("paymentMethod", "CASH")
                )
            )
        }
    }

    private fun restoreProductVariants(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        if (!tables.has("product_variants")) return
        val varArr = tables.getJSONArray("product_variants")
        for (i in 0 until varArr.length()) {
            val v = varArr.getJSONObject(i)
            sqlDb.execSQL(
                "INSERT OR REPLACE INTO product_variants (id, productId, variantName, uomName, multiplier, price, cost, barcode, isDefault) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    v.getInt("id"),
                    v.getInt("productId"),
                    v.getString("variantName"),
                    v.optString("uomName", "pc"),
                    v.optInt("multiplier", 1),
                    v.getDouble("price"),
                    v.optDouble("cost", 0.0),
                    v.optString("barcode", ""),
                    if (v.optBoolean("isDefault", false)) 1 else 0
                )
            )
        }
    }

    private fun restoreReturns(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        if (tables.has("return_transactions")) {
            val retArr = tables.getJSONArray("return_transactions")
            for (i in 0 until retArr.length()) {
                val r = retArr.getJSONObject(i)
                sqlDb.execSQL(
                    "INSERT OR REPLACE INTO return_transactions (id, originalTransactionId, timestamp, cashierUsername, totalRefundAmount, refundMethod, returnReason, customerName, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        r.getInt("id"),
                        r.getInt("originalTransactionId"),
                        r.getLong("timestamp"),
                        r.getString("cashierUsername"),
                        r.getDouble("totalRefundAmount"),
                        r.optString("refundMethod", "CASH"),
                        r.optString("returnReason", "Defective / Spoiled"),
                        r.optString("customerName", ""),
                        r.optString("notes", "")
                    )
                )
            }
        }

        if (tables.has("return_items")) {
            val retItemArr = tables.getJSONArray("return_items")
            for (i in 0 until retItemArr.length()) {
                val ri = retItemArr.getJSONObject(i)
                sqlDb.execSQL(
                    "INSERT OR REPLACE INTO return_items (id, returnTransactionId, productId, productName, quantity, unitPrice, refundAmount, uomName, restockToInventory) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        ri.getInt("id"),
                        ri.getInt("returnTransactionId"),
                        ri.getInt("productId"),
                        ri.getString("productName"),
                        ri.getInt("quantity"),
                        ri.getDouble("unitPrice"),
                        ri.getDouble("refundAmount"),
                        ri.optString("uomName", "pc"),
                        if (ri.optBoolean("restockToInventory", true)) 1 else 0
                    )
                )
            }
        }
    }

    private fun restoreSuppliersAndPurchaseOrders(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        if (tables.has("suppliers")) {
            val supArr = tables.getJSONArray("suppliers")
            for (i in 0 until supArr.length()) {
                val s = supArr.getJSONObject(i)
                sqlDb.execSQL(
                    "INSERT OR REPLACE INTO suppliers (id, name, contactPerson, phone, email, address, deliverySchedule, paymentTerms, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        s.getInt("id"),
                        s.getString("name"),
                        s.optString("contactPerson", ""),
                        s.optString("phone", ""),
                        s.optString("email", ""),
                        s.optString("address", ""),
                        s.optString("deliverySchedule", ""),
                        s.optString("paymentTerms", "Cash on Delivery"),
                        s.optString("notes", "")
                    )
                )
            }
        }

        if (tables.has("purchase_orders")) {
            val poArr = tables.getJSONArray("purchase_orders")
            for (i in 0 until poArr.length()) {
                val po = poArr.getJSONObject(i)
                sqlDb.execSQL(
                    "INSERT OR REPLACE INTO purchase_orders (id, poNumber, supplierId, supplierName, orderDate, expectedDeliveryDate, receivedDate, totalCost, status, paymentStatus, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        po.getInt("id"),
                        po.getString("poNumber"),
                        po.getInt("supplierId"),
                        po.getString("supplierName"),
                        po.getLong("orderDate"),
                        if (po.has("expectedDeliveryDate")) po.getLong("expectedDeliveryDate") else null,
                        if (po.has("receivedDate")) po.getLong("receivedDate") else null,
                        po.getDouble("totalCost"),
                        po.optString("status", "ORDERED"),
                        po.optString("paymentStatus", "UNPAID"),
                        po.optString("notes", "")
                    )
                )
            }
        }

        if (tables.has("purchase_order_items")) {
            val poiArr = tables.getJSONArray("purchase_order_items")
            for (i in 0 until poiArr.length()) {
                val poi = poiArr.getJSONObject(i)
                sqlDb.execSQL(
                    "INSERT OR REPLACE INTO purchase_order_items (id, purchaseOrderId, productId, productName, quantityOrdered, quantityReceived, unitCost, totalCost, uomName) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        poi.getInt("id"),
                        poi.getInt("purchaseOrderId"),
                        poi.getInt("productId"),
                        poi.getString("productName"),
                        poi.getInt("quantityOrdered"),
                        poi.optInt("quantityReceived", 0),
                        poi.getDouble("unitCost"),
                        poi.getDouble("totalCost"),
                        poi.optString("uomName", "pc")
                    )
                )
            }
        }
    }

    private fun restoreParkedTransactions(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        if (tables.has("parked_transactions")) {
            val parkedArr = tables.getJSONArray("parked_transactions")
            for (i in 0 until parkedArr.length()) {
                val p = parkedArr.getJSONObject(i)
                sqlDb.execSQL(
                    "INSERT OR REPLACE INTO parked_transactions (id, timestamp, note) VALUES (?, ?, ?)",
                    arrayOf<Any?>(p.getInt("id"), p.getLong("timestamp"), p.getString("note"))
                )
            }
        }

        if (tables.has("parked_transaction_items")) {
            val pItemArr = tables.getJSONArray("parked_transaction_items")
            for (i in 0 until pItemArr.length()) {
                val pi = pItemArr.getJSONObject(i)
                sqlDb.execSQL(
                    "INSERT OR REPLACE INTO parked_transaction_items (id, parkedTransactionId, productId, productName, price, quantity) VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(
                        pi.getInt("id"),
                        pi.getInt("parkedTransactionId"),
                        pi.getInt("productId"),
                        pi.getString("productName"),
                        pi.getDouble("price"),
                        pi.getInt("quantity")
                    )
                )
            }
        }
    }
}
