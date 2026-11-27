package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroButtonVariant
import com.munzo.storepoint.ui.components.HeroCard
import com.munzo.storepoint.ui.components.HeroColor
import com.munzo.storepoint.ui.components.HeroDivider
import com.munzo.storepoint.ui.components.HeroSize
import com.munzo.storepoint.ui.layout.LocalAdaptiveMetrics
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.glassPanel
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.util.CatalogCsvImportPreview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Offline CSV Bulk Catalog Export & Import dialog using Android Storage Access Framework (SAF).
 * Performs a dry-run validation preview (Insert / Update / Skipped) before committing atomically to SQLite.
 */
@Composable
fun AdminCsvImportDialog(
    viewModel: StorePointViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val metrics = LocalAdaptiveMetrics.current
    val tactile = rememberTactileFeedback()
    var preview by remember { mutableStateOf<CatalogCsvImportPreview?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                val csv = viewModel.buildCatalogCsvString()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                }
                tactile.perform(TactileHapticRole.ConfirmSuccess)
                Toast.makeText(context, "Catalog CSV exported successfully.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                tactile.perform(TactileHapticRole.WarningReject)
                Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                } ?: ""
                selectedFileName = uri.lastPathSegment ?: "catalog.csv"
                viewModel.previewCatalogCsv(content) { parsed ->
                    preview = parsed
                }
            } catch (e: Exception) {
                tactile.perform(TactileHapticRole.WarningReject)
                Toast.makeText(context, "Unable to read CSV: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

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
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Offline CSV Catalog Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Bulk edit inventory in Excel or Google Sheets offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Export Section
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1. Export Current Catalog", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Saves all products with columns: name, barcode, category, price, cost, stock, base_uom, expiration_date, quick_sell_pinned.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HeroButton(
                            onClick = {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                exportLauncher.launch("storepoint_catalog_$stamp.csv")
                            },
                            text = "Export Catalog CSV",
                            icon = Icons.Default.Download,
                            color = HeroColor.Primary,
                            variant = HeroButtonVariant.Bordered,
                            size = HeroSize.Md,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("csv_export_btn")
                        )
                    }
                }

                // Import Section
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("2. Import & Upsert CSV", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Matches existing items by barcode or product name. Missing categories are created automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HeroButton(
                            onClick = {
                                importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                            },
                            text = if (selectedFileName == null) "Choose CSV File" else "Choose Different CSV",
                            icon = Icons.Default.UploadFile,
                            color = HeroColor.Primary,
                            variant = HeroButtonVariant.Flat,
                            size = HeroSize.Md,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("csv_pick_file_btn")
                        )
                    }
                }

                // Dry-Run Preview Card
                preview?.let { p ->
                    HeroDivider()
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Validation Preview (${selectedFileName ?: "CSV"})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text("• New products to insert: ${p.rowsToInsert.size}", style = MaterialTheme.typography.bodySmall)
                            Text("• Existing products to update: ${p.rowsToUpdate.size}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "• Skipped invalid rows: ${p.skippedRows.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (p.skippedRows.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )

                            if (p.skippedRows.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    p.skippedRows.take(5).forEach { skip ->
                                        Text(
                                            text = "Row ${skip.rowNumber}: ${skip.reason}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    if (p.skippedRows.size > 5) {
                                        Text(
                                            text = "...and ${p.skippedRows.size - 5} more skipped rows",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HeroButton(
                        onClick = {
                            if (isImporting || p.validRows.isEmpty()) return@HeroButton
                            isImporting = true
                            viewModel.commitCatalogCsvImport(p.validRows) { ok, msg, _ ->
                                isImporting = false
                                tactile.perform(if (ok) TactileHapticRole.ConfirmSuccess else TactileHapticRole.WarningReject)
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                if (ok) onDismiss()
                            }
                        },
                        enabled = p.validRows.isNotEmpty() && !isImporting,
                        text = if (isImporting) "Importing..." else "Commit Atomic Import (${p.validRows.size} items)",
                        icon = Icons.Default.CheckCircle,
                        color = HeroColor.Primary,
                        variant = HeroButtonVariant.Solid,
                        size = HeroSize.Lg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("csv_commit_import_btn")
                    )
                }
            }
        }
    }
}
