package com.munzo.storepoint.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.data.StorePointRepository
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.data.TransactionItem
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.*
import com.munzo.storepoint.ui.layout.LocalAdaptiveMetrics
import com.munzo.storepoint.ui.theme.GCashColor
import com.munzo.storepoint.ui.theme.LocalReduceMotion
import com.munzo.storepoint.ui.theme.MayaColor
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.elasticValuePop
import com.munzo.storepoint.ui.theme.glassPanel
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.ui.theme.shakeOnTrigger
import com.munzo.storepoint.util.DigitalServicesHelper
import com.munzo.storepoint.util.PendingDigitalService
import java.util.Locale

@Composable
fun CashierPaymentDialog(
    totalAmount: Double,
    subtotal: Double,
    taxAmount: Double,
    activeCartItems: List<Pair<Product, Int>>,
    curr: String,
    viewModel: StorePointViewModel,
    onDismiss: () -> Unit,
    onCheckoutSuccess: (Transaction, List<TransactionItem>, List<PendingDigitalService>) -> Unit
) {
    var selectedPaymentMethod by remember { mutableStateOf("CASH") } // "CASH", "GCASH", "MAYA", "SPLIT"
    var selectedSplitWallet by remember { mutableStateOf("GCash") }
    var amountPaidInput by remember { mutableStateOf("") }
    var splitCashPaidInput by remember { mutableStateOf("") }
    var ewalletRefNumber by remember { mutableStateOf("") }
    var customerNameInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current
    val metrics = LocalAdaptiveMetrics.current
    val tactile = rememberTactileFeedback()
    var shakeTrigger by remember { mutableIntStateOf(0) }

    val parsedPayAmount = amountPaidInput.replace(',', '.').toDoubleOrNull() ?: 0.0
    val calculatedChange = if (totalAmount < 0.0) -totalAmount else (parsedPayAmount - totalAmount).coerceAtLeast(0.0)

    val parsedSplitCash = splitCashPaidInput.replace(',', '.').toDoubleOrNull() ?: 0.0
    val splitWalletBalance = (totalAmount - parsedSplitCash).coerceAtLeast(0.0)
    val splitCashChange = (parsedSplitCash - totalAmount).coerceAtLeast(0.0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        HeroCard(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = metrics.dialogMaxWidth)
                .heightIn(max = metrics.heightDp * metrics.dialogMaxHeightFraction)
                .fillMaxWidth()
                .glassPanel(cornerRadius = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(metrics.cardPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Multi-Tender Payment Settlement",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Payment Methods selectable row (Cash, GCash, Maya, Split Tender)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroFilterChip(
                        selected = selectedPaymentMethod == "CASH",
                        onClick = { selectedPaymentMethod = "CASH" },
                        label = { Text("Cash", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.AttachMoney, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("pay_cash_method")
                    )
                    HeroFilterChip(
                        selected = selectedPaymentMethod == "GCASH",
                        onClick = { selectedPaymentMethod = "GCASH" },
                        label = { Text("GCash", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.PhoneAndroid, null, tint = GCashColor, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("pay_gcash_method")
                    )
                    HeroFilterChip(
                        selected = selectedPaymentMethod == "MAYA",
                        onClick = { selectedPaymentMethod = "MAYA" },
                        label = { Text("Maya", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null, tint = MayaColor, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("pay_maya_method")
                    )
                    HeroFilterChip(
                        selected = selectedPaymentMethod == "SPLIT",
                        onClick = { selectedPaymentMethod = "SPLIT" },
                        label = { Text("Split Tender", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.TrendingFlat, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("pay_split_method")
                    )
                }

                // Total Due Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Amount Due:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "$curr${String.format(Locale.getDefault(), "%.2f", totalAmount)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Mode-specific Payment Content
                AnimatedContent(
                    targetState = selectedPaymentMethod,
                    transitionSpec = {
                        if (reduceMotion) {
                            fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
                        } else {
                            (slideInHorizontally { width -> width / 4 } + fadeIn())
                                .togetherWith(slideOutHorizontally { width -> -width / 4 } + fadeOut())
                        }
                    },
                    label = "paymentMethodTransition",
                    modifier = Modifier.fillMaxWidth()
                ) { method ->
                    when (method) {
                        "CASH" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                // Common Denomination Quick-Pay Buttons
                                Text("Quick-Pay Cash Denominations:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HeroChip(
                                        text = "Exact",
                                        variant = HeroChipVariant.Flat,
                                        onClick = {
                                            amountPaidInput = String.format(Locale.US, "%.2f", if (totalAmount < 0.0) 0.0 else totalAmount)
                                        },
                                        modifier = Modifier.testTag("denom_exact_btn")
                                    )
                                    for (denom in listOf(20, 50, 100, 200, 500, 1000)) {
                                        HeroChip(
                                            text = "+$curr$denom",
                                            variant = HeroChipVariant.Flat,
                                            onClick = {
                                                val current = amountPaidInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                                                amountPaidInput = if (current == 0.0) {
                                                    String.format(Locale.US, "%.2f", denom.toDouble())
                                                } else {
                                                    String.format(Locale.US, "%.2f", current + denom)
                                                }
                                            },
                                            modifier = Modifier.testTag("denom_${denom}_btn")
                                        )
                                    }
                                }

                                HeroInput(
                                    value = amountPaidInput,
                                    onValueChange = { amountPaidInput = it },
                                    label = { Text("Tendered Cash Paid Amount ($curr)") },
                                    placeholder = { Text(String.format(Locale.getDefault(), "%.2f", if (totalAmount < 0.0) 0.0 else totalAmount)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .shakeOnTrigger(shakeTrigger)
                                        .testTag("tendered_cash_input"),
                                    trailingIcon = {
                                        if (amountPaidInput.isNotEmpty()) {
                                            IconButton(onClick = { amountPaidInput = "" }) {
                                                Icon(Icons.Default.Clear, "Clear")
                                            }
                                        }
                                    }
                                )

                                // Instant Change Calculation
                                AnimatedVisibility(
                                    visible = totalAmount < 0.0 || (amountPaidInput.isNotBlank() && parsedPayAmount >= totalAmount),
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val changeLabel = if (totalAmount < 0.0) "CASH TO PAY OUT:" else "CHANGE TO RETURN:"
                                            Text(changeLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            Text(
                                                text = "$curr${String.format(Locale.getDefault(), "%.2f", calculatedChange)}",
                                                fontWeight = FontWeight.ExtraBold,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.elasticValuePop(calculatedChange)
                                            )
                                        }
                                    }
                                }
                                AnimatedVisibility(
                                    visible = amountPaidInput.isNotBlank() && parsedPayAmount < totalAmount,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Text(
                                        "Insufficient tender cash paid (Short by $curr${String.format(Locale.getDefault(), "%.2f", totalAmount - parsedPayAmount)}).",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    "GCASH" -> {
                        HeroCard(
                            colors = CardDefaults.cardColors(containerColor = GCashColor.copy(alpha = 0.12f)),
                            border = BorderStroke(1.5.dp, GCashColor.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.PhoneAndroid, null, tint = GCashColor)
                                    Text("GCash Digital Payment Settlement", fontWeight = FontWeight.Bold, color = GCashColor)
                                }
                                Text(
                                    "Collect exact amount $curr${String.format(Locale.getDefault(), "%.2f", totalAmount)} via customer GCash transfer or QR code.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        HeroInput(
                            value = ewalletRefNumber,
                            onValueChange = { ewalletRefNumber = it },
                            label = { Text("GCash Reference Number (Optional)") },
                            placeholder = { Text("e.g. 100234567890") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("ewallet_ref_input")
                        )
                    }
                    "MAYA" -> {
                        HeroCard(
                            colors = CardDefaults.cardColors(containerColor = MayaColor.copy(alpha = 0.12f)),
                            border = BorderStroke(1.5.dp, MayaColor.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.AccountBalanceWallet, null, tint = MayaColor)
                                    Text("Maya Digital Payment Settlement", fontWeight = FontWeight.Bold, color = MayaColor)
                                }
                                Text(
                                    "Collect exact amount $curr${String.format(Locale.getDefault(), "%.2f", totalAmount)} via customer Maya transfer or QR code.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        HeroInput(
                            value = ewalletRefNumber,
                            onValueChange = { ewalletRefNumber = it },
                            label = { Text("Maya Reference Number (Optional)") },
                            placeholder = { Text("e.g. 987654321") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("ewallet_ref_input")
                        )
                    }
                    "SPLIT" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("Split Bill: Part Cash + Part E-Wallet", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

                            // Quick presets for cash portion
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (denom in listOf(20, 50, 100, 200, 500)) {
                                    if (denom < totalAmount) {
                                        HeroChip(
                                            text = "Cash $curr$denom",
                                            variant = HeroChipVariant.Flat,
                                            onClick = { splitCashPaidInput = String.format(Locale.US, "%.2f", denom.toDouble()) }
                                        )
                                    }
                                }
                            }

                            HeroInput(
                                value = splitCashPaidInput,
                                onValueChange = { splitCashPaidInput = it },
                                label = { Text("Cash Tendered Portion ($curr)") },
                                placeholder = { Text("e.g. 100.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag("split_cash_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Remaining E-Wallet:", style = MaterialTheme.typography.bodySmall)
                                HeroFilterChip(
                                    selected = selectedSplitWallet == "GCash",
                                    onClick = { selectedSplitWallet = "GCash" },
                                    label = { Text("GCash") }
                                )
                                HeroFilterChip(
                                    selected = selectedSplitWallet == "Maya",
                                    onClick = { selectedSplitWallet = "Maya" },
                                    label = { Text("Maya") }
                                )
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Cash Paid:", style = MaterialTheme.typography.bodySmall)
                                        Text("$curr${String.format(Locale.getDefault(), "%.2f", parsedSplitCash)}", fontWeight = FontWeight.Bold)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Balance via $selectedSplitWallet:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text("$curr${String.format(Locale.getDefault(), "%.2f", splitWalletBalance)}", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (splitCashChange > 0.0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Cash Change:", style = MaterialTheme.typography.bodySmall)
                                            Text("$curr${String.format(Locale.getDefault(), "%.2f", splitCashChange)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }

                            HeroInput(
                                value = ewalletRefNumber,
                                onValueChange = { ewalletRefNumber = it },
                                label = { Text("$selectedSplitWallet Ref # (Optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                }

                HeroInput(
                    value = customerNameInput,
                    onValueChange = { customerNameInput = it },
                    label = { Text("Customer Name (Optional)") },
                    placeholder = { Text("e.g. Walk-in or Juan Dela Cruz") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("customer_name_input")
                )

                // Submit Checkout Actions
                val isSubmitEnabled = when (selectedPaymentMethod) {
                    "CASH" -> totalAmount <= 0.0 || (amountPaidInput.isNotBlank() && parsedPayAmount >= totalAmount)
                    "SPLIT" -> splitCashPaidInput.isNotBlank() && parsedSplitCash > 0.0 && parsedSplitCash < totalAmount
                    else -> true // GCASH or MAYA exact digital payment
                }

                HeroButton(
                    onClick = {
                        if (isSubmitting) return@HeroButton
                        if (!isSubmitEnabled) {
                            tactile.perform(TactileHapticRole.WarningReject)
                            shakeTrigger++
                            return@HeroButton
                        }
                        isSubmitting = true
                        val finalMethod = when (selectedPaymentMethod) {
                            "SPLIT" -> "SPLIT ($selectedSplitWallet)"
                            else -> selectedPaymentMethod
                        }
                        val finalCash = when (selectedPaymentMethod) {
                            "CASH" -> parsedPayAmount
                            "SPLIT" -> parsedSplitCash
                            else -> totalAmount
                        }
                        val finalCustomer = when {
                            selectedPaymentMethod == "SPLIT" -> {
                                val base = customerNameInput.ifBlank { "Walk-in" }
                                val refStr = if (ewalletRefNumber.isNotBlank()) " Ref:$ewalletRefNumber" else ""
                                "$base [Cash: $curr${String.format(Locale.US, "%.2f", parsedSplitCash)}, $selectedSplitWallet: $curr${String.format(Locale.US, "%.2f", splitWalletBalance)}$refStr]"
                            }
                            selectedPaymentMethod != "CASH" && ewalletRefNumber.isNotBlank() -> {
                                val base = customerNameInput.ifBlank { "Walk-in" }
                                "$base [Ref: $ewalletRefNumber]"
                            }
                            else -> customerNameInput
                        }

                        executePaymentSubmission(
                            viewModel = viewModel,
                            paymentMethod = finalMethod,
                            cashPaid = finalCash,
                            subtotal = subtotal,
                            taxAmount = taxAmount,
                            totalAmount = totalAmount,
                            cartLineItems = activeCartItems,
                            customerName = finalCustomer,
                            onError = { _ ->
                                isSubmitting = false
                                tactile.perform(TactileHapticRole.WarningReject)
                                shakeTrigger++
                            },
                            onComplete = { tx, items ->
                                isSubmitting = false
                                val pendingServices = DigitalServicesHelper.parseDigitalServiceItems(items)
                                onCheckoutSuccess(tx, items, pendingServices)
                                if ((selectedPaymentMethod == "CASH" || selectedPaymentMethod == "SPLIT") && viewModel.isAutoKickDrawerEnabled.value) {
                                    viewModel.kickCashDrawer { _, _ -> }
                                }
                            }
                        )
                    },
                    enabled = isSubmitEnabled && !isSubmitting,
                    modifier = Modifier.fillMaxWidth().testTag("payment_submit_button"),
                    text = if (isSubmitting) "Processing..." else "Complete Checkout",
                    icon = if (isSubmitting) null else Icons.Filled.CheckCircle,
                    color = HeroColor.Primary,
                    variant = HeroButtonVariant.Solid,
                    size = HeroSize.Lg
                )
            }
        }
    }
}

private fun executePaymentSubmission(
    viewModel: StorePointViewModel,
    paymentMethod: String,
    cashPaid: Double,
    subtotal: Double,
    taxAmount: Double,
    totalAmount: Double,
    cartLineItems: List<Pair<Product, Int>>,
    customerName: String = "",
    onError: (String) -> Unit = {},
    onComplete: (Transaction, List<TransactionItem>) -> Unit
) {
    val selectedUoms = viewModel.selectedCartUoms.value
    val currentDiscounts = viewModel.cartItemDiscounts.value
    val itemsToSave = cartLineItems.map { lineItem ->
        val prod = lineItem.first
        val uom = selectedUoms[prod.id] ?: com.munzo.storepoint.data.UomOption("Base Unit", 1, prod.price)
        val discount = currentDiscounts[prod.id] ?: 0.0
        val effectivePrice = (uom.price - discount).coerceAtLeast(0.0)
        val baseName = if (uom.name.equals("Base Unit", ignoreCase = true) || uom.name.equals("Piece", ignoreCase = true) || uom.name.equals("Stick", ignoreCase = true)) {
            prod.name
        } else {
            "${prod.name} (${uom.name})"
        }
        val nameWithUom = if (discount > 0.0) {
            val currSym = viewModel.storeConfig.value?.currencySymbol ?: "$"
            "$baseName (Disc -$currSym${String.format(Locale.US, "%.2f", discount)})"
        } else {
            baseName
        }
        TransactionItem(
            transactionId = 0,
            productId = prod.id,
            productName = nameWithUom,
            price = StorePointRepository.roundMoney(effectivePrice),
            quantity = lineItem.second,
            cost = prod.cost * uom.multiplier,
            uomName = uom.name
        )
    }

    viewModel.executeCheckout(
        paymentMethod = paymentMethod,
        cashPaid = if (paymentMethod == "CARD" || paymentMethod == "GCASH" || paymentMethod == "MAYA") totalAmount else cashPaid,
        subtotal = subtotal,
        taxAmount = taxAmount,
        totalAmount = totalAmount,
        checkoutItems = cartLineItems,
        customerName = customerName,
        onError = onError,
        onSuccess = { createdTransaction ->
            val finalItems = itemsToSave.map { it.copy(transactionId = createdTransaction.id) }
            onComplete(createdTransaction, finalItems)
        }
    )
}
