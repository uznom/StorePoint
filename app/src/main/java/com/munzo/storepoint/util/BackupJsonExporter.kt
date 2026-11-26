package com.munzo.storepoint.util

import android.util.Log
import com.munzo.storepoint.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Date

/**
 * Serializes all StorePoint Room tables into the canonical versioned JSON backup envelope.
 */
internal object BackupJsonExporter {

    private const val TAG = "BackupJsonExporter"

    suspend fun export(db: AppDatabase, formattedDate: String): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("backupVersion", 1)
        root.put("timestamp", System.currentTimeMillis())
        root.put("dateTime", formattedDate)
        root.put("appName", "StorePoint POS")

        val tables = JSONObject()
        val exportErrors = mutableListOf<String>()

        exportStoreConfig(db, tables)
        exportUsers(db, tables)
        exportCategories(db, tables)
        exportProducts(db, tables)
        exportCashierSessions(db, tables, exportErrors)
        exportTransactions(db, tables, exportErrors)
        exportTransactionItems(db, tables, exportErrors)
        exportDrawerTransactions(db, tables, exportErrors)
        exportPaymentSchedules(db, tables, exportErrors)
        exportProductVariants(db, tables, exportErrors)
        exportReturns(db, tables, exportErrors)
        exportSuppliersAndPurchaseOrders(db, tables, exportErrors)
        exportParkedTransactions(db, tables, exportErrors)

        root.put("tables", tables)

        if (exportErrors.isNotEmpty()) {
            val errorsJson = JSONArray()
            exportErrors.forEach { errorsJson.put(it) }
            root.put("exportErrors", errorsJson)
        }

        val tablesString = tables.toString()
        val checksum = MessageDigest.getInstance("SHA-256")
            .digest(tablesString.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        root.put("checksum", checksum)

        root.toString(2)
    }

    private suspend fun exportStoreConfig(db: AppDatabase, tables: JSONObject) {
        val storeConfig = db.storeConfigDao.getStoreConfigSync() ?: return
        tables.put("store_config", JSONObject().apply {
            put("id", storeConfig.id)
            put("storeName", storeConfig.storeName)
            put("currencySymbol", storeConfig.currencySymbol)
            put("taxPercentage", storeConfig.taxPercentage)
            put("setupCompleted", storeConfig.setupCompleted)
            put("gcashBalance", storeConfig.gcashBalance)
            put("hasGCash", storeConfig.hasGCash)
            put("hasMaya", storeConfig.hasMaya)
            put("hasLoad", storeConfig.hasLoad)
            put("smartLoadBalance", storeConfig.smartLoadBalance)
            put("globeLoadBalance", storeConfig.globeLoadBalance)
        })
    }

    private suspend fun exportUsers(db: AppDatabase, tables: JSONObject) {
        val usersArray = JSONArray()
        db.userDao.getAllUsersSync().forEach { u ->
            usersArray.put(JSONObject().apply {
                put("username", u.username)
                put("pinHash", u.pinHash)
                put("passwordHash", u.pinHash)
                put("role", u.role)
                put("barcodeId", u.barcodeId)
            })
        }
        tables.put("users", usersArray)
    }

    private suspend fun exportCategories(db: AppDatabase, tables: JSONObject) {
        val catArray = JSONArray()
        db.categoryDao.getAllCategoriesSync().forEach { c ->
            catArray.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
            })
        }
        tables.put("categories", catArray)
    }

    private suspend fun exportProducts(db: AppDatabase, tables: JSONObject) {
        val prodArray = JSONArray()
        db.productDao.getAllProductsSync().forEach { p ->
            prodArray.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("categoryId", p.categoryId)
                put("price", p.price)
                put("stockCount", p.stockCount)
                put("barcode", p.barcode)
                put("expirationDate", p.expirationDate ?: "")
                put("cost", p.cost)
                put("hasStick10s", p.hasStick10s)
                put("priceStick10s", p.priceStick10s)
                put("hasStick20s", p.hasStick20s)
                put("priceStick20s", p.priceStick20s)
                put("hasReam", p.hasReam)
                put("priceReam", p.priceReam)
                put("hasMasterCase", p.hasMasterCase)
                put("priceMasterCase", p.priceMasterCase)
                put("hasCustomUom", p.hasCustomUom)
                put("customUomName", p.customUomName)
                put("customUomMultiplier", p.customUomMultiplier)
                put("customUomPrice", p.customUomPrice)
                put("barcode10s", p.barcode10s)
                put("barcode20s", p.barcode20s)
                put("barcodeReam", p.barcodeReam)
                put("barcodeMasterCase", p.barcodeMasterCase)
                put("barcodeCustomUom", p.barcodeCustomUom)
                put("baseUom", p.baseUom)
            })
        }
        tables.put("products", prodArray)
    }

    private fun exportCashierSessions(db: AppDatabase, tables: JSONObject, errors: MutableList<String>) {
        try {
            db.query("SELECT * FROM cashier_sessions", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("cashierUsername", c.getString(c.getColumnIndexOrThrow("cashierUsername")))
                        put("startTime", c.getLong(c.getColumnIndexOrThrow("startTime")))
                        val endIdx = c.getColumnIndexOrThrow("endTime")
                        if (!c.isNull(endIdx)) put("endTime", c.getLong(endIdx))
                        put("startingCash", c.getDouble(c.getColumnIndexOrThrow("startingCash")))
                        val endCashIdx = c.getColumnIndexOrThrow("endingCash")
                        if (!c.isNull(endCashIdx)) put("endingCash", c.getDouble(endCashIdx))
                        put("status", c.getString(c.getColumnIndexOrThrow("status")))
                    })
                }
                tables.put("cashier_sessions", arr)
            }
        } catch (e: Exception) {
            errors.add("cashier_sessions: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting cashier_sessions: ${e.message}", e)
        }
    }

    private fun exportTransactions(db: AppDatabase, tables: JSONObject, errors: MutableList<String>) {
        try {
            db.query("SELECT * FROM transactions", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("timestamp", c.getLong(c.getColumnIndexOrThrow("timestamp")))
                        put("cashierUsername", c.getString(c.getColumnIndexOrThrow("cashierUsername")))
                        put("subtotal", c.getDouble(c.getColumnIndexOrThrow("subtotal")))
                        put("taxAmount", c.getDouble(c.getColumnIndexOrThrow("taxAmount")))
                        put("totalAmount", c.getDouble(c.getColumnIndexOrThrow("totalAmount")))
                        put("paymentMethod", c.getString(c.getColumnIndexOrThrow("paymentMethod")))
                        put("cashPaid", c.getDouble(c.getColumnIndexOrThrow("cashPaid")))
                        put("changeAmount", c.getDouble(c.getColumnIndexOrThrow("changeAmount")))
                        val statusIdx = c.getColumnIndex("status")
                        put("status", if (statusIdx != -1) c.getString(statusIdx) else "COMPLETED")
                        val custIdx = c.getColumnIndex("customerName")
                        put("customerName", if (custIdx != -1) c.getString(custIdx) else "")
                    })
                }
                tables.put("transactions", arr)
            }
        } catch (e: Exception) {
            errors.add("transactions: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting transactions: ${e.message}", e)
        }
    }

    private fun exportTransactionItems(db: AppDatabase, tables: JSONObject, errors: MutableList<String>) {
        try {
            db.query("SELECT * FROM transaction_items", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("transactionId", c.getInt(c.getColumnIndexOrThrow("transactionId")))
                        put("productId", c.getInt(c.getColumnIndexOrThrow("productId")))
                        put("productName", c.getString(c.getColumnIndexOrThrow("productName")))
                        put("price", c.getDouble(c.getColumnIndexOrThrow("price")))
                        put("quantity", c.getInt(c.getColumnIndexOrThrow("quantity")))
                        put("cost", c.getDouble(c.getColumnIndexOrThrow("cost")))
                        val uomIdx = c.getColumnIndex("uomName")
                        put("uomName", if (uomIdx != -1) c.getString(uomIdx) else "pc")
                    })
                }
                tables.put("transaction_items", arr)
            }
        } catch (e: Exception) {
            errors.add("transaction_items: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting transaction_items: ${e.message}", e)
        }
    }

    private fun exportDrawerTransactions(db: AppDatabase, tables: JSONObject, errors: MutableList<String>) {
        try {
            db.query("SELECT * FROM drawer_transactions", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("sessionId", c.getInt(c.getColumnIndexOrThrow("sessionId")))
                        put("timestamp", c.getLong(c.getColumnIndexOrThrow("timestamp")))
                        put("cashierUsername", c.getString(c.getColumnIndexOrThrow("cashierUsername")))
                        put("amount", c.getDouble(c.getColumnIndexOrThrow("amount")))
                        put("type", c.getString(c.getColumnIndexOrThrow("type")))
                        put("reason", c.getString(c.getColumnIndexOrThrow("reason")))
                    })
                }
                tables.put("drawer_transactions", arr)
            }
        } catch (e: Exception) {
            errors.add("drawer_transactions: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting drawer_transactions: ${e.message}", e)
        }
    }

    private fun exportPaymentSchedules(db: AppDatabase, tables: JSONObject, errors: MutableList<String>) {
        try {
            db.query("SELECT * FROM payment_schedules", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("title", c.getString(c.getColumnIndexOrThrow("title")))
                        put("amount", c.getDouble(c.getColumnIndexOrThrow("amount")))
                        put("dueDate", c.getString(c.getColumnIndexOrThrow("dueDate")))
                        put("category", c.getString(c.getColumnIndexOrThrow("category")))
                        put("priority", c.getString(c.getColumnIndexOrThrow("priority")))
                        put("isPaid", c.getInt(c.getColumnIndexOrThrow("isPaid")) == 1)
                        put("paymentMethod", c.getString(c.getColumnIndexOrThrow("paymentMethod")))
                    })
                }
                tables.put("payment_schedules", arr)
            }
        } catch (e: Exception) {
            errors.add("payment_schedules: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting payment_schedules: ${e.message}", e)
        }
    }

    private fun exportProductVariants(db: AppDatabase, tables: JSONObject, errors: MutableList<String>) {
        try {
            db.query("SELECT * FROM product_variants", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("productId", c.getInt(c.getColumnIndexOrThrow("productId")))
                        put("variantName", c.getString(c.getColumnIndexOrThrow("variantName")))
                        put("uomName", c.getString(c.getColumnIndexOrThrow("uomName")))
                        put("multiplier", c.getInt(c.getColumnIndexOrThrow("multiplier")))
                        put("price", c.getDouble(c.getColumnIndexOrThrow("price")))
                        put("cost", c.getDouble(c.getColumnIndexOrThrow("cost")))
                        put("barcode", c.getString(c.getColumnIndexOrThrow("barcode")))
                        put("isDefault", c.getInt(c.getColumnIndexOrThrow("isDefault")) == 1)
                    })
                }
                tables.put("product_variants", arr)
            }
        } catch (e: Exception) {
            errors.add("product_variants: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting product_variants: ${e.message}", e)
        }
    }

    private fun exportReturns(db: AppDatabase, tables: JSONObject, errors: MutableList<String>) {
        try {
            db.query("SELECT * FROM return_transactions", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("originalTransactionId", c.getInt(c.getColumnIndexOrThrow("originalTransactionId")))
                        put("timestamp", c.getLong(c.getColumnIndexOrThrow("timestamp")))
                        put("cashierUsername", c.getString(c.getColumnIndexOrThrow("cashierUsername")))
                        put("totalRefundAmount", c.getDouble(c.getColumnIndexOrThrow("totalRefundAmount")))
                        put("refundMethod", c.getString(c.getColumnIndexOrThrow("refundMethod")))
                        put("returnReason", c.getString(c.getColumnIndexOrThrow("returnReason")))
                        put("customerName", c.getString(c.getColumnIndexOrThrow("customerName")))
                        put("notes", c.getString(c.getColumnIndexOrThrow("notes")))
                    })
                }
                tables.put("return_transactions", arr)
            }

            db.query("SELECT * FROM return_items", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("returnTransactionId", c.getInt(c.getColumnIndexOrThrow("returnTransactionId")))
                        put("productId", c.getInt(c.getColumnIndexOrThrow("productId")))
                        put("productName", c.getString(c.getColumnIndexOrThrow("productName")))
                        put("quantity", c.getInt(c.getColumnIndexOrThrow("quantity")))
                        put("unitPrice", c.getDouble(c.getColumnIndexOrThrow("unitPrice")))
                        put("refundAmount", c.getDouble(c.getColumnIndexOrThrow("refundAmount")))
                        put("uomName", c.getString(c.getColumnIndexOrThrow("uomName")))
                        put("restockToInventory", c.getInt(c.getColumnIndexOrThrow("restockToInventory")) == 1)
                    })
                }
                tables.put("return_items", arr)
            }
        } catch (e: Exception) {
            errors.add("return_transactions: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting returns: ${e.message}", e)
        }
    }

    private fun exportSuppliersAndPurchaseOrders(db: AppDatabase, tables: JSONObject, errors: MutableList<String>) {
        try {
            db.query("SELECT * FROM suppliers", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("name", c.getString(c.getColumnIndexOrThrow("name")))
                        put("contactPerson", c.getString(c.getColumnIndexOrThrow("contactPerson")))
                        put("phone", c.getString(c.getColumnIndexOrThrow("phone")))
                        put("email", c.getString(c.getColumnIndexOrThrow("email")))
                        put("address", c.getString(c.getColumnIndexOrThrow("address")))
                        put("deliverySchedule", c.getString(c.getColumnIndexOrThrow("deliverySchedule")))
                        put("paymentTerms", c.getString(c.getColumnIndexOrThrow("paymentTerms")))
                        put("notes", c.getString(c.getColumnIndexOrThrow("notes")))
                    })
                }
                tables.put("suppliers", arr)
            }

            db.query("SELECT * FROM purchase_orders", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("poNumber", c.getString(c.getColumnIndexOrThrow("poNumber")))
                        put("supplierId", c.getInt(c.getColumnIndexOrThrow("supplierId")))
                        put("supplierName", c.getString(c.getColumnIndexOrThrow("supplierName")))
                        put("orderDate", c.getLong(c.getColumnIndexOrThrow("orderDate")))
                        val expIdx = c.getColumnIndexOrThrow("expectedDeliveryDate")
                        if (!c.isNull(expIdx)) put("expectedDeliveryDate", c.getLong(expIdx))
                        val recIdx = c.getColumnIndexOrThrow("receivedDate")
                        if (!c.isNull(recIdx)) put("receivedDate", c.getLong(recIdx))
                        put("totalCost", c.getDouble(c.getColumnIndexOrThrow("totalCost")))
                        put("status", c.getString(c.getColumnIndexOrThrow("status")))
                        put("paymentStatus", c.getString(c.getColumnIndexOrThrow("paymentStatus")))
                        put("notes", c.getString(c.getColumnIndexOrThrow("notes")))
                    })
                }
                tables.put("purchase_orders", arr)
            }

            db.query("SELECT * FROM purchase_order_items", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("purchaseOrderId", c.getInt(c.getColumnIndexOrThrow("purchaseOrderId")))
                        put("productId", c.getInt(c.getColumnIndexOrThrow("productId")))
                        put("productName", c.getString(c.getColumnIndexOrThrow("productName")))
                        put("quantityOrdered", c.getInt(c.getColumnIndexOrThrow("quantityOrdered")))
                        put("quantityReceived", c.getInt(c.getColumnIndexOrThrow("quantityReceived")))
                        put("unitCost", c.getDouble(c.getColumnIndexOrThrow("unitCost")))
                        put("totalCost", c.getDouble(c.getColumnIndexOrThrow("totalCost")))
                        put("uomName", c.getString(c.getColumnIndexOrThrow("uomName")))
                    })
                }
                tables.put("purchase_order_items", arr)
            }
        } catch (e: Exception) {
            errors.add("purchase_orders: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting purchase_orders: ${e.message}", e)
        }
    }

    private fun exportParkedTransactions(db: AppDatabase, tables: JSONObject, errors: MutableList<String>) {
        try {
            db.query("SELECT * FROM parked_transactions", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("timestamp", c.getLong(c.getColumnIndexOrThrow("timestamp")))
                        put("note", c.getString(c.getColumnIndexOrThrow("note")))
                    })
                }
                tables.put("parked_transactions", arr)
            }

            db.query("SELECT * FROM parked_transaction_items", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("parkedTransactionId", c.getInt(c.getColumnIndexOrThrow("parkedTransactionId")))
                        put("productId", c.getInt(c.getColumnIndexOrThrow("productId")))
                        put("productName", c.getString(c.getColumnIndexOrThrow("productName")))
                        put("price", c.getDouble(c.getColumnIndexOrThrow("price")))
                        put("quantity", c.getInt(c.getColumnIndexOrThrow("quantity")))
                    })
                }
                tables.put("parked_transaction_items", arr)
            }
        } catch (e: Exception) {
            errors.add("parked_transactions: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting parked_transactions: ${e.message}", e)
        }
    }
}
