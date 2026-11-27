package com.munzo.storepoint.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.Category
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroButtonVariant
import com.munzo.storepoint.ui.components.HeroColor
import com.munzo.storepoint.ui.components.HeroFilledTonalButton
import com.munzo.storepoint.ui.components.HeroFilterChip
import com.munzo.storepoint.ui.components.HeroInput
import com.munzo.storepoint.ui.components.HeroSize
import com.munzo.storepoint.ui.theme.HeroUITheme

private enum class CatalogStockFilter { All, LowStock, OutOfStock }

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
    var searchQuery by remember { mutableStateOf("") }
    var stockFilter by remember { mutableStateOf(CatalogStockFilter.All) }
    var showCsvToolsDialog by remember { mutableStateOf(false) }

    val lowStockCount = remember(products) { products.count { it.stockCount in 1..5 } }
    val outOfStockCount = remember(products) { products.count { it.stockCount <= 0 } }

    val filteredProducts = remember(products, searchQuery, stockFilter) {
        products.filter { item ->
            val matchesStock = when (stockFilter) {
                CatalogStockFilter.All -> true
                CatalogStockFilter.LowStock -> item.stockCount in 1..5
                CatalogStockFilter.OutOfStock -> item.stockCount <= 0
            }
            val matchesSearch = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.barcode.contains(searchQuery, ignoreCase = true) ||
                item.id.toString() == searchQuery.trim()
            matchesStock && matchesSearch
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Top Controls & Mode Toggle
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < 560.dp
            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProductsTabTitleBlock(isStockCheckMode = isStockCheckMode, compact = true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HeroButton(
                            onClick = {
                                isStockCheckMode = !isStockCheckMode
                                if (!isStockCheckMode) auditedItems.clear()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("toggle_stock_check_btn"),
                            text = if (isStockCheckMode) "Exit Audit" else "Stock Audit",
                            icon = if (isStockCheckMode) Icons.Default.Close else Icons.Default.Inventory,
                            color = if (isStockCheckMode) HeroColor.Danger else HeroColor.Default,
                            variant = if (isStockCheckMode) HeroButtonVariant.Solid else HeroButtonVariant.Flat,
                            size = HeroSize.Md
                        )

                        if (!isStockCheckMode) {
                            FilledTonalIconButton(
                                onClick = { showCsvToolsDialog = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(HeroUITheme.radius.md)
                                    .testTag("admin_catalog_csv_btn_compact")
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = "CSV Import / Export")
                            }
                            FilledTonalIconButton(
                                onClick = { viewModel.shareInventoryViaQuickShare(context) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(HeroUITheme.radius.md)
                                    .testTag("admin_catalog_quick_share_btn_compact")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Quick Share")
                            }
                            HeroButton(
                                onClick = onAddClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_product_button"),
                                text = "New Product",
                                icon = Icons.Default.Add,
                                color = HeroColor.Primary,
                                variant = HeroButtonVariant.Solid,
                                size = HeroSize.Md
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
                    ProductsTabTitleBlock(
                        isStockCheckMode = isStockCheckMode,
                        compact = false,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeroButton(
                            onClick = {
                                isStockCheckMode = !isStockCheckMode
                                if (!isStockCheckMode) auditedItems.clear()
                            },
                            modifier = Modifier.testTag("toggle_stock_check_btn"),
                            text = if (isStockCheckMode) "Exit Audit" else "Stock Check Mode",
                            icon = if (isStockCheckMode) Icons.Default.Close else Icons.Default.Inventory,
                            color = if (isStockCheckMode) HeroColor.Danger else HeroColor.Default,
                            variant = if (isStockCheckMode) HeroButtonVariant.Solid else HeroButtonVariant.Flat,
                            size = HeroSize.Md
                        )

                        if (!isStockCheckMode) {
                            HeroFilledTonalButton(
                                onClick = { showCsvToolsDialog = true },
                                modifier = Modifier.testTag("admin_catalog_csv_btn_wide")
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("CSV", style = MaterialTheme.typography.labelMedium)
                            }
                            HeroFilledTonalButton(
                                onClick = { viewModel.shareInventoryViaQuickShare(context) },
                                modifier = Modifier.testTag("admin_catalog_quick_share_btn_wide")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Quick Share", style = MaterialTheme.typography.labelMedium)
                            }
                            HeroButton(
                                onClick = onAddClick,
                                modifier = Modifier.testTag("add_product_button"),
                                text = "New Product",
                                icon = Icons.Default.Add,
                                color = HeroColor.Primary,
                                variant = HeroButtonVariant.Solid,
                                size = HeroSize.Md
                            )
                        }
                    }
                }
            }
        }

        if (isStockCheckMode) {
            AdminStockAuditPane(
                products = products,
                auditedItems = auditedItems,
                viewModel = viewModel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            // Fast Catalog Search & Stock Health Filter Bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroInput(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter catalog by product name, barcode, or #ID") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_products_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroFilterChip(
                        selected = stockFilter == CatalogStockFilter.All,
                        onClick = { stockFilter = CatalogStockFilter.All },
                        label = "All (${products.size})"
                    )
                    HeroFilterChip(
                        selected = stockFilter == CatalogStockFilter.LowStock,
                        onClick = { stockFilter = CatalogStockFilter.LowStock },
                        label = "Low Stock ($lowStockCount)"
                    )
                    HeroFilterChip(
                        selected = stockFilter == CatalogStockFilter.OutOfStock,
                        onClick = { stockFilter = CatalogStockFilter.OutOfStock },
                        label = "Out of Stock ($outOfStockCount)"
                    )
                }
            }

            AdminProductCatalogList(
                products = filteredProducts,
                categories = categories,
                curr = curr,
                emptyMessage = if (products.isEmpty()) {
                    "No products exist in store. Tap 'New Product' above to build items catalog."
                } else {
                    "No products match the current search or stock filter."
                },
                onEdit = onEdit,
                onDelete = onDelete,
                onRestockClick = onRestockClick,
                onVariantsClick = onVariantsClick,
                onToggleQuickSellPin = { prod ->
                    viewModel.setProductQuickSellPinned(prod.id, !prod.isQuickSellPinned)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }

    if (showCsvToolsDialog) {
        AdminCsvImportDialog(
            viewModel = viewModel,
            onDismiss = { showCsvToolsDialog = false }
        )
    }
}

@Composable
private fun ProductsTabTitleBlock(
    isStockCheckMode: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = if (isStockCheckMode) "Inventory Audit & Stocktake" else "Store Inventory Register",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isStockCheckMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = if (compact) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (isStockCheckMode) {
                "Verify matching digital registers with physical stocks safely"
            } else {
                "Manage items list and products database"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = if (compact) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis
        )
    }
}
