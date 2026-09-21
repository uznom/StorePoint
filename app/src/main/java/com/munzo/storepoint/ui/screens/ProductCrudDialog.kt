package com.munzo.storepoint.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.Category
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.ui.BarcodeScannerDialog
import com.munzo.storepoint.ui.theme.ExpressiveSectionHeader

// Product add/edit modal dialogue
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductCrudDialog(
    editingProduct: Product?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSubmit: (
        name: String,
        categoryId: Int,
        price: Double,
        stock: Int,
        barcode: String,
        expirationDate: String?,
        hasStick10s: Boolean,
        priceStick10s: Double,
        hasStick20s: Boolean,
        priceStick20s: Double,
        hasReam: Boolean,
        priceReam: Double,
        hasMasterCase: Boolean,
        priceMasterCase: Double,
        hasCustomUom: Boolean,
        customUomName: String,
        customUomMultiplier: Int,
        customUomPrice: Double,
        barcode10s: String,
        barcode20s: String,
        barcodeReam: String,
        barcodeMasterCase: String,
        barcodeCustomUom: String,
        cost: Double
    ) -> Unit
) {
    var pName by remember { mutableStateOf(editingProduct?.name ?: "") }
    var pPriceStr by remember { mutableStateOf(editingProduct?.price?.toString() ?: "") }
    var pStockStr by remember { mutableStateOf(editingProduct?.stockCount?.toString() ?: "") }
    var pBarcode by remember { mutableStateOf(editingProduct?.barcode ?: "") }
    var pExpirationDate by remember { mutableStateOf(editingProduct?.expirationDate ?: "") }
    var pCostStr by remember { mutableStateOf(editingProduct?.cost?.toString() ?: "") }
    
    var barcode10s by remember { mutableStateOf(editingProduct?.barcode10s ?: "") }
    var barcode20s by remember { mutableStateOf(editingProduct?.barcode20s ?: "") }
    var barcodeReam by remember { mutableStateOf(editingProduct?.barcodeReam ?: "") }
    var barcodeMasterCase by remember { mutableStateOf(editingProduct?.barcodeMasterCase ?: "") }
    var barcodeCustomUom by remember { mutableStateOf(editingProduct?.barcodeCustomUom ?: "") }
    
    var activeScanningTarget by remember { mutableStateOf<String?>(null) } // null, "base", "10s", "20s", "ream", "master", "custom"

    var pCategoryId by remember { mutableStateOf(editingProduct?.categoryId ?: (categories.firstOrNull()?.id ?: 0)) }
    var categoriesExpanded by remember { mutableStateOf(false) }

    val categoryName = categories.find { it.id == pCategoryId }?.name ?: ""
    val isPerishableByDefault = categoryName.equals("Groceries", ignoreCase = true) || categoryName.contains("Food", ignoreCase = true)
    var hasExpiration by remember(pCategoryId) { mutableStateOf(isPerishableByDefault || pExpirationDate.isNotBlank()) }

    var hasStick10s by remember { mutableStateOf(editingProduct?.hasStick10s ?: false) }
    var priceStick10sStr by remember { mutableStateOf(editingProduct?.priceStick10s?.toString() ?: "") }
    
    var hasStick20s by remember { mutableStateOf(editingProduct?.hasStick20s ?: false) }
    var priceStick20sStr by remember { mutableStateOf(editingProduct?.priceStick20s?.toString() ?: "") }
    
    var hasReam by remember { mutableStateOf(editingProduct?.hasReam ?: false) }
    var priceReamStr by remember { mutableStateOf(editingProduct?.priceReam?.toString() ?: "") }
    
    var hasMasterCase by remember { mutableStateOf(editingProduct?.hasMasterCase ?: false) }
    var priceMasterCaseStr by remember { mutableStateOf(editingProduct?.priceMasterCase?.toString() ?: "") }
    
    var hasCustomUom by remember { mutableStateOf(editingProduct?.hasCustomUom ?: false) }
    var customUomName by remember { mutableStateOf(editingProduct?.customUomName ?: "Pack") }
    var customUomMultiplierStr by remember { mutableStateOf(editingProduct?.customUomMultiplier?.toString() ?: "1") }
    var customUomPriceStr by remember { mutableStateOf(editingProduct?.customUomPrice?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val price = pPriceStr.toDoubleOrNull() ?: 0.0
                    val stock = pStockStr.toIntOrNull() ?: 0
                    val cost = pCostStr.toDoubleOrNull() ?: 0.0
                    if (pName.isNotBlank()) {
                        onSubmit(
                            pName.trim(), 
                            pCategoryId, 
                            price, 
                            stock, 
                            pBarcode.trim(),
                            if (hasExpiration && pExpirationDate.isNotBlank()) pExpirationDate.trim() else null,
                            hasStick10s,
                            priceStick10sStr.toDoubleOrNull() ?: 0.0,
                            hasStick20s,
                            priceStick20sStr.toDoubleOrNull() ?: 0.0,
                            hasReam,
                            priceReamStr.toDoubleOrNull() ?: 0.0,
                            hasMasterCase,
                            priceMasterCaseStr.toDoubleOrNull() ?: 0.0,
                            hasCustomUom,
                            customUomName.trim(),
                            customUomMultiplierStr.toIntOrNull() ?: 1,
                            customUomPriceStr.toDoubleOrNull() ?: 0.0,
                            barcode10s.trim(),
                            barcode20s.trim(),
                            barcodeReam.trim(),
                            barcodeMasterCase.trim(),
                            barcodeCustomUom.trim(),
                            cost
                        )
                    }
                },
                modifier = Modifier.testTag("product_submit_button"),
                enabled = pName.isNotBlank() && pPriceStr.toDoubleOrNull() != null && pStockStr.toIntOrNull() != null
            ) {
                Text("Save Item")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text(if (editingProduct == null) "Create Store Product Registry" else "Modify Product Details") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.imePadding().verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = pName,
                    onValueChange = { pName = it },
                    label = { Text("Product Label/Name") },
                    modifier = Modifier.fillMaxWidth().testTag("add_pname_input"),
                    singleLine = true
                )

                // Category dropdown filter inside modal
                ExposedDropdownMenuBox(
                    expanded = categoriesExpanded,
                    onExpandedChange = { categoriesExpanded = !categoriesExpanded }
                ) {
                    val categoryLabel = categories.find { it.id == pCategoryId }?.name ?: "Assign Category"
                    OutlinedTextField(
                        readOnly = true,
                        value = categoryLabel,
                        onValueChange = {},
                        label = { Text("Product Category") },
                        modifier = Modifier.fillMaxWidth().menuAnchor().testTag("add_pcategory_input"),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoriesExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = categoriesExpanded,
                        onDismissRequest = { categoriesExpanded = false }
                    ) {
                        categories.forEach { selection ->
                            DropdownMenuItem(
                                text = { Text(selection.name) },
                                onClick = {
                                    pCategoryId = selection.id
                                    categoriesExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = pPriceStr,
                        onValueChange = { pPriceStr = it },
                        label = { Text("Selling Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("add_pprice_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = pCostStr,
                        onValueChange = { pCostStr = it },
                        label = { Text("Dealer's Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("add_pcost_input"),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = pStockStr,
                    onValueChange = { pStockStr = it },
                    label = { Text("Open Inventory Stock Count") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("add_pstock_input"),
                    singleLine = true
                )

                // Barcode input with real CameraX scanner workflow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = pBarcode,
                        onValueChange = { pBarcode = it },
                        label = { Text("SKU / Barcode Lookup") },
                        modifier = Modifier.weight(1f).testTag("add_pbarcode_input"),
                        singleLine = true,
                        placeholder = { Text("e.g. 123456") },
                        trailingIcon = {
                            if (pBarcode.isNotEmpty()) {
                                IconButton(onClick = { pBarcode = "" }) {
                                    Icon(Icons.Default.Clear, "Clear")
                                }
                            }
                        }
                    )
                    
                    FilledIconButton(
                        onClick = {
                            activeScanningTarget = "base"
                        },
                        modifier = Modifier.size(56.dp).testTag("add_pbarcode_scan_button")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan with Camera")
                    }
                }

                if (activeScanningTarget != null) {
                    val currentTarget = activeScanningTarget
                    BarcodeScannerDialog(
                        title = "Scan Product Barcode",
                        instruction = "Scan the UPC/EAN/QR barcode package labels to retrieve its numeric serial instantly.",
                        onBarcodeScanned = { code ->
                            when (currentTarget) {
                                "base" -> pBarcode = code
                                "10s" -> barcode10s = code
                                "20s" -> barcode20s = code
                                "ream" -> barcodeReam = code
                                "master" -> barcodeMasterCase = code
                                "custom" -> barcodeCustomUom = code
                            }
                            activeScanningTarget = null
                            try {
                                val toneG = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)
                                toneG.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    try { toneG.release() } catch (ignored: Exception) {}
                                }, 250)
                            } catch (e: Exception) {
                                android.util.Log.w("AdminDashboard", "Tone generation failed: ${e.message}")
                            }
                        },
                        onDismiss = { activeScanningTarget = null }
                    )
                }

                // Perishable/Expiration Date Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Checkbox(
                        checked = hasExpiration,
                        onCheckedChange = { hasExpiration = it }
                    )
                    Text("This product has an expiration date", style = MaterialTheme.typography.bodyMedium)
                }

                if (hasExpiration) {
                    OutlinedTextField(
                        value = pExpirationDate,
                        onValueChange = { pExpirationDate = it },
                        label = { Text("Expiration Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth().testTag("add_pexpiration_input"),
                        singleLine = true,
                        placeholder = { Text("e.g. 2026-12-31") },
                        trailingIcon = {
                            IconButton(onClick = {
                                val future = java.util.Calendar.getInstance()
                                future.add(java.util.Calendar.DAY_OF_YEAR, 90)
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                pExpirationDate = sdf.format(future.time)
                            }) {
                                Icon(Icons.Default.Event, "Set 90 days from now")
                            }
                        }
                    )
                }

                // Dynamic UOM Configuration Segment
                val isCigaretteOrCigar = categoryName.contains("Cigarette", ignoreCase = true) || categoryName.contains("Cigar", ignoreCase = true)
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                ExpressiveSectionHeader(
                    title = "Unit of Measurement Setup",
                    icon = Icons.Default.Straighten
                )
                
                if (isCigaretteOrCigar) {
                    Text(
                        text = "Specify pricing for optional multi-unit packaging of Cigarettes/Cigars. Base unit is Stick/Piece.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    
                    // 10s Pack
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = hasStick10s, onCheckedChange = { hasStick10s = it })
                                Text("Available in 10s Pack (10 sticks)", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (hasStick10s) {
                                OutlinedTextField(
                                    value = priceStick10sStr,
                                    onValueChange = { priceStick10sStr = it },
                                    label = { Text("Price for 10s Pack") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("price_10s_input"),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = barcode10s,
                                        onValueChange = { barcode10s = it },
                                        label = { Text("10s Barcode Scanner Lookup") },
                                        modifier = Modifier.weight(1f).testTag("barcode_10s_input"),
                                        singleLine = true
                                    )
                                    IconButton(
                                        onClick = { activeScanningTarget = "10s" },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan 10s Barcode")
                                    }
                                }
                            }
                        }
                    }
                    
                    // 20s Pack
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = hasStick20s, onCheckedChange = { hasStick20s = it })
                                Text("Available in 20s Pack (20 sticks)", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (hasStick20s) {
                                OutlinedTextField(
                                    value = priceStick20sStr,
                                    onValueChange = { priceStick20sStr = it },
                                    label = { Text("Price for 20s Pack") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("price_20s_input"),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = barcode20s,
                                        onValueChange = { barcode20s = it },
                                        label = { Text("20s Barcode Scanner Lookup") },
                                        modifier = Modifier.weight(1f).testTag("barcode_20s_input"),
                                        singleLine = true
                                    )
                                    IconButton(
                                        onClick = { activeScanningTarget = "20s" },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan 20s Barcode")
                                    }
                                }
                            }
                        }
                    }
                    
                    // Ream
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = hasReam, onCheckedChange = { hasReam = it })
                                Text("Available in Ream / Carton (200 sticks)", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (hasReam) {
                                OutlinedTextField(
                                    value = priceReamStr,
                                    onValueChange = { priceReamStr = it },
                                    label = { Text("Price for Ream") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("price_ream_input"),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = barcodeReam,
                                        onValueChange = { barcodeReam = it },
                                        label = { Text("Ream Barcode Scanner Lookup") },
                                        modifier = Modifier.weight(1f).testTag("barcode_ream_input"),
                                        singleLine = true
                                    )
                                    IconButton(
                                        onClick = { activeScanningTarget = "ream" },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Ream Barcode")
                                    }
                                }
                            }
                        }
                    }
                    
                    // Master Case
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = hasMasterCase, onCheckedChange = { hasMasterCase = it })
                                Text("Available in Master Case (10,000 sticks)", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (hasMasterCase) {
                                OutlinedTextField(
                                    value = priceMasterCaseStr,
                                    onValueChange = { priceMasterCaseStr = it },
                                    label = { Text("Price for Master Case") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("price_mastercase_input"),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = barcodeMasterCase,
                                        onValueChange = { barcodeMasterCase = it },
                                        label = { Text("Master Case Barcode Scanner Lookup") },
                                        modifier = Modifier.weight(1f).testTag("barcode_master_input"),
                                        singleLine = true
                                    )
                                    IconButton(
                                        onClick = { activeScanningTarget = "master" },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Master Case Barcode")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Optionally set up a custom bulk unit option (e.g. Box, Pack, Case, Case of 12) for this item.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = hasCustomUom, onCheckedChange = { hasCustomUom = it })
                                Text("Enable Custom Unit Option", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (hasCustomUom) {
                                OutlinedTextField(
                                    value = customUomName,
                                    onValueChange = { customUomName = it },
                                    label = { Text("UOM Name (e.g., Box, Case of 6)") },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("custom_uom_name_input"),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customUomMultiplierStr,
                                    onValueChange = { customUomMultiplierStr = it },
                                    label = { Text("Pieces Multiplier (e.g. 6 or 12)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("custom_uom_multiplier_input"),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customUomPriceStr,
                                    onValueChange = { customUomPriceStr = it },
                                    label = { Text("Price for this Custom UOM") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("custom_uom_price_input"),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = barcodeCustomUom,
                                        onValueChange = { barcodeCustomUom = it },
                                        label = { Text("Custom UOM Barcode Scanner") },
                                        modifier = Modifier.weight(1f).testTag("barcode_custom_input"),
                                        singleLine = true
                                    )
                                    IconButton(
                                        onClick = { activeScanningTarget = "custom" },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Custom UOM Barcode")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
