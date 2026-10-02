package com.munzo.storepoint

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.screens.AboutScreen
import com.munzo.storepoint.ui.screens.AdminDashboardScreen
import com.munzo.storepoint.ui.screens.CashierPOSScreen
import com.munzo.storepoint.ui.screens.InventoryPortalScreen
import com.munzo.storepoint.ui.screens.LoginScreen
import com.munzo.storepoint.ui.screens.SetupScreen
import com.munzo.storepoint.ui.theme.ExpressiveMorphingLoader
import com.munzo.storepoint.ui.theme.MyApplicationTheme

/**
 * StorePoint's single host activity.
 *
 * Extends [FragmentActivity] (a [ComponentActivity] subclass) purely so the
 * `androidx.biometric` prompt can attach its fragment — fingerprint-first sign-in is
 * the primary auth path. All other behaviour is unchanged.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: StorePointViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        android.util.Log.d("MainActivity", "Camera permission outcome: $isGranted")
    }

    private val requestBluetoothPermissionsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        android.util.Log.d("MainActivity", "Bluetooth permissions outcome: $permissions")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup Window attributes to show over keyguard/lockscreen & turn screen on dynamically for kiosk stability
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                val keyguardManager = getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                keyguardManager?.requestDismissKeyguard(this, null)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed setting window keyguard attributes", e)
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed setting layoutInDisplayCutoutMode: ${e.message}")
        }

        enableEdgeToEdge()

        // Ask for CAMERA permission the first time the app starts safely
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed launching camera permission request", e)
        }

        // Check BLUETOOTH permissions on Android 12+ (API 31+) for thermal printers like XP-58 Plus
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val needed = mutableListOf<String>()
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    needed.add(android.Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.BLUETOOTH_SCAN
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    needed.add(android.Manifest.permission.BLUETOOTH_SCAN)
                }
                if (needed.isNotEmpty()) {
                    requestBluetoothPermissionsLauncher.launch(needed.toTypedArray())
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed launching bluetooth permission request", e)
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isDatabaseReady by viewModel.isDatabaseReady.collectAsState()
                    val storeConfig by viewModel.storeConfig.collectAsState()
                    val activeUser by viewModel.activeUser.collectAsState()
                    val isKioskActive by viewModel.isKioskModeActive.collectAsState()
                    val isAlwaysOnEnabled by viewModel.isAlwaysOnEnabled.collectAsState()
                    // SECURITY (issue #4): true when lockdown is NOT genuinely enforced
                    // (terminal is not provisioned as device owner, or the policy calls
                    // failed). Drives the REDUCED-mode warning so a cash register is never
                    // presented as secured when it is only screen-pinned.
                    var degradedKioskLockdown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                    // --- Predictive back gesture ---
                    // Predictive back is enabled app-wide through
                    // android:enableOnBackInvokedCallback in the manifest, and the NavHost below
                    // animates the predictive back transition on its own. This handler only takes
                    // over while kiosk lockdown is active, so the back gesture cannot escape the
                    // locked terminal. Cancelling the gesture makes the system play its
                    // "snap back" animation instead of navigating away from the POS screen.
                    androidx.activity.compose.PredictiveBackHandler(enabled = isKioskActive) { progress ->
                        try {
                            // Track the gesture; preview no navigation while locked down.
                            progress.collect { }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            // The user abandoned the gesture, so keep the current screen.
                            throw cancelled
                        }
                        // The gesture would have completed: suppress it to preserve device lockdown.
                        throw kotlinx.coroutines.CancellationException(
                            "Kiosk lockdown active: back navigation suppressed"
                        )
                    }

                    // Handle Always-On Screen flag
                    androidx.compose.runtime.LaunchedEffect(isAlwaysOnEnabled) {
                        if (isAlwaysOnEnabled) {
                            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }

                    // Enforce full-screen immersive mode (hiding navigation bar) when kiosk mode is on
                    androidx.compose.runtime.LaunchedEffect(isKioskActive) {
                        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                        if (isKioskActive) {
                            windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                            windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        } else {
                            windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        }
                    }

                    // Continuous broadcast interceptor to handle system events when kiosk is active, such as power/screen-sleep cycles
                    androidx.compose.runtime.DisposableEffect(isKioskActive) {
                        if (isKioskActive) {
                            val receiver = object : android.content.BroadcastReceiver() {
                                override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                                    val action = intent.action
                                    if (action == android.content.Intent.ACTION_SCREEN_OFF) {
                                        // User pressed power button to go to sleep; wake up immediately to prevent lockscreen bypass!
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
                                    // SECURITY (issue #8): must be NOT_EXPORTED.
                                    // ACTION_SCREEN_OFF is a protected system broadcast that
                                    // only the system can send, so there is no functional
                                    // reason to export this receiver. Exported, any app on
                                    // the terminal could spam it to repeatedly relaunch
                                    // StorePoint in the foreground - and the receiver is
                                    // only registered while kiosk lockdown is active, i.e.
                                    // exactly when the device is supposed to be secured.
                                    // This was the only RECEIVER_EXPORTED in the codebase;
                                    // the manifest and BootReceiver were already tightened
                                    // for the same class of spoofing risk (audit M1/M2).
                                    registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
                                } else {
                                    // Pre-33 default for a system-only broadcast is already
                                    // effectively private; nothing to opt into.
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

                    // SECURITY (issue #7): lock-task is now owned by the Activity, not by
                    // a Compose effect. The effect only expressed intent; the actual
                    // enter/exit/reconcile lifecycle lives in syncKioskLockTask(), which
                    // the Activity also calls from onResume/onStop. Compose state
                    // (`wasKioskActive`) was lost on configuration change while the OS
                    // stayed locked, leaving the two permanently out of step.
                    androidx.compose.runtime.LaunchedEffect(isKioskActive) {
                        syncKioskLockTask(isKioskActive)
                    }

                    val navController = rememberNavController()
                    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()

                    if (!isDatabaseReady) {
                        // Database state loading layout
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            ExpressiveMorphingLoader(label = "Optimizing StorePoint Database...")
                        }
                    } else if (!isOnboardingCompleted) {
                        // Redirect to onboarding Screens
                        com.munzo.storepoint.ui.screens.OnboardingScreen(viewModel = viewModel)
                    } else if (storeConfig == null || !storeConfig!!.setupCompleted) {
                        // Redirect to setup wizard screen
                        SetupScreen(viewModel = viewModel)
                    } else {
                        // Standard Router flow
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
                                androidx.compose.runtime.LaunchedEffect(activeUserState) {
                                    if (activeUserState != null) {
                                        val dest = if (activeUserState?.role == "INVENTORY") "inventory_portal" else "cashier_pos"
                                        navController.navigate(dest) {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                }
                                LoginScreen(
                                    viewModel = viewModel,
                                    onAboutNavigate = {
                                        navController.navigate("about")
                                    },
                                    onPinResetNavigate = {
                                        navController.navigate("pin_reset")
                                    },
                                    onLoginSuccess = { role ->
                                        val dest = if (role == "INVENTORY") "inventory_portal" else "cashier_pos"
                                        navController.navigate(dest) {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // One-time 4-digit PIN migration. Reachable only for
                            // accounts flagged `pinResetRequired`, and only after the
                            // user proves ownership with their existing credential.
                            composable("pin_reset") {
                                com.munzo.storepoint.ui.screens.PinResetScreen(
                                    viewModel = viewModel,
                                    onCompleted = {
                                        navController.popBackStack()
                                    }
                                )
                            }

                            composable("about") {
                                AboutScreen(
                                    viewModel = viewModel,
                                    onBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }

                            composable("cashier_pos") {
                                CashierPOSScreen(
                                    viewModel = viewModel,
                                    onAdminDashboardNavigate = {
                                        navController.navigate("admin_dashboard")
                                    },
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
                                    onNavigateToInventoryPortal = {
                                        navController.navigate("inventory_portal")
                                    }
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
                                    onBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.isKioskModeActive.value) {
            hideSystemBars()
            // SECURITY (issue #7): reconcile the OS lock state on every resume. The OS can
            // drop lock-task across process death or a system-initiated restart, and this
            // is the reliable point to re-assert it. Previously this only called the
            // debounced re-assert, so a genuine OS-side drop could survive.
            if (!isOsLockTaskActive() || !lockTaskEngaged) {
                syncKioskLockTask(true)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (viewModel.isKioskModeActive.value) {
            // User attempted to leave activity (e.g. unpin combination or Home) - immediately reassert
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

    /** Timestamp of the last re-assert, used to debounce focus churn. */
    private var lastKioskReassertAt = 0L

    /**
     * Re-asserts lock-task if the OS has dropped it.
     *
     * SECURITY (issue #5/#7): this previously ran an unguarded `startActivity(this)` +
     * `startLockTask()` on every focus change, every leave-hint, and every Home key
     * event. Focus oscillates during ordinary use (dialogs, the shade, rotation), so
     * that could stack into repeated activity relaunches. It is now debounced and
     * routes through the single lock-task owner.
     */
    private fun reassertKioskLock() {
        if (!viewModel.isKioskModeActive.value) return

        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastKioskReassertAt < KIOSK_REASSERT_DEBOUNCE_MS) return
        lastKioskReassertAt = now

        // Nothing to do if the OS still has us locked; avoid a needless relaunch.
        if (isOsLockTaskActive() && lockTaskEngaged) return

        try {
            val bringToFront = android.content.Intent(this, MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(bringToFront)
            // Single owner: do not call startLockTask() directly here.
            syncKioskLockTask(true)
            android.util.Log.d("MainActivity", "Kiosk mode re-asserted LockTask lock successfully.")
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Kiosk lock reassert error: ${e.message}")
        }
    }

    private companion object {
        /** Minimum gap between lock-task re-asserts, to stop focus churn causing a loop. */
        const val KIOSK_REASSERT_DEBOUNCE_MS = 1_500L
    }

    /**
     * Releases kiosk lockdown from outside the lock-task state machine.
     *
     * SECURITY (issue #7): the status bar's emergency-unlock gesture used to call
     * `stopLockTask()` on the Activity directly, bypassing the lock-task owner and
     * leaving its internal state stale. Routing it through the owner keeps the app and
     * the OS in step. Public so the gesture can reach it, but the transition itself
     * remains owned here.
     */
    fun releaseKioskLockTask() {
        viewModel.toggleKioskMode(false)
        syncKioskLockTask(false)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        // SECURITY (issue #5): the previous implementation swallowed KEYCODE_HOME and
        // KEYCODE_APP_SWITCH to prevent leaving the app. Those keys are not delivered
        // to applications on modern Android, so both branches were unreachable - dead
        // code that gave a false impression of defence in depth. Real enforcement comes
        // from LockTask / device-owner policy (issue #4), and is asserted in
        // onWindowFocusChanged below. Predictive back is separately suppressed in
        // setContent via PredictiveBackHandler.
        if (viewModel.isKioskModeActive.value) {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                // Suppress back in kiosk mode to prevent navigating out of the register.
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // --- Lock-task ownership (issue #7) ---

    /**
     * True when we have asked the OS to engage lock-task and have not been told it
     * stopped. Lives on the Activity, not in Compose `remember`, so it survives
     * configuration change and low-memory process recreation.
     */
    private var lockTaskEngaged = false

    /** Reads the OS lock-task state, which is the source of truth. */
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

    /**
     * Reconciles our lock-task state with the OS, entering or exiting lockdown.
     *
     * SECURITY (issue #7): this is the single owner of the lock-task lifecycle. It was
     * previously a Compose `LaunchedEffect` keyed on `isKioskActive`, so the transition
     * was coupled to composition: a cancelled effect, an activity recreation, or a
     * second caller (`KioskStatusBar` called `stopLockTask()` directly) could leave the
     * app and the OS permanently out of step. It is now driven from the Activity and
     * called on state change *and* from onResume, so the OS state is re-asserted after
     * anything that could have dropped it.
     *
     * @return the resulting lockdown strength.
     */
    private fun syncKioskLockTask(kioskActive: Boolean): LockdownStrength {
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
            return StorePointDeviceAdminReceiver.isDeviceOwner(this)
                .let { if (it) LockdownStrength.DeviceOwnerEnforced else LockdownStrength.ScreenPinningOnly }
        }

        // Entering lockdown.
        var degraded = false
        try {
            val dpm = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
            val adminName = android.content.ComponentName(this, StorePointDeviceAdminReceiver::class.java)
            if (dpm != null && dpm.isDeviceOwnerApp(packageName)) {
                // Whitelist for true lock task so the OS shows no pinning toast.
                dpm.setLockTaskPackages(adminName, arrayOf(packageName))
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    dpm.setLockTaskFeatures(adminName, android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
                }
            } else {
                // SECURITY (issue #4): not being device owner means lockdown is only
                // dismissable screen pinning. Record it so the owner is told.
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
                // Fail loudly: a cash register must never look secured when it is not.
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
        return if (degraded) LockdownStrength.ScreenPinningOnly else LockdownStrength.DeviceOwnerEnforced
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (viewModel.isKioskModeActive.value) {
            if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK && (event.isLongPress || event.repeatCount > 0)) {
                // Completely block long-press back trigger
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (viewModel.isKioskModeActive.value) {
                hideSystemBars()
                reassertKioskLock()
            }
        } else {
            if (viewModel.isKioskModeActive.value) {
                reassertKioskLock()
                // If focus is lost because some system overlay (e.g. power button long press or quick settings shade) appeared,
                // dismiss it immediately on pre-Android 12 devices. On Android 12+ (API 31+), ACTION_CLOSE_SYSTEM_DIALOGS
                // is restricted to system processes; skip to prevent SecurityException (audit H6).
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
