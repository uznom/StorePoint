package com.munzo.storepoint.util

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.munzo.storepoint.data.AppDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports and restores the 6 audit, reconciliation, BOM/production, inventory movement, and
 * offline print queue tables so full StorePoint disaster recovery leaves zero orphaned ledgers.
 */
internal object BackupJsonExtendedTables {

    private const val TAG = "BackupJsonExtended"

    fun exportAll(db: AppDatabase, tables: JSONObject, errors: MutableList<String>) {
        exportWalletLedger(db, tables, errors)
        exportShiftReconciliations(db, tables, errors)
        exportBillOfMaterials(db, tables, errors)
        exportProductionRuns(db, tables, errors)
        exportInventoryMovements(db, tables, errors)
        exportPendingPrintJobs(db, tables, errors)
    }

    fun restoreAll(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        restoreWalletLedger(sqlDb, tables)
        restoreShiftReconciliations(sqlDb, tables)
        restoreBillOfMaterials(sqlDb, tables)
        restoreProductionRuns(sqlDb, tables)
        restoreInventoryMovements(sqlDb, tables)
        restorePendingPrintJobs(sqlDb, tables)
    }

    private fun exportWalletLedger(db: AppDatabase, tables: JSONObject, errors: MutableList<String>) {
        try {
            db.query("SELECT * FROM wallet_ledger", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("walletType", c.getString(c.getColumnIndexOrThrow("walletType")))
                        put("timestamp", c.getLong(c.getColumnIndexOrThrow("timestamp")))
                        put("actorUsername", c.getString(c.getColumnIndexOrThrow("actorUsername")))
                        put("type", c.getString(c.getColumnIndexOrThrow("type")))
                        put("delta", c.getDouble(c.getColumnIndexOrThrow("delta")))
                        put("balanceAfter", c.getDouble(c.getColumnIndexOrThrow("balanceAfter")))
                        put("reference", c.getString(c.getColumnIndexOrThrow("reference")))
                        put("notes", c.getString(c.getColumnIndexOrThrow("notes")))
                    })
                }
                tables.put("wallet_ledger", arr)
            }
        } catch (e: Exception) {
            errors.add("wallet_ledger: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting wallet_ledger: ${e.message}", e)
        }
    }

    private fun exportShiftReconciliations(db: AppDatabase, tables: JSONObject, errors: MutableList<String>) {
        try {
            db.query("SELECT * FROM shift_reconciliations", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("sessionId", c.getInt(c.getColumnIndexOrThrow("sessionId")))
                        put("cashierUsername", c.getString(c.getColumnIndexOrThrow("cashierUsername")))
                        put("timestamp", c.getLong(c.getColumnIndexOrThrow("timestamp")))
                        put("count1000", c.getInt(c.getColumnIndexOrThrow("count1000")))
                        put("count500", c.getInt(c.getColumnIndexOrThrow("count500")))
                        put("count200", c.getInt(c.getColumnIndexOrThrow("count200")))
                        put("count100", c.getInt(c.getColumnIndexOrThrow("count100")))
                        put("count50", c.getInt(c.getColumnIndexOrThrow("count50")))
                        put("count20", c.getInt(c.getColumnIndexOrThrow("count20")))
                        put("countCoins", c.getDouble(c.getColumnIndexOrThrow("countCoins")))
                        put("totalCountedCash", c.getDouble(c.getColumnIndexOrThrow("totalCountedCash")))
                        put("expectedCash", c.getDouble(c.getColumnIndexOrThrow("expectedCash")))
                        put("cashVariance", c.getDouble(c.getColumnIndexOrThrow("cashVariance")))
                        put("countedGCash", c.getDouble(c.getColumnIndexOrThrow("countedGCash")))
                        put("expectedGCash", c.getDouble(c.getColumnIndexOrThrow("expectedGCash")))
                        put("gcashVariance", c.getDouble(c.getColumnIndexOrThrow("gcashVariance")))
                        put("countedSmartLoad", c.getDouble(c.getColumnIndexOrThrow("countedSmartLoad")))
                        put("expectedSmartLoad", c.getDouble(c.getColumnIndexOrThrow("expectedSmartLoad")))
                        put("smartLoadVariance", c.getDouble(c.getColumnIndexOrThrow("smartLoadVariance")))
                        put("countedGlobeLoad", c.getDouble(c.getColumnIndexOrThrow("countedGlobeLoad")))
                        put("expectedGlobeLoad", c.getDouble(c.getColumnIndexOrThrow("expectedGlobeLoad")))
                        put("globeLoadVariance", c.getDouble(c.getColumnIndexOrThrow("globeLoadVariance")))
                        put("hasDiscrepancy", c.getInt(c.getColumnIndexOrThrow("hasDiscrepancy")) == 1)
                        put("managerOverrideApproved", c.getInt(c.getColumnIndexOrThrow("managerOverrideApproved")) == 1)
                        put("managerUsername", c.getString(c.getColumnIndexOrThrow("managerUsername")))
                        put("discrepancyReason", c.getString(c.getColumnIndexOrThrow("discrepancyReason")))
                        put("receiptPrinted", c.getInt(c.getColumnIndexOrThrow("receiptPrinted")) == 1)
                    })
                }
                tables.put("shift_reconciliations", arr)
            }
        } catch (e: Exception) {
            errors.add("shift_reconciliations: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting shift_reconciliations: ${e.message}", e)
        }
    }

    private fun exportBillOfMaterials(db: AppDatabase, tables: JSONObject, errors: MutableList<String>) {
        try {
            db.query("SELECT * FROM bill_of_materials", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("rawMaterialProductId", c.getInt(c.getColumnIndexOrThrow("rawMaterialProductId")))
                        put("targetRetailProductId", c.getInt(c.getColumnIndexOrThrow("targetRetailProductId")))
                        put("rawQuantityNeeded", c.getDouble(c.getColumnIndexOrThrow("rawQuantityNeeded")))
                        put("producedRetailYield", c.getDouble(c.getColumnIndexOrThrow("producedRetailYield")))
                        put("wasteLossPercentage", c.getDouble(c.getColumnIndexOrThrow("wasteLossPercentage")))
                        put("notes", c.getString(c.getColumnIndexOrThrow("notes")))
                    })
                }
                tables.put("bill_of_materials", arr)
            }
        } catch (e: Exception) {
            errors.add("bill_of_materials: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting bill_of_materials: ${e.message}", e)
        }
    }

    private fun exportProductionRuns(db: AppDatabase, tables: JSONObject, errors: MutableList<String>) {
        try {
            db.query("SELECT * FROM production_runs", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("productionCode", c.getString(c.getColumnIndexOrThrow("productionCode")))
                        put("rawMaterialProductId", c.getInt(c.getColumnIndexOrThrow("rawMaterialProductId")))
                        put("rawMaterialName", c.getString(c.getColumnIndexOrThrow("rawMaterialName")))
                        put("rawMaterialQtyUsed", c.getDouble(c.getColumnIndexOrThrow("rawMaterialQtyUsed")))
                        put("rawMaterialUnitCost", c.getDouble(c.getColumnIndexOrThrow("rawMaterialUnitCost")))
                        put("targetRetailProductId", c.getInt(c.getColumnIndexOrThrow("targetRetailProductId")))
                        put("targetRetailProductName", c.getString(c.getColumnIndexOrThrow("targetRetailProductName")))
                        put("targetRetailQtyProduced", c.getDouble(c.getColumnIndexOrThrow("targetRetailQtyProduced")))
                        put("derivedRetailUnitCost", c.getDouble(c.getColumnIndexOrThrow("derivedRetailUnitCost")))
                        put("retailSellingPrice", c.getDouble(c.getColumnIndexOrThrow("retailSellingPrice")))
                        put("wasteLossQty", c.getDouble(c.getColumnIndexOrThrow("wasteLossQty")))
                        put("operatorUsername", c.getString(c.getColumnIndexOrThrow("operatorUsername")))
                        put("timestamp", c.getLong(c.getColumnIndexOrThrow("timestamp")))
                        put("notes", c.getString(c.getColumnIndexOrThrow("notes")))
                    })
                }
                tables.put("production_runs", arr)
            }
        } catch (e: Exception) {
            errors.add("production_runs: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting production_runs: ${e.message}", e)
        }
    }

    private fun exportInventoryMovements(db: AppDatabase, tables: JSONObject, errors: MutableList<String>) {
        try {
            db.query("SELECT * FROM inventory_movements", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("productId", c.getInt(c.getColumnIndexOrThrow("productId")))
                        put("productName", c.getString(c.getColumnIndexOrThrow("productName")))
                        put("timestamp", c.getLong(c.getColumnIndexOrThrow("timestamp")))
                        put("actorUsername", c.getString(c.getColumnIndexOrThrow("actorUsername")))
                        put("movementType", c.getString(c.getColumnIndexOrThrow("movementType")))
                        put("quantityDelta", c.getDouble(c.getColumnIndexOrThrow("quantityDelta")))
                        put("stockBefore", c.getInt(c.getColumnIndexOrThrow("stockBefore")))
                        put("stockAfter", c.getInt(c.getColumnIndexOrThrow("stockAfter")))
                        put("reference", c.getString(c.getColumnIndexOrThrow("reference")))
                        put("notes", c.getString(c.getColumnIndexOrThrow("notes")))
                    })
                }
                tables.put("inventory_movements", arr)
            }
        } catch (e: Exception) {
            errors.add("inventory_movements: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting inventory_movements: ${e.message}", e)
        }
    }

    private fun exportPendingPrintJobs(db: AppDatabase, tables: JSONObject, errors: MutableList<String>) {
        try {
            db.query("SELECT * FROM pending_print_jobs", null).use { c ->
                val arr = JSONArray()
                while (c.moveToNext()) {
                    arr.put(JSONObject().apply {
                        put("id", c.getInt(c.getColumnIndexOrThrow("id")))
                        put("createdAt", c.getLong(c.getColumnIndexOrThrow("createdAt")))
                        put("jobType", c.getString(c.getColumnIndexOrThrow("jobType")))
                        put("label", c.getString(c.getColumnIndexOrThrow("label")))
                        put("payloadBase64", c.getString(c.getColumnIndexOrThrow("payloadBase64")))
                        put("lastError", c.getString(c.getColumnIndexOrThrow("lastError")))
                        put("retryCount", c.getInt(c.getColumnIndexOrThrow("retryCount")))
                    })
                }
                tables.put("pending_print_jobs", arr)
            }
        } catch (e: Exception) {
            errors.add("pending_print_jobs: ${e.localizedMessage}")
            Log.w(TAG, "Error exporting pending_print_jobs: ${e.message}", e)
        }
    }

    private fun restoreWalletLedger(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        if (!tables.has("wallet_ledger")) return
        val arr = tables.getJSONArray("wallet_ledger")
        for (i in 0 until arr.length()) {
            val w = arr.getJSONObject(i)
            sqlDb.execSQL(
                "INSERT OR REPLACE INTO wallet_ledger (id, walletType, timestamp, actorUsername, type, delta, balanceAfter, reference, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    w.getInt("id"),
                    w.getString("walletType"),
                    w.getLong("timestamp"),
                    w.getString("actorUsername"),
                    w.getString("type"),
                    w.getDouble("delta"),
                    w.getDouble("balanceAfter"),
                    w.optString("reference", ""),
                    w.optString("notes", "")
                )
            )
        }
    }

    private fun restoreShiftReconciliations(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        if (!tables.has("shift_reconciliations")) return
        val arr = tables.getJSONArray("shift_reconciliations")
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            sqlDb.execSQL(
                """
                INSERT OR REPLACE INTO shift_reconciliations (
                    id, sessionId, cashierUsername, timestamp,
                    count1000, count500, count200, count100, count50, count20, countCoins,
                    totalCountedCash, expectedCash, cashVariance,
                    countedGCash, expectedGCash, gcashVariance,
                    countedSmartLoad, expectedSmartLoad, smartLoadVariance,
                    countedGlobeLoad, expectedGlobeLoad, globeLoadVariance,
                    hasDiscrepancy, managerOverrideApproved, managerUsername, discrepancyReason, receiptPrinted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    r.getInt("id"),
                    r.getInt("sessionId"),
                    r.getString("cashierUsername"),
                    r.getLong("timestamp"),
                    r.optInt("count1000", 0),
                    r.optInt("count500", 0),
                    r.optInt("count200", 0),
                    r.optInt("count100", 0),
                    r.optInt("count50", 0),
                    r.optInt("count20", 0),
                    r.optDouble("countCoins", 0.0),
                    r.optDouble("totalCountedCash", 0.0),
                    r.optDouble("expectedCash", 0.0),
                    r.optDouble("cashVariance", 0.0),
                    r.optDouble("countedGCash", 0.0),
                    r.optDouble("expectedGCash", 0.0),
                    r.optDouble("gcashVariance", 0.0),
                    r.optDouble("countedSmartLoad", 0.0),
                    r.optDouble("expectedSmartLoad", 0.0),
                    r.optDouble("smartLoadVariance", 0.0),
                    r.optDouble("countedGlobeLoad", 0.0),
                    r.optDouble("expectedGlobeLoad", 0.0),
                    r.optDouble("globeLoadVariance", 0.0),
                    if (r.optBoolean("hasDiscrepancy", false)) 1 else 0,
                    if (r.optBoolean("managerOverrideApproved", false)) 1 else 0,
                    r.optString("managerUsername", ""),
                    r.optString("discrepancyReason", ""),
                    if (r.optBoolean("receiptPrinted", false)) 1 else 0
                )
            )
        }
    }

    private fun restoreBillOfMaterials(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        if (!tables.has("bill_of_materials")) return
        val arr = tables.getJSONArray("bill_of_materials")
        for (i in 0 until arr.length()) {
            val b = arr.getJSONObject(i)
            sqlDb.execSQL(
                "INSERT OR REPLACE INTO bill_of_materials (id, rawMaterialProductId, targetRetailProductId, rawQuantityNeeded, producedRetailYield, wasteLossPercentage, notes) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    b.getInt("id"),
                    b.getInt("rawMaterialProductId"),
                    b.getInt("targetRetailProductId"),
                    b.getDouble("rawQuantityNeeded"),
                    b.getDouble("producedRetailYield"),
                    b.optDouble("wasteLossPercentage", 0.0),
                    b.optString("notes", "")
                )
            )
        }
    }

    private fun restoreProductionRuns(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        if (!tables.has("production_runs")) return
        val arr = tables.getJSONArray("production_runs")
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            sqlDb.execSQL(
                """
                INSERT OR REPLACE INTO production_runs (
                    id, productionCode, rawMaterialProductId, rawMaterialName,
                    rawMaterialQtyUsed, rawMaterialUnitCost, targetRetailProductId,
                    targetRetailProductName, targetRetailQtyProduced, derivedRetailUnitCost,
                    retailSellingPrice, wasteLossQty, operatorUsername, timestamp, notes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    p.getInt("id"),
                    p.getString("productionCode"),
                    p.getInt("rawMaterialProductId"),
                    p.getString("rawMaterialName"),
                    p.getDouble("rawMaterialQtyUsed"),
                    p.getDouble("rawMaterialUnitCost"),
                    p.getInt("targetRetailProductId"),
                    p.getString("targetRetailProductName"),
                    p.getDouble("targetRetailQtyProduced"),
                    p.getDouble("derivedRetailUnitCost"),
                    p.getDouble("retailSellingPrice"),
                    p.optDouble("wasteLossQty", 0.0),
                    p.getString("operatorUsername"),
                    p.getLong("timestamp"),
                    p.optString("notes", "")
                )
            )
        }
    }

    private fun restoreInventoryMovements(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        if (!tables.has("inventory_movements")) return
        val arr = tables.getJSONArray("inventory_movements")
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            sqlDb.execSQL(
                "INSERT OR REPLACE INTO inventory_movements (id, productId, productName, timestamp, actorUsername, movementType, quantityDelta, stockBefore, stockAfter, reference, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    m.getInt("id"),
                    m.getInt("productId"),
                    m.getString("productName"),
                    m.getLong("timestamp"),
                    m.getString("actorUsername"),
                    m.getString("movementType"),
                    m.getDouble("quantityDelta"),
                    m.getInt("stockBefore"),
                    m.getInt("stockAfter"),
                    m.optString("reference", ""),
                    m.optString("notes", "")
                )
            )
        }
    }

    private fun restorePendingPrintJobs(sqlDb: SupportSQLiteDatabase, tables: JSONObject) {
        if (!tables.has("pending_print_jobs")) return
        val arr = tables.getJSONArray("pending_print_jobs")
        for (i in 0 until arr.length()) {
            val j = arr.getJSONObject(i)
            sqlDb.execSQL(
                "INSERT OR REPLACE INTO pending_print_jobs (id, createdAt, jobType, label, payloadBase64, lastError, retryCount) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    j.getInt("id"),
                    j.getLong("createdAt"),
                    j.getString("jobType"),
                    j.getString("label"),
                    j.getString("payloadBase64"),
                    j.optString("lastError", ""),
                    j.optInt("retryCount", 0)
                )
            )
        }
    }
}
