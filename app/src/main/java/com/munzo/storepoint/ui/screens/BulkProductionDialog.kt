package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munzo.storepoint.data.BillOfMaterials
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.data.ProductionLogEntity
import com.munzo.storepoint.data.StorePointRepository
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.theme.HeroUITheme
import com.munzo.storepoint.ui.components.*
import com.munzo.storepoint.ui.components.ExpressiveButton
import com.munzo.storepoint.ui.components.ExpressiveButtonSize
import com.munzo.storepoint.ui.components.ExpressiveButtonVariant
import com.munzo.storepoint.ui.deleteBomRecipe
import com.munzo.storepoint.ui.executeRepackagingProduction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class RepackagingPreset(
    val title: String,
    val description: String,
    val rawKeyword: String,
    val targetKeyword: String,
    val defaultRawQty: Double,
    val defaultYieldQty: Double,
    val defaultWastePct: Double,
    val packagingSize: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkProductionDialog(
    viewModel: StorePointViewModel,
    onDismiss: () -> Unit,
    preselectedRawProduct: Product? = null
) {
    val context = LocalContext.current
    val allProducts by viewModel.allProducts.collectAsState()
    val allBoms by viewModel.allBOMs.collectAsState()
    val allProductionRuns by viewModel.allProductionRuns.collectAsState()
    val allMovements by viewModel.allInventoryMovements.collectAsState()
    val storeConfig by viewModel.storeConfig.collectAsState()
    val curr = storeConfig?.currencySymbol ?: "₱"

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    var selectedRawProduct by remember { mutableStateOf(preselectedRawProduct ?: allProducts.firstOrNull()) }
    var selectedTargetProduct by remember { mutableStateOf<Product?>(null) }

    var packagingSizeStr by remember { mutableStateOf("") }
    var rawUsageQtyStr by remember { mutableStateOf("1") }
    var targetYieldQtyStr by remember { mutableStateOf("10") }
    var wasteLossPctStr by remember { mutableStateOf("0") }
    var retailPriceStr by remember { mutableStateOf("") }
    var productionNotes by remember { mutableStateOf("") }
    var saveAsBomRecipe by remember { mutableStateOf(true) }

    var isExecuting by remember { mutableStateOf(false) }

    // Dropdown expansion states
    var rawDropdownExpanded by remember { mutableStateOf(false) }
    var targetDropdownExpanded by remember { mutableStateOf(false) }

    // Repackaging Presets (Philippine retail scenarios: Water, Cooking Oil, Rice, Sugar)
    val presets = remember {
        listOf(
            RepackagingPreset(
                title = "20L Water ➔ 500ml Ice Bags",
                description = "1 container (20L) produces ~40x 500ml ice water bags",
                rawKeyword = "Water",
                targetKeyword = "Ice",
                defaultRawQty = 1.0,
                defaultYieldQty = 40.0,
                defaultWastePct = 0.0,
                packagingSize = "500ml Ice Bag"
            ),
            RepackagingPreset(
                title = "20L Oil ➔ 375ml Bottles",
                description = "1 container (20L) produces ~50x 375ml bottles with ~2% residue",
                rawKeyword = "Oil",
                targetKeyword = "375ml",
                defaultRawQty = 1.0,
                defaultYieldQty = 50.0,
                defaultWastePct = 2.0,
                packagingSize = "375ml Bottle"
            ),
            RepackagingPreset(
                title = "20L Oil ➔ 187.5ml Pouches",
                description = "1 container (20L) produces ~100x 187.5ml plastic pouches",
                rawKeyword = "Oil",
                targetKeyword = "Pouch",
                defaultRawQty = 1.0,
                defaultYieldQty = 100.0,
                defaultWastePct = 2.5,
                packagingSize = "187.5ml Pouch"
            ),
            RepackagingPreset(
                title = "50kg Rice ➔ 1kg Retail Bags",
                description = "1 wholesale sack (50kg) repacked into 50x 1kg consumer bags",
                rawKeyword = "Rice",
                targetKeyword = "1kg",
                defaultRawQty = 1.0,
                defaultYieldQty = 50.0,
                defaultWastePct = 0.0,
                packagingSize = "1kg Bag"
            ),
            RepackagingPreset(
                title = "50kg Sugar ➔ 500g Packs",
                description = "1 sack (50kg) repacked into 100x 500g half-kilo packs",
                rawKeyword = "Sugar",
                targetKeyword = "500g",
                defaultRawQty = 1.0,
                defaultYieldQty = 100.0,
                defaultWastePct = 1.0,
                packagingSize = "500g Pack"
            )
        )
    }

    // Autofill from preset
    fun applyPreset(preset: RepackagingPreset) {
        val raw = allProducts.firstOrNull { it.name.contains(preset.rawKeyword, ignoreCase = true) }
        val target = allProducts.firstOrNull { it.name.contains(preset.targetKeyword, ignoreCase = true) }
        if (raw != null) selectedRawProduct = raw
        if (target != null) {
            selectedTargetProduct = target
            retailPriceStr = String.format(Locale.US, "%.2f", target.price)
        }
        packagingSizeStr = preset.packagingSize
        rawUsageQtyStr = preset.defaultRawQty.toInt().toString()
        targetYieldQtyStr = preset.defaultYieldQty.toInt().toString()
        wasteLossPctStr = String.format(Locale.US, "%.1f", preset.defaultWastePct)
    }

    // Autofill from saved BOM recipe
    fun applyBom(bom: BillOfMaterials) {
        val raw = allProducts.firstOrNull { it.id == bom.rawMaterialProductId }
        val target = allProducts.firstOrNull { it.id == bom.targetRetailProductId }
        if (raw != null) selectedRawProduct = raw
        if (target != null) {
            selectedTargetProduct = target
            retailPriceStr = String.format(Locale.US, "%.2f", target.price)
        }
        rawUsageQtyStr = bom.rawQuantityNeeded.toInt().toString()
        targetYieldQtyStr = bom.producedRetailYield.toInt().toString()
        wasteLossPctStr = String.format(Locale.US, "%.1f", bom.wasteLossPercentage)
        if (bom.notes.isNotBlank()) productionNotes = bom.notes
        selectedTabIndex = 0
    }

    // Prefill target selling price when selected
    LaunchedEffect(selectedTargetProduct) {
        val target = selectedTargetProduct
        if (target != null && retailPriceStr.isBlank()) {
            retailPriceStr = String.format(Locale.US, "%.2f", target.price)
        }
    }

    val rawUsage = rawUsageQtyStr.toDoubleOrNull() ?: 0.0
    val targetYield = targetYieldQtyStr.toDoubleOrNull() ?: 0.0
    val wastePct = wasteLossPctStr.toDoubleOrNull() ?: 0.0
    val retailPrice = retailPriceStr.toDoubleOrNull() ?: (selectedTargetProduct?.price ?: 0.0)

    val rawUnitCost = selectedRawProduct?.cost ?: 0.0
    val totalBatchRawCost = StorePointRepository.roundMoney(rawUsage * rawUnitCost)
    val wasteLossQty = if (targetYield > 0.0) (targetYield * (wastePct / 100.0)) else 0.0

    // Unit Cost incorporates raw consumption divided by actual sellable yield
    val derivedUnitCost = if (targetYield > 0.0) {
        StorePointRepository.roundMoney(totalBatchRawCost / targetYield)
    } else 0.0

    val unitGrossProfit = StorePointRepository.roundMoney(retailPrice - derivedUnitCost)
    val profitMarginPct = if (retailPrice > 0.0) {
        StorePointRepository.roundMoney((unitGrossProfit / retailPrice) * 100.0)
    } else 0.0

    val isRawStockSufficient = selectedRawProduct?.let { it.stockCount >= rawUsage.toInt() } ?: false
    val canExecute = selectedRawProduct != null &&
            selectedTargetProduct != null &&
            selectedRawProduct?.id != selectedTargetProduct?.id &&
            rawUsage > 0.0 &&
            targetYield > 0.0 &&
            isRawStockSufficient &&
            !isExecuting

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    AlertDialog(
        onDismissRequest = { if (!isExecuting) onDismiss() },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Transform, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Bulk-to-Retail Repackaging (BOM Engine)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = onDismiss, enabled = !isExecuting) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Navigation Tabs: Batch Production / Audit Trail / BOM Presets
                val tabLabels = listOf(
                    "Repack Batch",
                    "Audit Trail (${allProductionRuns.size})",
                    "BOMs (${allBoms.size})"
                )
                HeroSegmentedTabs(
                    items = listOf(0, 1, 2),
                    selectedItem = selectedTabIndex,
                    onItemSelected = { selectedTabIndex = it },
                    labelProvider = { tabLabels[it] },
                    iconProvider = {
                        when (it) {
                            0 -> Icons.Default.PrecisionManufacturing
                            1 -> Icons.Default.History
                            else -> Icons.Default.Bookmark
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                when (selectedTabIndex) {
                    // TAB 0: REPACKAGING BATCH WORKFLOW
                    0 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .imePadding()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Transform wholesale bulk stock (e.g., 20L Water/Oil, 50kg Sacks) into retail packaged SKUs with atomic inventory updates and derived unit costing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Quick Preset Scenario Chips
                            Text("Quick Presets (1-Tap Philippine Retail):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presets.forEach { p ->
                                    SuggestionChip(
                                        onClick = { applyPreset(p) },
                                        label = { Text(p.title, fontSize = 11.sp) }
                                    )
                                }
                            }

                            // 1. Source Raw Material Product
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("1. Source Raw Bulk Stock", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                        selectedRawProduct?.let { raw ->
                                            Surface(
                                                shape = CircleShape,
                                                color = if (raw.stockCount >= rawUsage.toInt() && raw.stockCount > 0) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer
                                            ) {
                                                Text(
                                                    text = if (raw.stockCount >= rawUsage.toInt() && raw.stockCount > 0) "IN STOCK" else "LOW/INSUFFICIENT",
                                                    color = if (raw.stockCount >= rawUsage.toInt() && raw.stockCount > 0) Color(0xFF10B981) else MaterialTheme.colorScheme.onErrorContainer,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        HeroInput(
                                            value = selectedRawProduct?.name ?: "Select Raw Product",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Raw Bulk Stock SKU") },
                                            trailingIcon = {
                                                IconButton(onClick = { rawDropdownExpanded = true }) {
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().clickable { rawDropdownExpanded = true }
                                        )
                                        DropdownMenu(
                                            expanded = rawDropdownExpanded,
                                            onDismissRequest = { rawDropdownExpanded = false },
                                            modifier = Modifier.heightIn(max = 280.dp)
                                        ) {
                                            allProducts.forEach { prod ->
                                                DropdownMenuItem(
                                                    text = { Text("${prod.name} (Stock: ${prod.stockCount} ${prod.baseUom} • Cost: $curr${String.format(Locale.US, "%.2f", prod.cost)})") },
                                                    onClick = {
                                                        selectedRawProduct = prod
                                                        rawDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    selectedRawProduct?.let { raw ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "On-Hand: ${raw.stockCount} ${raw.baseUom} • Raw Unit Cost: $curr${String.format(Locale.US, "%.2f", raw.cost)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (raw.stockCount < rawUsage.toInt()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    HeroInput(
                                        value = rawUsageQtyStr,
                                        onValueChange = { str -> if (str.all { it.isDigit() }) rawUsageQtyStr = str },
                                        label = { Text("Raw Batch Usage (${selectedRawProduct?.baseUom ?: "units"})") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth().testTag("raw_usage_input"),
                                        singleLine = true,
                                        isError = !isRawStockSufficient
                                    )
                                    if (!isRawStockSufficient) {
                                        Text("Insufficient stock in inventory for this batch size.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            // 2. Target Retail Output SKU & Packaging Spec
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("2. Target Packaged Retail SKU", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)

                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        HeroInput(
                                            value = selectedTargetProduct?.name ?: "Select Target Retail Product",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Target Retail Product") },
                                            trailingIcon = {
                                                IconButton(onClick = { targetDropdownExpanded = true }) {
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().clickable { targetDropdownExpanded = true }
                                        )
                                        DropdownMenu(
                                            expanded = targetDropdownExpanded,
                                            onDismissRequest = { targetDropdownExpanded = false },
                                            modifier = Modifier.heightIn(max = 280.dp)
                                        ) {
                                            allProducts.filter { it.id != selectedRawProduct?.id }.forEach { prod ->
                                                DropdownMenuItem(
                                                    text = { Text("${prod.name} (Stock: ${prod.stockCount} ${prod.baseUom} • Price: $curr${String.format(Locale.US, "%.2f", prod.price)})") },
                                                    onClick = {
                                                        selectedTargetProduct = prod
                                                        retailPriceStr = String.format(Locale.US, "%.2f", prod.price)
                                                        targetDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    HeroInput(
                                        value = packagingSizeStr,
                                        onValueChange = { packagingSizeStr = it },
                                        label = { Text("Packaging Size / Specification (e.g. 375ml bottle, 500ml bag)") },
                                        modifier = Modifier.fillMaxWidth().testTag("packaging_spec_input"),
                                        singleLine = true
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        HeroInput(
                                            value = targetYieldQtyStr,
                                            onValueChange = { str -> if (str.all { it.isDigit() }) targetYieldQtyStr = str },
                                            label = { Text("Yield (${selectedTargetProduct?.baseUom ?: "pcs"})") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f).testTag("target_yield_input"),
                                            singleLine = true
                                        )
                                        HeroInput(
                                            value = wasteLossPctStr,
                                            onValueChange = { wasteLossPctStr = it },
                                            label = { Text("Scrap / Waste %") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.weight(1f).testTag("waste_pct_input"),
                                            singleLine = true
                                        )
                                    }

                                    HeroInput(
                                        value = retailPriceStr,
                                        onValueChange = { retailPriceStr = it },
                                        label = { Text("Retail Selling Price ($curr)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.fillMaxWidth().testTag("retail_price_input"),
                                        singleLine = true
                                    )
                                }
                            }

                            // 3. Financial & Derived Cost Breakdown
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth().testTag("derived_economics_card")
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("3. Derived Economics & Profit Margin", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Total Batch Raw Material Cost:", style = MaterialTheme.typography.bodySmall)
                                        Text("$curr${String.format(Locale.US, "%.2f", totalBatchRawCost)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Scrap / Loss Factor Deduction:", style = MaterialTheme.typography.bodySmall)
                                        Text("${String.format(Locale.US, "%.1f", wasteLossQty)} units (${String.format(Locale.US, "%.1f", wastePct)}%)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Derived Unit Cost (with scrap loss):", style = MaterialTheme.typography.bodySmall)
                                        Text("$curr${String.format(Locale.US, "%.2f", derivedUnitCost)} / unit", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Unit Gross Profit:", style = MaterialTheme.typography.bodySmall)
                                        Text("$curr${String.format(Locale.US, "%.2f", unitGrossProfit)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    }

                                    HeroDivider(modifier = Modifier.padding(vertical = 2.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Projected Profit Margin:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Surface(
                                            shape = CircleShape,
                                            color = when {
                                                profitMarginPct >= 30.0 -> Color(0xFF10B981)
                                                profitMarginPct >= 15.0 -> Color(0xFF0D9488)
                                                profitMarginPct >= 0.0 -> MaterialTheme.colorScheme.tertiary
                                                else -> MaterialTheme.colorScheme.error
                                            }
                                        ) {
                                            Text(
                                                text = "${String.format(Locale.US, "%.1f", profitMarginPct)}% margin",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Recipe Save & Notes
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                HeroCheckbox(
                                    checked = saveAsBomRecipe,
                                    onCheckedChange = { saveAsBomRecipe = it }
                                )
                                Text("Save as standard Bill of Materials (BOM) recipe for 1-click repetition", style = MaterialTheme.typography.bodySmall)
                            }

                            HeroInput(
                                value = productionNotes,
                                onValueChange = { productionNotes = it },
                                label = { Text("Batch Notes / Operator Instructions") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 2
                            )
                        }
                    }

                    // TAB 1: IMMUTABLE AUDIT TRAIL (PRODUCTION RUNS & INVENTORY MOVEMENTS)
                    1 -> {
                        if (allProductionRuns.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
                                    Text("No production runs logged yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(allProductionRuns) { run ->
                                    HeroCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                                    Text(run.productionCode, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                                Text(dateFormat.format(Date(run.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                            Text(
                                                text = "Consumed: ${run.rawMaterialQtyUsed.toInt()}x ${run.rawMaterialName} ➔ Produced: +${run.targetRetailQtyProduced.toInt()}x ${run.targetRetailProductName}",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Derived Cost: $curr${String.format(Locale.US, "%.2f", run.derivedRetailUnitCost)}", style = MaterialTheme.typography.labelSmall)
                                                Text("Price: $curr${String.format(Locale.US, "%.2f", run.retailSellingPrice)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                                Text("Scrap: ${String.format(Locale.US, "%.1f", run.wasteLossQty)} loss", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                            }
                                            if (run.notes.isNotBlank()) {
                                                Text("Notes: ${run.notes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text("Operator: @${run.operatorUsername}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TAB 2: SAVED BOM RECIPES
                    2 -> {
                        if (allBoms.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
                                    Text("No preset BOM recipes saved", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                    Text("Check 'Save as standard BOM recipe' during a batch run to store it here.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(allBoms) { bom ->
                                    val rawName = allProducts.firstOrNull { it.id == bom.rawMaterialProductId }?.name ?: "Raw #${bom.rawMaterialProductId}"
                                    val targetName = allProducts.firstOrNull { it.id == bom.targetRetailProductId }?.name ?: "Retail #${bom.targetRetailProductId}"
                                    HeroCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text("$rawName ➔ $targetName", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                                Text("Usage: ${bom.rawQuantityNeeded.toInt()} ➔ Yield: ${bom.producedRetailYield.toInt()} (Scrap: ${String.format(Locale.US, "%.1f", bom.wasteLossPercentage)}%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                if (bom.notes.isNotBlank()) {
                                                    Text(bom.notes, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                HeroOutlinedButton(
                                                    onClick = { applyBom(bom) },
                                                    size = HeroSize.Sm,
                                                    text = "Apply"
                                                )
                                                IconButton(
                                                    onClick = {
                                                        viewModel.deleteBomRecipe(bom.id) { success ->
                                                            if (success) Toast.makeText(context, "Recipe deleted", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
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
        },
        confirmButton = {
            if (selectedTabIndex == 0) {
                HeroButton(
                    onClick = {
                        val raw = selectedRawProduct ?: return@HeroButton
                        val target = selectedTargetProduct ?: return@HeroButton

                        val combinedNotes = buildString {
                            if (packagingSizeStr.isNotBlank()) append("[Packaging: ${packagingSizeStr.trim()}] ")
                            if (productionNotes.isNotBlank()) append(productionNotes.trim())
                        }.trim()

                        isExecuting = true
                        viewModel.executeRepackagingProduction(
                            rawMaterialProductId = raw.id,
                            rawMaterialQtyUsed = rawUsage,
                            targetRetailProductId = target.id,
                            targetRetailQtyProduced = targetYield,
                            wasteLossQty = wasteLossQty,
                            retailSellingPrice = retailPrice,
                            saveAsBom = saveAsBomRecipe,
                            notes = combinedNotes
                        ) { success, msg, _ ->
                            isExecuting = false
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (success) {
                                onDismiss()
                            }
                        }
                    },
                    text = if (isExecuting) "Processing Batch..." else "Execute Production Run",
                    icon = Icons.Default.PrecisionManufacturing,
                    enabled = canExecute,
                    color = HeroColor.Primary,
                    modifier = Modifier.testTag("execute_production_btn")
                )
            } else {
                HeroTextButton(onClick = { selectedTabIndex = 0 }, text = "Back to Repack Batch")
            }
        },
        dismissButton = {
            HeroTextButton(onClick = onDismiss, enabled = !isExecuting, text = "Close")
        }
    )
}
