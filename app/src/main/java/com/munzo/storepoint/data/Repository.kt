package com.munzo.storepoint.data

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Facade repository unifying StorePoint's domain-specific data delegates while preserving
 * full backward compatibility across ViewModels, coordinators, and unit tests.
 */
class StorePointRepository(private val db: AppDatabase) {

    /** Canonical wallet identifiers, so the DAO index, the migration seed and the UI cannot drift. */
    object WalletType {
        const val GCASH = "GCASH"
        const val SMART = "SMART"
        const val GLOBE = "GLOBE"
    }

    object WalletEntryType {
        const val RELOAD = "RELOAD"
        const val CONSUMED = "CONSUMED"
        const val INFLOW = "INFLOW"
        const val OUTFLOW = "OUTFLOW"
        const val RECONCILE = "RECONCILE"
        const val OPENING_BALANCE = "OPENING_BALANCE"
    }

    data class PurchaseOrderReceiveLine(
        val productId: Int,
        val quantityReceived: Int,
        val newUnitCost: Double
    )

    companion object {
        fun roundMoney(amount: Double): Double {
            if (amount.isNaN() || amount.isInfinite()) return 0.0
            return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).toDouble()
        }

        fun canonicalWalletType(label: String): String = when {
            label.contains("gCash", ignoreCase = true) -> WalletType.GCASH
            label.contains("smart", ignoreCase = true) ||
                label.contains("tnt", ignoreCase = true) ||
                label.contains("dito", ignoreCase = true) -> WalletType.SMART
            label.contains("globe", ignoreCase = true) ||
                label.contains("tm", ignoreCase = true) -> WalletType.GLOBE
            else -> WalletType.GCASH
        }

        fun calculateVatFromInclusive(amount: Double, vatPercent: Double = 12.0): Double {
            if (amount <= 0.0 || vatPercent <= 0.0 || amount.isNaN() || amount.isInfinite()) return 0.0
            val rateFraction = vatPercent / 100.0
            val vat = (amount / (1.0 + rateFraction)) * rateFraction
            return roundMoney(vat)
        }

        fun calculateVatableSalesFromInclusive(amount: Double, vatPercent: Double = 12.0): Double {
            if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) return 0.0
            val vat = calculateVatFromInclusive(amount, vatPercent)
            return roundMoney(amount - vat)
        }
    }

    private val catalog = CatalogInventoryRepository(db)
    private val sales = SalesCheckoutRepository(db)
    private val purchasing = PurchasingProductionRepository(db)
    private val shifts = ShiftUserConfigRepository(db)
    private val offlineExt = OfflineExtensionRepository(db)

    // --- Store Config ---
    val storeConfig: Flow<StoreConfig?> = shifts.storeConfig
    suspend fun getStoreConfigSync(): StoreConfig? = shifts.getStoreConfigSync()
    suspend fun saveStoreConfig(config: StoreConfig) = shifts.saveStoreConfig(config)

    // --- User Accounts ---
    val allUsers: Flow<List<User>> = shifts.allUsers
    val usersPendingPinReset: Flow<List<User>> = shifts.usersPendingPinReset
    val biometricEnrolledUsers: Flow<List<User>> = shifts.biometricEnrolledUsers
    suspend fun getAllUsersSync(): List<User> = shifts.getAllUsersSync()
    suspend fun getUserByUsername(username: String): User? = shifts.getUserByUsername(username)
    suspend fun getUserByBarcode(barcode: String): User? = shifts.getUserByBarcode(barcode)
    suspend fun saveUser(user: User) = shifts.saveUser(user)
    suspend fun deleteUser(username: String) = shifts.deleteUser(username)
    suspend fun setBiometricEnrolled(username: String, enrolled: Boolean) =
        shifts.setBiometricEnrolled(username, enrolled)
    suspend fun updatePinAfterReset(username: String, newPinHash: String) =
        shifts.updatePinAfterReset(username, newPinHash)

    // --- Categories ---
    val allCategories: Flow<List<Category>> = catalog.allCategories
    suspend fun saveCategory(category: Category) = catalog.saveCategory(category)
    suspend fun deleteCategory(id: Int, softDelete: Boolean = true) = catalog.deleteCategory(id, softDelete)
    suspend fun unarchiveCategory(id: Int) = catalog.unarchiveCategory(id)

    // --- Products ---
    val allProducts: Flow<List<Product>> = catalog.allProducts
    val archivedProducts: Flow<List<Product>> = catalog.archivedProducts
    fun getProductById(id: Int): Flow<Product?> = catalog.getProductById(id)
    suspend fun getProductByBarcode(barcode: String): Product? = catalog.getProductByBarcode(barcode)
    suspend fun saveProduct(product: Product) = catalog.saveProduct(product)
    suspend fun deleteProduct(id: Int, softDelete: Boolean = true) = catalog.deleteProduct(id, softDelete)
    suspend fun unarchiveProduct(id: Int) = catalog.unarchiveProduct(id)
    suspend fun updateStock(productId: Int, newStock: Int) = catalog.updateStock(productId, newStock)
    suspend fun updateStockAndCost(productId: Int, newStock: Int, newCost: Double) =
        catalog.updateStockAndCost(productId, newStock, newCost)
    suspend fun restockProductAtomic(
        productId: Int,
        productName: String,
        baseUnitsAdded: Int,
        newUnitCost: Double?,
        actorUsername: String,
        reference: String,
        notes: String,
        drawerPayout: DrawerTransaction? = null
    ): Int = catalog.restockProductAtomic(
        productId = productId,
        productName = productName,
        baseUnitsAdded = baseUnitsAdded,
        newUnitCost = newUnitCost,
        actorUsername = actorUsername,
        reference = reference,
        notes = notes,
        drawerPayout = drawerPayout
    )
    suspend fun updateQuickSellPinned(productId: Int, pinned: Boolean) =
        catalog.updateQuickSellPinned(productId, pinned)

    // --- Cashier Sessions ---
    val activeSession: Flow<CashierSession?> = shifts.activeSession
    val allCashierSessions: Flow<List<CashierSession>> = shifts.allCashierSessions
    suspend fun getActiveSessionSync(): CashierSession? = shifts.getActiveSessionSync()
    suspend fun startSession(username: String, startingCash: Double): Boolean =
        shifts.startSession(username, startingCash)
    suspend fun endActiveSession(endingCash: Double) = shifts.endActiveSession(endingCash)

    // --- Transactions & Checkout ---
    val allTransactions: Flow<List<Transaction>> = sales.allTransactions
    val allTransactionItems: Flow<List<TransactionItem>> = sales.allTransactionItems
    fun getTransactionsByCashier(cashier: String): Flow<List<Transaction>> =
        sales.getTransactionsByCashier(cashier)
    fun getTransactionItems(transactionId: Int): Flow<List<TransactionItem>> =
        sales.getTransactionItems(transactionId)
    suspend fun updateTransactionStatus(txId: Int, status: String) =
        sales.updateTransactionStatus(txId, status)
    suspend fun getTransactionById(txId: Int): Transaction? = sales.getTransactionById(txId)
    suspend fun getTransactionItemsSync(txId: Int): List<TransactionItem> =
        sales.getTransactionItemsSync(txId)

    suspend fun checkout(
        cashierUsername: String,
        cartItems: List<CartItemDetails>,
        paymentMethod: String,
        cashPaid: Double,
        subtotal: Double,
        taxAmount: Double,
        totalAmount: Double,
        gcashDelta: Double = 0.0,
        smartLoadDelta: Double = 0.0,
        globeLoadDelta: Double = 0.0,
        customerName: String = "",
        gcashNotes: String? = null,
        smartLoadNotes: String? = null,
        globeLoadNotes: String? = null,
        customerId: Int? = null
    ): Transaction = sales.checkout(
        cashierUsername = cashierUsername,
        cartItems = cartItems,
        paymentMethod = paymentMethod,
        cashPaid = cashPaid,
        subtotal = subtotal,
        taxAmount = taxAmount,
        totalAmount = totalAmount,
        gcashDelta = gcashDelta,
        smartLoadDelta = smartLoadDelta,
        globeLoadDelta = globeLoadDelta,
        customerName = customerName,
        gcashNotes = gcashNotes,
        smartLoadNotes = smartLoadNotes,
        globeLoadNotes = globeLoadNotes,
        customerId = customerId
    )

    // --- Drawer Transactions ---
    val allDrawerTransactions: Flow<List<DrawerTransaction>> = shifts.allDrawerTransactions
    fun getDrawerTransactionsBySession(sessionId: Int): Flow<List<DrawerTransaction>> =
        shifts.getDrawerTransactionsBySession(sessionId)
    suspend fun getDrawerTransactionsBySessionSync(sessionId: Int): List<DrawerTransaction> =
        shifts.getDrawerTransactionsBySessionSync(sessionId)
    suspend fun logDrawerTransaction(tx: DrawerTransaction): Long = shifts.logDrawerTransaction(tx)

    // --- Wallet Ledger ---
    val allWalletLedger: Flow<List<WalletLedgerEntry>> = shifts.allWalletLedger
    fun getRecentWalletEntries(walletType: String, limit: Int = 50): Flow<List<WalletLedgerEntry>> =
        shifts.getRecentWalletEntries(walletType, limit)
    suspend fun getRecentWalletEntriesSync(walletType: String, limit: Int = 50): List<WalletLedgerEntry> =
        shifts.getRecentWalletEntriesSync(walletType, limit)
    suspend fun getSystemDerivedBalanceSync(walletType: String): Double =
        shifts.getSystemDerivedBalanceSync(walletType)
    suspend fun getLiveBalanceSync(walletType: String): Double = shifts.getLiveBalanceSync(walletType)
    suspend fun applyWalletMovement(
        walletType: String,
        type: String,
        delta: Double,
        actorUsername: String,
        reference: String = "",
        notes: String = ""
    ): Double = shifts.applyWalletMovement(walletType, type, delta, actorUsername, reference, notes)
    suspend fun reloadWallet(
        walletType: String,
        amount: Double,
        actorUsername: String,
        reference: String = "",
        notes: String = ""
    ): Double? = shifts.reloadWallet(walletType, amount, actorUsername, reference, notes)
    suspend fun recordWalletConsumption(
        walletType: String,
        amount: Double,
        actorUsername: String,
        reference: String = ""
    ): Double = shifts.recordWalletConsumption(walletType, amount, actorUsername, reference)
    suspend fun reconcileWallet(
        walletType: String,
        countedBalance: Double,
        actorUsername: String,
        notes: String = ""
    ): Double? = shifts.reconcileWallet(walletType, countedBalance, actorUsername, notes)

    // --- Parked Transactions ---
    val allParkedTransactions: Flow<List<ParkedTransaction>> = sales.allParkedTransactions
    suspend fun getParkedItems(parkedId: Int): List<ParkedTransactionItem> = sales.getParkedItems(parkedId)
    suspend fun parkTransaction(note: String, items: List<Pair<Product, Int>>) =
        sales.parkTransaction(note, items)
    suspend fun parkTransactionWithDetails(note: String, items: List<CartItemDetails>) =
        sales.parkTransactionWithDetails(note, items)
    suspend fun deleteParkedTransaction(parkedId: Int) = sales.deleteParkedTransaction(parkedId)

    // --- Prepopulation ---
    suspend fun prePopulateData() = shifts.prePopulateData()

    // --- Payment Schedules ---
    val allPaymentSchedules: Flow<List<PaymentSchedule>> = purchasing.allPaymentSchedules
    suspend fun savePaymentSchedule(schedule: PaymentSchedule) = purchasing.savePaymentSchedule(schedule)
    suspend fun updatePaymentSchedule(schedule: PaymentSchedule) = purchasing.updatePaymentSchedule(schedule)
    suspend fun deletePaymentSchedule(schedule: PaymentSchedule) = purchasing.deletePaymentSchedule(schedule)
    suspend fun deletePaymentScheduleById(id: Int) = purchasing.deletePaymentScheduleById(id)

    // --- Product Variants ---
    fun getVariantsForProduct(productId: Int): Flow<List<ProductVariant>> =
        catalog.getVariantsForProduct(productId)
    suspend fun getVariantsForProductSync(productId: Int): List<ProductVariant> =
        catalog.getVariantsForProductSync(productId)
    val allProductVariants: Flow<List<ProductVariant>> = catalog.allProductVariants
    suspend fun getVariantByBarcode(barcode: String): ProductVariant? = catalog.getVariantByBarcode(barcode)
    suspend fun saveProductVariant(variant: ProductVariant): Long = catalog.saveProductVariant(variant)
    suspend fun updateVariantStock(id: Int, newStock: Int) = catalog.updateVariantStock(id, newStock)
    suspend fun deleteProductVariant(id: Int) = catalog.deleteProductVariant(id)
    suspend fun deleteVariantsForProduct(productId: Int) = catalog.deleteVariantsForProduct(productId)

    // --- Returns & Refunds ---
    val allReturns: Flow<List<ReturnTransaction>> = sales.allReturns
    suspend fun getAllReturnsSync(): List<ReturnTransaction> = sales.getAllReturnsSync()
    fun getReturnsForTransaction(transactionId: Int): Flow<List<ReturnTransaction>> =
        sales.getReturnsForTransaction(transactionId)
    fun getReturnItems(returnTxId: Int): Flow<List<ReturnItem>> = sales.getReturnItems(returnTxId)
    suspend fun getReturnItemsSync(returnTxId: Int): List<ReturnItem> = sales.getReturnItemsSync(returnTxId)
    suspend fun insertReturnTransaction(returnTx: ReturnTransaction): Long =
        sales.insertReturnTransaction(returnTx)
    suspend fun insertReturnItem(item: ReturnItem) = sales.insertReturnItem(item)
    suspend fun processReturnAtomic(
        returnTx: ReturnTransaction,
        items: List<ReturnItem>,
        cashRefund: Boolean,
        sessionId: Int?,
        newOriginalStatus: String
    ): ReturnTransaction = sales.processReturnAtomic(returnTx, items, cashRefund, sessionId, newOriginalStatus)

    // --- Suppliers & Purchase Orders ---
    val allSuppliers: Flow<List<Supplier>> = purchasing.allSuppliers
    suspend fun getAllSuppliersSync(): List<Supplier> = purchasing.getAllSuppliersSync()
    suspend fun getSupplierById(id: Int): Supplier? = purchasing.getSupplierById(id)
    suspend fun saveSupplier(supplier: Supplier): Long = purchasing.saveSupplier(supplier)
    suspend fun updateSupplier(supplier: Supplier) = purchasing.updateSupplier(supplier)
    suspend fun deleteSupplier(id: Int) = purchasing.deleteSupplier(id)

    val allPurchaseOrders: Flow<List<PurchaseOrder>> = purchasing.allPurchaseOrders
    suspend fun getAllPurchaseOrdersSync(): List<PurchaseOrder> = purchasing.getAllPurchaseOrdersSync()
    suspend fun getPurchaseOrderById(id: Int): PurchaseOrder? = purchasing.getPurchaseOrderById(id)
    fun getPurchaseOrderItems(poId: Int): Flow<List<PurchaseOrderItem>> =
        purchasing.getPurchaseOrderItems(poId)
    suspend fun getPurchaseOrderItemsSync(poId: Int): List<PurchaseOrderItem> =
        purchasing.getPurchaseOrderItemsSync(poId)
    suspend fun savePurchaseOrder(po: PurchaseOrder): Long = purchasing.savePurchaseOrder(po)
    suspend fun updatePurchaseOrder(po: PurchaseOrder) = purchasing.updatePurchaseOrder(po)
    suspend fun savePurchaseOrderItem(item: PurchaseOrderItem) = purchasing.savePurchaseOrderItem(item)
    suspend fun markPurchaseOrderReceived(
        id: Int,
        status: String,
        receivedDate: Long,
        paymentStatus: String
    ) = purchasing.markPurchaseOrderReceived(id, status, receivedDate, paymentStatus)
    suspend fun updatePurchaseOrderStatus(id: Int, status: String) =
        purchasing.updatePurchaseOrderStatus(id, status)
    suspend fun deletePurchaseOrder(id: Int) = purchasing.deletePurchaseOrder(id)
    suspend fun createPurchaseOrderAtomic(
        po: PurchaseOrder,
        items: List<PurchaseOrderItem>
    ): PurchaseOrder = purchasing.createPurchaseOrderAtomic(po, items)
    suspend fun receivePurchaseOrderAtomic(
        poId: Int,
        receivedItems: List<PurchaseOrderReceiveLine>,
        receivedDate: Long,
        paymentStatus: String
    ) = purchasing.receivePurchaseOrderAtomic(poId, receivedItems, receivedDate, paymentStatus)

    // --- Relational Queries ---
    val allCategoriesWithProducts: Flow<List<CategoryWithProducts>> = catalog.allCategoriesWithProducts
    val allProductsWithVariants: Flow<List<ProductWithVariants>> = catalog.allProductsWithVariants
    fun getProductWithVariants(id: Int): Flow<ProductWithVariants?> = catalog.getProductWithVariants(id)
    val allSessionsWithDrawers: Flow<List<CashierSessionWithDrawers>> = shifts.allSessionsWithDrawers
    suspend fun getSessionWithDrawers(id: Int): CashierSessionWithDrawers? = shifts.getSessionWithDrawers(id)
    val allTransactionsWithItems: Flow<List<TransactionWithItems>> = sales.allTransactionsWithItems
    suspend fun getTransactionWithItems(id: Int): TransactionWithItems? = sales.getTransactionWithItems(id)
    val allParkedWithItems: Flow<List<ParkedTransactionWithItems>> = sales.allParkedWithItems
    val allReturnsWithItems: Flow<List<ReturnTransactionWithItems>> = sales.allReturnsWithItems
    suspend fun getReturnWithItems(id: Int): ReturnTransactionWithItems? = sales.getReturnWithItems(id)
    val allSuppliersWithOrders: Flow<List<SupplierWithPurchaseOrders>> = purchasing.allSuppliersWithOrders
    val allPurchaseOrdersWithItems: Flow<List<PurchaseOrderWithItems>> = purchasing.allPurchaseOrdersWithItems
    suspend fun getPurchaseOrderWithItems(id: Int): PurchaseOrderWithItems? =
        purchasing.getPurchaseOrderWithItems(id)

    // --- Shift Reconciliation ---
    val allShiftReconciliations: Flow<List<ShiftReconciliation>> = shifts.allShiftReconciliations
    suspend fun getShiftReconciliation(sessionId: Int): ShiftReconciliation? =
        shifts.getShiftReconciliation(sessionId)
    suspend fun reconcileAndCloseShift(
        reconciliation: ShiftReconciliation,
        endingCash: Double,
        endingGCash: Double? = null,
        endingSmartLoad: Double? = null,
        endingGlobeLoad: Double? = null
    ) = shifts.reconcileAndCloseShift(reconciliation, endingCash, endingGCash, endingSmartLoad, endingGlobeLoad)

    // --- Production & BOM ---
    val allProductionRuns: Flow<List<ProductionLogEntity>> = purchasing.allProductionRuns
    suspend fun getAllProductionRunsSync(): List<ProductionLogEntity> = purchasing.getAllProductionRunsSync()
    val allBOMs: Flow<List<BillOfMaterials>> = purchasing.allBOMs
    suspend fun getBOMsForRawProduct(rawId: Int): List<BillOfMaterials> = purchasing.getBOMsForRawProduct(rawId)
    suspend fun saveBOM(bom: BillOfMaterials): Long = purchasing.saveBOM(bom)
    suspend fun deleteBOM(id: Int) = purchasing.deleteBOM(id)

    // --- Inventory Movements ---
    val allInventoryMovements: Flow<List<InventoryMovementEntity>> = catalog.allInventoryMovements
    suspend fun logInventoryMovement(movement: InventoryMovementEntity): Long =
        catalog.logInventoryMovement(movement)
    suspend fun getInventoryMovementsForProduct(productId: Int): List<InventoryMovementEntity> =
        catalog.getInventoryMovementsForProduct(productId)
    suspend fun executeProductionRunAtomic(
        productionRun: ProductionLogEntity,
        saveAsBom: Boolean = false
    ): ProductionLogEntity = purchasing.executeProductionRunAtomic(productionRun, saveAsBom)

    // --- Suki Tab (Offline Customer Credit / Utang) ---
    val allActiveCustomers: Flow<List<CustomerAccount>> = offlineExt.allActiveCustomers
    val allCustomers: Flow<List<CustomerAccount>> = offlineExt.allCustomers
    suspend fun getAllCustomersSync(): List<CustomerAccount> = offlineExt.getAllCustomersSync()
    suspend fun getCustomerByIdSync(id: Int): CustomerAccount? = offlineExt.getCustomerByIdSync(id)
    fun getLedgerForCustomer(customerId: Int): Flow<List<CustomerCreditEntry>> =
        offlineExt.getLedgerForCustomer(customerId)
    suspend fun getLedgerForCustomerSync(customerId: Int): List<CustomerCreditEntry> =
        offlineExt.getLedgerForCustomerSync(customerId)
    suspend fun saveCustomerAccount(account: CustomerAccount): Long =
        offlineExt.saveCustomerAccount(account)
    suspend fun archiveCustomerAccount(id: Int) = offlineExt.archiveCustomerAccount(id)
    suspend fun deleteCustomerAccount(id: Int) = offlineExt.deleteCustomerAccount(id)
    suspend fun recordCustomerPayment(
        customerId: Int,
        amount: Double,
        paymentMethod: String,
        actorUsername: String,
        notes: String = ""
    ): CustomerCreditEntry = offlineExt.recordCustomerPayment(
        customerId = customerId,
        amount = amount,
        paymentMethod = paymentMethod,
        actorUsername = actorUsername,
        notes = notes
    )

    // --- Offline Thermal Print Queue ---
    val allPendingPrintJobs: Flow<List<PendingPrintJob>> = offlineExt.allPendingPrintJobs
    suspend fun getAllPendingPrintJobsSync(): List<PendingPrintJob> = offlineExt.getAllPendingPrintJobsSync()
    suspend fun enqueuePrintJob(
        jobType: String,
        label: String,
        payloadBase64: String,
        error: String
    ): Long = offlineExt.enqueuePrintJob(jobType, label, payloadBase64, error)
    suspend fun markPrintJobRetryFailed(jobId: Int, error: String) =
        offlineExt.markPrintJobRetryFailed(jobId, error)
    suspend fun deletePrintJob(jobId: Int) = offlineExt.deletePrintJob(jobId)
    suspend fun clearAllPrintJobs() = offlineExt.clearAllPrintJobs()

    // --- Mid-Shift Live X-Reading ---
    suspend fun computeActiveShiftXReading(): ShiftXReadingSummary? =
        offlineExt.computeActiveShiftXReading()

    // --- Offline CSV Bulk Product Upsert ---
    suspend fun applyCatalogCsvImport(rows: List<com.munzo.storepoint.util.CatalogCsvRow>): CsvCatalogImportResult =
        offlineExt.applyCatalogCsvImport(rows)
}
