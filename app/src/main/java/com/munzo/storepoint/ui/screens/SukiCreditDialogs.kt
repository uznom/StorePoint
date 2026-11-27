package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.munzo.storepoint.data.CustomerAccount
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroButtonVariant
import com.munzo.storepoint.ui.components.HeroCard
import com.munzo.storepoint.ui.components.HeroChip
import com.munzo.storepoint.ui.components.HeroChipVariant
import com.munzo.storepoint.ui.components.HeroColor
import com.munzo.storepoint.ui.components.HeroDivider
import com.munzo.storepoint.ui.components.HeroFilterChip
import com.munzo.storepoint.ui.components.HeroInput
import com.munzo.storepoint.ui.components.HeroSize
import com.munzo.storepoint.ui.components.HeroTextButton
import com.munzo.storepoint.ui.layout.LocalAdaptiveMetrics
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.glassPanel
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cashier Drawer Suki Tab (Offline Customer Credit / Utang) quick management dialog:
 * lookup balances, add a new Suki account, accept Cash/GCash/Maya repayments, and inspect ledger history.
 */
@Composable
fun CashierSukiTabDialog(
    viewModel: StorePointViewModel,
    curr: String,
    onDismiss: () -> Unit
) {
    val metrics = LocalAdaptiveMetrics.current
    val customers by viewModel.allActiveCustomers.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAddCustomer by remember { mutableStateOf(false) }
    var repayTarget by remember { mutableStateOf<CustomerAccount?>(null) }
    var historyTarget by remember { mutableStateOf<CustomerAccount?>(null) }

    val filtered = remember(customers, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) customers
        else customers.filter {
            it.name.contains(q, ignoreCase = true) || it.phone.contains(q, ignoreCase = true)
        }
    }
    val totalOutstanding = remember(customers) { customers.sumOf { it.currentBalance } }

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
                    .padding(metrics.cardPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Suki Tab (Customer Credit)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Total Unpaid Balance: $curr${String.format(Locale.getDefault(), "%.2f", totalOutstanding)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroInput(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search Suki name or phone...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    HeroButton(
                        onClick = { showAddCustomer = true },
                        text = "New",
                        icon = Icons.Default.PersonAdd,
                        color = HeroColor.Primary,
                        variant = HeroButtonVariant.Solid,
                        size = HeroSize.Md,
                        modifier = Modifier.testTag("suki_dialog_add_btn")
                    )
                }

                if (filtered.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (customers.isEmpty()) "No Suki accounts yet. Tap 'New' to add a customer credit account."
                            else "No Suki customer matches \"$searchQuery\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { customer ->
                            SukiCustomerCompactRow(
                                customer = customer,
                                curr = curr,
                                onRepay = { repayTarget = customer },
                                onHistory = { historyTarget = customer }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddCustomer) {
        SukiCustomerFormDialog(
            editingCustomer = null,
            curr = curr,
            onSave = { account ->
                viewModel.saveCustomerAccount(account) { ok, _, _ ->
                    if (ok) showAddCustomer = false
                }
            },
            onDismiss = { showAddCustomer = false }
        )
    }

    repayTarget?.let { target ->
        SukiRepaymentDialog(
            customer = target,
            curr = curr,
            viewModel = viewModel,
            onDismiss = { repayTarget = null }
        )
    }

    historyTarget?.let { target ->
        SukiLedgerHistoryDialog(
            customer = target,
            curr = curr,
            viewModel = viewModel,
            onDismiss = { historyTarget = null }
        )
    }
}

@Composable
private fun SukiCustomerCompactRow(
    customer: CustomerAccount,
    curr: String,
    onRepay: () -> Unit,
    onHistory: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${customer.phone.ifBlank { "No phone" }} • Limit $curr${String.format(Locale.getDefault(), "%.2f", customer.creditLimit)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Balance: $curr${String.format(Locale.getDefault(), "%.2f", customer.currentBalance)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (customer.currentBalance > 0.01) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onHistory) {
                    Icon(Icons.Default.History, contentDescription = "Ledger History")
                }
                HeroButton(
                    onClick = onRepay,
                    text = "Pay",
                    icon = Icons.Default.Payments,
                    color = HeroColor.Primary,
                    variant = HeroButtonVariant.Solid,
                    size = HeroSize.Sm,
                    enabled = customer.currentBalance > 0.01
                )
            }
        }
    }
}

@Composable
fun SukiCustomerFormDialog(
    editingCustomer: CustomerAccount?,
    curr: String,
    onSave: (CustomerAccount) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(editingCustomer) { mutableStateOf(editingCustomer?.name ?: "") }
    var phone by remember(editingCustomer) { mutableStateOf(editingCustomer?.phone ?: "") }
    var address by remember(editingCustomer) { mutableStateOf(editingCustomer?.address ?: "") }
    var creditLimit by remember(editingCustomer) {
        mutableStateOf(String.format(Locale.US, "%.2f", editingCustomer?.creditLimit ?: 1000.0))
    }
    var notes by remember(editingCustomer) { mutableStateOf(editingCustomer?.notes ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingCustomer == null) "New Suki Credit Account" else "Edit Suki Account", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroInput(
                    value = name,
                    onValueChange = { name = it; errorMsg = null },
                    label = { Text("Customer Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                HeroInput(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (Optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                HeroInput(
                    value = creditLimit,
                    onValueChange = { creditLimit = it; errorMsg = null },
                    label = { Text("Credit Limit ($curr)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                HeroInput(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address / Purok (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                HeroInput(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            HeroButton(
                onClick = {
                    val trimmedName = name.trim()
                    val parsedLimit = creditLimit.replace(',', '.').toDoubleOrNull()
                    if (trimmedName.isBlank()) {
                        errorMsg = "Customer name is required."
                        return@HeroButton
                    }
                    if (parsedLimit == null || parsedLimit < 0.0) {
                        errorMsg = "Enter a valid credit limit (0 or greater)."
                        return@HeroButton
                    }
                    val account = (editingCustomer ?: CustomerAccount(name = trimmedName)).copy(
                        name = trimmedName,
                        phone = phone.trim(),
                        address = address.trim(),
                        creditLimit = parsedLimit,
                        notes = notes.trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(account)
                },
                text = "Save Account",
                color = HeroColor.Primary,
                variant = HeroButtonVariant.Solid
            )
        },
        dismissButton = {
            HeroTextButton(onClick = onDismiss, text = "Cancel")
        }
    )
}

@Composable
fun SukiRepaymentDialog(
    customer: CustomerAccount,
    curr: String,
    viewModel: StorePointViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tactile = rememberTactileFeedback()
    var amountStr by remember(customer) {
        mutableStateOf(String.format(Locale.US, "%.2f", customer.currentBalance))
    }
    var method by remember { mutableStateOf("CASH") }
    var notes by remember { mutableStateOf("") }
    var printSlip by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val parsedAmount = amountStr.replace(',', '.').toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Suki Payment", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(customer.name, fontWeight = FontWeight.Bold)
                        Text(
                            "Current Unpaid Balance: $curr${String.format(Locale.getDefault(), "%.2f", customer.currentBalance)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeroChip(
                        text = "Full ($curr${String.format(Locale.US, "%.2f", customer.currentBalance)})",
                        variant = HeroChipVariant.Flat,
                        color = HeroColor.Primary,
                        onClick = { amountStr = String.format(Locale.US, "%.2f", customer.currentBalance) }
                    )
                    if (customer.currentBalance >= 100.0) {
                        HeroChip(
                            text = "Half",
                            variant = HeroChipVariant.Flat,
                            onClick = { amountStr = String.format(Locale.US, "%.2f", customer.currentBalance / 2.0) }
                        )
                    }
                }

                HeroInput(
                    value = amountStr,
                    onValueChange = { amountStr = it; errorMsg = null },
                    label = { Text("Repayment Amount ($curr)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CASH", "GCASH", "MAYA").forEach { m ->
                        HeroFilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = m
                        )
                    }
                }

                if (method == "CASH") {
                    Text(
                        "Cash repayments automatically log a Cash-In entry to the active shift drawer.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HeroInput(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Reference / Notes (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Print Thermal Repayment Slip", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = printSlip, onCheckedChange = { printSlip = it })
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            HeroButton(
                onClick = {
                    if (isSubmitting) return@HeroButton
                    if (parsedAmount <= 0.0 || parsedAmount > customer.currentBalance + 0.01) {
                        errorMsg = "Enter an amount between 0.01 and ${String.format(Locale.US, "%.2f", customer.currentBalance)}."
                        tactile.perform(TactileHapticRole.WarningReject)
                        return@HeroButton
                    }
                    isSubmitting = true
                    viewModel.recordCustomerPayment(
                        customerId = customer.id,
                        amount = parsedAmount,
                        paymentMethod = method,
                        notes = notes.trim(),
                        printSlip = printSlip
                    ) { ok, msg, _ ->
                        isSubmitting = false
                        if (ok) {
                            tactile.perform(TactileHapticRole.ConfirmSuccess)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            errorMsg = msg
                            tactile.perform(TactileHapticRole.WarningReject)
                        }
                    }
                },
                enabled = !isSubmitting,
                text = if (isSubmitting) "Saving..." else "Confirm Payment",
                color = HeroColor.Primary,
                variant = HeroButtonVariant.Solid
            )
        },
        dismissButton = {
            HeroTextButton(onClick = onDismiss, text = "Cancel")
        }
    )
}

@Composable
fun SukiLedgerHistoryDialog(
    customer: CustomerAccount,
    curr: String,
    viewModel: StorePointViewModel,
    onDismiss: () -> Unit
) {
    val entries by remember(customer.id) {
        viewModel.getCustomerCreditLedger(customer.id)
    }.collectAsState(initial = emptyList())
    val dateFmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("${customer.name} — Credit Ledger", fontWeight = FontWeight.Bold)
                Text(
                    "Balance: $curr${String.format(Locale.getDefault(), "%.2f", customer.currentBalance)} / Limit $curr${String.format(Locale.getDefault(), "%.2f", customer.creditLimit)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            if (entries.isEmpty()) {
                Text("No credit charges or payments recorded yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        val isCharge = entry.type.equals("CHARGE", ignoreCase = true)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isCharge) "UTANG CHARGE" else "PAYMENT (${entry.paymentMethod})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCharge) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${if (isCharge) "+" else "-"}$curr${String.format(Locale.getDefault(), "%.2f", entry.amount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isCharge) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${dateFmt.format(Date(entry.timestamp))} • ${entry.actorUsername}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Bal: $curr${String.format(Locale.getDefault(), "%.2f", entry.balanceAfter)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (entry.notes.isNotBlank()) {
                                Text(
                                    text = entry.notes,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            HeroDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            HeroTextButton(onClick = onDismiss, text = "Close")
        }
    )
}
