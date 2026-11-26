package com.munzo.storepoint.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroButtonVariant
import com.munzo.storepoint.ui.components.HeroColor
import com.munzo.storepoint.ui.components.HeroSize
import com.munzo.storepoint.ui.theme.HeroUITheme
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.elasticValuePop
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import java.util.Locale

@Composable
internal fun CashierCompactWorkspace(
    showDigital: Boolean,
    activePortraitTab: Int,
    cartLineCount: Int,
    totalItemUnits: Int,
    totalAmount: Double,
    curr: String,
    canCharge: Boolean,
    onSelectPortraitTab: (tabIndex: Int, catalogSection: Int?) -> Unit,
    onQuickCharge: () -> Unit,
    registerContent: @Composable () -> Unit,
    catalogContent: @Composable () -> Unit
) {
    val tactile = rememberTactileFeedback()
    val selectedIndex = if (!showDigital && activePortraitTab == 2) 1 else activePortraitTab

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pos_portrait_tab_row"),
            containerColor = HeroUITheme.colors.surface,
            contentColor = HeroUITheme.colors.accent,
            indicator = { tabPositions ->
                if (selectedIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                        color = HeroUITheme.colors.accent
                    )
                }
            }
        ) {
            Tab(
                selected = selectedIndex == 0,
                onClick = {
                    tactile.perform(TactileHapticRole.Tap)
                    onSelectPortraitTab(0, null)
                },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BadgedBox(badge = {
                            if (cartLineCount > 0) {
                                Badge(modifier = Modifier.elasticValuePop(totalItemUnits)) {
                                    AnimatedContent(
                                        targetState = totalItemUnits,
                                        transitionSpec = {
                                            (slideInVertically { height -> height } + fadeIn()).togetherWith(
                                                slideOutVertically { height -> -height } + fadeOut()
                                            )
                                        },
                                        label = "cartBadgeCount"
                                    ) { count ->
                                        Text("$count")
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = "Cart ($cartLineCount)",
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
                    tactile.perform(TactileHapticRole.Tap)
                    onSelectPortraitTab(1, 0)
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
                        tactile.perform(TactileHapticRole.Tap)
                        onSelectPortraitTab(2, 1)
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

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (selectedIndex == 0) {
                registerContent()
            } else {
                catalogContent()
            }
        }

        // Ergonomic Persistent Bottom Cart Dock while browsing Catalog/Digital on compact screens
        AnimatedVisibility(
            visible = selectedIndex != 0 && cartLineCount > 0,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("compact_cart_dock"),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            tactile.perform(TactileHapticRole.Tap)
                            onSelectPortraitTab(0, null)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$totalItemUnits item${if (totalItemUnits == 1) "" else "s"} in cart • Tap to review",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$curr${String.format(Locale.getDefault(), "%.2f", totalAmount)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.elasticValuePop(totalAmount)
                        )
                    }

                    HeroButton(
                        onClick = onQuickCharge,
                        enabled = canCharge,
                        text = "Charge",
                        icon = Icons.Default.ShoppingBag,
                        color = HeroColor.Primary,
                        variant = HeroButtonVariant.Solid,
                        size = HeroSize.Sm
                    )
                }
            }
        }
    }
}
