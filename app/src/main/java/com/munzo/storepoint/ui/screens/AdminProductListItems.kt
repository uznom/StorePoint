package com.munzo.storepoint.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.Category
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.ui.components.HeroCard
import com.munzo.storepoint.ui.components.HeroDivider
import com.munzo.storepoint.ui.components.HeroFilledTonalButton
import com.munzo.storepoint.ui.layout.LocalAdaptiveMetrics
import com.munzo.storepoint.ui.theme.ExpressivePillBadge
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.expressiveCard
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.ui.theme.tactileBounce
import java.util.Locale

@Composable
internal fun AdminProductCatalogList(
    products: List<Product>,
    categories: List<Category>,
    curr: String,
    emptyMessage: String,
    onEdit: (Product) -> Unit,
    onDelete: (Int) -> Unit,
    onRestockClick: (Product) -> Unit,
    onVariantsClick: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = LocalAdaptiveMetrics.current
    val categoryNameById = categories.associate { it.id to it.name }

    if (products.isEmpty()) {
        Text(
            text = emptyMessage,
            color = Color.Gray,
            modifier = modifier.padding(16.dp)
        )
        return
    }

    if (metrics.useTabularProductList) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AdminProductTableHeader()
                HeroDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    itemsIndexed(products, key = { _, item -> item.id }) { index, item ->
                        AdminProductTableRow(
                            item = item,
                            categoryName = categoryNameById[item.categoryId] ?: "Uncategorized",
                            curr = curr,
                            alternateRow = index % 2 == 1,
                            onEdit = onEdit,
                            onDelete = onDelete,
                            onRestockClick = onRestockClick,
                            onVariantsClick = onVariantsClick
                        )
                        HeroDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(products, key = { it.id }) { item ->
                AdminProductCardItem(
                    item = item,
                    categoryName = categoryNameById[item.categoryId] ?: "No Category",
                    curr = curr,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onRestockClick = onRestockClick,
                    onVariantsClick = onVariantsClick
                )
            }
        }
    }
}

@Composable
private fun AdminProductTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "PRODUCT / BARCODE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(2.2f)
        )
        Text(
            text = "CATEGORY",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.1f)
        )
        Text(
            text = "PRICE / COST",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.1f)
        )
        Text(
            text = "STOCK",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.0f)
        )
        Text(
            text = "ACTIONS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(214.dp)
        )
    }
}

@Composable
private fun AdminProductTableRow(
    item: Product,
    categoryName: String,
    curr: String,
    alternateRow: Boolean,
    onEdit: (Product) -> Unit,
    onDelete: (Int) -> Unit,
    onRestockClick: (Product) -> Unit,
    onVariantsClick: (Product) -> Unit
) {
    val tactile = rememberTactileFeedback()
    val isLowStock = item.stockCount in 1..5
    val isOutOfStock = item.stockCount <= 0
    val rowBg = if (alternateRow) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(2.2f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "#${item.id} • ${item.barcode.ifEmpty { "No barcode" }}" +
                    if (!item.expirationDate.isNullOrBlank()) " • Exp ${item.expirationDate}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = categoryName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.1f)
        )

        Column(modifier = Modifier.weight(1.1f)) {
            Text(
                text = "$curr${String.format(Locale.getDefault(), "%.2f", item.price)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Text(
                text = "Cost $curr${String.format(Locale.getDefault(), "%.2f", item.cost)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Column(modifier = Modifier.weight(1.0f)) {
            ExpressivePillBadge(
                text = if (isOutOfStock) "OUT (0)" else "${item.stockCount} ${item.baseUom}",
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

        Row(
            modifier = Modifier.width(214.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroFilledTonalButton(
                onClick = {
                    tactile.perform(TactileHapticRole.Tap)
                    onRestockClick(item)
                },
                modifier = Modifier.testTag("revised_restock_btn_${item.id}"),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = "Restock", modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Restock", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }

            OutlinedIconButton(
                onClick = {
                    tactile.perform(TactileHapticRole.Tap)
                    onVariantsClick(item)
                },
                modifier = Modifier
                    .size(34.dp)
                    .testTag("product_variants_btn_${item.id}")
            ) {
                Icon(Icons.Default.Layers, "Variants & UOM", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(15.dp))
            }

            OutlinedIconButton(
                onClick = {
                    tactile.perform(TactileHapticRole.Tap)
                    onEdit(item)
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.Edit, "Edit product", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            }

            OutlinedIconButton(
                onClick = {
                    tactile.perform(TactileHapticRole.Tap)
                    onDelete(item.id)
                },
                modifier = Modifier.size(34.dp),
                colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, "Delete product", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun AdminProductCardItem(
    item: Product,
    categoryName: String,
    curr: String,
    onEdit: (Product) -> Unit,
    onDelete: (Int) -> Unit,
    onRestockClick: (Product) -> Unit,
    onVariantsClick: (Product) -> Unit
) {
    val tactile = rememberTactileFeedback()
    val isLowStock = item.stockCount in 1..5
    val isOutOfStock = item.stockCount <= 0

    HeroCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .expressiveCard(cornerRadius = 22.dp)
            .tactileBounce(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }

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

            HeroDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroFilledTonalButton(
                    onClick = {
                        tactile.perform(TactileHapticRole.Tap)
                        onRestockClick(item)
                    },
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag("revised_restock_btn_${item.id}"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Receive / Restock Unit", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restock", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedIconButton(
                        onClick = {
                            tactile.perform(TactileHapticRole.Tap)
                            onVariantsClick(item)
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("product_variants_btn_${item.id}")
                    ) {
                        Icon(Icons.Default.Layers, "Variants & UOM", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    }

                    OutlinedIconButton(
                        onClick = {
                            tactile.perform(TactileHapticRole.Tap)
                            onEdit(item)
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.Edit, "Edit product", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }

                    OutlinedIconButton(
                        onClick = {
                            tactile.perform(TactileHapticRole.Tap)
                            onDelete(item.id)
                        },
                        modifier = Modifier.size(38.dp),
                        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, "Delete product", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
