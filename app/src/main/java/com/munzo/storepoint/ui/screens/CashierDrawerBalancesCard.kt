package com.munzo.storepoint.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.CashierSession
import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroInput
import com.munzo.storepoint.ui.components.HeroSize
import com.munzo.storepoint.ui.components.HeroTextButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun CashierDrawerBalancesCard(
    session: CashierSession,
    storeConfig: StoreConfig?,
    curr: String,
    isSensitiveDataVisible: Boolean,
    viewModel: StorePointViewModel,
    onCashInOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Shift Started", style = MaterialTheme.typography.bodySmall)
            val startTimeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.startTime))
            Text(
                text = if (isSensitiveDataVisible) startTimeFormatted else "••:••",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Starting cash drawer", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = if (isSensitiveDataVisible) "$curr${String.format(Locale.getDefault(), "%.2f", session.startingCash)}" else "$curr••••",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            HeroTextButton(
                onClick = onCashInOut,
                modifier = Modifier.testTag("quick_cash_in_out_btn"),
                icon = Icons.Default.PointOfSale,
                size = HeroSize.Sm,
                text = "Cash In/Out"
            )
        }

        if (storeConfig?.hasGCash == true) {
            WalletBalanceRowWithAdjust(
                title = "GCash Balance",
                dialogTitle = "Update GCash Balance",
                dialogBody = "Enter the accurate secondary GCash digital balance of this outlet's retailing account:",
                fieldLabel = "GCash Balance ($curr)",
                buttonTag = "drawer_adjust_gcash_btn",
                fieldTag = "temp_gcash_balance_field",
                balance = storeConfig.gcashBalance,
                curr = curr,
                isSensitiveDataVisible = isSensitiveDataVisible,
                valueColor = MaterialTheme.colorScheme.tertiary,
                onSave = { viewModel.updateGCashBalance(it) }
            )
        }

        if (storeConfig?.hasLoad == true) {
            WalletBalanceRowWithAdjust(
                title = "Smart/TNT Load Wallet",
                dialogTitle = "Update Smart/TNT Load Wallet",
                dialogBody = "Enter current retailer load wallet balance for Smart/TNT (2% rebate active):",
                fieldLabel = "Smart Load Balance ($curr)",
                buttonTag = "drawer_adjust_smart_btn",
                fieldTag = "temp_smart_balance_field",
                balance = storeConfig.smartLoadBalance,
                curr = curr,
                isSensitiveDataVisible = isSensitiveDataVisible,
                valueColor = MaterialTheme.colorScheme.primary,
                onSave = { viewModel.updateSmartLoadBalance(it) }
            )

            WalletBalanceRowWithAdjust(
                title = "Globe/TM Load Wallet",
                dialogTitle = "Update Globe/TM Load Wallet",
                dialogBody = "Enter current retailer load wallet balance for Globe/TM (2% rebate active):",
                fieldLabel = "Globe Load Balance ($curr)",
                buttonTag = "drawer_adjust_globe_btn",
                fieldTag = "temp_globe_balance_field",
                balance = storeConfig.globeLoadBalance,
                curr = curr,
                isSensitiveDataVisible = isSensitiveDataVisible,
                valueColor = MaterialTheme.colorScheme.secondary,
                onSave = { viewModel.updateGlobeLoadBalance(it) }
            )
        }
    }
}

@Composable
private fun WalletBalanceRowWithAdjust(
    title: String,
    dialogTitle: String,
    dialogBody: String,
    fieldLabel: String,
    buttonTag: String,
    fieldTag: String,
    balance: Double,
    curr: String,
    isSensitiveDataVisible: Boolean,
    valueColor: Color,
    onSave: (Double) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text(
                text = if (isSensitiveDataVisible) "$curr${String.format(Locale.getDefault(), "%.2f", balance)}" else "$curr••••",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }

        HeroTextButton(
            onClick = { showDialog = true },
            modifier = Modifier.testTag(buttonTag),
            icon = Icons.Default.Edit,
            size = HeroSize.Sm,
            text = "Adjust"
        )
    }

    if (showDialog) {
        var tempBalStr by remember(balance) { mutableStateOf(balance.toString()) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(dialogTitle) },
            text = {
                Column(
                    modifier = Modifier.imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(dialogBody, style = MaterialTheme.typography.bodyMedium)
                    HeroInput(
                        value = tempBalStr,
                        onValueChange = { tempBalStr = it },
                        label = { Text(fieldLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(fieldTag)
                    )
                }
            },
            confirmButton = {
                HeroButton(
                    onClick = {
                        onSave(tempBalStr.toDoubleOrNull() ?: balance)
                        showDialog = false
                    },
                    text = "Save"
                )
            },
            dismissButton = {
                HeroTextButton(onClick = { showDialog = false }, text = "Cancel")
            }
        )
    }
}
