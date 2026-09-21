package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.data.ProductVariant
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductVariantsDialog(
    product: Product,
    viewModel: StorePointViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val storeConfig by viewModel.storeConfig.collectAsState()
    val allVariants by viewModel.allProductVariants.collectAsState()
    val curr = storeConfig?.currencySymbol ?: "₱"

    val productVariants = remember(allVariants, product.id) {
        allVariants.filter { it.productId == product.id }
    }

    var isAddingOrEditing by remember { mutableStateOf(false) }
    var editingVariantId by remember { mutableStateOf(0) }
    var variantNameInput by remember { mutableStateOf("") }
    var uomNameInput by remember { mutableStateOf("pc") }
    var multiplierInput by remember { mutableStateOf("1") }
    var priceInput by remember { mutableStateOf("") }
    var costInput by remember { mutableStateOf("") }
    var barcodeInput by remember { mutableStateOf("") }

    fun resetForm() {
        editingVariantId = 0
        variantNameInput = ""
        uomNameInput = "pc"
        multiplierInput = "1"
        priceInput = ""
        costInput = ""
        barcodeInput = ""
        isAddingOrEditing = false
    }

    fun startEdit(variant: ProductVariant) {
        editingVariantId = variant.id
        variantNameInput = variant.variantName
        uomNameInput = variant.uomName
        multiplierInput = variant.multiplier.toString()
        priceInput = String.format(Locale.getDefault(), "%.2f", variant.price)
        costInput = String.format(Locale.getDefault(), "%.2f", variant.cost)
        barcodeInput = variant.barcode
        isAddingOrEditing = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .imePadding()
                .expressiveGlassCard(cornerRadius = 28.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("Variants & UOM", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${product.name} (Base: $curr${String.format(Locale.getDefault(), "%.2f", product.price)})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        actions = {
                            if (!isAddingOrEditing) {
                                Button(
                                    onClick = {
                                        resetForm()
                                        isAddingOrEditing = true
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.padding(end = 8.dp).testTag("add_variant_btn"),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                ) {
                    if (isAddingOrEditing) {
                        // Edit/Add Form
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                if (editingVariantId == 0) "Create New Variant / Pack Size" else "Edit Variant",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            // Quick Sari-Sari Presets with horizontal scroll
                            Text("Quick Presets for Sari-Sari Stores:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AssistChip(
                                    onClick = {
                                        variantNameInput = "Tingi (1 Sachet/Stick)"
                                        uomNameInput = "tingi"
                                        multiplierInput = "1"
                                    },
                                    label = { Text("Tingi (1s)", style = MaterialTheme.typography.labelSmall) }
                                )
                                AssistChip(
                                    onClick = {
                                        variantNameInput = "Half-Dozen (6s)"
                                        uomNameInput = "pack"
                                        multiplierInput = "6"
                                    },
                                    label = { Text("6-Pack", style = MaterialTheme.typography.labelSmall) }
                                )
                                AssistChip(
                                    onClick = {
                                        variantNameInput = "1 Pack (12s / Dozen)"
                                        uomNameInput = "pack"
                                        multiplierInput = "12"
                                    },
                                    label = { Text("12-Pack (Dozen)", style = MaterialTheme.typography.labelSmall) }
                                )
                                AssistChip(
                                    onClick = {
                                        variantNameInput = "Bulk Box / Carton"
                                        uomNameInput = "box"
                                        multiplierInput = "24"
                                    },
                                    label = { Text("Box (24s)", style = MaterialTheme.typography.labelSmall) }
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = variantNameInput,
                                    onValueChange = { variantNameInput = it },
                                    label = { Text("Variant Name (e.g. 1L Bottle, 12-Pack)") },
                                    modifier = Modifier.weight(1.5f).testTag("variant_name_input"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = uomNameInput,
                                    onValueChange = { uomNameInput = it },
                                    label = { Text("UOM (pc, pack, kg, bottle)") },
                                    modifier = Modifier.weight(1f).testTag("variant_uom_input"),
                                    singleLine = true
                                )
                            }

                            OutlinedTextField(
                                value = multiplierInput,
                                onValueChange = { multiplierInput = it },
                                label = { Text("Stock Multiplier (Base units deducted)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag("variant_multiplier_input"),
                                singleLine = true
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = priceInput,
                                    onValueChange = { priceInput = it },
                                    label = { Text("Selling Price ($curr)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f).testTag("variant_price_input"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = costInput,
                                    onValueChange = { costInput = it },
                                    label = { Text("Cost Price ($curr)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f).testTag("variant_cost_input"),
                                    singleLine = true
                                )
                            }

                            OutlinedTextField(
                                value = barcodeInput,
                                onValueChange = { barcodeInput = it },
                                label = { Text("Variant Barcode (Optional - scan directly on POS)") },
                                modifier = Modifier.fillMaxWidth().testTag("variant_barcode_input"),
                                singleLine = true
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { resetForm() }) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            val vName = variantNameInput.trim()
                                            val mult = multiplierInput.toIntOrNull() ?: 1
                                            val prc = priceInput.toDoubleOrNull() ?: 0.0
                                            val cst = costInput.toDoubleOrNull() ?: 0.0
                                            if (vName.isBlank() || prc <= 0.0) {
                                                Toast.makeText(context, "Please enter a variant name and valid price.", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }

                                            val variant = ProductVariant(
                                                id = editingVariantId,
                                                productId = product.id,
                                                variantName = vName,
                                                uomName = uomNameInput.trim().ifBlank { "pc" },
                                                multiplier = mult.coerceAtLeast(1),
                                                price = prc,
                                                cost = cst,
                                                barcode = barcodeInput.trim()
                                            )

                                            viewModel.saveProductVariant(variant) {
                                                Toast.makeText(context, "Variant saved successfully!", Toast.LENGTH_SHORT).show()
                                                resetForm()
                                            }
                                        },
                                        shape = CircleShape,
                                        modifier = Modifier.testTag("save_variant_submit_btn")
                                    ) {
                                        Text("Save Variant", fontWeight = FontWeight.Bold)
                                    }
                            }
                        }
                    } else {
                        // Variants List
                        if (productVariants.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(56.dp))
                                    Text("No custom variants configured for this product.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                    Text("Tap '+ Add Variant' to configure tingi, multi-packs, or sizing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(productVariants) { variant ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .expressiveCard(cornerRadius = 20.dp)
                                            .tactileBounce(),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(variant.variantName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    "Deducts ${variant.multiplier} base units • UOM: ${variant.uomName}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (variant.barcode.isNotBlank()) {
                                                    Text("Barcode: ${variant.barcode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        "$curr${String.format(Locale.getDefault(), "%.2f", variant.price)}",
                                                        fontWeight = FontWeight.Black,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                    if (variant.cost > 0.0) {
                                                        Text("Cost: $curr${String.format(Locale.getDefault(), "%.2f", variant.cost)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                    }
                                                }

                                                IconButton(onClick = { startEdit(variant) }) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Edit Variant", tint = MaterialTheme.colorScheme.outline)
                                                }

                                                IconButton(onClick = {
                                                    viewModel.deleteProductVariant(variant.id) {
                                                        Toast.makeText(context, "Variant deleted.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete Variant", tint = MaterialTheme.colorScheme.error)
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
        }
    }
}
