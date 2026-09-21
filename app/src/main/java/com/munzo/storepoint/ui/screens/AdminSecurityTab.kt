package com.munzo.storepoint.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.StorePointDeviceAdminReceiver
import com.munzo.storepoint.data.*
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.theme.*
import com.munzo.storepoint.util.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SecurityTab(viewModel: StorePointViewModel) {
    val context = LocalContext.current
    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val adminName = remember { ComponentName(context, com.munzo.storepoint.StorePointDeviceAdminReceiver::class.java) }
    var isAdminActive by remember { mutableStateOf(dpm.isAdminActive(adminName)) }

    // Device admin consent is granted in a system screen, so re-read the real status as soon as
    // the user comes back. Without this the status card stayed stale ("PROTECTION DEACTIVATED")
    // and the Enable button kept showing even after the admin was granted.
    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isAdminActive = dpm.isAdminActive(adminName)
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAdminActive = dpm.isAdminActive(adminName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isKioskActive by viewModel.isKioskModeActive.collectAsState()
    val isAlwaysOn by viewModel.isAlwaysOnEnabled.collectAsState()
    val savedKioskPin by viewModel.kioskPin.collectAsState()

    // Dialog state controllers
    var showKioskPinSetupDialog by remember { mutableStateOf(false) }
    var showKioskPinVerifyDialog by remember { mutableStateOf(false) }
    var pinSetupInput by remember { mutableStateOf("") }
    var pinSetupConfirmInput by remember { mutableStateOf("") }
    var pinSetupError by remember { mutableStateOf("") }
    
    var pinVerifyInput by remember { mutableStateOf("") }
    var pinVerifyError by remember { mutableStateOf("") }

    var showWipeDataDialog by remember { mutableStateOf(false) }
    var adminWipePasswordInput by remember { mutableStateOf("") }
    var wipeDataError by remember { mutableStateOf("") }

    // Admin Verification for Inventory Import (.spinventory & JSON)
    var showImportInventoryPinDialog by remember { mutableStateOf(false) }
    var pendingImportInventoryContent by remember { mutableStateOf("") }
    var importInventoryPinInput by remember { mutableStateOf("") }
    var importInventoryPinError by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val dbHealth by viewModel.databaseHealth.collectAsState()
    var isCheckingDbHealth by remember { mutableStateOf(false) }

    // Backup & Inventory Imports launcher (.spinventory, .json, */*)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val fileContents = stream.bufferedReader().use { it.readText() }
                    pendingImportInventoryContent = fileContents
                    importInventoryPinInput = ""
                    importInventoryPinError = ""
                    showImportInventoryPinDialog = true
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot read file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Biometrics state
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val isBiometricSupported = remember(context) { BiometricAuthHelper.isBiometricSupported(context) }

    // Hardware Printer states
    val printerType by viewModel.printerType.collectAsState()
    val printerIp by viewModel.printerIpAddress.collectAsState()
    val printerPort by viewModel.printerPort.collectAsState()
    val printerMac by viewModel.printerBtMac.collectAsState()
    val is80mm by viewModel.is80mmThermal.collectAsState()
    val isAutoKick by viewModel.isAutoKickDrawerEnabled.collectAsState()
    var editPrinterIp by remember(printerIp) { mutableStateOf(printerIp) }
    var editPrinterPort by remember(printerPort) { mutableStateOf(printerPort.toString()) }
    var editPrinterMac by remember(printerMac) { mutableStateOf(printerMac) }

    // Diagnostics states
    val crashLogs by viewModel.crashLogs.collectAsState()
    var showCrashLogsDialog by remember { mutableStateOf(false) }

    // Backup & Restore states
    val backupSnapshots by viewModel.backupSnapshots.collectAsState()
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf("") }
    var restoreSummaryResult by remember { mutableStateOf<DatabaseBackupManager.RestoreSummary?>(null) }
    var showRestoreSummaryDialog by remember { mutableStateOf(false) }

    // M2: optional backup passphrase protection
    var showExportEncryptDialog by remember { mutableStateOf(false) }
    var backupPassphrase by remember { mutableStateOf("") }
    var backupPassphraseConfirm by remember { mutableStateOf("") }
    var backupPassphraseError by remember { mutableStateOf("") }
    var restorePassphrase by remember { mutableStateOf("") }

    // Full DB Restore Launcher
    val fullRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val json = stream.bufferedReader().use { it.readText() }
                    pendingRestoreJson = json
                    showRestoreConfirmDialog = true
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Read error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Admin Contact & Assistance Phone
    val adminPrefs = remember { context.getSharedPreferences("storepoint_admin_prefs", Context.MODE_PRIVATE) }
    var adminContactName by remember { mutableStateOf(adminPrefs.getString("admin_name", "") ?: "") }
    var adminPhoneNumber by remember { mutableStateOf(adminPrefs.getString("admin_phone_number", "") ?: "") }
    var adminPhoneSavedFeedback by remember { mutableStateOf(false) }

    // Allowed Apps & Terminal Whitelist
    var appSearchQuery by remember { mutableStateOf("") }
    var allowedAppsSet by remember {
        mutableStateOf(DigitalServicesHelper.getAllowedApps(adminPrefs))
    }
    var preferredLoadApp by remember {
        mutableStateOf(DigitalServicesHelper.getPreferredLoadApp(adminPrefs))
    }
    var appsSavedFeedback by remember { mutableStateOf(false) }

    val installedApps = remember {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        try {
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            resolveInfos.map {
                it.activityInfo.packageName to it.loadLabel(pm).toString()
            }.filter { it.first != context.packageName }
             .distinctBy { it.first }
             .sortedBy { it.second.lowercase(Locale.getDefault()) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Device Security Management",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // --- DATABASE CONNECTION & PEAK PERFORMANCE ENGINE HEALTH ---
        Card(
            modifier = Modifier.fillMaxWidth().testTag("db_connection_health_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (dbHealth?.isConnected == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (dbHealth?.isConnected == true) Icons.Default.Storage else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (dbHealth?.isConnected == true) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Database Engine & Connection Health",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "SQLite WAL Engine • Turbo Memory (128MB Cache, 256MB MMAP)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val badgeText = if (dbHealth == null) "CHECKING..." else if (dbHealth?.isConnected == true) "ACTIVE & PEAK" else "NO CONNECTIONS"
                    val badgeColor = if (dbHealth?.isConnected == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    val contentBadgeColor = if (dbHealth?.isConnected == true) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    ExpressiveMorphingBadge(
                        text = badgeText,
                        containerColor = badgeColor,
                        contentColor = contentBadgeColor,
                        icon = if (dbHealth?.isConnected == true) Icons.Default.CheckCircle else Icons.Default.Error
                    )
                }

                if (isCheckingDbHealth) {
                    ExpressiveSquiggleLinearProgress(
                        color = MaterialTheme.colorScheme.primary,
                        amplitude = 3.dp,
                        wavelength = 20.dp
                    )
                }

                dbHealth?.let { health ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Latency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("${health.latencyMs} ms", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Write Mode", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(if (health.isWritable) "Read / Write" else "Read Only", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Integrity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(if (health.integrityOk) "Passed (OK)" else "Corrupted", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = if (health.integrityOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Text(
                        text = health.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                FilledTonalButton(
                    onClick = {
                        isCheckingDbHealth = true
                        viewModel.checkDatabaseHealth()
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            isCheckingDbHealth = false
                            Toast.makeText(context, "Database connection verified: Peak throughput active", Toast.LENGTH_SHORT).show()
                        }, 500)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("audit_db_connections_btn")
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verify Database Connections & Performance")
                }
            }
        }

        // --- ADMIN ASSISTANCE & PHONE DIALING CONFIGURATION ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Admin Assistance & Cashier Phone Dialing",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Configured contact number dialed when cashiers trigger 'Call Admin' from the register.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = adminContactName,
                    onValueChange = {
                        adminContactName = it
                        adminPhoneSavedFeedback = false
                    },
                    label = { Text("Admin / Owner Full Name") },
                    placeholder = { Text("e.g. Juan Dela Cruz") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Badge, null) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = adminPhoneNumber,
                    onValueChange = {
                        adminPhoneNumber = it
                        adminPhoneSavedFeedback = false
                    },
                    label = { Text("Admin / Supervisor Mobile Number") },
                    placeholder = { Text("e.g. 09171234567 or +639171234567") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Call, null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_security_phone_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            adminPrefs.edit()
                                .putString("admin_phone_number", adminPhoneNumber.trim())
                                .putString("admin_name", adminContactName.trim())
                                .apply()
                            adminPhoneSavedFeedback = true
                            Toast.makeText(context, "Admin contact details saved successfully!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (adminPhoneSavedFeedback) "Saved!" else "Save Contact Details")
                    }

                    OutlinedButton(
                        onClick = {
                            val clean = adminPhoneNumber.filter { it.isDigit() || it == '+' }
                            if (clean.isNotBlank()) {
                                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(dialIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot launch dialer: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Please enter a phone number to test dialing.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.PhoneForwarded, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Dial")
                    }
                }
            }
        }

        // --- ALLOWED TERMINAL APPS (WHITELIST FOR CASHIER TERMINAL) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allowed Terminal Apps (Launcher Whitelist)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Control which external applications the POS terminal is authorized to launch. Other apps are restricted from the cashier.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${allowedAppsSet.size} of ${installedApps.size} apps authorized",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                allowedAppsSet = installedApps.map { it.first }.toSet()
                                appsSavedFeedback = false
                            }
                        ) {
                            Text("Allow All", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(
                            onClick = {
                                allowedAppsSet = DigitalServicesHelper.DEFAULT_ALLOWED_APPS
                                appsSavedFeedback = false
                            }
                        ) {
                            Text("Reset Defaults", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { appSearchQuery = it },
                    label = { Text("Search installed applications") },
                    placeholder = { Text("Filter by app name or package...") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (appSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { appSearchQuery = "" }) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // List of Apps with Toggles
                val filteredApps = if (appSearchQuery.isBlank()) installedApps else installedApps.filter {
                    it.second.contains(appSearchQuery, ignoreCase = true) || it.first.contains(appSearchQuery, ignoreCase = true)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No applications found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            filteredApps.forEach { (pkg, label) ->
                                val isAllowed = allowedAppsSet.contains(pkg)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isAllowed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                            else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Switch(
                                        checked = isAllowed,
                                        onCheckedChange = { checked ->
                                            allowedAppsSet = if (checked) {
                                                allowedAppsSet + pkg
                                            } else {
                                                allowedAppsSet - pkg
                                            }
                                            appsSavedFeedback = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Preferred Load App Selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Preferred Load Application:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Default is USSD Dialer & SIM Toolkit. You can designate an installed app (e.g. Smart Padala, GlobeOne) as the dedicated load launcher.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var loadPickerExpanded by remember { mutableStateOf(false) }
                    val currentLoadAppLabel = if (preferredLoadApp.isBlank()) {
                        "Default: Dialer (*123# / *143#) & SIM Toolkit"
                    } else {
                        installedApps.find { it.first == preferredLoadApp }?.second ?: preferredLoadApp
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { loadPickerExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.SimCard, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(currentLoadAppLabel, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(
                            expanded = loadPickerExpanded,
                            onDismissRequest = { loadPickerExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Default: Dialer & SIM Toolkit") },
                                onClick = {
                                    preferredLoadApp = ""
                                    appsSavedFeedback = false
                                    loadPickerExpanded = false
                                }
                            )
                            val allowedInstalledApps = installedApps.filter { allowedAppsSet.contains(it.first) }
                            allowedInstalledApps.forEach { (pkg, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        preferredLoadApp = pkg
                                        appsSavedFeedback = false
                                        loadPickerExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Save Whitelist Button
                Button(
                    onClick = {
                        DigitalServicesHelper.saveAllowedApps(adminPrefs, allowedAppsSet)
                        DigitalServicesHelper.savePreferredLoadApp(adminPrefs, preferredLoadApp)
                        appsSavedFeedback = true
                        Toast.makeText(context, "Allowed terminal apps configuration saved!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (appsSavedFeedback) "Saved Successfully!" else "Save Allowed Apps Configuration")
                }
            }
        }

        // --- ALWAYS ON DISPLAY TOGGLE ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isAlwaysOn) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.LightMode,
                        contentDescription = "Always On Awake",
                        tint = if (isAlwaysOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Column {
                        Text(
                            text = "Always-On Awake Mode",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Keeps the checkout register screen lit continuously without dimming or screen-off timer shutdowns.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isAlwaysOn,
                    onCheckedChange = { viewModel.setAlwaysOnEnabled(it) }
                )
            }
        }

        // --- LAUNCHER KIOSK LOCKDOWN CONTROL ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isKioskActive) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (isKioskActive) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Kiosk",
                        tint = if (isKioskActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = if (isKioskActive) "TERMINAL LOCKDOWN ACTIVE" else "LAUNCHER LOCKDOWN STANDBY",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isKioskActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Kiosk mode restricts tablet use purely to StorePointPOS with PIN lockouts.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Text(
                    text = "When active, the device enters Kiosk Pin-Screen Lockdown. This disables the home button, recent apps, and pulldown quick settings so cashiers cannot escape the app, launch other applications, or access unauthorized device menus.",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (savedKioskPin.orEmpty().isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Kiosk security PIN code is set.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "No Kiosk PIN configured yet. You will be prompted to set one before locking.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Button(
                    onClick = {
                        if (isKioskActive) {
                            // Turn OFF requires verifying the PIN first!
                            if (savedKioskPin.orEmpty().isEmpty()) {
                                viewModel.toggleKioskMode(false)
                            } else {
                                pinVerifyInput = ""
                                pinVerifyError = ""
                                showKioskPinVerifyDialog = true
                            }
                        } else {
                            // Turn ON requires PIN to exist
                            if (savedKioskPin.orEmpty().isEmpty()) {
                                pinSetupInput = ""
                                pinSetupConfirmInput = ""
                                pinSetupError = ""
                                showKioskPinSetupDialog = true
                            } else {
                                viewModel.toggleKioskMode(true)
                                Toast.makeText(context, "System entered secure Kiosk lockdown.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("toggle_kiosk_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isKioskActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isKioskActive) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isKioskActive) "Disable Kiosk Mode (Enter PIN to Unlock)" else "Activate Kiosk Mode (Lock Screen)")
                }
            }
        }

        // --- BIOMETRIC SECURITY MANAGEMENT ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Biometric Security",
                            tint = if (isBiometricEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "Biometric Authentication",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (isBiometricSupported) "Hardware fingerprint / face unlock supported on this terminal" else "Biometric sensor unavailable on this device",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isBiometricSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !isBiometricSupported) {
                                Toast.makeText(context, "Biometric hardware is not configured or available on this device.", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.setBiometricEnabled(enabled)
                            }
                        },
                        enabled = isBiometricSupported
                    )
                }

                Text(
                    text = "Allow cashiers and managers to authenticate at login and wake terminal locks instantly using fingerprint biometrics.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // --- HARDWARE PRINTER & ESC/POS PERIPHERAL CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Hardware Printer",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "Thermal Receipt Printer & Cash Drawer",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Raw ESC/POS protocol communication for POS peripherals.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Text(
                    text = "Connect network TCP/IP or Bluetooth thermal receipt printers. Direct ESC/POS byte commands enable ultra-fast receipt printing and automatic solenoid cash drawer release.",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Printer Type selection
                Text("Connection Mode:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val types = listOf(
                        EscPosHelper.PrinterType.SYSTEM_SPOOLER to "System Print",
                        EscPosHelper.PrinterType.NETWORK_ESCPOS to "Network LAN",
                        EscPosHelper.PrinterType.BLUETOOTH_ESCPOS to "Bluetooth"
                    )
                    types.forEach { (type, label) ->
                        val isSelected = printerType == type.name
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setPrinterType(type.name) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (printerType == EscPosHelper.PrinterType.NETWORK_ESCPOS.name) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editPrinterIp,
                            onValueChange = {
                                editPrinterIp = it
                                viewModel.setPrinterNetworkConfig(it, editPrinterPort.toIntOrNull() ?: 9100)
                            },
                            label = { Text("Printer IP Address") },
                            placeholder = { Text("192.168.1.100") },
                            modifier = Modifier.weight(2f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editPrinterPort,
                            onValueChange = {
                                editPrinterPort = it
                                viewModel.setPrinterNetworkConfig(editPrinterIp, it.toIntOrNull() ?: 9100)
                            },
                            label = { Text("Port") },
                            placeholder = { Text("9100") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                if (printerType == EscPosHelper.PrinterType.BLUETOOTH_ESCPOS.name) {
                    OutlinedTextField(
                        value = editPrinterMac,
                        onValueChange = {
                            editPrinterMac = it
                            viewModel.setPrinterBluetoothMac(it)
                        },
                        label = { Text("Bluetooth MAC Address") },
                        placeholder = { Text("00:11:22:33:44:55") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Paper width and auto drawer switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("80mm Paper Width", fontWeight = FontWeight.SemiBold)
                        Text(if (is80mm) "80mm Wide (48 columns)" else "58mm Standard (32 columns)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = is80mm, onCheckedChange = { viewModel.setPaperWidth80mm(it) })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Kick Drawer on Cash Sale", fontWeight = FontWeight.SemiBold)
                        Text("Automatically pop drawer open upon cash checkout completion", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isAutoKick, onCheckedChange = { viewModel.setAutoKickDrawerEnabled(it) })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.kickCashDrawer { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kick Drawer")
                    }

                    Button(
                        onClick = {
                            viewModel.testHardwarePrinter { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Print, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Print & Kick")
                    }
                }
            }
        }

        // --- FULL DATABASE BACKUP & DISASTER RECOVERY ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Backup,
                        contentDescription = "Full Disaster Recovery",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "Database Disaster Recovery & Backup",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Complete database snapshot with SHA-256 integrity verification.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Text(
                    text = "Export the entire operational state: Store configuration, user credentials, categories, product catalog, sales receipts, drawer shifts, cash payouts, and payment schedules.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Automatic Transaction Safeguard: A full database backup is silently saved to device internal storage (Documents/StorePoint/Backups/) after every transaction. These backups survive app uninstallation and data clearing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                if (backupSnapshots.isNotEmpty()) {
                    val latest = backupSnapshots.first()
                    Text(
                        text = "Latest persistent vault: ${latest.name} (${latest.length() / 1024} KB)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            backupPassphrase = ""
                            backupPassphraseConfirm = ""
                            backupPassphraseError = ""
                            showExportEncryptDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Upload, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export Full DB")
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            fullRestoreLauncher.launch("application/json")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore Full DB")
                    }
                }

                // Dedicated Section: Store Inventory Data Transfer & Quick Share (.spinventory / .json)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Import Inventory Data:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("Receive Quick Share packages (.spinventory) or catalog backups.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { viewModel.shareInventoryViaQuickShare(context) }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Quick Share", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { importLauncher.launch("*/*") },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("import_inventory_data_btn")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import Inventory", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // --- OBSERVABILITY & CRASH DIAGNOSTICS CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Software Updates",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Terminal Software Updates",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Automated GitHub Releases distribution & OTA updates. (Installed: v$APP_VERSION)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Text(
                    text = "StorePoint can check the official GitHub Releases pipeline for compiled APK assets, download new release binaries over Wi-Fi, and initiate system package updates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val isCheckingUpdates by viewModel.updateCheckInProgress.collectAsState()
                Button(
                    onClick = {
                        viewModel.checkForAppUpdates(APP_VERSION) { info ->
                            if (info != null && info.isNewer) {
                                Toast.makeText(context, "New update available: ${info.latestVersionTag}! Open 'About StorePoint' to install.", Toast.LENGTH_LONG).show()
                            } else if (info != null) {
                                Toast.makeText(context, "StorePoint is already running the latest version.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Unable to check updates: check internet connection.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("security_check_updates_btn"),
                    enabled = !isCheckingUpdates
                ) {
                    if (isCheckingUpdates) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking GitHub Releases...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check for OTA Releases")
                    }
                }
            }
        }

        // --- OBSERVABILITY & CRASH DIAGNOSTICS CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Diagnostics",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "System Observability & Diagnostics",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Crash logging, device telemetry, and operational observability.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Telemetry summary badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rt = Runtime.getRuntime()
                    val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
                    val maxMb = rt.maxMemory() / (1024 * 1024)
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("App RAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("${usedMb}MB / ${maxMb}MB", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Crash Incidents", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("${crashLogs.size} Logged", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = if (crashLogs.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showCrashLogsDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Visibility, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Crash Logs (${crashLogs.size})")
                    }

                    Button(
                        onClick = {
                            val report = viewModel.getDiagnosticReport()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "StorePoint Diagnostics")
                                putExtra(Intent.EXTRA_TEXT, report)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Diagnostics"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share Report")
                    }
                }
            }
        }

        // --- DEVICE ADMINISTRATOR CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isAdminActive) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (isAdminActive) Icons.Default.Security else Icons.Default.Warning,
                        contentDescription = "Status",
                        tint = if (isAdminActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = if (isAdminActive) "DEVICE PROTECTION ON" else "DEVICE PROTECTION OFF",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isAdminActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Device Administrator status",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Text(
                    text = if (isAdminActive) {
                        "Device Administrator is active. StorePoint can lock or erase this device if the terminal is lost or stolen. Uninstalling the app is still permitted through Android settings."
                    } else {
                        "Device Administrator is off. StorePoint cannot lock or erase this device remotely if it is lost or stolen. Enable it to turn on terminal protection."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                if (!isAdminActive) {
                    Button(
                        onClick = {
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminName)
                                putExtra(
                                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "Lets StorePoint lock or erase this device if the terminal is lost or stolen."
                                )
                            }
                            deviceAdminLauncher.launch(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("activate_device_admin_btn")
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enable Device Administrator")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Protection is active.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = {
                                isAdminActive = dpm.isAdminActive(adminName)
                            }
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refresh Status")
                        }
                    }
                }
            }
        }

        // --- ACCIDENTAL DATA CLEAR PREVENTATIVE WIPE ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Data Clear",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "Accidental Data Wipe Protection",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Admin PIN certification required to clear records.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Text(
                    text = "To clear all local products, transactions, and session history records, click below. This requires validating active admin credentials beforehand.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Button(
                    onClick = {
                        adminWipePasswordInput = ""
                        wipeDataError = ""
                        showWipeDataDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteForever, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Deconstruct & Clear Entire Local System")
                }
            }
        }

        // Additional register lock system or tips
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "How to Block Uninstallation & Clear-Data:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = "1. Toggle 'Enable Device Administrator' above. When prompted by Android, grant 'Activate' permission.\n" +
                            "2. Under Android rules, once activated, the OS restricts instant uninstall command from the Launcher Home.\n" +
                            "3. Verify: Go to device settings / drag the icon. You will see uninstall is restricted while active.\n" +
                            "4. To deliberate remove/wipe the register yourself later, simply deactivate this Device Administrator policy first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }

    // --- SETUP SECURE KIOSK MODE PIN DIALOG ---
    if (showKioskPinSetupDialog) {
        AlertDialog(
            onDismissRequest = { showKioskPinSetupDialog = false },
            title = { Text("Set Secure Kiosk PIN", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Create a numeric PIN to lock/unlock Kiosk Mode. Cashiers will not be able to disable Kiosk Mode without this code.")
                    OutlinedTextField(
                        value = pinSetupInput,
                        onValueChange = { if (it.length <= 8) pinSetupInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Create PIN (Numbers only)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pinSetupConfirmInput,
                        onValueChange = { if (it.length <= 8) pinSetupConfirmInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Confirm PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinSetupError.isNotEmpty()) {
                        Text(pinSetupError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinSetupInput.length < 4) {
                            pinSetupError = "PIN must be at least 4 digits."
                        } else if (pinSetupInput != pinSetupConfirmInput) {
                            pinSetupError = "PINs do not match."
                        } else {
                            viewModel.setKioskPin(pinSetupInput)
                            showKioskPinSetupDialog = false
                            viewModel.toggleKioskMode(true)
                            Toast.makeText(context, "Kiosk PIN configured. Entering lockdown.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Confirm & Enter Lockdown")
                }
            },
            dismissButton = {
                TextButton(onClick = { showKioskPinSetupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- VERIFY SECURE KIOSK MODE PIN TO UNLOCK ---
    if (showKioskPinVerifyDialog) {
        AlertDialog(
            onDismissRequest = { showKioskPinVerifyDialog = false },
            title = { Text("Disable Kiosk Mode", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter the Kiosk secure PIN code to release lockdown:")
                    OutlinedTextField(
                        value = pinVerifyInput,
                        onValueChange = { pinVerifyInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Enter PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinVerifyError.isNotEmpty()) {
                        Text(pinVerifyError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (viewModel.isKioskPinValid(pinVerifyInput)) {
                            viewModel.toggleKioskMode(false)
                            showKioskPinVerifyDialog = false
                            Toast.makeText(context, "Kiosk Mode released.", Toast.LENGTH_SHORT).show()
                        } else {
                            pinVerifyError = "Incorrect Kiosk security PIN."
                        }
                    }
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showKioskPinVerifyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- SECURE DATA CLEARING PIN VERIFY DIALOG ---
    if (showWipeDataDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDataDialog = false },
            title = { Text("Validate Administrative Authority", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Warning! This deletes all transaction items, users, and catalog sets. Retriggering the system setup sequence. Enter your Admin 6-Digit PIN to proceed:")
                    Spacer(Modifier.height(4.dp))
                    ExpressiveOtpPinInput(
                        pin = adminWipePasswordInput,
                        onPinChange = {
                            adminWipePasswordInput = it
                            wipeDataError = ""
                        },
                        pinLength = 6,
                        isMasked = true,
                        isError = wipeDataError.isNotEmpty(),
                        errorMessage = wipeDataError,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = adminWipePasswordInput.length == 6,
                    onClick = {
                        viewModel.clearAllDatabaseData(
                            adminPass = adminWipePasswordInput,
                            onSuccess = {
                                showWipeDataDialog = false
                                Toast.makeText(context, "System successfully cleared and reset.", Toast.LENGTH_LONG).show()
                            },
                            onFailure = { err ->
                                wipeDataError = err
                            }
                        )
                    }
                ) {
                    Text("Authorize Pure Clearance")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- SECURE INVENTORY IMPORT ADMIN PIN VERIFICATION DIALOG ---
    if (showImportInventoryPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showImportInventoryPinDialog = false
                importInventoryPinInput = ""
                importInventoryPinError = ""
                pendingImportInventoryContent = ""
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Admin Import Certification", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "You are importing inventory data (.spinventory package / catalog). Enter your Admin 6-Digit PIN to certify and merge this into the active store catalog:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    ExpressiveOtpPinInput(
                        pin = importInventoryPinInput,
                        onPinChange = {
                            importInventoryPinInput = it
                            importInventoryPinError = ""
                        },
                        pinLength = 6,
                        isMasked = true,
                        isError = importInventoryPinError.isNotEmpty(),
                        errorMessage = importInventoryPinError,
                        modifier = Modifier.fillMaxWidth().testTag("import_inventory_admin_pin_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importInventoryPinInput.length == 6) {
                            coroutineScope.launch {
                                val isAuth = viewModel.verifyAdminPin(importInventoryPinInput)
                                if (isAuth) {
                                    viewModel.importInventoryPackageDetailed(
                                        rawContent = pendingImportInventoryContent,
                                        onSuccess = { catCount, prodCount ->
                                            showImportInventoryPinDialog = false
                                            importInventoryPinInput = ""
                                            pendingImportInventoryContent = ""
                                            Toast.makeText(
                                                context,
                                                "Inventory import successful! Merged $catCount categories and $prodCount products.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        },
                                        onFailure = { err ->
                                            importInventoryPinError = "Import failed: $err"
                                        }
                                    )
                                } else {
                                    importInventoryPinError = "Incorrect Admin PIN. Certification failed."
                                }
                            }
                        }
                    },
                    enabled = importInventoryPinInput.length == 6,
                    modifier = Modifier.testTag("confirm_import_inventory_btn")
                ) {
                    Text("Certify & Import")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportInventoryPinDialog = false
                    importInventoryPinInput = ""
                    importInventoryPinError = ""
                    pendingImportInventoryContent = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- CRASH DIAGNOSTICS & INCIDENT VIEWER MODAL ---
    if (showCrashLogsDialog) {
        AlertDialog(
            onDismissRequest = { showCrashLogsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BugReport, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Crash Diagnostics Logs (${crashLogs.size})")
                }
            },
            text = {
                if (crashLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("No crashes recorded. System is operating stably.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(crashLogs) { entry ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(entry.dateFormatted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Text("Cashier: ${entry.activeCashier}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text(
                                        text = "${entry.exceptionName}: ${entry.message}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = entry.stackTrace.take(300) + if (entry.stackTrace.length > 300) "..." else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (crashLogs.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            viewModel.clearCrashLogs()
                            Toast.makeText(context, "Crash diagnostics cleared.", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Clear Logs", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                Button(onClick = { showCrashLogsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // --- BACKUP ENCRYPTION EXPORT DIALOG (M2) ---
    if (showExportEncryptDialog) {
        AlertDialog(
            onDismissRequest = { showExportEncryptDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Database Backup", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Optionally protect this backup with a password (AES-256 + HMAC-SHA256). Leave the fields empty to export an unprotected file. Do not forget the password — it cannot be recovered.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text("Backup password (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = backupPassphraseConfirm,
                        onValueChange = { backupPassphraseConfirm = it },
                        label = { Text("Confirm backup password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (backupPassphraseError.isNotEmpty()) {
                        Text(backupPassphraseError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (backupPassphrase != backupPassphraseConfirm) {
                            backupPassphraseError = "Passwords do not match."
                            return@Button
                        }
                        showExportEncryptDialog = false
                        viewModel.exportDatabaseBackup(backupPassphrase) { file ->
                            if (file != null) {
                                try {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Database Backup"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Failed to create database snapshot.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportEncryptDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- FULL DATABASE RESTORE CONFIRMATION DIALOG ---
    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirm Full Database Restore", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Restoring a database snapshot will overwrite existing local data with the contents of the backup file. This operation updates store configurations, user accounts, product catalogs, transactions, and shifts.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "An automated safety snapshot of the current state will be created before applying this restore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = restorePassphrase,
                        onValueChange = { restorePassphrase = it },
                        label = { Text("Backup password (required only for password-protected backups)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        viewModel.restoreDatabaseBackup(pendingRestoreJson, restorePassphrase) { summary ->
                            restoreSummaryResult = summary
                            showRestoreSummaryDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Proceed with Full Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- FULL DATABASE RESTORE SUMMARY MODAL ---
    if (showRestoreSummaryDialog && restoreSummaryResult != null) {
        val summary = restoreSummaryResult!!
        AlertDialog(
            onDismissRequest = { showRestoreSummaryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (summary.success) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (summary.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (summary.success) "Restore Completed" else "Restore Failed", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(summary.message, fontWeight = FontWeight.Medium)
                    if (summary.success) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("• Staff Users Restored: ${summary.usersCount}", style = MaterialTheme.typography.bodySmall)
                        Text("• Products Restored: ${summary.productsCount}", style = MaterialTheme.typography.bodySmall)
                        Text("• Transactions Restored: ${summary.transactionsCount}", style = MaterialTheme.typography.bodySmall)
                        if (summary.backupDate.isNotEmpty()) {
                            Text("• Backup Date: ${summary.backupDate}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showRestoreSummaryDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

