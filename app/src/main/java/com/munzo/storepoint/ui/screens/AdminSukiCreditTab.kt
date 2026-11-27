package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.CustomerAccount
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroButtonVariant
import com.munzo.storepoint.ui.components.HeroCard
import com.munzo.storepoint.ui.components.HeroColor
import com.munzo.storepoint.ui.components.HeroFilterChip
import com.munzo.storepoint.ui.components.HeroInput
import com.munzo.storepoint.ui.components.HeroSize
import com.munzo.storepoint.ui.components.HeroTextButton
import com.munzo.storepoint.ui.theme.ExpressivePillBadge
import java.util.Locale

@Composable
fun AdminSukiCreditTab(
    viewModel: StorePointViewModel,
    curr: String
) {
    val context = LocalContext.current
    val customers by viewModel.allCustomers.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var withBalanceOnly by remember { mutableStateOf(false) }

    var showFormDialog by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<CustomerAccount?>(null) }
    var repayTarget by remember { mutableStateOf<CustomerAccount?>(null) }
    var historyTarget by remember { mutableStateOf<CustomerAccount?>(null) }
    var deleteTarget by remember { mutableStateOf<CustomerAccount?>(null) }

    val totalReceivables = remember(customers) { customers.sumOf { it.currentBalance } }
    val unpaidAccountsCount = remember(customers) { customers.count { it.currentBalance > 0.01 } }

    val filtered = remember(customers, searchQuery, withBalanceOnly) {
        val q = searchQuery.trim()
        customers.filter { acct ->
            val matchesBal = !withBalanceOnly || acct.currentBalance > 0.01
            val matchesQuery = q.isEmpty() ||
                acct.name.contains(q, ignoreCase = true) ||
                acct.phone.contains(q, ignoreCase = true) ||
                acct.address.contains(q, ignoreCase = true)
            matchesBal && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_suki_credit_tab"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Suki Tab & Customer Credit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Manage offline utang accounts, credit limits, and repayment ledgers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            HeroButton(
                onClick = {
                    editingCustomer = null
                    showFormDialog = true
                },
                text = "New Suki",
                icon = Icons.Default.PersonAdd,
                color = HeroColor.Primary,
                variant = HeroButtonVariant.Solid,
                size = HeroSize.Md,
                modifier = Modifier.testTag("admin_add_suki_btn")
            )
        }

        // KPI Summary Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SukiKpiMiniCard(
                label = "Total Receivables",
                value = "$curr${String.format(Locale.getDefault(), "%.2f", totalReceivables)}",
                accentColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            SukiKpiMiniCard(
                label = "With Balance",
                value = "$unpaidAccountsCount / ${customers.size}",
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        // Search + Filter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroInput(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Suki name, phone, or address...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            HeroFilterChip(
                selected = withBalanceOnly,
                onClick = { withBalanceOnly = !withBalanceOnly },
                label = "Unpaid Only"
            )
        }

        if (filtered.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (customers.isEmpty()) "No Suki accounts created yet. Tap 'New Suki' to start tracking offline credit."
                    else "No Suki accounts match the current search or filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { customer ->
                    val usage = if (customer.creditLimit > 0.0) {
                        (customer.currentBalance / customer.creditLimit).toFloat().coerceIn(0f, 1f)
                    } else if (customer.currentBalance > 0.0) 1f else 0f

                    HeroCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = customer.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = listOfNotNull(
                                            customer.phone.takeIf { it.isNotBlank() },
                                            customer.address.takeIf { it.isNotBlank() }
                                        ).joinToString(" • ").ifBlank { "No contact details" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                ExpressivePillBadge(
                                    text = "$curr${String.format(Locale.getDefault(), "%.2f", customer.currentBalance)}",
                                    containerColor = if (customer.currentBalance > 0.01) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                    contentColor = if (customer.currentBalance > 0.01) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Credit Utilization",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Limit: $curr${String.format(Locale.getDefault(), "%.2f", customer.creditLimit)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { usage },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    color = if (usage >= 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HeroButton(
                                    onClick = { repayTarget = customer },
                                    text = "Record Payment",
                                    icon = Icons.Default.Payments,
                                    color = HeroColor.Primary,
                                    variant = HeroButtonVariant.Solid,
                                    size = HeroSize.Sm,
                                    enabled = customer.currentBalance > 0.01
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedIconButton(
                                        onClick = { historyTarget = customer },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.History, "Ledger", modifier = Modifier.size(16.dp))
                                    }
                                    OutlinedIconButton(
                                        onClick = {
                                            editingCustomer = customer
                                            showFormDialog = true
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                    OutlinedIconButton(
                                        onClick = { deleteTarget = customer },
                                        modifier = Modifier.size(36.dp),
                                        colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFormDialog) {
        SukiCustomerFormDialog(
            editingCustomer = editingCustomer,
            curr = curr,
            onSave = { account ->
                viewModel.saveCustomerAccount(account) { ok, msg, _ ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (ok) showFormDialog = false
                }
            },
            onDismiss = { showFormDialog = false }
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

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Suki Account?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (target.currentBalance > 0.01) {
                        "${target.name} still has an unpaid balance of $curr${String.format(Locale.getDefault(), "%.2f", target.currentBalance)}. Settle the balance before deleting."
                    } else {
                        "Remove ${target.name} and their settled credit ledger permanently?"
                    }
                )
            },
            confirmButton = {
                if (target.currentBalance <= 0.01) {
                    HeroButton(
                        onClick = {
                            viewModel.deleteCustomerAccount(target.id) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                            deleteTarget = null
                        },
                        text = "Delete",
                        color = HeroColor.Danger,
                        variant = HeroButtonVariant.Solid
                    )
                }
            },
            dismissButton = {
                HeroTextButton(onClick = { deleteTarget = null }, text = "Close")
            }
        )
    }
}

@Composable
private fun SukiKpiMiniCard(
    label: String,
    value: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = accentColor, maxLines = 1)
        }
    }
}
