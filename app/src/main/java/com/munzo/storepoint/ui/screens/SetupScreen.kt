package com.munzo.storepoint.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.munzo.storepoint.StorePointDeviceAdminReceiver
import com.munzo.storepoint.util.DatabaseBackupManager
import com.munzo.storepoint.ui.theme.ExpressiveOtpPinInput
import com.munzo.storepoint.util.SecurityHelper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.layout.LocalAdaptiveMetrics
import com.munzo.storepoint.ui.theme.ExpressiveButtonShape
import com.munzo.storepoint.ui.theme.ExpressiveSectionHeader
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.tactileBounce
import com.munzo.storepoint.ui.theme.glassPanel
import com.munzo.storepoint.ui.theme.rememberTactileFeedback

import com.munzo.storepoint.ui.components.*
import com.munzo.storepoint.ui.theme.HeroUITheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(viewModel: StorePointViewModel) {
    var storeName by remember { mutableStateOf("") }
    var currencySymbol by remember { mutableStateOf("₱") }
    val currencies = listOf("₱", "$", "€", "£", "¥")
    var currencyExpanded by remember { mutableStateOf(false) }

    var taxPercentageStr by remember { mutableStateOf("12.0") }
    var hasGCash by remember { mutableStateOf(true) }
    var hasMaya by remember { mutableStateOf(true) }
    var hasLoad by remember { mutableStateOf(true) }
    var smartLoadBalStr by remember { mutableStateOf("0.00") }
    var globeLoadBalStr by remember { mutableStateOf("0.00") }
    var loadFeeStr by remember { mutableStateOf("2.00") }
    var gcashFeeStr by remember { mutableStateOf("10.00") }
    var mayaFeeStr by remember { mutableStateOf("10.00") }
    var mayaBankFeeStr by remember { mutableStateOf("15.00") }

    var adminName by remember { mutableStateOf("") }
    var adminPhoneNumber by remember { mutableStateOf("") }
    var adminUsername by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }

    var activeStep by remember { mutableStateOf(1) } // Step 1: Profile & Money, Step 2: Security

    val metrics = LocalAdaptiveMetrics.current
    val tactile = rememberTactileFeedback()
    val mockExpanded = !metrics.isCompactWidth

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isDeviceAdminActive by remember {
        mutableStateOf(StorePointDeviceAdminReceiver.isDeviceAdminActive(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDeviceAdminActive = StorePointDeviceAdminReceiver.isDeviceAdminActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var existingBackupFile by remember { mutableStateOf<File?>(null) }
    var existingBackupMeta by remember { mutableStateOf<DatabaseBackupManager.BackupMetadata?>(null) }
    var isRestoringBackup by remember { mutableStateOf(false) }
    var showRestoreDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val file = viewModel.getLatestPersistentBackup()
            if (file != null && file.exists() && file.length() > 0) {
                val meta = viewModel.peekBackupMetadata(file)
                if (meta != null && (meta.productsCount > 0 || meta.transactionsCount > 0)) {
                    withContext(Dispatchers.Main) {
                        existingBackupFile = file
                        existingBackupMeta = meta
                    }
                }
            }
        }
    }

    val isExpanded = !metrics.isCompactWidth

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("StorePoint Initial Sign-Up", fontWeight = FontWeight.Bold, color = HeroUITheme.colors.foreground)
                        HeroChip(text = "ONBOARDING", color = HeroColor.Primary, variant = HeroChipVariant.Flat)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = HeroUITheme.colors.surface,
                    titleContentColor = HeroUITheme.colors.foreground
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .background(HeroUITheme.colors.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = metrics.maxFormWidth)
                    .fillMaxWidth()
                    .padding(horizontal = metrics.screenPadding, vertical = metrics.sectionSpacing)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // HeroUI Brand Logo
                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .tactileBounce(),
                    shape = HeroUITheme.radius.xl,
                    color = HeroUITheme.colors.accent,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "SP",
                            color = HeroUITheme.colors.accentForeground,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Welcome to StorePoint POS",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = HeroUITheme.colors.foreground,
                    modifier = Modifier.padding(bottom = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Text(
                    text = "Deploying an offline-first commercial retail terminal has never been this simple.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HeroUITheme.colors.muted,
                    modifier = Modifier.padding(bottom = 20.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // Auto-Restore Card if previous persistent internal backup was detected
                if (existingBackupMeta != null && !showRestoreDismissed) {
                    SetupExistingBackupBanner(
                        context = context,
                        viewModel = viewModel,
                        existingBackupMeta = existingBackupMeta,
                        existingBackupFile = existingBackupFile,
                        onDismiss = { showRestoreDismissed = true }
                    )
                }

                // Step Progress Indicator using HeroUI Pill Navigation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = HeroUITheme.radius.full,
                        color = if (activeStep == 1) HeroUITheme.colors.accent else HeroUITheme.colors.surfaceSecondary,
                        modifier = Modifier.clickable { activeStep = 1 }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (activeStep == 1) HeroUITheme.colors.accentForeground else HeroUITheme.colors.muted),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "1",
                                    color = if (activeStep == 1) HeroUITheme.colors.accent else HeroUITheme.colors.surface,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                "Profile & Money",
                                color = if (activeStep == 1) HeroUITheme.colors.accentForeground else HeroUITheme.colors.foreground,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(2.dp)
                            .background(HeroUITheme.colors.border)
                    )

                    Surface(
                        shape = HeroUITheme.radius.full,
                        color = if (activeStep == 2) HeroUITheme.colors.accent else HeroUITheme.colors.surfaceSecondary,
                        modifier = Modifier.clickable {
                            if (storeName.isNotBlank() && taxPercentageStr.toDoubleOrNull() != null) {
                                activeStep = 2
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (activeStep == 2) HeroUITheme.colors.accentForeground else HeroUITheme.colors.muted),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "2",
                                    color = if (activeStep == 2) HeroUITheme.colors.accent else HeroUITheme.colors.surface,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                "Security & Admin",
                                color = if (activeStep == 2) HeroUITheme.colors.accentForeground else HeroUITheme.colors.foreground,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Step Body inside HeroCard
                HeroCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    backgroundColor = HeroUITheme.colors.surface,
                    borderColor = HeroUITheme.colors.border,
                    shape = HeroUITheme.radius.xxl
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (activeStep == 1) {
                            HeroCardHeader(
                                title = "Step 1: Store Profile & Financials",
                                subtitle = "Configure business branding, currency, and digital services",
                                icon = Icons.Default.Store,
                                iconColor = HeroUITheme.colors.accent
                            )

                            if (mockExpanded) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    HeroInput(
                                        value = storeName,
                                        onValueChange = { storeName = it },
                                        label = { Text("Store/Business Name") },
                                        leadingIcon = { Icon(Icons.Default.Store, null, tint = HeroUITheme.colors.accent) },
                                        modifier = Modifier
                                            .weight(1.5f)
                                            .testTag("store_name_input"),
                                        singleLine = true,
                                        shape = HeroUITheme.radius.md
                                    )

                                    ExposedDropdownMenuBox(
                                        expanded = currencyExpanded,
                                        onExpandedChange = { currencyExpanded = !currencyExpanded },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        HeroInput(
                                            readOnly = true,
                                            value = "Currency:  $currencySymbol",
                                            onValueChange = {},
                                            label = { Text("Currency Unit") },
                                            leadingIcon = { Icon(Icons.Default.Paid, null, tint = HeroUITheme.colors.accent) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                                .testTag("currency_selector_input"),
                                            shape = HeroUITheme.radius.md
                                        )
                                        ExposedDropdownMenu(
                                            expanded = currencyExpanded,
                                            onDismissRequest = { currencyExpanded = false }
                                        ) {
                                            currencies.forEach { selection ->
                                                DropdownMenuItem(
                                                    text = { Text(selection) },
                                                    onClick = {
                                                        currencySymbol = selection
                                                        currencyExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                HeroInput(
                                    value = storeName,
                                    onValueChange = { storeName = it },
                                    label = { Text("Store/Business Name") },
                                    leadingIcon = { Icon(Icons.Default.Store, null, tint = HeroUITheme.colors.accent) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("store_name_input"),
                                    singleLine = true,
                                    shape = HeroUITheme.radius.md
                                )

                                ExposedDropdownMenuBox(
                                    expanded = currencyExpanded,
                                    onExpandedChange = { currencyExpanded = !currencyExpanded }
                                ) {
                                    HeroInput(
                                        readOnly = true,
                                        value = "Currency Unit:  $currencySymbol",
                                        onValueChange = {},
                                        label = { Text("Operational Currency") },
                                        leadingIcon = { Icon(Icons.Default.Paid, null, tint = HeroUITheme.colors.accent) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                            .testTag("currency_selector_input"),
                                        shape = HeroUITheme.radius.md
                                    )
                                    ExposedDropdownMenu(
                                        expanded = currencyExpanded,
                                        onDismissRequest = { currencyExpanded = false }
                                    ) {
                                        currencies.forEach { selection ->
                                            DropdownMenuItem(
                                                text = { Text(selection) },
                                                onClick = {
                                                    currencySymbol = selection
                                                    currencyExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Tax rate (Philippine 12% VAT default)
                            HeroInput(
                                value = taxPercentageStr,
                                onValueChange = { taxPercentageStr = it },
                                label = { Text("Value-Added Tax (%) - PH VAT") },
                                supportingText = { Text("Standard 12% VAT included in product retail prices (amount / 1.12 x 0.12)") },
                                leadingIcon = { Icon(Icons.Default.Percent, null, tint = HeroUITheme.colors.accent) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tax_percentage_input"),
                                singleLine = true,
                                shape = HeroUITheme.radius.md
                            )

                            HeroDivider(color = HeroUITheme.colors.separator, modifier = Modifier.padding(vertical = 4.dp))

                            Text(
                                "Digital Services Retailing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = HeroUITheme.colors.foreground
                            )

                            // Expressive Hero Selection Tiles
                            val services = listOf(
                                Triple("GCash Retailing", "Cash In, Cash Out transactions", hasGCash) to { hasGCash = !hasGCash },
                                Triple("Maya Services", "Digital payments with flat bank fees", hasMaya) to { hasMaya = !hasMaya },
                                Triple("Prepaid Mobile Load", "Smart, TNT, Globe, TM e-loading (2% retailer rebate)", hasLoad) to { hasLoad = !hasLoad }
                            )

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { hasGCash = !hasGCash },
                                shape = HeroUITheme.radius.lg,
                                color = if (hasGCash) HeroUITheme.colors.accent.copy(alpha = 0.1f) else HeroUITheme.colors.surfaceSecondary,
                                border = if (hasGCash) androidx.compose.foundation.BorderStroke(1.5.dp, HeroUITheme.colors.accent) else androidx.compose.foundation.BorderStroke(1.dp, HeroUITheme.colors.border)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    HeroCheckbox(
                                        checked = hasGCash,
                                        onCheckedChange = { hasGCash = it },
                                        modifier = Modifier.testTag("setup_has_gcash_checkbox")
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("GCash Retailing", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("Cash In, Cash Out transactions", style = MaterialTheme.typography.bodySmall, color = HeroUITheme.colors.muted)
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { hasMaya = !hasMaya },
                                shape = HeroUITheme.radius.lg,
                                color = if (hasMaya) HeroUITheme.colors.accent.copy(alpha = 0.1f) else HeroUITheme.colors.surfaceSecondary,
                                border = if (hasMaya) androidx.compose.foundation.BorderStroke(1.5.dp, HeroUITheme.colors.accent) else androidx.compose.foundation.BorderStroke(1.dp, HeroUITheme.colors.border)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    HeroCheckbox(
                                        checked = hasMaya,
                                        onCheckedChange = { hasMaya = it },
                                        modifier = Modifier.testTag("setup_has_maya_checkbox")
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Maya Services", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("Digital payments with flat bank fees", style = MaterialTheme.typography.bodySmall, color = HeroUITheme.colors.muted)
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { hasLoad = !hasLoad },
                                shape = HeroUITheme.radius.lg,
                                color = if (hasLoad) HeroUITheme.colors.accent.copy(alpha = 0.1f) else HeroUITheme.colors.surfaceSecondary,
                                border = if (hasLoad) androidx.compose.foundation.BorderStroke(1.5.dp, HeroUITheme.colors.accent) else androidx.compose.foundation.BorderStroke(1.dp, HeroUITheme.colors.border)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    HeroCheckbox(
                                        checked = hasLoad,
                                        onCheckedChange = { hasLoad = it },
                                        modifier = Modifier.testTag("setup_has_load_checkbox")
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Prepaid Mobile Load", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("Smart, TNT, Globe, TM e-loading (2% retailer rebate)", style = MaterialTheme.typography.bodySmall, color = HeroUITheme.colors.muted)
                                    }
                                }
                            }

                            if (hasLoad) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    HeroInput(
                                        value = smartLoadBalStr,
                                        onValueChange = { smartLoadBalStr = it },
                                        label = { Text("Smart/TNT Wallet ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f).testTag("setup_smart_load_input"),
                                        shape = HeroUITheme.radius.md
                                    )
                                    HeroInput(
                                        value = globeLoadBalStr,
                                        onValueChange = { globeLoadBalStr = it },
                                        label = { Text("Globe/TM Wallet ($currencySymbol)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f).testTag("setup_globe_load_input"),
                                        shape = HeroUITheme.radius.md
                                    )
                                }
                            }

                            if (hasLoad || hasGCash || hasMaya) {
                                HeroDivider(color = HeroUITheme.colors.separator, modifier = Modifier.padding(vertical = 4.dp))
                                Text(
                                    "Customer Service Fee Tuning",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = HeroUITheme.colors.foreground
                                )

                                if (hasLoad) {
                                    HeroInput(
                                        value = loadFeeStr,
                                        onValueChange = { loadFeeStr = it },
                                        label = { Text("Prepaid Load Charge ($currencySymbol)") },
                                        supportingText = { Text("Default customer surcharge (e.g. ₱2.00 on ₱20 load)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth().testTag("setup_load_fee_input"),
                                        shape = HeroUITheme.radius.md,
                                        singleLine = true
                                    )
                                }

                                if (hasGCash) {
                                    HeroInput(
                                        value = gcashFeeStr,
                                        onValueChange = { gcashFeeStr = it },
                                        label = { Text("GCash Service Fee ($currencySymbol)") },
                                        supportingText = { Text("Base rate per ₱1,000 tier (e.g. ₱10.00)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth().testTag("setup_gcash_fee_input"),
                                        shape = HeroUITheme.radius.md,
                                        singleLine = true
                                    )
                                }

                                if (hasMaya) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        HeroInput(
                                            value = mayaFeeStr,
                                            onValueChange = { mayaFeeStr = it },
                                            label = { Text("Maya Fee ($currencySymbol)") },
                                            supportingText = { Text("Store fee (₱10.00)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f).testTag("setup_maya_fee_input"),
                                            shape = HeroUITheme.radius.md,
                                            singleLine = true
                                        )
                                        HeroInput(
                                            value = mayaBankFeeStr,
                                            onValueChange = { mayaBankFeeStr = it },
                                            label = { Text("Bank Fee ($currencySymbol)") },
                                            supportingText = { Text("Transfer fee (₱15.00)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f).testTag("setup_maya_bank_fee_input"),
                                            shape = HeroUITheme.radius.md,
                                            singleLine = true
                                        )
                                    }
                                }
                            }

                        } else {
                            SetupStepTwoSecurity(
                                context = context,
                                isExpanded = isExpanded,
                                isDeviceAdminActive = isDeviceAdminActive,
                                adminName = adminName,
                                onAdminNameChange = { adminName = it },
                                adminPhoneNumber = adminPhoneNumber,
                                onAdminPhoneNumberChange = { adminPhoneNumber = it },
                                adminUsername = adminUsername,
                                onAdminUsernameChange = { adminUsername = it },
                                adminPassword = adminPassword,
                                onAdminPasswordChange = { adminPassword = it }
                            )
                        }
                    }
                }

                // Action / Navigation Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeStep == 2) {
                        HeroButton(
                            onClick = { activeStep = 1 },
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            color = HeroColor.Default,
                            variant = HeroButtonVariant.Bordered,
                            size = HeroSize.Lg,
                            icon = Icons.Default.ArrowBack,
                            text = "Back"
                        )
                    }

                    HeroButton(
                        onClick = {
                            if (activeStep == 1) {
                                if (storeName.isNotBlank() && taxPercentageStr.toDoubleOrNull() != null) {
                                    tactile.perform(TactileHapticRole.Tap)
                                    activeStep = 2
                                } else {
                                    tactile.perform(TactileHapticRole.WarningReject)
                                }
                            } else {
                                if (!isDeviceAdminActive) {
                                    tactile.perform(TactileHapticRole.WarningReject)
                                    Toast.makeText(context, "Please activate Device Administrator first to secure this POS terminal.", Toast.LENGTH_LONG).show()
                                    try {
                                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, StorePointDeviceAdminReceiver.getComponentName(context))
                                            putExtra(
                                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                                "StorePoint requires Device Administrator privileges to lock kiosk mode and prevent unauthorized app uninstallation or clearing data."
                                            )
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                    return@HeroButton
                                }
                                if (adminUsername.isNotBlank() && SecurityHelper.isValidPin(adminPassword)) {
                                    tactile.perform(TactileHapticRole.ConfirmSuccess)
                                    viewModel.completeInitialSetup(
                                        storeName = storeName.trim(),
                                        currency = currencySymbol,
                                        taxPercent = taxPercentageStr.toDoubleOrNull() ?: 0.0,
                                        adminUser = adminUsername.trim(),
                                        adminPass = adminPassword,
                                        adminName = adminName.trim(),
                                        adminPhone = adminPhoneNumber.trim(),
                                        hasGCash = hasGCash,
                                        hasMaya = hasMaya,
                                        hasLoad = hasLoad,
                                        smartLoadBalance = smartLoadBalStr.toDoubleOrNull() ?: 0.0,
                                        globeLoadBalance = globeLoadBalStr.toDoubleOrNull() ?: 0.0,
                                        loadServiceFee = loadFeeStr.toDoubleOrNull() ?: 2.0,
                                        gcashServiceFee = gcashFeeStr.toDoubleOrNull() ?: 10.0,
                                        mayaServiceFee = mayaFeeStr.toDoubleOrNull() ?: 10.0,
                                        mayaBankFee = mayaBankFeeStr.toDoubleOrNull() ?: 15.0
                                    )
                                } else {
                                    tactile.perform(TactileHapticRole.WarningReject)
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (activeStep == 2) 8.dp else 0.dp)
                            .testTag("setup_action_button"),
                        color = HeroColor.Primary,
                        variant = HeroButtonVariant.Solid,
                        size = HeroSize.Lg,
                        icon = if (activeStep == 1) null else Icons.Default.Check,
                        trailingIcon = if (activeStep == 1) Icons.Default.ArrowForward else null,
                        text = if (activeStep == 1) "Next Phase" else "Save & Complete",
                        enabled = if (activeStep == 1) {
                            storeName.isNotBlank() && taxPercentageStr.toDoubleOrNull() != null
                        } else {
                            storeName.isNotBlank() && taxPercentageStr.toDoubleOrNull() != null &&
                            adminUsername.isNotBlank() &&
                            SecurityHelper.isValidPin(adminPassword) &&
                            !SecurityHelper.isWeakPin(adminPassword) &&
                            isDeviceAdminActive
                        }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

