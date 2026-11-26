package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.munzo.storepoint.data.User
import com.munzo.storepoint.ui.BarcodeScannerDialog
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroButtonVariant
import com.munzo.storepoint.ui.components.HeroCard
import com.munzo.storepoint.ui.components.HeroChip
import com.munzo.storepoint.ui.components.HeroChipVariant
import com.munzo.storepoint.ui.components.HeroColor
import com.munzo.storepoint.ui.components.HeroDivider
import com.munzo.storepoint.ui.components.HeroSize
import com.munzo.storepoint.ui.layout.LocalAdaptiveMetrics
import com.munzo.storepoint.ui.theme.HeroUITheme
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.ui.theme.shakeOnTrigger
import com.munzo.storepoint.util.BiometricAuthHelper
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: StorePointViewModel,
    onAboutNavigate: () -> Unit,
    onPinResetNavigate: () -> Unit,
    onLoginSuccess: (String) -> Unit // returns role: "ADMIN" or "CASHIER"
) {
    val context = LocalContext.current
    val tactile = rememberTactileFeedback()
    val metrics = LocalAdaptiveMetrics.current
    val storeConfig by viewModel.storeConfig.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isAuthenticating by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    // 5-tap emergency kiosk unlock trigger on SP emblem
    var logoTapCount by remember { mutableIntStateOf(0) }
    var showLogoUnlockDialog by remember { mutableStateOf(false) }
    var logoUnlockInput by remember { mutableStateOf("") }
    var logoUnlockError by remember { mutableStateOf("") }

    var loginError by remember { mutableStateOf("") }
    var shakeTrigger by remember { mutableIntStateOf(0) }
    var successPulseTrigger by remember { mutableIntStateOf(0) }

    val isExpanded = metrics.isExpandedWidth
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val lastUser by viewModel.lastLoggedInUser.collectAsState()
    val pendingResetUsers by viewModel.usersPendingPinReset.collectAsState()

    val fragmentActivity = context as? FragmentActivity
    val biometricAvailability = remember(context) { BiometricAuthHelper.checkAvailability(context) }
    val isBiometricAvailable = biometricAvailability.isAvailable

    val suppressedBiometric = remember { mutableStateOf(false) }
    val effectiveLastUser = if (suppressedBiometric.value) null else lastUser
    val biometricTargetUser = remember(effectiveLastUser, pendingResetUsers) {
        effectiveLastUser?.takeIf { it !in pendingResetUsers.map(User::username) }
    }
    val canOfferBiometric = isBiometricEnabled && isBiometricAvailable &&
        fragmentActivity != null && biometricTargetUser != null

    val showPinResetBanner = pendingResetUsers.any {
        it.username.equals(username.trim(), ignoreCase = true)
    }

    fun handleLogoTap() {
        logoTapCount++
        if (logoTapCount >= 5) {
            logoTapCount = 0
            showLogoUnlockDialog = true
        }
    }

    fun attemptLogin(pin: String) {
        if (username.isBlank()) {
            loginError = "Enter your username first."
            tactile.perform(TactileHapticRole.WarningReject)
            shakeTrigger++
            return
        }
        isAuthenticating = true
        loginError = ""
        viewModel.login(
            username = username.trim(),
            pass = pin,
            onSuccess = { user ->
                isAuthenticating = false
                tactile.perform(TactileHapticRole.ConfirmSuccess)
                successPulseTrigger++
                onLoginSuccess(user.role)
            },
            onFailure = { errorMsg ->
                isAuthenticating = false
                loginError = errorMsg
                password = ""
                tactile.perform(TactileHapticRole.WarningReject)
                shakeTrigger++
            }
        )
    }

    fun launchBiometric(target: String) {
        val host = fragmentActivity ?: return
        BiometricAuthHelper.authenticate(
            activity = host,
            title = "Fingerprint Sign-In",
            subtitle = "Verify to open the register as $target",
            onOutcome = { outcome, message ->
                when (outcome) {
                    BiometricAuthHelper.BiometricOutcome.SUCCESS -> Unit
                    BiometricAuthHelper.BiometricOutcome.FALLBACK -> Unit
                    BiometricAuthHelper.BiometricOutcome.UNAVAILABLE -> loginError = message
                    else -> loginError = message
                }
            },
            onSuccess = {
                isAuthenticating = true
                viewModel.loginWithBiometric(
                    targetUsername = target,
                    onSuccess = { user ->
                        isAuthenticating = false
                        tactile.perform(TactileHapticRole.ConfirmSuccess)
                        successPulseTrigger++
                        onLoginSuccess(user.role)
                    },
                    onFailure = { err ->
                        isAuthenticating = false
                        loginError = err
                        tactile.perform(TactileHapticRole.WarningReject)
                        shakeTrigger++
                    }
                )
            }
        )
    }

    if (showScanner) {
        BarcodeScannerDialog(
            products = emptyList(),
            onBarcodeScanned = { scannedBarcode ->
                showScanner = false
                isAuthenticating = true
                viewModel.loginWithBarcode(
                    barcodeId = scannedBarcode,
                    onSuccess = { user ->
                        isAuthenticating = false
                        tactile.perform(TactileHapticRole.ConfirmSuccess)
                        successPulseTrigger++
                        Toast.makeText(context, "Logged in via Badge: Welcome ${user.username}!", Toast.LENGTH_SHORT).show()
                        onLoginSuccess(user.role)
                    },
                    onFailure = { errorMsg ->
                        isAuthenticating = false
                        tactile.perform(TactileHapticRole.WarningReject)
                        shakeTrigger++
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onDismiss = { showScanner = false }
        )
    }

    if (showLogoUnlockDialog) {
        KioskEmergencyUnlockDialog(
            viewModel = viewModel,
            pinInput = logoUnlockInput,
            errorText = logoUnlockError,
            onPinChange = { logoUnlockInput = it },
            onError = { logoUnlockError = it },
            onDismiss = {
                showLogoUnlockDialog = false
                logoUnlockInput = ""
                logoUnlockError = ""
            }
        )
    }

    LaunchedEffect(canOfferBiometric, biometricTargetUser) {
        if (canOfferBiometric && !isAuthenticating) {
            biometricTargetUser?.let { launchBiometric(it) }
        }
    }

    LaunchedEffect(logoTapCount) {
        if (logoTapCount > 0) {
            delay(3000L)
            logoTapCount = 0
        }
    }

    val storeName = storeConfig?.storeName ?: "StorePoint POS"
    val handleSwitchUser = {
        suppressedBiometric.value = true
        username = ""
        password = ""
        loginError = ""
    }

    Scaffold(modifier = Modifier.imePadding()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(HeroUITheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(metrics.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LoginExpandedBrandPanel(
                        storeName = storeName,
                        pulseTrigger = successPulseTrigger,
                        onLogoTap = ::handleLogoTap,
                        onScanBadgeClick = { showScanner = true },
                        onAboutNavigate = onAboutNavigate,
                        modifier = Modifier.weight(1f)
                    )

                    HeroCard(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .shakeOnTrigger(trigger = shakeTrigger),
                        backgroundColor = HeroUITheme.colors.surface,
                        borderColor = HeroUITheme.colors.border
                    ) {
                        LoginCredentialsCardContent(
                            username = username,
                            password = password,
                            loginError = loginError,
                            isAuthenticating = isAuthenticating,
                            showPinResetBanner = showPinResetBanner,
                            showExpandedHeader = true,
                            canOfferBiometric = canOfferBiometric,
                            showSwitchUserInCard = isBiometricAvailable && lastUser != null,
                            onUsernameChange = { username = it },
                            onPasswordChange = { password = it },
                            onSubmit = ::attemptLogin,
                            onPinResetNavigate = onPinResetNavigate,
                            onBiometricClick = { biometricTargetUser?.let(::launchBiometric) },
                            onSwitchUserClick = handleSwitchUser,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = metrics.maxFormWidth.coerceAtMost(500.dp))
                        .fillMaxWidth()
                        .padding(horizontal = metrics.screenPadding, vertical = metrics.sectionSpacing)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (metrics.isCompactHeight) 12.dp else 18.dp)
                ) {
                    LoginBrandEmblem(
                        size = if (metrics.isCompactHeight) 64.dp else 80.dp,
                        pulseTrigger = successPulseTrigger,
                        onLogoTap = ::handleLogoTap
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = storeName,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = HeroUITheme.colors.foreground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        HeroChip(
                            text = "SECURE TERMINAL ACCESS",
                            color = HeroColor.Primary,
                            variant = HeroChipVariant.Flat
                        )
                    }

                    LoginBadgeScanCard(
                        expanded = false,
                        onScanBadgeClick = { showScanner = true }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeroDivider(modifier = Modifier.weight(1f), color = HeroUITheme.colors.border)
                        Text(
                            text = "OR ENTER PIN",
                            modifier = Modifier.padding(horizontal = 12.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = HeroUITheme.colors.muted,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        HeroDivider(modifier = Modifier.weight(1f), color = HeroUITheme.colors.border)
                    }

                    HeroCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shakeOnTrigger(trigger = shakeTrigger),
                        backgroundColor = HeroUITheme.colors.surface,
                        borderColor = HeroUITheme.colors.border
                    ) {
                        LoginCredentialsCardContent(
                            username = username,
                            password = password,
                            loginError = loginError,
                            isAuthenticating = isAuthenticating,
                            showPinResetBanner = showPinResetBanner,
                            showExpandedHeader = false,
                            canOfferBiometric = false,
                            showSwitchUserInCard = false,
                            onUsernameChange = { username = it },
                            onPasswordChange = { password = it },
                            onSubmit = ::attemptLogin,
                            onPinResetNavigate = onPinResetNavigate,
                            onBiometricClick = {},
                            onSwitchUserClick = {},
                            modifier = Modifier.padding(metrics.cardPadding)
                        )
                    }

                    if (canOfferBiometric) {
                        HeroButton(
                            onClick = { biometricTargetUser?.let(::launchBiometric) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("biometric_login_button"),
                            color = HeroColor.Success,
                            variant = HeroButtonVariant.Flat,
                            size = HeroSize.Md,
                            icon = Icons.Default.Fingerprint,
                            text = "Sign in with fingerprint"
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isBiometricAvailable && lastUser != null) {
                            HeroButton(
                                onClick = handleSwitchUser,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("switch_user_button"),
                                color = HeroColor.Default,
                                variant = HeroButtonVariant.Bordered,
                                size = HeroSize.Sm,
                                icon = Icons.Default.PersonOutline,
                                text = "Switch User"
                            )
                        }
                        HeroButton(
                            onClick = onAboutNavigate,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("about_storepoint_button"),
                            color = HeroColor.Default,
                            variant = HeroButtonVariant.Bordered,
                            size = HeroSize.Sm,
                            icon = Icons.Default.Info,
                            text = "About"
                        )
                    }
                }
            }
        }
    }
}
