package com.munzo.storepoint

import com.munzo.storepoint.data.Product
import com.munzo.storepoint.data.StorePointRepository
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.util.BackupIntegrityCodec
import com.munzo.storepoint.util.DatabaseBackupManager
import com.munzo.storepoint.util.PdfExportUtil
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class DataAndFinancialIntegrityTest {

    // =========================================================================
    // 1. Backup Canonical Checksum & Tamper Detection
    // =========================================================================

    @Test
    fun canonicalChecksum_isDeterministicAcrossKeyReordering() {
        val tablesA = JSONObject().apply {
            put("products", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1)
                    put("name", "Coffee 3-in-1")
                    put("price", 12.5)
                    put("stockCount", 40)
                })
            })
            put("store_config", JSONObject().apply {
                put("storeName", "Aling Nena Store")
                put("gcashBalance", 2500.0)
            })
        }

        val tablesB = JSONObject().apply {
            put("store_config", JSONObject().apply {
                put("gcashBalance", 2500)
                put("storeName", "Aling Nena Store")
            })
            put("products", JSONArray().apply {
                put(JSONObject().apply {
                    put("stockCount", 40)
                    put("price", 12.5)
                    put("name", "Coffee 3-in-1")
                    put("id", 1)
                })
            })
        }

        val hashA = BackupIntegrityCodec.computeCanonicalChecksum(tablesA)
        val hashB = BackupIntegrityCodec.computeCanonicalChecksum(tablesB)
        assertEquals(64, hashA.length)
        assertEquals(hashA, hashB)
    }

    @Test
    fun verifyChecksum_rejectsTamperedFinancialRecord() {
        val tables = JSONObject().apply {
            put("transactions", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 10)
                    put("totalAmount", 150.0)
                    put("status", "COMPLETED")
                })
            })
        }
        val root = JSONObject().apply {
            put("backupVersion", 1)
            put("tables", tables)
            put("checksumAlgorithm", BackupIntegrityCodec.CHECKSUM_ALGO_CANONICAL)
            put("checksum", BackupIntegrityCodec.computeCanonicalChecksum(tables))
        }

        assertTrue(BackupIntegrityCodec.verifyChecksum(root, tables))

        // Tamper with the transaction total by 1 centavo
        tables.getJSONArray("transactions").getJSONObject(0).put("totalAmount", 149.99)
        assertFalse(BackupIntegrityCodec.verifyChecksum(root, tables))
    }

    @Test
    fun verifyChecksum_supportsLegacyUnmarkedAndChecksumlessBackups() {
        val tables = JSONObject().apply {
            put("categories", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1)
                    put("name", "Beverages")
                })
            })
        }
        val legacyRawHash = MessageDigest.getInstance("SHA-256")
            .digest(tables.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val legacyRoot = JSONObject().apply {
            put("tables", tables)
            put("checksum", legacyRawHash)
        }
        assertTrue(BackupIntegrityCodec.verifyChecksum(legacyRoot, tables))

        val corruptedLegacyRoot = JSONObject().apply {
            put("tables", tables)
            put("checksum", "0".repeat(64))
        }
        assertFalse(BackupIntegrityCodec.verifyChecksum(corruptedLegacyRoot, tables))

        val noChecksumRoot = JSONObject().apply {
            put("tables", tables)
        }
        assertTrue(BackupIntegrityCodec.verifyChecksum(noChecksumRoot, tables))
    }

    @Test
    fun base64PureCodec_roundTripsAllByteValuesAndEdgeLengths() {
        val cases = listOf(
            ByteArray(0),
            byteArrayOf(0x00),
            byteArrayOf(0x01, 0x02),
            byteArrayOf(0x01, 0x02, 0x03),
            "StorePoint POS Encrypted Payload!".toByteArray(Charsets.UTF_8),
            ByteArray(256) { it.toByte() }
        )

        for (raw in cases) {
            val encoded = BackupIntegrityCodec.encodeBase64Pure(raw)
            val decoded = BackupIntegrityCodec.decodeBase64Pure(encoded)
            assertArrayEquals("Round-trip mismatch for length ${raw.size}", raw, decoded)
        }
    }

    @Test
    fun encryptedBackupEnvelope_rejectsWrongPassphraseAndTamperedCiphertext() = kotlinx.coroutines.runBlocking {
        val payload = """{"backupVersion":1,"tables":{"transactions":[{"id":1,"totalAmount":500.0}]}}"""
        val encResult = DatabaseBackupManager.encryptBackupJson(payload, "StrongPass#2026")
        assertTrue(encResult.isSuccess)
        val encrypted = encResult.getOrThrow()

        val env = JSONObject(encrypted)
        assertEquals(1, env.getInt("enc"))

        val decResult = DatabaseBackupManager.decryptBackupJson(encrypted, "StrongPass#2026")
        assertTrue(decResult.isSuccess)
        assertEquals(payload, decResult.getOrThrow())

        // Wrong passphrase must fail
        val wrongPassResult = DatabaseBackupManager.decryptBackupJson(encrypted, "WrongPass#2026")
        assertTrue("Decryption with wrong passphrase must fail", wrongPassResult.isFailure)

        // Tampering with ciphertext must fail HMAC-SHA256 tag verification
        val ct = env.getString("data")
        val flippedChar = if (ct.first() == 'A') 'B' else 'A'
        env.put("data", flippedChar + ct.substring(1))
        val tamperResult = DatabaseBackupManager.decryptBackupJson(env.toString(), "StrongPass#2026")
        assertTrue("Tampered ciphertext must fail authentication check", tamperResult.isFailure)
    }

    // =========================================================================
    // 2. Financial Precision, VAT & Wallet Accounting Invariants
    // =========================================================================

    @Test
    fun roundMoney_handlesHalfUpAndNonFiniteValuesSafely() {
        assertEquals(12.35, StorePointRepository.roundMoney(12.345), 0.0)
        assertEquals(12.34, StorePointRepository.roundMoney(12.344), 0.0)
        assertEquals(-5.68, StorePointRepository.roundMoney(-5.675), 0.0)
        assertEquals(0.0, StorePointRepository.roundMoney(Double.NaN), 0.0)
        assertEquals(0.0, StorePointRepository.roundMoney(Double.POSITIVE_INFINITY), 0.0)
        assertEquals(0.0, StorePointRepository.roundMoney(Double.NEGATIVE_INFINITY), 0.0)
    }

    @Test
    fun vatInclusiveDecomposition_alwaysReconcilesToExactGrossAmount() {
        val testAmounts = listOf(1.0, 19.99, 50.0, 100.0, 112.0, 333.33, 1499.75, 9999.95)
        for (gross in testAmounts) {
            val vat = StorePointRepository.calculateVatFromInclusive(gross, 12.0)
            val vatable = StorePointRepository.calculateVatableSalesFromInclusive(gross, 12.0)
            val reconstructed = StorePointRepository.roundMoney(vatable + vat)
            assertEquals("Vatable + VAT must equal gross for $gross", gross, reconstructed, 0.001)
            assertTrue("VAT must be non-negative for $gross", vat >= 0.0)
            assertTrue("Vatable must be positive for $gross", vatable > 0.0)
        }
        assertEquals(0.0, StorePointRepository.calculateVatFromInclusive(-10.0, 12.0), 0.0)
        assertEquals(0.0, StorePointRepository.calculateVatFromInclusive(Double.NaN, 12.0), 0.0)
    }

    @Test
    fun canonicalWalletType_mapsCarriersConsistently() {
        assertEquals(StorePointRepository.WalletType.GCASH, StorePointRepository.canonicalWalletType("GCash"))
        assertEquals(StorePointRepository.WalletType.SMART, StorePointRepository.canonicalWalletType("Smart / TNT"))
        assertEquals(StorePointRepository.WalletType.SMART, StorePointRepository.canonicalWalletType("DITO Load"))
        assertEquals(StorePointRepository.WalletType.GLOBE, StorePointRepository.canonicalWalletType("Globe / TM"))
    }

    // =========================================================================
    // 3. CSV Formula Neutralization & Financial Report Integrity
    // =========================================================================

    @Test
    fun csvExport_neutralizesFormulaInjectionWhilePreservingDataAndTotals() {
        assertEquals("'=CMD|'/C calc'!A0", PdfExportUtil.sanitizeCsvText("=CMD|'/C calc'!A0"))
        assertEquals("'+1234", PdfExportUtil.sanitizeCsvText("+1234"))
        assertEquals("'-SUM(A1:A2)", PdfExportUtil.sanitizeCsvText("-SUM(A1:A2)"))
        assertEquals("'@IMPORTXML", PdfExportUtil.sanitizeCsvText("@IMPORTXML"))
        assertEquals("Juan \"\"Suki\"\" Dela Cruz", PdfExportUtil.sanitizeCsvText("Juan \"Suki\" Dela Cruz"))

        val tx = Transaction(
            id = 42,
            timestamp = 1700000000000L,
            cashierUsername = "cashier1",
            subtotal = 89.29,
            taxAmount = 10.71,
            totalAmount = 100.0,
            paymentMethod = "CASH",
            cashPaid = 100.0,
            changeAmount = 0.0,
            status = "COMPLETED",
            customerName = "=HYPERLINK(\"http://bad\")"
        )
        val salesCsv = PdfExportUtil.buildSalesSummaryCsv(listOf(tx))
        assertTrue(salesCsv.contains("\"'=HYPERLINK(\"\"http://bad\"\")\""))
        assertTrue(salesCsv.contains("89.29,10.71,100.00,COMPLETED"))

        val prod = Product(
            id = 7,
            name = "+Promo Bundle",
            categoryId = 1,
            price = 55.0,
            cost = 40.0,
            stockCount = 10,
            barcode = "480001"
        )
        val invCsv = PdfExportUtil.buildInventoryValuationCsv(listOf(prod))
        assertTrue(invCsv.contains("\"'+Promo Bundle\""))
        assertTrue(invCsv.contains("400.00,550.00,150.00"))
    }

    // =========================================================================
    // 4. Room Schema Completeness Verification
    // =========================================================================

    @Test
    fun appDatabase_isPinnedToVersion20WithAll25Entities() {
        val candidates = listOf(
            File("schemas/com.munzo.storepoint.data.AppDatabase/20.json"),
            File("app/schemas/com.munzo.storepoint.data.AppDatabase/20.json")
        )
        val schemaFile = candidates.firstOrNull { it.exists() }
        assertTrue("Expected Room schema 20.json to exist", schemaFile != null)

        val root = JSONObject(schemaFile!!.readText(Charsets.UTF_8))
        val database = root.getJSONObject("database")
        assertEquals(20, database.getInt("version"))

        val entities = database.getJSONArray("entities")
        assertEquals(25, entities.length())

        val tableNames = mutableSetOf<String>()
        for (i in 0 until entities.length()) {
            tableNames.add(entities.getJSONObject(i).getString("tableName"))
        }
        val expectedTables = setOf(
            "store_config",
            "user_accounts",
            "categories",
            "products",
            "cashier_sessions",
            "transactions",
            "transaction_items",
            "parked_transactions",
            "parked_transaction_items",
            "drawer_transactions",
            "payment_schedules",
            "return_transactions",
            "return_items",
            "product_variants",
            "suppliers",
            "purchase_orders",
            "purchase_order_items",
            "wallet_ledger",
            "shift_reconciliations",
            "bill_of_materials",
            "production_runs",
            "inventory_movements",
            "customer_accounts",
            "customer_credit_ledger",
            "pending_print_jobs"
        )
        assertEquals(expectedTables, tableNames)
    }
}
