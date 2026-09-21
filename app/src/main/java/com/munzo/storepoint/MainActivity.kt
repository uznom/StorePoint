package com.munzo.storepoint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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

class MainActivity : ComponentActivity() {

    private val viewModel: StorePointViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        android.util.Log.d("MainActivity", "Camera permission outcome: $isGranted")
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
                    var wasKioskActive by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

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
                                    registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
                                } else {
                                    registerReceiver(receiver, filter)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to register shutdown/quick settings dismisser", e)
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

                    // Start/Stop Device LockTask for kiosk mode stability safely
                    androidx.compose.runtime.LaunchedEffect(isKioskActive) {
                        if (isKioskActive) {
                            try {
                                val dpm = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
                                val adminName = android.content.ComponentName(this@MainActivity, StorePointDeviceAdminReceiver::class.java)
                                if (dpm != null) {
                                    val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)
                                    if (isDeviceOwner) {
                                        // Whitelist our app for true lock task mode so we don't show the screen pinning toast
                                        dpm.setLockTaskPackages(adminName, arrayOf(packageName))
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                            dpm.setLockTaskFeatures(adminName, android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
                                        }
                                        android.util.Log.d("MainActivity", "Kiosk: Whitelisting for True Lock Task Mode configured.")
                                    } else {
                                        android.util.Log.d("MainActivity", "Kiosk: Not device owner, falling back to screen pinning behavior.")
                                    }
                                }
                            } catch (e: Throwable) {
                                android.util.Log.e("MainActivity", "Failed setting LOCK_TASK_FEATURE_NONE or setLockTaskPackages", e)
                            }

                            try {
                                startLockTask()
                                wasKioskActive = true
                            } catch (e: Throwable) {
                                android.util.Log.e("MainActivity", "LockTask start failed: falling back to basic Compose suppression", e)
                            }
                        } else {
                            val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                            val isLocked = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                am?.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
                            } else {
                                am?.isInLockTaskMode == true
                            }
                            if (wasKioskActive || isLocked) {
                                try {
                                    stopLockTask()
                                } catch (e: Throwable) {
                                    android.util.Log.e("MainActivity", "LockTask stop failed", e)
                                }
                                wasKioskActive = false
                            }
                        }
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
                                    onLoginSuccess = { role ->
                                        val dest = if (role == "INVENTORY") "inventory_portal" else "cashier_pos"
                                        navController.navigate(dest) {
                                            popUpTo("login") { inclusive = true }
                                        }
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
            reassertKioskLock()
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

    private fun reassertKioskLock() {
        if (!viewModel.isKioskModeActive.value) return
        try {
            val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val isLocked = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                am?.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
            } else {
                @Suppress("DEPRECATION")
                am?.isInLockTaskMode == true
            }
            if (!isLocked) {
                val bringToFront = android.content.Intent(this, MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(bringToFront)
                startLockTask()
                android.util.Log.d("MainActivity", "Kiosk mode re-asserted LockTask lock successfully.")
            }
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Kiosk lock reassert error: ${e.message}")
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (viewModel.isKioskModeActive.value) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_BACK -> {
                    // Suppress back key in kiosk mode to prevent unpinning combination
                    return true
                }
                android.view.KeyEvent.KEYCODE_HOME,
                android.view.KeyEvent.KEYCODE_APP_SWITCH -> {
                    reassertKioskLock()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
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
