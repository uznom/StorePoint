package com.munzo.storepoint.ui.screens

import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.munzo.storepoint.ui.theme.ExpressiveButtonShape
import com.munzo.storepoint.ui.theme.ExpressiveSectionHeader
import com.munzo.storepoint.ui.theme.ExpressiveCardShape
import com.munzo.storepoint.ui.theme.ExpressiveChipShape
import com.munzo.storepoint.ui.theme.expressiveGlassCard
import com.munzo.storepoint.ui.theme.ExpressiveSplitButton
import com.munzo.storepoint.ui.theme.ExpressiveButtonGroup
import com.munzo.storepoint.ui.theme.glassPanel
import com.munzo.storepoint.ui.theme.tactileBounce
import com.munzo.storepoint.ui.theme.GCashColor
import com.munzo.storepoint.ui.theme.MayaColor
import com.munzo.storepoint.ui.theme.LoadColor
import com.munzo.storepoint.ui.components.ExpressiveButton
import com.munzo.storepoint.ui.components.ExpressiveButtonSize
import com.munzo.storepoint.ui.components.ExpressiveButtonVariant
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import com.munzo.storepoint.data.*
import com.munzo.storepoint.ui.BarcodeScannerDialog
import com.munzo.storepoint.ui.KioskStatusBar
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.util.PdfExportUtil
import com.munzo.storepoint.util.DigitalServicesHelper
import com.munzo.storepoint.util.PendingDigitalService
import com.munzo.storepoint.util.APP_VERSION
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierPOSScreen(
    viewModel: StorePointViewModel,
    onAdminDashboardNavigate: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val storeConfig by viewModel.storeConfig.collectAsState()
    val activeUser by viewModel.activeUser.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()

    val categories by viewModel.allCategories.collectAsState()
    val products by viewModel.allProducts.collectAsState()
    val cartItems by viewModel.cart.collectAsState()
    val parkedList by viewModel.parkedTransactions.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var activePortraitTab by remember { mutableStateOf(0) }
    var activeCatalogSection by remember { mutableStateOf(0) } // 0 = Standard Products, 1 = Digital Services

    val isProductsLoading = products.isEmpty() && categories.isEmpty()

    // Dialog flags
    var showShiftStartDialog by remember { mutableStateOf(false) }
    var showShiftCloseDialog by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showParkDialog by remember { mutableStateOf(false) }
    var showParkedTxDialog by remember { mutableStateOf(false) }
    var showReceiptDialog by remember { mutableStateOf(false) }
    var showReturnRefundDialog by remember { mutableStateOf(false) }
    var productForVariantPicker by remember { mutableStateOf<com.munzo.storepoint.data.Product?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showCallAdminDialog by remember { mutableStateOf(false) }
    var showTerminalAppsDialog by remember { mutableStateOf(false) }
    var showPostCheckoutDigitalDialog by remember { mutableStateOf(false) }
    var pendingDigitalServices by remember { mutableStateOf<List<PendingDigitalService>>(emptyList()) }
    val isScannerOpen by viewModel.isScannerOpen.collectAsState()
    val isKioskActive by viewModel.isKioskModeActive.collectAsState()

    // Last completed transaction for receipt viewing
    var lastCompletedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var lastCompletedItems by remember { mutableStateOf<List<TransactionItem>>(emptyList()) }

    // Shift drawer local states
    var initialCashInput by remember { mutableStateOf("100.00") }
    var initialGCashInput by remember { mutableStateOf("0.00") }
    var initialSmartLoadInput by remember { mutableStateOf("0.00") }
    var initialGlobeLoadInput by remember { mutableStateOf("0.00") }
    var closeCashInput by remember { mutableStateOf("250.00") }
    var closeGCashInput by remember { mutableStateOf("0.00") }
    var closeSmartLoadInput by remember { mutableStateOf("0.00") }
    var closeGlobeLoadInput by remember { mutableStateOf("0.00") }

    // Scanner inline controllers state
    var scannerQuantityInput by remember { mutableStateOf("1") }
    var manualBarcodeInput by remember { mutableStateOf("") }

    // Park/Hold note input
    var parkNoteInput by remember { mutableStateOf("") }

    // Active configuration currencies
    val curr = storeConfig?.currencySymbol ?: "$"
    var isSensitiveDataVisible by remember { mutableStateOf(true) }

    // Drawer scope & State Navigation
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val checkedCartItems = remember { mutableStateMapOf<Int, Boolean>() }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var longPressedCartItem by remember { mutableStateOf<com.munzo.storepoint.data.Product?>(null) }
    var itemPendingDelete by remember { mutableStateOf<com.munzo.storepoint.data.Product?>(null) }

    val selectedCartUoms by viewModel.selectedCartUoms.collectAsState()

    // Calculations based exclusively on CHECKED shopping cart items
    val activeCartItems = remember(cartItems) {
        derivedStateOf {
            cartItems.filter { checkedCartItems[it.first.id] != false }
        }
    }.value
    val doubleTaxRate = storeConfig?.taxPercentage ?: 12.0
    val grossCartTotal = activeCartItems.sumOf { 
        val uom = selectedCartUoms[it.first.id] ?: com.munzo.storepoint.data.UomOption("Base Unit", 1, it.first.price)
        uom.price * it.second
    }
    // Philippine VAT: retail prices displayed on product tags already include the 12% Value-Added Tax.
    // Formula: amount / 1.12 x .12
    val taxAmount = StorePointRepository.calculateVatFromInclusive(grossCartTotal, doubleTaxRate)
    val subtotal = StorePointRepository.calculateVatableSalesFromInclusive(grossCartTotal, doubleTaxRate)
    val totalAmount = StorePointRepository.roundMoney(grossCartTotal)

    // Session-check: Trigger drawer open state if activeSession is null
    LaunchedEffect(activeSession, storeConfig) {
        if (activeSession == null) {
            showShiftStartDialog = true
            storeConfig?.let {
                initialGCashInput = String.format(Locale.getDefault(), "%.2f", it.gcashBalance)
                initialSmartLoadInput = String.format(Locale.getDefault(), "%.2f", it.smartLoadBalance)
                initialGlobeLoadInput = String.format(Locale.getDefault(), "%.2f", it.globeLoadBalance)
            }
        }
    }

    // Global Key Events / Hotkeys listener modifier
    val hotkeyModifier = Modifier.onKeyEvent { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown) {
            when (keyEvent.key) {
                Key.F4 -> { // Park transaction hotkey
                    if (cartItems.isNotEmpty()) {
                        parkNoteInput = "Parked at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
                        showParkDialog = true
                    } else {
                        Toast.makeText(context, "Nothing to park. Cart is empty.", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                Key.F9 -> { // Payment process hotkey
                    if (activeCartItems.isNotEmpty() && activeSession != null) {
                        showPaymentDialog = true
                    } else if (activeSession == null) {
                        Toast.makeText(context, "Shift session must be active to pay.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No checked items in cart to checkout.", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        } else {
            false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                // Adaptive width: full 85% on narrow phones, capped at 320dp on wider screens
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .widthIn(max = 320.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Drawer Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(52.dp),
                            shape = ExpressiveButtonShape,
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 3.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "SP",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = storeConfig?.storeName ?: "StorePoint POS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            )
                            Text(
                                text = "Secure Cashier Drawer",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = "ACTIVE CASHIER PROFILE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .expressiveGlassCard(cornerRadius = 16.dp, elevation = 1.dp, accentGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(14.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = activeUser?.username ?: "Cashier",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = activeUser?.role ?: "CASHIER",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "SECURE CASH & DIGITAL BALANCES",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        letterSpacing = 0.5.sp
                    )

                    activeSession?.let { session ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Shift Started", style = MaterialTheme.typography.bodySmall)
                                val startTimeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.startTime))
                                Text(
                                    text = if (isSensitiveDataVisible) startTimeFormatted else "••:••",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Starting cash drawer", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = if (isSensitiveDataVisible) "$curr${String.format(Locale.getDefault(), "%.2f", session.startingCash)}" else "$curr••••",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (storeConfig?.hasGCash == true) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("GCash Balance", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            text = if (isSensitiveDataVisible) "$curr${String.format(Locale.getDefault(), "%.2f", storeConfig?.gcashBalance ?: 0.0)}" else "$curr••••",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }

                                    var showAdjustGCashDialogDrawer by remember { mutableStateOf(false) }
                                    TextButton(
                                        onClick = { showAdjustGCashDialogDrawer = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp).testTag("drawer_adjust_gcash_btn")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit GCash Balance", modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Adjust", style = MaterialTheme.typography.labelSmall)
                                    }

                                    if (showAdjustGCashDialogDrawer) {
                                        var tempBalStr by remember { mutableStateOf(storeConfig?.gcashBalance?.toString() ?: "0.00") }
                                        AlertDialog(
                                            onDismissRequest = { showAdjustGCashDialogDrawer = false },
                                            title = { Text("Update GCash Balance") },
                                            text = {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("Enter the accurate secondary GCash digital balance of this outlet's retailing account:", style = MaterialTheme.typography.bodyMedium)
                                                    OutlinedTextField(
                                                        value = tempBalStr,
                                                        onValueChange = { tempBalStr = it },
                                                        label = { Text("GCash Balance ($curr)") },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        modifier = Modifier.fillMaxWidth().testTag("temp_gcash_balance_field")
                                                    )
                                                }
                                            },
                                            confirmButton = {
                                                Button(
                                                    onClick = {
                                                        val parsedBal = tempBalStr.toDoubleOrNull() ?: storeConfig?.gcashBalance ?: 0.0
                                                        viewModel.updateGCashBalance(parsedBal)
                                                        showAdjustGCashDialogDrawer = false
                                                    }
                                                ) {
                                                    Text("Save")
                                                }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = { showAdjustGCashDialogDrawer = false }) {
                                                    Text("Cancel")
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            if (storeConfig?.hasLoad == true) {
                                // Smart / TNT Retailer Load Balance
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Smart/TNT Load Wallet", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            text = if (isSensitiveDataVisible) "$curr${String.format(Locale.getDefault(), "%.2f", storeConfig?.smartLoadBalance ?: 0.0)}" else "$curr••••",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    var showAdjustSmartDialogDrawer by remember { mutableStateOf(false) }
                                    TextButton(
                                        onClick = { showAdjustSmartDialogDrawer = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp).testTag("drawer_adjust_smart_btn")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Smart Load Balance", modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Adjust", style = MaterialTheme.typography.labelSmall)
                                    }

                                    if (showAdjustSmartDialogDrawer) {
                                        var tempBalStr by remember { mutableStateOf(storeConfig?.smartLoadBalance?.toString() ?: "0.00") }
                                        AlertDialog(
                                            onDismissRequest = { showAdjustSmartDialogDrawer = false },
                                            title = { Text("Update Smart/TNT Load Wallet") },
                                            text = {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("Enter current retailer load wallet balance for Smart/TNT (2% rebate active):", style = MaterialTheme.typography.bodyMedium)
                                                    OutlinedTextField(
                                                        value = tempBalStr,
                                                        onValueChange = { tempBalStr = it },
                                                        label = { Text("Smart Load Balance ($curr)") },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        modifier = Modifier.fillMaxWidth().testTag("temp_smart_balance_field")
                                                    )
                                                }
                                            },
                                            confirmButton = {
                                                Button(
                                                    onClick = {
                                                        val parsedBal = tempBalStr.toDoubleOrNull() ?: storeConfig?.smartLoadBalance ?: 0.0
                                                        viewModel.updateSmartLoadBalance(parsedBal)
                                                        showAdjustSmartDialogDrawer = false
                                                    }
                                                ) {
                                                    Text("Save")
                                                }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = { showAdjustSmartDialogDrawer = false }) {
                                                    Text("Cancel")
                                                }
                                            }
                                        )
                                    }
                                }

                                // Globe / TM Retailer Load Balance
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Globe/TM Load Wallet", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            text = if (isSensitiveDataVisible) "$curr${String.format(Locale.getDefault(), "%.2f", storeConfig?.globeLoadBalance ?: 0.0)}" else "$curr••••",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }

                                    var showAdjustGlobeDialogDrawer by remember { mutableStateOf(false) }
                                    TextButton(
                                        onClick = { showAdjustGlobeDialogDrawer = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp).testTag("drawer_adjust_globe_btn")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Globe Load Balance", modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Adjust", style = MaterialTheme.typography.labelSmall)
                                    }

                                    if (showAdjustGlobeDialogDrawer) {
                                        var tempBalStr by remember { mutableStateOf(storeConfig?.globeLoadBalance?.toString() ?: "0.00") }
                                        AlertDialog(
                                            onDismissRequest = { showAdjustGlobeDialogDrawer = false },
                                            title = { Text("Update Globe/TM Load Wallet") },
                                            text = {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("Enter current retailer load wallet balance for Globe/TM (2% rebate active):", style = MaterialTheme.typography.bodyMedium)
                                                    OutlinedTextField(
                                                        value = tempBalStr,
                                                        onValueChange = { tempBalStr = it },
                                                        label = { Text("Globe Load Balance ($curr)") },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        modifier = Modifier.fillMaxWidth().testTag("temp_globe_balance_field")
                                                    )
                                                }
                                            },
                                            confirmButton = {
                                                Button(
                                                    onClick = {
                                                        val parsedBal = tempBalStr.toDoubleOrNull() ?: storeConfig?.globeLoadBalance ?: 0.0
                                                        viewModel.updateGlobeLoadBalance(parsedBal)
                                                        showAdjustGlobeDialogDrawer = false
                                                    }
                                                ) {
                                                    Text("Save")
                                                }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = { showAdjustGlobeDialogDrawer = false }) {
                                                    Text("Cancel")
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider()

                    // Returns, Refunds & Exchanges Launcher
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.AssignmentReturn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        label = { Text("Returns & Refunds", fontWeight = FontWeight.SemiBold) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showReturnRefundDialog = true
                        },
                        modifier = Modifier.testTag("drawer_returns_refunds")
                    )

                    // Call Admin / Supervisor Assistance Request (Cashiers only, strictly hidden on admin accounts)
                    if (activeUser?.role?.equals("ADMIN", ignoreCase = true) != true) {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Call Admin", fontWeight = FontWeight.Bold)
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        Text(
                                            text = "HELP",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 0.5.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            },
                            selected = false,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                showCallAdminDialog = true
                            },
                            modifier = Modifier.testTag("drawer_call_admin")
                        )
                    }

                    // Admin Dashboard navigation in Drawer if they are admin
                    if (activeUser?.role == "ADMIN") {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                            label = { Text("Admin Dashboard") },
                            selected = false,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                onAdminDashboardNavigate()
                            },
                            modifier = Modifier.testTag("drawer_admin_dashboard")
                        )
                    }

                    // Shift session actions — single, non-duplicated source for shift + terminal actions.
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Shift Session",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (activeSession != null) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = if (activeSession != null) "SHIFT ACTIVE" else "NO ACTIVE SHIFT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = if (activeSession != null) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    ExpressiveButton(
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            if (activeSession != null) {
                                showShiftCloseDialog = true
                            } else {
                                Toast.makeText(context, "No active shift running. Start a shift first.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drawer_end_shift_btn"),
                        label = "End of Day Close Shift",
                        icon = Icons.Default.Assessment,
                        variant = ExpressiveButtonVariant.FILLED,
                        size = ExpressiveButtonSize.M,
                        enabled = activeSession != null,
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExpressiveButton(
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.logout()
                            onLogout()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drawer_logout_btn"),
                        label = "Log Out Terminal",
                        icon = Icons.Default.ExitToApp,
                        variant = ExpressiveButtonVariant.OUTLINED,
                        size = ExpressiveButtonSize.M,
                    )


                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "StorePoint POS Terminal v$APP_VERSION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Text(
                        text = "Developed by uznom (GitHub: @uznom)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp)
                            .clickable {
                                coroutineScope.launch { drawerState.close() }
                                showAboutDialog = true
                            }
                    )
                }
            }
        },
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(hotkeyModifier)
            ) {
                Scaffold(
                    topBar = {
                        Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                            if (isKioskActive) {
                                KioskStatusBar(viewModel = viewModel)
                            }
                            TopAppBar(
                                windowInsets = if (isKioskActive) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
                                navigationIcon = {
                                    IconButton(onClick = {
                                        coroutineScope.launch { drawerState.open() }
                                    }, modifier = Modifier.testTag("hamburger_menu_button")) {
                                        Icon(Icons.Default.Menu, contentDescription = "Open Sidebar Navigation")
                                    }
                                },
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // SP logo
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = ExpressiveButtonShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            shadowElevation = 2.dp
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "SP",
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f, fill = false)) {
                                            Text(
                                                text = storeConfig?.storeName ?: "StorePoint POS",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Transacting: ${activeUser?.username ?: "Cashier"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = { showReturnRefundDialog = true },
                                        modifier = Modifier.testTag("top_bar_return_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AssignmentReturn,
                                            contentDescription = "Returns & Refunds"
                                        )
                                    }
                                    IconButton(
                                        onClick = { showParkedTxDialog = true },
                                        modifier = Modifier.testTag("parked_sidebar_button")
                                    ) {
                                        BadgedBox(
                                            badge = {
                                                if (parkedList.isNotEmpty()) {
                                                    Badge { Text("${parkedList.size}") }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.HourglassEmpty,
                                                contentDescription = "Held Transactions (${parkedList.size})"
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { showTerminalAppsDialog = true },
                                        modifier = Modifier.testTag("top_bar_terminal_apps_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Apps,
                                            contentDescription = "Terminal Apps"
                                        )
                                    }
                                    IconButton(
                                        onClick = { showAboutDialog = true },
                                        modifier = Modifier.testTag("top_bar_about_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "About Creator"
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    // Main Workspace Layout with BoxWithConstraints for superb responsive design on mobile portrait
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .imePadding()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        val isPortrait = maxWidth < 650.dp

                    // Left Workspace Content: Catalog Search, Categories & Grid
                    @Composable
                    fun SariSariCatalogWorkspace() {
                        val showDigitalCategory = (storeConfig?.hasGCash == true) || (storeConfig?.hasMaya == true) || (storeConfig?.hasLoad == true)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            if (showDigitalCategory && !isPortrait) {
                                TabRow(
                                    selectedTabIndex = activeCatalogSection,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                    indicator = {}
                                ) {
                                    Tab(
                                        selected = activeCatalogSection == 0,
                                        onClick = { 
                                            activeCatalogSection = 0
                                            if (isPortrait) activePortraitTab = 1
                                        },
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Storefront, null, modifier = Modifier.size(18.dp))
                                                Text("Standard Products", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    )
                                    Tab(
                                        selected = activeCatalogSection == 1,
                                        onClick = { 
                                            activeCatalogSection = 1
                                            if (isPortrait) activePortraitTab = 2
                                        },
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(18.dp))
                                                Text("Digital Services", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    )
                                }
                            }

                            if (activeCatalogSection == 1 && showDigitalCategory) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    DigitalServicesForm(
                                        curr = curr,
                                        initialService = "GCash",
                                        initialNetwork = "Smart",
                                        onAddProductToCart = { dynamicProduct ->
                                            viewModel.addToCart(dynamicProduct)
                                        },
                                        initialGCashBalance = storeConfig?.gcashBalance ?: 0.0,
                                        smartLoadBalance = storeConfig?.smartLoadBalance ?: 0.0,
                                        globeLoadBalance = storeConfig?.globeLoadBalance ?: 0.0,
                                        isSensitiveDataVisible = isSensitiveDataVisible,
                                        hasGCash = storeConfig?.hasGCash ?: true,
                                        hasMaya = storeConfig?.hasMaya ?: true,
                                        hasLoad = storeConfig?.hasLoad ?: true,
                                        defaultLoadFee = storeConfig?.loadServiceFee ?: 2.0,
                                        defaultGcashFee = storeConfig?.gcashServiceFee ?: 10.0,
                                        defaultMayaFee = storeConfig?.mayaServiceFee ?: 10.0,
                                        defaultMayaBankFee = storeConfig?.mayaBankFee ?: 15.0,
                                        digitalCategoryId = categories.find { it.name.contains("GCash", ignoreCase = true) || it.name.contains("Digital", ignoreCase = true) }?.id ?: categories.firstOrNull()?.id ?: 1
                                    )
                                }
                            } else {
                            // Google M3 Expressive Pill Search Bar with integrated Scan button
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search product name, ID...", style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear search",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        FilledTonalIconButton(
                                            onClick = { viewModel.isScannerOpen.value = true },
                                            modifier = Modifier.testTag("barcode_cam_trigger")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.QrCodeScanner,
                                                contentDescription = "Scan",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                shape = CircleShape,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .testTag("catalog_search_bar"),
                                singleLine = true
                            )



                             // Horizontal Category row filters
                             Row(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .horizontalScroll(rememberScrollState())
                                     .padding(vertical = 6.dp),
                                 verticalAlignment = Alignment.CenterVertically
                             ) {
                                Text(
                                    "Filter: ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.width(8.dp))

                                // "All" chip
                                FilterChip(
                                    selected = selectedCategory == null,
                                    onClick = { selectedCategory = null },
                                    label = { Text("All") },
                                    modifier = Modifier.testTag("cat_chip_all")
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                val showDigitalCategory = (storeConfig?.hasGCash == true) || (storeConfig?.hasMaya == true) || (storeConfig?.hasLoad == true)
                                categories.filterNot { cat ->
                                    cat.name.contains("Digital", ignoreCase = true) || cat.name.contains("Service", ignoreCase = true)
                                }.forEach { cat ->
                                    FilterChip(
                                        selected = selectedCategory?.id == cat.id,
                                        onClick = { selectedCategory = cat },
                                        label = { Text(cat.name) },
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .testTag("cat_chip_${cat.id}")
                                    )
                                }
                            }

                            // Product Grid Catalog
                            val filteredProducts = products.filter { p ->
                                val categoryName = categories.find { it.id == p.categoryId }?.name ?: ""
                                val isOldDigital = p.name.contains("GCash Cash-In", ignoreCase = true) || 
                                                   p.name.contains("GCash Cash-Out", ignoreCase = true) || 
                                                   p.name.contains("Smart Load", ignoreCase = true) ||
                                                   categoryName.contains("Digital", ignoreCase = true)
                                
                                !isOldDigital && (selectedCategory == null || p.categoryId == selectedCategory?.id) &&
                                        (p.name.contains(searchQuery, ignoreCase = true) || p.barcode.contains(searchQuery))
                            }

                            val searchQueryMatchedDigital = searchQuery.isNotEmpty() && (
                                searchQuery.contains("gcash", ignoreCase = true) || 
                                searchQuery.contains("g-cash", ignoreCase = true) || 
                                searchQuery.contains("g cash", ignoreCase = true) || 
                                searchQuery.contains("maya", ignoreCase = true) || 
                                searchQuery.contains("load", ignoreCase = true) || 
                                searchQuery.contains("smart", ignoreCase = true) || 
                                searchQuery.contains("tnt", ignoreCase = true) || 
                                searchQuery.contains("globe", ignoreCase = true) || 
                                searchQuery.contains("tm", ignoreCase = true)
                            )
                            val isDigitalActive = searchQueryMatchedDigital

                            if (isDigitalActive) {
                                val initialService = when {
                                    searchQuery.contains("gcash", ignoreCase = true) || searchQuery.contains("g-cash", ignoreCase = true) || searchQuery.contains("g cash", ignoreCase = true) -> "GCash"
                                    searchQuery.contains("maya", ignoreCase = true) -> "Maya"
                                    searchQuery.contains("load", ignoreCase = true) || searchQuery.contains("smart", ignoreCase = true) || searchQuery.contains("tnt", ignoreCase = true) || searchQuery.contains("globe", ignoreCase = true) || searchQuery.contains("tm", ignoreCase = true) -> "Load"
                                    else -> "GCash"
                                }
                                val initialNetwork = when {
                                    searchQuery.contains("smart", ignoreCase = true) -> "Smart"
                                    searchQuery.contains("tnt", ignoreCase = true) -> "TNT"
                                    searchQuery.contains("globe", ignoreCase = true) -> "Globe"
                                    searchQuery.contains("tm", ignoreCase = true) -> "TM"
                                    else -> "Smart"
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    DigitalServicesForm(
                                        curr = curr,
                                        initialService = initialService,
                                        initialNetwork = initialNetwork,
                                        onAddProductToCart = { dynamicProduct ->
                                            viewModel.addToCart(dynamicProduct)
                                            searchQuery = "" // Clear search so catalog resets
                                        },
                                        initialGCashBalance = storeConfig?.gcashBalance ?: 0.0,
                                        smartLoadBalance = storeConfig?.smartLoadBalance ?: 0.0,
                                        globeLoadBalance = storeConfig?.globeLoadBalance ?: 0.0,
                                        isSensitiveDataVisible = isSensitiveDataVisible,
                                        hasGCash = storeConfig?.hasGCash ?: true,
                                        hasMaya = storeConfig?.hasMaya ?: true,
                                        hasLoad = storeConfig?.hasLoad ?: true,
                                        defaultLoadFee = storeConfig?.loadServiceFee ?: 2.0,
                                        defaultGcashFee = storeConfig?.gcashServiceFee ?: 10.0,
                                        defaultMayaFee = storeConfig?.mayaServiceFee ?: 10.0,
                                        defaultMayaBankFee = storeConfig?.mayaBankFee ?: 15.0,
                                        digitalCategoryId = categories.find { it.name.contains("GCash", ignoreCase = true) || it.name.contains("Digital", ignoreCase = true) }?.id ?: categories.firstOrNull()?.id ?: 1
                                    )
                                }
                            } else if (isProductsLoading) {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 130.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(12) {
                                        ShimmerProductCard()
                                    }
                                }
                            } else if (filteredProducts.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "No products found to match filters.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        Text(
                                            "Ensure items are setup in the Admin dashboard inventory catalog.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 150.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(filteredProducts) { item ->
                                        val isLowStock = item.stockCount in 1..5
                                        val isOutOfStock = item.stockCount <= 0
                                        val availableUoms = viewModel.getActiveUomsForProduct(item)
                                        val isGCash = item.name.contains("GCash", ignoreCase = true)
                                        val isSmart = item.name.contains("Smart", ignoreCase = true) || item.name.contains("TNT", ignoreCase = true)
                                        val isGlobe = item.name.contains("Globe", ignoreCase = true) || item.name.contains("TM", ignoreCase = true)

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .tactileBounce {
                                                    if (!isOutOfStock) {
                                                        if (availableUoms.size > 1) {
                                                            productForVariantPicker = item
                                                        } else {
                                                            viewModel.addToCart(item)
                                                            viewModel.playBeep()
                                                        }
                                                    }
                                                }
                                                .testTag("product_card_${item.id}"),
                                            shape = ExpressiveCardShape,
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isOutOfStock) {
                                                    MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.4f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerLow
                                                }
                                            ),
                                            border = if (isOutOfStock || isLowStock) {
                                                BorderStroke(
                                                    width = 1.5.dp,
                                                    color = if (isOutOfStock) 
                                                        MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                                    else 
                                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                                                )
                                            } else null,
                                            elevation = CardDefaults.cardElevation(
                                                defaultElevation = if (isOutOfStock) 0.dp else 2.dp
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Category Tag and Stock Pill
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val categoryName = categories.find { it.id == item.categoryId }?.name ?: "General"
                                                    Surface(
                                                        shape = ExpressiveChipShape,
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    ) {
                                                        Text(
                                                            text = categoryName,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }

                                                    Surface(
                                                        shape = ExpressiveChipShape,
                                                        color = when {
                                                            isOutOfStock -> MaterialTheme.colorScheme.errorContainer
                                                            isLowStock -> MaterialTheme.colorScheme.tertiaryContainer
                                                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                                        },
                                                        modifier = Modifier.padding(start = 8.dp)
                                                    ) {
                                                        Text(
                                                            text = when {
                                                                isOutOfStock -> "OUT"
                                                                isLowStock -> "LOW: ${item.stockCount}"
                                                                else -> "${item.stockCount} left"
                                                            },
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = when {
                                                                isOutOfStock -> MaterialTheme.colorScheme.onErrorContainer
                                                                isLowStock -> MaterialTheme.colorScheme.onTertiaryContainer
                                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                            },
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }

                                                // Fixed 2-Line Product Title for Perfect Alignment
                                                Text(
                                                    text = item.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isOutOfStock) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 2,
                                                    minLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                // Bottom Price and Action Row
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "$curr${String.format(Locale.getDefault(), "%.2f", item.price)}",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Black,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )

                                                    if (availableUoms.size > 1) {
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text(
                                                                text = "${availableUoms.size} UOM",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    } else if (isGCash || isSmart || isGlobe) {
                                                        val context = LocalContext.current
                                                        IconButton(
                                                            onClick = {
                                                                try {
                                                                    if (isGCash) {
                                                                        val pkg = "com.globe.gcash.android"
                                                                        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                                                                        if (intent != null) {
                                                                            context.startActivity(intent)
                                                                        } else {
                                                                            val playIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkg"))
                                                                            playIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                            context.startActivity(playIntent)
                                                                        }
                                                                    } else {
                                                                        val ussd = if (isSmart) "*343#" else "*100#"
                                                                        val dialIntent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                                                            data = android.net.Uri.parse("tel:" + android.net.Uri.encode(ussd))
                                                                        }
                                                                        context.startActivity(dialIntent)
                                                                    }
                                                                } catch (_: Exception) {}
                                                            },
                                                            modifier = Modifier.size(26.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Launch,
                                                                contentDescription = "Dial/App",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            } // Closes else
                        } // Closes Column
                    } // Closes SariSariCatalogWorkspace
                    val catalogContent = @Composable { SariSariCatalogWorkspace() }

                    // Right Workspace Content: Checkout Register Card
                    val registerContent = @Composable {
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ExpressiveSectionHeader(
                                    title = "Checkout Register",
                                    subtitle = "Scan items and take payment",
                                    icon = Icons.Default.PointOfSale
                                )

                                // 1) INLINE CUSTOMER QUANTITY + BARCODE SCANNER
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = scannerQuantityInput,
                                        onValueChange = { scannerQuantityInput = it },
                                        label = { Text("Qty") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.width(76.dp).testTag("scanner_qty_field"),
                                        singleLine = true
                                    )

                                    Box(modifier = Modifier.weight(1f)) {
                                        var isDropdownExpanded by remember { mutableStateOf(false) }
                                        val matchedSuggestions = remember(manualBarcodeInput, products) {
                                            if (manualBarcodeInput.isBlank()) emptyList()
                                            else products.filter {
                                                it.name.contains(manualBarcodeInput, ignoreCase = true) ||
                                                it.barcode.contains(manualBarcodeInput, ignoreCase = true)
                                            }.take(5)
                                        }

                                        OutlinedTextField(
                                            value = manualBarcodeInput,
                                            onValueChange = { 
                                                manualBarcodeInput = it 
                                                isDropdownExpanded = true
                                            },
                                            label = { Text("Scan/Search Item") },
                                            placeholder = { Text("Barcode or name") },
                                            trailingIcon = {
                                                IconButton(onClick = { viewModel.isScannerOpen.value = true }) {
                                                    Icon(Icons.Default.QrCodeScanner, "Launch camera")
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().testTag("scanner_barcode_field"),
                                            singleLine = true,
                                            keyboardActions = KeyboardActions(onDone = {
                                                if (manualBarcodeInput.isNotBlank()) {
                                                    val scanQty = scannerQuantityInput.toIntOrNull() ?: 1
                                                    viewModel.handleBarcodeScan(manualBarcodeInput, scanQty)
                                                    manualBarcodeInput = ""
                                                    isDropdownExpanded = false
                                                }
                                            })
                                        )

                                        if (isDropdownExpanded && matchedSuggestions.isNotEmpty()) {
                                            DropdownMenu(
                                                expanded = isDropdownExpanded,
                                                onDismissRequest = { isDropdownExpanded = false },
                                                modifier = Modifier.fillMaxWidth(0.95f).heightIn(max = 280.dp)
                                            ) {
                                                matchedSuggestions.forEach { prod ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(prod.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                    Text(
                                                                        text = "Code: ${prod.barcode.ifEmpty { "None" }} • Stock: ${prod.stockCount}", 
                                                                        style = MaterialTheme.typography.labelSmall, 
                                                                        color = MaterialTheme.colorScheme.outline
                                                                    )
                                                                }
                                                                Text(
                                                                    text = "$curr${String.format(java.util.Locale.getDefault(), "%.2f", prod.price)}", 
                                                                    style = MaterialTheme.typography.labelMedium, 
                                                                    fontWeight = FontWeight.Bold, 
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.padding(start = 8.dp)
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            val scanQty = scannerQuantityInput.toIntOrNull() ?: 1
                                                            viewModel.addToCart(prod, scanQty)
                                                            viewModel.playBeep()
                                                            Toast.makeText(context, "Added: $scanQty x ${prod.name}", Toast.LENGTH_SHORT).show()
                                                            manualBarcodeInput = ""
                                                            isDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            if (manualBarcodeInput.isNotBlank()) {
                                                val scanQty = scannerQuantityInput.toIntOrNull() ?: 1
                                                viewModel.handleBarcodeScan(manualBarcodeInput, scanQty)
                                                manualBarcodeInput = ""
                                            } else {
                                                Toast.makeText(context, "Please scan or enter a barcode first", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                                    ) {
                                        Icon(Icons.Default.Add, "Add barcode item")
                                    }
                                }

                                // 2) TRANSACTION LIST WITH CHECKBOX + LINE TOTAL PRICE
                                if (cartItems.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.ShoppingCart,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                modifier = Modifier.size(56.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "Cart Register is empty",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Text(
                                                "Scan items or tap on left catalog folders.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        item {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                val allChecked = cartItems.all { checkedCartItems[it.first.id] != false }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(
                                                        checked = allChecked && cartItems.isNotEmpty(),
                                                        onCheckedChange = { allCheckedValue ->
                                                            cartItems.forEach {
                                                                checkedCartItems[it.first.id] = allCheckedValue
                                                            }
                                                        },
                                                        modifier = Modifier.testTag("bulk_select_checkbox")
                                                    )
                                                    Text("Select All", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                }
                                                
                                                val checkedCount = cartItems.count { checkedCartItems[it.first.id] != false }
                                                if (checkedCount > 0) {
                                                    TextButton(
                                                        onClick = { showBulkDeleteConfirm = true },
                                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                        modifier = Modifier.testTag("bulk_delete_button")
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Delete Checked", modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Delete Selected ($checkedCount)", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }

                                        items(cartItems) { lineItem ->
                                            val product = lineItem.first
                                            val qty = lineItem.second
                                            val isChecked = checkedCartItems[product.id] ?: true
                                            val activeUom = selectedCartUoms[product.id] ?: com.munzo.storepoint.data.UomOption("Base Unit", 1, product.price); val linePriceTotal = activeUom.price * qty

                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .pointerInput(product.id) {
                                                        detectTapGestures(
                                                            onLongPress = {
                                                                longPressedCartItem = product
                                                            }
                                                        )
                                                    },
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isChecked) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLowest
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    // Top Row: Checkbox, Name, and Delete
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = isChecked,
                                                            onCheckedChange = { checkedCartItems[product.id] = it },
                                                            modifier = Modifier.testTag("item_check_${product.id}")
                                                        )

                                                        Spacer(modifier = Modifier.width(6.dp))

                                                        Text(
                                                            text = product.name,
                                                            fontWeight = FontWeight.Bold,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )

                                                        IconButton(
                                                            onClick = { itemPendingDelete = product },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Delete,
                                                                contentDescription = "Delete item",
                                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }

                                                    // Bottom Row: Price/UOM and Quantity Stepper + Line Total
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        // Price and UOM selector
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Text(
                                                                text = "$curr${String.format(Locale.getDefault(), "%.2f", activeUom.price)}",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = MaterialTheme.colorScheme.outline
                                                            )

                                                            val uomsList = viewModel.getActiveUomsForProduct(product)
                                                            if (uomsList.size > 1) {
                                                                var showUomMenu by remember { mutableStateOf(false) }
                                                                Box {
                                                                    Surface(
                                                                        onClick = { showUomMenu = true },
                                                                        shape = RoundedCornerShape(6.dp),
                                                                        color = MaterialTheme.colorScheme.secondaryContainer
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Text(activeUom.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                                                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                                                        }
                                                                    }
                                                                    DropdownMenu(
                                                                        expanded = showUomMenu,
                                                                        onDismissRequest = { showUomMenu = false }
                                                                    ) {
                                                                        uomsList.forEach { option ->
                                                                            DropdownMenuItem(
                                                                                text = { Text("${option.name} ($curr${String.format(Locale.getDefault(), "%.2f", option.price)})") },
                                                                                onClick = {
                                                                                    viewModel.selectCartUom(product.id, option)
                                                                                    showUomMenu = false
                                                                                }
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        // Stepper + Line Total
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Surface(
                                                                shape = RoundedCornerShape(8.dp),
                                                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                                                                ) {
                                                                    IconButton(
                                                                        onClick = { viewModel.updateCartQuantity(product, qty - 1) },
                                                                        modifier = Modifier.size(26.dp)
                                                                    ) {
                                                                        Icon(Icons.Default.Remove, "Less", modifier = Modifier.size(12.dp))
                                                                    }
                                                                    Text(
                                                                        text = "$qty",
                                                                        style = MaterialTheme.typography.labelMedium,
                                                                        fontWeight = FontWeight.Bold,
                                                                        modifier = Modifier.padding(horizontal = 4.dp)
                                                                    )
                                                                    IconButton(
                                                                        onClick = { viewModel.updateCartQuantity(product, qty + 1) },
                                                                        modifier = Modifier.size(26.dp)
                                                                    ) {
                                                                        Icon(Icons.Default.Add, "Add", modifier = Modifier.size(12.dp))
                                                                    }
                                                                }
                                                            }

                                                            Text(
                                                                text = "$curr${String.format(Locale.getDefault(), "%.2f", linePriceTotal)}",
                                                                fontWeight = FontWeight.Black,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Math Subtotals
                                HorizontalDivider()

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("VATable Sales (Net)", style = MaterialTheme.typography.bodySmall)
                                        Text("$curr${String.format(Locale.getDefault(), "%.2f", subtotal)}")
                                    }
                                    if (doubleTaxRate > 0.0) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("VAT (${String.format(Locale.US, "%.1f", doubleTaxRate)}% Included)", style = MaterialTheme.typography.bodySmall)
                                            Text("$curr${String.format(Locale.getDefault(), "%.2f", taxAmount)}")
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("TOTAL AMOUNT (VAT Inc.)", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                        Text("$curr${String.format(Locale.getDefault(), "%.2f", totalAmount)}", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                // Action hotkeys guides / Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.clearCart() },
                                        enabled = cartItems.isNotEmpty(),
                                        modifier = Modifier.size(44.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = "Clear Cart",
                                            tint = if (cartItems.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            parkNoteInput = "Parked Note #${parkedList.size + 1}"
                                            showParkDialog = true
                                        },
                                        enabled = cartItems.isNotEmpty(),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Pause, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Park")
                                    }
                                }

                                ExpressiveButton(
                                    onClick = { showPaymentDialog = true },
                                    enabled = activeCartItems.isNotEmpty() && activeSession != null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("checkout_charge_button"),
                                    label = "Charge $curr${String.format(Locale.getDefault(), "%.2f", totalAmount)} [F9]",
                                    icon = Icons.Default.ShoppingBag,
                                    variant = ExpressiveButtonVariant.FILLED,
                                    size = ExpressiveButtonSize.L,
                                )
                            }
                        }
                    }

                    if (isPortrait) {
                        // Portrait layout: Smoothly switch between Checkout Cart, Products, and Digital Services
                        Column(modifier = Modifier.fillMaxSize()) {
                            val showDigital = (storeConfig?.hasGCash == true) || (storeConfig?.hasMaya == true) || (storeConfig?.hasLoad == true)
                            // Clean bounds safety check: if selectedTabIndex is invalid because digital was disabled
                            val selectedIndex = if (!showDigital && activePortraitTab == 2) 1 else activePortraitTab

                            TabRow(
                                selectedTabIndex = selectedIndex,
                                modifier = Modifier.fillMaxWidth().testTag("pos_portrait_tab_row")
                            ) {
                                Tab(
                                    selected = selectedIndex == 0,
                                    onClick = { activePortraitTab = 0 },
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            BadgedBox(badge = {
                                                if (cartItems.isNotEmpty()) {
                                                    Badge { Text("${cartItems.sumOf { it.second }}") }
                                                }
                                            }) {
                                                Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(18.dp))
                                            }
                                            Text(
                                                text = "Cart (${cartItems.size})",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    },
                                    modifier = Modifier.testTag("tab_checkout_cart")
                                )
                                Tab(
                                    selected = selectedIndex == 1,
                                    onClick = { 
                                        activePortraitTab = 1
                                        activeCatalogSection = 0
                                    },
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.Storefront, null, modifier = Modifier.size(18.dp))
                                            Text(
                                                text = "Catalog",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    },
                                    modifier = Modifier.testTag("tab_standard_products")
                                )
                                if (showDigital) {
                                    Tab(
                                        selected = selectedIndex == 2,
                                        onClick = { 
                                            activePortraitTab = 2
                                            activeCatalogSection = 1
                                        },
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(18.dp))
                                                Text(
                                                    text = "Digital",
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        },
                                        modifier = Modifier.testTag("tab_digital_services")
                                    )
                                }
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                if (selectedIndex == 0) {
                                    registerContent()
                                } else {
                                    catalogContent()
                                }
                            }
                        }
                    } else {
                        // Landscape/Wide layout: Side-by-side Sari-Sari Digital Retailing & Checkout Register
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.fillMaxHeight().weight(1.0f)) {
                                catalogContent()
                            }
                            Box(modifier = Modifier.fillMaxHeight().weight(1.0f)) {
                                registerContent()
                            }
                        }
                    }
                }
            }
        }
    }
)

        // --- Dialogs & Panels ---

        // 1) Shift start dialog
        if (showShiftStartDialog) {
            AlertDialog(
                onDismissRequest = {}, // Force compliance: must enter shift to use POS
                confirmButton = {
                    ExpressiveButton(
                        onClick = {
                            val amount = initialCashInput.toDoubleOrNull() ?: 0.0
                            val gcashBal = initialGCashInput.toDoubleOrNull() ?: 0.0
                            val smartBal = initialSmartLoadInput.toDoubleOrNull() ?: 0.0
                            val globeBal = initialGlobeLoadInput.toDoubleOrNull() ?: 0.0
                            viewModel.startCashierShift(amount)
                            if (storeConfig?.hasGCash == true) {
                                viewModel.updateGCashBalance(gcashBal)
                            }
                            if (storeConfig?.hasLoad == true) {
                                viewModel.updateSmartLoadBalance(smartBal)
                                viewModel.updateGlobeLoadBalance(globeBal)
                            }
                            showShiftStartDialog = false
                        },
                        modifier = Modifier.testTag("shift_start_confirm"),
                        label = "Register Drawer Start",
                        variant = ExpressiveButtonVariant.FILLED,
                        size = ExpressiveButtonSize.M,
                    )
                },
                title = { Text("Open Shift Drawer Session") },
                text = {
                    Column(
                        modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Welcome cashier! Register the physical starting cash float and the digital wallet balance to start your shift session.")
                        
                        OutlinedTextField(
                            value = initialCashInput,
                            onValueChange = { initialCashInput = it },
                            label = { Text("Starting Cash float ($curr)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("starting_cash_input")
                        )

                        if (storeConfig?.hasGCash == true) {
                            OutlinedTextField(
                                value = initialGCashInput,
                                onValueChange = { initialGCashInput = it },
                                label = { Text("Starting GCash/Digital Balance ($curr)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag("starting_gcash_input")
                            )
                        }

                        if (storeConfig?.hasLoad == true) {
                            OutlinedTextField(
                                value = initialSmartLoadInput,
                                onValueChange = { initialSmartLoadInput = it },
                                label = { Text("Starting Smart/TNT Load Wallet ($curr)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag("starting_smart_load_input")
                            )

                            OutlinedTextField(
                                value = initialGlobeLoadInput,
                                onValueChange = { initialGlobeLoadInput = it },
                                label = { Text("Starting Globe/TM Load Wallet ($curr)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag("starting_globe_load_input")
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "This automatically pre-fills with the ending GCash balance from your previous shift.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }

        // 2) Shift closure dialog
        if (showShiftCloseDialog) {
            val transactionsList by viewModel.allTransactions.collectAsState()
            val sessionTransactions = remember(transactionsList, activeSession) {
                transactionsList.filter { it.timestamp >= (activeSession?.startTime ?: 0L) }
            }
            val totalSalesInSession = remember(sessionTransactions) {
                sessionTransactions.sumOf { it.totalAmount }
            }
            val cashSalesInSession = remember(sessionTransactions) {
                sessionTransactions.filter { it.paymentMethod == "CASH" }.sumOf { it.totalAmount }
            }
            val nonCashSalesInSession = remember(sessionTransactions) {
                sessionTransactions.filter { it.paymentMethod != "CASH" }.sumOf { it.totalAmount }
            }
            val sessionVatableSales = remember(totalSalesInSession, doubleTaxRate) {
                StorePointRepository.calculateVatableSalesFromInclusive(totalSalesInSession, doubleTaxRate)
            }
            val sessionVatAmount = remember(totalSalesInSession, doubleTaxRate) {
                StorePointRepository.calculateVatFromInclusive(totalSalesInSession, doubleTaxRate)
            }
            val drawerPayoutsList by viewModel.allDrawerTransactions.collectAsState()
            val sessionPayouts = remember(drawerPayoutsList, activeSession) {
                drawerPayoutsList.filter {
                    it.sessionId == (activeSession?.id ?: 0)
                }.sumOf { it.amount }
            }
            val expectedCash = (activeSession?.startingCash ?: 0.0) + cashSalesInSession + sessionPayouts
            val expectedGCash = storeConfig?.gcashBalance ?: 0.0
            val expectedSmartLoad = storeConfig?.smartLoadBalance ?: 0.0
            val expectedGlobeLoad = storeConfig?.globeLoadBalance ?: 0.0

            val enteredCash = closeCashInput.toDoubleOrNull() ?: 0.0
            val enteredGCash = closeGCashInput.toDoubleOrNull() ?: 0.0
            val enteredSmartLoad = closeSmartLoadInput.toDoubleOrNull() ?: 0.0
            val enteredGlobeLoad = closeGlobeLoadInput.toDoubleOrNull() ?: 0.0

            val cashShortage = (expectedCash - enteredCash).coerceAtLeast(0.0)
            val gcashShortage = (expectedGCash - enteredGCash).coerceAtLeast(0.0)
            val smartLoadShortage = (expectedSmartLoad - enteredSmartLoad).coerceAtLeast(0.0)
            val globeLoadShortage = (expectedGlobeLoad - enteredGlobeLoad).coerceAtLeast(0.0)

            // Auto-fill inputs if default
            LaunchedEffect(showShiftCloseDialog) {
                closeCashInput = String.format(Locale.getDefault(), "%.2f", expectedCash)
                closeGCashInput = String.format(Locale.getDefault(), "%.2f", expectedGCash)
                closeSmartLoadInput = String.format(Locale.getDefault(), "%.2f", expectedSmartLoad)
                closeGlobeLoadInput = String.format(Locale.getDefault(), "%.2f", expectedGlobeLoad)
            }

            AlertDialog(
                onDismissRequest = { showShiftCloseDialog = false },
                confirmButton = {
                    ExpressiveButton(
                        onClick = {
                            viewModel.closeCashierShift(
                                endingCash = enteredCash,
                                endingGCash = if (storeConfig?.hasGCash == true) enteredGCash else null,
                                endingSmartLoad = if (storeConfig?.hasLoad == true) enteredSmartLoad else null,
                                endingGlobeLoad = if (storeConfig?.hasLoad == true) enteredGlobeLoad else null
                            )
                            showShiftCloseDialog = false
                        },
                        modifier = Modifier.testTag("shift_close_confirm"),
                        label = "Confirm & Close Shift",
                        variant = ExpressiveButtonVariant.FILLED,
                        size = ExpressiveButtonSize.M,
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showShiftCloseDialog = false }) {
                        Text("Cancel")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("End of Day Close Shift (Z-Reading)", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Verify cash drawer contents and digital balances to complete the cashier shift closure.", style = MaterialTheme.typography.bodyMedium)

                        // Shift Performance Summary Card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Cashier:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(activeSession?.cashierUsername ?: "Cashier", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Shift Started:", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(activeSession?.startTime ?: System.currentTimeMillis())),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Transactions:", style = MaterialTheme.typography.bodySmall)
                                    Text("${sessionTransactions.size}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Gross Sales (VAT Inc.):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("$curr${String.format(Locale.getDefault(), "%.2f", totalSalesInSession)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("VATable Sales (Net):", style = MaterialTheme.typography.bodySmall)
                                    Text("$curr${String.format(Locale.getDefault(), "%.2f", sessionVatableSales)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("12% VAT Collected:", style = MaterialTheme.typography.bodySmall)
                                    Text("$curr${String.format(Locale.getDefault(), "%.2f", sessionVatAmount)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Cash Sales in Drawer:", style = MaterialTheme.typography.bodySmall)
                                    Text("$curr${String.format(Locale.getDefault(), "%.2f", cashSalesInSession)}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (nonCashSalesInSession > 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Non-Cash Sales:", style = MaterialTheme.typography.bodySmall)
                                        Text("$curr${String.format(Locale.getDefault(), "%.2f", nonCashSalesInSession)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        // Print Z-Reading Shift Report button
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        val zTicket = com.munzo.storepoint.util.EscPosHelper.buildShiftZReadingTicket(
                                            storeConfig = storeConfig ?: StoreConfig(storeName = "StorePoint POS", currencySymbol = curr, taxPercentage = 12.0),
                                            cashierUsername = activeSession?.cashierUsername ?: "Cashier",
                                            startTime = activeSession?.startTime ?: System.currentTimeMillis(),
                                            endTime = System.currentTimeMillis(),
                                            startingCash = activeSession?.startingCash ?: 0.0,
                                            endingCash = enteredCash,
                                            expectedCash = expectedCash,
                                            totalSales = totalSalesInSession,
                                            cashSales = cashSalesInSession,
                                            nonCashSales = nonCashSalesInSession,
                                            vatableSales = sessionVatableSales,
                                            vatAmount = sessionVatAmount,
                                            payouts = sessionPayouts,
                                            transactionCount = sessionTransactions.size
                                        )
                                        val printerIp = viewModel.printerIpAddress.value
                                        val printerPort = viewModel.printerPort.value
                                        val printerMac = viewModel.printerBtMac.value
                                        if (printerIp.isNotBlank() && printerIp != "192.168.1.100") {
                                            com.munzo.storepoint.util.EscPosHelper.printOverNetwork(printerIp, printerPort, zTicket)
                                            Toast.makeText(context, "Z-Reading printed to network printer", Toast.LENGTH_SHORT).show()
                                        } else if (printerMac.isNotBlank()) {
                                            com.munzo.storepoint.util.EscPosHelper.printOverBluetooth(printerMac, zTicket)
                                            Toast.makeText(context, "Z-Reading printed to Bluetooth printer", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Z-Reading generated! Connect a thermal printer in Admin to print physically.", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Print error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Print Z-Reading Shift Report")
                        }

                        HorizontalDivider()

                        // Drawer Cash Accounting
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (sessionPayouts != 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Supplier Payouts (Deducted):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                        Text("$curr${String.format(Locale.getDefault(), "%.2f", -sessionPayouts)}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                Text("Expected Cash in Drawer:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                Text("$curr${String.format(Locale.getDefault(), "%.2f", expectedCash)}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedTextField(
                                value = closeCashInput,
                                onValueChange = { closeCashInput = it },
                                label = { Text("Actual Cash in Drawer ($curr)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag("ending_cash_input")
                            )
                            if (cashShortage > 0.01) {
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
                                        text = "Shortage Warning: Your Cash Drawer is short by $curr${String.format(Locale.getDefault(), "%.2f", cashShortage)}!",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        // GCash Accounting (Only if enabled)
                        if (storeConfig?.hasGCash == true) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Expected GCash Balance:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    Text("$curr${String.format(Locale.getDefault(), "%.2f", expectedGCash)}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedTextField(
                                    value = closeGCashInput,
                                    onValueChange = { closeGCashInput = it },
                                    label = { Text("Actual GCash Terminal Balance ($curr)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().testTag("ending_gcash_input")
                                )
                                if (gcashShortage > 0.01) {
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
                                            text = "Shortage Warning: Your GCash account is short by $curr${String.format(Locale.getDefault(), "%.2f", gcashShortage)}!",
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }

                        // Prepaid Load Accounting (Smart and Globe)
                        if (storeConfig?.hasLoad == true) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Expected Smart/TNT Load:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    Text("$curr${String.format(Locale.getDefault(), "%.2f", expectedSmartLoad)}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedTextField(
                                    value = closeSmartLoadInput,
                                    onValueChange = { closeSmartLoadInput = it },
                                    label = { Text("Actual Smart/TNT Load Wallet ($curr)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().testTag("ending_smart_load_input")
                                )
                                if (smartLoadShortage > 0.01) {
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
                                            text = "Shortage Warning: Smart/TNT load wallet is short by $curr${String.format(Locale.getDefault(), "%.2f", smartLoadShortage)}!",
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Expected Globe/TM Load:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    Text("$curr${String.format(Locale.getDefault(), "%.2f", expectedGlobeLoad)}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedTextField(
                                    value = closeGlobeLoadInput,
                                    onValueChange = { closeGlobeLoadInput = it },
                                    label = { Text("Actual Globe/TM Load Wallet ($curr)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().testTag("ending_globe_load_input")
                                )
                                if (globeLoadShortage > 0.01) {
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
                                            text = "Shortage Warning: Globe/TM load wallet is short by $curr${String.format(Locale.getDefault(), "%.2f", globeLoadShortage)}!",
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }

        // 3) Barcode scanner camera view
        if (isScannerOpen) {
            BarcodeScannerDialog(
                products = products,
                onBarcodeScanned = { barcode ->
                    viewModel.handleBarcodeScan(barcode)
                    viewModel.isScannerOpen.value = false
                },
                onDismiss = { viewModel.isScannerOpen.value = false }
            )
        }

        // 4) Park Transaction Dialog
        if (showParkDialog) {
            AlertDialog(
                onDismissRequest = { showParkDialog = false },
                confirmButton = {
                    ExpressiveButton(
                        onClick = {
                            if (parkNoteInput.isNotBlank()) {
                                viewModel.parkActiveTransaction(parkNoteInput.trim())
                                showParkDialog = false
                            }
                        },
                        modifier = Modifier.testTag("park_submit_button"),
                        label = "Park Sale",
                        variant = ExpressiveButtonVariant.FILLED,
                        size = ExpressiveButtonSize.M,
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showParkDialog = false }) {
                        Text("Browse")
                    }
                },
                title = { Text("Park Transaction (Hold)") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Hold catalog cart temporarily. You can retrieve it instantly later using the held sidebar panels.")
                        OutlinedTextField(
                            value = parkNoteInput,
                            onValueChange = { parkNoteInput = it },
                            label = { Text("Reference text / Customer name") },
                            modifier = Modifier.fillMaxWidth().testTag("park_note_input")
                        )
                    }
                }
            )
        }

        // 5) Resuming Parked Dialog list
        if (showParkedTxDialog) {
            AlertDialog(
                onDismissRequest = { showParkedTxDialog = false },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showParkedTxDialog = false }) {
                        Text("Close")
                    }
                },
                title = { Text("Parked Held Transactions Archive (${parkedList.size})") },
                text = {
                    if (parkedList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No pending sales on hold.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(parkedList) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.note, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Held: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Button(
                                            onClick = {
                                                viewModel.resumeParkedTransaction(item.id) {
                                                    showParkedTxDialog = false
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(32.dp).testTag("resume_park_btn_${item.id}")
                                        ) {
                                            Text("Resume", style = MaterialTheme.typography.bodySmall)
                                        }

                                        IconButton(onClick = { viewModel.deleteParkedTransaction(item.id) }) {
                                            Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }

        // --- SINGLE ITEM DELETE SAFETY CONFIRMATION ---
        if (itemPendingDelete != null) {
            val productToDelete = itemPendingDelete!!
            AlertDialog(
                onDismissRequest = { itemPendingDelete = null },
                title = { Text("Confirm Item Removal", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to remove '${productToDelete.name}' from your checkout cart?") },
                confirmButton = {
                    ExpressiveButton(
                        onClick = {
                            viewModel.removeFromCart(productToDelete)
                            checkedCartItems[productToDelete.id] = false
                            itemPendingDelete = null
                            Toast.makeText(context, "${productToDelete.name} removed from cart.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("single_delete_confirm_btn"),
                        label = "Confirm Remove",
                        variant = ExpressiveButtonVariant.FILLED,
                        size = ExpressiveButtonSize.M,
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                },
                dismissButton = {
                    TextButton(onClick = { itemPendingDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // --- BULK DELETE CONFIRMATION DIALOG ---
        if (showBulkDeleteConfirm) {
            val checkedQty = cartItems.count { checkedCartItems[it.first.id] != false }
            AlertDialog(
                onDismissRequest = { showBulkDeleteConfirm = false },
                title = { Text("Confirm Bulk Deletion", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete all $checkedQty checked item(s) from the checkout cart? This action cannot be undone.") },
                confirmButton = {
                    ExpressiveButton(
                        onClick = {
                            val toDelete = cartItems.filter { checkedCartItems[it.first.id] != false }
                            toDelete.forEach { (p, _) ->
                                viewModel.removeFromCart(p)
                                checkedCartItems[p.id] = false
                            }
                            showBulkDeleteConfirm = false
                            Toast.makeText(context, "Deleted $checkedQty item(s) successfully.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("bulk_delete_confirm_button"),
                        label = "Confirm Bulk Delete",
                        variant = ExpressiveButtonVariant.FILLED,
                        size = ExpressiveButtonSize.M,
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showBulkDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // --- EDIT PRODUCT UOM VIA TOUCH LONG-PRESS ---
        if (longPressedCartItem != null) {
            val product = longPressedCartItem!!
            val qty = cartItems.find { it.first.id == product.id }?.second ?: 0
            val activeUom = selectedCartUoms[product.id] ?: com.munzo.storepoint.data.UomOption("Base Unit", 1, product.price)
            val subtotalValue = activeUom.price * qty

            AlertDialog(
                onDismissRequest = { longPressedCartItem = null },
                confirmButton = {
                    Button(
                        onClick = { longPressedCartItem = null },
                        modifier = Modifier.testTag("close_uom_edit_dialog")
                    ) {
                        Text("Close")
                    }
                },
                title = {
                    Text(
                        text = "Edit Item Measurement & Pricing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Product Name: ${product.name}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        
                        Text("Current Unit of Measurement: ${activeUom.name}", style = MaterialTheme.typography.bodyMedium)
                        
                        Text("Base Retail Price: $curr${String.format(Locale.US, "%.2f", product.price)}", style = MaterialTheme.typography.bodySmall)

                        if (product.cost > 0.0) {
                            Text("Dealer's Cost Price: $curr${String.format(Locale.US, "%.2f", product.cost * activeUom.multiplier)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }

                        Text("Selected Qty: $qty unit(s)", style = MaterialTheme.typography.bodySmall)
                        
                        Text("Line Subtotal: $curr${String.format(Locale.US, "%.2f", subtotalValue)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Text("Switch Unit of Measurement:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)

                        val uomsList = viewModel.getActiveUomsForProduct(product)
                        uomsList.forEach { option ->
                            val isOptionSelected = activeUom.name == option.name
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassPanel(cornerRadius = 16.dp)
                                    .tactileBounce()
                                    .clickable {
                                        viewModel.selectCartUom(product.id, option)
                                    }
                                    .border(
                                        width = if (isOptionSelected) 2.dp else 0.dp,
                                        color = if (isOptionSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOptionSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(option.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text("Multiplier: ×${option.multiplier}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text("$curr${String.format(Locale.US, "%.2f", option.price)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            )
        }

        // 5.5) CALL ADMIN / SUPERVISOR ASSISTANCE DIALOG (With Direct Admin Phone Dialing)
        if (showCallAdminDialog) {
            var selectedReason by remember { mutableStateOf("Manager Override / Void") }
            var assistanceNote by remember { mutableStateOf("") }
            val adminPrefs = remember { context.getSharedPreferences("storepoint_admin_prefs", Context.MODE_PRIVATE) }
            var adminPhoneInput by remember { mutableStateOf(adminPrefs.getString("admin_phone_number", "") ?: "") }
            val reasons = listOf(
                "Manager Override / Void",
                "Price Check / Discount",
                "Cash Drawer Float Refill",
                "Hardware / Scanner Issue",
                "Customer Dispute / Inquiries"
            )

            val triggerDialAdmin = {
                val clean = adminPhoneInput.filter { it.isDigit() || it == '+' }
                adminPrefs.edit().putString("admin_phone_number", adminPhoneInput).apply()
                val dialIntent = if (clean.isNotBlank()) {
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
                } else {
                    Intent(Intent.ACTION_DIAL)
                }.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(dialIntent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot open phone dialer: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }

            AlertDialog(
                onDismissRequest = { showCallAdminDialog = false },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                title = {
                    Text("Call Admin / Supervisor", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Request immediate manager or admin assistance. Confirming will dial the administrator's phone number directly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (adminPhoneInput.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Column {
                                        Text(
                                            text = "Admin Contact Number:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = adminPhoneInput,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    Text(
                                        "No admin phone number is configured yet. Admin can set it under Admin Center > Security, or you may enter it below:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = adminPhoneInput,
                                onValueChange = {
                                    adminPhoneInput = it
                                    adminPrefs.edit().putString("admin_phone_number", it).apply()
                                },
                                label = { Text("Admin / Supervisor Phone Number") },
                                placeholder = { Text("e.g. 09171234567 or +639171234567") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                leadingIcon = { Icon(Icons.Default.Phone, null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("admin_phone_input")
                            )
                        }

                        Text(
                            "Reason for Assistance:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            reasons.forEach { reason ->
                                FilterChip(
                                    selected = selectedReason == reason,
                                    onClick = { selectedReason = reason },
                                    label = { Text(reason, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        OutlinedTextField(
                            value = assistanceNote,
                            onValueChange = { assistanceNote = it },
                            label = { Text("Note / Description (Optional)") },
                            placeholder = { Text("e.g. Needs manager PIN for void") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showCallAdminDialog = false
                            // Dial the admin
                            triggerDialAdmin()
                            try {
                                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator?.vibrate(500)
                                }
                            } catch (_: Exception) {}
                            try {
                                val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                                tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 600)
                            } catch (_: Exception) {}
                            val cashierName = activeUser?.username ?: "Register"
                            val noteText = if (assistanceNote.isNotBlank()) " ($assistanceNote)" else ""
                            Toast.makeText(
                                context,
                                "Calling Admin for Cashier $cashierName... [$selectedReason]$noteText",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.testTag("submit_call_admin_btn")
                    ) {
                        Icon(Icons.Default.Call, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dial Admin Now")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCallAdminDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 6) PAYMENT PROCESSING DIALOG
        if (showPaymentDialog) {
            var selectedPaymentMethod by remember { mutableStateOf("CASH") }
            var amountPaidInput by remember { mutableStateOf("") }
            var customerNameInput by remember { mutableStateOf("") }

            val parsedPayAmount = amountPaidInput.replace(',', '.').toDoubleOrNull() ?: 0.0
            val calculatedChange = if (totalAmount < 0.0) -totalAmount else (parsedPayAmount - totalAmount).coerceAtLeast(0.0)

            Dialog(onDismissRequest = { showPaymentDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .glassPanel(cornerRadius = 24.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Select Payment Checkout Mode",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Payment Methods toggle row (Locked to CASH only)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Button(
                                onClick = { 
                                    selectedPaymentMethod = "CASH" 
                                },
                                modifier = Modifier.fillMaxWidth(0.6f).testTag("pay_cash_method"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.AttachMoney, null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cash Mode Only")
                            }
                        }

                        // Input bills
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Tender Total Due: $curr${String.format(Locale.getDefault(), "%.2f", totalAmount)}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = amountPaidInput,
                                onValueChange = { amountPaidInput = it },
                                label = { Text(if (selectedPaymentMethod == "CASH") "Tendered Cash Paid Amount ($curr)" else "Digital Payment Reference Amount") },
                                placeholder = { Text(String.format(Locale.getDefault(), "%.2f", if (totalAmount < 0.0) 0.0 else totalAmount)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag("tendered_cash_input"),
                                enabled = selectedPaymentMethod == "CASH",
                                trailingIcon = {
                                    IconButton(onClick = { amountPaidInput = String.format(Locale.getDefault(), "%.2f", if (totalAmount < 0.0) 0.0 else totalAmount) }) {
                                        Icon(Icons.Default.DoneAll, "Exact amount")
                                    }
                                }
                            )

                            OutlinedTextField(
                                value = customerNameInput,
                                onValueChange = { customerNameInput = it },
                                label = { Text("Customer Name (Optional)") },
                                placeholder = { Text("e.g. Walk-in or Juan Dela Cruz") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("customer_name_input")
                            )
                        }

                        // Change log
                        if (selectedPaymentMethod == "CASH" && (totalAmount < 0.0 || (amountPaidInput.isNotBlank() && parsedPayAmount >= totalAmount))) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val changeLabel = if (totalAmount < 0.0) "CASH TO PAY OUT:" else "CHANGE TO RETURN:"
                                Text(changeLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Text("$curr${String.format(Locale.getDefault(), "%.2f", calculatedChange)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        } else if (selectedPaymentMethod == "CASH" && amountPaidInput.isNotBlank() && parsedPayAmount < totalAmount) {
                            Text(
                                "Insufficient tender cash paid.",
                                color = Color.Red,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.align(Alignment.Start)
                            )
                        }

                        // Submit Checkout Actions (Processes check-out on activeCartItems exclusively!)
                        ExpressiveButton(
                            onClick = {
                                executePaymentAction(
                                    viewModel = viewModel,
                                    paymentMethod = selectedPaymentMethod,
                                    cashPaid = parsedPayAmount,
                                    subtotal = subtotal,
                                    taxAmount = taxAmount,
                                    totalAmount = totalAmount,
                                    cartLineItems = activeCartItems,
                                    customerName = customerNameInput,
                                    onComplete = { tx, items ->
                                        // Save completed tx state to open printable Receipt Dialog
                                        val pendingServices = DigitalServicesHelper.parseDigitalServiceItems(items)
                                        pendingDigitalServices = pendingServices
                                        lastCompletedTransaction = tx
                                        lastCompletedItems = items
                                        showPaymentDialog = false
                                        showReceiptDialog = true
                                        if (selectedPaymentMethod == "CASH" && viewModel.isAutoKickDrawerEnabled.value) {
                                            viewModel.kickCashDrawer { _, _ -> }
                                        }
                                    }
                                )
                            },
                            enabled = selectedPaymentMethod != "CASH" || totalAmount <= 0.0 || (amountPaidInput.isNotBlank() && parsedPayAmount >= totalAmount),
                            modifier = Modifier.fillMaxWidth().testTag("payment_submit_button"),
                            label = "Complete Checkout",
                            icon = Icons.Filled.CheckCircle,
                            variant = ExpressiveButtonVariant.FILLED,
                            size = ExpressiveButtonSize.L,
                        )
                    }
                }
            }
        }

        // 7) RECEIPT DIALOG (Prints, exports receipt PDF files natively)
        if (showReceiptDialog && lastCompletedTransaction != null) {
            val transaction = lastCompletedTransaction!!
            val items = lastCompletedItems

            AlertDialog(
                onDismissRequest = {
                    showReceiptDialog = false
                    if (pendingDigitalServices.isNotEmpty()) {
                        showPostCheckoutDigitalDialog = true
                    } else {
                        lastCompletedTransaction = null
                        lastCompletedItems = emptyList()
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val currentPrinterType by viewModel.printerType.collectAsState()
                        if (currentPrinterType != com.munzo.storepoint.util.EscPosHelper.PrinterType.SYSTEM_SPOOLER.name) {
                            FilledTonalButton(
                                onClick = {
                                    viewModel.printReceiptHardware(transaction, items) { _, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.testTag("receipt_thermal_print")
                            ) {
                                Icon(Icons.Default.Print, null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ESC/POS")
                            }
                        }

                        Button(
                            onClick = {
                                storeConfig?.let { config ->
                                    PdfExportUtil.exportReceiptPdf(context, config, transaction, items)
                                }
                            },
                            modifier = Modifier.testTag("receipt_share_pdf")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PDF")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showReceiptDialog = false
                            if (pendingDigitalServices.isNotEmpty()) {
                                showPostCheckoutDigitalDialog = true
                            } else {
                                lastCompletedTransaction = null
                                lastCompletedItems = emptyList()
                            }
                        },
                        modifier = Modifier.testTag("receipt_done")
                    ) {
                        Text(if (pendingDigitalServices.isNotEmpty()) "Next: Fulfill Digital Service" else "Done")
                    }
                },
                title = { Text("Checkout Transaction Success") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(56.dp))
                        Text("Transaction #STP-${transaction.id} registered offline.", fontWeight = FontWeight.Bold)
                        if (transaction.customerName.isNotBlank()) {
                            Text("Customer: ${transaction.customerName}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("Total Amount: $curr${String.format(Locale.getDefault(), "%.2f", transaction.totalAmount)}")
                        Text("Cash Paid: $curr${String.format(Locale.getDefault(), "%.2f", transaction.cashPaid)}")
                        Text("Change: $curr${String.format(Locale.getDefault(), "%.2f", transaction.changeAmount)}")

                        // Prominent banner if pay-first digital service items need fulfillment
                        if (pendingDigitalServices.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ElectricBolt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "DIGITAL SERVICE READY TO SEND",
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Text(
                                        text = "Customer has paid cash. Launch external app now to complete ${pendingDigitalServices.size} service(s).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Button(
                                        onClick = {
                                            showReceiptDialog = false
                                            showPostCheckoutDigitalDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(Icons.Default.Launch, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Service App Now")
                                    }
                                }
                            }
                        }

                        HorizontalDivider()
                        Text("You can export the printable invoice receipt immediately in PDF format above for standard thermal printer sizes.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            )
        }

        // 7b) POST-CHECKOUT DIGITAL SERVICE ACTION DIALOG (Pay-First GCash, Maya, Load Fulfillment)
        if (showPostCheckoutDigitalDialog && pendingDigitalServices.isNotEmpty()) {
            val adminPrefs = remember { context.getSharedPreferences(DigitalServicesHelper.PREFS_NAME, Context.MODE_PRIVATE) }
            val preferredLoadApp = remember(adminPrefs) { DigitalServicesHelper.getPreferredLoadApp(adminPrefs) }

            AlertDialog(
                onDismissRequest = {
                    showPostCheckoutDigitalDialog = false
                    pendingDigitalServices = emptyList()
                    lastCompletedTransaction = null
                    lastCompletedItems = emptyList()
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Payments,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Fulfill Digital Services (Paid)", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Customer payment has been recorded. Fulfill external transfer or load top-up below:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        pendingDigitalServices.forEach { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.service} ${item.action}" + if (item.network.isNotBlank()) " (${item.network})" else "",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = when (item.service) {
                                                "GCash" -> GCashColor
                                                "Maya" -> MayaColor
                                                else -> LoadColor
                                            }
                                        )
                                        Text(
                                            text = "$curr${String.format(Locale.getDefault(), "%.2f", item.amount)}",
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }

                                    if (item.action == "Cash Out") {
                                        Text(
                                            text = "Verify customer transferred $curr${String.format(Locale.getDefault(), "%.2f", item.amount)} to store wallet before releasing cash.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        Text(
                                            text = "Customer paid fee. Send $curr${String.format(Locale.getDefault(), "%.2f", item.amount)} to recipient.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    if (item.mobileNumber.isNotBlank()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                                                Text(item.mobileNumber, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            IconButton(
                                                onClick = {
                                                    DigitalServicesHelper.copyToClipboard(context, "Mobile Number", item.mobileNumber)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy number", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }

                                    when (item.service) {
                                        "GCash" -> {
                                            Button(
                                                onClick = {
                                                    DigitalServicesHelper.launchGCash(context)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = GCashColor)
                                            ) {
                                                Icon(Icons.Default.Launch, null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    if (item.action == "Cash Out") "Open GCash (Verify Transfer)"
                                                    else "Open GCash (Send ₱${String.format(Locale.getDefault(), "%.2f", item.amount)})"
                                                )
                                            }
                                        }
                                        "Maya" -> {
                                            Button(
                                                onClick = {
                                                    DigitalServicesHelper.launchMaya(context)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = MayaColor)
                                            ) {
                                                Icon(Icons.Default.Launch, null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    if (item.action == "Cash Out") "Open Maya (Verify Transfer)"
                                                    else "Open Maya (Send ₱${String.format(Locale.getDefault(), "%.2f", item.amount)})"
                                                )
                                            }
                                        }
                                        "Load" -> {
                                            val isSmart = item.network.equals("Smart", ignoreCase = true) || item.network.equals("TNT", ignoreCase = true)
                                            val ussdCode = if (isSmart) "*123#" else "*143#"
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        DigitalServicesHelper.launchDialer(context, ussdCode)
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(containerColor = LoadColor)
                                                ) {
                                                    Icon(Icons.Default.Call, null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Dial ($ussdCode)", style = MaterialTheme.typography.labelSmall)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        DigitalServicesHelper.launchSimToolkit(context)
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Default.SimCard, null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("SIM Toolkit", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }

                                            if (preferredLoadApp.isNotBlank()) {
                                                val pm = context.packageManager
                                                val appName = try {
                                                    val info = pm.getApplicationInfo(preferredLoadApp, 0)
                                                    pm.getApplicationLabel(info).toString()
                                                } catch (e: Exception) {
                                                    preferredLoadApp
                                                }
                                                FilledTonalButton(
                                                    onClick = {
                                                        DigitalServicesHelper.launchAppByPackage(context, preferredLoadApp)
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Default.AppShortcut, null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Open $appName (Admin Load App)")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPostCheckoutDigitalDialog = false
                            pendingDigitalServices = emptyList()
                            lastCompletedTransaction = null
                            lastCompletedItems = emptyList()
                        }
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("All Services Completed")
                    }
                }
            )
        }

        // 7c) AUTHORIZED TERMINAL APPS QUICK LAUNCH DIALOG
        if (showTerminalAppsDialog) {
            val adminPrefs = remember { context.getSharedPreferences(DigitalServicesHelper.PREFS_NAME, Context.MODE_PRIVATE) }
            val allowedPackages = remember(adminPrefs) { DigitalServicesHelper.getAllowedApps(adminPrefs) }
            val pm = context.packageManager
            val launchableApps = remember(allowedPackages) {
                allowedPackages.mapNotNull { pkg ->
                    try {
                        val info = pm.getApplicationInfo(pkg, 0)
                        val label = pm.getApplicationLabel(info).toString()
                        pkg to label
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            AlertDialog(
                onDismissRequest = { showTerminalAppsDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Apps, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Authorized Terminal Apps", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Admin-whitelisted applications authorized for launch on this register terminal:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (launchableApps.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("No external apps currently authorized by admin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                launchableApps.forEach { (pkg, label) ->
                                    Card(
                                        onClick = {
                                            DigitalServicesHelper.launchAppByPackage(context, pkg)
                                            showTerminalAppsDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Text(pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                            Icon(Icons.Default.Launch, contentDescription = "Launch", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTerminalAppsDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Quick Variant / UOM Selection Dialog for Sari-Sari Selling
        if (productForVariantPicker != null) {
            val prod = productForVariantPicker!!
            val uomOptions = viewModel.getActiveUomsForProduct(prod)
            AlertDialog(
                onDismissRequest = { productForVariantPicker = null },
                title = {
                    Column {
                        Text("Select Size / Variant", fontWeight = FontWeight.Bold)
                        Text(prod.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Available Stock: ${prod.stockCount} ${prod.baseUom.ifBlank { "pcs" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        uomOptions.forEach { opt ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addToCart(prod, 1)
                                        viewModel.selectCartUom(prod.id, opt)
                                        viewModel.playBeep()
                                        productForVariantPicker = null
                                    }
                                    .testTag("variant_pick_${opt.name.replace(" ", "_")}"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(opt.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        if (opt.multiplier > 1) {
                                            Text("Deducts ${opt.multiplier} base units", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                    Text(
                                        "$curr${String.format(Locale.getDefault(), "%.2f", opt.price)}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { productForVariantPicker = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Returns, Refunds and Item Exchanges Dialog
        if (showReturnRefundDialog) {
            ReturnRefundDialog(
                viewModel = viewModel,
                onDismiss = { showReturnRefundDialog = false }
            )
        }

        // 8) ABOUT CREATOR & DEVELOPER DIALOG
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                confirmButton = {
                    Button(
                        onClick = { showAboutDialog = false },
                        modifier = Modifier.testTag("about_dialog_close")
                    ) {
                        Text("Awesome")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("About App & Creator", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Dev Avatar Block
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "GitHub Developer",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "uznom",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "GitHub: @uznom",
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/uznom"))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "https://github.com/uznom",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // App Technical Highlights
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "SYSTEM INFO & DETAILS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth().glassPanel(cornerRadius = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "• StorePoint POS Terminal v$APP_VERSION",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "• Powered by Jetpack Compose & Material 3 design system",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "• Advanced Room Local Cache database offline-first stability",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "• Live camera barcode engine powered by Google ML Kit scanner SDK",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "• PDF Exports of receipts, cashier badges, and full shop audits instantly",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Crafted with dedication for robust offline retail administration.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            )
        }


    }

// Private helper to wrap ViewModel actions securely
private fun executePaymentAction(
    viewModel: StorePointViewModel,
    paymentMethod: String,
    cashPaid: Double,
    subtotal: Double,
    taxAmount: Double,
    totalAmount: Double,
    cartLineItems: List<Pair<Product, Int>>,
    customerName: String = "",
    onComplete: (Transaction, List<TransactionItem>) -> Unit
) {
    val selectedUoms = viewModel.selectedCartUoms.value
    val itemsToSave = cartLineItems.map { lineItem ->
        val prod = lineItem.first
        val uom = selectedUoms[prod.id] ?: com.munzo.storepoint.data.UomOption("Base Unit", 1, prod.price)
        val nameWithUom = if (uom.name.equals("Base Unit", ignoreCase = true) || uom.name.equals("Piece", ignoreCase = true) || uom.name.equals("Stick", ignoreCase = true)) {
            prod.name
        } else {
            "${prod.name} (${uom.name})"
        }
        TransactionItem(
            transactionId = 0, // Stub placeholder during checkout compilation
            productId = prod.id,
            productName = nameWithUom,
            price = uom.price,
            quantity = lineItem.second,
            cost = prod.cost * uom.multiplier,
            uomName = uom.name
        )
    }

    viewModel.executeCheckout(
        paymentMethod = paymentMethod,
        cashPaid = if (paymentMethod == "CARD" || paymentMethod == "GCASH" || paymentMethod == "MAYA") totalAmount else cashPaid,
        subtotal = subtotal,
        taxAmount = taxAmount,
        totalAmount = totalAmount,
        checkoutItems = cartLineItems,
        customerName = customerName,
        onSuccess = { createdTransaction ->
            val finalItems = itemsToSave.map { it.copy(transactionId = createdTransaction.id) }
            onComplete(createdTransaction, finalItems)
        }
    )
}

