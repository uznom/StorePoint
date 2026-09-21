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
import com.munzo.storepoint.ui.components.ExpressiveButton
import com.munzo.storepoint.ui.components.ExpressiveButtonSize
import com.munzo.storepoint.ui.components.ExpressiveButtonVariant
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.munzo.storepoint.data.*
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppliersAndPurchasesTab(
    viewModel: StorePointViewModel
) {
    val context = LocalContext.current
    val storeConfig by viewModel.storeConfig.collectAsState()
    val suppliers by viewModel.allSuppliers.collectAsState()
    val purchaseOrders by viewModel.allPurchaseOrders.collectAsState()
    val products by viewModel.allProducts.collectAsState()
    val curr = storeConfig?.currencySymbol ?: "₱"

    var subTab by remember { mutableStateOf(0) } // 0 = Suppliers, 1 = Purchase Orders

    // Dialog flags
    var showSupplierDialog by remember { mutableStateOf(false) }
    var editingSupplier by remember { mutableStateOf<Supplier?>(null) }

    var showCreatePoDialog by remember { mutableStateOf(false) }
    var receivingPo by remember { mutableStateOf<PurchaseOrder?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sub-tabs & Action Row
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < 600.dp
            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpressiveSegmentedTabs(
                        modifier = Modifier.fillMaxWidth(),
                        items = listOf(
                            "Suppliers (${suppliers.size})" to Icons.Default.Business,
                            "Purchase Orders (${purchaseOrders.size})" to Icons.Default.ShoppingCart
                        ),
                        selectedIndex = subTab,
                        onTabSelected = { subTab = it }
                    )

                    if (subTab == 0) {
                        Button(
                            onClick = {
                                editingSupplier = null
                                showSupplierDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().testTag("add_supplier_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Supplier", maxLines = 1, softWrap = false)
                        }
                    } else {
                        Button(
                            onClick = {
                                if (suppliers.isEmpty()) {
                                    Toast.makeText(context, "Please add at least one supplier first.", Toast.LENGTH_SHORT).show()
                                } else {
                                    showCreatePoDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("create_po_btn")
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Purchase Order", maxLines = 1, softWrap = false)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveSegmentedTabs(
                        modifier = Modifier.weight(1f, fill = false),
                        items = listOf(
                            "Suppliers (${suppliers.size})" to Icons.Default.Business,
                            "Purchase Orders (${purchaseOrders.size})" to Icons.Default.ShoppingCart
                        ),
                        selectedIndex = subTab,
                        onTabSelected = { subTab = it }
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    if (subTab == 0) {
                        Button(
                            onClick = {
                                editingSupplier = null
                                showSupplierDialog = true
                            },
                            modifier = Modifier.testTag("add_supplier_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Supplier", maxLines = 1, softWrap = false)
                        }
                    } else {
                        Button(
                            onClick = {
                                if (suppliers.isEmpty()) {
                                    Toast.makeText(context, "Please add at least one supplier first.", Toast.LENGTH_SHORT).show()
                                } else {
                                    showCreatePoDialog = true
                                }
                            },
                            modifier = Modifier.testTag("create_po_btn")
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Purchase Order", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }

        if (subTab == 0) {
            // Suppliers Directory
            if (suppliers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.LocalShipping, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(64.dp))
                        Text("No suppliers registered yet.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                        Text("Add your direct distributors, market stalls, and beverage delivery agents.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(suppliers) { sup ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .expressiveCard(cornerRadius = 24.dp)
                                .tactileBounce(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(sup.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
                                        if (sup.deliverySchedule.isNotBlank()) {
                                            ExpressivePillBadge(
                                                text = sup.deliverySchedule,
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    if (sup.contactPerson.isNotBlank() || sup.phone.isNotBlank()) {
                                        Text(
                                            "Contact: ${sup.contactPerson.ifBlank { "N/A" }} • Phone: ${sup.phone.ifBlank { "N/A" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    if (sup.paymentTerms.isNotBlank()) {
                                        Text("Payment Terms: ${sup.paymentTerms}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    if (sup.notes.isNotBlank()) {
                                        Text(sup.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        editingSupplier = sup
                                        showSupplierDialog = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Supplier")
                                    }
                                    IconButton(onClick = {
                                        viewModel.deleteSupplier(sup.id) {
                                            Toast.makeText(context, "Supplier removed.", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Supplier", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Purchase Orders List
            if (purchaseOrders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(64.dp))
                        Text("No purchase orders created yet.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                        Text("Create purchase orders to track shipments, restock goods, and lock in accurate COGS.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(purchaseOrders) { po ->
                        val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(po.orderDate))
                        val isOrdered = po.status == "ORDERED"
                        val isReceived = po.status == "RECEIVED"

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .expressiveCard(cornerRadius = 24.dp)
                                .tactileBounce(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOrdered) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(po.poNumber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
                                        ExpressivePillBadge(
                                            text = po.status,
                                            containerColor = when (po.status) {
                                                "RECEIVED" -> MaterialTheme.colorScheme.primaryContainer
                                                "ORDERED" -> MaterialTheme.colorScheme.secondaryContainer
                                                else -> MaterialTheme.colorScheme.errorContainer
                                            },
                                            contentColor = when (po.status) {
                                                "RECEIVED" -> MaterialTheme.colorScheme.onPrimaryContainer
                                                "ORDERED" -> MaterialTheme.colorScheme.onSecondaryContainer
                                                else -> MaterialTheme.colorScheme.error
                                            }
                                        )
                                    }

                                    Text(
                                        "$curr${String.format(Locale.getDefault(), "%.2f", po.totalCost)}",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Supplier: ${po.supplierName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                                    Text("Date: $dateStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, softWrap = false)
                                }

                                if (po.expectedDeliveryDate != null) {
                                    val expStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(po.expectedDeliveryDate))
                                    Text("Expected Delivery: $expStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, softWrap = false)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ExpressivePillBadge(
                                        text = "Payment: ${po.paymentStatus}",
                                        containerColor = if (po.paymentStatus == "PAID") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                        contentColor = if (po.paymentStatus == "PAID") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (isOrdered) {
                                            ExpressiveButton(
                                                onClick = { receivingPo = po },
                                                modifier = Modifier.testTag("receive_po_btn_${po.id}"),
                                                label = "Receive Order",
                                                icon = Icons.Default.Check,
                                                variant = ExpressiveButtonVariant.FILLED,
                                                size = ExpressiveButtonSize.S,
                                                containerColor = MaterialTheme.colorScheme.primary,
                                            )
                                        }

                                        IconButton(onClick = {
                                            viewModel.deletePurchaseOrder(po.id) {
                                                Toast.makeText(context, "Purchase order deleted.", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete PO", tint = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Supplier Dialog
    if (showSupplierDialog) {
        var nameInput by remember { mutableStateOf(editingSupplier?.name ?: "") }
        var contactPersonInput by remember { mutableStateOf(editingSupplier?.contactPerson ?: "") }
        var phoneInput by remember { mutableStateOf(editingSupplier?.phone ?: "") }
        var deliveryScheduleInput by remember { mutableStateOf(editingSupplier?.deliverySchedule ?: "") }
        var paymentTermsInput by remember { mutableStateOf(editingSupplier?.paymentTerms ?: "COD") }
        var notesInput by remember { mutableStateOf(editingSupplier?.notes ?: "") }

        AlertDialog(
            onDismissRequest = { showSupplierDialog = false },
            title = { Text(if (editingSupplier == null) "Add Supplier" else "Edit Supplier") },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Supplier / Vendor Name *") },
                        modifier = Modifier.fillMaxWidth().testTag("supplier_name_field"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = contactPersonInput,
                        onValueChange = { contactPersonInput = it },
                        label = { Text("Contact Person / Agent") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Mobile / Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = deliveryScheduleInput,
                        onValueChange = { deliveryScheduleInput = it },
                        label = { Text("Delivery Schedule (e.g. Tue & Fri mornings)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = paymentTermsInput,
                        onValueChange = { paymentTermsInput = it },
                        label = { Text("Payment Terms (e.g. COD, 7 Days, 15 Days)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Notes (Minimum order qty, agent notes)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sName = nameInput.trim()
                        if (sName.isBlank()) {
                            Toast.makeText(context, "Supplier name cannot be empty.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val supplier = Supplier(
                            id = editingSupplier?.id ?: 0,
                            name = sName,
                            contactPerson = contactPersonInput.trim(),
                            phone = phoneInput.trim(),
                            deliverySchedule = deliveryScheduleInput.trim(),
                            paymentTerms = paymentTermsInput.trim(),
                            notes = notesInput.trim()
                        )
                        viewModel.saveSupplier(supplier) {
                            Toast.makeText(context, "Supplier saved.", Toast.LENGTH_SHORT).show()
                            showSupplierDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_supplier_submit_btn")
                ) {
                    Text("Save Supplier")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSupplierDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Create Purchase Order Dialog
    if (showCreatePoDialog) {
        var selectedSupplier by remember { mutableStateOf(suppliers.firstOrNull()) }
        var poNotes by remember { mutableStateOf("") }
        // Order items: productId -> Pair(quantity, unitCost)
        val orderItems = remember { mutableStateMapOf<Int, Pair<Int, Double>>() }

        Dialog(
            onDismissRequest = { showCreatePoDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.9f)
                    .clip(RoundedCornerShape(20.dp)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Create Purchase Order", fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { showCreatePoDialog = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                        )
                    }
                ) { pValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(pValues)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Supplier Selector
                        var supplierDropdownExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { supplierDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Supplier: ${selectedSupplier?.name ?: "Select Supplier"}", fontWeight = FontWeight.Bold)
                            }
                            DropdownMenu(expanded = supplierDropdownExpanded, onDismissRequest = { supplierDropdownExpanded = false }) {
                                suppliers.forEach { sup ->
                                    DropdownMenuItem(
                                        text = { Text(sup.name) },
                                        onClick = {
                                            selectedSupplier = sup
                                            supplierDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Text("Select Products to Order:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(products) { prod ->
                                val selection = orderItems[prod.id]
                                val isSelected = selection != null
                                val currentQty = selection?.first ?: 1
                                val currentCost = selection?.second ?: prod.cost

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
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
                                                    checked = isSelected,
                                                    onCheckedChange = { checked ->
                                                        if (checked) {
                                                            orderItems[prod.id] = Pair(10, if (prod.cost > 0) prod.cost else prod.price * 0.8)
                                                        } else {
                                                            orderItems.remove(prod.id)
                                                        }
                                                    }
                                                )
                                                Column {
                                                    Text(prod.name, fontWeight = FontWeight.Bold)
                                                    Text("Current Stock: ${prod.stockCount} ${prod.baseUom}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                }
                                            }

                                            if (isSelected) {
                                                Text(
                                                    "$curr${String.format(Locale.getDefault(), "%.2f", currentQty * currentCost)}",
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        if (isSelected) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 40.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    value = currentQty.toString(),
                                                    onValueChange = {
                                                        val q = it.toIntOrNull() ?: 1
                                                        orderItems[prod.id] = Pair(q, currentCost)
                                                    },
                                                    label = { Text("Qty") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.weight(1f).height(54.dp),
                                                    singleLine = true
                                                )
                                                OutlinedTextField(
                                                    value = String.format(Locale.getDefault(), "%.2f", currentCost),
                                                    onValueChange = {
                                                        val c = it.toDoubleOrNull() ?: 0.0
                                                        orderItems[prod.id] = Pair(currentQty, c)
                                                    },
                                                    label = { Text("Unit Cost") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    modifier = Modifier.weight(1.2f).height(54.dp),
                                                    singleLine = true
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val totalPoCost = orderItems.entries.sumOf { it.value.first * it.value.second }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total PO Cost:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "$curr${String.format(Locale.getDefault(), "%.2f", totalPoCost)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = {
                                if (selectedSupplier == null || orderItems.isEmpty()) {
                                    Toast.makeText(context, "Select at least 1 product to order.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val itemsList = orderItems.mapNotNull { (pId, details) ->
                                    val p = products.find { it.id == pId }
                                    if (p != null) Pair(p, details) else null
                                }

                                viewModel.createPurchaseOrder(
                                    supplier = selectedSupplier!!,
                                    expectedDeliveryDate = System.currentTimeMillis() + 86400000L * 3, // 3 days default
                                    items = itemsList,
                                    notes = poNotes,
                                    onSuccess = {
                                        Toast.makeText(context, "PO ${it.poNumber} created successfully!", Toast.LENGTH_SHORT).show()
                                        showCreatePoDialog = false
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("submit_po_btn"),
                            enabled = orderItems.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Place Purchase Order")
                        }
                    }
                }
            }
        }
    }

    // Receive Shipment Dialog
    if (receivingPo != null) {
        val po = receivingPo!!
        var paymentStatusInput by remember { mutableStateOf("PAID") }
        var poItems by remember { mutableStateOf<List<PurchaseOrderItem>>(emptyList()) }
        val receivedQuantities = remember { mutableStateMapOf<Int, Int>() }

        LaunchedEffect(po.id) {
            viewModel.repository.getPurchaseOrderItems(po.id).collect { items ->
                poItems = items
                items.forEach {
                    if (!receivedQuantities.containsKey(it.productId)) {
                        receivedQuantities[it.productId] = it.quantityOrdered
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { receivingPo = null },
            title = { Text("Receive Shipment - ${po.poNumber}") },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Supplier: ${po.supplierName}", fontWeight = FontWeight.Bold)
                    Text("Confirm items received to automatically update product inventory and cost prices:")

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(poItems) { item ->
                            val currentRec = receivedQuantities[item.productId] ?: item.quantityOrdered
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Ordered: ${item.quantityOrdered} ${item.uomName} • Unit: $curr${String.format(Locale.getDefault(), "%.2f", item.unitCost)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                OutlinedTextField(
                                    value = currentRec.toString(),
                                    onValueChange = {
                                        val q = it.toIntOrNull() ?: 0
                                        receivedQuantities[item.productId] = q
                                    },
                                    label = { Text("Rec'd") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(80.dp),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Payment Status:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = paymentStatusInput == "PAID",
                                onClick = { paymentStatusInput = "PAID" },
                                label = { Text("PAID") }
                            )
                            FilterChip(
                                selected = paymentStatusInput == "UNPAID",
                                onClick = { paymentStatusInput = "UNPAID" },
                                label = { Text("UNPAID (Pay later)") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val receivedList = receivedQuantities.map { (pId, q) -> Pair(pId, q) }
                        viewModel.receivePurchaseOrder(
                            poId = po.id,
                            receivedItems = receivedList,
                            paymentStatus = paymentStatusInput,
                            onSuccess = {
                                Toast.makeText(context, "Shipment received! Inventory stock and costs updated.", Toast.LENGTH_LONG).show()
                                receivingPo = null
                            }
                        )
                    },
                    modifier = Modifier.testTag("confirm_receive_shipment_btn")
                ) {
                    Text("Confirm & Restock Stock")
                }
            },
            dismissButton = {
                TextButton(onClick = { receivingPo = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
