package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munzo.storepoint.data.Category
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.ui.BarcodeScannerDialog
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.ExpressiveButton
import com.munzo.storepoint.ui.components.ExpressiveButtonSize
import com.munzo.storepoint.ui.components.ExpressiveButtonVariant
import com.munzo.storepoint.ui.components.ExpressiveWidthClass
import com.munzo.storepoint.ui.components.rememberExpressiveWidthClass
import com.munzo.storepoint.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryPortalScreen(
    viewModel: StorePointViewModel,
    onLogout: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val storeConfig by viewModel.storeConfig.collectAsState()
    val activeUser by viewModel.activeUser.collectAsState()
    val products by viewModel.allProducts.collectAsState()
    val categories by viewModel.allCategories.collectAsState()

    val curr = storeConfig?.currencySymbol ?: "₱"
    var selectedPortalTab by remember { mutableStateOf(0) } // 0: Price Checker, 1: Stocktake Audit, 2: Quick Share

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null && activeUser?.role == "ADMIN") {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back to Admin")
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = storeConfig?.storeName ?: "StorePoint",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Inventory & Audit Console",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    val widthClass = rememberExpressiveWidthClass()
                    val showLabels = widthClass != ExpressiveWidthClass.COMPACT

                    if (showLabels) {
                        // Quick Share Action Button in TopBar (wide layouts)
                        FilledTonalButton(
                            onClick = { viewModel.shareInventoryViaQuickShare(context) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("portal_quick_share_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Quick Share", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Quick Share", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        // Compact layouts: icon-only to avoid a cramped/clipped app bar
                        FilledTonalIconButton(
                            onClick = { viewModel.shareInventoryViaQuickShare(context) },
                            modifier = Modifier.size(40.dp).testTag("portal_quick_share_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Quick Share", modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Logged in Staff Badge & Logout
                    IconButton(
                        onClick = {
                            viewModel.logout()
                            onLogout()
                        },
                        modifier = Modifier.testTag("portal_logout_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Sign Out",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
        ) { padding ->
        val widthClass = rememberExpressiveWidthClass()
        val isCompact = widthClass == ExpressiveWidthClass.COMPACT

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = if (isCompact) Dp.Unspecified else 1100.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // User Status Pill & Expressive Tabs (stacks on compact windows so the
            // segmented tabs are never squeezed or clipped)
            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = "Auditor: ${activeUser?.username?.uppercase() ?: "STAFF"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    ExpressiveSegmentedTabs(
                        items = listOf(
                            "Price Check" to Icons.Default.QrCodeScanner,
                            "Stocktake" to Icons.Default.FactCheck,
                            "Quick Share" to Icons.Default.Share
                        ),
                        selectedIndex = selectedPortalTab,
                        onTabSelected = { selectedPortalTab = it },
                        modifier = Modifier.fillMaxWidth().testTag("portal_segmented_tabs")
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = "Auditor: ${activeUser?.username?.uppercase() ?: "STAFF"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    ExpressiveSegmentedTabs(
                        items = listOf(
                            "Price Check" to Icons.Default.QrCodeScanner,
                            "Stocktake Audit" to Icons.Default.FactCheck,
                            "Quick Share" to Icons.Default.Share
                        ),
                        selectedIndex = selectedPortalTab,
                        onTabSelected = { selectedPortalTab = it },
                        modifier = Modifier.testTag("portal_segmented_tabs")
                    )
                }
            }

            AnimatedContent(
                targetState = selectedPortalTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "portal_content"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> InventoryPriceCheckerTab(products = products, categories = categories, curr = curr, viewModel = viewModel)
                    1 -> InventoryStocktakeAuditTab(products = products, categories = categories, curr = curr, viewModel = viewModel)
                    2 -> InventoryQuickShareHubTab(viewModel = viewModel, totalProducts = products.size, totalCategories = categories.size)
                }
            }
        }
        }
    }
}

/**
 * Tab 1: Live Price Checker and Product Lookup for Aisle Audits
 */
@Composable
private fun InventoryPriceCheckerTab(
    products: List<Product>,
    categories: List<Category>,
    curr: String,
    viewModel: StorePointViewModel
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }

    val filteredProducts = remember(query, products) {
        if (query.isBlank()) products.take(20)
        else products.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.barcode.contains(query, ignoreCase = true) ||
            it.barcode10s.contains(query, ignoreCase = true) ||
            it.barcode20s.contains(query, ignoreCase = true) ||
            it.barcodeReam.contains(query, ignoreCase = true) ||
            it.barcodeMasterCase.contains(query, ignoreCase = true) ||
            it.barcodeCustomUom.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Search & Scanner Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search by Name or Barcode") },
                placeholder = { Text("Scan or enter SKU to check price...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("price_check_search_field"),
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            FilledIconButton(
                onClick = { showScanner = true },
                modifier = Modifier
                    .size(56.dp)
                    .testTag("price_check_scanner_btn"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode")
            }
        }

        // Camera Scanner Dialog
        if (showScanner) {
            BarcodeScannerDialog(
                products = products,
                title = "Price Checker Scanner",
                instruction = "Scan any product barcode or shelf label to verify current pricing.",
                onBarcodeScanned = { scannedCode ->
                    val matched = products.find { p ->
                        p.barcode.equals(scannedCode.trim(), ignoreCase = true) ||
                        p.barcode10s.equals(scannedCode.trim(), ignoreCase = true) ||
                        p.barcode20s.equals(scannedCode.trim(), ignoreCase = true) ||
                        p.barcodeReam.equals(scannedCode.trim(), ignoreCase = true) ||
                        p.barcodeMasterCase.equals(scannedCode.trim(), ignoreCase = true) ||
                        p.barcodeCustomUom.equals(scannedCode.trim(), ignoreCase = true)
                    }
                    if (matched != null) {
                        selectedProduct = matched
                        query = matched.name
                        viewModel.playBeep()
                        Toast.makeText(context, "Matched: ${matched.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No item found for barcode '$scannedCode'", Toast.LENGTH_LONG).show()
                    }
                    showScanner = false
                },
                onDismiss = { showScanner = false }
            )
        }

        // Active Product Price Card Highlight
        if (selectedProduct != null) {
            val prod = selectedProduct!!
            val catName = categories.find { it.id == prod.categoryId }?.name ?: "Uncategorized"

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .expressiveCard(cornerRadius = 24.dp)
                    .tactileBounce(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = prod.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Category: $catName • Barcode: ${prod.barcode.ifEmpty { "None" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        IconButton(onClick = { selectedProduct = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close details")
                        }
                    }

                    // Large Price Display
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Retail Price (${prod.baseUom}):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = "$curr${String.format(Locale.US, "%.2f", prod.price)}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("Available Stock:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                Surface(
                                    shape = CircleShape,
                                    color = when {
                                        prod.stockCount <= 0 -> MaterialTheme.colorScheme.errorContainer
                                        prod.stockCount in 1..5 -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    }
                                ) {
                                    Text(
                                        text = "${prod.stockCount} ${prod.baseUom}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }

                    // Multi-UOM Price Breakdown
                    val uomOptions = viewModel.getActiveUomsForProduct(prod)
                    if (uomOptions.size > 1) {
                        Text("Multi-Unit Pricing Breakdown:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uomOptions.filter { it.multiplier > 1 }.forEach { uom ->
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(uom.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Text("$curr${String.format(Locale.US, "%.2f", uom.price)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Text("${uom.multiplier} ${prod.baseUom}/unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Product Catalog List for Quick Price Inspection
        Text(
            text = "Store Catalog (${filteredProducts.size} items)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredProducts) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .expressiveCard(cornerRadius = 18.dp)
                        .tactileBounce(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    onClick = {
                        selectedProduct = item
                        viewModel.playBeep()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Barcode: ${item.barcode.ifEmpty { "N/A" }} • Stock: ${item.stockCount} ${item.baseUom}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$curr${String.format(Locale.US, "%.2f", item.price)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("Tap to inspect", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 2: Interactive Stocktake Audit Console
 */
@Composable
private fun InventoryStocktakeAuditTab(
    products: List<Product>,
    categories: List<Category>,
    curr: String,
    viewModel: StorePointViewModel
) {
    val context = LocalContext.current
    val auditedItems = remember { mutableStateMapOf<Int, Int>() }
    var auditQuery by remember { mutableStateOf("") }
    var showAuditScanner by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Audit Metrics Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val totalAudited = auditedItems.size
            val matches = auditedItems.count { (id, count) -> products.find { it.id == id }?.stockCount == count }
            val discrepancies = totalAudited - matches

            ExpressiveMetricCard(
                title = "Audited",
                value = "$totalAudited",
                subtitle = "of ${products.size} items",
                icon = Icons.Default.Checklist,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            ExpressiveMetricCard(
                title = "In Sync",
                value = "$matches",
                subtitle = "DB matched",
                icon = Icons.Default.CheckCircle,
                accentColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )

            ExpressiveMetricCard(
                title = "Discrepancies",
                value = "$discrepancies",
                subtitle = if (discrepancies > 0) "Needs sync" else "All balanced",
                icon = Icons.Default.Warning,
                accentColor = if (discrepancies > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f)
            )
        }

        // Action Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = auditQuery,
                onValueChange = { auditQuery = it },
                label = { Text("Audit Item / SKU Barcode") },
                placeholder = { Text("Scan or type item...") },
                modifier = Modifier.weight(1f).testTag("audit_input_field"),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    IconButton(onClick = { showAuditScanner = true }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Camera Scanner", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )

            Button(
                onClick = {
                    if (auditQuery.isNotBlank()) {
                        val match = products.find {
                            it.barcode.equals(auditQuery.trim(), ignoreCase = true) ||
                            it.name.contains(auditQuery.trim(), ignoreCase = true)
                        }
                        if (match != null) {
                            val cur = auditedItems[match.id] ?: 0
                            auditedItems[match.id] = cur + 1
                            viewModel.playBeep()
                            auditQuery = ""
                            Toast.makeText(context, "Audited: ${match.name} (+1)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Item not found in catalog.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.height(56.dp).testTag("audit_add_btn"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Count")
            }
        }

        // Scanner Dialog
        if (showAuditScanner) {
            BarcodeScannerDialog(
                products = products,
                title = "Stocktake Camera Scanner",
                instruction = "Scan barcodes continuously to increment audited counts.",
                onBarcodeScanned = { scanned ->
                    val match = products.find { it.barcode.equals(scanned.trim(), ignoreCase = true) }
                    if (match != null) {
                        val cur = auditedItems[match.id] ?: 0
                        auditedItems[match.id] = cur + 1
                        viewModel.playBeep()
                        Toast.makeText(context, "${match.name}: Scanned count ${cur + 1}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Unknown barcode: $scanned", Toast.LENGTH_LONG).show()
                    }
                    showAuditScanner = false
                },
                onDismiss = { showAuditScanner = false }
            )
        }

        // Audit Worksheet Header & Bulk Actions (stacks on compact windows so the
        // action buttons keep full labels and 48dp touch targets)
        val worksheetCompact = rememberExpressiveWidthClass() == ExpressiveWidthClass.COMPACT
        if (auditedItems.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Audit Worksheet (${auditedItems.size} items monitored)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (worksheetCompact) Arrangement.spacedBy(8.dp)
                    else Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveButton(
                        onClick = {
                            var synced = 0
                            auditedItems.forEach { (id, physicalCount) ->
                                val p = products.find { it.id == id }
                                if (p != null && p.stockCount != physicalCount) {
                                    viewModel.saveProduct(p.copy(stockCount = physicalCount))
                                    synced++
                                }
                            }
                            if (synced > 0) {
                                Toast.makeText(context, "Corrected $synced product counts in database!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "All audited items match system counts.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("audit_sync_btn"),
                        label = "Sync All",
                        icon = Icons.Default.Sync,
                        variant = ExpressiveButtonVariant.FILLED,
                        size = ExpressiveButtonSize.M,
                    )

                    ExpressiveButton(
                        onClick = { auditedItems.clear() },
                        modifier = Modifier.testTag("audit_clear_btn"),
                        label = "Clear",
                        icon = Icons.Default.Clear,
                        variant = ExpressiveButtonVariant.OUTLINED,
                        size = ExpressiveButtonSize.M,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audit Worksheet (0 items monitored)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Worksheet List
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (auditedItems.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Worksheet Empty", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Scan or search items above to begin counting physical inventory stacks on the store floor.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(auditedItems.keys.toList()) { productId ->
                    val item = products.find { it.id == productId }
                    val physicalCount = auditedItems[productId] ?: 0
                    if (item != null) {
                        val systemCount = item.stockCount
                        val variance = physicalCount - systemCount
                        val isMatch = variance == 0

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassPanel(cornerRadius = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMatch) Color.Transparent else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("System Stock: $systemCount • Barcode: ${item.barcode.ifEmpty { "N/A" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    
                                    // Status pill
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when {
                                            isMatch -> MaterialTheme.colorScheme.primaryContainer
                                            variance > 0 -> MaterialTheme.colorScheme.secondaryContainer
                                            else -> MaterialTheme.colorScheme.errorContainer
                                        },
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                isMatch -> "Matched"
                                                variance > 0 -> "Surplus (+$variance)"
                                                else -> "Shortage ($variance)"
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Physical quantity controls
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (physicalCount > 0) auditedItems[productId] = physicalCount - 1
                                        },
                                        modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                    }

                                    Text(
                                        text = "$physicalCount",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.widthIn(min = 28.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    IconButton(
                                        onClick = { auditedItems[productId] = physicalCount + 1 },
                                        modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    if (!isMatch) {
                                        IconButton(
                                            onClick = {
                                                viewModel.saveProduct(item.copy(stockCount = physicalCount))
                                                Toast.makeText(context, "${item.name} stock updated to $physicalCount!", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = "Sync", tint = MaterialTheme.colorScheme.primary)
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

/**
 * Tab 3: Quick Share Hub for StorePoint App-Only Transfer
 */
@Composable
private fun InventoryQuickShareHubTab(
    viewModel: StorePointViewModel,
    totalProducts: Int,
    totalCategories: Int
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .expressiveCard(cornerRadius = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Text(
                    text = "App-Only Quick Share",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Export and beam store inventory data directly between Android devices running StorePoint. The file is packaged as an app-only .spinventory container for secure terminal-to-terminal sync.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Products", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text("$totalProducts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Categories", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text("$totalCategories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Format", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(".spinventory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.shareInventoryViaQuickShare(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("hub_quick_share_button"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Inventory via Quick Share", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Informational Security Notice
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Column {
                    Text("Admin Import Control", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text(
                        "On the receiving terminal, this data is imported under Admin Center → Security → Import Inventory Data, certified by Admin 6-digit PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
