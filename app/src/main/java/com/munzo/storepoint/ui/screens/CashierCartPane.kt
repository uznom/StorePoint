package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.data.UomOption
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroButtonVariant
import com.munzo.storepoint.ui.components.HeroCard
import com.munzo.storepoint.ui.components.HeroCheckbox
import com.munzo.storepoint.ui.components.HeroColor
import com.munzo.storepoint.ui.components.HeroDivider
import com.munzo.storepoint.ui.components.HeroInput
import com.munzo.storepoint.ui.components.HeroOutlinedButton
import com.munzo.storepoint.ui.components.HeroSize
import com.munzo.storepoint.ui.components.HeroTextButton
import com.munzo.storepoint.ui.layout.LocalAdaptiveMetrics
import com.munzo.storepoint.ui.layout.ScanSearchField
import com.munzo.storepoint.ui.theme.ExpressiveSectionHeader
import com.munzo.storepoint.ui.theme.HeroUITheme
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.celebratePulse
import com.munzo.storepoint.ui.theme.elasticValuePop
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import java.util.Locale

@Composable
internal fun CashierCartPane(
    viewModel: StorePointViewModel,
    products: List<Product>,
    categoryNameById: Map<Int, String>,
    cartItems: List<Pair<Product, Int>>,
    activeCartItems: List<Pair<Product, Int>>,
    checkedCartItems: SnapshotStateMap<Int, Boolean>,
    selectedCartUoms: Map<Int, UomOption>,
    cartItemDiscounts: Map<Int, Double>,
    scannerQuantityInput: String,
    onScannerQuantityChange: (String) -> Unit,
    manualBarcodeInput: String,
    onManualBarcodeChange: (String) -> Unit,
    curr: String,
    doubleTaxRate: Double,
    subtotal: Double,
    taxAmount: Double,
    totalAmount: Double,
    hasActiveSession: Boolean,
    checkoutPulseTrigger: Int,
    onOpenScanner: () -> Unit,
    onRequestBulkDelete: () -> Unit,
    onLongPressCartItem: (Product) -> Unit,
    onRequestItemDelete: (Product) -> Unit,
    onRequestItemDiscount: (Product) -> Unit,
    onRequestPark: () -> Unit,
    onRequestCharge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tactile = rememberTactileFeedback()
    val metrics = LocalAdaptiveMetrics.current

    HeroCard(
        modifier = modifier
            .fillMaxSize()
            .padding(metrics.itemSpacing),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(metrics.cardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExpressiveSectionHeader(
                title = "Checkout Register",
                subtitle = "Scan items and take payment",
                icon = Icons.Default.PointOfSale
            )

            // 1) Inline Quantity + Barcode Scanner Row
            CashierInlineScannerRow(
                viewModel = viewModel,
                products = products,
                categoryNameById = categoryNameById,
                curr = curr,
                scannerQuantityInput = scannerQuantityInput,
                onScannerQuantityChange = onScannerQuantityChange,
                manualBarcodeInput = manualBarcodeInput,
                onManualBarcodeChange = onManualBarcodeChange,
                onOpenScanner = onOpenScanner
            )

            // 2) Transaction Line List
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
                            text = "Cart Register is empty",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Scan items or tap on left catalog folders.",
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val allChecked = cartItems.all { checkedCartItems[it.first.id] != false }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HeroCheckbox(
                                    checked = allChecked && cartItems.isNotEmpty(),
                                    onCheckedChange = { allCheckedValue ->
                                        cartItems.forEach { checkedCartItems[it.first.id] = allCheckedValue }
                                    },
                                    modifier = Modifier.testTag("bulk_select_checkbox")
                                )
                                Text("Select All", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }

                            val checkedCount = cartItems.count { checkedCartItems[it.first.id] != false }
                            if (checkedCount > 0) {
                                HeroTextButton(
                                    onClick = onRequestBulkDelete,
                                    color = HeroColor.Danger,
                                    icon = Icons.Default.Delete,
                                    size = HeroSize.Sm,
                                    text = "Delete Selected ($checkedCount)",
                                    modifier = Modifier.testTag("bulk_delete_button")
                                )
                            }
                        }
                    }

                    items(items = cartItems, key = { it.first.id }) { lineItem ->
                        val product = lineItem.first
                        val qty = lineItem.second
                        val isChecked = checkedCartItems[product.id] ?: true
                        val activeUom = selectedCartUoms[product.id] ?: UomOption("Base Unit", 1, product.price)
                        val itemDiscount = cartItemDiscounts[product.id] ?: 0.0

                        CashierCartLineItemCard(
                            product = product,
                            qty = qty,
                            isChecked = isChecked,
                            activeUom = activeUom,
                            itemDiscount = itemDiscount,
                            curr = curr,
                            viewModel = viewModel,
                            onCheckedChange = { checkedCartItems[product.id] = it },
                            onLongPress = { onLongPressCartItem(product) },
                            onDelete = { onRequestItemDelete(product) },
                            onDiscountClick = { onRequestItemDiscount(product) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            // 3) Math Subtotals
            HeroDivider()

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
                        Text(
                            "VAT (${String.format(Locale.US, "%.1f", doubleTaxRate)}% Included)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text("$curr${String.format(Locale.getDefault(), "%.2f", taxAmount)}")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TOTAL AMOUNT (VAT Inc.)",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    AnimatedContent(
                        targetState = totalAmount,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInVertically { height -> height } + fadeIn()).togetherWith(
                                    slideOutVertically { height -> -height } + fadeOut()
                                )
                            } else {
                                (slideInVertically { height -> -height } + fadeIn()).togetherWith(
                                    slideOutVertically { height -> height } + fadeOut()
                                )
                            }
                        },
                        label = "posTotalAmount",
                        modifier = Modifier.elasticValuePop(totalAmount)
                    ) { targetTotal ->
                        Text(
                            text = "$curr${String.format(Locale.getDefault(), "%.2f", targetTotal)}",
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 4) Clear / Park & Charge Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroOutlinedButton(
                    onClick = {
                        tactile.perform(TactileHapticRole.WarningReject)
                        viewModel.clearCart()
                    },
                    enabled = cartItems.isNotEmpty(),
                    color = HeroColor.Danger,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Cart",
                        tint = if (cartItems.isNotEmpty()) HeroUITheme.colors.danger else HeroUITheme.colors.muted.copy(alpha = 0.5f)
                    )
                }

                HeroOutlinedButton(
                    onClick = onRequestPark,
                    enabled = cartItems.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Pause,
                    text = "Park"
                )
            }

            HeroButton(
                onClick = onRequestCharge,
                enabled = activeCartItems.isNotEmpty() && hasActiveSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .celebratePulse(trigger = checkoutPulseTrigger)
                    .testTag("checkout_charge_button"),
                text = "Charge $curr${String.format(Locale.getDefault(), "%.2f", totalAmount)} [F9]",
                icon = Icons.Default.ShoppingBag,
                color = HeroColor.Primary,
                variant = HeroButtonVariant.Solid,
                size = HeroSize.Lg
            )
        }
    }
}

@Composable
private fun CashierInlineScannerRow(
    viewModel: StorePointViewModel,
    products: List<Product>,
    categoryNameById: Map<Int, String>,
    curr: String,
    scannerQuantityInput: String,
    onScannerQuantityChange: (String) -> Unit,
    manualBarcodeInput: String,
    onManualBarcodeChange: (String) -> Unit,
    onOpenScanner: () -> Unit
) {
    val context = LocalContext.current
    val tactile = rememberTactileFeedback()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeroInput(
            value = scannerQuantityInput,
            onValueChange = onScannerQuantityChange,
            label = { Text("Qty") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .width(74.dp)
                .testTag("scanner_qty_field"),
            singleLine = true
        )

        Box(modifier = Modifier.weight(1f)) {
            var isDropdownExpanded by remember { mutableStateOf(false) }
            val matchedSuggestions = remember(manualBarcodeInput, products, categoryNameById) {
                if (manualBarcodeInput.isBlank()) {
                    emptyList()
                } else {
                    products.filter {
                        val catName = categoryNameById[it.categoryId] ?: ""
                        it.name.contains(manualBarcodeInput, ignoreCase = true) ||
                            it.barcode.contains(manualBarcodeInput, ignoreCase = true) ||
                            catName.contains(manualBarcodeInput, ignoreCase = true)
                    }.take(5)
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val searchCopy = ScanSearchField.copyFor(maxWidth, hasSiblingField = true)
                HeroInput(
                    value = manualBarcodeInput,
                    onValueChange = {
                        onManualBarcodeChange(it)
                        isDropdownExpanded = true
                    },
                    label = { Text(searchCopy.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    placeholder = { Text(searchCopy.placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        IconButton(onClick = {
                            tactile.perform(TactileHapticRole.Tap)
                            onOpenScanner()
                        }) {
                            Icon(Icons.Default.QrCodeScanner, "Launch camera")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scanner_barcode_field"),
                    singleLine = true,
                    keyboardActions = KeyboardActions(onDone = {
                        if (manualBarcodeInput.isNotBlank()) {
                            val scanQty = scannerQuantityInput.toIntOrNull() ?: 1
                            tactile.perform(TactileHapticRole.ScanSuccess)
                            viewModel.handleBarcodeScan(manualBarcodeInput, scanQty)
                            onManualBarcodeChange("")
                            isDropdownExpanded = false
                        }
                    })
                )
            }

            if (isDropdownExpanded && matchedSuggestions.isNotEmpty()) {
                DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .heightIn(max = 280.dp)
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
                                        text = "$curr${String.format(Locale.getDefault(), "%.2f", prod.price)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            },
                            onClick = {
                                val scanQty = scannerQuantityInput.toIntOrNull() ?: 1
                                tactile.perform(TactileHapticRole.ScanSuccess)
                                viewModel.addToCart(prod, scanQty)
                                viewModel.playBeep()
                                Toast.makeText(context, "Added: $scanQty x ${prod.name}", Toast.LENGTH_SHORT).show()
                                onManualBarcodeChange("")
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
                    tactile.perform(TactileHapticRole.ScanSuccess)
                    viewModel.handleBarcodeScan(manualBarcodeInput, scanQty)
                    onManualBarcodeChange("")
                } else {
                    tactile.perform(TactileHapticRole.WarningReject)
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
}

@Composable
private fun CashierCartLineItemCard(
    product: Product,
    qty: Int,
    isChecked: Boolean,
    activeUom: UomOption,
    itemDiscount: Double,
    curr: String,
    viewModel: StorePointViewModel,
    onCheckedChange: (Boolean) -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onDiscountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tactile = rememberTactileFeedback()
    val effectivePrice = (activeUom.price - itemDiscount).coerceAtLeast(0.0)
    val linePriceTotal = effectivePrice * qty

    HeroCard(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(product.id) {
                detectTapGestures(onLongPress = { onLongPress() })
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(
            1.dp,
            if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroCheckbox(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange,
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
                    onClick = {
                        tactile.perform(TactileHapticRole.Tap)
                        onDelete()
                    },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (itemDiscount > 0.0) {
                        Column {
                            Text(
                                text = "$curr${String.format(Locale.getDefault(), "%.2f", activeUom.price)}",
                                style = MaterialTheme.typography.labelSmall.copy(textDecoration = TextDecoration.LineThrough),
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "$curr${String.format(Locale.getDefault(), "%.2f", effectivePrice)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Surface(
                            onClick = onDiscountClick,
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.testTag("cart_discount_chip_${product.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Percent, null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "-$curr${String.format(Locale.getDefault(), "%.2f", itemDiscount)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "$curr${String.format(Locale.getDefault(), "%.2f", activeUom.price)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        IconButton(
                            onClick = onDiscountClick,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("cart_discount_btn_${product.id}")
                        ) {
                            Icon(Icons.Default.Percent, contentDescription = "Add Discount", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(12.dp))
                        }
                    }

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
                            DropdownMenu(expanded = showUomMenu, onDismissRequest = { showUomMenu = false }) {
                                uomsList.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text("${option.name} ($curr${String.format(Locale.getDefault(), "%.2f", option.price)})") },
                                        onClick = {
                                            tactile.perform(TactileHapticRole.CartAdjust)
                                            viewModel.selectCartUom(product.id, option)
                                            showUomMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

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
                                onClick = {
                                    tactile.perform(TactileHapticRole.CartAdjust)
                                    viewModel.updateCartQuantity(product, qty - 1)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Remove, "Less", modifier = Modifier.size(13.dp))
                            }
                            Text(
                                text = "$qty",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .elasticValuePop(qty)
                            )
                            IconButton(
                                onClick = {
                                    tactile.perform(TactileHapticRole.CartAdjust)
                                    viewModel.updateCartQuantity(product, qty + 1)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Add, "Add", modifier = Modifier.size(13.dp))
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
