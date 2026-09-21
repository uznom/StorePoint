package com.munzo.storepoint.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.munzo.storepoint.util.DigitalServicesHelper
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.ui.components.ExpressiveButtonGroup
import com.munzo.storepoint.ui.components.ExpressiveChip
import com.munzo.storepoint.ui.theme.ExpressiveSplitButton
import com.munzo.storepoint.ui.theme.GCashColor
import com.munzo.storepoint.ui.theme.LoadColor
import com.munzo.storepoint.ui.theme.MayaColor
import java.util.Locale

fun calculateTieredStoreFee(amount: Double, baseFeePerThousand: Double = 10.0): Double {
    if (amount <= 0.0) return 0.0
    val k = java.lang.Math.floor(amount / 1000.0).toInt()
    val baseMultiplier = if (baseFeePerThousand > 0.0) baseFeePerThousand else 10.0
    val baseFee = k * baseMultiplier
    val r = amount % 1000.0
    return if (amount >= 1000.0 && r == 0.0) {
        baseFee
    } else if (r <= 500.0) {
        baseFee + (baseMultiplier * 0.5).coerceAtLeast(5.0)
    } else {
        baseFee + baseMultiplier
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalServicesForm(
    curr: String,
    initialService: String = "GCash",
    initialNetwork: String = "Smart",
    onAddProductToCart: (Product) -> Unit,
    modifier: Modifier = Modifier,
    initialGCashBalance: Double,
    smartLoadBalance: Double = 0.0,
    globeLoadBalance: Double = 0.0,
    isSensitiveDataVisible: Boolean = false,
    hasGCash: Boolean = true,
    hasMaya: Boolean = true,
    hasLoad: Boolean = true,
    digitalCategoryId: Int = 1,
    defaultLoadFee: Double = 2.0,
    defaultGcashFee: Double = 10.0,
    defaultMayaFee: Double = 10.0,
    defaultMayaBankFee: Double = 15.0
) {
    val context = LocalContext.current
    val defaultService = when {
        hasGCash -> "GCash"
        hasMaya -> "Maya"
        hasLoad -> "Load"
        else -> "GCash"
    }
    var selectedService by remember(initialService, hasGCash, hasMaya, hasLoad) {
        val initialFound = when (initialService) {
            "GCash" -> if (hasGCash) "GCash" else null
            "Maya" -> if (hasMaya) "Maya" else null
            "Load" -> if (hasLoad) "Load" else null
            else -> null
        }
        mutableStateOf(initialFound ?: defaultService)
    }
    var selectedAction by remember { mutableStateOf("Cash In") } // "Cash In", "Cash Out"
    var selectedNetwork by remember(initialNetwork) { mutableStateOf(initialNetwork) } // "Smart", "TNT", "Globe", "TM"
    var mobileNumber by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var customFeeInput by remember(selectedService, selectedAction) { mutableStateOf("") }
    var customBankFeeInput by remember(selectedService, selectedAction) { mutableStateOf("") }

    val amount = amountStr.toDoubleOrNull() ?: 0.0

    val computedDefaultFee = when (selectedService) {
        "Load" -> defaultLoadFee
        "GCash" -> calculateTieredStoreFee(amount, defaultGcashFee)
        "Maya" -> calculateTieredStoreFee(amount, defaultMayaFee)
        else -> calculateTieredStoreFee(amount, defaultGcashFee)
    }

    val storeFee = customFeeInput.toDoubleOrNull() ?: computedDefaultFee

    val computedBankFee = if (selectedService == "Maya" && selectedAction == "Cash In") {
        defaultMayaBankFee
    } else {
        0.0
    }
    val bankFee = if (selectedService == "Maya" && selectedAction == "Cash In") {
        customBankFeeInput.toDoubleOrNull() ?: computedBankFee
    } else {
        0.0
    }

    val totalToPay = if (selectedService == "Load") {
        amount + storeFee
    } else if (selectedAction == "Cash In") {
        amount + storeFee + bankFee
    } else {
        // Cash-Out: customer sends Amount. We hand them Amount - storeFee.
        amount - storeFee
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("digital_services_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Sari-Sari Digital Services",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Surcharge fees & accounting calculated automatically",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Service Selector (GCash / Maya / Load) — responsive: equal-width cards on
            // wide windows, stacked full-width cards on narrow phones so labels never clip.
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                val stackServices = maxWidth < 360.dp
                val services = listOf("GCash", "Maya", "Load").filter { service ->
                    when (service) {
                        "GCash" -> hasGCash
                        "Maya" -> hasMaya
                        "Load" -> hasLoad
                        else -> true
                    }
                }

                @Composable
                fun ServiceCard(service: String, modifier: Modifier) {
                    val isSelected = selectedService == service
                    val color = when (service) {
                        "GCash" -> GCashColor
                        "Maya" -> MayaColor
                        else -> LoadColor
                    }
                    val shapeCorner by animateDpAsState(
                        targetValue = if (isSelected) 22.dp else 12.dp,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 250f),
                        label = "CornerShift"
                    )
                    // Unselected items recede instead of growing, so cards never overlap.
                    val scaleFactor by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.97f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                        label = "ScaleShift"
                    )

                    Card(
                        onClick = {
                            selectedService = service
                            if (service == "Load") {
                                selectedAction = "Cash In"
                            }
                        },
                        modifier = modifier
                            .height(52.dp)
                            .graphicsLayer {
                                scaleX = scaleFactor
                                scaleY = scaleFactor
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) color.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(shapeCorner),
                        border = BorderStroke(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val icon = when (service) {
                                "GCash" -> Icons.Default.AccountBalanceWallet
                                "Maya" -> Icons.Default.CreditCard
                                else -> Icons.Default.Phone
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = service,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (stackServices) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        services.forEach { service ->
                            ServiceCard(service, Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        services.forEach { service ->
                            ServiceCard(service, Modifier.weight(1f))
                        }
                    }
                }
            }

            // Pay-First Flow Note: Cashier collects payment first before launching GCash or Load app post-checkout
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Pay-First Flow: Customer pays first. App launcher will open post-checkout.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Action / Type selector (Cash In / Cash Out) — M3E connected button group
            // keeps both options on one segmented surface with full labels at any width.
            if (selectedService != "Load") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Transaction Type:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    ExpressiveButtonGroup(
                        options = listOf(
                            Icons.Default.ArrowUpward to "Cash In",
                            Icons.Default.ArrowDownward to "Cash Out"
                        ),
                        selectedIndex = if (selectedAction == "Cash In") 0 else 1,
                        onSelected = { index -> selectedAction = if (index == 0) "Cash In" else "Cash Out" },
                        connected = true,
                        modifier = Modifier.fillMaxWidth().testTag("digital_action_group")
                    )
                }
            } else {
                // Network Selector - ONLY shown for Load
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Load Mobile Network:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Smart", "TNT", "Globe", "TM").forEach { net ->
                            val isSelected = selectedNetwork == net
                            ExpressiveChip(
                                label = net,
                                selected = isSelected,
                                onClick = { selectedNetwork = net }
                            )
                        }
                    }
                }
            }

            // Input Fields Row
            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it },
                label = { Text("Retailing Amount ($curr)") },
                placeholder = { Text("0.00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("digital_amount_input"),
                leadingIcon = { Icon(Icons.Default.Payments, null, modifier = Modifier.size(16.dp)) }
            )

            // Customer Mobile Number Input (for GCash, Maya, Load)
            OutlinedTextField(
                value = mobileNumber,
                onValueChange = { input ->
                    if (input.length <= 13 && input.all { it.isDigit() || it == '+' }) {
                        mobileNumber = input
                    }
                },
                label = {
                    Text(
                        when (selectedService) {
                            "Load" -> "Recipient Mobile Number"
                            else -> if (selectedAction == "Cash Out") "Customer GCash/Maya Number" else "Recipient GCash/Maya Number"
                        }
                    )
                },
                placeholder = { Text("e.g. 09171234567") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().testTag("digital_mobile_input"),
                leadingIcon = { Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp)) },
                supportingText = {
                    Text(
                        if (selectedAction == "Cash Out" && selectedService != "Load")
                            "Customer sends funds to store; verify receipt before payout"
                        else
                            "Pay-first flow: Customer pays first; app launcher opens post-checkout"
                    )
                }
            )

            // Variable Store Charge & Bank Fee Inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customFeeInput,
                    onValueChange = { customFeeInput = it },
                    label = { Text("Store Service Charge ($curr)") },
                    placeholder = { Text(String.format(Locale.getDefault(), "%.2f", computedDefaultFee)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).testTag("digital_fee_input"),
                    leadingIcon = { Icon(Icons.Default.PriceCheck, null, modifier = Modifier.size(16.dp)) },
                    supportingText = {
                        val defStr = String.format(Locale.getDefault(), "%.2f", computedDefaultFee)
                        Text(if (customFeeInput.isEmpty()) "Owner default: $curr$defStr" else "Custom charge active")
                    }
                )

                if (selectedService == "Maya" && selectedAction == "Cash In") {
                    OutlinedTextField(
                        value = customBankFeeInput,
                        onValueChange = { customBankFeeInput = it },
                        label = { Text("Maya Bank Fee ($curr)") },
                        placeholder = { Text(String.format(Locale.getDefault(), "%.2f", defaultMayaBankFee)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("digital_bank_fee_input"),
                        supportingText = {
                            val bankStr = String.format(Locale.getDefault(), "%.2f", defaultMayaBankFee)
                            Text(if (customBankFeeInput.isEmpty()) "Owner default: $curr$bankStr" else "Custom bank fee")
                        }
                    )
                }
            }

            // Quick Pre-filled Amount Chips
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Quick Cash Presets:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = if (selectedService == "Load") listOf("20", "50", "100", "200") else listOf("100", "300", "500", "1000", "1500")
                    presets.forEach { preset ->
                        SuggestionChip(
                            onClick = { amountStr = preset },
                            label = { Text("$curr$preset") }
                        )
                    }
                }
            }

            // Receipt Breakdown / Calculations Dashboard
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "TRANSACTION BREAKDOWN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Base Retailing Amount:", style = MaterialTheme.typography.bodySmall)
                        Text("$curr${String.format(Locale.getDefault(), "%.2f", amount)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    if (selectedService != "Load") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Store Service Fee:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            Text("$curr${String.format(Locale.getDefault(), "%.2f", storeFee)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                        if (bankFee > 0.0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Maya Transfer Bank Fee (via GCash):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                Text("$curr${String.format(Locale.getDefault(), "%.2f", bankFee)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    } else { // Load
                        val loadWalletCost = com.munzo.storepoint.data.StorePointRepository.roundMoney(amount * 0.98)
                        val loadProfit = com.munzo.storepoint.data.StorePointRepository.roundMoney(storeFee + (amount * 0.02))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Customer Surcharge (Store Fee):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            Text("$curr${String.format(Locale.getDefault(), "%.2f", storeFee)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Retailer Wallet Cost (2% Rebate):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            Text("$curr${String.format(Locale.getDefault(), "%.2f", loadWalletCost)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Net Retailer Profit (Fee + 2%):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Text("+$curr${String.format(Locale.getDefault(), "%.2f", loadProfit)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val totalLabel = if (selectedService == "Load") {
                            "Total Collected from Customer:"
                        } else if (selectedAction == "Cash In") {
                            "Total Collected from Customer:"
                        } else {
                            "Disbursed Cash to Customer:"
                        }
                        val finalCostStyle = if (selectedAction == "Cash In" || selectedService == "Load") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

                        Text(totalLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        Text(
                            text = "$curr${String.format(Locale.getDefault(), "%.2f", totalToPay)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = finalCostStyle
                        )
                    }

                    // Accounting balance preview
                    if (selectedService == "GCash" || selectedService == "Maya") {
                        Spacer(modifier = Modifier.height(4.dp))
                        val balLabel = if (selectedService == "GCash") "GCash Balance:" else "Equivalent Digital Balance:"
                        val calculatedBal = if (selectedAction == "Cash In") {
                            initialGCashBalance - (amount + bankFee)
                        } else {
                            initialGCashBalance + amount
                        }
                        val displayedBal = if (isSensitiveDataVisible) "$curr${String.format(Locale.getDefault(), "%.2f", calculatedBal.coerceAtLeast(0.0))}" else "$curr••••"
                        Text(
                            text = "Estimated final $balLabel $displayedBal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else if (selectedService == "Load") {
                        Spacer(modifier = Modifier.height(4.dp))
                        val isSmartNetwork = selectedNetwork.equals("Smart", ignoreCase = true) || selectedNetwork.equals("TNT", ignoreCase = true)
                        val walletName = if (isSmartNetwork) "Smart Load" else "Globe Load"
                        val currentWalletBal = if (isSmartNetwork) smartLoadBalance else globeLoadBalance
                        val loadWalletCost = com.munzo.storepoint.data.StorePointRepository.roundMoney(amount * 0.98)
                        val calculatedBal = (currentWalletBal - loadWalletCost).coerceAtLeast(0.0)
                        val displayedBal = if (isSensitiveDataVisible) "$curr${String.format(Locale.getDefault(), "%.2f", calculatedBal)}" else "$curr••••"
                        Text(
                            text = "Estimated final $walletName Wallet: $displayedBal (Cost: $curr${String.format(Locale.getDefault(), "%.2f", loadWalletCost)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Add to Cart action button using the Material 3 Expressive Split Button
            ExpressiveSplitButton(
                mainLabel = "Add Digital Retailing to Cart",
                mainIcon = { Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(18.dp)) },
                onMainClick = {
                    if (amount <= 0.0) return@ExpressiveSplitButton
                    
                    val serviceName = selectedService
                    val actionName = if (selectedService == "Load") "Top-Up" else selectedAction
                    val networkSuffix = if (selectedService == "Load") " ($selectedNetwork)" else ""
                    val numSuffix = if (mobileNumber.isNotBlank()) " for $mobileNumber" else ""
                    
                    // Embed BankFee to parse securely at checkout VM
                    val prodName = "$serviceName $actionName$networkSuffix$numSuffix (Amt: $amount, Fee: $storeFee, BankFee: $bankFee)"
                    
                    val dynamicProduct = Product(
                        id = -100 - (System.currentTimeMillis() % 100000).toInt() - (1..999).random(),
                        name = prodName,
                        categoryId = digitalCategoryId, 
                        price = if (selectedAction == "Cash Out") -totalToPay else totalToPay,
                        stockCount = 9999,
                        barcode = ""
                    )
                    onAddProductToCart(dynamicProduct)
                    
                    amountStr = ""
                    mobileNumber = ""
                },
                onSecondaryClick = {
                    Toast.makeText(context, "Retailing Form Quick-Checked!", Toast.LENGTH_SHORT).show()
                },
                containerColor = if (amount > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = if (amount > 0.0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_digital_to_cart_btn")
            )

            // Quick Launch Authorized Terminal Apps Section (Configured by Admin)
            val adminPrefs = remember { context.getSharedPreferences(DigitalServicesHelper.PREFS_NAME, Context.MODE_PRIVATE) }
            val allowedApps = remember(adminPrefs) { DigitalServicesHelper.getAllowedApps(adminPrefs) }
            val pm = context.packageManager
            val launchableAllowedApps = remember(allowedApps) {
                allowedApps.mapNotNull { pkg ->
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        val label = pm.getApplicationLabel(appInfo).toString()
                        pkg to label
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            if (launchableAllowedApps.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Authorized Terminal Apps (Quick Launch)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        launchableAllowedApps.forEach { (pkg, label) ->
                            AssistChip(
                                onClick = {
                                    DigitalServicesHelper.launchAppByPackage(context, pkg)
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
