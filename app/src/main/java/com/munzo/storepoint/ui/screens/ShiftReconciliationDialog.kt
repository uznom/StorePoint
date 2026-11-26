package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munzo.storepoint.data.CashierSession
import com.munzo.storepoint.data.ShiftReconciliation
import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.data.StorePointRepository
import com.munzo.storepoint.ui.*
import com.munzo.storepoint.ui.theme.HeroUITheme
import com.munzo.storepoint.ui.components.*
import com.munzo.storepoint.ui.components.ExpressiveButton
import com.munzo.storepoint.util.BiometricAuthHelper
import com.munzo.storepoint.ui.components.ExpressiveButtonSize
import com.munzo.storepoint.ui.components.ExpressiveButtonVariant
import com.munzo.storepoint.util.EscPosHelper
import com.munzo.storepoint.util.SecurityHelper
import androidx.fragment.app.FragmentActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftReconciliationDialog(
    session: CashierSession,
    viewModel: StorePointViewModel,
    expectedCash: Double,
    startingFloat: Double = session.startingCash,
    cashSales: Double = 0.0,
    cashIn: Double = 0.0,
    cashOut: Double = 0.0,
    cashRefunds: Double = 0.0,
    expectedGCash: Double,
    gcashStartingBalance: Double = expectedGCash,
    gcashInflow: Double = 0.0,
    gcashDisbursed: Double = 0.0,
    expectedSmartLoad: Double,
    smartStartingBalance: Double = expectedSmartLoad,
    smartReloadSales: Double = 0.0,
    expectedGlobeLoad: Double,
    globeStartingBalance: Double = expectedGlobeLoad,
    globeReloadSales: Double = 0.0,
    onDismiss: () -> Unit,
    onShiftClosed: (ShiftReconciliation) -> Unit
) {
    val context = LocalContext.current
    val storeConfig by viewModel.storeConfig.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val curr = storeConfig?.currencySymbol ?: "₱"

    // Cash Drawer Physical Bills
    var count1000Str by remember { mutableStateOf("") }
    var count500Str by remember { mutableStateOf("") }
    var count200Str by remember { mutableStateOf("") }
    var count100Str by remember { mutableStateOf("") }
    var count50Str by remember { mutableStateOf("") }
    var count20Str by remember { mutableStateOf("") }

    // Cash Drawer Philippine Peso Coins Breakdown (₱10, ₱5, ₱1, ₱0.25, loose)
    var count10Str by remember { mutableStateOf("") }
    var count5Str by remember { mutableStateOf("") }
    var count1Str by remember { mutableStateOf("") }
    var count25cStr by remember { mutableStateOf("") }
    var countOtherCoinsStr by remember { mutableStateOf("") }

    val c1000 = count1000Str.toIntOrNull() ?: 0
    val c500 = count500Str.toIntOrNull() ?: 0
    val c200 = count200Str.toIntOrNull() ?: 0
    val c100 = count100Str.toIntOrNull() ?: 0
    val c50 = count50Str.toIntOrNull() ?: 0
    val c20 = count20Str.toIntOrNull() ?: 0

    val c10 = count10Str.toIntOrNull() ?: 0
    val c5 = count5Str.toIntOrNull() ?: 0
    val c1 = count1Str.toIntOrNull() ?: 0
    val c25c = count25cStr.toIntOrNull() ?: 0
    val cOtherCoins = countOtherCoinsStr.toDoubleOrNull() ?: 0.0

    // Subtotals
    val totalBills = remember(c1000, c500, c200, c100, c50, c20) {
        StorePointRepository.roundMoney(
            (c1000 * 1000.0) +
            (c500 * 500.0) +
            (c200 * 200.0) +
            (c100 * 100.0) +
            (c50 * 50.0) +
            (c20 * 20.0)
        )
    }

    val totalCoins = remember(c10, c5, c1, c25c, cOtherCoins) {
        StorePointRepository.roundMoney(
            (c10 * 10.0) +
            (c5 * 5.0) +
            (c1 * 1.0) +
            (c25c * 0.25) +
            cOtherCoins
        )
    }

    val totalCountedCash = remember(totalBills, totalCoins) {
        StorePointRepository.roundMoney(totalBills + totalCoins)
    }

    // GCash & Prepaid Load Counts (Pre-fills with ledger balance for quick audit or manual count)
    var gcashCountStr by remember { mutableStateOf(String.format(Locale.US, "%.2f", expectedGCash)) }
    var smartLoadCountStr by remember { mutableStateOf(String.format(Locale.US, "%.2f", expectedSmartLoad)) }
    var globeLoadCountStr by remember { mutableStateOf(String.format(Locale.US, "%.2f", expectedGlobeLoad)) }

    val countedGCash = gcashCountStr.toDoubleOrNull() ?: 0.0
    val countedSmart = smartLoadCountStr.toDoubleOrNull() ?: 0.0
    val countedGlobe = globeLoadCountStr.toDoubleOrNull() ?: 0.0

    // Mandatory Shift Closure Guard:
    // Requires physical cash counts to have been entered (at least one bill or coin field must be entered).
    val hasEnteredCashCount = count1000Str.isNotBlank() || count500Str.isNotBlank() ||
            count200Str.isNotBlank() || count100Str.isNotBlank() || count50Str.isNotBlank() ||
            count20Str.isNotBlank() || count10Str.isNotBlank() || count5Str.isNotBlank() ||
            count1Str.isNotBlank() || count25cStr.isNotBlank() || countOtherCoinsStr.isNotBlank()

    val hasEnteredGCashCount = storeConfig?.hasGCash != true || gcashCountStr.isNotBlank()
    val hasEnteredSmartCount = storeConfig?.hasLoad != true || smartLoadCountStr.isNotBlank()
    val hasEnteredGlobeCount = storeConfig?.hasLoad != true || globeLoadCountStr.isNotBlank()

    val allMandatoryCountsEntered = hasEnteredCashCount && hasEnteredGCashCount && hasEnteredSmartCount && hasEnteredGlobeCount

    // Variances: Variance = Counted - Expected
    val cashVariance = remember(totalCountedCash, expectedCash) {
        StorePointRepository.roundMoney(totalCountedCash - expectedCash)
    }
    val gcashVariance = remember(countedGCash, expectedGCash) {
        StorePointRepository.roundMoney(countedGCash - expectedGCash)
    }
    val smartVariance = remember(countedSmart, expectedSmartLoad) {
        StorePointRepository.roundMoney(countedSmart - expectedSmartLoad)
    }
    val globeVariance = remember(countedGlobe, expectedGlobeLoad) {
        StorePointRepository.roundMoney(countedGlobe - expectedGlobeLoad)
    }

    val hasCashDiscrepancy = Math.abs(cashVariance) >= 0.01
    val hasGcashDiscrepancy = storeConfig?.hasGCash == true && Math.abs(gcashVariance) >= 0.01
    val hasSmartDiscrepancy = storeConfig?.hasLoad == true && Math.abs(smartVariance) >= 0.01
    val hasGlobeDiscrepancy = storeConfig?.hasLoad == true && Math.abs(globeVariance) >= 0.01

    val hasAnyDiscrepancy = hasCashDiscrepancy || hasGcashDiscrepancy || hasSmartDiscrepancy || hasGlobeDiscrepancy

    // Manager Override & Audit Controls
    var managerUsername by remember { mutableStateOf("") }
    var managerPin by remember { mutableStateOf("") }
    var discrepancyNote by remember { mutableStateOf("") }
    var managerOverrideVerified by remember { mutableStateOf(false) }
    var overrideError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Completed Reconciliation for Receipt Printing
    var savedReconciliation by remember { mutableStateOf<ShiftReconciliation?>(null) }

    val formulaSummary = remember(startingFloat, cashSales, cashIn, cashOut, cashRefunds) {
        EscPosHelper.DrawerBalancingSummary(
            startingFloat = startingFloat,
            cashSales = cashSales,
            cashIn = cashIn,
            cashOut = cashOut,
            cashRefunds = cashRefunds
        )
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Shift Handover & Drawer Balancing", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Mandatory Shift Closure Guard Notice
                if (!hasEnteredCashCount) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.fillMaxWidth().testTag("mandatory_closure_guard_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Mandatory Shift Closure Guard Active", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text(
                                    "Physical cash drawer counts are required before ending the shift.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }

                // 1) Physical Cash Drawer Denominations Breakdown
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("1. Cash Drawer Physical Count", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Surface(
                                shape = CircleShape,
                                color = if (!hasEnteredCashCount) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else if (hasCashDiscrepancy) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    Color(0xFF10B981).copy(alpha = 0.2f)
                                }
                            ) {
                                Text(
                                    text = if (!hasEnteredCashCount) "COUNT REQUIRED"
                                    else if (hasCashDiscrepancy) "VARIANCE: $curr${String.format(Locale.US, "%.2f", cashVariance)}"
                                    else "BALANCED",
                                    color = if (!hasEnteredCashCount) MaterialTheme.colorScheme.outline
                                    else if (hasCashDiscrepancy) MaterialTheme.colorScheme.onErrorContainer
                                    else Color(0xFF10B981),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Bills Grid (₱1,000, ₱500, ₱200, ₱100, ₱50, ₱20)
                        Text("Peso Paper Bills", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DenomField(label = "₱1,000", value = count1000Str, onValueChange = { count1000Str = it }, testTag = "count_1000_input", modifier = Modifier.weight(1f))
                            DenomField(label = "₱500", value = count500Str, onValueChange = { count500Str = it }, testTag = "count_500_input", modifier = Modifier.weight(1f))
                            DenomField(label = "₱200", value = count200Str, onValueChange = { count200Str = it }, testTag = "count_200_input", modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DenomField(label = "₱100", value = count100Str, onValueChange = { count100Str = it }, testTag = "count_100_input", modifier = Modifier.weight(1f))
                            DenomField(label = "₱50", value = count50Str, onValueChange = { count50Str = it }, testTag = "count_50_input", modifier = Modifier.weight(1f))
                            DenomField(label = "₱20", value = count20Str, onValueChange = { count20Str = it }, testTag = "count_20_input", modifier = Modifier.weight(1f))
                        }

                        // Coins Grid (₱10, ₱5, ₱1, ₱0.25)
                        Text("Philippine Coins Breakdown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DenomField(label = "₱10 Coins", value = count10Str, onValueChange = { count10Str = it }, testTag = "count_10_input", modifier = Modifier.weight(1f))
                            DenomField(label = "₱5 Coins", value = count5Str, onValueChange = { count5Str = it }, testTag = "count_5_input", modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DenomField(label = "₱1 Coins", value = count1Str, onValueChange = { count1Str = it }, testTag = "count_1_input", modifier = Modifier.weight(1f))
                            DenomField(label = "25¢ (₱0.25)", value = count25cStr, onValueChange = { count25cStr = it }, testTag = "count_25c_input", modifier = Modifier.weight(1f))
                        }

                        // Loose Change / Centavos
                        HeroInput(
                            value = countOtherCoinsStr,
                            onValueChange = { countOtherCoinsStr = it },
                            label = { Text("Other Loose Coins & Centavos ($curr)", style = MaterialTheme.typography.labelSmall) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().testTag("count_coins_input"),
                            singleLine = true
                        )

                        HeroDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // Subtotal summary
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Bills Subtotal:", style = MaterialTheme.typography.bodySmall)
                            Text("$curr${String.format(Locale.US, "%.2f", totalBills)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Coins Subtotal:", style = MaterialTheme.typography.bodySmall)
                            Text("$curr${String.format(Locale.US, "%.2f", totalCoins)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Counted Cash:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("$curr${String.format(Locale.US, "%.2f", totalCountedCash)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Drawer Balance Formula Card (Starting Float + Cash Sales + Cash In - Cash Out - Refunds)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("drawer_formula_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Drawer Balance Formula Breakdown",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "(Starting Float + Cash Sales + Cash In - Cash Out - Refunds)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        HeroDivider(modifier = Modifier.padding(vertical = 2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Starting Float:", style = MaterialTheme.typography.bodySmall)
                            Text("$curr${String.format(Locale.US, "%.2f", startingFloat)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("(+) Cash Sales:", style = MaterialTheme.typography.bodySmall)
                            Text("+$curr${String.format(Locale.US, "%.2f", cashSales)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF10B981))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("(+) Cash In (Additions):", style = MaterialTheme.typography.bodySmall)
                            Text("+$curr${String.format(Locale.US, "%.2f", cashIn)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF10B981))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("(-) Cash Out (Payouts/Expenses):", style = MaterialTheme.typography.bodySmall)
                            Text("-$curr${String.format(Locale.US, "%.2f", cashOut)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("(-) Cash Refunds:", style = MaterialTheme.typography.bodySmall)
                            Text("-$curr${String.format(Locale.US, "%.2f", cashRefunds)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        HeroDivider(modifier = Modifier.padding(vertical = 2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("(=) Expected Cash Drawer:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("$curr${String.format(Locale.US, "%.2f", expectedCash)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 2) GCash Digital Wallet Reconciliation
                if (storeConfig?.hasGCash == true) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("2. GCash Digital Wallet", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                Surface(
                                    shape = CircleShape,
                                    color = if (hasGcashDiscrepancy) MaterialTheme.colorScheme.errorContainer else Color(0xFF10B981).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (hasGcashDiscrepancy) "VAR: $curr${String.format(Locale.US, "%.2f", gcashVariance)}" else "MATCH",
                                        color = if (hasGcashDiscrepancy) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF10B981),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // GCash Formula Card
                            Text(
                                "Formula: (Starting Balance + Cash In Inflow - Cash Out Disbursed)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Starting Balance: $curr${String.format(Locale.US, "%.2f", gcashStartingBalance)}", style = MaterialTheme.typography.bodySmall)
                                Text("Inflow: +$curr${String.format(Locale.US, "%.2f", gcashInflow)}", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Disbursed: -$curr${String.format(Locale.US, "%.2f", gcashDisbursed)}", style = MaterialTheme.typography.bodySmall)
                                Text("Expected Ledger: $curr${String.format(Locale.US, "%.2f", expectedGCash)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }

                            HeroInput(
                                value = gcashCountStr,
                                onValueChange = { gcashCountStr = it },
                                label = { Text("Physical GCash App Ending Balance ($curr)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth().testTag("count_gcash_input"),
                                singleLine = true
                            )
                        }
                    }
                }

                // 3) Prepaid Mobile Load Reconciliation
                if (storeConfig?.hasLoad == true) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("3. Prepaid Load SIM Ending Balances", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Text("Beginning vs. ending SIM balance evaluated against mobile reload sales.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    HeroInput(
                                        value = smartLoadCountStr,
                                        onValueChange = { smartLoadCountStr = it },
                                        label = { Text("Smart/TNT SIM") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.fillMaxWidth().testTag("count_smart_input"),
                                        singleLine = true
                                    )
                                    Text("Start: ${String.format(Locale.US, "%.0f", smartStartingBalance)} | Sales: ${String.format(Locale.US, "%.0f", smartReloadSales)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text("Expected: ${String.format(Locale.US, "%.0f", expectedSmartLoad)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    HeroInput(
                                        value = globeLoadCountStr,
                                        onValueChange = { globeLoadCountStr = it },
                                        label = { Text("Globe/TM SIM") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.fillMaxWidth().testTag("count_globe_input"),
                                        singleLine = true
                                    )
                                    Text("Start: ${String.format(Locale.US, "%.0f", globeStartingBalance)} | Sales: ${String.format(Locale.US, "%.0f", globeReloadSales)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text("Expected: ${String.format(Locale.US, "%.0f", expectedGlobeLoad)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // 4) Discrepancy & Manager Override Guard
                if (hasAnyDiscrepancy) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("discrepancy_override_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text("Reconciliation Variance Detected!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                            Text(
                                text = "A variance exists between counted totals and system ledgers. A supervisor/manager PIN and mandatory explanatory notes are required to close this shift.",
                                style = MaterialTheme.typography.bodySmall
                            )

                            HeroInput(
                                value = discrepancyNote,
                                onValueChange = { discrepancyNote = it },
                                label = { Text("Mandatory Discrepancy Reason *") },
                                modifier = Modifier.fillMaxWidth().testTag("discrepancy_reason_input"),
                                maxLines = 3,
                                singleLine = false
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                HeroInput(
                                    value = managerUsername,
                                    onValueChange = { managerUsername = it; overrideError = null },
                                    label = { Text("Manager User") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                HeroInput(
                                    value = managerPin,
                                    onValueChange = { managerPin = it; overrideError = null },
                                    label = { Text("PIN") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            overrideError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }

                            if (!managerOverrideVerified) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HeroButton(
                                        onClick = {
                                            val mgr = allUsers.firstOrNull {
                                                it.username.equals(managerUsername.trim(), ignoreCase = true) &&
                                                        it.role.equals("ADMIN", ignoreCase = true)
                                            }
                                            if (mgr != null && SecurityHelper.verifyPin(managerPin, mgr.pinHash).isMatch) {
                                                if (discrepancyNote.trim().length >= 4) {
                                                    managerOverrideVerified = true
                                                    overrideError = null
                                                } else {
                                                    overrideError = "Please explain the reason for this variance (min 4 chars)."
                                                }
                                            } else {
                                                overrideError = "Invalid manager credentials. Only ADMIN accounts can override."
                                            }
                                        },
                                        variant = HeroButtonVariant.Bordered,
                                        color = HeroColor.Default,
                                        text = "Verify PIN",
                                        icon = Icons.Default.VerifiedUser,
                                        modifier = Modifier.weight(1f).testTag("manager_override_btn")
                                    )

                                    val isBioAvail = remember(context) { BiometricAuthHelper.checkAvailability(context).isAvailable }
                                    if (isBioAvail) {
                                        HeroButton(
                                            onClick = {
                                                if (discrepancyNote.trim().length < 4) {
                                                    overrideError = "Please explain the reason for this variance (min 4 chars)."
                                                    return@HeroButton
                                                }
                                                val fragmentActivity = context.findActivity() as? FragmentActivity
                                                if (fragmentActivity != null) {
                                                    BiometricAuthHelper.authenticate(
                                                        activity = fragmentActivity,
                                                        title = "Supervisor Biometric Override",
                                                        subtitle = "Scan supervisor fingerprint to authorize shift discrepancy",
                                                        onOutcome = { outcome, msg ->
                                                            if (outcome != BiometricAuthHelper.BiometricOutcome.SUCCESS) {
                                                                overrideError = msg
                                                            }
                                                        },
                                                        onSuccess = {
                                                            val adminUser = allUsers.firstOrNull { it.role.equals("ADMIN", ignoreCase = true) }
                                                            managerUsername = adminUser?.username ?: "supervisor"
                                                            managerOverrideVerified = true
                                                            overrideError = null
                                                        }
                                                    )
                                                }
                                            },
                                            text = "Biometric",
                                            icon = Icons.Default.Fingerprint,
                                            modifier = Modifier.weight(1f).testTag("biometric_supervisor_override_btn")
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                        Text("Manager Override Approved by @${managerUsername.trim()}", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                // Receipt Printing Button if already reconciled or available
                savedReconciliation?.let { recon: ShiftReconciliation ->
                    HeroButton(
                        onClick = {
                            viewModel.printShiftReconciliationReceipt(recon, session.startTime, formulaSummary) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        text = "Print Reconciliation Receipt",
                        icon = Icons.Default.Print,
                        variant = HeroButtonVariant.Bordered,
                        color = HeroColor.Default,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            val canClose = allMandatoryCountsEntered && (!hasAnyDiscrepancy || managerOverrideVerified)
            HeroButton(
                onClick = {
                    isSubmitting = true
                    viewModel.reconcileShiftAndClose(
                        count1000 = c1000,
                        count500 = c500,
                        count200 = c200,
                        count100 = c100,
                        count50 = c50,
                        count20 = c20,
                        countCoins = totalCoins,
                        totalCountedCash = totalCountedCash,
                        expectedCash = expectedCash,
                        countedGCash = countedGCash,
                        expectedGCash = expectedGCash,
                        countedSmartLoad = countedSmart,
                        expectedSmartLoad = expectedSmartLoad,
                        countedGlobeLoad = countedGlobe,
                        expectedGlobeLoad = expectedGlobeLoad,
                        managerOverrideApproved = managerOverrideVerified,
                        managerUsername = if (managerOverrideVerified) managerUsername.trim() else "",
                        discrepancyReason = discrepancyNote.trim()
                    ) { success, msg, recon ->
                        isSubmitting = false
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (success && recon != null) {
                            savedReconciliation = recon
                            // Automatically trigger thermal print slip
                            viewModel.printShiftReconciliationReceipt(recon, session.startTime, formulaSummary) { _, _ -> }
                            onShiftClosed(recon)
                        }
                    }
                },
                text = if (isSubmitting) "Reconciling..." else "Confirm & Close Shift",
                icon = Icons.Default.Lock,
                enabled = canClose && !isSubmitting,
                color = HeroColor.Danger,
                modifier = Modifier.testTag("reconciliation_confirm_btn")
            )
        },
        dismissButton = {
            HeroTextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DenomField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String? = null,
    modifier: Modifier = Modifier
) {
    val fieldModifier = if (testTag != null) modifier.testTag(testTag) else modifier
    HeroInput(
        value = value,
        onValueChange = { str ->
            if (str.all { it.isDigit() }) onValueChange(str)
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = fieldModifier,
        singleLine = true
    )
}
