package com.munzo.storepoint.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.*
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.theme.*
import com.munzo.storepoint.ui.theme.HeroUITheme
import com.munzo.storepoint.ui.components.*
import com.munzo.storepoint.util.PdfExportUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Analytics and Financial Reporting Page
@Composable
fun AnalyticsTab(
    transactions: List<Transaction>,
    transactionItems: List<com.munzo.storepoint.data.TransactionItem>,
    sessions: List<CashierSession>,
    products: List<Product>,
    curr: String,
    gcashBalance: Double,
    smartLoadBalance: Double = 0.0,
    globeLoadBalance: Double = 0.0,
    storeConfig: StoreConfig? = null,
    viewModel: StorePointViewModel? = null
) {
    val context = LocalContext.current
    val activeSales = remember(transactions) {
        transactions.filter {
            it.status != "VOIDED" && it.status != "CANCELLED" && it.status != "REFUNDED"
        }
    }
    val activeSaleIds = remember(activeSales) { activeSales.map { it.id }.toSet() }
    val activeSaleItems = remember(transactionItems, activeSaleIds) {
        transactionItems.filter { it.transactionId in activeSaleIds }
    }
    val totalRevenue = StorePointRepository.roundMoney(activeSales.sumOf { it.totalAmount })
    val totalProfit = StorePointRepository.roundMoney(activeSaleItems.sumOf { (it.price - it.cost) * it.quantity })
    val avgTransaction = if (activeSales.isNotEmpty()) StorePointRepository.roundMoney(totalRevenue / activeSales.size) else 0.0
    val lowStockItems = products.filter { it.stockCount in 1..5 }
    val outOfStockItems = products.filter { it.stockCount <= 0 }

    // Top-performing products calculation
    val topProducts = remember(activeSaleItems) {
        activeSaleItems
            .groupBy { it.productName }
            .map { (name, items) ->
                val totalQty = items.sumOf { it.quantity }
                val totalSales = StorePointRepository.roundMoney(items.sumOf { it.price * it.quantity })
                val totalProfit = StorePointRepository.roundMoney(items.sumOf { (it.price - it.cost) * it.quantity })
                Triple(name, totalQty, Pair(totalSales, totalProfit))
            }
            .sortedByDescending { it.third.first } // Ranked by sales revenue
            .take(5)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // High-level cards row 1: Revenue & Profits
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpressiveMetricCard(
                modifier = Modifier.weight(1f),
                title = "Daily Gross Sales",
                value = "$curr${String.format(Locale.US, "%.2f", totalRevenue)}",
                icon = Icons.Default.TrendingUp,
                accentColor = MaterialTheme.colorScheme.primary
            )

            ExpressiveMetricCard(
                modifier = Modifier.weight(1f),
                title = "Earned Net Profit",
                value = "$curr${String.format(Locale.US, "%.2f", totalProfit)}",
                icon = Icons.Default.MonetizationOn,
                accentColor = Color(0xFF10B981)
            )
        }

        // High-level cards row 2: Average Basket & Stock Warnings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpressiveMetricCard(
                modifier = Modifier.weight(1f),
                title = "Avg Basket Ticket Size",
                value = "$curr${String.format(Locale.US, "%.2f", avgTransaction)}",
                icon = Icons.Default.ShoppingBasket,
                accentColor = MaterialTheme.colorScheme.secondary
            )

            ExpressiveMetricCard(
                modifier = Modifier.weight(1f),
                title = "Low-Stock Warnings",
                value = "${lowStockItems.size + outOfStockItems.size} items",
                icon = Icons.Default.Warning,
                accentColor = if (outOfStockItems.isNotEmpty()) MaterialTheme.colorScheme.error else Color(0xFFF59E0B),
                subtitle = if (outOfStockItems.isNotEmpty()) "${outOfStockItems.size} out of stock" else "${lowStockItems.size} low stock"
            )
        }

        // Sales Revenue Trend Plot
        Text("Sales Revenue Trend Visualization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        InteractiveSalesTrendChart(activeSales, curr)

        // Top-Performing Product Rankings
        HeroCard(
            modifier = Modifier
                .fillMaxWidth()
                .expressiveGlassCard(cornerRadius = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Top-Performing Product Rankings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Best-selling catalog items ranked by volume & revenue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Leaderboard,
                        contentDescription = "Rankings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (topProducts.isEmpty()) {
                    Text(
                        "No item sales registered yet. Complete register checkouts to see product leaderboards.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    topProducts.forEachIndexed { index, (productName, qtySold, salesProfit) ->
                        val (sales, profit) = salesProfit
                        val rankColor = when (index) {
                            0 -> Color(0xFFF59E0B) // Gold
                            1 -> Color(0xFF94A3B8) // Silver
                            2 -> Color(0xFFD97706) // Bronze
                            else -> MaterialTheme.colorScheme.outline
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = CircleShape,
                                    color = rankColor.copy(alpha = 0.2f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "#${index + 1}",
                                            fontWeight = FontWeight.Black,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = rankColor
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        productName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "$qtySold units sold • Profit: $curr${String.format(Locale.US, "%.2f", profit)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Text(
                                "$curr${String.format(Locale.US, "%.2f", sales)}",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // PDF & CSV Export Engine (PdfExportUtil)
        HeroCard(
            modifier = Modifier
                .fillMaxWidth()
                .expressiveGlassCard(
                    cornerRadius = 24.dp,
                    accentGlow = MaterialTheme.colorScheme.primary
                ),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "PDF & CSV Financial Export Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Export print-ready audit PDFs and spreadsheet CSV sheets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }

                // 1) Sales Summary Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sales Summaries", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text("Daily transactions, payment methods & VAT tax breakdown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroButton(
                            onClick = {
                                storeConfig?.let { cfg ->
                                    PdfExportUtil.exportSalesAndInventoryReportPdf(context, cfg, transactions, products)
                                }
                            },
                            modifier = Modifier.testTag("export_sales_pdf_btn"),
                            text = "PDF",
                            icon = Icons.Default.PictureAsPdf,
                            color = HeroColor.Primary,
                            variant = HeroButtonVariant.Bordered,
                            size = HeroSize.Sm
                        )

                        HeroButton(
                            onClick = {
                                storeConfig?.let { cfg ->
                                    PdfExportUtil.exportSalesSummaryCsv(context, cfg, transactions)
                                }
                            },
                            modifier = Modifier.testTag("export_sales_csv_btn"),
                            text = "CSV",
                            icon = Icons.Default.Description,
                            color = HeroColor.Primary,
                            variant = HeroButtonVariant.Solid,
                            size = HeroSize.Sm
                        )
                    }
                }

                HeroDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 2) Daily Register Journal Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Daily Register Journals", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text("Cashier shift sessions, starting floats, drawer cash counts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroButton(
                            onClick = {
                                storeConfig?.let { cfg ->
                                    PdfExportUtil.exportDailyRegisterJournalPdf(context, cfg, sessions)
                                }
                            },
                            modifier = Modifier.testTag("export_journal_pdf_btn"),
                            text = "PDF",
                            icon = Icons.Default.PictureAsPdf,
                            color = HeroColor.Primary,
                            variant = HeroButtonVariant.Bordered,
                            size = HeroSize.Sm
                        )

                        HeroButton(
                            onClick = {
                                storeConfig?.let { cfg ->
                                    PdfExportUtil.exportDailyRegisterJournalCsv(context, cfg, sessions)
                                }
                            },
                            modifier = Modifier.testTag("export_journal_csv_btn"),
                            text = "CSV",
                            icon = Icons.Default.Description,
                            color = HeroColor.Primary,
                            variant = HeroButtonVariant.Solid,
                            size = HeroSize.Sm
                        )
                    }
                }

                HeroDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 3) Inventory Valuation Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Inventory Valuation Reports", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text("Asset valuation (COGS vs Retail), stock quantities & profit margins", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroButton(
                            onClick = {
                                storeConfig?.let { cfg ->
                                    PdfExportUtil.exportInventoryValuationPdf(context, cfg, products)
                                }
                            },
                            modifier = Modifier.testTag("export_inventory_pdf_btn"),
                            text = "PDF",
                            icon = Icons.Default.PictureAsPdf,
                            color = HeroColor.Primary,
                            variant = HeroButtonVariant.Bordered,
                            size = HeroSize.Sm
                        )

                        HeroButton(
                            onClick = {
                                storeConfig?.let { cfg ->
                                    PdfExportUtil.exportInventoryValuationCsv(context, cfg, products)
                                }
                            },
                            modifier = Modifier.testTag("export_inventory_csv_btn"),
                            text = "CSV",
                            icon = Icons.Default.Description,
                            color = HeroColor.Primary,
                            variant = HeroButtonVariant.Solid,
                            size = HeroSize.Sm
                        )
                    }
                }
            }
        }

        // Digital Service Charges Configuration Card
        var showEditFeesDialog by remember { mutableStateOf(false) }
        var editLoadFee by remember(storeConfig) { mutableStateOf(String.format(Locale.US, "%.2f", storeConfig?.loadServiceFee ?: 2.0)) }
        var editGcashFee by remember(storeConfig) { mutableStateOf(String.format(Locale.US, "%.2f", storeConfig?.gcashServiceFee ?: 10.0)) }
        var editMayaFee by remember(storeConfig) { mutableStateOf(String.format(Locale.US, "%.2f", storeConfig?.mayaServiceFee ?: 10.0)) }
        var editMayaBankFee by remember(storeConfig) { mutableStateOf(String.format(Locale.US, "%.2f", storeConfig?.mayaBankFee ?: 15.0)) }

        HeroCard(
            modifier = Modifier
                .fillMaxWidth()
                .expressiveGlassCard(
                    cornerRadius = 24.dp,
                    accentGlow = MaterialTheme.colorScheme.primary
                ),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PriceChange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Digital Service Fee Configuration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Custom fee tuning for GCash tiers, Maya transfers & prepaid load",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    HeroButton(
                        onClick = {
                            editLoadFee = String.format(Locale.US, "%.2f", storeConfig?.loadServiceFee ?: 2.0)
                            editGcashFee = String.format(Locale.US, "%.2f", storeConfig?.gcashServiceFee ?: 10.0)
                            editMayaFee = String.format(Locale.US, "%.2f", storeConfig?.mayaServiceFee ?: 10.0)
                            editMayaBankFee = String.format(Locale.US, "%.2f", storeConfig?.mayaBankFee ?: 15.0)
                            showEditFeesDialog = true
                        },
                        modifier = Modifier.testTag("edit_digital_fees_btn"),
                        text = "Tune Fees",
                        icon = Icons.Default.Edit,
                        color = HeroColor.Primary,
                        variant = HeroButtonVariant.Flat,
                        size = HeroSize.Sm
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Prepaid Load Fee Pill
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Prepaid Load", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(
                                "$curr${String.format(Locale.US, "%.2f", storeConfig?.loadServiceFee ?: 2.0)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text("per load transaction", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    // GCash Fee Pill
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("GCash Tier Fee", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(
                                "$curr${String.format(Locale.US, "%.2f", storeConfig?.gcashServiceFee ?: 10.0)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text("per ₱1,000 bracket", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    // Maya Fee Pill
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Maya Transfer Fee", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(
                                "$curr${String.format(Locale.US, "%.2f", storeConfig?.mayaServiceFee ?: 10.0)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text("Bank: $curr${String.format(Locale.US, "%.2f", storeConfig?.mayaBankFee ?: 15.0)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        if (showEditFeesDialog) {
            AlertDialog(
                onDismissRequest = { showEditFeesDialog = false },
                title = { Text("Tune Digital Service Charges", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Set default store convenience fees for mobile load, GCash brackets, and Maya transfers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        HeroInput(
                            value = editLoadFee,
                            onValueChange = { editLoadFee = it },
                            label = { Text("Prepaid Load Charge ($curr)") },
                            supportingText = { Text("Default customer surcharge (e.g. ₱2.00 on ₱20 load)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("edit_load_fee_input")
                        )

                        HeroInput(
                            value = editGcashFee,
                            onValueChange = { editGcashFee = it },
                            label = { Text("GCash Service Charge ($curr)") },
                            supportingText = { Text("Base rate per ₱1,000 tier (e.g. ₱10.00)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("edit_gcash_fee_input")
                        )

                        HeroInput(
                            value = editMayaFee,
                            onValueChange = { editMayaFee = it },
                            label = { Text("Maya Service Charge ($curr)") },
                            supportingText = { Text("Base store convenience fee (e.g. ₱10.00)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("edit_maya_fee_input")
                        )

                        HeroInput(
                            value = editMayaBankFee,
                            onValueChange = { editMayaBankFee = it },
                            label = { Text("Maya Cash-In Bank Fee ($curr)") },
                            supportingText = { Text("Partner bank transfer surcharge (e.g. ₱15.00)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("edit_maya_bank_fee_input")
                        )
                    }
                },
                confirmButton = {
                    HeroButton(
                        onClick = {
                            val lFee = editLoadFee.toDoubleOrNull() ?: 2.0
                            val gFee = editGcashFee.toDoubleOrNull() ?: 10.0
                            val mFee = editMayaFee.toDoubleOrNull() ?: 10.0
                            val mbFee = editMayaBankFee.toDoubleOrNull() ?: 15.0
                            viewModel?.updateDigitalServiceFees(lFee, gFee, mFee, mbFee)
                            showEditFeesDialog = false
                        },
                        modifier = Modifier.testTag("save_service_fees_btn"),
                        text = "Save Charges",
                        color = HeroColor.Primary,
                        variant = HeroButtonVariant.Solid,
                        size = HeroSize.Md
                    )
                },
                dismissButton = {
                    HeroTextButton(onClick = { showEditFeesDialog = false }, text = "Cancel")
                }
            )
        }

        // Shifts history audit log list
        Text("Staff Register Shift Sessions Log (${sessions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        HeroCard(
            modifier = Modifier.fillMaxWidth().glassPanel(cornerRadius = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (sessions.isEmpty()) {
                    Text("No shifts recorded in local database yet.", color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(12.dp))
                } else {
                    sessions.take(6).forEach { session ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Cashier: ${session.cashierUsername.uppercase()}", fontWeight = FontWeight.Bold)
                                val stTime = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(session.startTime))
                                val edTime = if (session.endTime != null) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.endTime)) else "Active shift"
                                Text("Time frame: $stTime - $edTime", style = MaterialTheme.typography.labelSmall)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (session.status == "ACTIVE") "SHIFT ACTIVE" else "CLOSED",
                                    color = if (session.status == "ACTIVE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Float start: $curr${session.startingCash}", style = MaterialTheme.typography.labelSmall)
                                if (session.endingCash != null) {
                                    Text("Drawer end: $curr${session.endingCash}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        HeroDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

// Canvas-drawn sales trend diagram
@Composable
fun InteractiveSalesTrendChart(transactions: List<Transaction>, curr: String) {
    val rawPrices = transactions.map { it.totalAmount }
    val dataPoints = if (rawPrices.isEmpty()) {
        listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f)
    } else if (rawPrices.size < 5) {
        rawPrices.map { it.toFloat() } + List(5 - rawPrices.size) { 0f }
    } else {
        rawPrices.take(10).map { it.toFloat() }
    }

    val maxVal = (dataPoints.maxOrNull() ?: 100f).coerceAtLeast(10f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .expressiveGlassCard(
                cornerRadius = 24.dp,
                accentGlow = MaterialTheme.colorScheme.primary
            )
            .padding(16.dp)
    ) {
        val chartAccent = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val spacing = width / (dataPoints.size - 1).coerceAtLeast(1)

            val path = Path()
            val points = mutableListOf<Offset>()

            dataPoints.forEachIndexed { i, value ->
                val x = i * spacing
                val y = height - (value / maxVal) * (height - 30f) - 15f
                points.add(Offset(x, y))

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    val prev = points[i - 1]
                    val controlX = (prev.x + x) / 2
                    path.cubicTo(controlX, prev.y, controlX, y, x, y)
                }
            }

            if (dataPoints.isNotEmpty()) {
                points.forEach { (x, y) ->
                    drawCircle(
                        color = chartAccent,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                }

                drawPath(
                    path = path,
                    color = chartAccent,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        // Overlay descriptions
        Text(
            "Max plot value: $curr${Math.round(maxVal)}",
            modifier = Modifier.align(Alignment.TopEnd),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            "Time ->",
            modifier = Modifier.align(Alignment.BottomEnd),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
