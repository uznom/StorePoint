package com.munzo.storepoint.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.munzo.storepoint.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import android.util.Base64
import android.util.Log
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object DatabaseBackupManager {

    private const val TAG = "DatabaseBackupManager"

    data class RestoreSummary(
        val success: Boolean,
        val message: String,
        val productsCount: Int = 0,
        val transactionsCount: Int = 0,
        val usersCount: Int = 0,
        val backupDate: String = ""
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    private fun formatDate(date: Date): String = synchronized(dateFormat) {
        dateFormat.format(date)
    }

    private fun formatFileDate(date: Date): String = synchronized(fileDateFormat) {
        fileDateFormat.format(date)
    }

    // =========================================================================
    // M2: Optional backup-at-rest encryption (Encrypt-then-MAC):
    //   • AES-256-CBC ciphertext, wrapped with an HMAC-SHA256 tag,
    //   • key material = PBKDF2-HMAC-SHA256(passphrase, random salt),
    //   • envelope = {"enc":1, salt, iv, mac, data} (base64).
    // The on-device SQLite file itself remains unencrypted (see README limitation);
    // this protects exported/shared backup files that contain hashes + customer data.
    // =========================================================================
    private val backupPbkdf2Iterations = 120_000

    private fun deriveBackupKeys(passphrase: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, backupPbkdf2Iterations, 512)
        val material = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Pair(material.copyOfRange(0, 32), material.copyOfRange(32, 64))
    }

    suspend fun encryptBackupJson(jsonContent: String, passphrase: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (passphrase.isEmpty()) {
                return@withContext Result.failure(Exception("Backup encryption password must not be empty."))
            }
            val random = SecureRandom()
            val salt = ByteArray(16)
            val iv = ByteArray(16)
            random.nextBytes(salt)
            random.nextBytes(iv)
            val (encKey, macKey) = deriveBackupKeys(passphrase, salt)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
            val plain = jsonContent.toByteArray(Charsets.UTF_8)
            val first = cipher.update(plain, 0, plain.size)
            val tail = cipher.doFinal()
            val ciphertext = ByteArray(first.size + tail.size)
            first.copyInto(ciphertext, 0)
            tail.copyInto(ciphertext, first.size)

            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(macKey, "HmacSHA256"))
            mac.update(iv, 0, iv.size)
            mac.update(ciphertext, 0, ciphertext.size)
            val tag = mac.doFinal()

            val envelope = JSONObject()
            envelope.put("enc", 1)
            envelope.put("iterations", backupPbkdf2Iterations)
            envelope.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            envelope.put("mac", Base64.encodeToString(tag, Base64.NO_WRAP))
            envelope.put("data", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            Result.success(envelope.toString())
        } catch (e: Exception) {
            Result.failure(Exception("Backup encryption failed: ${e.localizedMessage}"))
        }
    }

    suspend fun decryptBackupJson(envelopeContent: String, passphrase: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(envelopeContent)
            if (root.optInt("enc", 0) != 1) {
                return@withContext Result.failure(Exception("This file does not look like an encrypted StorePoint backup."))
            }
            if (passphrase.isEmpty()) {
                return@withContext Result.failure(Exception("A backup password is required to restore this file."))
            }

            val salt = Base64.decode(root.getString("salt"), Base64.DEFAULT)
            val iv = Base64.decode(root.getString("iv"), Base64.DEFAULT)
            val expectedTag = Base64.decode(root.getString("mac"), Base64.DEFAULT)
            val ciphertext = Base64.decode(root.getString("data"), Base64.DEFAULT)
            val (encKey, macKey) = deriveBackupKeys(passphrase, salt)

            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(macKey, "HmacSHA256"))
            mac.update(iv, 0, iv.size)
            mac.update(ciphertext, 0, ciphertext.size)
            val actualTag = mac.doFinal()
            if (!MessageDigest.isEqual(expectedTag, actualTag)) {
                return@withContext Result.failure(Exception("Incorrect backup password — MAC verification failed."))
            }

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
            val plain = cipher.doFinal(ciphertext)
            Result.success(String(plain, Charsets.UTF_8))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to decrypt backup: ${e.localizedMessage}"))
        }
    }

    suspend fun exportBackupJson(db: AppDatabase): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("backupVersion", 1)
        root.put("timestamp", System.currentTimeMillis())
        root.put("dateTime", formatDate(Date()))
        root.put("appName", "StorePoint POS")

        val tables = JSONObject()
        val exportErrors = mutableListOf<String>()

        // 1. Store Config
        val storeConfig = db.storeConfigDao.getStoreConfigSync()
        if (storeConfig != null) {
            val cfgObj = JSONObject().apply {
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
            }
            tables.put("store_config", cfgObj)
        }

        // 2. Users
        val users = db.userDao.getAllUsersSync()
        val usersArray = JSONArray()
        users.forEach { u ->
            usersArray.put(JSONObject().apply {
                put("username", u.username)
                put("pinHash", u.pinHash)
                put("passwordHash", u.pinHash)
                put("role", u.role)
                put("barcodeId", u.barcodeId)
            })
        }
        tables.put("users", usersArray)

        // 3. Categories
        val categories = db.categoryDao.getAllCategoriesSync()
        val catArray = JSONArray()
        categories.forEach { c ->
            catArray.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
            })
        }
        tables.put("categories", catArray)

        // 4. Products
        val products = db.productDao.getAllProductsSync()
        val prodArray = JSONArray()
        products.forEach { p ->
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

        // 5. Cashier Sessions
        try {
            db.query("SELECT * FROM cashier_sessions", null).use { sessionsCursor ->
                val sessionsArray = JSONArray()
                while (sessionsCursor.moveToNext()) {
                    sessionsArray.put(JSONObject().apply {
                        put("id", sessionsCursor.getInt(sessionsCursor.getColumnIndexOrThrow("id")))
                        put("cashierUsername", sessionsCursor.getString(sessionsCursor.getColumnIndexOrThrow("cashierUsername")))
                        put("startTime", sessionsCursor.getLong(sessionsCursor.getColumnIndexOrThrow("startTime")))
                        val endIdx = sessionsCursor.getColumnIndexOrThrow("endTime")
                        if (!sessionsCursor.isNull(endIdx)) put("endTime", sessionsCursor.getLong(endIdx))
                        put("startingCash", sessionsCursor.getDouble(sessionsCursor.getColumnIndexOrThrow("startingCash")))
                        val endCashIdx = sessionsCursor.getColumnIndexOrThrow("endingCash")
                        if (!sessionsCursor.isNull(endCashIdx)) put("endingCash", sessionsCursor.getDouble(endCashIdx))
                        put("status", sessionsCursor.getString(sessionsCursor.getColumnIndexOrThrow("status")))
                    })
                }
                tables.put("cashier_sessions", sessionsArray)
            }
        } catch (e: Exception) {
            exportErrors.add("cashier_sessions: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting cashier_sessions: ${e.message}", e)
        }

        // 6. Transactions
        try {
            db.query("SELECT * FROM transactions", null).use { txCursor ->
                val txArray = JSONArray()
                while (txCursor.moveToNext()) {
                    txArray.put(JSONObject().apply {
                        put("id", txCursor.getInt(txCursor.getColumnIndexOrThrow("id")))
                        put("timestamp", txCursor.getLong(txCursor.getColumnIndexOrThrow("timestamp")))
                        put("cashierUsername", txCursor.getString(txCursor.getColumnIndexOrThrow("cashierUsername")))
                        put("subtotal", txCursor.getDouble(txCursor.getColumnIndexOrThrow("subtotal")))
                        put("taxAmount", txCursor.getDouble(txCursor.getColumnIndexOrThrow("taxAmount")))
                        put("totalAmount", txCursor.getDouble(txCursor.getColumnIndexOrThrow("totalAmount")))
                        put("paymentMethod", txCursor.getString(txCursor.getColumnIndexOrThrow("paymentMethod")))
                        put("cashPaid", txCursor.getDouble(txCursor.getColumnIndexOrThrow("cashPaid")))
                        put("changeAmount", txCursor.getDouble(txCursor.getColumnIndexOrThrow("changeAmount")))
                        val statusIdx = txCursor.getColumnIndex("status")
                        put("status", if (statusIdx != -1) txCursor.getString(statusIdx) else "COMPLETED")
                        val custIdx = txCursor.getColumnIndex("customerName")
                        put("customerName", if (custIdx != -1) txCursor.getString(custIdx) else "")
                    })
                }
                tables.put("transactions", txArray)
            }
        } catch (e: Exception) {
            exportErrors.add("transactions: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting transactions: ${e.message}", e)
        }

        // 7. Transaction Items
        try {
            db.query("SELECT * FROM transaction_items", null).use { itemsCursor ->
                val itemsArray = JSONArray()
                while (itemsCursor.moveToNext()) {
                    itemsArray.put(JSONObject().apply {
                        put("id", itemsCursor.getInt(itemsCursor.getColumnIndexOrThrow("id")))
                        put("transactionId", itemsCursor.getInt(itemsCursor.getColumnIndexOrThrow("transactionId")))
                        put("productId", itemsCursor.getInt(itemsCursor.getColumnIndexOrThrow("productId")))
                        put("productName", itemsCursor.getString(itemsCursor.getColumnIndexOrThrow("productName")))
                        put("price", itemsCursor.getDouble(itemsCursor.getColumnIndexOrThrow("price")))
                        put("quantity", itemsCursor.getInt(itemsCursor.getColumnIndexOrThrow("quantity")))
                        put("cost", itemsCursor.getDouble(itemsCursor.getColumnIndexOrThrow("cost")))
                        val uomIdx = itemsCursor.getColumnIndex("uomName")
                        put("uomName", if (uomIdx != -1) itemsCursor.getString(uomIdx) else "pc")
                    })
                }
                tables.put("transaction_items", itemsArray)
            }
        } catch (e: Exception) {
            exportErrors.add("transaction_items: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting transaction_items: ${e.message}", e)
        }

        // 8. Drawer Transactions
        try {
            db.query("SELECT * FROM drawer_transactions", null).use { drawerCursor ->
                val drawerArray = JSONArray()
                while (drawerCursor.moveToNext()) {
                    drawerArray.put(JSONObject().apply {
                        put("id", drawerCursor.getInt(drawerCursor.getColumnIndexOrThrow("id")))
                        put("sessionId", drawerCursor.getInt(drawerCursor.getColumnIndexOrThrow("sessionId")))
                        put("timestamp", drawerCursor.getLong(drawerCursor.getColumnIndexOrThrow("timestamp")))
                        put("cashierUsername", drawerCursor.getString(drawerCursor.getColumnIndexOrThrow("cashierUsername")))
                        put("amount", drawerCursor.getDouble(drawerCursor.getColumnIndexOrThrow("amount")))
                        put("type", drawerCursor.getString(drawerCursor.getColumnIndexOrThrow("type")))
                        put("reason", drawerCursor.getString(drawerCursor.getColumnIndexOrThrow("reason")))
                    })
                }
                tables.put("drawer_transactions", drawerArray)
            }
        } catch (e: Exception) {
            exportErrors.add("drawer_transactions: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting drawer_transactions: ${e.message}", e)
        }

        // 9. Payment Schedules
        try {
            db.query("SELECT * FROM payment_schedules", null).use { schedCursor ->
                val schedArray = JSONArray()
                while (schedCursor.moveToNext()) {
                    schedArray.put(JSONObject().apply {
                        put("id", schedCursor.getInt(schedCursor.getColumnIndexOrThrow("id")))
                        put("title", schedCursor.getString(schedCursor.getColumnIndexOrThrow("title")))
                        put("amount", schedCursor.getDouble(schedCursor.getColumnIndexOrThrow("amount")))
                        put("dueDate", schedCursor.getString(schedCursor.getColumnIndexOrThrow("dueDate")))
                        put("category", schedCursor.getString(schedCursor.getColumnIndexOrThrow("category")))
                        put("priority", schedCursor.getString(schedCursor.getColumnIndexOrThrow("priority")))
                        put("isPaid", schedCursor.getInt(schedCursor.getColumnIndexOrThrow("isPaid")) == 1)
                        put("paymentMethod", schedCursor.getString(schedCursor.getColumnIndexOrThrow("paymentMethod")))
                    })
                }
                tables.put("payment_schedules", schedArray)
            }
        } catch (e: Exception) {
            exportErrors.add("payment_schedules: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting payment_schedules: ${e.message}", e)
        }

        // 10. Product Variants
        try {
            db.query("SELECT * FROM product_variants", null).use { varCursor ->
                val varArray = JSONArray()
                while (varCursor.moveToNext()) {
                    varArray.put(JSONObject().apply {
                        put("id", varCursor.getInt(varCursor.getColumnIndexOrThrow("id")))
                        put("productId", varCursor.getInt(varCursor.getColumnIndexOrThrow("productId")))
                        put("variantName", varCursor.getString(varCursor.getColumnIndexOrThrow("variantName")))
                        put("uomName", varCursor.getString(varCursor.getColumnIndexOrThrow("uomName")))
                        put("multiplier", varCursor.getInt(varCursor.getColumnIndexOrThrow("multiplier")))
                        put("price", varCursor.getDouble(varCursor.getColumnIndexOrThrow("price")))
                        put("cost", varCursor.getDouble(varCursor.getColumnIndexOrThrow("cost")))
                        put("barcode", varCursor.getString(varCursor.getColumnIndexOrThrow("barcode")))
                        put("isDefault", varCursor.getInt(varCursor.getColumnIndexOrThrow("isDefault")) == 1)
                    })
                }
                tables.put("product_variants", varArray)
            }
        } catch (e: Exception) {
            exportErrors.add("product_variants: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting product_variants: ${e.message}", e)
        }

        // 11. Return Transactions & Items
        try {
            db.query("SELECT * FROM return_transactions", null).use { retTxCursor ->
                val retTxArray = JSONArray()
                while (retTxCursor.moveToNext()) {
                    retTxArray.put(JSONObject().apply {
                        put("id", retTxCursor.getInt(retTxCursor.getColumnIndexOrThrow("id")))
                        put("originalTransactionId", retTxCursor.getInt(retTxCursor.getColumnIndexOrThrow("originalTransactionId")))
                        put("timestamp", retTxCursor.getLong(retTxCursor.getColumnIndexOrThrow("timestamp")))
                        put("cashierUsername", retTxCursor.getString(retTxCursor.getColumnIndexOrThrow("cashierUsername")))
                        put("totalRefundAmount", retTxCursor.getDouble(retTxCursor.getColumnIndexOrThrow("totalRefundAmount")))
                        put("refundMethod", retTxCursor.getString(retTxCursor.getColumnIndexOrThrow("refundMethod")))
                        put("returnReason", retTxCursor.getString(retTxCursor.getColumnIndexOrThrow("returnReason")))
                        put("customerName", retTxCursor.getString(retTxCursor.getColumnIndexOrThrow("customerName")))
                        put("notes", retTxCursor.getString(retTxCursor.getColumnIndexOrThrow("notes")))
                    })
                }
                tables.put("return_transactions", retTxArray)
            }

            db.query("SELECT * FROM return_items", null).use { retItemCursor ->
                val retItemArray = JSONArray()
                while (retItemCursor.moveToNext()) {
                    retItemArray.put(JSONObject().apply {
                        put("id", retItemCursor.getInt(retItemCursor.getColumnIndexOrThrow("id")))
                        put("returnTransactionId", retItemCursor.getInt(retItemCursor.getColumnIndexOrThrow("returnTransactionId")))
                        put("productId", retItemCursor.getInt(retItemCursor.getColumnIndexOrThrow("productId")))
                        put("productName", retItemCursor.getString(retItemCursor.getColumnIndexOrThrow("productName")))
                        put("quantity", retItemCursor.getInt(retItemCursor.getColumnIndexOrThrow("quantity")))
                        put("unitPrice", retItemCursor.getDouble(retItemCursor.getColumnIndexOrThrow("unitPrice")))
                        put("refundAmount", retItemCursor.getDouble(retItemCursor.getColumnIndexOrThrow("refundAmount")))
                        put("uomName", retItemCursor.getString(retItemCursor.getColumnIndexOrThrow("uomName")))
                        put("restockToInventory", retItemCursor.getInt(retItemCursor.getColumnIndexOrThrow("restockToInventory")) == 1)
                    })
                }
                tables.put("return_items", retItemArray)
            }
        } catch (e: Exception) {
            exportErrors.add("return_transactions: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting returns: ${e.message}", e)
        }

        // 12. Suppliers & Purchase Orders
        try {
            db.query("SELECT * FROM suppliers", null).use { supCursor ->
                val supArray = JSONArray()
                while (supCursor.moveToNext()) {
                    supArray.put(JSONObject().apply {
                        put("id", supCursor.getInt(supCursor.getColumnIndexOrThrow("id")))
                        put("name", supCursor.getString(supCursor.getColumnIndexOrThrow("name")))
                        put("contactPerson", supCursor.getString(supCursor.getColumnIndexOrThrow("contactPerson")))
                        put("phone", supCursor.getString(supCursor.getColumnIndexOrThrow("phone")))
                        put("email", supCursor.getString(supCursor.getColumnIndexOrThrow("email")))
                        put("address", supCursor.getString(supCursor.getColumnIndexOrThrow("address")))
                        put("deliverySchedule", supCursor.getString(supCursor.getColumnIndexOrThrow("deliverySchedule")))
                        put("paymentTerms", supCursor.getString(supCursor.getColumnIndexOrThrow("paymentTerms")))
                        put("notes", supCursor.getString(supCursor.getColumnIndexOrThrow("notes")))
                    })
                }
                tables.put("suppliers", supArray)
            }

            db.query("SELECT * FROM purchase_orders", null).use { poCursor ->
                val poArray = JSONArray()
                while (poCursor.moveToNext()) {
                    poArray.put(JSONObject().apply {
                        put("id", poCursor.getInt(poCursor.getColumnIndexOrThrow("id")))
                        put("poNumber", poCursor.getString(poCursor.getColumnIndexOrThrow("poNumber")))
                        put("supplierId", poCursor.getInt(poCursor.getColumnIndexOrThrow("supplierId")))
                        put("supplierName", poCursor.getString(poCursor.getColumnIndexOrThrow("supplierName")))
                        put("orderDate", poCursor.getLong(poCursor.getColumnIndexOrThrow("orderDate")))
                        if (!poCursor.isNull(poCursor.getColumnIndexOrThrow("expectedDeliveryDate"))) {
                            put("expectedDeliveryDate", poCursor.getLong(poCursor.getColumnIndexOrThrow("expectedDeliveryDate")))
                        }
                        if (!poCursor.isNull(poCursor.getColumnIndexOrThrow("receivedDate"))) {
                            put("receivedDate", poCursor.getLong(poCursor.getColumnIndexOrThrow("receivedDate")))
                        }
                        put("totalCost", poCursor.getDouble(poCursor.getColumnIndexOrThrow("totalCost")))
                        put("status", poCursor.getString(poCursor.getColumnIndexOrThrow("status")))
                        put("paymentStatus", poCursor.getString(poCursor.getColumnIndexOrThrow("paymentStatus")))
                        put("notes", poCursor.getString(poCursor.getColumnIndexOrThrow("notes")))
                    })
                }
                tables.put("purchase_orders", poArray)
            }

            db.query("SELECT * FROM purchase_order_items", null).use { poiCursor ->
                val poiArray = JSONArray()
                while (poiCursor.moveToNext()) {
                    poiArray.put(JSONObject().apply {
                        put("id", poiCursor.getInt(poiCursor.getColumnIndexOrThrow("id")))
                        put("purchaseOrderId", poiCursor.getInt(poiCursor.getColumnIndexOrThrow("purchaseOrderId")))
                        put("productId", poiCursor.getInt(poiCursor.getColumnIndexOrThrow("productId")))
                        put("productName", poiCursor.getString(poiCursor.getColumnIndexOrThrow("productName")))
                        put("quantityOrdered", poiCursor.getInt(poiCursor.getColumnIndexOrThrow("quantityOrdered")))
                        put("quantityReceived", poiCursor.getInt(poiCursor.getColumnIndexOrThrow("quantityReceived")))
                        put("unitCost", poiCursor.getDouble(poiCursor.getColumnIndexOrThrow("unitCost")))
                        put("totalCost", poiCursor.getDouble(poiCursor.getColumnIndexOrThrow("totalCost")))
                        put("uomName", poiCursor.getString(poiCursor.getColumnIndexOrThrow("uomName")))
                    })
                }
                tables.put("purchase_order_items", poiArray)
            }
        } catch (e: Exception) {
            exportErrors.add("purchase_orders: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting purchase_orders: ${e.message}", e)
        }

        // 13. Parked Transactions & Items
        try {
            db.query("SELECT * FROM parked_transactions", null).use { parkedCursor ->
                val parkedArray = JSONArray()
                while (parkedCursor.moveToNext()) {
                    parkedArray.put(JSONObject().apply {
                        put("id", parkedCursor.getInt(parkedCursor.getColumnIndexOrThrow("id")))
                        put("timestamp", parkedCursor.getLong(parkedCursor.getColumnIndexOrThrow("timestamp")))
                        put("note", parkedCursor.getString(parkedCursor.getColumnIndexOrThrow("note")))
                    })
                }
                tables.put("parked_transactions", parkedArray)
            }

            db.query("SELECT * FROM parked_transaction_items", null).use { parkedItemsCursor ->
                val parkedItemsArray = JSONArray()
                while (parkedItemsCursor.moveToNext()) {
                    parkedItemsArray.put(JSONObject().apply {
                        put("id", parkedItemsCursor.getInt(parkedItemsCursor.getColumnIndexOrThrow("id")))
                        put("parkedTransactionId", parkedItemsCursor.getInt(parkedItemsCursor.getColumnIndexOrThrow("parkedTransactionId")))
                        put("productId", parkedItemsCursor.getInt(parkedItemsCursor.getColumnIndexOrThrow("productId")))
                        put("productName", parkedItemsCursor.getString(parkedItemsCursor.getColumnIndexOrThrow("productName")))
                        put("price", parkedItemsCursor.getDouble(parkedItemsCursor.getColumnIndexOrThrow("price")))
                        put("quantity", parkedItemsCursor.getInt(parkedItemsCursor.getColumnIndexOrThrow("quantity")))
                    })
                }
                tables.put("parked_transaction_items", parkedItemsArray)
            }
        } catch (e: Exception) {
            exportErrors.add("parked_transactions: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting parked_transactions: ${e.message}", e)
        }

        root.put("tables", tables)

        // M5: fail loud — record any table that failed to export so a partial backup is detectable.
        if (exportErrors.isNotEmpty()) {
            val errorsJson = JSONArray()
            exportErrors.forEach { errorsJson.put(it) }
            root.put("exportErrors", errorsJson)
        }

        // Checksum
        val tablesString = tables.toString()
        val checksum = MessageDigest.getInstance("SHA-256")
            .digest(tablesString.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        root.put("checksum", checksum)

        root.toString(2)
    }

    suspend fun saveBackupToFile(context: Context, jsonContent: String): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
        val filename = "storepoint_backup_${formatFileDate(Date())}.json"
        val file = File(backupDir, filename)
        FileOutputStream(file).use { out ->
            out.write(jsonContent.toByteArray(Charsets.UTF_8))
        }
        file
    }

    fun shareBackupFile(context: Context, file: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "StorePoint POS Backup - ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export / Share Database Backup"))
    }

    suspend fun importAndRestoreBackupJson(context: Context, db: AppDatabase, jsonContent: String): RestoreSummary = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonContent)
            if (!root.has("tables")) {
                return@withContext RestoreSummary(false, "Invalid backup format: missing 'tables' payload.")
            }

            val tables = root.getJSONObject("tables")
            val backupDate = root.optString("dateTime", "Unknown Date")

            var importedProducts = 0
            var importedTransactions = 0
            var importedUsers = 0

            // Execute full atomic replacement within a single database transaction
            db.runInTransaction {
                // Temporarily disable foreign keys during mass restore to prevent ordering issues
                try {
                    db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF;")
                } catch (_: Exception) {}

                try {
                    // Clear existing tables
                    db.clearAllTables()

                // Restore Store Config
                if (tables.has("store_config")) {
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
                    db.openHelper.writableDatabase.execSQL(
                        "INSERT OR REPLACE INTO store_config (id, storeName, currencySymbol, taxPercentage, setupCompleted, gcashBalance, hasGCash, hasMaya, hasLoad, smartLoadBalance, globeLoadBalance) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf<Any?>(cfg.id, cfg.storeName, cfg.currencySymbol, cfg.taxPercentage, if (cfg.setupCompleted) 1 else 0, cfg.gcashBalance, if (cfg.hasGCash) 1 else 0, if (cfg.hasMaya) 1 else 0, if (cfg.hasLoad) 1 else 0, cfg.smartLoadBalance, cfg.globeLoadBalance)
                    )
                }

                // Restore Users
                if (tables.has("users")) {
                    val usersArr = tables.getJSONArray("users")
                    importedUsers = usersArr.length()
                    for (i in 0 until usersArr.length()) {
                        val u = usersArr.getJSONObject(i)
                        val storedPinHash = if (u.has("pinHash")) u.getString("pinHash") else u.optString("passwordHash", "")
                        db.openHelper.writableDatabase.execSQL(
                            "INSERT OR REPLACE INTO user_accounts (username, pinHash, role, barcodeId) VALUES (?, ?, ?, ?)",
                            arrayOf<Any?>(u.getString("username"), storedPinHash, u.getString("role"), u.optString("barcodeId", ""))
                        )
                    }
                }

                // Restore Categories
                if (tables.has("categories")) {
                    val catArr = tables.getJSONArray("categories")
                    for (i in 0 until catArr.length()) {
                        val c = catArr.getJSONObject(i)
                        db.openHelper.writableDatabase.execSQL(
                            "INSERT OR REPLACE INTO categories (id, name) VALUES (?, ?)",
                            arrayOf<Any?>(c.getInt("id"), c.getString("name"))
                        )
                    }
                }

                // Restore Products
                if (tables.has("products")) {
                    val prodArr = tables.getJSONArray("products")
                    importedProducts = prodArr.length()
                    for (i in 0 until prodArr.length()) {
                        val p = prodArr.getJSONObject(i)
                        db.openHelper.writableDatabase.execSQL(
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
                }

                // Restore Cashier Sessions
                if (tables.has("cashier_sessions")) {
                    val sArr = tables.getJSONArray("cashier_sessions")
                    for (i in 0 until sArr.length()) {
                        val s = sArr.getJSONObject(i)
                        db.openHelper.writableDatabase.execSQL(
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

                // Restore Transactions
                if (tables.has("transactions")) {
                    val txArr = tables.getJSONArray("transactions")
                    importedTransactions = txArr.length()
                    for (i in 0 until txArr.length()) {
                        val tx = txArr.getJSONObject(i)
                        db.openHelper.writableDatabase.execSQL(
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
                }

                // Restore Transaction Items
                if (tables.has("transaction_items")) {
                    val itemArr = tables.getJSONArray("transaction_items")
                    for (i in 0 until itemArr.length()) {
                        val item = itemArr.getJSONObject(i)
                        db.openHelper.writableDatabase.execSQL(
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

                // Restore Drawer Transactions
                if (tables.has("drawer_transactions")) {
                    val dArr = tables.getJSONArray("drawer_transactions")
                    for (i in 0 until dArr.length()) {
                        val d = dArr.getJSONObject(i)
                        db.openHelper.writableDatabase.execSQL(
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

                // Restore Payment Schedules
                if (tables.has("payment_schedules")) {
                    val pArr = tables.getJSONArray("payment_schedules")
                    for (i in 0 until pArr.length()) {
                        val ps = pArr.getJSONObject(i)
                        db.openHelper.writableDatabase.execSQL(
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

                // Restore Product Variants
                if (tables.has("product_variants")) {
                    val varArr = tables.getJSONArray("product_variants")
                    for (i in 0 until varArr.length()) {
                        val v = varArr.getJSONObject(i)
                        db.openHelper.writableDatabase.execSQL(
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

                // Restore Returns
                if (tables.has("return_transactions")) {
                    val retArr = tables.getJSONArray("return_transactions")
                    for (i in 0 until retArr.length()) {
                        val r = retArr.getJSONObject(i)
                        db.openHelper.writableDatabase.execSQL(
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
                        db.openHelper.writableDatabase.execSQL(
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

                // Restore Suppliers & POs
                if (tables.has("suppliers")) {
                    val supArr = tables.getJSONArray("suppliers")
                    for (i in 0 until supArr.length()) {
                        val s = supArr.getJSONObject(i)
                        db.openHelper.writableDatabase.execSQL(
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
                        db.openHelper.writableDatabase.execSQL(
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
                        db.openHelper.writableDatabase.execSQL(
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

                // Restore Parked Transactions & Items
                if (tables.has("parked_transactions")) {
                    val parkedArr = tables.getJSONArray("parked_transactions")
                    for (i in 0 until parkedArr.length()) {
                        val p = parkedArr.getJSONObject(i)
                        db.openHelper.writableDatabase.execSQL(
                            "INSERT OR REPLACE INTO parked_transactions (id, timestamp, note) VALUES (?, ?, ?)",
                            arrayOf<Any?>(
                                p.getInt("id"),
                                p.getLong("timestamp"),
                                p.getString("note")
                            )
                        )
                    }
                }

                if (tables.has("parked_transaction_items")) {
                    val pItemArr = tables.getJSONArray("parked_transaction_items")
                    for (i in 0 until pItemArr.length()) {
                        val pi = pItemArr.getJSONObject(i)
                        db.openHelper.writableDatabase.execSQL(
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
            } finally {
                // Re-enable foreign key constraints
                try {
                    db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON;")
                } catch (_: Exception) {}
            }
        }

            // M5: warn loudly when the backup was exported with errors (possible partial backup).
            val exportWarning = if (root.optJSONArray("exportErrors")?.length() != null && root.optJSONArray("exportErrors")!!.length() > 0) {
                " WARNING: this backup was created with ${root.optJSONArray("exportErrors")!!.length()} table export error(s) and may be incomplete."
            } else {
                ""
            }

            RestoreSummary(
                success = true,
                message = "Database successfully restored from backup created on $backupDate" + exportWarning,
                productsCount = importedProducts,
                transactionsCount = importedTransactions,
                usersCount = importedUsers,
                backupDate = backupDate
            )
        } catch (e: Exception) {
            RestoreSummary(
                success = false,
                message = "Database restore failed: ${e.localizedMessage}"
            )
        }
    }

    data class BackupMetadata(
        val dateTime: String,
        val storeName: String,
        val productsCount: Int,
        val transactionsCount: Int,
        val usersCount: Int,
        val checksum: String,
        val file: File
    )

    fun getPersistentBackupDirectory(context: Context): File {
        // App-private internal storage: isolated from other apps on device and protected by Android sandbox
        val backupDir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
        return backupDir
    }

    suspend fun savePersistentSnapshot(context: Context, db: AppDatabase, txId: Int? = null): File? = withContext(Dispatchers.IO) {
        try {
            val json = exportBackupJson(db)
            val targetDir = getPersistentBackupDirectory(context)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            // A. Write Canonical Latest Vault File atomically
            val canonicalFile = File(targetDir, "storepoint_vault_latest.json")
            val tempFile = File(targetDir, "storepoint_vault_latest.tmp")
            FileOutputStream(tempFile).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            if (canonicalFile.exists()) canonicalFile.delete()
            tempFile.renameTo(canonicalFile)

            // B. If transaction checkout, write historical snapshot
            if (txId != null) {
                val txFile = File(targetDir, "storepoint_tx_${txId}_${formatFileDate(Date())}.json")
                FileOutputStream(txFile).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }

            // C. Also write a copy in app-private filesDir for fast local fallback
            try {
                val localDir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
                val localFile = File(localDir, "storepoint_backup_${formatFileDate(Date())}.json")
                FileOutputStream(localFile).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            } catch (_: Exception) {}

            // D. Prune older transaction files to conserve storage space (keep newest 50 tx backups)
            val allTxFiles = targetDir.listFiles { file ->
                file.name.startsWith("storepoint_tx_") && file.name.endsWith(".json")
            }?.sortedByDescending { it.lastModified() }

            if (allTxFiles != null && allTxFiles.size > 50) {
                for (i in 50 until allTxFiles.size) {
                    try {
                        allTxFiles[i].delete()
                    } catch (_: Exception) {}
                }
            }

            Log.d(TAG, "Silent persistent snapshot saved successfully: ${canonicalFile.absolutePath}")
            canonicalFile
        } catch (e: Exception) {
            Log.e(TAG, "Persistent snapshot error: ${e.message}", e)
            null
        }
    }

    fun getLatestPersistentBackup(context: Context): File? {
        val persistentDir = getPersistentBackupDirectory(context)
        // 1. Check canonical vault file
        val canonical = File(persistentDir, "storepoint_vault_latest.json")
        if (canonical.exists() && canonical.length() > 0) {
            return canonical
        }

        // 2. Check any other JSON backups in persistent dir
        val persistentFiles = persistentDir.listFiles { file ->
            file.extension.equals("json", ignoreCase = true) && file.length() > 0
        }?.sortedByDescending { it.lastModified() }

        if (!persistentFiles.isNullOrEmpty()) {
            return persistentFiles.first()
        }

        // 3. Fallback to private app backups if persistent dir is empty
        return listLocalSnapshots(context).firstOrNull()
    }

    fun peekBackupMetadata(file: File): BackupMetadata? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val content = FileInputStream(file).use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
            val root = JSONObject(content)
            val dateTime = root.optString("dateTime", formatDate(Date(file.lastModified())))
            val checksum = root.optString("checksum", "")

            var storeName = "StorePoint Store"
            var prodCount = 0
            var txCount = 0
            var userCount = 0

            if (root.has("tables")) {
                val tables = root.getJSONObject("tables")
                if (tables.has("store_config")) {
                    storeName = tables.getJSONObject("store_config").optString("storeName", storeName)
                }
                if (tables.has("products")) {
                    prodCount = tables.getJSONArray("products").length()
                }
                if (tables.has("transactions")) {
                    txCount = tables.getJSONArray("transactions").length()
                }
                if (tables.has("users")) {
                    userCount = tables.getJSONArray("users").length()
                }
            }

            BackupMetadata(
                dateTime = dateTime,
                storeName = storeName,
                productsCount = prodCount,
                transactionsCount = txCount,
                usersCount = userCount,
                checksum = checksum,
                file = file
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error peeking backup metadata for ${file.name}: ${e.message}")
            null
        }
    }

    suspend fun autoCreateLocalSnapshot(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        try {
            savePersistentSnapshot(context, db)
            // Prune snapshots older than 7 days
            val dir = File(context.filesDir, "backups")
            if (dir.exists() && dir.isDirectory) {
                val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
                dir.listFiles()?.forEach { file ->
                    if (file.lastModified() < sevenDaysAgo) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BackupManager", "Auto-snapshot failed", e)
        }
    }

    fun listLocalSnapshots(context: Context): List<File> {
        val persistentFiles = try {
            val dir = getPersistentBackupDirectory(context)
            dir.listFiles()?.filter { it.extension.equals("json", ignoreCase = true) } ?: emptyList()
        } catch (_: Exception) {
            emptyList<File>()
        }
        val localFiles = try {
            val dir = File(context.filesDir, "backups")
            dir.listFiles()?.filter { it.extension.equals("json", ignoreCase = true) } ?: emptyList()
        } catch (_: Exception) {
            emptyList<File>()
        }
        val combined = (persistentFiles + localFiles)
            .distinctBy { it.name }
            .sortedByDescending { it.lastModified() }
        return combined
    }
}
