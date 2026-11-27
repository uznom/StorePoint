package com.munzo.storepoint.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.munzo.storepoint.data.Category
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.data.User
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.layout.AdminNavMode
import com.munzo.storepoint.ui.layout.LocalAdaptiveMetrics
import com.munzo.storepoint.util.PdfExportUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: StorePointViewModel,
    onBackToPOS: () -> Unit,
    onNavigateToInventoryPortal: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val metrics = LocalAdaptiveMetrics.current
    val storeConfig by viewModel.storeConfig.collectAsState()
    val isKioskActive by viewModel.isKioskModeActive.collectAsState()

    val categories by viewModel.allCategories.collectAsState()
    val products by viewModel.allProducts.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val transactionItems by viewModel.allTransactionItems.collectAsState()
    val sessions by viewModel.allCashierSessions.collectAsState()
    val users by viewModel.allUsers.collectAsState()

    var activeTab by remember { mutableStateOf("products") }
    var showMoreNavSheet by remember { mutableStateOf(false) }

    var deleteConfirmType by remember { mutableStateOf<String?>(null) }
    var deleteConfirmId by remember { mutableStateOf<Any?>(null) }
    var deleteConfirmName by remember { mutableStateOf("") }

    var showProductDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var productToRestock by remember { mutableStateOf<Product?>(null) }
    var productForVariants by remember { mutableStateOf<Product?>(null) }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var showUserDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<User?>(null) }
    var showBulkProductionDialog by remember { mutableStateOf(false) }

    val curr = storeConfig?.currencySymbol ?: "$"
    val tabs = ADMIN_TAB_DESTINATIONS

    Scaffold(
        topBar = {
            AdminTopAppBar(
                viewModel = viewModel,
                isKioskActive = isKioskActive,
                onBackToPOS = onBackToPOS,
                onNavigateToInventoryPortal = onNavigateToInventoryPortal,
                onOpenBulkProduction = { showBulkProductionDialog = true },
                onExportReportPdf = {
                    storeConfig?.let { config ->
                        PdfExportUtil.exportSalesAndInventoryReportPdf(context, config, transactions, products)
                    }
                }
            )
        },
        bottomBar = {
            if (metrics.adminNavMode == AdminNavMode.BottomBar) {
                AdminCompactBottomBar(
                    tabs = tabs,
                    activeTab = activeTab,
                    onSelectTab = { activeTab = it },
                    onOpenMore = { showMoreNavSheet = true }
                )
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            if (metrics.adminNavMode != AdminNavMode.BottomBar) {
                AdminSideNavigationRail(
                    navMode = metrics.adminNavMode,
                    tabs = tabs,
                    activeTab = activeTab,
                    onSelectTab = { activeTab = it }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(metrics.screenPadding)
            ) {
                when (activeTab) {
                    "analytics" -> AnalyticsTab(
                        transactions = transactions,
                        transactionItems = transactionItems,
                        sessions = sessions,
                        products = products,
                        curr = curr,
                        gcashBalance = storeConfig?.gcashBalance ?: 0.0,
                        smartLoadBalance = storeConfig?.smartLoadBalance ?: 0.0,
                        globeLoadBalance = storeConfig?.globeLoadBalance ?: 0.0,
                        storeConfig = storeConfig,
                        viewModel = viewModel
                    )
                    "products" -> ProductsTab(
                        products = products,
                        categories = categories,
                        curr = curr,
                        onEdit = {
                            editingProduct = it
                            showProductDialog = true
                        },
                        onDelete = { id ->
                            val item = products.find { it.id == id }
                            deleteConfirmName = item?.name ?: "Product #$id"
                            deleteConfirmId = id
                            deleteConfirmType = "product"
                        },
                        onAddClick = {
                            editingProduct = null
                            showProductDialog = true
                        },
                        onRestockClick = { productToRestock = it },
                        onVariantsClick = { productForVariants = it },
                        viewModel = viewModel
                    )
                    "suki" -> AdminSukiCreditTab(viewModel = viewModel, curr = curr)
                    "suppliers" -> SuppliersAndPurchasesTab(viewModel = viewModel)
                    "categories" -> CategoriesTab(
                        categories = categories,
                        onDelete = { id ->
                            val item = categories.find { it.id == id }
                            deleteConfirmName = item?.name ?: "Category #$id"
                            deleteConfirmId = id
                            deleteConfirmType = "category"
                        },
                        onAddClick = {
                            editingCategory = null
                            showCategoryDialog = true
                        },
                        onEditClick = {
                            editingCategory = it
                            showCategoryDialog = true
                        }
                    )
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
                            val config = storeConfig ?: StoreConfig(
                                id = 1,
                                storeName = "StorePoint POS",
                                currencySymbol = "₱",
                                taxPercentage = 12.0
                            )
                            PdfExportUtil.exportCashierBadgePdf(
                                context = context,
                                storeConfig = config,
                                username = usr.username,
                                role = usr.role,
                                barcodeId = usr.barcodeId
                            )
                        },
                        viewModel = viewModel
                    )
                    "calendar" -> CalendarTab(viewModel = viewModel)
                    "security" -> SecurityTab(viewModel)
                    "about" -> AboutScreenContent(viewModel = viewModel, modifier = Modifier.fillMaxSize())
                }
            }
        }

        if (showMoreNavSheet) {
            AdminMoreNavigationSheet(
                moreTabs = tabs.drop(4),
                activeTab = activeTab,
                onSelectTab = { activeTab = it },
                onDismiss = { showMoreNavSheet = false }
            )
        }

        if (deleteConfirmType != null) {
            AdminDeleteConfirmDialog(
                targetName = deleteConfirmName,
                onConfirm = {
                    val id = deleteConfirmId
                    when (deleteConfirmType) {
                        "product" -> if (id is Int) viewModel.deleteProduct(id)
                        "category" -> if (id is Int) viewModel.deleteCategory(id)
                        "user" -> if (id is String) viewModel.deleteUser(id)
                    }
                    deleteConfirmType = null
                },
                onDismiss = { deleteConfirmType = null }
            )
        }

        if (showProductDialog) {
            ProductCrudDialog(
                editingProduct = editingProduct,
                categories = categories,
                onDismiss = { showProductDialog = false },
                onSubmit = { name, categoryId, price, stock, barcode, expirationDate,
                             hasStick10s, priceStick10s, hasStick20s, priceStick20s, hasReam, priceReam, hasMasterCase, priceMasterCase,
                             hasCustomUom, customUomName, customUomMultiplier, customUomPrice,
                             barcode10s, barcode20s, barcodeReam, barcodeMasterCase, barcodeCustomUom, cost, baseUom, trackInventory ->
                    val prod = (editingProduct ?: Product(
                        name = name,
                        categoryId = categoryId,
                        price = price,
                        stockCount = stock,
                        barcode = barcode,
                        expirationDate = expirationDate,
                        cost = cost,
                        baseUom = baseUom,
                        trackInventory = trackInventory
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
                        cost = cost,
                        baseUom = baseUom,
                        trackInventory = trackInventory
                    )
                    viewModel.saveProduct(prod)
                    showProductDialog = false
                }
            )
        }

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

        if (showCategoryDialog) {
            AdminCategoryCrudDialog(
                editingCategory = editingCategory,
                onSave = { cat ->
                    viewModel.saveCategory(cat)
                    showCategoryDialog = false
                },
                onDismiss = { showCategoryDialog = false }
            )
        }

        if (showUserDialog) {
            AdminUserCrudDialog(
                editingUser = editingUser,
                onSave = { user ->
                    viewModel.saveUser(user)
                    showUserDialog = false
                },
                onDismiss = { showUserDialog = false }
            )
        }

        val variantProduct = productForVariants
        if (variantProduct != null) {
            ProductVariantsDialog(
                product = variantProduct,
                viewModel = viewModel,
                onDismiss = { productForVariants = null }
            )
        }

        if (showBulkProductionDialog) {
            BulkProductionDialog(
                viewModel = viewModel,
                onDismiss = { showBulkProductionDialog = false }
            )
        }
    }
}
