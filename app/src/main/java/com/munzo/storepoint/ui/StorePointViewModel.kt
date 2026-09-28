package com.munzo.storepoint.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.munzo.storepoint.data.*
import com.munzo.storepoint.util.APP_VERSION
import com.munzo.storepoint.util.AppUpdateInfo
import com.munzo.storepoint.util.BiometricAuthHelper
import com.munzo.storepoint.util.CrashDiagnosticsManager
import com.munzo.storepoint.util.DatabaseBackupManager
import com.munzo.storepoint.util.EscPosHelper
import com.munzo.storepoint.util.KioskLockout
import com.munzo.storepoint.util.SecurityHelper
import com.munzo.storepoint.util.SessionStateCache
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

    /**
     * Escalating failure counter for the kiosk PIN itself.
     *
     * SECURITY (issue #3): the Admin Security dialog used to call [isKioskPinValid]
     * directly on every button press, with no fail counter at all, against a PIN
     * whose keyspace is only 10,000. Every other privileged path in the app
     * ([verifyAdminPin], [verifyKioskUnlock], `login`) was already throttled; this
     * was the single exception, and it guarded the register.
     */
    private val kioskPinLockout = KioskLockout(
        readFailCount = { prefs.getInt(KIOSK_PIN_FAIL_COUNT, 0) },
        readLockedUntil = { prefs.getLong(KIOSK_PIN_LOCKED_UNTIL, 0L) },
        writeState = { _, fails, lockedUntil ->
            prefs.edit().putInt(KIOSK_PIN_FAIL_COUNT, fails)
                .putLong(KIOSK_PIN_LOCKED_UNTIL, lockedUntil).apply()
        }
    )

    /**
     * Verifies the kiosk PIN under an escalating lockout.
     *
     * This is the **only** supported way to release lockdown with the kiosk PIN.
     * [isKioskPinValid] remains a pure, side-effect-free comparison for callers
     * that only need to know whether a PIN is configured.
     */
    fun verifyKioskPinUnlock(pin: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val active = kioskPinLockout.lockoutMessage(KIOSK_PIN_LOCKOUT_KEY)
        if (active != null) {
            onFailure(active)
            return
        }

        if (isKioskPinValid(pin)) {
            kioskPinLockout.onSuccess(KIOSK_PIN_LOCKOUT_KEY)
            onSuccess()
        } else {
            onFailure(kioskPinLockout.onFailure(KIOSK_PIN_LOCKOUT_KEY))
        }
    }

    /** Verifies a candidate kiosk PIN against the stored PBKDF2 hash (constant-time). */
    fun isKioskPinValid(candidate: String): Boolean {
        val storedHash = kioskPin.value ?: ""
        return storedHash.isNotEmpty() && SecurityHelper.verifyPassword(candidate, storedHash).isMatch
    }

    /** True when a kiosk PIN has been configured on this terminal. */
    fun hasKioskPin(): Boolean = !kioskPin.value.isNullOrEmpty()

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

    /**
     * Verifies a candidate admin PIN for privileged operations (inventory import certification).
     *
     * SECURITY (audit H1): fail-closed — a blank candidate never authenticates, a database with
     * no admin accounts never auto-authorizes, and candidates are only checked through
     * [SecurityHelper.verifyPin] (constant-time; legacy plaintext/SHA-256 hashes are still
     * accepted there until they are upgraded on next login). The previous implementation's
     * `pinHash == candidatePin` plaintext shortcut and blank/no-admin `return true` paths are
     * removed. Repeated failures trigger a 60-second lockout to slow brute-force of the
     * PIN space.
     */
    suspend fun verifyAdminPin(candidatePin: String): Boolean {
        if (candidatePin.isBlank()) return false

        val now = System.currentTimeMillis()
        val lockedUntil = prefs.getLong("admin_pin_locked_until", 0L)
        if (now < lockedUntil) return false

        val active = activeUser.value
        val verified = if (active != null && active.role.equals("ADMIN", ignoreCase = true) &&
            SecurityHelper.verifyPin(candidatePin, active.pinHash).isMatch
        ) {
            true
        } else {
            val dbAdmins = repository.getAllUsersSync().filter { it.role.equals("ADMIN", ignoreCase = true) }
            // Fail closed: with no admin accounts there is nothing valid to verify against.
            dbAdmins.isNotEmpty() && dbAdmins.any { SecurityHelper.verifyPin(candidatePin, it.pinHash).isMatch }
        }

        if (verified) {
            prefs.edit().putInt("admin_pin_fail_count", 0).putLong("admin_pin_locked_until", 0L).apply()
        } else {
            val fails = prefs.getInt("admin_pin_fail_count", 0) + 1
            if (fails >= 5) {
                prefs.edit().putInt("admin_pin_fail_count", 0).putLong("admin_pin_locked_until", now + 60_000L).apply()
            } else {
                prefs.edit().putInt("admin_pin_fail_count", fails).apply()
            }
        }
        return verified
    }

    // Active cart items (Product to count)
    internal val _cartMap = MutableStateFlow<Map<Int, Pair<Product, Int>>>(emptyMap())
    val cart: StateFlow<List<Pair<Product, Int>>> = _cartMap.map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected UOM per product inside the cart
    internal val _selectedCartUoms = MutableStateFlow<Map<Int, UomOption>>(emptyMap())
    val selectedCartUoms: StateFlow<Map<Int, UomOption>> = _selectedCartUoms.asStateFlow()

    /**
     * Session/UI state cache (cart draft, last payment method, last tabs).
     *
     * The cart draft is mirrored to disk on every mutation so an interrupted sale
     * survives a crash or a kiosk reboot. Restoration re-resolves product ids against
     * the live catalogue so stale prices and stock levels are never resurrected.
     */
    val sessionCache = SessionStateCache(context)

    init {
        // Mirror every cart/UOM change to the draft. Debounced is unnecessary here:
        // the write is a tiny SharedPreferences `apply()` (async, in-memory commit),
        // not a disk sync.
        viewModelScope.launch {
            combine(_cartMap, _selectedCartUoms) { cart, uoms -> cart to uoms }
                .collect { (cart, uoms) -> sessionCache.saveCartDraft(cart, uoms) }
        }
    }

    /**
     * Re-populates the cart from the persisted draft.
     *
     * Called after authentication. Lines whose product no longer exists, or whose
     * quantity now exceeds available stock, are dropped rather than restored at an
     * invalid value — a silently-wrong basket is worse than a missing line.
     */
    fun restoreCartDraft() {
        viewModelScope.launch {
            val draft = sessionCache.loadCartDraft() ?: return@launch
            val catalogue = allProducts.value.associateBy { it.id }
            if (catalogue.isEmpty()) return@launch

            val restoredCart = mutableMapOf<Int, Pair<Product, Int>>()
            val restoredUoms = mutableMapOf<Int, UomOption>()
            var dropped = 0

            draft.forEach { line ->
                val product = catalogue[line.productId]
                if (product == null) {
                    dropped++
                    return@forEach
                }
                val uom = line.uomName?.let { name ->
                    UomOption(name, line.uomMultiplier.coerceAtLeast(1), line.uomPrice)
                }
                // Respect the same stock ceiling the live add-to-cart path enforces.
                val effective = line.quantity * (uom?.multiplier ?: 1)
                if (line.quantity <= 0 || effective > product.stockCount) {
                    dropped++
                    return@forEach
                }
                restoredCart[product.id] = Pair(product, line.quantity)
                if (uom != null) restoredUoms[product.id] = uom
            }

            if (restoredCart.isNotEmpty()) {
                _cartMap.value = restoredCart
                _selectedCartUoms.value = restoredUoms
            }
            if (dropped > 0) {
                android.util.Log.i("StorePointViewModel", "Cart restore dropped $dropped unavailable line(s)")
            }
            // Reflect the outcome so the POS screen can acknowledge the restore.
            restoredCartLineCount = restoredCart.size
        }
    }

    /**
     * Number of lines recovered by the most recent [restoreCartDraft] call.
     *
     * Exposed to the POS screen so it can show a "Restored your basket" notice.
     */
    var restoredCartLineCount: Int = 0
        internal set

    /** Clears the "basket restored" acknowledgement once the POS screen has shown it. */
    fun acknowledgeCartRestore() {
        restoredCartLineCount = 0
        sessionCache.didRestoreCart = false
    }


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
            // Save admin credentials. `pinResetRequired = false` because this account
            // is created now under the 4-digit standard — it must not be routed
            // through the legacy-credential migration wizard.
            val user = User(
                username = adminUser,
                pinHash = hashedPass,
                role = "ADMIN",
                biometricEnrolled = false,
                pinResetRequired = false
            )
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

    private companion object {
        /** Lockout bucket for the kiosk PIN, kept separate from admin-PIN throttles. */
        const val KIOSK_PIN_LOCKOUT_KEY = "kiosk_pin"
        const val KIOSK_PIN_FAIL_COUNT = "kiosk_pin_fail_count"
        const val KIOSK_PIN_LOCKED_UNTIL = "kiosk_pin_locked_until"
    }

    fun toggleKioskMode(active: Boolean) {
        prefs.edit().putBoolean("is_kiosk_mode_active", active).apply()
        isKioskModeActive.value = active
        if (!active) {
            // Releasing lockdown clears its failure history, so a later re-activation
            // does not inherit a stale lockout from a previous lockdown cycle.
            kioskPinLockout.onSuccess(KIOSK_PIN_LOCKOUT_KEY)
        }
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

    /**
     * Progressive lockout schedule, indexed by consecutive-failure count.
     *
     * A 4-digit PIN has a 10,000-combination keyspace, so the previous flat
     * 5-try/30s lockout was too weak to compensate. The window now grows on each
     * successive failure (30s -> 60s -> 120s -> 300s) and is tracked *per username*,
     * so an attacker cannot lock every staff account out at once.
     */
    private val lockoutScheduleMs = longArrayOf(30_000L, 60_000L, 120_000L, 300_000L)

    private fun lockoutWindowFor(failCount: Int): Long =
        lockoutScheduleMs[failCount.coerceAtMost(lockoutScheduleMs.lastIndex)]

    /** Accounts still holding a pre-migration 6-digit credential that must reset. */
    val usersPendingPinReset: StateFlow<List<User>> = repository.usersPendingPinReset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Users who have bound a device biometric, used to drive fingerprint auto-login. */
    val biometricEnrolledUsers: StateFlow<List<User>> = repository.biometricEnrolledUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Completes a user's one-time migration to a 4-digit PIN.
     *
     * The caller must have already authorized this via the current credential. Enforces
     * the weak-PIN policy before persisting.
     */
    fun resetPin(
        username: String,
        newPin: String,
        onSuccess: (User) -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            if (!SecurityHelper.isValidPin(newPin)) {
                onFailure("PIN must be exactly ${SecurityHelper.PIN_LENGTH} digits.")
                return@launch
            }
            if (SecurityHelper.isWeakPin(newPin)) {
                onFailure("That PIN is too easy to guess. ${SecurityHelper.pinPolicyHint()}")
                return@launch
            }
            val user = repository.getUserByUsername(username)
            if (user == null) {
                onFailure("Account not found.")
                return@launch
            }
            val updated = user.copy(
                pinHash = SecurityHelper.hashPin(newPin),
                pinResetRequired = false
            )
            repository.updatePinAfterReset(username, updated.pinHash)
            onSuccess(updated)
        }
    }

    /**
     * Authorizes the forced PIN migration using the account's existing credential.
     *
     * Accepts the legacy 6-digit format (and 4-digit, for users already reset) via
     * [SecurityHelper.verifyPinLenient]. Rate-limited on the same escalating schedule
     * as normal sign-in, because this is the highest-value target on the terminal.
     */
    fun verifyPinResetAuthorization(
        username: String,
        currentPin: String,
        onSuccess: (User) -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val lockKey = "pinreset_$username"
            val lockedUntil = prefs.getLong("${lockKey}_locked_until", 0L)
            if (now < lockedUntil) {
                val remainSec = ((lockedUntil - now) / 1000).toInt().coerceAtLeast(1)
                onFailure("Too many attempts. Try again in ${remainSec}s.")
                return@launch
            }

            val user = repository.getUserByUsername(username)
            if (user == null) {
                onFailure("Account not found.")
                return@launch
            }

            val verification = SecurityHelper.verifyPinLenient(currentPin, user.pinHash)
            if (verification.isMatch) {
                prefs.edit().putInt("${lockKey}_fails", 0).putLong("${lockKey}_locked_until", 0L).apply()
                onSuccess(user)
                return@launch
            }

            val fails = prefs.getInt("${lockKey}_fails", 0) + 1
            if (fails >= 4) {
                val window = lockoutWindowFor(fails - 4)
                prefs.edit()
                    .putInt("${lockKey}_fails", 0)
                    .putLong("${lockKey}_locked_until", now + window)
                    .apply()
                onFailure("Too many attempts. Locked for ${window / 1000} seconds.")
            } else {
                val remaining = 4 - fails
                prefs.edit().putInt("${lockKey}_fails", fails).apply()
                onFailure("Current PIN incorrect. $remaining attempt${if (remaining == 1) "" else "s"} left.")
            }
        }
    }

    /**
     * Primary sign-in path.
     *
     * Accepts a 4-digit PIN. Users flagged `pinResetRequired` are refused rather than
     * signed in, because their stored credential is still the legacy 6-digit format.
     * On success the account is auto-enrolled for fingerprint sign-in so subsequent
     * launches reach the register in zero taps.
     */
    fun login(username: String, pass: String, onSuccess: (User) -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val lockKey = "login_fail_$username"
            val lockedUntil = prefs.getLong("${lockKey}_locked_until", 0L)
            if (now < lockedUntil) {
                val remainSec = ((lockedUntil - now) / 1000).toInt().coerceAtLeast(1)
                onFailure("Too many failed attempts. Try again in ${remainSec}s.")
                return@launch
            }

            val user = repository.getUserByUsername(username)
            if (user != null && !user.pinResetRequired) {
                val verification = SecurityHelper.verifyPin(pass, user.pinHash)
                if (verification.isMatch) {
                    prefs.edit().putInt("${lockKey}_fails", 0).putLong("${lockKey}_locked_until", 0L).apply()
                    var signedIn = user
                    if (verification.needsUpgrade) {
                        signedIn = user.copy(pinHash = SecurityHelper.hashPin(pass))
                        repository.saveUser(signedIn)
                    }
                    signedIn = maybeAutoEnrollBiometric(signedIn)
                    completeLogin(signedIn, onSuccess)
                    return@launch
                }
            }

            // Failed attempt bookkeeping with a growing, per-account lockout window.
            val fails = prefs.getInt("${lockKey}_fails", 0) + 1
            if (fails >= 4) {
                val window = lockoutWindowFor(fails - 4)
                prefs.edit()
                    .putInt("${lockKey}_fails", 0)
                    .putLong("${lockKey}_locked_until", now + window)
                    .apply()
                onFailure("Too many failed attempts. Locked for ${window / 1000} seconds.")
            } else {
                prefs.edit().putInt("${lockKey}_fails", fails).apply()
                val remaining = 4 - fails
                val msg = if (user?.pinResetRequired == true) {
                    "This account still needs a new ${SecurityHelper.PIN_LENGTH}-digit PIN."
                } else {
                    "Incorrect username or ${SecurityHelper.PIN_LENGTH}-digit PIN. " +
                        "$remaining attempt${if (remaining == 1) "" else "s"} left."
                }
                onFailure(msg)
            }
        }
    }

    /** Shared post-authentication bookkeeping used by PIN and fingerprint sign-in. */
    private suspend fun completeLogin(user: User, onSuccess: (User) -> Unit) {
        activeUser.value = user
        CrashDiagnosticsManager.currentCashier = user.username
        prefs.edit().putString("last_logged_in_user", user.username).apply()
        lastLoggedInUser.value = user.username
        playBeep()
        // Rehydrate any basket that survived a crash or a shift change.
        restoreCartDraft()
        onSuccess(user)
    }

    /**
     * Binds a device biometric to the account on first successful authentication.
     *
     * Persisted to `user_accounts.biometricEnrolled` so the login screen can offer
     * zero-tap fingerprint sign-in on the next launch. Respects the admin's global
     * biometric kill-switch.
     */
    private suspend fun maybeAutoEnrollBiometric(user: User): User {
        if (user.biometricEnrolled) return user
        if (!isBiometricEnabled.value) return user
        if (!BiometricAuthHelper.checkAvailability(getApplication()).isAvailable) return user
        repository.setBiometricEnrolled(user.username, true)
        return user.copy(biometricEnrolled = true)
    }

    /** Explicitly (un)binds a biometric for a user from the Admin security screen. */
    fun setBiometricEnrolledForUser(username: String, enrolled: Boolean) {
        viewModelScope.launch {
            repository.setBiometricEnrolled(username, enrolled)
        }
    }

    /**
     * Authorizes leaving kiosk lockdown or a protected admin action.
     *
     * Uses [SecurityHelper.verifyPinLenient] so an admin who has not yet completed the
     * 4-digit migration can still exit kiosk lockdown — otherwise the migration would
     * become a way to permanently lock a terminal into kiosk mode. This path stays
     * rate-limited below and matches every admin account.
     */
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
            // SECURITY (audit H2): constant-time verification only — the previous
            // `hash == pin` plaintext shortcut is removed. Legacy plaintext/SHA-256 hashes
            // remain verifiable through SecurityHelper and are upgraded to PBKDF2 on success.
            var isValid = false
            if (pin.isNotBlank()) {
                for (admin in admins) {
                    val verification = SecurityHelper.verifyPinLenient(pin, admin.passwordHash)
                    if (verification.isMatch) {
                        isValid = true
                        if (verification.needsUpgrade) {
                            repository.saveUser(admin.copy(pinHash = SecurityHelper.hashPin(pin)))
                        }
                        break
                    }
                }
            }

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

    /**
     * Fingerprint sign-in.
     *
     * Resolves to exactly ONE explicitly-enrolled account. The previous implementation
     * guessed "the first non-admin user" and hard-blocked admins from biometric
     * sign-in entirely; both behaviours are removed. Resolution order is:
     *   1. the caller-supplied username, if that account is enrolled;
     *   2. otherwise the last user to sign in, if enrolled.
     * If neither is enrolled the caller is told to fall back to the PIN pad.
     */
    fun loginWithBiometric(targetUsername: String?, onSuccess: (User) -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            val requested = targetUsername?.takeIf { it.isNotBlank() }
                ?: prefs.getString("last_logged_in_user", null)

            val user = requested?.let { repository.getUserByUsername(it) }

            if (user == null) {
                onFailure("No account found for fingerprint sign-in.")
                return@launch
            }
            if (!user.biometricEnrolled) {
                onFailure("${user.username} has not enrolled a fingerprint. Use your PIN instead.")
                return@launch
            }
            if (user.pinResetRequired) {
                onFailure("${user.username} must set a new ${SecurityHelper.PIN_LENGTH}-digit PIN first.")
                return@launch
            }

            completeLogin(user, onSuccess)
        }
    }

    /**
     * Ends the cashier session.
     *
     * The cart is intentionally *not* cleared here. Under the Zeigarnik Effect an
     * in-progress basket is the thing the user most wants back; a shift change or a
     * kiosk lock should not silently destroy it. The draft continues to be mirrored to
     * disk by the autosave above, so the next sign-in restores it. Callers who genuinely
     * want to discard the basket (successful checkout, explicit "clear cart") already do
     * so through their own code paths.
     */
    fun logout() {
        activeUser.value = null
        CrashDiagnosticsManager.currentCashier = "None"
        restoredCartLineCount = 0
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
