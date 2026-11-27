package com.munzo.storepoint.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.HeroCard
import com.munzo.storepoint.ui.theme.HeroUITheme
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.ui.theme.tactileBounce
import java.util.Locale

/**
 * Compact 1-tap horizontal quick-sell ribbon displayed above the catalog grid
 * whenever at least one product is pinned ([Product.isQuickSellPinned] == true).
 */
@Composable
internal fun CashierQuickSellRibbon(
    products: List<Product>,
    curr: String,
    viewModel: StorePointViewModel,
    onSelectProductForVariantPicker: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    val pinnedProducts = remember(products) {
        products.filter { it.isQuickSellPinned }
    }
    if (pinnedProducts.isEmpty()) return

    val tactile = rememberTactileFeedback()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = HeroUITheme.colors.accent,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "QUICK SELL (${pinnedProducts.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = HeroUITheme.colors.accent
            )
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cashier_quick_sell_ribbon"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(pinnedProducts, key = { "qs_${it.id}" }) { item ->
                val isOutOfStock = item.stockCount <= 0
                val availableUoms = remember(
                    item.id, item.price, item.hasCustomUom, item.hasStick10s,
                    item.hasStick20s, item.hasReam, item.hasMasterCase
                ) { viewModel.getActiveUomsForProduct(item) }

                HeroCard(
                    modifier = Modifier
                        .widthIn(min = 112.dp, max = 164.dp)
                        .tactileBounce(pressFeedback = true) {
                            if (isOutOfStock) {
                                tactile.perform(TactileHapticRole.WarningReject)
                            } else if (availableUoms.size > 1) {
                                tactile.perform(TactileHapticRole.Tap)
                                onSelectProductForVariantPicker(item)
                            } else {
                                tactile.perform(TactileHapticRole.ScanSuccess)
                                viewModel.addToCart(item)
                                viewModel.playBeep()
                            }
                        }
                        .testTag("quick_sell_tile_${item.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOutOfStock) {
                            HeroUITheme.colors.surfaceSecondary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        }
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isOutOfStock) {
                            HeroUITheme.colors.danger.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isOutOfStock) HeroUITheme.colors.muted else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$curr${String.format(Locale.getDefault(), "%.2f", item.price)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Text(
                                text = if (isOutOfStock) "OUT" else "${item.stockCount}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOutOfStock) {
                                    HeroUITheme.colors.danger
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
