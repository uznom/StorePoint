package com.munzo.storepoint.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.ui.BarcodeScannerDialog
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.ExpressiveButton
import com.munzo.storepoint.ui.components.ExpressiveButtonSize
import com.munzo.storepoint.ui.components.ExpressiveButtonVariant
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroButtonVariant
import com.munzo.storepoint.ui.components.HeroCard
import com.munzo.storepoint.ui.components.HeroColor
import com.munzo.storepoint.ui.components.HeroFilledTonalButton
import com.munzo.storepoint.ui.components.HeroInput
import com.munzo.storepoint.ui.components.HeroSize
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.elasticValuePop
import com.munzo.storepoint.ui.theme.glassPanel
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AdminStockAuditPane(
    products: List<Product>,
    auditedItems: SnapshotStateMap<Int, Int>,
    viewModel: StorePointViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tactile = rememberTactileFeedback()
    var showScannerDialog by remember { mutableStateOf(false) }
    var manualAuditInput by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val shareAuditReport = {
        shareStocktakeAuditReport(context, products, auditedItems)
    }
    val syncAllAudited = {
        var syncedCount = 0
        auditedItems.forEach { (id, checkedCount) ->
            val p = products.find { it.id == id }
            if (p != null && p.stockCount != checkedCount) {
                viewModel.saveProduct(p.copy(stockCount = checkedCount))
                syncedCount++
            }
        }
        if (syncedCount > 0) {
            tactile.perform(TactileHapticRole.ConfirmSuccess)
            Toast.makeText(context, "Bulk corrected $syncedCount discrepancy records in database!", Toast.LENGTH_LONG).show()
        } else {
            tactile.perform(TactileHapticRole.Tap)
            Toast.makeText(context, "All audited items are already synchronized!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeroCard(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(cornerRadius = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isCompactConsole = maxWidth < 540.dp
                    if (isCompactConsole) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Inventory, null, tint = MaterialTheme.colorScheme.primary)
                                Text("Local Audit Console", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            if (auditedItems.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HeroFilledTonalButton(
                                        onClick = shareAuditReport,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("audit_share_report_btn"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Share, null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Share Report", style = MaterialTheme.typography.labelSmall)
                                    }

                                    HeroButton(
                                        onClick = syncAllAudited,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("audit_sync_all_btn"),
                                        text = "Correct All",
                                        icon = Icons.Default.Sync,
                                        color = HeroColor.Success,
                                        variant = HeroButtonVariant.Solid,
                                        size = HeroSize.Sm
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Inventory, null, tint = MaterialTheme.colorScheme.primary)
                                Text("Local Audit Console", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            if (auditedItems.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    HeroFilledTonalButton(
                                        onClick = shareAuditReport,
                                        modifier = Modifier.testTag("audit_share_report_btn"),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Share, null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Share Report", style = MaterialTheme.typography.labelSmall)
                                    }

                                    ExpressiveButton(
                                        onClick = syncAllAudited,
                                        modifier = Modifier.testTag("audit_sync_all_btn"),
                                        label = "Sync All",
                                        icon = Icons.Default.Sync,
                                        variant = ExpressiveButtonVariant.FILLED,
                                        size = ExpressiveButtonSize.XS
                                    )
                                }
                            }
                        }
                    }
                }

                // Scanner & Manual Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        val matchedSuggestions = remember(manualAuditInput, products) {
                            if (manualAuditInput.isBlank()) {
                                emptyList()
                            } else {
                                products.filter { p ->
                                    p.name.contains(manualAuditInput, ignoreCase = true) ||
                                        p.barcode.contains(manualAuditInput, ignoreCase = true)
                                }.take(5)
                            }
                        }

                        HeroInput(
                            value = manualAuditInput,
                            onValueChange = {
                                manualAuditInput = it
                                isDropdownExpanded = true
                            },
                            label = { Text("Search Item / Barcode") },
                            placeholder = { Text("Enter SKU, barcode, or item name") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("audit_barcode_field"),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { showScannerDialog = true }) {
                                    Icon(Icons.Default.QrCodeScanner, "Launch Audit Scanner", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )

                        if (isDropdownExpanded && matchedSuggestions.isNotEmpty()) {
                            DropdownMenu(
                                expanded = isDropdownExpanded,
                                onDismissRequest = { isDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                matchedSuggestions.forEach { prod ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(prod.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    "Barcode: ${prod.barcode.ifEmpty { "None" }} • Stock: ${prod.stockCount}",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        },
                                        onClick = {
                                            val current = auditedItems[prod.id] ?: 0
                                            auditedItems[prod.id] = current + 1
                                            tactile.perform(TactileHapticRole.ScanSuccess)
                                            viewModel.playBeep()
                                            manualAuditInput = ""
                                            isDropdownExpanded = false
                                            Toast.makeText(context, "${prod.name} registered (+1)", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            if (manualAuditInput.isNotBlank()) {
                                val match = products.find {
                                    it.barcode.equals(manualAuditInput.trim(), ignoreCase = true) ||
                                        it.name.contains(manualAuditInput, ignoreCase = true)
                                }
                                if (match != null) {
                                    val current = auditedItems[match.id] ?: 0
                                    auditedItems[match.id] = current + 1
                                    tactile.perform(TactileHapticRole.ScanSuccess)
                                    viewModel.playBeep()
                                    manualAuditInput = ""
                                    Toast.makeText(context, "Added ${match.name} to audit sheet (+1)", Toast.LENGTH_SHORT).show()
                                } else {
                                    tactile.perform(TactileHapticRole.WarningReject)
                                    Toast.makeText(context, "No matching product SKU or name found.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Icon(Icons.Default.Add, "Search & Add to Worksheet")
                    }
                }

                // Summary Compact Counter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val totalCheckedSize = auditedItems.size
                    val perfectMatchSize = auditedItems.count { (id, checkedQty) ->
                        products.find { it.id == id }?.stockCount == checkedQty
                    }
                    val discrepancySize = totalCheckedSize - perfectMatchSize

                    AuditSummaryCounterCard(
                        label = "Audited Items",
                        value = "$totalCheckedSize / ${products.size}",
                        popKey = totalCheckedSize,
                        valueColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    AuditSummaryCounterCard(
                        label = "In Sync",
                        value = "$perfectMatchSize",
                        popKey = perfectMatchSize,
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    AuditSummaryCounterCard(
                        label = "Discrepancies",
                        value = "$discrepancySize",
                        popKey = discrepancySize,
                        valueColor = if (discrepancySize > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Text(
            text = "Audit Worksheet Progress (${auditedItems.size} items monitored)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (auditedItems.isEmpty()) {
                item {
                    HeroCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Empty Audit Worksheet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Scan a product's barcode with the camera or search using the text-field above to begin matching physical inventory stocks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(auditedItems.keys.toList(), key = { it }) { productId ->
                    val item = products.find { it.id == productId }
                    val physicalQty = auditedItems[productId] ?: 0
                    if (item != null) {
                        AuditWorksheetRow(
                            item = item,
                            physicalQty = physicalQty,
                            onDecrement = {
                                if (physicalQty > 0) {
                                    tactile.perform(TactileHapticRole.CartAdjust)
                                    auditedItems[productId] = physicalQty - 1
                                }
                            },
                            onIncrement = {
                                tactile.perform(TactileHapticRole.CartAdjust)
                                auditedItems[productId] = physicalQty + 1
                            },
                            onCorrect = {
                                tactile.perform(TactileHapticRole.ConfirmSuccess)
                                viewModel.saveProduct(item.copy(stockCount = physicalQty))
                                Toast.makeText(context, "${item.name} stocks corrected in DB!", Toast.LENGTH_SHORT).show()
                            },
                            onRemove = {
                                tactile.perform(TactileHapticRole.Tap)
                                auditedItems.remove(productId)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showScannerDialog) {
        BarcodeScannerDialog(
            products = products,
            title = "Inventory Audit Scanner",
            instruction = "Scan product barcodes to check if physical stacks match digital store stock counts.",
            onBarcodeScanned = { barcode ->
                val matched = products.find { it.barcode.trim().equals(barcode.trim(), ignoreCase = true) }
                if (matched != null) {
                    val currentQty = auditedItems[matched.id] ?: 0
                    auditedItems[matched.id] = currentQty + 1
                    tactile.perform(TactileHapticRole.ScanSuccess)
                    viewModel.playBeep()
                    Toast.makeText(context, "Matched: ${matched.name}. Prepped in Worksheet.", Toast.LENGTH_SHORT).show()
                } else {
                    tactile.perform(TactileHapticRole.WarningReject)
                    Toast.makeText(context, "Opps! No catalog item found with barcode '$barcode'", Toast.LENGTH_LONG).show()
                }
                showScannerDialog = false
            },
            onDismiss = { showScannerDialog = false }
        )
    }
}

@Composable
private fun AuditSummaryCounterCard(
    label: String,
    value: String,
    popKey: Int,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    HeroCard(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                modifier = Modifier.elasticValuePop(popKey)
            )
        }
    }
}

@Composable
private fun AuditWorksheetRow(
    item: Product,
    physicalQty: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onCorrect: () -> Unit,
    onRemove: () -> Unit
) {
    val systemQty = item.stockCount
    val variance = physicalQty - systemQty
    val isMatch = variance == 0

    HeroCard(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(cornerRadius = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMatch) Color.Transparent else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text("System Registered Stock: $systemQty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

                val badgeBg = when {
                    isMatch -> MaterialTheme.colorScheme.primaryContainer
                    variance > 0 -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                }
                val badgeFg = when {
                    isMatch -> MaterialTheme.colorScheme.onPrimaryContainer
                    variance > 0 -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onErrorContainer
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                isMatch -> Icons.Default.CheckCircle
                                variance > 0 -> Icons.Default.ArrowUpward
                                else -> Icons.Default.Warning
                            },
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = badgeFg
                        )
                        Text(
                            text = when {
                                isMatch -> "DB Match"
                                variance > 0 -> "Surplus (+$variance)"
                                else -> "Shortage ($variance)"
                            },
                            color = badgeFg,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onDecrement,
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Remove, "Decrease audited quantity", modifier = Modifier.size(16.dp))
                }

                Text(
                    text = "$physicalQty",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .widthIn(min = 28.dp)
                        .elasticValuePop(physicalQty),
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = onIncrement,
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Add, "Increase audited quantity", modifier = Modifier.size(16.dp))
                }
            }

            if (!isMatch) {
                ExpressiveButton(
                    onClick = onCorrect,
                    modifier = Modifier.testTag("correct_stock_btn_${item.id}"),
                    label = "Correct",
                    icon = Icons.Default.Sync,
                    variant = ExpressiveButtonVariant.TONAL,
                    size = ExpressiveButtonSize.XS,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            } else {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, "Remove from monitor list", tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

private fun shareStocktakeAuditReport(
    context: Context,
    products: List<Product>,
    auditedItems: Map<Int, Int>
) {
    val report = StringBuilder()
    report.append("--- STOREPOINT STOCKTAKE AUDIT ---\n")
    report.append("Date Conducted: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}\n\n")

    var totalChecked = 0
    var discrepancies = 0
    auditedItems.forEach { (id, checkedCount) ->
        val p = products.find { it.id == id }
        if (p != null) {
            totalChecked++
            val variance = checkedCount - p.stockCount
            if (variance != 0) {
                discrepancies++
                report.append("• ${p.name}\n")
                report.append("  System: ${p.stockCount} | Scanned: $checkedCount\n")
                report.append("  Variance: ${if (variance > 0) "+$variance" else "$variance"}\n\n")
            }
        }
    }

    report.append("Summary metrics:\n")
    report.append("- Items Checked: $totalChecked\n")
    report.append("- Discrepancy Alerts: $discrepancies\n")
    report.append("-----------------------------\n")

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Inventory Audit Log")
        putExtra(Intent.EXTRA_TEXT, report.toString())
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Inventory Audit Report"))
}
