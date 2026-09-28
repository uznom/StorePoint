package com.munzo.storepoint.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.StorePointRepository.WalletType
import com.munzo.storepoint.data.WalletLedgerEntry
import com.munzo.storepoint.ui.StorePointViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Admin-only wallet reload and history.
 *
 * Owners fund their GCash / Smart-TNT / Globe-TM floats outside the app. This card is
 * where they tell StorePoint "I loaded P5,000 into Smart", so the float the POS sells
 * against is backed by real money.
 *
 * Every movement is written to the `wallet_ledger` table, so the balance is always
 * reconstructible and a shift-close discrepancy is a real signal rather than a
 * self-reported number. See [StorePointViewModel.reloadWallet].
 *
 * Reloading is admin-gated in the ViewModel, not just hidden here: a cashier topping
 * up their own float is a straightforward shrinkage vector, so the check fails closed
 * regardless of what the UI does.
 */
@Composable
fun WalletReloadCard(
    viewModel: StorePointViewModel,
    isAdmin: Boolean,
    curr: String,
    modifier: Modifier = Modifier
) {
    var selectedWallet by remember { mutableStateOf(WalletType.SMART) }
    var showReloadDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    var amountInput by remember { mutableStateOf("") }
    var referenceInput by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val walletOptions = listOf(
        "GCash" to WalletType.GCASH,
        "Smart / TNT" to WalletType.SMART,
        "Globe / TM" to WalletType.GLOBE
    )
    val currentLabel = walletOptions.first { it.second == selectedWallet }.first

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Reload Wallet Float",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Record money you loaded into your own e-wallet accounts.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (!isAdmin) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Admin access required. Only an owner or supervisor can reload a wallet float.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Text(
                    text = "Select the wallet you funded:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    walletOptions.forEach { (label, key) ->
                        FilterChip(
                            selected = selectedWallet == key,
                            onClick = { selectedWallet = key },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                Button(
                    onClick = {
                        amountInput = ""
                        referenceInput = ""
                        dialogError = ""
                        showReloadDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reload $currentLabel")
                }

                TextButton(
                    onClick = { showHistoryDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("View $currentLabel ledger history", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    if (showReloadDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showReloadDialog = false },
            title = { Text("Reload $currentLabel", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter the amount you loaded into your own $currentLabel account. " +
                            "This increases the float StorePoint sells from and is recorded in the wallet ledger.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { input ->
                            amountInput = input.filter { it.isDigit() || it == '.' }
                            dialogError = ""
                        },
                        label = { Text("Amount ($curr)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = dialogError.isNotEmpty(),
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = referenceInput,
                        onValueChange = { referenceInput = it },
                        label = { Text("Reference (optional)") },
                        placeholder = { Text("e.g. GCash ref no.") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (dialogError.isNotEmpty()) {
                        Text(
                            dialogError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSaving && amountInput.isNotBlank(),
                    onClick = {
                        val amount = amountInput.toDoubleOrNull()
                        if (amount == null || amount <= 0.0) {
                            dialogError = "Enter an amount greater than zero."
                        } else {
                            isSaving = true
                            viewModel.reloadWallet(
                                walletLabel = currentLabel,
                                amount = amount,
                                reference = referenceInput,
                                onSuccess = {
                                    isSaving = false
                                    showReloadDialog = false
                                },
                                onFailure = { err ->
                                    isSaving = false
                                    dialogError = err
                                }
                            )
                        }
                    }
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Confirm Reload")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showReloadDialog = false }, enabled = !isSaving) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showHistoryDialog) {
        WalletHistoryDialog(
            viewModel = viewModel,
            walletLabel = currentLabel,
            curr = curr,
            onDismiss = { showHistoryDialog = false }
        )
    }
}

/**
 * Shows the recorded movements for one wallet, newest first, plus the
 * system-derived balance that shift close reconciles against.
 */
@Composable
private fun WalletHistoryDialog(
    viewModel: StorePointViewModel,
    walletLabel: String,
    curr: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<WalletLedgerEntry>?>(null) }
    var derivedBalance by remember { mutableStateOf<Double?>(null) }

    // Load once per wallet when the dialog opens.
    remember(walletLabel) {
        scope.launch {
            entries = viewModel.getRecentWalletHistory(walletLabel, 50)
            derivedBalance = viewModel.getSystemDerivedWalletBalance(walletLabel)
        }
        true
    }

    val formatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$walletLabel History", fontWeight = FontWeight.Bold) },
        text = {
            val list = entries
            when {
                list == null -> Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                list.isEmpty() -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No movements recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                else -> Column {
                    derivedBalance?.let {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "System-derived balance",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "$curr${String.format(Locale.getDefault(), "%.2f", it)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(list, key = { it.id }) { entry ->
                            WalletLedgerRow(entry, curr, formatter)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/** One ledger line: what moved, who moved it, and the running balance after. */
@Composable
private fun WalletLedgerRow(
    entry: WalletLedgerEntry,
    curr: String,
    formatter: SimpleDateFormat
) {
    val isCredit = entry.delta >= 0.0
    val tint = if (isCredit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.type,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = formatter.format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = buildString {
                    append(entry.actorUsername)
                    if (entry.reference.isNotBlank()) append(" - ${entry.reference}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.notes.isNotBlank()) {
                Text(
                    text = entry.notes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (isCredit) "+" else "") +
                    "$curr${String.format(Locale.getDefault(), "%.2f", entry.delta)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tint
            )
            Text(
                text = "bal $curr${String.format(Locale.getDefault(), "%.2f", entry.balanceAfter)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
