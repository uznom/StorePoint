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
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.LockReset
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
import com.munzo.storepoint.ui.theme.PinPadEntry
import com.munzo.storepoint.ui.theme.shakeOnTrigger
import com.munzo.storepoint.util.SecurityHelper
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
    onPinResetNavigate: () -> Unit,
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
                    Text("Enter the Admin 4-Digit PIN to immediately stop Lock Task Mode and return to normal operation.")
                    Spacer(Modifier.height(4.dp))
                    // Keypad, not the IME: the dialog is reachable from the status bar
                    // during lockdown, where a system keyboard would cover the screen.
                    PinPadEntry(
                        pin = logoUnlockInput,
                        onPinChange = { logoUnlockInput = it },
                        isMasked = true,
                        isError = logoUnlockError.isNotEmpty(),
                        errorMessage = logoUnlockError.ifEmpty { null },
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
    val pendingResetUsers by viewModel.usersPendingPinReset.collectAsState()

    // Fingerprint-first: the primary path on a device with a working sensor.
    val fragmentActivity = LocalContext.current as? androidx.fragment.app.FragmentActivity
    val biometricAvailability = remember(context) {
        com.munzo.storepoint.util.BiometricAuthHelper.checkAvailability(context)
    }
    val isBiometricAvailable = biometricAvailability.isAvailable

    // The account the fingerprint prompt will resolve to on launch.
    // `suppressedBiometric` is a local opt-out: "Sign in as someone else" suppresses
    // the auto-prompt for the rest of this composition without clearing persisted
    // state, so a legitimate later return still gets the zero-tap path.
    val suppressedBiometric = remember { mutableStateOf(false) }
    val effectiveLastUser = if (suppressedBiometric.value) null else lastUser
    val biometricTargetUser = remember(effectiveLastUser, pendingResetUsers) {
        effectiveLastUser?.takeIf { it !in pendingResetUsers.map(User::username) }
    }
    val canOfferBiometric = isBiometricEnabled && isBiometricAvailable &&
        fragmentActivity != null && biometricTargetUser != null

    // Inline error surface: a shake + message beats a transient toast the cashier
    // can miss mid-rush (Laws of UX: Recognition over Recall, Doherty Threshold).
    var loginError by remember { mutableStateOf("") }
    var shakeTrigger by remember { mutableIntStateOf(0) }

    // Auto-login: fires as soon as the 4th digit lands, so the common case costs
    // zero extra taps (Laws of UX: Doherty Threshold, Goal-Gradient Effect).
    fun attemptLogin(pin: String) {
        if (username.isBlank()) {
            loginError = "Enter your username first."
            return
        }
        isAuthenticating = true
        loginError = ""
        viewModel.login(
            username = username.trim(),
            pass = pin,
            onSuccess = { user ->
                isAuthenticating = false
                onLoginSuccess(user.role)
            },
            onFailure = { errorMsg ->
                isAuthenticating = false
                loginError = errorMsg
                password = ""            // clear so a retry starts clean
                shakeTrigger++           // drive the error micro-interaction
            }
        )
    }

    // Fires the system prompt. Split out so both the auto-trigger on launch and the
    // manual button share identical behaviour.
    fun launchBiometric(target: String) {
        val host = fragmentActivity ?: return
        com.munzo.storepoint.util.BiometricAuthHelper.authenticate(
            activity = host,
            title = "Fingerprint Sign-In",
            subtitle = "Verify to open the register as $target",
            onOutcome = { outcome, message ->
                when (outcome) {
                    com.munzo.storepoint.util.BiometricAuthHelper.BiometricOutcome.SUCCESS ->
                        Unit // handled by onSuccess below
                    com.munzo.storepoint.util.BiometricAuthHelper.BiometricOutcome.FALLBACK ->
                        Unit // user chose the PIN pad; no error to show
                    com.munzo.storepoint.util.BiometricAuthHelper.BiometricOutcome.UNAVAILABLE ->
                        loginError = message
                    else -> loginError = message // recoverable / fatal both route to PIN
                }
            },
            onSuccess = {
                isAuthenticating = true
                viewModel.loginWithBiometric(
                    targetUsername = target,
                    onSuccess = { user ->
                        isAuthenticating = false
                        onLoginSuccess(user.role)
                    },
                    onFailure = { err ->
                        isAuthenticating = false
                        loginError = err
                    }
                )
            }
        )
    }

    // Zero-tap return: the previous user is almost always the one returning to the
    // register, so prompt for them automatically on launch (Laws of UX: Doherty
    // Threshold + Goal-Gradient — no taps to reach the goal).
    LaunchedEffect(canOfferBiometric, biometricTargetUser) {
        if (canOfferBiometric && !isAuthenticating) {
            biometricTargetUser?.let { launchBiometric(it) }
        }
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
                        "OR ENTER ${SecurityHelper.PIN_LENGTH}-DIGIT PIN",
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
                    modifier = Modifier
                        .fillMaxWidth()
                        // Rejection feedback: a short shake paired with the inline error
                        // text, so a wrong PIN is unmissable even mid-checkout.
                        .shakeOnTrigger(trigger = shakeTrigger),
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
                        // Migration prompt: shown only when the typed account still
                        // holds a legacy credential. Discoverable rather than a dead
                        // end, so an upgrading user is never locked out.
                        if (pendingResetUsers.any { it.username.equals(username.trim(), ignoreCase = true) }) {
                            Surface(
                                shape = ExpressiveCardShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pin_reset_banner")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "This account still uses an old 6-digit PIN.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    ExpressiveButton(
                                        onClick = onPinResetNavigate,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("pin_reset_start_button"),
                                        label = "Set up my new 4-digit PIN",
                                        icon = Icons.Default.LockReset,
                                        variant = ExpressiveButtonVariant.TONAL,
                                        size = ExpressiveButtonSize.M
                                    )
                                }
                            }
                        }

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

                        // Shakes on failed auth: a visible, non-blocking confirmation
                        // that the attempt was rejected (micro-interaction).
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "${SecurityHelper.PIN_LENGTH}-Digit Security PIN",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // On-screen numeric keypad instead of the system keyboard:
                            // the IME covered half the register and hid the sign-in
                            // context, and it is a shoulder-surfing surface for the PIN
                            // itself on a shared terminal.
                            PinPadEntry(
                                pin = password,
                                onPinChange = { password = it },
                                // Auto-login the instant the final digit lands - no
                                // "Sign In" tap required in the common case.
                                onPinComplete = { completed -> attemptLogin(completed) },
                                isMasked = true,
                                isError = loginError.isNotEmpty(),
                                errorMessage = loginError.ifEmpty { null },
                                isEnabled = !isAuthenticating,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("password_input")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    isAuthenticating -> "Verifying…"
                                    password.length == SecurityHelper.PIN_LENGTH -> "✓ PIN ready — verifying automatically"
                                    else -> "${password.length}/${SecurityHelper.PIN_LENGTH} digits (numbers only)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isAuthenticating -> MaterialTheme.colorScheme.primary
                                    password.length == SecurityHelper.PIN_LENGTH -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outline
                                }
                            )
                        }

                        // Manual sign-in is retained as an explicit, discoverable action
                        // (Redundancy over a hidden auto-submit) and doubles as the
                        // retry affordance after an error.
                        ExpressiveButton(
                            onClick = { attemptLogin(password) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("submit_login_button"),
                            label = if (isAuthenticating) "Authorizing…" else "Sign In",
                            icon = Icons.Default.Login,
                            variant = ExpressiveButtonVariant.FILLED,
                            size = ExpressiveButtonSize.L,
                            enabled = !isAuthenticating,
                        )

                    }
                }

                // Secondary actions: side-by-side on tablets/desktop, stacked full-width on phones.
                val secondaryActions = buildList {
                    // Fingerprint is the PRIMARY path, so it is promoted to the first
                    // action and given the highest-emphasis variant (Von Restorff
                    // effect: the visually distinct option is the one remembered).
                    if (canOfferBiometric) {
                        add(
                            ExpressiveAction(
                                label = "Sign in with fingerprint",
                                icon = Icons.Default.Fingerprint,
                                variant = ExpressiveButtonVariant.FILLED,
                                size = ExpressiveButtonSize.L,
                                testTag = "biometric_login_button",
                                onClick = { launchBiometric(biometricTargetUser!!) }
                            )
                        )
                    }

                    // Escape hatch: a failed sensor or a different staff member must
                    // never strand the user on a screen with no way forward.
                    if (isBiometricAvailable && lastUser != null) {
                        add(
                            ExpressiveAction(
                                label = "Sign in as someone else",
                                icon = Icons.Default.PersonOutline,
                                variant = ExpressiveButtonVariant.TEXT,
                                size = ExpressiveButtonSize.S,
                                testTag = "switch_user_button",
                                onClick = {
                                    // Suppress the auto-prompt for this composition so
                                    // the cashier can type a different username.
                                    suppressedBiometric.value = true
                                    username = ""
                                    password = ""
                                    loginError = ""
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
