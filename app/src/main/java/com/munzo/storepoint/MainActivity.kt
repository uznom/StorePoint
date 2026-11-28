package com.munzo.storepoint

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.munzo.storepoint.data.DatabaseRecoveryManager
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.screens.AboutScreen
import com.munzo.storepoint.ui.screens.AdminDashboardScreen
import com.munzo.storepoint.ui.screens.CashierPOSScreen
import com.munzo.storepoint.ui.screens.DatabaseRecoveryPromptScreen
import com.munzo.storepoint.ui.screens.InventoryPortalScreen
import com.munzo.storepoint.ui.screens.LoginScreen
import com.munzo.storepoint.ui.screens.OnboardingScreen
import com.munzo.storepoint.ui.screens.PinResetScreen
import com.munzo.storepoint.ui.screens.SetupScreen
import com.munzo.storepoint.ui.screens.StartupSafeModeScreen
import com.munzo.storepoint.ui.theme.ExpressiveMorphingLoader
import com.munzo.storepoint.ui.theme.MyApplicationTheme
import com.munzo.storepoint.util.CrashDiagnosticsManager
import com.munzo.storepoint.util.StartupBootGuard

/**
 * StorePoint's single host activity.
 *
 * Extends [FragmentActivity] so `androidx.biometric` can attach its prompt fragment.
 * Uses a fail-safe, multi-stage boot gate:
 *  1. Evaluates [StartupBootGuard] BEFORE instantiating [StorePointViewModel] or Room.
 *  2. Runs [DatabaseRecoveryManager.verifyOrRecoverDatabase] asynchronously off the main thread,
 *     quarantining any corrupted SQLite file and surfacing a backup restore prompt if needed.
 *  3. Routes any fatal startup failure or crash loop directly to [StartupSafeModeScreen].
 */
class MainActivity : FragmentActivity() {

    private sealed class StartupStage {
        data object Preflight : StartupStage()
        data class SafeMode(val evaluation: StartupBootGuard.LaunchEvaluation) : StartupStage()
        data class Ready(
            val viewModel: StorePointViewModel,
            val recoveryPrompt: DatabaseRecoveryManager.DatabaseRecoveryPromptState?
        ) : StartupStage()
    }

    /** Only populated after preflight succeeds — remains null in Safe Mode or Preflight. */
    private var activeViewModel: StorePointViewModel? = null
    private val hidBarcodeBuffer = com.munzo.storepoint.util.HidBarcodeScannerBuffer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Guard window/edge-to-edge configuration so vendor OS quirks never abort cold start.
        try {
            enableEdgeToEdge()
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "enableEdgeToEdge skipped: ${e.message}")
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else {
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
            }
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Failed setting layoutInDisplayCutoutMode: ${e.message}")
        }

        val initialEvaluation = StartupBootGuard.evaluateAndBeginLaunch(applicationContext)

        setContent {
            var bootAttemptNonce by remember { mutableIntStateOf(0) }
            var pendingRecoveryPrompt by remember {
                mutableStateOf<DatabaseRecoveryManager.DatabaseRecoveryPromptState?>(null)
            }
            var startupStage by remember {
                mutableStateOf<StartupStage>(
                    if (initialEvaluation.enterSafeMode) {
                        StartupStage.SafeMode(initialEvaluation)
                    } else {
                        StartupStage.Preflight
                    }
                )
            }

            // Asynchronous off-main-thread database preflight & ViewModel boot boundary.
            LaunchedEffect(bootAttemptNonce, startupStage is StartupStage.Preflight) {
                if (startupStage !is StartupStage.Preflight) return@LaunchedEffect
                try {
                    val autoRecoveryPrompt = DatabaseRecoveryManager.verifyOrRecoverDatabase(applicationContext)
                    val effectivePrompt = pendingRecoveryPrompt ?: autoRecoveryPrompt
                    pendingRecoveryPrompt = null

                    val vm = ViewModelProvider(this@MainActivity)[StorePointViewModel::class.java]
                    activeViewModel = vm
                    startupStage = StartupStage.Ready(
                        viewModel = vm,
                        recoveryPrompt = effectivePrompt
                    )
                } catch (fatal: Throwable) {
                    android.util.Log.e("MainActivity", "Fatal startup error intercepted by boot guard", fatal)
                    CrashDiagnosticsManager.logException(applicationContext, fatal)
                    StartupBootGuard.recordUncaughtCrash(
                        context = applicationContext,
                        threadName = Thread.currentThread().name,
                        throwable = fatal,
                        forceSafeMode = true
                    )
                    activeViewModel = null
                    val eval = StartupBootGuard.evaluateBootSnapshot(
                        StartupBootGuard.readSnapshot(applicationContext)
                    )
                    startupStage = StartupStage.SafeMode(eval)
                }
            }

            when (val stage = startupStage) {
                is StartupStage.SafeMode -> {
                    MaterialTheme {
                        StartupSafeModeScreen(
                            evaluation = stage.evaluation,
                            onRetryNormalLaunch = {
                                activeViewModel = null
                                startupStage = StartupStage.Preflight
                                bootAttemptNonce++
                            },
                            onDatabaseResetComplete = { prompt ->
                                activeViewModel = null
                                pendingRecoveryPrompt = prompt
                                startupStage = StartupStage.Preflight
                                bootAttemptNonce++
                            }
                        )
                    }
                }

                is StartupStage.Preflight -> {
                    MyApplicationTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(44.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Starting StorePoint POS...",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                is StartupStage.Ready -> {
                    var activeRecoveryPrompt by remember(stage) { mutableStateOf(stage.recoveryPrompt) }
                    MyApplicationTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            if (activeRecoveryPrompt != null) {
                                LaunchedEffect(Unit) {
                                    StartupBootGuard.markBootHealthy(applicationContext)
                                }
                                DatabaseRecoveryPromptScreen(
                                    prompt = activeRecoveryPrompt!!,
                                    viewModel = stage.viewModel,
                                    onContinueFresh = { activeRecoveryPrompt = null },
                                    onRestoreSucceeded = { activeRecoveryPrompt = null }
                                )
                            } else {
                                StorePointPrimaryApp(viewModel = stage.viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun StorePointPrimaryApp(viewModel: StorePointViewModel) {
        val isDatabaseReady by viewModel.isDatabaseReady.collectAsState()
        val storeConfig by viewModel.storeConfig.collectAsState()
        val isKioskActive by viewModel.isKioskModeActive.collectAsState()
        val isAlwaysOnEnabled by viewModel.isAlwaysOnEnabled.collectAsState()

        // Mark boot healthy once DB initialization completes and primary Compose UI is live.
        LaunchedEffect(isDatabaseReady) {
            if (isDatabaseReady) {
                StartupBootGuard.markBootHealthy(applicationContext)
            }
        }

        // Predictive back gesture: only intercept while kiosk lockdown is active.
        androidx.activity.compose.PredictiveBackHandler(enabled = isKioskActive) { progress ->
            try {
                progress.collect { }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            }
            throw kotlinx.coroutines.CancellationException(
                "Kiosk lockdown active: back navigation suppressed"
            )
        }

        // Handle Always-On Screen flag
        LaunchedEffect(isAlwaysOnEnabled) {
            try {
                if (isAlwaysOnEnabled) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            } catch (e: Throwable) {
                android.util.Log.w("MainActivity", "Failed updating keep-screen-on flag: ${e.message}")
            }
        }

        // Enforce full-screen immersive mode & keyguard override only when kiosk mode is on
        LaunchedEffect(isKioskActive) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                    setShowWhenLocked(isKioskActive)
                    setTurnScreenOn(isKioskActive)
                    if (isKioskActive) {
                        val keyguardManager = getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                        keyguardManager?.requestDismissKeyguard(this@MainActivity, null)
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.w("MainActivity", "Failed updating keyguard flags: ${e.message}")
            }
            try {
                val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                if (isKioskActive) {
                    windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            } catch (e: Throwable) {
                android.util.Log.w("MainActivity", "Failed updating system bar insets: ${e.message}")
            }
        }

        // Continuous broadcast interceptor for power/screen-sleep cycles while kiosk is active
        DisposableEffect(isKioskActive) {
            if (isKioskActive) {
                val receiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                        if (intent.action == android.content.Intent.ACTION_SCREEN_OFF) {
                            try {
                                val wakeupIntent = android.content.Intent(context, MainActivity::class.java).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                context.startActivity(wakeupIntent)
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to wakeup/relaunch active kiosk screen", e)
                            }
                        }
                    }
                }
                val filter = android.content.IntentFilter().apply {
                    addAction(android.content.Intent.ACTION_SCREEN_OFF)
                }
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        registerReceiver(receiver, filter)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to register screen-off receiver", e)
                }
                onDispose {
                    try {
                        unregisterReceiver(receiver)
                    } catch (e: Exception) {
                        android.util.Log.w("MainActivity", "Unregister receiver ignored: ${e.message}")
                    }
                }
            } else {
                onDispose {}
            }
        }

        LaunchedEffect(isKioskActive) {
            syncKioskLockTask(isKioskActive)
        }

        val navController = rememberNavController()
        val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()

        if (!isDatabaseReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                ExpressiveMorphingLoader(label = "Optimizing StorePoint Database...")
            }
        } else if (!isOnboardingCompleted) {
            OnboardingScreen(viewModel = viewModel)
        } else if (storeConfig == null || !storeConfig!!.setupCompleted) {
            SetupScreen(viewModel = viewModel)
        } else {
            NavHost(
                navController = navController,
                startDestination = "login",
                enterTransition = { fadeIn(animationSpec = tween(220)) },
                exitTransition = { fadeOut(animationSpec = tween(180)) },
                popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                popExitTransition = { fadeOut(animationSpec = tween(180)) },
                modifier = Modifier.fillMaxSize()
            ) {
                composable("login") {
                    val activeUserState by viewModel.activeUser.collectAsState()
                    LaunchedEffect(activeUserState) {
                        if (activeUserState != null) {
                            val dest = if (activeUserState?.role == "INVENTORY") "inventory_portal" else "cashier_pos"
                            navController.navigate(dest) {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    }
                    LoginScreen(
                        viewModel = viewModel,
                        onAboutNavigate = { navController.navigate("about") },
                        onPinResetNavigate = { navController.navigate("pin_reset") },
                        onLoginSuccess = { role ->
                            val dest = if (role == "INVENTORY") "inventory_portal" else "cashier_pos"
                            navController.navigate(dest) {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    )
                }

                composable("pin_reset") {
                    PinResetScreen(
                        viewModel = viewModel,
                        onCompleted = { navController.popBackStack() }
                    )
                }

                composable("about") {
                    AboutScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("cashier_pos") {
                    CashierPOSScreen(
                        viewModel = viewModel,
                        onAdminDashboardNavigate = { navController.navigate("admin_dashboard") },
                        onLogout = {
                            navController.navigate("login") {
                                popUpTo("cashier_pos") { inclusive = true }
                            }
                        }
                    )
                }

                composable("admin_dashboard") {
                    AdminDashboardScreen(
                        viewModel = viewModel,
                        onBackToPOS = {
                            navController.navigate("cashier_pos") {
                                popUpTo("admin_dashboard") { inclusive = true }
                            }
                        },
                        onNavigateToInventoryPortal = { navController.navigate("inventory_portal") }
                    )
                }

                composable("inventory_portal") {
                    InventoryPortalScreen(
                        viewModel = viewModel,
                        onLogout = {
                            viewModel.logout()
                            navController.navigate("login") {
                                popUpTo("inventory_portal") { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val vm = activeViewModel ?: return
        if (vm.isKioskModeActive.value) {
            hideSystemBars()
            if (!isOsLockTaskActive() || !lockTaskEngaged) {
                syncKioskLockTask(true)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val vm = activeViewModel ?: return
        if (vm.isKioskModeActive.value) {
            reassertKioskLock()
        }
    }

    private fun hideSystemBars() {
        try {
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "hideSystemBars error: ${e.message}")
        }
    }

    private var lastKioskReassertAt = 0L

    private fun reassertKioskLock() {
        val vm = activeViewModel ?: return
        if (!vm.isKioskModeActive.value) return

        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastKioskReassertAt < KIOSK_REASSERT_DEBOUNCE_MS) return
        lastKioskReassertAt = now

        if (isOsLockTaskActive() && lockTaskEngaged) return

        try {
            val bringToFront = android.content.Intent(this, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(bringToFront)
            syncKioskLockTask(true)
            android.util.Log.d("MainActivity", "Kiosk mode re-asserted LockTask lock successfully.")
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Kiosk lock reassert error: ${e.message}")
        }
    }

    private companion object {
        const val KIOSK_REASSERT_DEBOUNCE_MS = 1_500L
    }

    fun releaseKioskLockTask() {
        activeViewModel?.toggleKioskMode(false)
        syncKioskLockTask(false)
    }

    private var lockTaskEngaged = false

    private fun isOsLockTaskActive(): Boolean {
        val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            ?: return false
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            am.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            @Suppress("DEPRECATION")
            am.isInLockTaskMode
        }
    }

    private fun syncKioskLockTask(kioskActive: Boolean): LockdownStrength {
        val vm = activeViewModel
        if (!kioskActive) {
            if (lockTaskEngaged || isOsLockTaskActive()) {
                try {
                    stopLockTask()
                    android.util.Log.i("MainActivity", "Kiosk lockdown released.")
                } catch (e: Throwable) {
                    android.util.Log.e("MainActivity", "LockTask stop failed", e)
                }
            }
            lockTaskEngaged = false
            vm?.isKioskLockdownDegraded?.value = false
            return StorePointDeviceAdminReceiver.isDeviceOwner(this)
                .let { if (it) LockdownStrength.DeviceOwnerEnforced else LockdownStrength.ScreenPinningOnly }
        }

        var degraded = false
        try {
            val dpm = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
            val adminName = android.content.ComponentName(this, StorePointDeviceAdminReceiver::class.java)
            if (dpm != null && dpm.isDeviceOwnerApp(packageName)) {
                dpm.setLockTaskPackages(adminName, arrayOf(packageName))
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    dpm.setLockTaskFeatures(adminName, android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
                }
            } else {
                android.util.Log.w("MainActivity", "Kiosk: NOT device owner - screen pinning only.")
                degraded = true
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Failed applying lock-task policy", e)
            degraded = true
        }

        try {
            startLockTask()
            lockTaskEngaged = true
            if (degraded) {
                Toast.makeText(
                    this,
                    "Kiosk entered in REDUCED mode: this terminal is not provisioned as device " +
                        "owner, so lockdown is dismissable screen pinning only. See Admin > Security.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "LockTask start failed", e)
            lockTaskEngaged = false
            degraded = true
        }
        val strength = if (degraded) LockdownStrength.ScreenPinningOnly else LockdownStrength.DeviceOwnerEnforced
        vm?.isKioskLockdownDegraded?.value = (strength == LockdownStrength.ScreenPinningOnly)
        return strength
    }

    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val vm = activeViewModel ?: return super.dispatchKeyEvent(event)
        // Global USB/Bluetooth HID hardware barcode scanner capture.
        if (event.action == android.view.KeyEvent.ACTION_DOWN && !vm.hidScannerSuppressed.value) {
            val isTerminator = event.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                event.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER ||
                event.keyCode == android.view.KeyEvent.KEYCODE_TAB
            if (isTerminator) {
                val scanned = hidBarcodeBuffer.onTerminator(event.eventTime)
                if (scanned != null) {
                    vm.emitHidBarcodeScan(scanned)
                    return true
                }
            } else if (!event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) {
                val unicode = event.unicodeChar
                if (unicode > 0) {
                    hidBarcodeBuffer.onPrintableChar(unicode.toChar(), event.eventTime)
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val vm = activeViewModel ?: return
        if (hasFocus) {
            if (vm.isKioskModeActive.value) {
                hideSystemBars()
                reassertKioskLock()
            }
        } else {
            if (vm.isKioskModeActive.value) {
                reassertKioskLock()
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                    try {
                        @Suppress("DEPRECATION")
                        val closeDialog = android.content.Intent(android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                        sendBroadcast(closeDialog)
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Failed sending close system dialogs intent", e)
                    }

                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            @Suppress("DEPRECATION")
                            val closeDialog = android.content.Intent(android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                            sendBroadcast(closeDialog)
                        } catch (e: Exception) {
                            android.util.Log.w("MainActivity", "Close system dialogs delayed broadcast ignored: ${e.message}")
                        }
                    }, 300)
                }
            }
        }
    }
}
