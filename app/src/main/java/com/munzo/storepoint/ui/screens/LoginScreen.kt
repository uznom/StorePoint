package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.data.User
import com.munzo.storepoint.ui.layout.rememberWindowLayout
import com.munzo.storepoint.ui.layout.WindowLayout
import com.munzo.storepoint.ui.theme.ExpressiveButtonShape
import com.munzo.storepoint.ui.theme.ExpressiveCardShape
import com.munzo.storepoint.ui.theme.tactileBounce
import com.munzo.storepoint.ui.theme.glassPanel
import com.munzo.storepoint.ui.theme.ExpressiveOtpPinInput
import com.munzo.storepoint.ui.components.ExpressiveAction
import com.munzo.storepoint.ui.components.ExpressiveActionRow
import com.munzo.storepoint.ui.components.ExpressiveButton
import com.munzo.storepoint.ui.components.ExpressiveButtonSize
import com.munzo.storepoint.ui.components.ExpressiveButtonVariant

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    viewModel: StorePointViewModel,
    onAboutNavigate: () -> Unit,
    onLoginSuccess: (String) -> Unit // returns role: "ADMIN" or "CASHIER"
) {
    val context = LocalContext.current
    val storeConfig by viewModel.storeConfig.collectAsState()
    val users by viewModel.allUsers.collectAsState(initial = emptyList())

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isAuthenticating by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    // Tap counter on the SP logo
    var logoTapCount by remember { mutableStateOf(0) }
    var showLogoUnlockDialog by remember { mutableStateOf(false) }
    var logoUnlockInput by remember { mutableStateOf("") }
    var logoUnlockError by remember { mutableStateOf("") }

    // Support barcode scan simulation automatically
    if (showScanner) {
        com.munzo.storepoint.ui.BarcodeScannerDialog(
            products = emptyList(),
            onBarcodeScanned = { scannedBarcode ->
                showScanner = false
                isAuthenticating = true
                viewModel.loginWithBarcode(
                    barcodeId = scannedBarcode,
                    onSuccess = { user ->
                        isAuthenticating = false
                        Toast.makeText(context, "Logged in via Badge: Welcome ${user.username}!", Toast.LENGTH_SHORT).show()
                        onLoginSuccess(user.role)
                    },
                    onFailure = { errorMsg ->
                        isAuthenticating = false
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onDismiss = { showScanner = false }
        )
    }

    if (showLogoUnlockDialog) {
        AlertDialog(
            onDismissRequest = {
                showLogoUnlockDialog = false
                logoUnlockInput = ""
                logoUnlockError = ""
            },
            title = { Text("Kiosk Emergency Unlock", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Enter the Admin 6-Digit PIN to immediately stop Lock Task Mode and return to normal operation.")
                    Spacer(Modifier.height(4.dp))
                    ExpressiveOtpPinInput(
                        pin = logoUnlockInput,
                        onPinChange = { logoUnlockInput = it },
                        pinLength = 6,
                        isMasked = true,
                        isError = logoUnlockError.isNotEmpty(),
                        errorMessage = logoUnlockError,
                        modifier = Modifier.fillMaxWidth().testTag("kiosk_unlock_pin_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.verifyKioskUnlock(
                            pin = logoUnlockInput,
                            onSuccess = {
                                viewModel.toggleKioskMode(false)
                                showLogoUnlockDialog = false
                                
                                var actContext = context
                                var activity: android.app.Activity? = null
                                while (actContext is android.content.ContextWrapper) {
                                    if (actContext is android.app.Activity) {
                                        activity = actContext
                                        break
                                    }
                                    actContext = actContext.baseContext
                                }

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
                                logoUnlockError = err
                            }
                        )
                    }
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLogoUnlockDialog = false
                    logoUnlockInput = ""
                    logoUnlockError = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    val windowLayout = rememberWindowLayout()
    val isCompact = windowLayout == WindowLayout.Compact
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val lastUser by viewModel.lastLoggedInUser.collectAsState()
    val activity = LocalContext.current as? android.app.Activity
    val isBiometricAvailable = remember(context) {
        com.munzo.storepoint.util.BiometricAuthHelper.isBiometricSupported(context)
    }



    Scaffold(
        modifier = Modifier.imePadding()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = if (isCompact) 16.dp else 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LaunchedEffect(logoTapCount) {
                    if (logoTapCount > 0) {
                        kotlinx.coroutines.delay(3000L)
                        logoTapCount = 0
                    }
                }

                // Google-style SP App Logo with subtle elevation and tactile bounce
                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .tactileBounce()
                        .clickable {
                            logoTapCount++
                            if (logoTapCount >= 5) {
                                logoTapCount = 0
                                showLogoUnlockDialog = true
                            }
                        },
                    shape = ExpressiveButtonShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "SP",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            ),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = storeConfig?.storeName ?: "StorePoint POS",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "Secure Terminal Access",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                // --- SCAN BADGE HIGHLIGHT CARD ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    shape = ExpressiveCardShape
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Scan ID Badge for Instant Login",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Simply tap below to launch the camera-based scanner and scan your barcode ID badge.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                        ExpressiveButton(
                            onClick = { showScanner = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_badge_login_button"),
                            label = "Tap to Scan Badge",
                            icon = Icons.Default.QrCodeScanner,
                            variant = ExpressiveButtonVariant.FILLED,
                            size = ExpressiveButtonSize.L,
                        )
                    }
                }

                // Divider OR label
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        "OR ENTER 6-DIGIT PIN",
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }

                // --- MANUAL LOGIN CARD ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    shape = ExpressiveCardShape
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Account Username") },
                            leadingIcon = { Icon(Icons.Default.AccountCircle, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("username_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "6-Digit Security PIN",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ExpressiveOtpPinInput(
                                pin = password,
                                onPinChange = { password = it },
                                pinLength = 6,
                                isMasked = true,
                                modifier = Modifier.fillMaxWidth().testTag("password_input")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (password.length == 6) "✓ 6-digit security PIN ready" else "${password.length}/6 digits (numbers only)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (password.length == 6) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }

                        ExpressiveButton(
                            onClick = {
                                if (username.isBlank() || password.length != 6) {
                                    Toast.makeText(context, "Please enter your username and full 6-digit security PIN.", Toast.LENGTH_SHORT).show()
                                    return@ExpressiveButton
                                }
                                isAuthenticating = true
                                viewModel.login(
                                    username = username.trim(),
                                    pass = password,
                                    onSuccess = { user ->
                                        isAuthenticating = false
                                        onLoginSuccess(user.role)
                                    },
                                    onFailure = { errorMsg ->
                                        isAuthenticating = false
                                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("submit_login_button"),
                            label = if (isAuthenticating) "Authorizing..." else "Sign In",
                            icon = Icons.Default.Login,
                            variant = ExpressiveButtonVariant.FILLED,
                            size = ExpressiveButtonSize.L,
                            enabled = !isAuthenticating,
                        )

                    }
                }

                // Secondary actions: side-by-side on tablets/desktop, stacked full-width on phones.
                val secondaryActions = buildList {
                    if (isBiometricEnabled && isBiometricAvailable && activity != null) {
                        add(
                            ExpressiveAction(
                                label = "Biometric Unlock",
                                icon = Icons.Default.Fingerprint,
                                variant = ExpressiveButtonVariant.TONAL,
                                size = ExpressiveButtonSize.M,
                                testTag = "biometric_login_button",
                                onClick = {
                                    com.munzo.storepoint.util.BiometricAuthHelper.authenticate(
                                        activity = activity,
                                        title = "StorePoint Biometric Sign-In",
                                        subtitle = "Scan fingerprint to unlock register",
                                        negativeButtonText = "Use PIN",
                                        onSuccess = {
                                            val target = if (username.isNotBlank()) username.trim() else lastUser
                                            viewModel.loginWithBiometric(
                                                targetUsername = target,
                                                onSuccess = { user ->
                                                    Toast.makeText(context, "Biometric Verified: Welcome ${user.username}!", Toast.LENGTH_SHORT).show()
                                                    onLoginSuccess(user.role)
                                                },
                                                onFailure = { err ->
                                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            )
                        )
                    }

                    add(
                        ExpressiveAction(
                            label = "About & Privacy",
                            icon = Icons.Default.Info,
                            variant = ExpressiveButtonVariant.OUTLINED,
                            size = ExpressiveButtonSize.M,
                            testTag = "about_storepoint_button",
                            onClick = onAboutNavigate
                        )
                    )
                }

                ExpressiveActionRow(
                    actions = secondaryActions,
                    stackBelowWidth = 480.dp
                )
            }
        }
    }
}
