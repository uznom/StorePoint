package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.munzo.storepoint.data.ReturnTransaction
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.data.TransactionItem
import com.munzo.storepoint.ui.StorePointViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnRefundDialog(
    viewModel: StorePointViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val storeConfig by viewModel.storeConfig.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val curr = storeConfig?.currencySymbol ?: "₱"

    var selectedTransactionId by remember { mutableStateOf<Int?>(null) }
    var transactionSearchQuery by remember { mutableStateOf("") }
    var loadedTx by remember { mutableStateOf<Transaction?>(null) }
    var loadedItems by remember { mutableStateOf<List<TransactionItem>>(emptyList()) }
    var isLoadingTx by remember { mutableStateOf(false) }

    // Return selection state: item.id -> (returnQuantity, restock)
    val returnSelections = remember { mutableStateMapOf<Int, Pair<Int, Boolean>>() }
    var returnReason by remember { mutableStateOf("Defective / Spoiled") }
    var refundMethod by remember { mutableStateOf("CASH") } // "CASH", "STORE_CREDIT", "EXCHANGE"
    var customerName by remember { mutableStateOf("") }
    var returnNotes by remember { mutableStateOf("") }
    var completedReturnTx by remember { mutableStateOf<ReturnTransaction?>(null) }

    val recentTransactions = remember(allTransactions, transactionSearchQuery) {
        if (transactionSearchQuery.isBlank()) {
            allTransactions.take(15)
        } else {
            allTransactions.filter {
                it.id.toString().contains(transactionSearchQuery) ||
                it.cashierUsername.contains(transactionSearchQuery, ignoreCase = true) ||
                it.paymentMethod.contains(transactionSearchQuery, ignoreCase = true)
            }.take(15)
        }
    }

    fun loadTransaction(txId: Int) {
        isLoadingTx = true
        selectedTransactionId = txId
        returnSelections.clear()
        viewModel.getTransactionWithItems(txId) { tx, items ->
            isLoadingTx = false
            loadedTx = tx
            loadedItems = items
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 880.dp)
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .imePadding()
                .clip(RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            if (completedReturnTx != null) {
                // Success Return Receipt Screen
                val ret = completedReturnTx!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Return Success",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "Return Processed Successfully",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Return Slip #${ret.id} (Original Tx #${ret.originalTransactionId})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Refunded:")
                                Text(
                                    "$curr${String.format(Locale.getDefault(), "%.2f", ret.totalRefundAmount)}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Refund Tender:")
                                Text(ret.refundMethod, fontWeight = FontWeight.SemiBold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Reason:")
                                Text(ret.returnReason)
                            }
                            if (ret.customerName.isNotBlank()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Customer:")
                                    Text(ret.customerName)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Processed By:")
                                Text(ret.cashierUsername)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                completedReturnTx = null
                                loadedTx = null
                                loadedItems = emptyList()
                                selectedTransactionId = null
                            },
                            shape = CircleShape,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Process Another Return")
                        }
                        Button(
                            onClick = onDismiss,
                            shape = CircleShape,
                            modifier = Modifier.weight(1f).height(48.dp).testTag("return_done_button")
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text("Returns, Refunds & Exchanges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Lookup sale, restock items, and issue refund tender", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                ) { paddingValues ->
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        val isCompact = maxWidth < 650.dp
                        var mobileTab by remember { mutableIntStateOf(if (selectedTransactionId == null) 0 else 1) }

                        // Composable for Transaction Selector (Left side on tablet, Tab 0 on mobile)
                        @Composable
                        fun TransactionSelectorSection(modifier: Modifier = Modifier) {
                            Column(
                                modifier = modifier,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("1. Select Completed Transaction", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                
                                OutlinedTextField(
                                    value = transactionSearchQuery,
                                    onValueChange = { transactionSearchQuery = it },
                                    label = { Text("Search Tx #, Cashier, Tender") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth().testTag("return_tx_search_input"),
                                    singleLine = true
                                )

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(recentTransactions) { tx ->
                                        val isSelected = selectedTransactionId == tx.id
                                        val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(tx.timestamp))
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { 
                                                    loadTransaction(tx.id)
                                                    if (isCompact) mobileTab = 1
                                                }
                                                .testTag("tx_card_${tx.id}"),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(
                                                if (isSelected) 2.dp else 1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Tx #${tx.id} • ${tx.paymentMethod}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                    Text("$dateStr • Cashier: ${tx.cashierUsername}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                    if (tx.status != "COMPLETED") {
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.errorContainer,
                                                            shape = RoundedCornerShape(4.dp),
                                                            modifier = Modifier.padding(top = 2.dp)
                                                        ) {
                                                            Text(tx.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(horizontal = 4.dp))
                                                        }
                                                    }
                                                }
                                                Text(
                                                    "$curr${String.format(Locale.getDefault(), "%.2f", tx.totalAmount)}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Composable for Return Items and Refund Details
                        @Composable
                        fun ReturnItemsSection(modifier: Modifier = Modifier) {
                            Column(
                                modifier = modifier,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("2. Items to Return & Restock Options", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    if (isCompact && loadedTx != null) {
                                        TextButton(
                                            onClick = { mobileTab = 0 },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Change Tx", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }

                                if (isLoadingTx) {
                                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                } else if (loadedTx == null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                                            Text("Select a transaction to review its items", color = MaterialTheme.colorScheme.outline)
                                            if (isCompact) {
                                                Button(onClick = { mobileTab = 0 }) {
                                                    Text("Pick a Transaction")
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    if (isCompact) {
                                        // Mobile helper card showing which transaction is active
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Tx #${loadedTx!!.id} (${loadedTx!!.paymentMethod})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                                Text("Original Total: $curr${String.format(Locale.getDefault(), "%.2f", loadedTx!!.totalAmount)}", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }

                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(loadedItems) { item ->
                                            val currentSelection = returnSelections[item.id]
                                            val isChecked = currentSelection != null
                                            val returnQty = currentSelection?.first ?: 1
                                            val restock = currentSelection?.second ?: true

                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isChecked) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerLow
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.weight(1f),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Checkbox(
                                                                checked = isChecked,
                                                                onCheckedChange = { checked ->
                                                                    if (checked) {
                                                                        returnSelections[item.id] = Pair(1, true)
                                                                    } else {
                                                                        returnSelections.remove(item.id)
                                                                    }
                                                                },
                                                                modifier = Modifier.testTag("return_check_${item.id}")
                                                            )
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(item.productName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                                Text("Sold: ${item.quantity} • Unit: $curr${String.format(Locale.getDefault(), "%.2f", item.price)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                                            }
                                                        }
                                                        Text(
                                                            "$curr${String.format(Locale.getDefault(), "%.2f", item.price * (if (isChecked) returnQty else item.quantity))}",
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }

                                                    if (isChecked) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                Text("Return Qty:", style = MaterialTheme.typography.bodySmall)
                                                                IconButton(
                                                                    onClick = {
                                                                        if (returnQty > 1) {
                                                                            returnSelections[item.id] = Pair(returnQty - 1, restock)
                                                                        }
                                                                    },
                                                                    modifier = Modifier.size(28.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                                                }
                                                                Text("$returnQty", fontWeight = FontWeight.Bold)
                                                                IconButton(
                                                                    onClick = {
                                                                        if (returnQty < item.quantity) {
                                                                            returnSelections[item.id] = Pair(returnQty + 1, restock)
                                                                        }
                                                                    },
                                                                    modifier = Modifier.size(28.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                                                }
                                                            }

                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Checkbox(
                                                                    checked = restock,
                                                                    onCheckedChange = { checked ->
                                                                        returnSelections[item.id] = Pair(returnQty, checked)
                                                                    }
                                                                )
                                                                Text("Restock", style = MaterialTheme.typography.labelSmall)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Return summary & details
                                    val totalRefundAmount = returnSelections.entries.sumOf { (itemId, selection) ->
                                        val item = loadedItems.find { it.id == itemId }
                                        if (item != null) item.price * selection.first else 0.0
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val reasons = listOf("Defective / Spoiled", "Expired Product", "Wrong Item Given", "Customer Changed Mind", "Size / Exchange")
                                        var reasonExpanded by remember { mutableStateOf(false) }

                                        val methods = listOf("CASH" to "Cash Drawer", "STORE_CREDIT" to "Store Credit", "EXCHANGE" to "Item Exchange")
                                        var methodExpanded by remember { mutableStateOf(false) }

                                        if (isCompact) {
                                            // Stack vertically on mobile so buttons have full width and never crush text vertically
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                // Reason dropdown button
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                    OutlinedButton(
                                                        onClick = { reasonExpanded = true },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Icon(Icons.Default.ReportProblem, null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Reason: $returnReason", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                                                        Spacer(modifier = Modifier.weight(1f))
                                                        Icon(Icons.Default.ArrowDropDown, null)
                                                    }
                                                    DropdownMenu(expanded = reasonExpanded, onDismissRequest = { reasonExpanded = false }) {
                                                        reasons.forEach { r ->
                                                            DropdownMenuItem(
                                                                text = { Text(r) },
                                                                onClick = {
                                                                    returnReason = r
                                                                    reasonExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                // Refund Method dropdown button
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                    val label = methods.find { it.first == refundMethod }?.second ?: refundMethod
                                                    OutlinedButton(
                                                        onClick = { methodExpanded = true },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Icon(Icons.Default.Payment, null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Refund: $label", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                                                        Spacer(modifier = Modifier.weight(1f))
                                                        Icon(Icons.Default.ArrowDropDown, null)
                                                    }
                                                    DropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                                                        methods.forEach { (m, name) ->
                                                            DropdownMenuItem(
                                                                text = { Text(name) },
                                                                onClick = {
                                                                    refundMethod = m
                                                                    methodExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            // Side by side on wider screens
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Box(modifier = Modifier.weight(1f)) {
                                                    OutlinedButton(
                                                        onClick = { reasonExpanded = true },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(returnReason, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                                                    }
                                                    DropdownMenu(expanded = reasonExpanded, onDismissRequest = { reasonExpanded = false }) {
                                                        reasons.forEach { r ->
                                                            DropdownMenuItem(
                                                                text = { Text(r) },
                                                                onClick = {
                                                                    returnReason = r
                                                                    reasonExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                Box(modifier = Modifier.weight(1f)) {
                                                    val label = methods.find { it.first == refundMethod }?.second ?: refundMethod
                                                    OutlinedButton(
                                                        onClick = { methodExpanded = true },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                                                    }
                                                    DropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                                                        methods.forEach { (m, name) ->
                                                            DropdownMenuItem(
                                                                text = { Text(name) },
                                                                onClick = {
                                                                    refundMethod = m
                                                                    methodExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Refund Total:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(
                                                "$curr${String.format(Locale.getDefault(), "%.2f", totalRefundAmount)}",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                if (returnSelections.isEmpty()) {
                                                    Toast.makeText(context, "Please select at least 1 item to return", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                val drafts = returnSelections.mapNotNull { (itemId, sel) ->
                                                    val item = loadedItems.find { it.id == itemId }
                                                    if (item != null) {
                                                        StorePointViewModel.ReturnItemDraft(
                                                            originalItem = item,
                                                            returnQuantity = sel.first,
                                                            unitRefundPrice = item.price,
                                                            restockToInventory = sel.second
                                                        )
                                                    } else null
                                                }

                                                viewModel.processReturn(
                                                    originalTransactionId = loadedTx!!.id,
                                                    returnedItems = drafts,
                                                    refundMethod = refundMethod,
                                                    returnReason = returnReason,
                                                    customerName = customerName,
                                                    notes = returnNotes,
                                                    onSuccess = { ret ->
                                                        completedReturnTx = ret
                                                        Toast.makeText(context, "Return #${ret.id} processed successfully!", Toast.LENGTH_LONG).show()
                                                    },
                                                    onFailure = { err ->
                                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            },
                                            shape = CircleShape,
                                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("process_return_submit_btn"),
                                            enabled = returnSelections.isNotEmpty()
                                        ) {
                                            Icon(Icons.Default.AssignmentReturn, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Complete Return ($curr${String.format(Locale.getDefault(), "%.2f", totalRefundAmount)})", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        if (isCompact) {
                            // Mobile stepped layout with Tab navigation
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TabRow(
                                    selectedTabIndex = mobileTab,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Tab(
                                        selected = mobileTab == 0,
                                        onClick = { mobileTab = 0 },
                                        text = {
                                            Text(
                                                "1. Select Tx",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    )
                                    Tab(
                                        selected = mobileTab == 1,
                                        onClick = { mobileTab = 1 },
                                        text = {
                                            Text(
                                                if (returnSelections.isEmpty()) "2. Return Items" else "2. Return Items (${returnSelections.size})",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    )
                                }

                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    if (mobileTab == 0) {
                                        TransactionSelectorSection(modifier = Modifier.fillMaxSize())
                                    } else {
                                        ReturnItemsSection(modifier = Modifier.fillMaxSize())
                                    }
                                }
                            }
                        } else {
                            // Tablet/Desktop wide 2-column layout
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                TransactionSelectorSection(modifier = Modifier.weight(1.1f).fillMaxHeight())
                                ReturnItemsSection(modifier = Modifier.weight(1.5f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
        }
    }
}
