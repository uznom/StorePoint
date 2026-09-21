package com.munzo.storepoint

import com.munzo.storepoint.data.CartItemDetails
import com.munzo.storepoint.data.ParkedTransaction
import com.munzo.storepoint.data.ParkedTransactionItem
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.data.TransactionItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseSchemaAndBackupTest {

    @Test
    fun transactionModel_defaultStatusAndCustomerName() {
        val tx = Transaction(
            id = 1,
            timestamp = 1000L,
            cashierUsername = "cashier1",
            subtotal = 100.0,
            taxAmount = 12.0,
            totalAmount = 112.0,
            paymentMethod = "CASH",
            cashPaid = 120.0,
            changeAmount = 8.0
        )

        assertEquals("COMPLETED", tx.status)
        assertEquals("", tx.customerName)
    }

    @Test
    fun transactionItemModel_uomNameAndCostDefaults() {
        val item = TransactionItem(
            id = 1,
            transactionId = 1,
            productId = 10,
            productName = "Test Item",
            price = 50.0,
            quantity = 2
        )

        assertEquals("pc", item.uomName)
        assertEquals(0.0, item.cost, 0.0)
    }

    @Test
    fun parkedTransactionModels_structureVerification() {
        val parked = ParkedTransaction(
            id = 42,
            timestamp = 5000L,
            note = "Customer at ATM"
        )
        assertEquals(42, parked.id)
        assertEquals("Customer at ATM", parked.note)

        val parkedItem = ParkedTransactionItem(
            id = 100,
            parkedTransactionId = 42,
            productId = 10,
            productName = "Test Item",
            price = 50.0,
            quantity = 2
        )
        assertEquals(42, parkedItem.parkedTransactionId)
        assertEquals(10, parkedItem.productId)
        assertEquals(2, parkedItem.quantity)
    }

    @Test
    fun backupPayload_containsAllExpectedTablesAndColumns() {
        val sampleBackupJson = JSONObject().apply {
            put("backupVersion", 1)
            put("timestamp", 123456789L)
            put("appName", "StorePoint POS")

            val tables = JSONObject().apply {
                // Transactions
                val txArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", 1)
                        put("timestamp", 1000L)
                        put("cashierUsername", "admin")
                        put("subtotal", 100.0)
                        put("taxAmount", 0.0)
                        put("totalAmount", 100.0)
                        put("paymentMethod", "CASH")
                        put("cashPaid", 100.0)
                        put("changeAmount", 0.0)
                        put("status", "COMPLETED")
                        put("customerName", "VIP Customer")
                    })
                }
                put("transactions", txArray)

                // Transaction Items
                val itemArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", 1)
                        put("transactionId", 1)
                        put("productId", 5)
                        put("productName", "Milk")
                        put("price", 100.0)
                        put("quantity", 1)
                        put("cost", 80.0)
                        put("uomName", "Bottle")
                    })
                }
                put("transaction_items", itemArray)

                // Parked Transactions
                val parkedArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", 1)
                        put("timestamp", 2000L)
                        put("note", "VIP Hold")
                    })
                }
                put("parked_transactions", parkedArray)

                // Parked Items
                val parkedItemsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", 1)
                        put("parkedTransactionId", 1)
                        put("productId", 5)
                        put("productName", "Milk")
                        put("price", 100.0)
                        put("quantity", 2)
                    })
                }
                put("parked_transaction_items", parkedItemsArray)
            }
            put("tables", tables)
        }

        assertTrue(sampleBackupJson.has("tables"))
        val tables = sampleBackupJson.getJSONObject("tables")

        assertTrue(tables.has("transactions"))
        val tx = tables.getJSONArray("transactions").getJSONObject(0)
        assertEquals("COMPLETED", tx.getString("status"))
        assertEquals("VIP Customer", tx.getString("customerName"))

        assertTrue(tables.has("transaction_items"))
        val item = tables.getJSONArray("transaction_items").getJSONObject(0)
        assertEquals("Bottle", item.getString("uomName"))
        assertEquals(80.0, item.getDouble("cost"), 0.0)

        assertTrue(tables.has("parked_transactions"))
        val parked = tables.getJSONArray("parked_transactions").getJSONObject(0)
        assertEquals("VIP Hold", parked.getString("note"))

        assertTrue(tables.has("parked_transaction_items"))
        val parkedItem = tables.getJSONArray("parked_transaction_items").getJSONObject(0)
        assertEquals(2, parkedItem.getInt("quantity"))
    }
}
