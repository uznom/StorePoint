package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroButtonVariant
import com.munzo.storepoint.ui.components.HeroCard
import com.munzo.storepoint.ui.components.HeroColor
import com.munzo.storepoint.ui.components.HeroDivider
import com.munzo.storepoint.ui.components.HeroSize
import com.munzo.storepoint.ui.components.HeroTextButton
import com.munzo.storepoint.ui.layout.LocalAdaptiveMetrics
import com.munzo.storepoint.ui.theme.ExpressivePillBadge
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.glassPanel
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Offline Thermal Print Queue & 1-Tap Reprint Last Receipt dialog.
 * Allows cashiers to reprint the most recent receipt instantly or retry queued ESC/POS jobs
 * saved when the Bluetooth/USB/Network printer disconnected or ran out of paper.
 */
@Composable
fun PrintQueueDialog(
    viewModel: StorePointViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val metrics = LocalAdaptiveMetrics.current
    val tactile = rememberTactileFeedback()
    val pendingJobs by viewModel.pendingPrintJobs.collectAsState()
    val lastReceipt by viewModel.lastPrintedReceipt.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    var isBusy by remember { mutableStateOf(false) }
    val timeFmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    val canReprint = lastReceipt != null || allTransactions.isNotEmpty()

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
                        Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Thermal Print Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${pendingJobs.size} queued offline print job(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // 1-Tap Reprint Last Receipt Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Quick Reprint Last Receipt", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = lastReceipt?.label
                                    ?: allTransactions.firstOrNull()?.let { "Receipt #TX-${it.id}" }
                                    ?: "No recent receipt available",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HeroButton(
                            onClick = {
                                if (isBusy) return@HeroButton
                                isBusy = true
                                viewModel.reprintLastReceipt { ok, msg ->
                                    isBusy = false
                                    tactile.perform(if (ok) TactileHapticRole.ConfirmSuccess else TactileHapticRole.WarningReject)
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = canReprint && !isBusy,
                            text = "Reprint",
                            icon = Icons.Default.Print,
                            color = HeroColor.Primary,
                            variant = HeroButtonVariant.Solid,
                            size = HeroSize.Sm,
                            modifier = Modifier.testTag("reprint_last_receipt_btn")
                        )
                    }
                }

                HeroDivider()

                if (pendingJobs.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Print queue is clear. Failed Bluetooth, USB, or Network ESC/POS receipts will automatically appear here for offline retry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pendingJobs, key = { it.id }) { job ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            ExpressivePillBadge(
                                                text = job.jobType,
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                text = job.label,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = "${timeFmt.format(Date(job.createdAt))} • Retries: ${job.retryCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (job.lastError.isNotBlank()) {
                                            Text(
                                                text = job.lastError,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                if (isBusy) return@IconButton
                                                isBusy = true
                                                viewModel.retryPrintJob(job) { ok, msg ->
                                                    isBusy = false
                                                    tactile.perform(if (ok) TactileHapticRole.ConfirmSuccess else TactileHapticRole.WarningReject)
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            enabled = !isBusy
                                        ) {
                                            Icon(Icons.Default.Refresh, "Retry", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(
                                            onClick = { viewModel.deletePrintJob(job.id) },
                                            enabled = !isBusy
                                        ) {
                                            Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
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
                        HeroTextButton(
                            onClick = { viewModel.clearPrintQueue() },
                            text = "Clear Queue",
                            enabled = !isBusy
                        )
                        HeroButton(
                            onClick = {
                                if (isBusy) return@HeroButton
                                isBusy = true
                                viewModel.retryAllPendingPrintJobs { okCount, failCount ->
                                    isBusy = false
                                    tactile.perform(if (failCount == 0) TactileHapticRole.ConfirmSuccess else TactileHapticRole.WarningReject)
                                    Toast.makeText(
                                        context,
                                        "Retried queue: $okCount printed, $failCount remaining.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = !isBusy,
                            text = if (isBusy) "Printing..." else "Retry All (${pendingJobs.size})",
                            icon = Icons.Default.Refresh,
                            color = HeroColor.Primary,
                            variant = HeroButtonVariant.Solid,
                            size = HeroSize.Md
                        )
                    }
                }
            }
        }
    }
}
