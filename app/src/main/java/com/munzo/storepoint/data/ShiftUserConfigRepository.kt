package com.munzo.storepoint.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Domain delegate handling store config, users, cashier sessions, drawer transactions,
 * wallet ledger, shift reconciliations, and initial catalog prepopulation.
 */
internal class ShiftUserConfigRepository(private val db: AppDatabase) {

    private val storeConfigDao = db.storeConfigDao
    private val userDao = db.userDao
    private val categoryDao = db.categoryDao
    private val productDao = db.productDao
    private val cashierSessionDao = db.cashierSessionDao
    private val drawerTransactionDao = db.drawerTransactionDao
    private val walletLedgerDao = db.walletLedgerDao
    private val shiftReconciliationDao = db.shiftReconciliationDao

    // --- Store Config ---
    val storeConfig: Flow<StoreConfig?> = storeConfigDao.getStoreConfig()

    suspend fun getStoreConfigSync(): StoreConfig? = storeConfigDao.getStoreConfigSync()

    suspend fun saveStoreConfig(config: StoreConfig) {
        storeConfigDao.insertStoreConfig(config)
    }

    // --- User Accounts ---
    val allUsers: Flow<List<User>> = userDao.getAllUsers()
    val usersPendingPinReset: Flow<List<User>> = userDao.getUsersPendingPinReset()
    val biometricEnrolledUsers: Flow<List<User>> = userDao.getBiometricEnrolledUsers()

    suspend fun getAllUsersSync(): List<User> = userDao.getAllUsersSync()

    suspend fun getUserByUsername(username: String): User? = userDao.getUserSync(username)

    suspend fun getUserByBarcode(barcode: String): User? = userDao.getUserByBarcodeSync(barcode)

    suspend fun saveUser(user: User) {
        userDao.insertUser(user)
    }

    suspend fun deleteUser(username: String) {
        userDao.deleteUser(username)
    }

    suspend fun setBiometricEnrolled(username: String, enrolled: Boolean) {
        userDao.setBiometricEnrolled(username, enrolled)
    }

    suspend fun updatePinAfterReset(username: String, newPinHash: String) {
        userDao.updatePinAfterReset(username, newPinHash)
    }

    // --- Cashier Sessions ---
    val activeSession: Flow<CashierSession?> = cashierSessionDao.getActiveSession()
    val allCashierSessions: Flow<List<CashierSession>> = cashierSessionDao.getAllSessions()
    val allSessionsWithDrawers: Flow<List<CashierSessionWithDrawers>> =
        cashierSessionDao.getAllSessionsWithDrawers()

    suspend fun getActiveSessionSync(): CashierSession? = cashierSessionDao.getActiveSessionSync()

    suspend fun getSessionWithDrawers(id: Int): CashierSessionWithDrawers? =
        cashierSessionDao.getSessionWithDrawers(id)

    suspend fun startSession(username: String, startingCash: Double): Boolean = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val normStartingCash = StorePointRepository.roundMoney(startingCash)
        var started = false
        db.withTransaction {
            db.openHelper.writableDatabase.execSQL(
                """INSERT INTO cashier_sessions (cashierUsername, startTime, startingCash, status)
                   SELECT ?, ?, ?, 'ACTIVE'
                   WHERE NOT EXISTS (SELECT 1 FROM cashier_sessions WHERE status = 'ACTIVE')""",
                arrayOf<Any?>(username, timestamp, normStartingCash)
            )
            started = db.openHelper.writableDatabase.compileStatement("SELECT changes()").simpleQueryForLong() > 0
        }
        started
    }

    suspend fun endActiveSession(endingCash: Double) = withContext(Dispatchers.IO) {
        val active = cashierSessionDao.getActiveSessionSync()
        if (active != null) {
            val normEndingCash = StorePointRepository.roundMoney(endingCash)
            cashierSessionDao.updateSession(
                active.copy(
                    endTime = System.currentTimeMillis(),
                    endingCash = normEndingCash,
                    status = "CLOSED"
                )
            )
        }
    }

    // --- Drawer Transactions ---
    val allDrawerTransactions: Flow<List<DrawerTransaction>> = drawerTransactionDao.getAllDrawerTransactions()

    fun getDrawerTransactionsBySession(sessionId: Int): Flow<List<DrawerTransaction>> =
        drawerTransactionDao.getDrawerTransactionsBySession(sessionId)

    suspend fun getDrawerTransactionsBySessionSync(sessionId: Int): List<DrawerTransaction> =
        drawerTransactionDao.getDrawerTransactionsBySessionSync(sessionId)

    suspend fun logDrawerTransaction(tx: DrawerTransaction): Long =
        drawerTransactionDao.insertDrawerTransaction(tx.copy(amount = StorePointRepository.roundMoney(tx.amount)))

    // --- Wallet Ledger ---
    val allWalletLedger: Flow<List<WalletLedgerEntry>> = walletLedgerDao.getAllWalletLedger()

    fun getRecentWalletEntries(walletType: String, limit: Int = 50): Flow<List<WalletLedgerEntry>> =
        walletLedgerDao.getRecentForWallet(walletType, limit)

    suspend fun getRecentWalletEntriesSync(walletType: String, limit: Int = 50): List<WalletLedgerEntry> =
        walletLedgerDao.getRecentForWalletSync(walletType, limit)

    suspend fun getSystemDerivedBalanceSync(walletType: String): Double =
        StorePointRepository.roundMoney(walletLedgerDao.getNetDeltaSync(walletType))

    suspend fun getLiveBalanceSync(walletType: String): Double {
        val config = getStoreConfigSync() ?: return 0.0
        return StorePointRepository.roundMoney(
            when (walletType) {
                StorePointRepository.WalletType.GCASH -> config.gcashBalance
                StorePointRepository.WalletType.SMART -> config.smartLoadBalance
                StorePointRepository.WalletType.GLOBE -> config.globeLoadBalance
                else -> 0.0
            }
        )
    }

    suspend fun applyWalletMovement(
        walletType: String,
        type: String,
        delta: Double,
        actorUsername: String,
        reference: String = "",
        notes: String = ""
    ): Double = withContext(Dispatchers.IO) {
        var computedNext = 0.0
        db.runInTransaction {
            val currentConfig = storeConfigDao.getStoreConfigBlocking() ?: return@runInTransaction
            val current = when (walletType) {
                StorePointRepository.WalletType.GCASH -> currentConfig.gcashBalance
                StorePointRepository.WalletType.SMART -> currentConfig.smartLoadBalance
                StorePointRepository.WalletType.GLOBE -> currentConfig.globeLoadBalance
                else -> currentConfig.gcashBalance
            }
            val next = StorePointRepository.roundMoney(current + delta)
            computedNext = next

            val updated = when (walletType) {
                StorePointRepository.WalletType.GCASH -> currentConfig.copy(gcashBalance = next)
                StorePointRepository.WalletType.SMART -> currentConfig.copy(smartLoadBalance = next)
                StorePointRepository.WalletType.GLOBE -> currentConfig.copy(globeLoadBalance = next)
                else -> currentConfig
            }

            storeConfigDao.insertStoreConfigBlocking(updated)
            walletLedgerDao.insertWalletEntryBlocking(
                WalletLedgerEntry(
                    walletType = walletType,
                    timestamp = System.currentTimeMillis(),
                    actorUsername = actorUsername,
                    type = type,
                    delta = StorePointRepository.roundMoney(delta),
                    balanceAfter = next,
                    reference = reference.trim(),
                    notes = notes.trim()
                )
            )
        }
        computedNext
    }

    suspend fun reloadWallet(
        walletType: String,
        amount: Double,
        actorUsername: String,
        reference: String = "",
        notes: String = ""
    ): Double? {
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) return null
        return applyWalletMovement(
            walletType = walletType,
            type = StorePointRepository.WalletEntryType.RELOAD,
            delta = StorePointRepository.roundMoney(amount),
            actorUsername = actorUsername,
            reference = reference,
            notes = notes
        )
    }

    suspend fun recordWalletConsumption(
        walletType: String,
        amount: Double,
        actorUsername: String,
        reference: String = ""
    ): Double = applyWalletMovement(
        walletType = walletType,
        type = StorePointRepository.WalletEntryType.CONSUMED,
        delta = -StorePointRepository.roundMoney(abs(amount)),
        actorUsername = actorUsername,
        reference = reference,
        notes = "Customer load sale"
    )

    suspend fun reconcileWallet(
        walletType: String,
        countedBalance: Double,
        actorUsername: String,
        notes: String = ""
    ): Double? {
        if (countedBalance.isNaN() || countedBalance.isInfinite() || countedBalance < 0.0) return null
        val live = getLiveBalanceSync(walletType)
        val delta = StorePointRepository.roundMoney(countedBalance - live)
        if (delta == 0.0) return live
        return applyWalletMovement(
            walletType = walletType,
            type = StorePointRepository.WalletEntryType.RECONCILE,
            delta = delta,
            actorUsername = actorUsername,
            notes = notes.ifBlank { "Shift close reconciliation" }
        )
    }

    // --- Shift Reconciliation ---
    val allShiftReconciliations: Flow<List<ShiftReconciliation>> =
        shiftReconciliationDao.getAllReconciliations()

    suspend fun getShiftReconciliation(sessionId: Int): ShiftReconciliation? = withContext(Dispatchers.IO) {
        shiftReconciliationDao.getReconciliationBySessionId(sessionId)
    }

    suspend fun reconcileAndCloseShift(
        reconciliation: ShiftReconciliation,
        endingCash: Double,
        endingGCash: Double? = null,
        endingSmartLoad: Double? = null,
        endingGlobeLoad: Double? = null
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            shiftReconciliationDao.insertReconciliation(reconciliation)

            val normEndingCash = StorePointRepository.roundMoney(endingCash)
            val activeSession = cashierSessionDao.getActiveSessionSync()
            if (activeSession != null) {
                cashierSessionDao.updateSession(
                    activeSession.copy(
                        endTime = reconciliation.timestamp,
                        endingCash = normEndingCash,
                        status = "CLOSED"
                    )
                )
            }

            val currentConfig = storeConfigDao.getStoreConfigSync()
            if (currentConfig != null) {
                var updated = currentConfig

                if (endingGCash != null) {
                    val normGCash = StorePointRepository.roundMoney(endingGCash)
                    val delta = StorePointRepository.roundMoney(normGCash - currentConfig.gcashBalance)
                    if (abs(delta) >= 0.01) {
                        walletLedgerDao.insertWalletEntryBlocking(
                            WalletLedgerEntry(
                                walletType = StorePointRepository.WalletType.GCASH,
                                timestamp = reconciliation.timestamp,
                                actorUsername = reconciliation.cashierUsername,
                                type = StorePointRepository.WalletEntryType.RECONCILE,
                                delta = delta,
                                balanceAfter = normGCash,
                                reference = "Shift-Close-SES-${reconciliation.sessionId}",
                                notes = reconciliation.discrepancyReason.ifBlank { "Shift Handover GCash Reconciliation" }
                            )
                        )
                    }
                    updated = updated.copy(gcashBalance = normGCash)
                }

                if (endingSmartLoad != null) {
                    val normSmart = StorePointRepository.roundMoney(endingSmartLoad)
                    val delta = StorePointRepository.roundMoney(normSmart - currentConfig.smartLoadBalance)
                    if (abs(delta) >= 0.01) {
                        walletLedgerDao.insertWalletEntryBlocking(
                            WalletLedgerEntry(
                                walletType = StorePointRepository.WalletType.SMART,
                                timestamp = reconciliation.timestamp,
                                actorUsername = reconciliation.cashierUsername,
                                type = StorePointRepository.WalletEntryType.RECONCILE,
                                delta = delta,
                                balanceAfter = normSmart,
                                reference = "Shift-Close-SES-${reconciliation.sessionId}",
                                notes = reconciliation.discrepancyReason.ifBlank { "Shift Handover Smart Load Reconciliation" }
                            )
                        )
                    }
                    updated = updated.copy(smartLoadBalance = normSmart)
                }

                if (endingGlobeLoad != null) {
                    val normGlobe = StorePointRepository.roundMoney(endingGlobeLoad)
                    val delta = StorePointRepository.roundMoney(normGlobe - currentConfig.globeLoadBalance)
                    if (abs(delta) >= 0.01) {
                        walletLedgerDao.insertWalletEntryBlocking(
                            WalletLedgerEntry(
                                walletType = StorePointRepository.WalletType.GLOBE,
                                timestamp = reconciliation.timestamp,
                                actorUsername = reconciliation.cashierUsername,
                                type = StorePointRepository.WalletEntryType.RECONCILE,
                                delta = delta,
                                balanceAfter = normGlobe,
                                reference = "Shift-Close-SES-${reconciliation.sessionId}",
                                notes = reconciliation.discrepancyReason.ifBlank { "Shift Handover Globe Load Reconciliation" }
                            )
                        )
                    }
                    updated = updated.copy(globeLoadBalance = normGlobe)
                }

                if (updated != currentConfig) {
                    storeConfigDao.insertStoreConfigBlocking(updated)
                }
            }
        }
    }

    // --- Initial Prepopulation ---
    suspend fun prePopulateData() = withContext(Dispatchers.IO) {
        for (legacy in listOf("admin", "cashier")) {
            val user = userDao.getUserSync(legacy)
            if (user != null && hashSha256(legacy).equals(user.passwordHash, ignoreCase = true)) {
                userDao.deleteUser(legacy)
            }
        }

        if (storeConfigDao.getStoreConfigSync() == null) {
            storeConfigDao.insertStoreConfig(
                StoreConfig(
                    id = 1,
                    storeName = "",
                    currencySymbol = "₱",
                    taxPercentage = 12.0,
                    setupCompleted = false,
                    hasGCash = true,
                    hasMaya = true,
                    hasLoad = true
                )
            )
        }

        val firstCategories = categoryDao.getAllCategoriesSync()
        if (firstCategories.isEmpty()) {
            categoryDao.insertCategory(Category(name = "Groceries"))
            categoryDao.insertCategory(Category(name = "Beverages"))
            categoryDao.insertCategory(Category(name = "Electronics"))
            categoryDao.insertCategory(Category(name = "Apparel"))
            categoryDao.insertCategory(Category(name = "GCash & Load (Digital)"))
        }

        if (productDao.getAllProductsSync().isEmpty()) {
            val categoriesNow = categoryDao.getAllCategoriesSync()
            val groceryCatId = categoriesNow.find { it.name == "Groceries" }?.id ?: 1
            val digitalCatId = categoriesNow.find { it.name == "GCash & Load (Digital)" }?.id ?: 5

            productDao.insertProduct(
                Product(
                    name = "Magnolia Fresh Milk 1L",
                    categoryId = groceryCatId,
                    price = 110.0,
                    stockCount = 20,
                    barcode = "4800012345678",
                    expirationDate = "2026-07-28"
                )
            )
            productDao.insertProduct(
                Product(
                    name = "GCash Cash-In",
                    categoryId = digitalCatId,
                    price = 15.0,
                    stockCount = 9999,
                    barcode = ""
                )
            )
            productDao.insertProduct(
                Product(
                    name = "GCash Cash-Out",
                    categoryId = digitalCatId,
                    price = 20.0,
                    stockCount = 9999,
                    barcode = ""
                )
            )
            productDao.insertProduct(
                Product(
                    name = "Smart Load (Regular 50)",
                    categoryId = digitalCatId,
                    price = 53.0,
                    stockCount = 500,
                    barcode = ""
                )
            )
            productDao.insertProduct(
                Product(
                    name = "Globe Load (Regular 50)",
                    categoryId = digitalCatId,
                    price = 53.0,
                    stockCount = 500,
                    barcode = ""
                )
            )
        }
    }

    private fun hashSha256(str: String): String = MessageDigest.getInstance("SHA-256")
        .digest(str.toByteArray())
        .fold("") { acc, it -> acc + "%02x".format(it) }
}
