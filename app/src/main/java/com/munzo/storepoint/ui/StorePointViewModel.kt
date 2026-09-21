package com.munzo.storepoint.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.munzo.storepoint.data.*
import com.munzo.storepoint.util.APP_VERSION
import com.munzo.storepoint.util.AppUpdateInfo
import com.munzo.storepoint.util.CrashDiagnosticsManager
import com.munzo.storepoint.util.DatabaseBackupManager
import com.munzo.storepoint.util.EscPosHelper
import com.munzo.storepoint.util.SecurityHelper
import com.munzo.storepoint.util.UpdateDownloadState
import java.io.File
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class StorePointViewModel(application: Application) : AndroidViewModel(application) {

    internal val context = application.applicationContext
    internal val db = AppDatabase.getDatabase(context)
    val repository = StorePointRepository(db)

    // --- In-App Updates State ---
    val updateCheckInProgress = MutableStateFlow(false)
    val updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateErrorMessage = MutableStateFlow<String?>(null)
    val updateDownloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)

    // --- Configurations & Users ---
    val storeConfig: StateFlow<StoreConfig?> = repository.storeConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allUsers: StateFlow<List<User>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCategories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProducts: StateFlow<List<Product>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactionItems: StateFlow<List<TransactionItem>> = repository.allTransactionItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val parkedTransactions: StateFlow<List<ParkedTransaction>> = repository.allParkedTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSession: StateFlow<CashierSession?> = repository.activeSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allCashierSessions: StateFlow<List<CashierSession>> = repository.allCashierSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDrawerTransactions: StateFlow<List<DrawerTransaction>> = repository.allDrawerTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPaymentSchedules: StateFlow<List<PaymentSchedule>> = repository.allPaymentSchedules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReturns: StateFlow<List<ReturnTransaction>> = repository.allReturns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProductVariants: StateFlow<List<ProductVariant>> = repository.allProductVariants
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSuppliers: StateFlow<List<Supplier>> = repository.allSuppliers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPurchaseOrders: StateFlow<List<PurchaseOrder>> = repository.allPurchaseOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Relational StateFlows (@Relation) ---
    val allCategoriesWithProducts: StateFlow<List<CategoryWithProducts>> = repository.allCategoriesWithProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProductsWithVariants: StateFlow<List<ProductWithVariants>> = repository.allProductsWithVariants
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactionsWithItems: StateFlow<List<TransactionWithItems>> = repository.allTransactionsWithItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allParkedWithItems: StateFlow<List<ParkedTransactionWithItems>> = repository.allParkedWithItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReturnsWithItems: StateFlow<List<ReturnTransactionWithItems>> = repository.allReturnsWithItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSuppliersWithOrders: StateFlow<List<SupplierWithPurchaseOrders>> = repository.allSuppliersWithOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPurchaseOrdersWithItems: StateFlow<List<PurchaseOrderWithItems>> = repository.allPurchaseOrdersWithItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- UI/UX Interactive state ---
    val isDatabaseReady = MutableStateFlow(false)
    val databaseHealth = MutableStateFlow<DatabaseHealthReport?>(null)
    val activeUser = MutableStateFlow<User?>(null)
    
    // --- System & SharedPreferences preferences ---
    internal val prefs = context.getSharedPreferences("storepoint_sys_prefs", Context.MODE_PRIVATE)
    val isAlwaysOnEnabled = MutableStateFlow(prefs.getBoolean("always_on_screen", false))
    val kioskPin = MutableStateFlow(prefs.getString("system_kiosk_pin", ""))
    val isOnboardingCompleted = MutableStateFlow(prefs.getBoolean("onboarding_completed", false))

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean("onboarding_completed", completed).apply()
        isOnboardingCompleted.value = completed
    }

    fun setAlwaysOnEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("always_on_screen", enabled).apply()
        isAlwaysOnEnabled.value = enabled
    }

    fun setKioskPin(newPin: String) {
        // C1: Store only a salted PBKDF2 hash of the kiosk PIN (never plaintext).
        val hashedPin = if (newPin.isEmpty()) "" else SecurityHelper.hashPassword(newPin)
        prefs.edit().putString("system_kiosk_pin", hashedPin).apply()
        kioskPin.value = hashedPin
    }

    /** Verifies a candidate kiosk PIN against the stored PBKDF2 hash (constant-time). */
    fun isKioskPinValid(candidate: String): Boolean {
        val storedHash = kioskPin.value ?: ""
        return storedHash.isNotEmpty() && SecurityHelper.verifyPassword(candidate, storedHash).isMatch
    }

    internal var toneGenerator: android.media.ToneGenerator? = null

    fun playBeep() {
        try {
            if (toneGenerator == null) {
                toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)
            }
            toneGenerator?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (e: Exception) {
            android.util.Log.e("StorePointViewModel", "Error playing beep tone", e)
            try {
                toneGenerator?.release()
            } catch (_: Exception) {}
            toneGenerator = null
        }
    }

    // --- Secure administrative data clearing & Salted PIN Hashing ---
    fun hashPassword(password: String): String {
        return SecurityHelper.hashPassword(password)
    }

    fun hashPin(pin: String): String {
        return SecurityHelper.hashPin(pin)
    }

    suspend fun verifyAdminPin(candidatePin: String): Boolean {
        val active = activeUser.value
        if (active != null && active.role.equals("ADMIN", ignoreCase = true)) {
            if (candidatePin.isBlank() || SecurityHelper.verifyPin(candidatePin, active.pinHash).isMatch || active.pinHash == candidatePin) {
                return true
            }
        }
        val dbAdmins = repository.getAllUsersSync().filter { it.role.equals("ADMIN", ignoreCase = true) }
        if (dbAdmins.isEmpty()) {
            return true
        }
        return dbAdmins.any { 
            SecurityHelper.verifyPin(candidatePin, it.pinHash).isMatch || it.pinHash == candidatePin
        }
    }

    // Active cart items (Product to count)
    internal val _cartMap = MutableStateFlow<Map<Int, Pair<Product, Int>>>(emptyMap())
    val cart: StateFlow<List<Pair<Product, Int>>> = _cartMap.map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected UOM per product inside the cart
    internal val _selectedCartUoms = MutableStateFlow<Map<Int, UomOption>>(emptyMap())
    val selectedCartUoms: StateFlow<Map<Int, UomOption>> = _selectedCartUoms.asStateFlow()

    fun getDrawerPayoutsInActiveSession(): Double {
        val session = activeSession.value ?: return 0.0
        return allDrawerTransactions.value.filter {
            it.sessionId == session.id
        }.sumOf { it.amount }
    }

    fun getCurrentDrawerCash(): Double {
        val session = activeSession.value ?: return 0.0
        val sales = allTransactions.value.filter {
            it.timestamp >= session.startTime && it.paymentMethod == "CASH" && it.status != "VOIDED" && it.status != "CANCELLED"
        }.sumOf { it.totalAmount }
        
        val payouts = allDrawerTransactions.value.filter {
            it.sessionId == session.id
        }.sumOf { it.amount }
        
        return StorePointRepository.roundMoney(session.startingCash + sales + payouts)
    }

    // Filters and query state
    val searchBarcodeQuery = MutableStateFlow("")
    val searchProductQuery = MutableStateFlow("")
    val selectedCategoryFilter = MutableStateFlow<Category?>(null)

    // Scanner dialog flag
    var isScannerOpen = MutableStateFlow(false)

    // --- System Observability & Crash Diagnostics ---
    val crashLogs = MutableStateFlow<List<CrashDiagnosticsManager.CrashRecord>>(emptyList())

    // --- Disaster Recovery & Database Backup ---
    val backupSnapshots = MutableStateFlow<List<File>>(emptyList())

    // --- Hardware Peripherals & Direct ESC/POS Printing ---
    val printerType = MutableStateFlow(prefs.getString("printer_type", EscPosHelper.PrinterType.SYSTEM_SPOOLER.name) ?: EscPosHelper.PrinterType.SYSTEM_SPOOLER.name)
    val printerIpAddress = MutableStateFlow(prefs.getString("printer_ip", "192.168.1.100") ?: "192.168.1.100")
    val printerPort = MutableStateFlow(prefs.getInt("printer_port", 9100))
    val printerBtMac = MutableStateFlow(prefs.getString("printer_bt_mac", "") ?: "")
    val isAutoKickDrawerEnabled = MutableStateFlow(prefs.getBoolean("auto_kick_drawer", true))
    val is80mmThermal = MutableStateFlow(prefs.getBoolean("is_80mm_thermal", false))

    init {
        // Pre-populate core default accounts and product folders quickly
        viewModelScope.launch {
            try {
                repository.prePopulateData()
                // Wait for storeConfig Flow to emit its first non-null value from database
                val config = withTimeoutOrNull(3000L) {
                    repository.storeConfig.filterNotNull().first()
                }
                if (config?.setupCompleted == true) {
                    checkAndPerformDailyAutoBackup()
                }
            } catch (e: Exception) {
                android.util.Log.e("StorePointViewModel", "Error initializing store configuration / database", e)
            } finally {
                isDatabaseReady.value = true
                checkDatabaseHealth()
            }
        }
        refreshCrashLogs()
        refreshBackupSnapshots()
    }

    fun checkDatabaseHealth() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val report = AppDatabase.checkConnectionHealth(context)
            databaseHealth.value = report
            android.util.Log.i("StorePointViewModel", "Database Health Report: ${report.message}")
        }
    }

    // --- Setup operations ---
    fun completeInitialSetup(
        storeName: String,
        currency: String,
        taxPercent: Double,
        adminUser: String,
        adminPass: String,
        adminName: String = "",
        adminPhone: String = "",
        hasGCash: Boolean,
        hasMaya: Boolean,
        hasLoad: Boolean,
        smartLoadBalance: Double = 0.0,
        globeLoadBalance: Double = 0.0,
        loadServiceFee: Double = 2.0,
        gcashServiceFee: Double = 10.0,
        mayaServiceFee: Double = 10.0,
        mayaBankFee: Double = 15.0
    ) {
        viewModelScope.launch {
            val hashedPass = hashPin(adminPass)
            // Save admin credentials
            val user = User(username = adminUser, pinHash = hashedPass, role = "ADMIN")
            repository.saveUser(user)

            // Save admin contact details in preferences
            val adminPrefs = context.getSharedPreferences("storepoint_admin_prefs", Context.MODE_PRIVATE)
            adminPrefs.edit()
                .putString("admin_phone_number", adminPhone.trim())
                .putString("admin_name", adminName.trim())
                .apply()

            // Save store config
            val config = StoreConfig(
                storeName = storeName,
                currencySymbol = currency,
                taxPercentage = taxPercent,
                setupCompleted = true,
                hasGCash = hasGCash,
                hasMaya = hasMaya,
                hasLoad = hasLoad,
                smartLoadBalance = StorePointRepository.roundMoney(smartLoadBalance),
                globeLoadBalance = StorePointRepository.roundMoney(globeLoadBalance),
                loadServiceFee = StorePointRepository.roundMoney(loadServiceFee),
                gcashServiceFee = StorePointRepository.roundMoney(gcashServiceFee),
                mayaServiceFee = StorePointRepository.roundMoney(mayaServiceFee),
                mayaBankFee = StorePointRepository.roundMoney(mayaBankFee)
            )
            repository.saveStoreConfig(config)
            
            // Automatically log in active administrator
            activeUser.value = user
            Toast.makeText(context, "Initial Store Setup Completed Successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Authentication ---
    val isKioskModeActive = MutableStateFlow(prefs.getBoolean("is_kiosk_mode_active", false))

    fun toggleKioskMode(active: Boolean) {
        prefs.edit().putBoolean("is_kiosk_mode_active", active).apply()
        isKioskModeActive.value = active
    }

    fun loginWithBarcode(barcodeId: String, onSuccess: (User) -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            val user = repository.getUserByBarcode(barcodeId.trim())
            if (user != null) {
                activeUser.value = user
                playBeep()
                onSuccess(user)
            } else {
                onFailure("No staff member matches scanned barcode ID: $barcodeId")
            }
        }
    }

    val isBiometricEnabled = MutableStateFlow(prefs.getBoolean("is_biometric_enabled", true))
    val lastLoggedInUser = MutableStateFlow(prefs.getString("last_logged_in_user", null))

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_biometric_enabled", enabled).apply()
        isBiometricEnabled.value = enabled
    }

    fun login(username: String, pass: String, onSuccess: (User) -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            // M6: Rate-limiting / lockout after repeated failed logins
            val now = System.currentTimeMillis()
            val lockedUntil = prefs.getLong("login_locked_until", 0L)
            if (now < lockedUntil) {
                val remainSec = ((lockedUntil - now) / 1000).toInt().coerceAtLeast(1)
                onFailure("Too many failed attempts. Try again in ${remainSec}s.")
                return@launch
            }

            val user = repository.getUserByUsername(username)
            if (user != null) {
                val verification = SecurityHelper.verifyPin(pass, user.pinHash)
                if (verification.isMatch) {
                    // Successful authentication resets the failure counter
                    prefs.edit().putInt("login_fail_count", 0).putLong("login_locked_until", 0L).apply()
                    if (verification.needsUpgrade) {
                        val modernHash = SecurityHelper.hashPin(pass)
                        repository.saveUser(user.copy(pinHash = modernHash))
                    }
                    activeUser.value = user
                    CrashDiagnosticsManager.currentCashier = user.username
                    prefs.edit().putString("last_logged_in_user", user.username).apply()
                    lastLoggedInUser.value = user.username
                    onSuccess(user)
                    return@launch
                }
            }

            // Failed attempt bookkeeping
            val fails = prefs.getInt("login_fail_count", 0) + 1
            if (fails >= 5) {
                prefs.edit().putInt("login_fail_count", 0).putLong("login_locked_until", now + 30_000L).apply()
                onFailure("Too many failed attempts. Login locked for 30 seconds.")
            } else {
                prefs.edit().putInt("login_fail_count", fails).apply()
                onFailure("Incorrect local username or 6-digit PIN.")
            }
        }
    }

    fun verifyKioskUnlock(pin: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val lockedUntil = prefs.getLong("kiosk_unlock_locked_until", 0L)
            if (now < lockedUntil) {
                val remainSec = ((lockedUntil - now) / 1000).toInt().coerceAtLeast(1)
                onFailure("Lockout active. Try again in ${remainSec}s.")
                return@launch
            }

            val admins = repository.getAllUsersSync().filter { it.role.equals("ADMIN", ignoreCase = true) }
            val isValid = admins.any { SecurityHelper.verifyPin(pin, it.passwordHash).isMatch || it.passwordHash == pin }

            if (isValid) {
                prefs.edit().putInt("kiosk_unlock_fail_count", 0).putLong("kiosk_unlock_locked_until", 0L).apply()
                onSuccess()
            } else {
                val fails = prefs.getInt("kiosk_unlock_fail_count", 0) + 1
                if (fails >= 5) {
                    prefs.edit().putInt("kiosk_unlock_fail_count", 0).putLong("kiosk_unlock_locked_until", now + 60_000L).apply()
                    onFailure("Too many failed unlock attempts. Locked for 60 seconds.")
                } else {
                    prefs.edit().putInt("kiosk_unlock_fail_count", fails).apply()
                    onFailure("PIN incorrect. Access Denied.")
                }
            }
        }
    }

    fun loginWithBiometric(targetUsername: String?, onSuccess: (User) -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            val usernameToFind = targetUsername ?: prefs.getString("last_logged_in_user", null)
            val user = if (usernameToFind != null) {
                repository.getUserByUsername(usernameToFind)
            } else {
                repository.getAllUsersSync().firstOrNull { it.role != "ADMIN" }
            }

            if (user != null) {
                if (user.role.equals("ADMIN", ignoreCase = true)) {
                    onFailure("Admin accounts require PIN sign-in. Biometric unlock is available for cashiers only.")
                    return@launch
                }
                activeUser.value = user
                CrashDiagnosticsManager.currentCashier = user.username
                prefs.edit().putString("last_logged_in_user", user.username).apply()
                lastLoggedInUser.value = user.username
                playBeep()
                onSuccess(user)
            } else {
                onFailure("No cashier account found for biometric sign-in.")
            }
        }
    }

    fun logout() {
        activeUser.value = null
        CrashDiagnosticsManager.currentCashier = "None"
        clearCart()
    }

    // --- Shift Session Management ---
    fun startCashierShift(
        startingCash: Double,
        startingSmartLoad: Double? = null,
        startingGlobeLoad: Double? = null
    ) {
        viewModelScope.launch {
            val username = activeUser.value?.username ?: "system_user"
            val normCash = StorePointRepository.roundMoney(startingCash)
            val started = repository.startSession(username, normCash)
            if (started) {
                val currentConfig = repository.getStoreConfigSync()
                if (currentConfig != null && (startingSmartLoad != null || startingGlobeLoad != null)) {
                    var updated = currentConfig
                    if (startingSmartLoad != null) {
                        updated = updated.copy(smartLoadBalance = StorePointRepository.roundMoney(startingSmartLoad))
                    }
                    if (startingGlobeLoad != null) {
                        updated = updated.copy(globeLoadBalance = StorePointRepository.roundMoney(startingGlobeLoad))
                    }
                    repository.saveStoreConfig(updated)
                }
                Toast.makeText(context, "Shift session started with ${storeConfig.value?.currencySymbol}$normCash starting cash drawer.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Cannot start shift: an active session is already running. End it first.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun closeCashierShift(
        endingCash: Double,
        endingGCash: Double? = null,
        endingSmartLoad: Double? = null,
        endingGlobeLoad: Double? = null
    ) {
        viewModelScope.launch {
            val normEndingCash = StorePointRepository.roundMoney(endingCash)
            repository.endActiveSession(normEndingCash)
            val currentConfig = repository.getStoreConfigSync()
            if (currentConfig != null) {
                var updated = currentConfig
                if (endingGCash != null) {
                    updated = updated.copy(gcashBalance = StorePointRepository.roundMoney(endingGCash))
                }
                if (endingSmartLoad != null) {
                    updated = updated.copy(smartLoadBalance = StorePointRepository.roundMoney(endingSmartLoad))
                }
                if (endingGlobeLoad != null) {
                    updated = updated.copy(globeLoadBalance = StorePointRepository.roundMoney(endingGlobeLoad))
                }
                if (updated != currentConfig) {
                    repository.saveStoreConfig(updated)
                }
            }
            clearCart()
            Toast.makeText(context, "Shift session closed. Drawer totals updated offline.", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateGCashBalance(newBalance: Double) {
        viewModelScope.launch {
            val currentConfig = repository.getStoreConfigSync()
            if (currentConfig != null) {
                repository.saveStoreConfig(currentConfig.copy(gcashBalance = StorePointRepository.roundMoney(newBalance)))
                Toast.makeText(context, "GCash balance updated successfully.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateSmartLoadBalance(newBalance: Double) {
        viewModelScope.launch {
            val currentConfig = repository.getStoreConfigSync()
            if (currentConfig != null) {
                repository.saveStoreConfig(currentConfig.copy(smartLoadBalance = StorePointRepository.roundMoney(newBalance)))
                Toast.makeText(context, "Smart Load balance updated successfully.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateGlobeLoadBalance(newBalance: Double) {
        viewModelScope.launch {
            val currentConfig = repository.getStoreConfigSync()
            if (currentConfig != null) {
                repository.saveStoreConfig(currentConfig.copy(globeLoadBalance = StorePointRepository.roundMoney(newBalance)))
                Toast.makeText(context, "Globe Load balance updated successfully.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateDigitalServiceFees(
        loadFee: Double,
        gcashFee: Double,
        mayaFee: Double,
        mayaBankFee: Double
    ) {
        viewModelScope.launch {
            val currentConfig = repository.getStoreConfigSync()
            if (currentConfig != null) {
                val updated = currentConfig.copy(
                    loadServiceFee = StorePointRepository.roundMoney(loadFee),
                    gcashServiceFee = StorePointRepository.roundMoney(gcashFee),
                    mayaServiceFee = StorePointRepository.roundMoney(mayaFee),
                    mayaBankFee = StorePointRepository.roundMoney(mayaBankFee)
                )
                repository.saveStoreConfig(updated)
                Toast.makeText(context, "Digital services service charges updated!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveUser(user: User) {
        viewModelScope.launch {
            val finalHash = if (user.pinHash.startsWith("pbkdf2$")) {
                user.pinHash
            } else {
                hashPin(user.pinHash)
            }
            val persisted = user.copy(pinHash = finalHash)
            repository.saveUser(persisted)
            Toast.makeText(context, "Saved user profile: ${user.username}", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteUser(username: String) {
        viewModelScope.launch {
            repository.deleteUser(username)
            Toast.makeText(context, "User profile removed.", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Cart & POS Operations Forwarders ---
    fun selectCartUom(productId: Int, uom: UomOption) = cartSelectUomImpl(productId, uom)
    fun addToCart(product: Product, quantity: Int = 1) = cartAddToCartImpl(product, quantity)
    fun updateCartQuantity(product: Product, quantity: Int) = cartUpdateQuantityImpl(product, quantity)
    fun removeFromCart(product: Product) = cartRemoveFromCartImpl(product)
    fun clearCart() = cartClearCartImpl()
    fun handleBarcodeScan(barcode: String, quantity: Int = 1) = cartHandleBarcodeScanImpl(barcode, quantity)
    fun parkActiveTransaction(note: String) = cartParkActiveTransactionImpl(note)
    fun resumeParkedTransaction(parkedId: Int, onComplete: () -> Unit = {}) = cartResumeParkedTransactionImpl(parkedId, onComplete)
    fun deleteParkedTransaction(parkedId: Int) = cartDeleteParkedTransactionImpl(parkedId)
    fun executeCheckout(
        paymentMethod: String,
        cashPaid: Double,
        subtotal: Double,
        taxAmount: Double,
        totalAmount: Double,
        checkoutItems: List<Pair<Product, Int>>? = null,
        customerName: String = "",
        onSuccess: (Transaction) -> Unit
    ) = cartExecuteCheckoutImpl(paymentMethod, cashPaid, subtotal, taxAmount, totalAmount, checkoutItems, customerName, onSuccess)

    // --- Inventory & Products Forwarders ---
    fun exportInventoryJson(): String = inventoryExportJsonImpl()
    fun exportInventoryPackage(): String = inventoryExportPackageImpl()
    fun importInventoryJson(jsonStr: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) = inventoryImportJsonImpl(jsonStr, onSuccess, onFailure)
    fun importInventoryPackageDetailed(rawContent: String, onSuccess: (Int, Int) -> Unit, onFailure: (String) -> Unit) =
        inventoryImportPackageDetailedImpl(rawContent, onSuccess, onFailure)
    fun shareInventoryViaQuickShare(context: Context) = inventoryShareViaQuickShareImpl(context)
    fun saveCategory(category: Category) = inventorySaveCategoryImpl(category)
    fun deleteCategory(id: Int) = inventoryDeleteCategoryImpl(id)
    fun saveProduct(product: Product) = inventorySaveProductImpl(product)
    fun deleteProduct(id: Int) = inventoryDeleteProductImpl(id)
    fun restockProductWithUom(
        product: Product,
        quantity: Int,
        multiplier: Int,
        totalCost: Double?,
        payFromDrawer: Boolean = false,
        onPayoutError: (String) -> Unit = {}
    ) = inventoryRestockProductWithUomImpl(product, quantity, multiplier, totalCost, payFromDrawer, onPayoutError)
    fun getActiveUomsForProduct(product: Product): List<UomOption> = inventoryGetActiveUomsForProductImpl(product)
    fun getVariantsForProduct(productId: Int): Flow<List<ProductVariant>> = inventoryGetVariantsForProductImpl(productId)
    fun saveProductVariant(variant: ProductVariant, onSuccess: () -> Unit = {}) = inventorySaveProductVariantImpl(variant, onSuccess)
    fun deleteProductVariant(id: Int, onSuccess: () -> Unit = {}) = inventoryDeleteProductVariantImpl(id, onSuccess)

    // --- Hardware & Thermal Printing Forwarders ---
    fun updatePrinterSettings(type: String, ip: String, port: Int, btMac: String, autoKick: Boolean, is80mm: Boolean) = hardwareUpdatePrinterSettingsImpl(type, ip, port, btMac, autoKick, is80mm)
    fun setPrinterType(type: String) = hardwareSetPrinterTypeImpl(type)
    fun setPrinterNetworkConfig(ip: String, port: Int) = hardwareSetPrinterNetworkConfigImpl(ip, port)
    fun setPrinterBluetoothMac(mac: String) = hardwareSetPrinterBluetoothMacImpl(mac)
    fun setPaperWidth80mm(is80: Boolean) = hardwareSetPaperWidth80mmImpl(is80)
    fun setAutoKickDrawerEnabled(enabled: Boolean) = hardwareSetAutoKickDrawerEnabledImpl(enabled)
    fun testHardwarePrinter(onResult: (Boolean, String) -> Unit) = hardwareTestPrinterImpl(onResult)
    fun kickCashDrawer(onResult: (Boolean, String) -> Unit) = hardwareKickCashDrawerImpl(onResult)
    fun printReceiptHardware(transaction: Transaction, items: List<TransactionItem>, onCompleted: (Boolean, String) -> Unit) = hardwarePrintReceiptImpl(transaction, items, onCompleted)

    // --- Backup & Disaster Recovery Forwarders ---
    fun refreshCrashLogs() = backupRefreshCrashLogsImpl()
    fun clearCrashLogs() = backupClearCrashLogsImpl()
    fun getDiagnosticReport(): String = backupGetDiagnosticReportImpl()
    fun refreshBackupSnapshots() = backupRefreshBackupSnapshotsImpl()
    internal fun checkAndPerformDailyAutoBackup() = backupCheckAndPerformDailyAutoBackupImpl()
    fun exportDatabaseBackup(onCompleted: (File?) -> Unit) = backupExportDatabaseBackupImpl(onCompleted)
    fun exportDatabaseBackup(passphrase: String, onCompleted: (File?) -> Unit) = backupExportDatabaseBackupImpl(passphrase, onCompleted)
    fun exportDatabaseBackup(onSuccess: (File) -> Unit, onFailure: (String) -> Unit) = backupExportDatabaseBackupImpl(onSuccess, onFailure)
    fun exportDatabaseBackup(passphrase: String, onSuccess: (File) -> Unit, onFailure: (String) -> Unit) = backupExportDatabaseBackupImpl(passphrase, onSuccess, onFailure)
    fun restoreDatabaseBackup(jsonContent: String, passphrase: String = "", onResult: (DatabaseBackupManager.RestoreSummary) -> Unit) = backupRestoreDatabaseBackupImpl(jsonContent, passphrase, onResult)
    fun restoreFromPersistentBackup(file: File, onResult: (DatabaseBackupManager.RestoreSummary) -> Unit) = backupRestoreFromPersistentBackupImpl(file, onResult)
    fun getLatestPersistentBackup(): File? = DatabaseBackupManager.getLatestPersistentBackup(context)
    fun peekBackupMetadata(file: File): DatabaseBackupManager.BackupMetadata? = DatabaseBackupManager.peekBackupMetadata(file)
    fun clearAllDatabaseData(adminPass: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) = backupClearAllDatabaseDataImpl(adminPass, onSuccess, onFailure)

    // --- Suppliers & Purchase Orders Forwarders ---
    fun saveSupplier(supplier: Supplier, onSuccess: () -> Unit = {}) = purchasesSaveSupplierImpl(supplier, onSuccess)
    fun deleteSupplier(supplierId: Int, onSuccess: () -> Unit = {}) = purchasesDeleteSupplierImpl(supplierId, onSuccess)
    fun createPurchaseOrder(
        supplier: Supplier,
        expectedDeliveryDate: Long?,
        items: List<Pair<Product, Pair<Int, Double>>>,
        notes: String,
        onSuccess: (PurchaseOrder) -> Unit
    ) = purchasesCreatePurchaseOrderImpl(supplier, expectedDeliveryDate, items, notes, onSuccess)
    fun receivePurchaseOrder(
        poId: Int,
        receivedItems: List<Pair<Int, Int>>,
        paymentStatus: String,
        onSuccess: () -> Unit
    ) = purchasesReceivePurchaseOrderImpl(poId, receivedItems, paymentStatus, onSuccess)
    fun deletePurchaseOrder(poId: Int, onSuccess: () -> Unit = {}) = purchasesDeletePurchaseOrderImpl(poId, onSuccess)

    // --- In-App Updates Forwarders ---
    fun checkForAppUpdates(currentVersion: String = APP_VERSION, onComplete: (AppUpdateInfo?) -> Unit = {}) = updatesCheckForAppUpdatesImpl(currentVersion, onComplete)
    fun startUpdateDownload(context: Context, downloadUrl: String, fileName: String = "StorePoint-update.apk") = updatesStartUpdateDownloadImpl(context, downloadUrl, fileName)
    fun installDownloadedApk(context: Context, apkFile: File) = updatesInstallDownloadedApkImpl(context, apkFile)
    fun clearUpdateState() = updatesClearUpdateStateImpl()

    // --- Returns & Refunds Forwarders ---
    fun getTransactionWithItems(txId: Int, onResult: (Transaction?, List<TransactionItem>) -> Unit) = returnsGetTransactionWithItemsImpl(txId, onResult)
    fun getReturnItemsForReturnTx(returnTxId: Int, onResult: (List<ReturnItem>) -> Unit) = returnsGetReturnItemsForReturnTxImpl(returnTxId, onResult)
    fun processReturn(
        originalTransactionId: Int,
        returnedItems: List<ReturnItemDraft>,
        refundMethod: String,
        returnReason: String,
        customerName: String,
        notes: String,
        onSuccess: (ReturnTransaction) -> Unit,
        onFailure: (String) -> Unit
    ) = returnsProcessReturnImpl(originalTransactionId, returnedItems, refundMethod, returnReason, customerName, notes, onSuccess, onFailure)

    // --- Payment Schedules Forwarders ---
    fun addPaymentSchedule(title: String, amount: Double, dueDate: String, category: String, priority: String, paymentMethod: String = "CASH") = schedulesAddPaymentScheduleImpl(title, amount, dueDate, category, priority, paymentMethod)
    fun togglePaymentScheduleStatus(schedule: PaymentSchedule) = schedulesTogglePaymentScheduleStatusImpl(schedule)
    fun deletePaymentSchedule(schedule: PaymentSchedule) = schedulesDeletePaymentScheduleImpl(schedule)

    // --- Returns, Refunds & Item Exchanges ---
    data class ReturnItemDraft(
        val originalItem: TransactionItem,
        val returnQuantity: Int,
        val unitRefundPrice: Double,
        val restockToInventory: Boolean
    )

    override fun onCleared() {
        super.onCleared()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            android.util.Log.e("StorePointViewModel", "Error releasing ToneGenerator", e)
        } finally {
            toneGenerator = null
        }
    }
}
