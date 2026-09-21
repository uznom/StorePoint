package com.munzo.storepoint.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.ui.theme.glassPanel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveInventoryDialog(
    product: Product,
    currencySymbol: String,
    drawerCash: Double,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Int, multiplier: Int, totalCost: Double?, payFromDrawer: Boolean) -> Unit
) {
    var quantityStr by remember { mutableStateOf("1") }
    var totalCostStr by remember { mutableStateOf("") }
    var payFromDrawer by remember { mutableStateOf(false) }
    
    val uomOptions = remember(product) {
        val list = mutableListOf<Pair<String, Int>>()
        list.add(Pair("Stick / Single (Base Unit, 1x)", 1))
        if (product.hasStick10s) {
            list.add(Pair("10s Pack (10x)", 10))
        }
        if (product.hasStick20s) {
            list.add(Pair("20s Pack (20x)", 20))
        }
        if (product.hasReam) {
            list.add(Pair("Ream / Carton (200x)", 200))
        }
        if (product.hasMasterCase) {
            list.add(Pair("Master Case (10,000x)", 10000))
        }
        if (product.hasCustomUom) {
            list.add(Pair("${product.customUomName} (${product.customUomMultiplier}x)", product.customUomMultiplier))
        }
        list.add(Pair("Custom Multiplier...", -1))
        list
    }
    
    var selectedOptionIdx by remember { mutableStateOf(0) }
    var uomExpanded by remember { mutableStateOf(false) }
    var customMultiplierStr by remember { mutableStateOf("") }
    
    val currentOption = uomOptions[selectedOptionIdx]
    val activeMultiplier = if (currentOption.second == -1) {
        customMultiplierStr.toIntOrNull() ?: 1
    } else {
        currentOption.second
    }
    
    val quantity = quantityStr.toIntOrNull() ?: 0
    val totalBaseUnits = quantity * activeMultiplier
    val totalCost = totalCostStr.toDoubleOrNull()
    
    val calculatedUnitCost = if (totalBaseUnits > 0 && totalCost != null && totalCost > 0.0) {
        totalCost / totalBaseUnits
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(text = "Receive / Restock Inventory", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(
                        text = "Product: ${product.name}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Current Stock: ${product.stockCount} base units",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Select UOM Dropdown Box
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Select UOM (Unit Of Measure)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { uomExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("restock_uom_dropdown_trigger")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentOption.first,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Expand options"
                                )
                            }
                        }
                        
                        DropdownMenu(
                            expanded = uomExpanded,
                            onDismissRequest = { uomExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            uomOptions.forEachIndexed { idx, pair ->
                                DropdownMenuItem(
                                    text = { Text(pair.first) },
                                    onClick = {
                                        selectedOptionIdx = idx
                                        uomExpanded = false
                                    },
                                    modifier = Modifier.testTag("restock_uom_item_${pair.second}")
                                )
                            }
                        }
                    }
                }

                // If Custom Multiplier is chosen
                if (currentOption.second == -1) {
                    OutlinedTextField(
                        value = customMultiplierStr,
                        onValueChange = { customMultiplierStr = it.filter { char -> char.isDigit() } },
                        label = { Text("Custom Multiplier (Items per UOM)") },
                        placeholder = { Text("e.g. 50") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("restock_custom_multiplier_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                // Quantity received field
                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it.filter { char -> char.isDigit() } },
                    label = { Text("Quantity Received") },
                    placeholder = { Text("e.g. 5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("restock_qty_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // Total Cost paid field
                OutlinedTextField(
                    value = totalCostStr,
                    onValueChange = { totalCostStr = it.filter { char -> char.isDigit() || char == '.' } },
                    label = { Text("Total Cost Paid (Optional)") },
                    placeholder = { Text("e.g. 6000.00") },
                    prefix = { Text(currencySymbol) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("restock_cost_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                if (totalCost != null && totalCost > 0.0) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = payFromDrawer,
                                onCheckedChange = { payFromDrawer = it },
                                modifier = Modifier.testTag("pay_from_drawer_checkbox")
                            )
                            Text("Deduct Supplier Payment from Cash Register Drawer", style = MaterialTheme.typography.bodyMedium)
                        }
                        
                        val isShortageActive = payFromDrawer && totalCost > drawerCash
                        if (isShortageActive) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Payout Error: Cash register drawer holds $currencySymbol${String.format(Locale.getDefault(), "%.2f", drawerCash)}, which is insufficient to pay total cost of $currencySymbol${String.format(Locale.getDefault(), "%.2f", totalCost)}.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // Math dynamic breakdown card
                Card(
                    modifier = Modifier.fillMaxWidth().glassPanel(cornerRadius = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "RECEIVING BREAKDOWN PREVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = "• Multiplier per Unit: $activeMultiplier base unit(s)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Text(
                            text = "• Total Base Units Added: $totalBaseUnits unit(s)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (calculatedUnitCost != null) {
                            Text(
                                text = "• Calculated Cost / Base Unit: $currencySymbol${String.format(Locale.getDefault(), "%.2f", calculatedUnitCost)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "• Calculated Cost / Base Unit: (Retains previous: $currencySymbol${String.format(Locale.getDefault(), "%.2f", product.cost)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        
                        val projectedStock = product.stockCount + totalBaseUnits
                        Text(
                            text = "• Projected Stock Count: $projectedStock base unit(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (projectedStock > product.stockCount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isShortageActive = payFromDrawer && totalCost != null && totalCost > drawerCash
            Button(
                onClick = {
                    onConfirm(quantity, activeMultiplier, totalCost, payFromDrawer)
                },
                enabled = quantity > 0 && activeMultiplier > 0 && !isShortageActive,
                modifier = Modifier.testTag("restock_dialog_confirm_btn")
            ) {
                Text("Confirm Receive")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("restock_dialog_dismiss_btn")
            ) {
                Text("Cancel")
            }
        }
    )
}
