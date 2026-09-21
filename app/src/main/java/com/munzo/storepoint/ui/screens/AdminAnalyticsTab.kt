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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.*
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Analytics and Shifts Page
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
    val totalRevenue = transactions.sumOf { it.totalAmount }
    val totalProfit = transactionItems.sumOf { (it.price - it.cost) * it.quantity }
    val avgTransaction = if (transactions.isNotEmpty()) totalRevenue / transactions.size else 0.0
    val lowStockItems = products.filter { it.stockCount in 1..5 }
    val outOfStockItems = products.filter { it.stockCount <= 0 }

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
                title = "Daily Sales Revenue",
                value = "$curr${String.format(Locale.US, "%.2f", totalRevenue)}",
                icon = Icons.Default.TrendingUp,
                accentColor = MaterialTheme.colorScheme.primary
            )

            ExpressiveMetricCard(
                modifier = Modifier.weight(1f),
                title = "Earned Net Profit",
                value = "$curr${String.format(Locale.US, "%.2f", totalProfit)}",
                icon = Icons.Default.MonetizationOn,
                accentColor = MaterialTheme.colorScheme.primary
            )
        }

        // High-level cards row 2: Average Basket & Stock Warnings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpressiveMetricCard(
                modifier = Modifier.weight(1f),
                title = "Avg Basket Ticket",
                value = "$curr${String.format(Locale.US, "%.2f", avgTransaction)}",
                icon = Icons.Default.ShoppingBasket,
                accentColor = MaterialTheme.colorScheme.secondary
            )

            ExpressiveMetricCard(
                modifier = Modifier.weight(1f),
                title = "Stock Warnings",
                value = "${lowStockItems.size + outOfStockItems.size} items",
                icon = Icons.Default.Warning,
                accentColor = if (outOfStockItems.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                subtitle = if (outOfStockItems.isNotEmpty()) "${outOfStockItems.size} out of stock" else null
            )
        }

        // High-fidelity GCash System Wallet Retailing balance card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .expressiveGlassCard(
                    cornerRadius = 24.dp,
                    accentGlow = MaterialTheme.colorScheme.primary
                ),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Wallet Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "GCash Retailing Balance",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "$curr${String.format(Locale.getDefault(), "%.2f", gcashBalance)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                ExpressivePillBadge(
                    text = "REGISTERED WALLET",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Prepaid Load Wallets (Smart/TNT and Globe/TM with 2% Rebate)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Smart / TNT Load Wallet Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .expressiveGlassCard(
                        cornerRadius = 20.dp,
                        accentGlow = MaterialTheme.colorScheme.primary
                    ),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        ExpressivePillBadge(
                            text = "2% REBATE",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Smart / TNT Load Wallet",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$curr${String.format(Locale.getDefault(), "%.2f", smartLoadBalance)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Globe / TM Load Wallet Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .expressiveGlassCard(
                        cornerRadius = 20.dp,
                        accentGlow = MaterialTheme.colorScheme.secondary
                    ),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SimCard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        ExpressivePillBadge(
                            text = "2% REBATE",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Globe / TM Load Wallet",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$curr${String.format(Locale.getDefault(), "%.2f", globeLoadBalance)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
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

        Card(
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
                                text = "Digital Service Charges",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Default store service fees for Load, GCash & Maya",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            editLoadFee = String.format(Locale.US, "%.2f", storeConfig?.loadServiceFee ?: 2.0)
                            editGcashFee = String.format(Locale.US, "%.2f", storeConfig?.gcashServiceFee ?: 10.0)
                            editMayaFee = String.format(Locale.US, "%.2f", storeConfig?.mayaServiceFee ?: 10.0)
                            editMayaBankFee = String.format(Locale.US, "%.2f", storeConfig?.mayaBankFee ?: 15.0)
                            showEditFeesDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit Charges", style = MaterialTheme.typography.labelMedium)
                    }
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
                            Text("GCash Fee", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(
                                "$curr${String.format(Locale.US, "%.2f", storeConfig?.gcashServiceFee ?: 10.0)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text("per ₱1,000 tier", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    // Maya Fee Pill
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Maya Fee", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(
                                "$curr${String.format(Locale.US, "%.2f", storeConfig?.mayaServiceFee ?: 10.0)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text("Bank fee: $curr${String.format(Locale.US, "%.2f", storeConfig?.mayaBankFee ?: 15.0)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        if (showEditFeesDialog) {
            AlertDialog(
                onDismissRequest = { showEditFeesDialog = false },
                title = { Text("Configure Digital Service Charges", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Set the default store convenience fees for digital services. Cashiers can also adjust fees on the POS screen if needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        OutlinedTextField(
                            value = editLoadFee,
                            onValueChange = { editLoadFee = it },
                            label = { Text("Prepaid Load Charge ($curr)") },
                            supportingText = { Text("Default customer surcharge (e.g. ₱2.00 on ₱20 load)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editGcashFee,
                            onValueChange = { editGcashFee = it },
                            label = { Text("GCash Service Charge ($curr)") },
                            supportingText = { Text("Base rate per ₱1,000 tier (e.g. ₱10.00)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editMayaFee,
                            onValueChange = { editMayaFee = it },
                            label = { Text("Maya Service Charge ($curr)") },
                            supportingText = { Text("Base store convenience fee (e.g. ₱10.00)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editMayaBankFee,
                            onValueChange = { editMayaBankFee = it },
                            label = { Text("Maya Cash-In Bank Fee ($curr)") },
                            supportingText = { Text("Maya/partner bank transfer surcharge (e.g. ₱15.00)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val lFee = editLoadFee.toDoubleOrNull() ?: 2.0
                            val gFee = editGcashFee.toDoubleOrNull() ?: 10.0
                            val mFee = editMayaFee.toDoubleOrNull() ?: 10.0
                            val mbFee = editMayaBankFee.toDoubleOrNull() ?: 15.0
                            viewModel?.updateDigitalServiceFees(lFee, gFee, mFee, mbFee)
                            showEditFeesDialog = false
                        }
                    ) {
                        Text("Save Charges")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditFeesDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Custom canvas graph diagram
        Text("Interactive Sales revenue curve (Daily sales trend plot)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        InteractiveSalesTrendChart(transactions, curr)

        // Shifts history audit log list
        Text("Staff register Shift sessions log (${sessions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth().glassPanel(cornerRadius = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

// Canvas-drawn sales trend diagram
@Composable
fun InteractiveSalesTrendChart(transactions: List<Transaction>, curr: String) {
    // Generate simple simulated hourly or daily data from past sales
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
