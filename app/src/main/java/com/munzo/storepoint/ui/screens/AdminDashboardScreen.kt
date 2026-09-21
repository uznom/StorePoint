package com.munzo.storepoint.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.items
import com.munzo.storepoint.data.*
import com.munzo.storepoint.ui.theme.*
import androidx.compose.ui.platform.LocalConfiguration
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.KioskStatusBar
import com.munzo.storepoint.ui.layout.WindowLayout
import com.munzo.storepoint.ui.layout.rememberWindowLayout
import com.munzo.storepoint.ui.components.ExpressiveButton
import com.munzo.storepoint.ui.components.ExpressiveButtonSize
import com.munzo.storepoint.ui.components.ExpressiveButtonVariant
import com.munzo.storepoint.util.PdfExportUtil
import com.munzo.storepoint.util.BiometricAuthHelper
import com.munzo.storepoint.util.CrashDiagnosticsManager
import com.munzo.storepoint.util.DatabaseBackupManager
import com.munzo.storepoint.util.EscPosHelper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: StorePointViewModel,
    onBackToPOS: () -> Unit,
    onNavigateToInventoryPortal: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val storeConfig by viewModel.storeConfig.collectAsState()
    val isKioskActive by viewModel.isKioskModeActive.collectAsState()

    val categories by viewModel.allCategories.collectAsState()
    val products by viewModel.allProducts.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val transactionItems by viewModel.allTransactionItems.collectAsState()
    val sessions by viewModel.allCashierSessions.collectAsState()
    val users by viewModel.allUsers.collectAsState()

    var activeTab by remember { mutableStateOf("products") } // tabs: "products"

    var deleteConfirmType by remember { mutableStateOf<String?>(null) }
    var deleteConfirmId by remember { mutableStateOf<Any?>(null) }
    var deleteConfirmName by remember { mutableStateOf("") }

    // Dialog flags
    var showProductDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var productToRestock by remember { mutableStateOf<Product?>(null) }
    var productForVariants by remember { mutableStateOf<Product?>(null) }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var showUserDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<User?>(null) }

    val curr = storeConfig?.currencySymbol ?: "$"

    val tabs = listOf(
        Triple("analytics", "Analytics", Icons.Default.Assessment),
        Triple("products", "Catalog Inventory", Icons.Default.Inventory),
        Triple("suppliers", "Suppliers & POs", Icons.Default.LocalShipping),
        Triple("categories", "Categories", Icons.Default.Category),
        Triple("users", "Staff Accounts", Icons.Default.People),
        Triple("calendar", "Payment Calendar", Icons.Default.CalendarMonth),
        Triple("security", "Security", Icons.Default.Security),
        Triple("about", "About Information", Icons.Default.Info)
    )

    val windowLayout = rememberWindowLayout()
    var showMoreNavSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                if (isKioskActive) {
                    KioskStatusBar(viewModel = viewModel)
                }
                TopAppBar(
                    windowInsets = if (isKioskActive) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Mini beautiful "SP" logo badge matching launcher/app design
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "SP",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
                                    ),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                                )
                            }
                            Text("Admin Center", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackToPOS) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "POS Register")
                        }
                    },
                    actions = {
                        if (onNavigateToInventoryPortal != null) {
                            IconButton(
                                onClick = onNavigateToInventoryPortal,
                                modifier = Modifier.testTag("admin_open_inventory_portal_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = "Inventory Portal",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                storeConfig?.let { config ->
                                    PdfExportUtil.exportSalesAndInventoryReportPdf(context, config, transactions, products)
                                }
                            },
                            modifier = Modifier.testTag("export_report_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "Export Audit PDF",
                                tint = MaterialTheme.colorScheme.primary
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
        },
        bottomBar = {
            // Adaptive bottom navigation for compact widths:
            // Sized in Scaffold bottomBar for dynamic auto-adjusting height when navigation/status bars are toggled!
            if (windowLayout == WindowLayout.Compact) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    val primaryTabs = tabs.take(4)
                    val moreTabs = tabs.drop(4)
                    NavigationBar(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        primaryTabs.forEach { (id, label, icon) ->
                            NavigationBarItem(
                                selected = activeTab == id,
                                onClick = { activeTab = id },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                        NavigationBarItem(
                            selected = moreTabs.any { it.first == activeTab },
                            onClick = { showMoreNavSheet = true },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (moreTabs.any { it.first == activeTab }) {
                                            Badge { Text("•") }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.MoreHoriz, contentDescription = "More sections")
                                }
                            },
                            label = { Text("More", maxLines = 1) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            if (windowLayout != WindowLayout.Compact) {
                // Tablet / expanded widths: keep the expressive pill carousel.
                ExpressiveScrollablePillTabs(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    tabs = tabs,
                    activeTabId = activeTab,
                    onTabSelected = { activeTab = it }
                )
            }

            // Tab Screen views
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                when (activeTab) {
                    "analytics" -> AnalyticsTab(
                        transactions,
                        transactionItems,
                        sessions,
                        products,
                        curr,
                        storeConfig?.gcashBalance ?: 0.0,
                        storeConfig?.smartLoadBalance ?: 0.0,
                        storeConfig?.globeLoadBalance ?: 0.0,
                        storeConfig = storeConfig,
                        viewModel = viewModel
                    )
                    "products" -> ProductsTab(products, categories, curr, onEdit = {
                        editingProduct = it
                        showProductDialog = true
                    }, onDelete = { id ->
                        val item = products.find { it.id == id }
                        deleteConfirmName = item?.name ?: "Product #$id"
                        deleteConfirmId = id
                        deleteConfirmType = "product"
                    }, onAddClick = {
                        editingProduct = null
                        showProductDialog = true
                    }, onRestockClick = {
                        productToRestock = it
                    }, onVariantsClick = {
                        productForVariants = it
                    }, viewModel = viewModel)
                    "suppliers" -> SuppliersAndPurchasesTab(viewModel = viewModel)
                    "categories" -> CategoriesTab(categories, onDelete = { id ->
                        val item = categories.find { it.id == id }
                        deleteConfirmName = item?.name ?: "Category #$id"
                        deleteConfirmId = id
                        deleteConfirmType = "category"
                    }, onAddClick = {
                        showCategoryDialog = true
                    })
                    "users" -> UsersTab(
                        users = users,
                        onDelete = { name ->
                            deleteConfirmName = name
                            deleteConfirmId = name
                            deleteConfirmType = "user"
                        },
                        onAddClick = {
                            editingUser = null
                            showUserDialog = true
                        },
                        onEdit = { usr ->
                            editingUser = usr
                            showUserDialog = true
                        },
                        onPrintBadge = { usr ->
                            val config = storeConfig ?: StoreConfig(id = 1, storeName = "StorePoint POS", currencySymbol = "₱", taxPercentage = 12.0)
                            com.munzo.storepoint.util.PdfExportUtil.exportCashierBadgePdf(
                                context = context,
                                storeConfig = config,
                                username = usr.username,
                                role = usr.role,
                                barcodeId = usr.barcodeId
                            )
                        }
                    )
                    "calendar" -> CalendarTab(viewModel = viewModel)
                    "security" -> SecurityTab(viewModel)
                    "about" -> AboutScreenContent(viewModel = viewModel, modifier = Modifier.fillMaxSize())
                }
            }

        }

        if (showMoreNavSheet) {
            ModalBottomSheet(onDismissRequest = { showMoreNavSheet = false }) {
                val moreTabs = tabs.drop(4)
                moreTabs.forEach { (id, label, icon) ->
                    ListItem(
                        headlineContent = {
                            Text(
                                label,
                                fontWeight = if (activeTab == id) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == id) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingContent = {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (activeTab == id) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .clickable {
                                activeTab = id
                                showMoreNavSheet = false
                            }
                            .testTag("admin_more_tab_$id")
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        // --- CRUD Dialog Modals ---

        // Deletion Confirmation Dialog
        if (deleteConfirmType != null) {
            AlertDialog(
                onDismissRequest = { deleteConfirmType = null },
                title = { Text(text = "Confirm Deletion", fontWeight = FontWeight.Bold) },
                text = { Text(text = "Are you absolutely sure you want to delete '$deleteConfirmName'? This choice is permanent and cannot be undone.") },
                confirmButton = {
                    ExpressiveButton(
                        onClick = {
                            val id = deleteConfirmId
                            when (deleteConfirmType) {
                                "product" -> if (id is Int) viewModel.deleteProduct(id)
                                "category" -> if (id is Int) viewModel.deleteCategory(id)
                                "user" -> if (id is String) viewModel.deleteUser(id)
                            }
                            deleteConfirmType = null
                        },
                        label = "Delete",
                        variant = ExpressiveButtonVariant.FILLED,
                        size = ExpressiveButtonSize.M,
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmType = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 1) PRODUCT CRUD DIALOG
        if (showProductDialog) {
            ProductCrudDialog(
                editingProduct = editingProduct,
                categories = categories,
                onDismiss = { showProductDialog = false },
                onSubmit = { name, categoryId, price, stock, barcode, expirationDate,
                             hasStick10s, priceStick10s, hasStick20s, priceStick20s, hasReam, priceReam, hasMasterCase, priceMasterCase,
                             hasCustomUom, customUomName, customUomMultiplier, customUomPrice,
                             barcode10s, barcode20s, barcodeReam, barcodeMasterCase, barcodeCustomUom, cost ->
                    val prod = (editingProduct ?: Product(
                        name = name,
                        categoryId = categoryId,
                        price = price,
                        stockCount = stock,
                        barcode = barcode,
                        expirationDate = expirationDate,
                        cost = cost
                    )).copy(
                        name = name,
                        categoryId = categoryId,
                        price = price,
                        stockCount = stock,
                        barcode = barcode,
                        expirationDate = expirationDate,
                        hasStick10s = hasStick10s,
                        priceStick10s = priceStick10s,
                        hasStick20s = hasStick20s,
                        priceStick20s = priceStick20s,
                        hasReam = hasReam,
                        priceReam = priceReam,
                        hasMasterCase = hasMasterCase,
                        priceMasterCase = priceMasterCase,
                        hasCustomUom = hasCustomUom,
                        customUomName = customUomName,
                        customUomMultiplier = customUomMultiplier,
                        customUomPrice = customUomPrice,
                        barcode10s = barcode10s,
                        barcode20s = barcode20s,
                        barcodeReam = barcodeReam,
                        barcodeMasterCase = barcodeMasterCase,
                        barcodeCustomUom = barcodeCustomUom,
                        cost = cost
                    )
                    viewModel.saveProduct(prod)
                    showProductDialog = false
                }
            )
        }

        // RECEIVE INVENTORY (MULTI-UOM RESTOCK) DIALOG
        val prodToRestockLocal = productToRestock
        if (prodToRestockLocal != null) {
            ReceiveInventoryDialog(
                product = prodToRestockLocal,
                currencySymbol = curr,
                drawerCash = viewModel.getCurrentDrawerCash(),
                onDismiss = { productToRestock = null },
                onConfirm = { qty, multiplier, totalCost, payFromDrawer ->
                    viewModel.restockProductWithUom(
                        product = prodToRestockLocal,
                        quantity = qty,
                        multiplier = multiplier,
                        totalCost = totalCost,
                        payFromDrawer = payFromDrawer
                    )
                    productToRestock = null
                }
            )
        }

        // 2) CATEGORY CREATE DIALOG
        if (showCategoryDialog) {
            var categoryNameInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCategoryDialog = false },
                confirmButton = {
                    ExpressiveButton(
                        onClick = {
                            if (categoryNameInput.isNotBlank()) {
                                viewModel.saveCategory(Category(name = categoryNameInput.trim()))
                                showCategoryDialog = false
                            }
                        },
                        modifier = Modifier.testTag("category_submit"),
                        label = "Save",
                        variant = ExpressiveButtonVariant.FILLED,
                        size = ExpressiveButtonSize.M,
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showCategoryDialog = false }) { Text("Cancel") }
                },
                title = { Text("Add Category Folder") },
                text = {
                    OutlinedTextField(
                        value = categoryNameInput,
                        onValueChange = { categoryNameInput = it },
                        label = { Text("Category Name") },
                        modifier = Modifier.fillMaxWidth().testTag("category_name_input"),
                        singleLine = true
                    )
                }
            )
        }        // 3) USER REGISTER DIALOG
        if (showUserDialog) {
            var inputUsername by remember { mutableStateOf(editingUser?.username ?: "") }
            var inputPin by remember { mutableStateOf("") }
            var inputBarcodeId by remember { mutableStateOf(editingUser?.barcodeId ?: "") }
            var inputRole by remember { mutableStateOf(editingUser?.role ?: "CASHIER") }
            var showStaffCameraScanner by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showUserDialog = false },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExpressiveButton(
                            onClick = {
                                val pinToSave = if (inputPin.isNotEmpty()) inputPin else (editingUser?.pinHash ?: "")
                                if (inputUsername.isBlank()) {
                                    Toast.makeText(context, "Username cannot be empty.", Toast.LENGTH_SHORT).show()
                                    return@ExpressiveButton
                                }
                                if (editingUser == null && inputPin.length != 6) {
                                    Toast.makeText(context, "A full 6-digit security PIN is required.", Toast.LENGTH_SHORT).show()
                                    return@ExpressiveButton
                                }
                                if (editingUser != null && inputPin.isNotEmpty() && inputPin.length != 6) {
                                    Toast.makeText(context, "PIN must be exactly 6 digits.", Toast.LENGTH_SHORT).show()
                                    return@ExpressiveButton
                                }

                                viewModel.saveUser(User(inputUsername.trim(), pinToSave, inputRole, inputBarcodeId.trim()))
                                showUserDialog = false
                                if (inputRole == "INVENTORY") {
                                    Toast.makeText(context, "Inventory audit account successfully registered & certified!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("user_submit"),
                            label = if (editingUser != null) "Save Changes" else "Add Staff User",
                            icon = if (editingUser != null) Icons.Default.Save else Icons.Default.PersonAdd,
                            variant = ExpressiveButtonVariant.FILLED,
                            size = ExpressiveButtonSize.M,
                        )

                        ExpressiveButton(
                            onClick = { showUserDialog = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("user_cancel"),
                            label = "Cancel",
                            icon = Icons.Default.Close,
                            variant = ExpressiveButtonVariant.TEXT,
                            size = ExpressiveButtonSize.M,
                        )
                    }
                },
                dismissButton = null,
                title = {
                    Text(
                        text = if (editingUser != null) "Edit Staff Account" else "Add Local Staff Account",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = inputUsername,
                            onValueChange = { inputUsername = it },
                            label = { Text("Username") },
                            modifier = Modifier.fillMaxWidth().testTag("staff_username_input"),
                            singleLine = true,
                            enabled = editingUser == null
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (editingUser != null) "New 6-Digit PIN (Leave blank to keep)" else "6-Digit Security PIN",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ExpressiveOtpPinInput(
                                pin = inputPin,
                                onPinChange = { inputPin = it },
                                pinLength = 6,
                                isMasked = true,
                                modifier = Modifier.fillMaxWidth().testTag("staff_password_input")
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (inputPin.length == 6) "✓ 6-digit PIN ready" else "${inputPin.length}/6 digits",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (inputPin.length == 6) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }

                        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                        val isTablet = configuration.screenWidthDp >= 600

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = inputBarcodeId,
                                onValueChange = { inputBarcodeId = it },
                                label = { Text("Staff Badge Barcode ID") },
                                placeholder = { Text("e.g. EMP_ID_789") },
                                modifier = Modifier.weight(1f).testTag("staff_barcode_input"),
                                singleLine = true,
                                trailingIcon = {
                                    if (inputBarcodeId.isNotEmpty()) {
                                        IconButton(onClick = { inputBarcodeId = "" }) {
                                            Icon(Icons.Default.Clear, "Clear")
                                        }
                                    }
                                }
                            )

                            if (isTablet) {
                                FilledIconButton(
                                    onClick = {
                                        showStaffCameraScanner = true
                                    },
                                    modifier = Modifier.size(56.dp).testTag("staff_barcode_scan_button")
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan with Camera")
                                }
                            }
                        }

                        if (showStaffCameraScanner && isTablet) {
                            com.munzo.storepoint.ui.BarcodeScannerDialog(
                                title = "Scan Staff Badge Barcode",
                                instruction = "Scan the staff numeric badge serial or QR code to register their account badge ID.",
                                onBarcodeScanned = { code ->
                                    inputBarcodeId = code
                                    showStaffCameraScanner = false
                                    try {
                                        val toneG = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)
                                        toneG.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                            try { toneG.release() } catch (ignored: Exception) {}
                                        }, 250)
                                    } catch (e: Exception) {
                                        android.util.Log.w("AdminDashboard", "Tone generation failed: ${e.message}")
                                    }
                                },
                                onDismiss = { showStaffCameraScanner = false }
                            )
                        }

                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Privilege Role:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                FilterChip(
                                    selected = inputRole == "ADMIN",
                                    onClick = { inputRole = "ADMIN" },
                                    label = { Text("Admin") },
                                    modifier = Modifier.testTag("role_chip_admin")
                                )
                                FilterChip(
                                    selected = inputRole == "CASHIER",
                                    onClick = { inputRole = "CASHIER" },
                                    label = { Text("Cashier") },
                                    modifier = Modifier.testTag("role_chip_cashier")
                                )
                                FilterChip(
                                    selected = inputRole == "INVENTORY",
                                    onClick = { inputRole = "INVENTORY" },
                                    label = { Text("Inventory") },
                                    modifier = Modifier.testTag("role_chip_inventory")
                                )
                            }
                        }

                        if (inputRole == "INVENTORY") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Admin Certified Inventory Role",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = "Authorized by Administrator. Grants inventory auditing, price checks, and Quick Share sync.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }

        if (productForVariants != null) {
            ProductVariantsDialog(
                product = productForVariants!!,
                viewModel = viewModel,
                onDismiss = { productForVariants = null }
            )
        }
    }
}

// Products Manager View
@Composable
fun ProductsTab(
    products: List<Product>,
    categories: List<Category>,
    curr: String,
    onEdit: (Product) -> Unit,
    onDelete: (Int) -> Unit,
    onAddClick: () -> Unit,
    onRestockClick: (Product) -> Unit,
    onVariantsClick: (Product) -> Unit = {},
    viewModel: StorePointViewModel
) {
    val context = LocalContext.current
    var isStockCheckMode by remember { mutableStateOf(false) }
    val auditedItems = remember { mutableStateMapOf<Int, Int>() }
    var showScannerDialog by remember { mutableStateOf(false) }
    var manualAuditInput by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // TOP CONTROLS & TOGGLE BLOCK
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < 560.dp
            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isStockCheckMode) "Inventory Audit & Stocktake" else "Store Inventory Register",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isStockCheckMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isStockCheckMode) "Verify matching digital registers with physical stocks safely" else "Manage items list and products database",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExpressiveButton(
                            onClick = {
                                isStockCheckMode = !isStockCheckMode
                                if (!isStockCheckMode) {
                                    auditedItems.clear()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("toggle_stock_check_btn"),
                            label = if (isStockCheckMode) "Exit Audit" else "Stock Audit",
                            icon = if (isStockCheckMode) Icons.Default.Close else Icons.Default.Inventory,
                            variant = ExpressiveButtonVariant.TONAL,
                            size = ExpressiveButtonSize.M,
                            containerColor = if (isStockCheckMode) MaterialTheme.colorScheme.errorContainer else null,
                            contentColor = if (isStockCheckMode) MaterialTheme.colorScheme.onErrorContainer else null,
                        )

                        if (!isStockCheckMode) {
                            FilledTonalIconButton(
                                onClick = { viewModel.shareInventoryViaQuickShare(context) },
                                modifier = Modifier.size(48.dp).testTag("admin_catalog_quick_share_btn_compact")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Quick Share")
                            }
                            ExpressiveButton(
                                onClick = onAddClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_product_button"),
                                label = "New Product",
                                icon = Icons.Default.Add,
                                variant = ExpressiveButtonVariant.FILLED,
                                size = ExpressiveButtonSize.M,
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isStockCheckMode) "Inventory Audit & Stocktake" else "Store Inventory Register",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isStockCheckMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isStockCheckMode) "Verify matching digital registers with physical stocks safely" else "Manage items list and products database",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ExpressiveButton(
                            onClick = {
                                isStockCheckMode = !isStockCheckMode
                                if (!isStockCheckMode) {
                                    auditedItems.clear()
                                }
                            },
                            modifier = Modifier.testTag("toggle_stock_check_btn"),
                            label = if (isStockCheckMode) "Exit Audit" else "Stock Check Mode",
                            icon = if (isStockCheckMode) Icons.Default.Close else Icons.Default.Inventory,
                            variant = ExpressiveButtonVariant.TONAL,
                            size = ExpressiveButtonSize.M,
                            containerColor = if (isStockCheckMode) MaterialTheme.colorScheme.errorContainer else null,
                            contentColor = if (isStockCheckMode) MaterialTheme.colorScheme.onErrorContainer else null,
                        )

                        if (!isStockCheckMode) {
                            FilledTonalButton(
                                onClick = { viewModel.shareInventoryViaQuickShare(context) },
                                modifier = Modifier.testTag("admin_catalog_quick_share_btn_wide")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Quick Share", style = MaterialTheme.typography.labelMedium)
                            }
                            ExpressiveButton(
                                onClick = onAddClick,
                                modifier = Modifier.testTag("add_product_button"),
                                label = "New Product",
                                icon = Icons.Default.Add,
                                variant = ExpressiveButtonVariant.FILLED,
                                size = ExpressiveButtonSize.M,
                            )
                        }
                    }
                }
            }
        }

        // DYNAMIC VIEW CHANGER
        if (isStockCheckMode) {
            // STOCKCHECK INTEGRATED COCKPIT
            Card(
                modifier = Modifier.fillMaxWidth().glassPanel(cornerRadius = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title and Description
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isCompactConsole = maxWidth < 540.dp
                        if (isCompactConsole) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Inventory, null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Local Audit Console", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                if (auditedItems.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = {
                                                val report = StringBuilder()
                                                report.append("--- STOREPOINT STOCKTAKE AUDIT ---\n")
                                                report.append("Date Conducted: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")
                                                
                                                var totalChecked = 0
                                                var discrepancies = 0
                                                
                                                auditedItems.forEach { (id, checkedCount) ->
                                                    val p = products.find { it.id == id }
                                                    if (p != null) {
                                                        totalChecked++
                                                        val variance = checkedCount - p.stockCount
                                                        if (variance != 0) {
                                                            discrepancies++
                                                            report.append("• ${p.name}\n")
                                                            report.append("  System: ${p.stockCount} | Scanned: $checkedCount\n")
                                                            report.append("  Variance: ${if (variance > 0) "+$variance" else "$variance"}\n\n")
                                                        }
                                                    }
                                                }
                                                
                                                report.append("Summary metrics:\n")
                                                report.append("- Items Checked: $totalChecked\n")
                                                report.append("- Discrepancy Alerts: $discrepancies\n")
                                                report.append("-----------------------------\n")

                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, "Inventory Audit Log")
                                                    putExtra(Intent.EXTRA_TEXT, report.toString())
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Inventory Audit Report"))
                                            },
                                            modifier = Modifier.weight(1f).testTag("audit_share_report_btn"),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Share, null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Share Report", style = MaterialTheme.typography.labelSmall)
                                        }

                                        ExpressiveButton(
                                            onClick = {
                                                var syncedCount = 0
                                                auditedItems.forEach { (id, checkedCount) ->
                                                    val p = products.find { it.id == id }
                                                    if (p != null && p.stockCount != checkedCount) {
                                                        viewModel.saveProduct(p.copy(stockCount = checkedCount))
                                                        syncedCount++
                                                    }
                                                }
                                                if (syncedCount > 0) {
                                                    Toast.makeText(context, "Bulk corrected $syncedCount discrepancy records in database!", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "All audited items are already synchronized!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.weight(1f).testTag("audit_sync_all_btn"),
                                            label = "Correct All",
                                            icon = Icons.Default.Sync,
                                            variant = ExpressiveButtonVariant.FILLED,
                                            size = ExpressiveButtonSize.XS,
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Inventory, null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Local Audit Console", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                
                                // Bulk Actions Bar
                                if (auditedItems.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FilledTonalButton(
                                            onClick = {
                                                val report = StringBuilder()
                                                report.append("--- STOREPOINT STOCKTAKE AUDIT ---\n")
                                                report.append("Date Conducted: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")
                                                
                                                var totalChecked = 0
                                                var discrepancies = 0
                                                
                                                auditedItems.forEach { (id, checkedCount) ->
                                                    val p = products.find { it.id == id }
                                                    if (p != null) {
                                                        totalChecked++
                                                        val variance = checkedCount - p.stockCount
                                                        if (variance != 0) {
                                                            discrepancies++
                                                            report.append("• ${p.name}\n")
                                                            report.append("  System: ${p.stockCount} | Scanned: $checkedCount\n")
                                                            report.append("  Variance: ${if (variance > 0) "+$variance" else "$variance"}\n\n")
                                                        }
                                                    }
                                                }
                                                
                                                report.append("Summary metrics:\n")
                                                report.append("- Items Checked: $totalChecked\n")
                                                report.append("- Discrepancy Alerts: $discrepancies\n")
                                                report.append("-----------------------------\n")

                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, "Inventory Audit Log")
                                                    putExtra(Intent.EXTRA_TEXT, report.toString())
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Inventory Audit Report"))
                                            },
                                            modifier = Modifier.testTag("audit_share_report_btn"),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Share, null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Share Report", style = MaterialTheme.typography.labelSmall)
                                        }

                                        ExpressiveButton(
                                            onClick = {
                                                var syncedCount = 0
                                                auditedItems.forEach { (id, checkedCount) ->
                                                    val p = products.find { it.id == id }
                                                    if (p != null && p.stockCount != checkedCount) {
                                                        viewModel.saveProduct(p.copy(stockCount = checkedCount))
                                                        syncedCount++
                                                    }
                                                }
                                                if (syncedCount > 0) {
                                                    Toast.makeText(context, "Bulk corrected $syncedCount discrepancy records in database!", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "All audited items are already synchronized!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.testTag("audit_sync_all_btn"),
                                            label = "Sync All",
                                            icon = Icons.Default.Sync,
                                            variant = ExpressiveButtonVariant.FILLED,
                                            size = ExpressiveButtonSize.XS,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SCANNER ENGINE TRIGGERS ROW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            val matchedSuggestions = remember(manualAuditInput, products) {
                                if (manualAuditInput.isBlank()) emptyList()
                                else products.filter { p ->
                                    p.name.contains(manualAuditInput, ignoreCase = true) ||
                                    p.barcode.contains(manualAuditInput, ignoreCase = true)
                                }.take(5)
                            }

                            OutlinedTextField(
                                value = manualAuditInput,
                                onValueChange = {
                                    manualAuditInput = it
                                    isDropdownExpanded = true
                                },
                                label = { Text("Search Item / Barcode") },
                                placeholder = { Text("Enter SKU, barcode, or item name") },
                                modifier = Modifier.fillMaxWidth().testTag("audit_barcode_field"),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { showScannerDialog = true }) {
                                        Icon(Icons.Default.QrCodeScanner, "Launch Audit Scanner", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )

                            if (isDropdownExpanded && matchedSuggestions.isNotEmpty()) {
                                DropdownMenu(
                                    expanded = isDropdownExpanded,
                                    onDismissRequest = { isDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    matchedSuggestions.forEach { prod ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(prod.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                    Text("Barcode: ${prod.barcode.ifEmpty { "None" }} • Stock: ${prod.stockCount}", style = MaterialTheme.typography.labelSmall)
                                                }
                                            },
                                            onClick = {
                                                val current = auditedItems[prod.id] ?: 0
                                                auditedItems[prod.id] = current + 1
                                                viewModel.playBeep()
                                                manualAuditInput = ""
                                                isDropdownExpanded = false
                                                Toast.makeText(context, "${prod.name} registered (+1)", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Add manually button
                        IconButton(
                            onClick = {
                                if (manualAuditInput.isNotBlank()) {
                                    val match = products.find { it.barcode.equals(manualAuditInput.trim(), ignoreCase = true) || it.name.contains(manualAuditInput, ignoreCase = true) }
                                    if (match != null) {
                                        val current = auditedItems[match.id] ?: 0
                                        auditedItems[match.id] = current + 1
                                        viewModel.playBeep()
                                        manualAuditInput = ""
                                        Toast.makeText(context, "Added ${match.name} to audit sheet (+1)", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No matching product SKU or name found.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            Icon(Icons.Default.Add, "Search & Add to Worksheet")
                        }
                    }

                    // SUMMARY COMPACT COUNTER CHIPS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val totalCheckedSize = auditedItems.size
                        val perfectMatchSize = auditedItems.count { (id, checkedQty) ->
                            products.find { it.id == id }?.stockCount == checkedQty
                        }
                        val discrepancySize = totalCheckedSize - perfectMatchSize

                        // Stats Card 1: Audited
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Audited Items", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("$totalCheckedSize / ${products.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Stats Card 2: Perfect Matches
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("In Sync", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("$perfectMatchSize", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // Stats Card 3: Discrepancies
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Discrepancies", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("$discrepancySize", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (discrepancySize > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // WORKSHEET VIEW
            Text(
                text = "Audit Worksheet Progress (${auditedItems.size} items monitored)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (auditedItems.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Empty Audit Worksheet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Scan a product's barcode with the camera or search using the text-field above to begin matching physical inventory stocks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                } else {
                    items(auditedItems.keys.toList()) { productId ->
                        val item = products.find { it.id == productId }
                        val physicalQty = auditedItems[productId] ?: 0
                        
                        if (item != null) {
                            val systemQty = item.stockCount
                            val variance = physicalQty - systemQty
                            val isMatch = variance == 0

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassPanel(cornerRadius = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isMatch) Color.Transparent else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("System Registered Stock: $systemQty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        
                                        // Status Pill Badge
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    when {
                                                        isMatch -> MaterialTheme.colorScheme.primaryContainer
                                                        variance > 0 -> MaterialTheme.colorScheme.secondaryContainer
                                                        else -> MaterialTheme.colorScheme.errorContainer
                                                    }
                                                )
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = when {
                                                        isMatch -> Icons.Default.CheckCircle
                                                        variance > 0 -> Icons.Default.ArrowUpward
                                                        else -> Icons.Default.Warning
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = when {
                                                        isMatch -> MaterialTheme.colorScheme.onPrimaryContainer
                                                        variance > 0 -> MaterialTheme.colorScheme.onSecondaryContainer
                                                        else -> MaterialTheme.colorScheme.onError
                                                    }
                                                )
                                                Text(
                                                    text = when {
                                                        isMatch -> "DB Match"
                                                        variance > 0 -> "Surplus (+$variance)"
                                                        else -> "Shortage ($variance)"
                                                    },
                                                    color = when {
                                                        isMatch -> MaterialTheme.colorScheme.onPrimaryContainer
                                                        variance > 0 -> MaterialTheme.colorScheme.onSecondaryContainer
                                                        else -> MaterialTheme.colorScheme.onError
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // PHYSICAL QUANTITY INCREMENTER
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (physicalQty > 0) {
                                                    auditedItems[productId] = physicalQty - 1
                                                }
                                            },
                                            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                                        ) {
                                            Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp))
                                        }

                                        Text(
                                            text = "$physicalQty",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.widthIn(min = 28.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )

                                        IconButton(
                                            onClick = {
                                                auditedItems[productId] = physicalQty + 1
                                            },
                                            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                                        ) {
                                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // CORRECT STOCK IN DB DIRECT BUTTON
                                    if (!isMatch) {
                                        ExpressiveButton(
                                            onClick = {
                                                viewModel.saveProduct(item.copy(stockCount = physicalQty))
                                                Toast.makeText(context, "${item.name} stocks corrected in DB!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.testTag("correct_stock_btn_${item.id}"),
                                            label = "Correct",
                                            icon = Icons.Default.Sync,
                                            variant = ExpressiveButtonVariant.TONAL,
                                            size = ExpressiveButtonSize.XS,
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        )
                                    } else {
                                        IconButton(onClick = { auditedItems.remove(productId) }) {
                                            Icon(Icons.Default.Delete, "Remove from monitor list", tint = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating Camera Dialog for Audit Scan
            if (showScannerDialog) {
                com.munzo.storepoint.ui.BarcodeScannerDialog(
                    products = products,
                    title = "Inventory Audit Scanner",
                    instruction = "Scan product barcodes to check if physical stacks match digital store stock counts.",
                    onBarcodeScanned = { barcode ->
                        val matched = products.find { it.barcode.trim().equals(barcode.trim(), ignoreCase = true) }
                        if (matched != null) {
                            val currentQty = auditedItems[matched.id] ?: 0
                            auditedItems[matched.id] = currentQty + 1
                            viewModel.playBeep()
                            Toast.makeText(context, "Matched: ${matched.name}. Prepped in Worksheet.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Opps! No catalog item found with barcode '$barcode'", Toast.LENGTH_LONG).show()
                        }
                        showScannerDialog = false
                    },
                    onDismiss = { showScannerDialog = false }
                )
            }

        } else {
            // STANDARD PRODUCT CATALOG REGISTRY
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (products.isEmpty()) {
                    item {
                        Text("No products exist in store. Tap 'New Product' above to build items catalog.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                    }
                } else {
                    items(products) { item ->
                        val isLowStock = item.stockCount in 1..5
                        val isOutOfStock = item.stockCount <= 0

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 3.dp)
                                .expressiveCard(cornerRadius = 24.dp)
                                .tactileBounce(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Top Row: Product Name, Category Badge, and Stock Badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val categoryName = categories.find { it.id == item.categoryId }?.name ?: "No Category"
                                        Text(
                                            text = categoryName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    
                                    // Stock Badge
                                    ExpressivePillBadge(
                                        text = if (isOutOfStock) "STOCKED OUT" else "${item.stockCount} IN STOCK",
                                        containerColor = when {
                                            isOutOfStock -> MaterialTheme.colorScheme.errorContainer
                                            isLowStock -> MaterialTheme.colorScheme.tertiaryContainer
                                            else -> MaterialTheme.colorScheme.primaryContainer
                                        },
                                        contentColor = when {
                                            isOutOfStock -> MaterialTheme.colorScheme.error
                                            isLowStock -> MaterialTheme.colorScheme.onTertiaryContainer
                                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                                        }
                                    )
                                }

                                // Middle Info Row: ID and Barcode, Expiration, Selling Price
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "ID: #${item.id}  •  Barcode: ${item.barcode.ifEmpty { "None" }}  •  Cost/Unit: $curr${String.format(Locale.getDefault(), "%.2f", item.cost)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!item.expirationDate.isNullOrBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Event,
                                                    contentDescription = "Expiration Date",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Expires: ${item.expirationDate}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = "$curr${String.format(Locale.getDefault(), "%.2f", item.price)}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )

                                // Action Buttons Row (Edit, Delete, Quick Restock)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left actions: Quick Restock
                                    FilledTonalButton(
                                        onClick = { onRestockClick(item) },
                                        modifier = Modifier.weight(1f, fill = false).testTag("revised_restock_btn_${item.id}"),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddCircle,
                                            contentDescription = "Receive / Restock Unit",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Restock",
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Right actions: Variants, Edit & Delete
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedIconButton(
                                            onClick = { onVariantsClick(item) },
                                            modifier = Modifier.size(38.dp).testTag("product_variants_btn_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Layers,
                                                contentDescription = "Variants & UOM",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        OutlinedIconButton(
                                            onClick = { onEdit(item) },
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit product",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        OutlinedIconButton(
                                            onClick = { onDelete(item.id) },
                                            modifier = Modifier.size(38.dp),
                                            colors = IconButtonDefaults.outlinedIconButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete product",
                                                tint = MaterialTheme.colorScheme.error,
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
        }
    }
}



