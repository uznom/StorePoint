package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.Category
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.data.StorePointRepository
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.data.TransactionItem
import com.munzo.storepoint.data.UomOption
import com.munzo.storepoint.ui.BarcodeScannerDialog
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.layout.LocalAdaptiveMetrics
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.util.APP_VERSION
import com.munzo.storepoint.util.PendingDigitalService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val tactile = rememberTactileFeedback()
    val metrics = LocalAdaptiveMetrics.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val storeConfig by viewModel.storeConfig.collectAsState()
    val activeUser by viewModel.activeUser.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val products by viewModel.allProducts.collectAsState()
    val cartItems by viewModel.cart.collectAsState()
    val parkedList by viewModel.parkedTransactions.collectAsState()
    val isScannerOpen by viewModel.isScannerOpen.collectAsState()
    val isKioskActive by viewModel.isKioskModeActive.collectAsState()
    val selectedCartUoms by viewModel.selectedCartUoms.collectAsState()
    val cartItemDiscounts by viewModel.cartItemDiscounts.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    LaunchedEffect(searchQuery) {
        delay(140)
        debouncedSearchQuery = searchQuery
    }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    val categoryNameById = remember(categories) { categories.associate { it.id to it.name } }
    var activePortraitTab by remember { mutableIntStateOf(0) }
    var activeCatalogSection by remember { mutableIntStateOf(0) }

    // Restored-basket notice
    val restoredLineCount = viewModel.restoredCartLineCount
    var showRestoredNotice by remember { mutableStateOf(false) }
    LaunchedEffect(restoredLineCount) {
        if (restoredLineCount > 0) {
            showRestoredNotice = true
            viewModel.acknowledgeCartRestore()
        }
    }
    LaunchedEffect(showRestoredNotice) {
        if (showRestoredNotice) {
            delay(4000)
            showRestoredNotice = false
        }
    }

    val isProductsLoading = products.isEmpty() && categories.isEmpty()

    // Dialog states
    var showShiftStartDialog by remember { mutableStateOf(false) }
    var showShiftCloseDialog by remember { mutableStateOf(false) }
    var showActiveShiftLogoutWarning by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showParkDialog by remember { mutableStateOf(false) }
    var showParkedTxDialog by remember { mutableStateOf(false) }
    var showReceiptDialog by remember { mutableStateOf(false) }
    var showReturnRefundDialog by remember { mutableStateOf(false) }
    var showBulkProductionDialog by remember { mutableStateOf(false) }
    var showDrawerCashInOutDialog by remember { mutableStateOf(false) }
    var showFastCashierSwitchDialog by remember { mutableStateOf(false) }
    var productForVariantPicker by remember { mutableStateOf<Product?>(null) }
    var productForDiscountDialog by remember { mutableStateOf<Product?>(null) }
    var lastScannedBarcodeFeedback by remember { mutableStateOf<String?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showCallAdminDialog by remember { mutableStateOf(false) }
    var showTerminalAppsDialog by remember { mutableStateOf(false) }
    var showPostCheckoutDigitalDialog by remember { mutableStateOf(false) }
    var pendingDigitalServices by remember { mutableStateOf<List<PendingDigitalService>>(emptyList()) }
    var lastCompletedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var lastCompletedItems by remember { mutableStateOf<List<TransactionItem>>(emptyList()) }
    var checkoutPulseTrigger by remember { mutableIntStateOf(0) }

    // Shift drawer inputs
    var initialCashInput by remember { mutableStateOf("100.00") }
    var initialGCashInput by remember { mutableStateOf("0.00") }
    var initialSmartLoadInput by remember { mutableStateOf("0.00") }
    var initialGlobeLoadInput by remember { mutableStateOf("0.00") }

    // Scanner & Park inputs
    var scannerQuantityInput by remember { mutableStateOf("1") }
    var manualBarcodeInput by remember { mutableStateOf("") }
    var parkNoteInput by remember { mutableStateOf("") }

    val curr = storeConfig?.currencySymbol ?: "$"
    val isSensitiveDataVisible = true
    val checkedCartItems = remember { mutableStateMapOf<Int, Boolean>() }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var longPressedCartItem by remember { mutableStateOf<Product?>(null) }
    var itemPendingDelete by remember { mutableStateOf<Product?>(null) }

    val activeCartItems by remember(cartItems, checkedCartItems) {
        derivedStateOf { cartItems.filter { checkedCartItems[it.first.id] != false } }
    }
    val doubleTaxRate = storeConfig?.taxPercentage ?: 12.0
    val grossCartTotal = activeCartItems.sumOf {
        val uom = selectedCartUoms[it.first.id] ?: UomOption("Base Unit", 1, it.first.price)
        val discount = cartItemDiscounts[it.first.id] ?: 0.0
        (uom.price - discount).coerceAtLeast(0.0) * it.second
    }
    val taxAmount = StorePointRepository.calculateVatFromInclusive(grossCartTotal, doubleTaxRate)
    val subtotal = StorePointRepository.calculateVatableSalesFromInclusive(grossCartTotal, doubleTaxRate)
    val totalAmount = StorePointRepository.roundMoney(grossCartTotal)

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

    val hotkeyModifier = Modifier.onKeyEvent { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown) {
            when (keyEvent.key) {
                Key.F4 -> {
                    if (cartItems.isNotEmpty()) {
                        parkNoteInput = "Parked at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
                        showParkDialog = true
                    } else {
                        Toast.makeText(context, "Nothing to park. Cart is empty.", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                Key.F9 -> {
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
            CashierDrawerContent(
                storeConfig = storeConfig,
                activeUser = activeUser,
                activeSession = activeSession,
                curr = curr,
                isSensitiveDataVisible = isSensitiveDataVisible,
                viewModel = viewModel,
                onCloseDrawer = { coroutineScope.launch { drawerState.close() } },
                onFastCashierSwitch = { showFastCashierSwitchDialog = true },
                onCashInOut = { showDrawerCashInOutDialog = true },
                onReturns = { showReturnRefundDialog = true },
                onBulkProduction = { showBulkProductionDialog = true },
                onCallAdmin = { showCallAdminDialog = true },
                onAdminDashboardNavigate = onAdminDashboardNavigate,
                onRequestShiftClose = { showShiftCloseDialog = true },
                onRequestLogout = {
                    if (activeSession != null) {
                        showActiveShiftLogoutWarning = true
                    } else {
                        viewModel.logout()
                        onLogout()
                    }
                },
                onShowAbout = { showAboutDialog = true }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(hotkeyModifier)
        ) {
            Scaffold(
                topBar = {
                    CashierTopAppBar(
                        viewModel = viewModel,
                        isKioskActive = isKioskActive,
                        storeName = storeConfig?.storeName ?: "StorePoint POS",
                        activeUser = activeUser,
                        parkedCount = parkedList.size,
                        onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                        onCallAdmin = { showCallAdminDialog = true },
                        onReturns = { showReturnRefundDialog = true },
                        onParked = { showParkedTxDialog = true },
                        onTerminalApps = { showTerminalAppsDialog = true },
                        onAbout = { showAboutDialog = true }
                    )
                }
            ) { innerPadding ->
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    val isPortrait = maxWidth < 650.dp
                    val showDigital = (storeConfig?.hasGCash == true) ||
                        (storeConfig?.hasMaya == true) ||
                        (storeConfig?.hasLoad == true)

                    val catalogContent: @Composable () -> Unit = {
                        CashierCatalogPane(
                            viewModel = viewModel,
                            storeConfig = storeConfig,
                            categories = categories,
                            products = products,
                            categoryNameById = categoryNameById,
                            curr = curr,
                            isPortrait = isPortrait,
                            isProductsLoading = isProductsLoading,
                            isSensitiveDataVisible = isSensitiveDataVisible,
                            activeCatalogSection = activeCatalogSection,
                            onCatalogSectionChange = { idx ->
                                activeCatalogSection = idx
                                if (isPortrait) activePortraitTab = idx + 1
                            },
                            searchQuery = searchQuery,
                            debouncedSearchQuery = debouncedSearchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            selectedCategory = selectedCategory,
                            onSelectCategory = { selectedCategory = it },
                            onOpenScanner = { viewModel.isScannerOpen.value = true },
                            onSelectProductForVariantPicker = { productForVariantPicker = it }
                        )
                    }

                    val registerContent: @Composable () -> Unit = {
                        CashierCartPane(
                            viewModel = viewModel,
                            products = products,
                            categoryNameById = categoryNameById,
                            cartItems = cartItems,
                            activeCartItems = activeCartItems,
                            checkedCartItems = checkedCartItems,
                            selectedCartUoms = selectedCartUoms,
                            cartItemDiscounts = cartItemDiscounts,
                            scannerQuantityInput = scannerQuantityInput,
                            onScannerQuantityChange = { scannerQuantityInput = it },
                            manualBarcodeInput = manualBarcodeInput,
                            onManualBarcodeChange = { manualBarcodeInput = it },
                            curr = curr,
                            doubleTaxRate = doubleTaxRate,
                            subtotal = subtotal,
                            taxAmount = taxAmount,
                            totalAmount = totalAmount,
                            hasActiveSession = activeSession != null,
                            checkoutPulseTrigger = checkoutPulseTrigger,
                            onOpenScanner = { viewModel.isScannerOpen.value = true },
                            onRequestBulkDelete = { showBulkDeleteConfirm = true },
                            onLongPressCartItem = { longPressedCartItem = it },
                            onRequestItemDelete = { itemPendingDelete = it },
                            onRequestItemDiscount = { productForDiscountDialog = it },
                            onRequestPark = {
                                parkNoteInput = "Parked Note #${parkedList.size + 1}"
                                showParkDialog = true
                            },
                            onRequestCharge = { showPaymentDialog = true }
                        )
                    }

                    if (isPortrait) {
                        CashierCompactWorkspace(
                            showDigital = showDigital,
                            activePortraitTab = activePortraitTab,
                            cartLineCount = cartItems.size,
                            totalItemUnits = cartItems.sumOf { it.second },
                            totalAmount = totalAmount,
                            curr = curr,
                            canCharge = activeCartItems.isNotEmpty() && activeSession != null,
                            onSelectPortraitTab = { tabIdx, catalogSection ->
                                activePortraitTab = tabIdx
                                if (catalogSection != null) activeCatalogSection = catalogSection
                            },
                            onQuickCharge = { showPaymentDialog = true },
                            registerContent = registerContent,
                            catalogContent = catalogContent
                        )
                    } else {
                        Row(modifier = Modifier.fillMaxSize()) {
                            val catalogWeight = metrics.posCatalogWeight.coerceIn(0.50f, 0.65f)
                            val registerWeight = 1f - catalogWeight
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(catalogWeight)
                            ) {
                                catalogContent()
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(registerWeight)
                            ) {
                                registerContent()
                            }
                        }
                    }
                }
            }

            // --- Dialog Hosts ---
            if (showShiftStartDialog) {
                CashierShiftStartDialog(
                    storeConfig = storeConfig,
                    curr = curr,
                    initialCashInput = initialCashInput,
                    onInitialCashChange = { initialCashInput = it },
                    initialGCashInput = initialGCashInput,
                    onInitialGCashChange = { initialGCashInput = it },
                    initialSmartLoadInput = initialSmartLoadInput,
                    onInitialSmartLoadChange = { initialSmartLoadInput = it },
                    initialGlobeLoadInput = initialGlobeLoadInput,
                    onInitialGlobeLoadChange = { initialGlobeLoadInput = it },
                    onConfirmStart = {
                        viewModel.startCashierShift(initialCashInput.toDoubleOrNull() ?: 0.0)
                        if (storeConfig?.hasGCash == true) {
                            viewModel.updateGCashBalance(initialGCashInput.toDoubleOrNull() ?: 0.0)
                        }
                        if (storeConfig?.hasLoad == true) {
                            viewModel.updateSmartLoadBalance(initialSmartLoadInput.toDoubleOrNull() ?: 0.0)
                            viewModel.updateGlobeLoadBalance(initialGlobeLoadInput.toDoubleOrNull() ?: 0.0)
                        }
                        showShiftStartDialog = false
                    }
                )
            }

            if (showShiftCloseDialog) {
                val session = activeSession
                if (session != null) {
                    CashierShiftCloseCoordinator(
                        session = session,
                        storeConfig = storeConfig,
                        viewModel = viewModel,
                        onDismiss = { showShiftCloseDialog = false },
                        onShiftClosed = {
                            showShiftCloseDialog = false
                            tactile.perform(TactileHapticRole.ConfirmSuccess)
                            Toast.makeText(context, "Shift closed and reconciled successfully.", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    showShiftCloseDialog = false
                }
            }

            if (showActiveShiftLogoutWarning) {
                ActiveShiftLogoutWarningDialog(
                    activeSession = activeSession,
                    activeUser = activeUser,
                    onReconcileNow = {
                        showActiveShiftLogoutWarning = false
                        showShiftCloseDialog = true
                    },
                    onAdminForceLogout = {
                        showActiveShiftLogoutWarning = false
                        viewModel.logout()
                        onLogout()
                    },
                    onDismiss = { showActiveShiftLogoutWarning = false }
                )
            }

            if (isScannerOpen) {
                BarcodeScannerDialog(
                    products = products,
                    title = "Continuous Barcode Scanner",
                    instruction = "Point camera at barcodes. Items are scanned and added continuously.",
                    continuous = true,
                    lastScannedBarcode = lastScannedBarcodeFeedback,
                    onBarcodeScanned = { barcode ->
                        val match = products.find {
                            it.barcode.equals(barcode.trim(), ignoreCase = true) ||
                                (it.hasStick10s && it.barcode10s.equals(barcode.trim(), ignoreCase = true)) ||
                                (it.hasStick20s && it.barcode20s.equals(barcode.trim(), ignoreCase = true))
                        }
                        lastScannedBarcodeFeedback = if (match != null) "${match.name} (+1)" else barcode
                        tactile.perform(if (match != null) TactileHapticRole.ScanSuccess else TactileHapticRole.WarningReject)
                        viewModel.handleBarcodeScan(barcode)
                    },
                    onDismiss = {
                        viewModel.isScannerOpen.value = false
                        lastScannedBarcodeFeedback = null
                    }
                )
            }

            if (showParkDialog) {
                ParkSaleDialog(
                    parkNoteInput = parkNoteInput,
                    onParkNoteChange = { parkNoteInput = it },
                    onConfirmPark = {
                        viewModel.parkActiveTransaction(parkNoteInput.trim())
                        showParkDialog = false
                    },
                    onDismiss = { showParkDialog = false }
                )
            }

            if (showParkedTxDialog) {
                ResumeParkedTransactionsDialog(
                    parkedList = parkedList,
                    onResume = { id ->
                        viewModel.resumeParkedTransaction(id) { showParkedTxDialog = false }
                    },
                    onDelete = { id -> viewModel.deleteParkedTransaction(id) },
                    onDismiss = { showParkedTxDialog = false }
                )
            }

            itemPendingDelete?.let { productToDelete ->
                CartItemRemoveConfirmDialog(
                    product = productToDelete,
                    onConfirm = {
                        viewModel.removeFromCart(productToDelete)
                        checkedCartItems[productToDelete.id] = false
                        itemPendingDelete = null
                    },
                    onDismiss = { itemPendingDelete = null }
                )
            }

            if (showBulkDeleteConfirm) {
                val checkedQty = cartItems.count { checkedCartItems[it.first.id] != false }
                CartBulkDeleteConfirmDialog(
                    checkedCount = checkedQty,
                    onConfirm = {
                        cartItems.filter { checkedCartItems[it.first.id] != false }.forEach { (p, _) ->
                            viewModel.removeFromCart(p)
                            checkedCartItems[p.id] = false
                        }
                        showBulkDeleteConfirm = false
                    },
                    onDismiss = { showBulkDeleteConfirm = false }
                )
            }

            longPressedCartItem?.let { product ->
                val qty = cartItems.find { it.first.id == product.id }?.second ?: 0
                val activeUom = selectedCartUoms[product.id] ?: UomOption("Base Unit", 1, product.price)
                CartLongPressUomDialog(
                    product = product,
                    qty = qty,
                    activeUom = activeUom,
                    uomsList = viewModel.getActiveUomsForProduct(product),
                    curr = curr,
                    onSelectUom = { option -> viewModel.selectCartUom(product.id, option) },
                    onDismiss = { longPressedCartItem = null }
                )
            }

            if (showCallAdminDialog) {
                CashierCallAdminDialog(
                    activeUser = activeUser,
                    onDismiss = { showCallAdminDialog = false }
                )
            }

            if (showPaymentDialog) {
                CashierPaymentDialog(
                    totalAmount = totalAmount,
                    subtotal = subtotal,
                    taxAmount = taxAmount,
                    activeCartItems = activeCartItems,
                    curr = curr,
                    viewModel = viewModel,
                    onDismiss = { showPaymentDialog = false },
                    onCheckoutSuccess = { tx, items, pendingServices ->
                        tactile.perform(TactileHapticRole.ConfirmSuccess)
                        checkoutPulseTrigger++
                        pendingDigitalServices = pendingServices
                        lastCompletedTransaction = tx
                        lastCompletedItems = items
                        showPaymentDialog = false
                        showReceiptDialog = true
                    }
                )
            }

            if (showReceiptDialog && lastCompletedTransaction != null) {
                CashierReceiptDialog(
                    transaction = lastCompletedTransaction!!,
                    items = lastCompletedItems,
                    pendingDigitalServices = pendingDigitalServices,
                    curr = curr,
                    viewModel = viewModel,
                    storeConfig = storeConfig,
                    onDismiss = {
                        showReceiptDialog = false
                        lastCompletedTransaction = null
                        lastCompletedItems = emptyList()
                    },
                    onOpenDigitalServices = {
                        showReceiptDialog = false
                        showPostCheckoutDigitalDialog = true
                    }
                )
            }

            if (showPostCheckoutDigitalDialog && pendingDigitalServices.isNotEmpty()) {
                CashierPostCheckoutDigitalDialog(
                    pendingDigitalServices = pendingDigitalServices,
                    curr = curr,
                    onDismiss = {
                        showPostCheckoutDigitalDialog = false
                        pendingDigitalServices = emptyList()
                        lastCompletedTransaction = null
                        lastCompletedItems = emptyList()
                    },
                    onAllCompleted = {
                        showPostCheckoutDigitalDialog = false
                        pendingDigitalServices = emptyList()
                        lastCompletedTransaction = null
                        lastCompletedItems = emptyList()
                    }
                )
            }

            if (showTerminalAppsDialog) {
                CashierTerminalAppsDialog(onDismiss = { showTerminalAppsDialog = false })
            }

            productForVariantPicker?.let { product ->
                CashierVariantPickerDialog(
                    product = product,
                    curr = curr,
                    viewModel = viewModel,
                    onDismiss = { productForVariantPicker = null }
                )
            }

            productForDiscountDialog?.let { prod ->
                val activeUom = selectedCartUoms[prod.id] ?: UomOption("Base Unit", 1, prod.price)
                val currentDiscount = cartItemDiscounts[prod.id] ?: 0.0
                CashierItemDiscountDialog(
                    product = prod,
                    activeUom = activeUom,
                    currentDiscount = currentDiscount,
                    curr = curr,
                    viewModel = viewModel,
                    onDismiss = { productForDiscountDialog = null }
                )
            }

            if (showReturnRefundDialog) {
                ReturnRefundDialog(viewModel = viewModel, onDismiss = { showReturnRefundDialog = false })
            }

            if (showBulkProductionDialog) {
                BulkProductionDialog(viewModel = viewModel, onDismiss = { showBulkProductionDialog = false })
            }

            if (showDrawerCashInOutDialog) {
                DrawerCashInOutDialog(viewModel = viewModel, onDismiss = { showDrawerCashInOutDialog = false })
            }

            if (showFastCashierSwitchDialog) {
                FastCashierSwitchDialog(viewModel = viewModel, onDismiss = { showFastCashierSwitchDialog = false })
            }

            if (showAboutDialog) {
                CashierAboutDialog(appVersion = APP_VERSION, onDismiss = { showAboutDialog = false })
            }

            CashierRestoredCartOverlay(
                visible = showRestoredNotice,
                restoredLineCount = restoredLineCount
            )
        }
    }
}
