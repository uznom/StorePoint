package com.munzo.storepoint.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munzo.storepoint.ui.theme.ExpressiveOtpPinInput
import com.munzo.storepoint.ui.theme.tactileBounce
import kotlinx.coroutines.delay

// Helper extension to find the Activity from a Context
private fun Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskStatusBar(
    viewModel: StorePointViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storeConfig by viewModel.storeConfig.collectAsState()
    val activeUser by viewModel.activeUser.collectAsState()
    val isAdmin = activeUser?.role.equals("ADMIN", ignoreCase = true)

    var timeString by remember { mutableStateOf("") }
    var batteryPercent by remember { mutableStateOf(100) }
    var isBatteryCharging by remember { mutableStateOf(false) }

    // Connectivity status states
    var isWifiConnected by remember { mutableStateOf(false) }
    var isWifiEnabled by remember { mutableStateOf(false) }
    var isBluetoothEnabled by remember { mutableStateOf(false) }

    // Emergency Kiosk Unlock (tap 5x)
    var unlockTapCount by remember { mutableStateOf(0) }
    var showSecretUnlockDialog by remember { mutableStateOf(false) }
    var unlockInput by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf("") }

    // Admin PIN Authorization for Wi-Fi / Bluetooth toggles
    var showAdminAuthDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var adminAuthPin by remember { mutableStateOf("") }
    var adminAuthError by remember { mutableStateOf("") }
    var targetActionName by remember { mutableStateOf("") }

    // Reset tap count after 3 seconds of inactivity
    LaunchedEffect(unlockTapCount) {
        if (unlockTapCount > 0) {
            delay(3000L)
            unlockTapCount = 0
        }
    }

    // Dynamic Time, Battery, and Network status periodic updater
    LaunchedEffect(Unit) {
        while (true) {
            val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            timeString = sdf.format(java.util.Date())

            // Battery query
            try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                if (bm != null) {
                    val capacity = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    if (capacity > 0) {
                        batteryPercent = capacity.coerceIn(0, 100)
                    }
                    val status = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS)
                    isBatteryCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == android.os.BatteryManager.BATTERY_STATUS_FULL
                }
            } catch (e: Exception) {
                android.util.Log.w("KioskStatusBar", "Failed reading battery status: ${e.message}")
            }

            // Wi-Fi query
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNet = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(activeNet)
                val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                isWifiConnected = wifiConnected && hasInternet

                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                isWifiEnabled = wm?.isWifiEnabled ?: isWifiConnected
            } catch (e: Exception) {
                android.util.Log.w("KioskStatusBar", "Failed reading Wi-Fi status: ${e.message}")
            }

            // Bluetooth query
            try {
                val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                isBluetoothEnabled = adapter?.isEnabled == true
            } catch (e: Exception) {
                android.util.Log.w("KioskStatusBar", "Failed reading Bluetooth status: ${e.message}")
            }

            delay(4000L) // Refresh every 4 seconds
        }
    }

    // Handler for admin protected actions
    fun executeAdminProtectedAction(actionTitle: String, action: () -> Unit) {
        if (isAdmin) {
            action()
        } else {
            targetActionName = actionTitle
            pendingAction = action
            adminAuthPin = ""
            adminAuthError = ""
            showAdminAuthDialog = true
        }
    }

    // Launch Wi-Fi panel / settings
    fun launchWifiAction() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val intent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {
                Toast.makeText(context, "Could not launch Wi-Fi settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Launch Bluetooth toggle / settings
    fun launchBluetoothAction() {
        try {
            if (!isBluetoothEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(enableBtIntent)
            } else {
                val btSettingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(btSettingsIntent)
            }
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {
                Toast.makeText(context, "Could not launch Bluetooth settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- ADMIN AUTH PIN DIALOG (For toggling Wi-Fi / Bluetooth when not signed in as Admin) ---
    if (showAdminAuthDialog) {
        AlertDialog(
            onDismissRequest = {
                showAdminAuthDialog = false
                adminAuthPin = ""
                adminAuthError = ""
                pendingAction = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Admin Authorization Required", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Modifying $targetActionName is restricted to terminal administrators. Enter the 6-Digit Admin PIN to proceed.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    ExpressiveOtpPinInput(
                        pin = adminAuthPin,
                        onPinChange = {
                            adminAuthPin = it
                            adminAuthError = ""
                        },
                        pinLength = 6,
                        isMasked = true,
                        isError = adminAuthError.isNotEmpty(),
                        errorMessage = adminAuthError,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.verifyKioskUnlock(
                            pin = adminAuthPin,
                            onSuccess = {
                                showAdminAuthDialog = false
                                val action = pendingAction
                                pendingAction = null
                                adminAuthPin = ""
                                adminAuthError = ""
                                action?.invoke()
                            },
                            onFailure = { err ->
                                adminAuthError = err
                            }
                        )
                    }
                ) {
                    Text("Authorize")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAdminAuthDialog = false
                        adminAuthPin = ""
                        adminAuthError = ""
                        pendingAction = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- EMERGENCY KIOSK UNLOCK DIALOG (5 Taps on Lock Badge) ---
    if (showSecretUnlockDialog) {
        AlertDialog(
            onDismissRequest = {
                showSecretUnlockDialog = false
                unlockInput = ""
                unlockError = ""
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Kiosk Emergency Unlock", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter the Admin 6-Digit PIN to immediately stop Lock Task Mode and return to normal operation.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    ExpressiveOtpPinInput(
                        pin = unlockInput,
                        onPinChange = {
                            unlockInput = it
                            unlockError = ""
                        },
                        pinLength = 6,
                        isMasked = true,
                        isError = unlockError.isNotEmpty(),
                        errorMessage = unlockError,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.verifyKioskUnlock(
                            pin = unlockInput,
                            onSuccess = {
                                viewModel.toggleKioskMode(false)
                                showSecretUnlockDialog = false
                                val activity = context.findActivity()
                                if (activity != null) {
                                    try {
                                        activity.stopLockTask()
                                        Toast.makeText(context, "Kiosk Lockdown Mode successfully disabled by Admin.", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Kiosk Mode disabled (stopLockTask failed: ${e.localizedMessage})", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Kiosk Mode disabled in ViewModel.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onFailure = { err ->
                                unlockError = err
                            }
                        )
                    }
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSecretUnlockDialog = false
                    unlockInput = ""
                    unlockError = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    val topCutout = WindowInsets.displayCutout.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    val topStatus = WindowInsets.statusBars.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    val topSafePadding = maxOf(topCutout, topStatus)

    // --- 1:1 MATERIAL 3 EXPRESSIVE STATUS BAR (GOOGLE PIXEL STYLE) ---
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topSafePadding)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                val isCompactBar = maxWidth < 580.dp

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT: Pixel-Style Clock + Emergency Kiosk Unlock Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pixel Clock Pill
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = timeString.ifBlank { "--:--" },
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.5.sp,
                                        letterSpacing = 0.3.sp
                                    )
                                )
                            }
                        }

                        // Pixel Kiosk Lockdown Pill (Tap 5x for Emergency Unlock)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .clip(CircleShape)
                                .tactileBounce()
                                .clickable {
                                    unlockTapCount++
                                    if (unlockTapCount >= 5) {
                                        unlockTapCount = 0
                                        showSecretUnlockDialog = true
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lockdown active",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (isCompactBar) "KIOSK" else "KIOSK LOCKED",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                            }
                        }
                    }

                    // CENTER: Dynamic Terminal / Store Badge (Hidden on very narrow mobile screens)
                    if (!isCompactBar) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = storeConfig?.storeName?.ifBlank { "StorePoint POS Terminal" } ?: "StorePoint POS Terminal",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    // RIGHT: Pixel Material 3 Expressive Quick-Settings Style Pills (Wi-Fi, Bluetooth, Battery)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1) WI-FI PILL TOGGLE (Admin Only)
                        val wifiContainerColor by animateColorAsState(
                            targetValue = if (isWifiConnected || isWifiEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                            },
                            animationSpec = tween(250)
                        )
                        val wifiContentColor by animateColorAsState(
                            targetValue = if (isWifiConnected || isWifiEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(250)
                        )

                        Surface(
                            shape = CircleShape,
                            color = wifiContainerColor,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(CircleShape)
                                .tactileBounce()
                                .clickable {
                                    executeAdminProtectedAction("Wi-Fi Settings") {
                                        launchWifiAction()
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isWifiConnected || isWifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
                                    contentDescription = "Wi-Fi Toggle (Admin Only)",
                                    tint = wifiContentColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                if (!isCompactBar) {
                                    Text(
                                        text = if (isWifiConnected) "Wi-Fi" else if (isWifiEnabled) "Searching" else "Off",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.3.sp
                                        ),
                                        color = wifiContentColor
                                    )
                                }
                            }
                        }

                        // 2) BLUETOOTH PILL TOGGLE (Admin Only)
                        val btContainerColor by animateColorAsState(
                            targetValue = if (isBluetoothEnabled) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                            },
                            animationSpec = tween(250)
                        )
                        val btContentColor by animateColorAsState(
                            targetValue = if (isBluetoothEnabled) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(250)
                        )

                        Surface(
                            shape = CircleShape,
                            color = btContainerColor,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(CircleShape)
                                .tactileBounce()
                                .clickable {
                                    executeAdminProtectedAction("Bluetooth Settings") {
                                        launchBluetoothAction()
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isBluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                                    contentDescription = "Bluetooth Toggle (Admin Only)",
                                    tint = btContentColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                if (!isCompactBar) {
                                    Text(
                                        text = if (isBluetoothEnabled) "BT On" else "BT Off",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.3.sp
                                        ),
                                        color = btContentColor
                                    )
                                }
                            }
                        }

                        // 3) PIXEL BATTERY PILL
                        val isLowBattery = batteryPercent <= 20
                        val batteryTint = when {
                            isLowBattery -> MaterialTheme.colorScheme.error
                            isBatteryCharging -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val batteryContainerColor = when {
                            isLowBattery -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                            isBatteryCharging -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                        }

                        Surface(
                            shape = CircleShape,
                            color = batteryContainerColor,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = when {
                                        isBatteryCharging -> Icons.Default.BatteryChargingFull
                                        batteryPercent > 80 -> Icons.Default.BatteryFull
                                        batteryPercent > 20 -> Icons.Default.BatteryChargingFull
                                        else -> Icons.Default.BatteryAlert
                                    },
                                    contentDescription = null,
                                    tint = batteryTint,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "$batteryPercent%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    ),
                                    color = batteryTint
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                thickness = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }
}

