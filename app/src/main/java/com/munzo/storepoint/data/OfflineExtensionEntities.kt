package com.munzo.storepoint.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Offline Customer Credit ("Suki Tab" / Utang) account record.
 * Every balance change is accompanied by an auditable [CustomerCreditEntry] row.
 */
@Entity(
    tableName = "customer_accounts",
    indices = [
        Index(value = ["name"]),
        Index(value = ["phone"])
    ]
)
data class CustomerAccount(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val creditLimit: Double = 1000.0,
    val currentBalance: Double = 0.0,
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Double-entry ledger tracking every Suki Tab credit charge, repayment, or admin adjustment.
 * [type]: "CHARGE" (increases balance), "PAYMENT" (reduces balance), "ADJUSTMENT" (signed delta).
 */
@Entity(
    tableName = "customer_credit_ledger",
    foreignKeys = [
        ForeignKey(
            entity = CustomerAccount::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["timestamp"])
    ]
)
data class CustomerCreditEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerId: Int,
    val timestamp: Long,
    val actorUsername: String,
    val type: String, // "CHARGE", "PAYMENT", "ADJUSTMENT"
    val amount: Double, // Positive magnitude; direction defined by type (or signed for ADJUSTMENT)
    val balanceAfter: Double,
    val paymentMethod: String = "SUKI_CREDIT", // "SUKI_CREDIT", "CASH", "GCASH", "MAYA"
    val transactionId: Int? = null,
    val notes: String = ""
)

/**
 * Persistent offline thermal printer queue storing raw ESC/POS bytes (Base64 encoded)
 * whenever a Bluetooth/USB receipt, refund, drawer slip, X-Reading, or Z-Reading fails to print.
 */
@Entity(
    tableName = "pending_print_jobs",
    indices = [
        Index(value = ["createdAt"])
    ]
)
data class PendingPrintJob(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val jobType: String, // "RECEIPT", "REFUND", "DRAWER_SLIP", "X_READING", "Z_READING", "SUKI_PAYMENT"
    val label: String,
    val payloadBase64: String,
    val lastError: String = "",
    val retryCount: Int = 0
)

/**
 * Non-destructive live mid-shift X-Reading summary calculated from the active [CashierSession].
 */
data class ShiftXReadingSummary(
    val sessionId: Int,
    val cashierUsername: String,
    val shiftStartTime: Long,
    val generatedAt: Long,
    val startingCash: Double,
    val transactionCount: Int,
    val grossSales: Double,
    val totalDiscounts: Double,
    val totalTax: Double,
    val cashSales: Double,
    val gcashSales: Double,
    val mayaSales: Double,
    val cardSales: Double,
    val sukiCreditSales: Double,
    val otherDigitalSales: Double,
    val drawerCashIn: Double,
    val drawerPayouts: Double,
    val cashRefunds: Double,
    val expectedCashInDrawer: Double
)

/**
 * Result of an atomic bulk CSV product import operation.
 */
data class CsvCatalogImportResult(
    val insertedCount: Int,
    val updatedCount: Int,
    val createdCategoriesCount: Int
)
