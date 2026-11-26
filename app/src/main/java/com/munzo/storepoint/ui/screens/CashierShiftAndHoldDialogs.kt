package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.CashierSession
import com.munzo.storepoint.data.ParkedTransaction
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.data.StorePointRepository
import com.munzo.storepoint.data.UomOption
import com.munzo.storepoint.data.User
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.ExpressiveButton
import com.munzo.storepoint.ui.components.ExpressiveButtonSize
import com.munzo.storepoint.ui.components.ExpressiveButtonVariant
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroButtonVariant
import com.munzo.storepoint.ui.components.HeroCard
import com.munzo.storepoint.ui.components.HeroColor
import com.munzo.storepoint.ui.components.HeroDivider
import com.munzo.storepoint.ui.components.HeroInput
import com.munzo.storepoint.ui.components.HeroSize
import com.munzo.storepoint.ui.components.HeroTextButton
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.glassPanel
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.ui.theme.tactileBounce
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun CashierShiftStartDialog(
    storeConfig: StoreConfig?,
    curr: String,
    initialCashInput: String,
    onInitialCashChange: (String) -> Unit,
    initialGCashInput: String,
    onInitialGCashChange: (String) -> Unit,
    initialSmartLoadInput: String,
    onInitialSmartLoadChange: (String) -> Unit,
    initialGlobeLoadInput: String,
    onInitialGlobeLoadChange: (String) -> Unit,
    onConfirmStart: () -> Unit
) {
    val tactile = rememberTactileFeedback()

    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            HeroButton(
                onClick = {
                    tactile.perform(TactileHapticRole.ConfirmSuccess)
                    onConfirmStart()
                },
                modifier = Modifier.testTag("shift_start_confirm"),
                text = "Register Drawer Start",
                color = HeroColor.Primary,
                variant = HeroButtonVariant.Solid,
                size = HeroSize.Md
            )
        },
        title = { Text("Open Shift Drawer Session") },
        text = {
            Column(
                modifier = Modifier
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Welcome cashier! Register the physical starting cash float and the digital wallet balance to start your shift session.")

                HeroInput(
                    value = initialCashInput,
                    onValueChange = onInitialCashChange,
                    label = { Text("Starting Cash float ($curr)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("starting_cash_input")
                )

                if (storeConfig?.hasGCash == true) {
                    HeroInput(
                        value = initialGCashInput,
                        onValueChange = onInitialGCashChange,
                        label = { Text("Starting GCash/Digital Balance ($curr)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("starting_gcash_input")
                    )
                }

                if (storeConfig?.hasLoad == true) {
                    HeroInput(
                        value = initialSmartLoadInput,
                        onValueChange = onInitialSmartLoadChange,
                        label = { Text("Starting Smart/TNT Load Wallet ($curr)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("starting_smart_load_input")
                    )

                    HeroInput(
                        value = initialGlobeLoadInput,
                        onValueChange = onInitialGlobeLoadChange,
                        label = { Text("Starting Globe/TM Load Wallet ($curr)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("starting_globe_load_input")
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "This automatically pre-fills with the ending GCash balance from your previous shift.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}

@Composable
internal fun CashierShiftCloseCoordinator(
    session: CashierSession,
    storeConfig: StoreConfig?,
    viewModel: StorePointViewModel,
    onDismiss: () -> Unit,
    onShiftClosed: () -> Unit
) {
    val transactionsList by viewModel.allTransactions.collectAsState()
    val drawerPayoutsList by viewModel.allDrawerTransactions.collectAsState()
    val allWalletEntries by viewModel.allWalletLedger.collectAsState()

    val sessionTransactions = remember(transactionsList, session) {
        transactionsList.filter { it.timestamp >= session.startTime }
    }
    val startingFloat = session.startingCash
    val cashSalesInSession = remember(sessionTransactions) {
        sessionTransactions.sumOf {
            when {
                it.paymentMethod == "CASH" -> it.totalAmount
                it.paymentMethod.startsWith("SPLIT") -> it.cashPaid
                else -> 0.0
            }
        }
    }
    val sessionDrawerTx = remember(drawerPayoutsList, session) {
        drawerPayoutsList.filter { it.sessionId == session.id }
    }
    val cashIn = remember(sessionDrawerTx) {
        sessionDrawerTx.filter { it.amount > 0 }.sumOf { it.amount }
    }
    val cashOut = remember(sessionDrawerTx) {
        sessionDrawerTx.filter { it.amount < 0 && !it.reason.startsWith("Refund") && it.type != "REFUND" }.sumOf { -it.amount }
    }
    val cashRefunds = remember(sessionDrawerTx) {
        sessionDrawerTx.filter { it.amount < 0 && (it.reason.startsWith("Refund") || it.type == "REFUND") }.sumOf { -it.amount }
    }
    val expectedCash = StorePointRepository.roundMoney(startingFloat + cashSalesInSession + cashIn - cashOut - cashRefunds)

    val sessionGcashEntries = remember(allWalletEntries, session) {
        allWalletEntries.filter { it.walletType == "GCASH" && it.timestamp >= session.startTime }
    }
    val gcashInflow = remember(sessionGcashEntries) { sessionGcashEntries.filter { it.delta > 0 }.sumOf { it.delta } }
    val gcashDisbursed = remember(sessionGcashEntries) { sessionGcashEntries.filter { it.delta < 0 }.sumOf { -it.delta } }
    val expectedGCash = storeConfig?.gcashBalance ?: 0.0
    val gcashStartingBalance = StorePointRepository.roundMoney(expectedGCash - gcashInflow + gcashDisbursed)

    val sessionSmartEntries = remember(allWalletEntries, session) {
        allWalletEntries.filter { it.walletType == "SMART" && it.timestamp >= session.startTime }
    }
    val smartReloadSales = remember(sessionSmartEntries) { sessionSmartEntries.filter { it.delta < 0 }.sumOf { -it.delta } }
    val expectedSmartLoad = storeConfig?.smartLoadBalance ?: 0.0
    val smartStartingBalance = StorePointRepository.roundMoney(expectedSmartLoad + smartReloadSales)

    val sessionGlobeEntries = remember(allWalletEntries, session) {
        allWalletEntries.filter { it.walletType == "GLOBE" && it.timestamp >= session.startTime }
    }
    val globeReloadSales = remember(sessionGlobeEntries) { sessionGlobeEntries.filter { it.delta < 0 }.sumOf { -it.delta } }
    val expectedGlobeLoad = storeConfig?.globeLoadBalance ?: 0.0
    val globeStartingBalance = StorePointRepository.roundMoney(expectedGlobeLoad + globeReloadSales)

    ShiftReconciliationDialog(
        session = session,
        viewModel = viewModel,
        expectedCash = expectedCash,
        startingFloat = startingFloat,
        cashSales = cashSalesInSession,
        cashIn = cashIn,
        cashOut = cashOut,
        cashRefunds = cashRefunds,
        expectedGCash = expectedGCash,
        gcashStartingBalance = gcashStartingBalance,
        gcashInflow = gcashInflow,
        gcashDisbursed = gcashDisbursed,
        expectedSmartLoad = expectedSmartLoad,
        smartStartingBalance = smartStartingBalance,
        smartReloadSales = smartReloadSales,
        expectedGlobeLoad = expectedGlobeLoad,
        globeStartingBalance = globeStartingBalance,
        globeReloadSales = globeReloadSales,
        onDismiss = onDismiss,
        onShiftClosed = { _ -> onShiftClosed() }
    )
}

@Composable
internal fun ActiveShiftLogoutWarningDialog(
    activeSession: CashierSession?,
    activeUser: User?,
    onReconcileNow: () -> Unit,
    onAdminForceLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Active Shift Handover Required", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "You have an active open cashier shift (#SES-${activeSession?.id}). Store policy requires physical drawer balancing and reconciliation before logging out.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Please reconcile your shift now or cancel to return to POS operations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            HeroButton(
                onClick = onReconcileNow,
                modifier = Modifier.testTag("logout_warning_reconcile_btn"),
                text = "Reconcile & Close Shift"
            )
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (activeUser?.role.equals("ADMIN", ignoreCase = true)) {
                    HeroTextButton(
                        onClick = onAdminForceLogout,
                        color = HeroColor.Danger,
                        text = "Admin Force Logout"
                    )
                }
                HeroTextButton(onClick = onDismiss, text = "Cancel")
            }
        }
    )
}

@Composable
internal fun ParkSaleDialog(
    parkNoteInput: String,
    onParkNoteChange: (String) -> Unit,
    onConfirmPark: () -> Unit,
    onDismiss: () -> Unit
) {
    val tactile = rememberTactileFeedback()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            ExpressiveButton(
                onClick = {
                    if (parkNoteInput.isNotBlank()) {
                        tactile.perform(TactileHapticRole.ConfirmSuccess)
                        onConfirmPark()
                    }
                },
                modifier = Modifier.testTag("park_submit_button"),
                label = "Park Sale",
                variant = ExpressiveButtonVariant.FILLED,
                size = ExpressiveButtonSize.M
            )
        },
        dismissButton = {
            HeroTextButton(onClick = onDismiss, text = "Browse")
        },
        title = { Text("Park Transaction (Hold)") },
        text = {
            Column(
                modifier = Modifier.imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Hold catalog cart temporarily. You can retrieve it instantly later using the held sidebar panels.")
                HeroInput(
                    value = parkNoteInput,
                    onValueChange = onParkNoteChange,
                    label = { Text("Reference text / Customer name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("park_note_input")
                )
            }
        }
    )
}

@Composable
internal fun ResumeParkedTransactionsDialog(
    parkedList: List<ParkedTransaction>,
    onResume: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            HeroTextButton(onClick = onDismiss, text = "Close")
        },
        title = { Text("Parked Held Transactions Archive (${parkedList.size})") },
        text = {
            if (parkedList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No pending sales on hold.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(parkedList, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.note, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Held: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                HeroButton(
                                    onClick = { onResume(item.id) },
                                    size = HeroSize.Sm,
                                    modifier = Modifier.testTag("resume_park_btn_${item.id}"),
                                    text = "Resume"
                                )

                                IconButton(onClick = { onDelete(item.id) }) {
                                    Icon(Icons.Default.Delete, "Delete parked transaction", tint = Color.Red.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
internal fun CartItemRemoveConfirmDialog(
    product: Product,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tactile = rememberTactileFeedback()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Item Removal", fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to remove '${product.name}' from your checkout cart?") },
        confirmButton = {
            ExpressiveButton(
                onClick = {
                    tactile.perform(TactileHapticRole.WarningReject)
                    onConfirm()
                    Toast.makeText(context, "${product.name} removed from cart.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.testTag("single_delete_confirm_btn"),
                label = "Confirm Remove",
                variant = ExpressiveButtonVariant.FILLED,
                size = ExpressiveButtonSize.M,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        },
        dismissButton = {
            HeroTextButton(onClick = onDismiss, text = "Cancel")
        }
    )
}

@Composable
internal fun CartBulkDeleteConfirmDialog(
    checkedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tactile = rememberTactileFeedback()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Bulk Deletion", fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to delete all $checkedCount checked item(s) from the checkout cart? This action cannot be undone.") },
        confirmButton = {
            ExpressiveButton(
                onClick = {
                    tactile.perform(TactileHapticRole.WarningReject)
                    onConfirm()
                    Toast.makeText(context, "Deleted $checkedCount item(s) successfully.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.testTag("bulk_delete_confirm_button"),
                label = "Confirm Bulk Delete",
                variant = ExpressiveButtonVariant.FILLED,
                size = ExpressiveButtonSize.M,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        },
        dismissButton = {
            HeroTextButton(onClick = onDismiss, text = "Cancel")
        }
    )
}

@Composable
internal fun CartLongPressUomDialog(
    product: Product,
    qty: Int,
    activeUom: UomOption,
    uomsList: List<UomOption>,
    curr: String,
    onSelectUom: (UomOption) -> Unit,
    onDismiss: () -> Unit
) {
    val tactile = rememberTactileFeedback()
    val subtotalValue = activeUom.price * qty

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            HeroButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_uom_edit_dialog"),
                text = "Close"
            )
        },
        title = {
            Text(
                text = "Edit Item Measurement & Pricing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Product Name: ${product.name}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text("Current Unit of Measurement: ${activeUom.name}", style = MaterialTheme.typography.bodyMedium)
                Text("Base Retail Price: $curr${String.format(Locale.US, "%.2f", product.price)}", style = MaterialTheme.typography.bodySmall)
                if (product.cost > 0.0) {
                    Text(
                        "Dealer's Cost Price: $curr${String.format(Locale.US, "%.2f", product.cost * activeUom.multiplier)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text("Selected Qty: $qty unit(s)", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Line Subtotal: $curr${String.format(Locale.US, "%.2f", subtotalValue)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                HeroDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Switch Unit of Measurement:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)

                uomsList.forEach { option ->
                    val isOptionSelected = activeUom.name == option.name
                    HeroCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassPanel(cornerRadius = 16.dp)
                            .tactileBounce(pressFeedback = true) {
                                tactile.perform(TactileHapticRole.CartAdjust)
                                onSelectUom(option)
                            }
                            .border(
                                width = if (isOptionSelected) 2.dp else 0.dp,
                                color = if (isOptionSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOptionSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else Color.Transparent
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(option.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Multiplier: ×${option.multiplier}", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(
                                text = "$curr${String.format(Locale.US, "%.2f", option.price)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    )
}
