package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.munzo.storepoint.data.ShiftXReadingSummary
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroButtonVariant
import com.munzo.storepoint.ui.components.HeroCard
import com.munzo.storepoint.ui.components.HeroColor
import com.munzo.storepoint.ui.components.HeroDivider
import com.munzo.storepoint.ui.components.HeroSize
import com.munzo.storepoint.ui.components.HeroTextButton
import com.munzo.storepoint.ui.layout.LocalAdaptiveMetrics
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.glassPanel
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Non-resetting Mid-Shift X-Reading dialog showing live shift totals, tender breakdown,
 * and expected drawer cash formula with 1-tap thermal printing.
 */
@Composable
fun ShiftXReadingDialog(
    viewModel: StorePointViewModel,
    curr: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val metrics = LocalAdaptiveMetrics.current
    val tactile = rememberTactileFeedback()
    var summary by remember { mutableStateOf<ShiftXReadingSummary?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isPrinting by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    fun loadReading() {
        isLoading = true
        viewModel.computeActiveShiftXReading { result ->
            summary = result
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadReading() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        HeroCard(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = metrics.dialogMaxWidth)
                .heightIn(max = metrics.heightDp * metrics.dialogMaxHeightFraction)
                .fillMaxWidth()
                .glassPanel(cornerRadius = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(metrics.cardPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Mid-Shift X-Reading", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Live audit preview • Does NOT close shift",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                when {
                    isLoading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    summary == null -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "No active shift session found. Start a shift to generate a live X-Reading.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    else -> {
                        val s = summary!!
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                XReadingRow("Session #", "SES-${s.sessionId}")
                                XReadingRow("Cashier", s.cashierUsername)
                                XReadingRow("Shift Started", dateFmt.format(Date(s.shiftStartTime)))
                                XReadingRow("Transactions", "${s.transactionCount}")
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("SALES & TENDERS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                XReadingRow("Gross Sales", "$curr${fmt2(s.grossSales)}", bold = true)
                                XReadingRow("Line Discounts", "-$curr${fmt2(s.totalDiscounts)}")
                                XReadingRow("VAT / Tax Included", "$curr${fmt2(s.totalTax)}")
                                HeroDivider(modifier = Modifier.padding(vertical = 4.dp))
                                XReadingRow("Cash Sales", "$curr${fmt2(s.cashSales)}")
                                XReadingRow("GCash Sales", "$curr${fmt2(s.gcashSales)}")
                                XReadingRow("Maya Sales", "$curr${fmt2(s.mayaSales)}")
                                if (s.cardSales > 0.0) XReadingRow("Card Sales", "$curr${fmt2(s.cardSales)}")
                                XReadingRow("Suki Tab (Credit)", "$curr${fmt2(s.sukiCreditSales)}")
                                if (s.otherDigitalSales > 0.0) XReadingRow("Other Digital", "$curr${fmt2(s.otherDigitalSales)}")
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("CASH DRAWER RECONCILIATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                XReadingRow("Starting Float", "$curr${fmt2(s.startingCash)}")
                                XReadingRow("+ Cash Sales", "$curr${fmt2(s.cashSales)}")
                                XReadingRow("+ Cash-In / Suki Repay", "$curr${fmt2(s.drawerCashIn)}")
                                XReadingRow("- Cash Payouts", "-$curr${fmt2(s.drawerPayouts)}")
                                XReadingRow("- Cash Refunds", "-$curr${fmt2(s.cashRefunds)}")
                                HeroDivider(modifier = Modifier.padding(vertical = 4.dp))
                                XReadingRow(
                                    label = "EXPECTED CASH IN DRAWER",
                                    value = "$curr${fmt2(s.expectedCashInDrawer)}",
                                    bold = true,
                                    highlight = true
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HeroTextButton(
                                onClick = { loadReading() },
                                text = "Refresh",
                                icon = Icons.Default.Refresh
                            )
                            HeroButton(
                                onClick = {
                                    if (isPrinting) return@HeroButton
                                    isPrinting = true
                                    viewModel.printXReadingHardware(s) { ok, msg ->
                                        isPrinting = false
                                        tactile.perform(if (ok) TactileHapticRole.ConfirmSuccess else TactileHapticRole.WarningReject)
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isPrinting,
                                text = if (isPrinting) "Printing..." else "Print X-Reading",
                                icon = Icons.Default.Print,
                                color = HeroColor.Primary,
                                variant = HeroButtonVariant.Solid,
                                size = HeroSize.Md,
                                modifier = Modifier.testTag("print_x_reading_btn")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XReadingRow(
    label: String,
    value: String,
    bold: Boolean = false,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (highlight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = if (highlight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun fmt2(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)
