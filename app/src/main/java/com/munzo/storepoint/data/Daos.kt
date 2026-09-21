package com.munzo.storepoint.data

import androidx.room.*
import androidx.room.Transaction as RoomTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreConfigDao {
    @Query("SELECT * FROM store_config WHERE id = 1")
    fun getStoreConfig(): Flow<StoreConfig?>

    @Query("SELECT * FROM store_config WHERE id = 1")
    suspend fun getStoreConfigSync(): StoreConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStoreConfig(config: StoreConfig)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM user_accounts")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM user_accounts")
    suspend fun getAllUsersSync(): List<User>

    @Query("SELECT * FROM user_accounts WHERE username = :username")
    suspend fun getUserSync(username: String): User?

    @Query("SELECT * FROM user_accounts WHERE barcodeId = :barcodeId")
    suspend fun getUserByBarcodeSync(barcodeId: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Query("DELETE FROM user_accounts WHERE username = :username")
    suspend fun deleteUser(username: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    suspend fun getAllCategoriesSync(): List<Category>

    @RoomTransaction
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getCategoriesWithProducts(): Flow<List<CategoryWithProducts>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Int)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsSync(): List<Product>

    @Query("SELECT * FROM products WHERE id = :id")
    fun getProductById(id: Int): Flow<Product?>

    @RoomTransaction
    @Query("SELECT * FROM products WHERE id = :id")
    fun getProductWithVariants(id: Int): Flow<ProductWithVariants?>

    @RoomTransaction
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProductsWithVariants(): Flow<List<ProductWithVariants>>

    @Query("SELECT * FROM products WHERE barcode = :barcode")
    suspend fun getProductByBarcode(barcode: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: Int)

    @Query("UPDATE products SET stockCount = :newStock WHERE id = :productId")
    suspend fun updateStock(productId: Int, newStock: Int)

    @Query("UPDATE products SET stockCount = :newStock, cost = :newCost WHERE id = :productId")
    suspend fun updateStockAndCost(productId: Int, newStock: Int, newCost: Double)
}

@Dao
interface CashierSessionDao {
    @Query("SELECT * FROM cashier_sessions WHERE status = 'ACTIVE' LIMIT 1")
    fun getActiveSession(): Flow<CashierSession?>

    @Query("SELECT * FROM cashier_sessions WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveSessionSync(): CashierSession?

    @Query("SELECT * FROM cashier_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<CashierSession>>

    @RoomTransaction
    @Query("SELECT * FROM cashier_sessions WHERE id = :id")
    suspend fun getSessionWithDrawers(id: Int): CashierSessionWithDrawers?

    @RoomTransaction
    @Query("SELECT * FROM cashier_sessions ORDER BY startTime DESC")
    fun getAllSessionsWithDrawers(): Flow<List<CashierSessionWithDrawers>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: CashierSession)

    @Update
    suspend fun updateSession(session: CashierSession)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Int): Transaction?

    @RoomTransaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionWithItems(id: Int): TransactionWithItems?

    @RoomTransaction
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsWithItems(): Flow<List<TransactionWithItems>>

    @Query("SELECT * FROM transactions WHERE cashierUsername = :cashier ORDER BY timestamp DESC")
    fun getTransactionsByCashier(cashier: String): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Query("UPDATE transactions SET status = :status WHERE id = :id")
    suspend fun updateTransactionStatus(id: Int, status: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionItem(item: TransactionItem)

    @Query("SELECT * FROM transaction_items WHERE transactionId = :transactionId")
    fun getTransactionItems(transactionId: Int): Flow<List<TransactionItem>>

    @Query("SELECT * FROM transaction_items WHERE transactionId = :transactionId")
    suspend fun getTransactionItemsSync(transactionId: Int): List<TransactionItem>

    @Query("SELECT * FROM transaction_items")
    fun getAllTransactionItems(): Flow<List<TransactionItem>>
}

@Dao
interface ParkedTransactionDao {
    @Query("SELECT * FROM parked_transactions ORDER BY timestamp DESC")
    fun getAllParkedTransactions(): Flow<List<ParkedTransaction>>

    @RoomTransaction
    @Query("SELECT * FROM parked_transactions ORDER BY timestamp DESC")
    fun getAllParkedWithItems(): Flow<List<ParkedTransactionWithItems>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParkedTransaction(parked: ParkedTransaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParkedItem(item: ParkedTransactionItem)

    @Query("SELECT * FROM parked_transaction_items WHERE parkedTransactionId = :parkedId")
    suspend fun getParkedItems(parkedId: Int): List<ParkedTransactionItem>

    @Query("DELETE FROM parked_transactions WHERE id = :parkedId")
    suspend fun deleteParked(parkedId: Int)

    @Query("DELETE FROM parked_transaction_items WHERE parkedTransactionId = :parkedId")
    suspend fun deleteParkedItems(parkedId: Int)
}

@Dao
interface DrawerTransactionDao {
    @Query("SELECT * FROM drawer_transactions ORDER BY timestamp DESC")
    fun getAllDrawerTransactions(): Flow<List<DrawerTransaction>>

    @Query("SELECT * FROM drawer_transactions WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getDrawerTransactionsBySession(sessionId: Int): Flow<List<DrawerTransaction>>

    @Query("SELECT * FROM drawer_transactions WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getDrawerTransactionsBySessionSync(sessionId: Int): List<DrawerTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrawerTransaction(tx: DrawerTransaction): Long
}

@Dao
interface PaymentScheduleDao {
    @Query("SELECT * FROM payment_schedules ORDER BY dueDate ASC")
    fun getAllPaymentSchedules(): Flow<List<PaymentSchedule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentSchedule(schedule: PaymentSchedule)

    @Update
    suspend fun updatePaymentSchedule(schedule: PaymentSchedule)

    @Delete
    suspend fun deletePaymentSchedule(schedule: PaymentSchedule)

    @Query("DELETE FROM payment_schedules WHERE id = :id")
    suspend fun deletePaymentScheduleById(id: Int)
}

@Dao
interface ProductVariantDao {
    @Query("SELECT * FROM product_variants WHERE productId = :productId ORDER BY price ASC")
    fun getVariantsForProduct(productId: Int): Flow<List<ProductVariant>>

    @Query("SELECT * FROM product_variants WHERE productId = :productId ORDER BY price ASC")
    suspend fun getVariantsForProductSync(productId: Int): List<ProductVariant>

    @Query("SELECT * FROM product_variants")
    fun getAllVariants(): Flow<List<ProductVariant>>

    @Query("SELECT * FROM product_variants")
    suspend fun getAllVariantsSync(): List<ProductVariant>

    @Query("SELECT * FROM product_variants WHERE barcode = :barcode LIMIT 1")
    suspend fun getVariantByBarcode(barcode: String): ProductVariant?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: ProductVariant): Long

    @Update
    suspend fun updateVariant(variant: ProductVariant)

    @Query("DELETE FROM product_variants WHERE id = :id")
    suspend fun deleteVariant(id: Int)

    @Query("DELETE FROM product_variants WHERE productId = :productId")
    suspend fun deleteVariantsForProduct(productId: Int)
}

@Dao
interface ReturnDao {
    @Query("SELECT * FROM return_transactions ORDER BY timestamp DESC")
    fun getAllReturns(): Flow<List<ReturnTransaction>>

    @Query("SELECT * FROM return_transactions ORDER BY timestamp DESC")
    suspend fun getAllReturnsSync(): List<ReturnTransaction>

    @RoomTransaction
    @Query("SELECT * FROM return_transactions WHERE id = :id")
    suspend fun getReturnWithItems(id: Int): ReturnTransactionWithItems?

    @RoomTransaction
    @Query("SELECT * FROM return_transactions ORDER BY timestamp DESC")
    fun getAllReturnsWithItems(): Flow<List<ReturnTransactionWithItems>>

    @Query("SELECT * FROM return_transactions WHERE originalTransactionId = :originalTxId ORDER BY timestamp DESC")
    fun getReturnsForTransaction(originalTxId: Int): Flow<List<ReturnTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReturnTransaction(returnTx: ReturnTransaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReturnItem(item: ReturnItem)

    @Query("SELECT * FROM return_items WHERE returnTransactionId = :returnTxId")
    fun getReturnItems(returnTxId: Int): Flow<List<ReturnItem>>

    @Query("SELECT * FROM return_items WHERE returnTransactionId = :returnTxId")
    suspend fun getReturnItemsSync(returnTxId: Int): List<ReturnItem>

    @Query("SELECT * FROM return_items")
    suspend fun getAllReturnItemsSync(): List<ReturnItem>
}

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    suspend fun getAllSuppliersSync(): List<Supplier>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getSupplierById(id: Int): Supplier?

    @RoomTransaction
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliersWithOrders(): Flow<List<SupplierWithPurchaseOrders>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: Supplier): Long

    @Update
    suspend fun updateSupplier(supplier: Supplier)

    @Query("DELETE FROM suppliers WHERE id = :id")
    suspend fun deleteSupplier(id: Int)
}

@Dao
interface PurchaseOrderDao {
    @Query("SELECT * FROM purchase_orders ORDER BY orderDate DESC")
    fun getAllPurchaseOrders(): Flow<List<PurchaseOrder>>

    @Query("SELECT * FROM purchase_orders ORDER BY orderDate DESC")
    suspend fun getAllPurchaseOrdersSync(): List<PurchaseOrder>

    @Query("SELECT * FROM purchase_orders WHERE id = :id")
    suspend fun getPurchaseOrderById(id: Int): PurchaseOrder?

    @RoomTransaction
    @Query("SELECT * FROM purchase_orders WHERE id = :id")
    suspend fun getPurchaseOrderWithItems(id: Int): PurchaseOrderWithItems?

    @RoomTransaction
    @Query("SELECT * FROM purchase_orders ORDER BY orderDate DESC")
    fun getAllPurchaseOrdersWithItems(): Flow<List<PurchaseOrderWithItems>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseOrder(po: PurchaseOrder): Long

    @Update
    suspend fun updatePurchaseOrder(po: PurchaseOrder)

    @Query("UPDATE purchase_orders SET status = :status, receivedDate = :receivedDate, paymentStatus = :paymentStatus WHERE id = :id")
    suspend fun markPurchaseOrderReceived(id: Int, status: String, receivedDate: Long, paymentStatus: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseOrderItem(item: PurchaseOrderItem)

    @Query("SELECT * FROM purchase_order_items WHERE purchaseOrderId = :poId")
    fun getPurchaseOrderItems(poId: Int): Flow<List<PurchaseOrderItem>>

    @Query("SELECT * FROM purchase_order_items WHERE purchaseOrderId = :poId")
    suspend fun getPurchaseOrderItemsSync(poId: Int): List<PurchaseOrderItem>

    @Query("SELECT * FROM purchase_order_items")
    suspend fun getAllPurchaseOrderItemsSync(): List<PurchaseOrderItem>

    @Query("DELETE FROM purchase_orders WHERE id = :id")
    suspend fun deletePurchaseOrder(id: Int)

    @Query("DELETE FROM purchase_order_items WHERE purchaseOrderId = :poId")
    suspend fun deletePurchaseOrderItems(poId: Int)
}
