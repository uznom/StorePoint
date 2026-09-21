package com.munzo.storepoint.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "store_config")
data class StoreConfig(
    @PrimaryKey val id: Int = 1,
    val storeName: String,
    val currencySymbol: String,
    val taxPercentage: Double = 12.0,
    val setupCompleted: Boolean = false,
    val gcashBalance: Double = 0.0,
    val hasGCash: Boolean = true,
    val hasMaya: Boolean = true,
    val hasLoad: Boolean = true,
    val smartLoadBalance: Double = 0.0,
    val globeLoadBalance: Double = 0.0,
    val loadServiceFee: Double = 2.0,
    val gcashServiceFee: Double = 10.0,
    val mayaServiceFee: Double = 10.0,
    val mayaBankFee: Double = 15.0
)

@Entity(tableName = "user_accounts")
data class User(
    @PrimaryKey val username: String,
    val pinHash: String,
    val role: String, // "ADMIN", "CASHIER", or "INVENTORY"
    val barcodeId: String = "" // For logging in, exiting Kiosk, or starting sessions via barcode scanning
) {
    val passwordHash: String get() = pinHash
}

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["barcode"])
    ]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val categoryId: Int,
    val price: Double,
    val stockCount: Int,
    val barcode: String,
    val expirationDate: String? = null,
    val cost: Double = 0.0,
    
    // Cigarettes / Special multi-UOM configurations
    val hasStick10s: Boolean = false,
    val priceStick10s: Double = 0.0,
    val hasStick20s: Boolean = false,
    val priceStick20s: Double = 0.0,
    val hasReam: Boolean = false,
    val priceReam: Double = 0.0,
    val hasMasterCase: Boolean = false,
    val priceMasterCase: Double = 0.0,
    
    // Custom non-cigarette UOM
    val hasCustomUom: Boolean = false,
    val customUomName: String = "Pack",
    val customUomMultiplier: Int = 1,
    val customUomPrice: Double = 0.0,

    // Specific barcodes for each multi-UOM option
    val barcode10s: String = "",
    val barcode20s: String = "",
    val barcodeReam: String = "",
    val barcodeMasterCase: String = "",
    val barcodeCustomUom: String = "",
    val baseUom: String = "pc"
)

@Entity(
    tableName = "product_variants",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["barcode"])
    ]
)
data class ProductVariant(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: Int,
    val variantName: String, // e.g. "Swakto 200ml", "1.5L", "1 Sachet", "1 Pack (12s)", "1/4 kg", "1 kg"
    val uomName: String = "pc", // e.g. "pc", "sachet", "pack", "kg", "bottle"
    val multiplier: Int = 1, // Number of base items deducted from inventory
    val price: Double,
    val cost: Double = 0.0,
    val barcode: String = "",
    val isDefault: Boolean = false
)

@Entity(
    tableName = "drawer_transactions",
    foreignKeys = [
        ForeignKey(
            entity = CashierSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"])
    ]
)
data class DrawerTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val timestamp: Long,
    val cashierUsername: String,
    val amount: Double, // Negative for payouts/expenses
    val type: String, // "PAYOUT"
    val reason: String
)

data class CartItemDetails(
    val product: Product,
    val quantity: Int,
    val uomName: String,
    val multiplier: Int,
    val selectedPrice: Double
)

data class UomOption(
    val name: String,
    val multiplier: Int,
    val price: Double
)

@Entity(tableName = "cashier_sessions")
data class CashierSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cashierUsername: String,
    val startTime: Long,
    val endTime: Long? = null,
    val startingCash: Double,
    val endingCash: Double? = null,
    val status: String = "ACTIVE" // "ACTIVE", "CLOSED"
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val cashierUsername: String,
    val subtotal: Double,
    val taxAmount: Double,
    val totalAmount: Double,
    val paymentMethod: String, // "CASH", "CARD"
    val cashPaid: Double,
    val changeAmount: Double,
    val status: String = "COMPLETED", // "COMPLETED", "PARTIALLY_REFUNDED", "REFUNDED"
    val customerName: String = ""
)

@Entity(
    tableName = "transaction_items",
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["productId"])
    ]
)
data class TransactionItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val transactionId: Int,
    val productId: Int,
    val productName: String,
    val price: Double,
    val quantity: Int,
    val cost: Double = 0.0,
    val uomName: String = "pc"
)

@Entity(
    tableName = "return_transactions",
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["originalTransactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["originalTransactionId"])
    ]
)
data class ReturnTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalTransactionId: Int,
    val timestamp: Long,
    val cashierUsername: String,
    val totalRefundAmount: Double,
    val refundMethod: String = "CASH", // "CASH", "STORE_CREDIT", "EXCHANGE"
    val returnReason: String = "Defective / Spoiled", // "Defective / Spoiled", "Expired", "Wrong Item", "Customer Changed Mind"
    val customerName: String = "",
    val notes: String = ""
)

@Entity(
    tableName = "return_items",
    foreignKeys = [
        ForeignKey(
            entity = ReturnTransaction::class,
            parentColumns = ["id"],
            childColumns = ["returnTransactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["returnTransactionId"]),
        Index(value = ["productId"])
    ]
)
data class ReturnItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val returnTransactionId: Int,
    val productId: Int,
    val productName: String,
    val quantity: Int,
    val unitPrice: Double,
    val refundAmount: Double,
    val uomName: String = "pc",
    val restockToInventory: Boolean = true
)

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val contactPerson: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val deliverySchedule: String = "", // e.g. "Tuesdays & Fridays"
    val paymentTerms: String = "Cash on Delivery", // e.g. "COD", "7 Days", "15 Days"
    val notes: String = ""
)

@Entity(
    tableName = "purchase_orders",
    foreignKeys = [
        ForeignKey(
            entity = Supplier::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["supplierId"])
    ]
)
data class PurchaseOrder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val poNumber: String,
    val supplierId: Int,
    val supplierName: String,
    val orderDate: Long,
    val expectedDeliveryDate: Long? = null,
    val receivedDate: Long? = null,
    val totalCost: Double,
    val status: String = "ORDERED", // "ORDERED", "RECEIVED", "CANCELLED"
    val paymentStatus: String = "UNPAID", // "UNPAID", "PAID"
    val notes: String = ""
)

@Entity(
    tableName = "purchase_order_items",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseOrder::class,
            parentColumns = ["id"],
            childColumns = ["purchaseOrderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["purchaseOrderId"]),
        Index(value = ["productId"])
    ]
)
data class PurchaseOrderItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val purchaseOrderId: Int,
    val productId: Int,
    val productName: String,
    val quantityOrdered: Int,
    val quantityReceived: Int = 0,
    val unitCost: Double,
    val totalCost: Double,
    val uomName: String = "pc"
)

@Entity(tableName = "parked_transactions")
data class ParkedTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val note: String
)

@Entity(
    tableName = "parked_transaction_items",
    foreignKeys = [
        ForeignKey(
            entity = ParkedTransaction::class,
            parentColumns = ["id"],
            childColumns = ["parkedTransactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["parkedTransactionId"]),
        Index(value = ["productId"])
    ]
)
data class ParkedTransactionItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val parkedTransactionId: Int,
    val productId: Int,
    val productName: String,
    val price: Double,
    val quantity: Int
)

@Entity(tableName = "payment_schedules")
data class PaymentSchedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amount: Double,
    val dueDate: String, // "YYYY-MM-DD" style
    val category: String, // "Supplier", "Utilities", "Rent", "Salaries", "Taxes", "Replenishment"
    val priority: String, // "High", "Medium", "Low"
    val isPaid: Boolean = false,
    val paymentMethod: String = "CASH" // "CASH" or "GCASH"
)

// =========================================================================
// Room Relational Models (@Embedded + @Relation)
// =========================================================================

data class CategoryWithProducts(
    @Embedded val category: Category,
    @Relation(
        parentColumn = "id",
        entityColumn = "categoryId"
    )
    val products: List<Product>
)

data class ProductWithVariants(
    @Embedded val product: Product,
    @Relation(
        parentColumn = "id",
        entityColumn = "productId"
    )
    val variants: List<ProductVariant>
)

data class TransactionWithItems(
    @Embedded val transaction: Transaction,
    @Relation(
        parentColumn = "id",
        entityColumn = "transactionId"
    )
    val items: List<TransactionItem>
)

data class CashierSessionWithDrawers(
    @Embedded val session: CashierSession,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val drawerTransactions: List<DrawerTransaction>
)

data class ReturnTransactionWithItems(
    @Embedded val returnTransaction: ReturnTransaction,
    @Relation(
        parentColumn = "id",
        entityColumn = "returnTransactionId"
    )
    val items: List<ReturnItem>
)

data class SupplierWithPurchaseOrders(
    @Embedded val supplier: Supplier,
    @Relation(
        parentColumn = "id",
        entityColumn = "supplierId"
    )
    val purchaseOrders: List<PurchaseOrder>
)

data class PurchaseOrderWithItems(
    @Embedded val purchaseOrder: PurchaseOrder,
    @Relation(
        parentColumn = "id",
        entityColumn = "purchaseOrderId"
    )
    val items: List<PurchaseOrderItem>
)

data class ParkedTransactionWithItems(
    @Embedded val parkedTransaction: ParkedTransaction,
    @Relation(
        parentColumn = "id",
        entityColumn = "parkedTransactionId"
    )
    val items: List<ParkedTransactionItem>
)
