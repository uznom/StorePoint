package com.munzo.storepoint.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.Category
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.HeroCard
import com.munzo.storepoint.ui.components.HeroChip
import com.munzo.storepoint.ui.components.HeroChipVariant
import com.munzo.storepoint.ui.components.HeroColor
import com.munzo.storepoint.ui.components.HeroInput
import com.munzo.storepoint.ui.components.HeroInputVariant
import com.munzo.storepoint.ui.components.HeroSegmentedTabs
import com.munzo.storepoint.ui.components.staggeredEnter
import com.munzo.storepoint.ui.layout.LocalAdaptiveMetrics
import com.munzo.storepoint.ui.layout.ScanSearchField
import com.munzo.storepoint.ui.theme.HeroUITheme
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.ui.theme.tactileBounce
import java.util.Locale

@Composable
internal fun CashierCatalogPane(
    viewModel: StorePointViewModel,
    storeConfig: StoreConfig?,
    categories: List<Category>,
    products: List<Product>,
    categoryNameById: Map<Int, String>,
    curr: String,
    isPortrait: Boolean,
    isProductsLoading: Boolean,
    isSensitiveDataVisible: Boolean,
    activeCatalogSection: Int,
    onCatalogSectionChange: (Int) -> Unit,
    searchQuery: String,
    debouncedSearchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategory: Category?,
    onSelectCategory: (Category?) -> Unit,
    onOpenScanner: () -> Unit,
    onSelectProductForVariantPicker: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = LocalAdaptiveMetrics.current
    val tactile = rememberTactileFeedback()
    val showDigitalCategory = (storeConfig?.hasGCash == true) ||
        (storeConfig?.hasMaya == true) ||
        (storeConfig?.hasLoad == true)
    val digitalCategoryId = remember(categories) {
        categories.find {
            it.name.contains("GCash", ignoreCase = true) ||
                it.name.contains("Digital", ignoreCase = true)
        }?.id ?: categories.firstOrNull()?.id ?: 1
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(metrics.itemSpacing)
    ) {
        if (showDigitalCategory && !isPortrait) {
            HeroSegmentedTabs(
                items = listOf("Standard Products", "Digital Services"),
                selectedItem = if (activeCatalogSection == 0) "Standard Products" else "Digital Services",
                onItemSelected = { selected ->
                    onCatalogSectionChange(if (selected == "Standard Products") 0 else 1)
                },
                labelProvider = { it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
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
                        tactile.perform(TactileHapticRole.ScanSuccess)
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
                    digitalCategoryId = digitalCategoryId
                )
            }
        } else {
            // Adaptive Pill Search Bar with integrated camera trigger
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val searchCopy = ScanSearchField.copyFor(maxWidth)
                HeroInput(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = searchCopy.placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = HeroUITheme.colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = HeroUITheme.colors.muted
                        )
                    },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = HeroUITheme.colors.muted
                                    )
                                }
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    tactile.perform(TactileHapticRole.Tap)
                                    onOpenScanner()
                                },
                                modifier = Modifier.testTag("barcode_cam_trigger"),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = HeroUITheme.colors.accent.copy(alpha = 0.14f),
                                    contentColor = HeroUITheme.colors.accent
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan",
                                    tint = HeroUITheme.colors.accent
                                )
                            }
                        }
                    },
                    shape = HeroUITheme.radius.full,
                    variant = HeroInputVariant.Flat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("catalog_search_bar"),
                    singleLine = true
                )
            }

            // Category Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Filter: ",
                    style = MaterialTheme.typography.labelMedium,
                    color = HeroUITheme.colors.muted
                )
                HeroChip(
                    text = "All",
                    variant = if (selectedCategory == null) HeroChipVariant.Solid else HeroChipVariant.Flat,
                    color = if (selectedCategory == null) HeroColor.Primary else HeroColor.Default,
                    onClick = { onSelectCategory(null) },
                    modifier = Modifier.testTag("cat_chip_all")
                )

                categories.filterNot { cat ->
                    cat.name.contains("Digital", ignoreCase = true) || cat.name.contains("Service", ignoreCase = true)
                }.forEach { cat ->
                    val isSelected = selectedCategory?.id == cat.id
                    HeroChip(
                        text = cat.name,
                        icon = if (cat.iconBadge.isNotBlank()) getCategoryIconVector(cat.iconBadge) else null,
                        variant = if (isSelected) HeroChipVariant.Solid else HeroChipVariant.Flat,
                        color = if (isSelected) HeroColor.Primary else HeroColor.Default,
                        onClick = { onSelectCategory(cat) },
                        modifier = Modifier.testTag("cat_chip_${cat.id}")
                    )
                }
            }

            val effectiveQuery = debouncedSearchQuery.trim()
            if (effectiveQuery.isBlank()) {
                CashierQuickSellRibbon(
                    products = products,
                    curr = curr,
                    viewModel = viewModel,
                    onSelectProductForVariantPicker = onSelectProductForVariantPicker
                )
            }
            val filteredProducts = remember(products, categoryNameById, selectedCategory, effectiveQuery) {
                products.filter { p ->
                    val categoryName = categoryNameById[p.categoryId] ?: ""
                    val isOldDigital = p.name.contains("GCash Cash-In", ignoreCase = true) ||
                        p.name.contains("GCash Cash-Out", ignoreCase = true) ||
                        p.name.contains("Smart Load", ignoreCase = true) ||
                        categoryName.contains("Digital", ignoreCase = true)

                    !isOldDigital &&
                        (selectedCategory == null || p.categoryId == selectedCategory.id) &&
                        (effectiveQuery.isBlank() ||
                            p.name.contains(effectiveQuery, ignoreCase = true) ||
                            p.barcode.contains(effectiveQuery, ignoreCase = true) ||
                            categoryName.contains(effectiveQuery, ignoreCase = true))
                }
            }

            val isDigitalSearch = effectiveQuery.isNotEmpty() && (
                effectiveQuery.contains("gcash", ignoreCase = true) ||
                    effectiveQuery.contains("g-cash", ignoreCase = true) ||
                    effectiveQuery.contains("g cash", ignoreCase = true) ||
                    effectiveQuery.contains("maya", ignoreCase = true) ||
                    effectiveQuery.contains("load", ignoreCase = true) ||
                    effectiveQuery.contains("smart", ignoreCase = true) ||
                    effectiveQuery.contains("tnt", ignoreCase = true) ||
                    effectiveQuery.contains("globe", ignoreCase = true) ||
                    effectiveQuery.contains("tm", ignoreCase = true)
                )

            when {
                isDigitalSearch -> {
                    val initialService = when {
                        effectiveQuery.contains("gcash", ignoreCase = true) ||
                            effectiveQuery.contains("g-cash", ignoreCase = true) ||
                            effectiveQuery.contains("g cash", ignoreCase = true) -> "GCash"
                        effectiveQuery.contains("maya", ignoreCase = true) -> "Maya"
                        else -> "Load"
                    }
                    val initialNetwork = when {
                        effectiveQuery.contains("smart", ignoreCase = true) -> "Smart"
                        effectiveQuery.contains("tnt", ignoreCase = true) -> "TNT"
                        effectiveQuery.contains("globe", ignoreCase = true) -> "Globe"
                        effectiveQuery.contains("tm", ignoreCase = true) -> "TM"
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
                                tactile.perform(TactileHapticRole.ScanSuccess)
                                viewModel.addToCart(dynamicProduct)
                                onSearchQueryChange("")
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
                            digitalCategoryId = digitalCategoryId
                        )
                    }
                }
                isProductsLoading -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = metrics.catalogMinCellWidth),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(8) { ShimmerProductCard() }
                    }
                }
                filteredProducts.isEmpty() -> {
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
                                text = "No products found to match filters.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "Ensure items are setup in the Admin dashboard inventory catalog.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = metrics.catalogMinCellWidth),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(
                            items = filteredProducts,
                            key = { it.id },
                            contentType = { "product" }
                        ) { item ->
                            CashierProductGridTile(
                                item = item,
                                categoryName = categoryNameById[item.categoryId] ?: "General",
                                curr = curr,
                                viewModel = viewModel,
                                onSelectForVariantPicker = { onSelectProductForVariantPicker(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CashierProductGridTile(
    item: Product,
    categoryName: String,
    curr: String,
    viewModel: StorePointViewModel,
    onSelectForVariantPicker: () -> Unit
) {
    val context = LocalContext.current
    val tactile = rememberTactileFeedback()
    val isLowStock = item.stockCount in 1..5
    val isOutOfStock = item.stockCount <= 0
    val availableUoms = remember(
        item.id, item.price, item.hasCustomUom, item.hasStick10s, item.hasStick20s, item.hasReam, item.hasMasterCase
    ) { viewModel.getActiveUomsForProduct(item) }
    val isGCash = item.name.contains("GCash", ignoreCase = true)
    val isSmart = item.name.contains("Smart", ignoreCase = true) || item.name.contains("TNT", ignoreCase = true)
    val isGlobe = item.name.contains("Globe", ignoreCase = true) || item.name.contains("TM", ignoreCase = true)

    HeroCard(
        modifier = Modifier
            .fillMaxWidth()
            .tactileBounce(pressFeedback = true) {
                if (isOutOfStock) {
                    tactile.perform(TactileHapticRole.WarningReject)
                } else if (availableUoms.size > 1) {
                    tactile.perform(TactileHapticRole.Tap)
                    onSelectForVariantPicker()
                } else {
                    tactile.perform(TactileHapticRole.ScanSuccess)
                    viewModel.addToCart(item)
                    viewModel.playBeep()
                }
            }
            .staggeredEnter((item.id and 0x3F) % 8)
            .testTag("product_card_${item.id}"),
        shape = HeroUITheme.radius.lg,
        colors = CardDefaults.cardColors(
            containerColor = if (isOutOfStock) {
                HeroUITheme.colors.surfaceSecondary.copy(alpha = 0.5f)
            } else {
                HeroUITheme.colors.surface
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isOutOfStock -> HeroUITheme.colors.danger.copy(alpha = 0.4f)
                isLowStock -> HeroUITheme.colors.warning.copy(alpha = 0.5f)
                else -> HeroUITheme.colors.border
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOutOfStock) 0.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroChip(
                    text = categoryName,
                    variant = HeroChipVariant.Flat,
                    color = HeroColor.Primary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                HeroChip(
                    text = when {
                        isOutOfStock -> "OUT"
                        isLowStock -> "LOW: ${item.stockCount}"
                        else -> "${item.stockCount} left"
                    },
                    variant = HeroChipVariant.Flat,
                    color = when {
                        isOutOfStock -> HeroColor.Danger
                        isLowStock -> HeroColor.Warning
                        else -> HeroColor.Default
                    },
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isOutOfStock) HeroUITheme.colors.muted else HeroUITheme.colors.foreground,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$curr${String.format(Locale.getDefault(), "%.2f", item.price)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = HeroUITheme.colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (availableUoms.size > 1) {
                    HeroChip(
                        text = "${availableUoms.size} UOM",
                        variant = HeroChipVariant.Flat,
                        color = HeroColor.Primary
                    )
                } else if (isGCash || isSmart || isGlobe) {
                    IconButton(
                        onClick = {
                            try {
                                if (isGCash) {
                                    val pkg = "com.globe.gcash.android"
                                    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                                    if (intent != null) {
                                        context.startActivity(intent)
                                    } else {
                                        val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                                        playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(playIntent)
                                    }
                                } else {
                                    val ussd = if (isSmart) "*343#" else "*100#"
                                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:" + Uri.encode(ussd))
                                    }
                                    context.startActivity(dialIntent)
                                }
                            } catch (_: Exception) {
                            }
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = "Dial/App",
                            tint = HeroUITheme.colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
