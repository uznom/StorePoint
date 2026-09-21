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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.layout.rememberWindowLayout
import com.munzo.storepoint.ui.layout.WindowLayout
import com.munzo.storepoint.ui.theme.ExpressiveButtonShape
import com.munzo.storepoint.ui.theme.ExpressiveSectionHeader
import com.munzo.storepoint.ui.theme.tactileBounce
import com.munzo.storepoint.ui.theme.glassPanel

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

    val windowLayout = rememberWindowLayout()
    val isCompact = windowLayout == WindowLayout.Compact

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("StorePoint Initial Sign-Up", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = if (isCompact) 20.dp else 24.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // Google-style geometric SP AppLogo styled with clean sans-serif typography
                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .tactileBounce(),
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
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome to StorePoint POS",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = "Deploying an offline-first retail system has never been this simple. Configure your offline store register now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Auto-Restore Card if previous persistent internal backup was detected
            if (existingBackupMeta != null && !showRestoreDismissed) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SettingsBackupRestore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Previous Installation Backup Found!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Store: ${existingBackupMeta?.storeName ?: "StorePoint"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Products", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${existingBackupMeta?.productsCount ?: 0}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Transactions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${existingBackupMeta?.transactionsCount ?: 0}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Backup Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(existingBackupMeta?.dateTime?.take(10) ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (isRestoringBackup) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Restoring database from internal backup...", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { showRestoreDismissed = true }
                                ) {
                                    Text("Start Fresh Setup")
                                }
                                Button(
                                    onClick = {
                                        val file = existingBackupFile
                                        if (file != null) {
                                            isRestoringBackup = true
                                            viewModel.restoreFromPersistentBackup(file) { summary ->
                                                isRestoringBackup = false
                                                if (summary.success) {
                                                    Toast.makeText(context, "Store restored successfully!", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "Restore failed: ${summary.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Restore Store Now", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Step Progress Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step 1 Circle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (activeStep >= 1) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "1", 
                        color = if (activeStep >= 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Divider line
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(2.dp)
                        .background(
                            if (activeStep >= 2) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )

                // Step 2 Circle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (activeStep >= 2) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "2", 
                        color = if (activeStep >= 2) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Step Body inside card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (activeStep == 1) {
                        Text(
                            "Step 1: Store profile & Financials",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = storeName,
                            onValueChange = { storeName = it },
                            label = { Text("Store/Business Name") },
                            leadingIcon = { Icon(Icons.Default.Store, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("store_name_input"),
                            singleLine = true
                        )

                        // Currency Selector dropdown
                        ExposedDropdownMenuBox(
                            expanded = currencyExpanded,
                            onExpandedChange = { currencyExpanded = !currencyExpanded }
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = "Currency Unit:  $currencySymbol",
                                onValueChange = {},
                                label = { Text("Operational Currency") },
                                leadingIcon = { Icon(Icons.Default.Paid, null) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("currency_selector_input")
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

                        // Tax rate (Philippine 12% VAT default)
                        OutlinedTextField(
                            value = taxPercentageStr,
                            onValueChange = { taxPercentageStr = it },
                            label = { Text("Value-Added Tax (%) - PH VAT") },
                            supportingText = { Text("Standard 12% VAT is included in product retail prices (amount / 1.12 x 0.12)") },
                            leadingIcon = { Icon(Icons.Default.Percent, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tax_percentage_input"),
                            singleLine = true
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        ExpressiveSectionHeader(
                            title = "Digital Services Available",
                            icon = Icons.Default.AccountBalanceWallet
                        )

                        Text(
                            "Enable the digital services this store offers. You'll be able to transact GCash, Maya, and Load directly from the POS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Google-style Expressive Selection Tiles
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { hasGCash = !hasGCash },
                            shape = RoundedCornerShape(16.dp),
                            color = if (hasGCash) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (hasGCash) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Checkbox(
                                    checked = hasGCash,
                                    onCheckedChange = { hasGCash = it },
                                    modifier = Modifier.testTag("setup_has_gcash_checkbox")
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("GCash Retailing", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Cash In, Cash Out transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { hasMaya = !hasMaya },
                            shape = RoundedCornerShape(16.dp),
                            color = if (hasMaya) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (hasMaya) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Checkbox(
                                    checked = hasMaya,
                                    onCheckedChange = { hasMaya = it },
                                    modifier = Modifier.testTag("setup_has_maya_checkbox")
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Maya Services", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Digital payments with flat bank fees", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { hasLoad = !hasLoad },
                            shape = RoundedCornerShape(16.dp),
                            color = if (hasLoad) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (hasLoad) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Checkbox(
                                    checked = hasLoad,
                                    onCheckedChange = { hasLoad = it },
                                    modifier = Modifier.testTag("setup_has_load_checkbox")
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Prepaid Mobile Load", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Smart, TNT, Globe, TM e-loading (2% retailer rebate)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        if (hasLoad) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = smartLoadBalStr,
                                    onValueChange = { smartLoadBalStr = it },
                                    label = { Text("Smart/TNT Wallet ($currencySymbol)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f).testTag("setup_smart_load_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                OutlinedTextField(
                                    value = globeLoadBalStr,
                                    onValueChange = { globeLoadBalStr = it },
                                    label = { Text("Globe/TM Wallet ($currencySymbol)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f).testTag("setup_globe_load_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            Text(
                                "Specify initial retailer load wallet balances for Smart/TNT and Globe/TM. A 2% retailer rebate applies to each load sale.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (hasLoad || hasGCash || hasMaya) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            ExpressiveSectionHeader(
                                title = "Digital Service Surcharges & Fees",
                                icon = Icons.Default.PriceChange
                            )
                            Text(
                                "Configure your store's default customer service fees. You can also fine-tune them anytime in the Admin Dashboard or per transaction on POS.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (hasLoad) {
                                OutlinedTextField(
                                    value = loadFeeStr,
                                    onValueChange = { loadFeeStr = it },
                                    label = { Text("Prepaid Load Charge ($currencySymbol)") },
                                    supportingText = { Text("Default customer surcharge (e.g. ₱2.00 on ₱20 load)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().testTag("setup_load_fee_input"),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }

                            if (hasGCash) {
                                OutlinedTextField(
                                    value = gcashFeeStr,
                                    onValueChange = { gcashFeeStr = it },
                                    label = { Text("GCash Service Fee ($currencySymbol)") },
                                    supportingText = { Text("Base rate per ₱1,000 tier (e.g. ₱10.00)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().testTag("setup_gcash_fee_input"),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }

                            if (hasMaya) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = mayaFeeStr,
                                        onValueChange = { mayaFeeStr = it },
                                        label = { Text("Maya Fee ($currencySymbol)") },
                                        supportingText = { Text("Store fee (e.g. ₱10.00)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f).testTag("setup_maya_fee_input"),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = mayaBankFeeStr,
                                        onValueChange = { mayaBankFeeStr = it },
                                        label = { Text("Bank Fee ($currencySymbol)") },
                                        supportingText = { Text("Transfer fee (e.g. ₱15.00)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f).testTag("setup_maya_bank_fee_input"),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                    } else {
                        // Step 2: Administrative Security
                        Text(
                            "Step 2: Admin accounts & terminal security",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            "Create your master administrator account and activate Device Administration to lock kiosk mode and prevent unauthorized app uninstallation or data clearing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Device Administrator Security Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDeviceAdminActive)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isDeviceAdminActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isDeviceAdminActive) Icons.Default.Shield else Icons.Default.GppMaybe,
                                            contentDescription = null,
                                            tint = if (isDeviceAdminActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Device Administrator",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isDeviceAdminActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    ) {
                                        Text(
                                            text = if (isDeviceAdminActive) "ACTIVE" else "REQUIRED",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isDeviceAdminActive)
                                        "Device Administrator is active. Anti-uninstall protection and Kiosk unpin defense are enabled."
                                    else
                                        "Device Administrator privilege is required to lock kiosk mode, prevent unauthorized uninstallation, and stop cashiers or users from clearing app data.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!isDeviceAdminActive) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, StorePointDeviceAdminReceiver.getComponentName(context))
                                                    putExtra(
                                                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                                        "StorePoint requires Device Administrator privileges to lock kiosk mode and prevent unauthorized app uninstallation or clearing data."
                                                    )
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not open Device Admin screen: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Activate Device Administrator", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = adminName,
                            onValueChange = { adminName = it },
                            label = { Text("Admin / Owner Full Name") },
                            placeholder = { Text("e.g. Juan Dela Cruz") },
                            leadingIcon = { Icon(Icons.Default.Badge, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_name_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = adminPhoneNumber,
                            onValueChange = { input ->
                                if (input.length <= 15 && input.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
                                    adminPhoneNumber = input
                                }
                            },
                            label = { Text("Admin Phone Number (for 'Call Admin')") },
                            placeholder = { Text("e.g. 09171234567") },
                            supportingText = { Text("Dialed automatically when cashiers trigger 'Call Admin' from the register") },
                            leadingIcon = { Icon(Icons.Default.Phone, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_phone_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = adminUsername,
                            onValueChange = { adminUsername = it },
                            label = { Text("Admin Username") },
                            placeholder = { Text("e.g. admin") },
                            leadingIcon = { Icon(Icons.Default.AccountCircle, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_user_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Admin 6-Digit PIN",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ExpressiveOtpPinInput(
                                pin = adminPassword,
                                onPinChange = { adminPassword = it },
                                pinLength = 6,
                                isMasked = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_password_input")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (adminPassword.length == 6) "✓ 6-digit security PIN ready" else "${adminPassword.length}/6 digits (numeric only)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (adminPassword.length == 6) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action / Navigation Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeStep == 2) {
                    OutlinedButton(
                        onClick = { activeStep = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .padding(end = 8.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Back", fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = {
                        if (activeStep == 1) {
                            if (storeName.isNotBlank() && taxPercentageStr.toDoubleOrNull() != null) {
                                activeStep = 2
                            }
                        } else {
                            if (!isDeviceAdminActive) {
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
                                return@Button
                            }
                            if (adminUsername.isNotBlank() && adminPassword.length == 6) {
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
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .padding(start = if (activeStep == 2) 8.dp else 0.dp)
                        .testTag("setup_action_button"),
                    shape = CircleShape,
                    enabled = if (activeStep == 1) {
                        storeName.isNotBlank() && taxPercentageStr.toDoubleOrNull() != null
                    } else {
                        storeName.isNotBlank() && taxPercentageStr.toDoubleOrNull() != null &&
                        adminUsername.isNotBlank() && adminPassword.length == 6 && isDeviceAdminActive
                    }
                ) {
                    Text(
                        text = if (activeStep == 1) "Next Phase" else "Save & Complete",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                    if (activeStep == 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    } else {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
}

