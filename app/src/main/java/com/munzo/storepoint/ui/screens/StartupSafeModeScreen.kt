package com.munzo.storepoint.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munzo.storepoint.data.DatabaseRecoveryManager
import com.munzo.storepoint.util.CrashDiagnosticsManager
import com.munzo.storepoint.util.StartupBootGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Zero-dependency Startup Safe-Mode Recovery Screen with built-in Crash Log Viewer.
 *
 * Deliberately does NOT depend on [com.munzo.storepoint.ui.StorePointViewModel], Room,
 * custom icon shims, or expressive animations so that even if any of those subsystems fail
 * at startup, the operator always lands on a readable, actionable recovery console instead
 * of being kicked back to the Android home screen.
 */
@Composable
fun StartupSafeModeScreen(
    evaluation: StartupBootGuard.LaunchEvaluation,
    onRetryNormalLaunch: () -> Unit,
    onDatabaseResetComplete: (DatabaseRecoveryManager.DatabaseRecoveryPromptState) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var crashHistory by remember { mutableStateOf<List<CrashDiagnosticsManager.CrashRecord>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Latest Trigger, 1 = Persisted Crash History
    var selectedHistoryIdx by remember { mutableIntStateOf(0) }
    var statusNotice by remember { mutableStateOf<String?>(null) }
    var showResetDbDialog by remember { mutableStateOf(false) }
    var isResettingDb by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        crashHistory = withContext(Dispatchers.IO) {
            try {
                CrashDiagnosticsManager.getCrashLogs(context)
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    val deviceSummary = remember { StartupBootGuard.getDeviceSummary() }

    val primaryTraceText = remember(evaluation, crashHistory) {
        when {
            evaluation.lastCrashStackTrace.isNotBlank() -> buildString {
                appendLine("Reason: ${evaluation.reason}")
                appendLine("Exception: ${evaluation.lastCrashException}")
                appendLine("Message: ${evaluation.lastCrashMessage}")
                if (evaluation.lastCrashThread.isNotBlank()) {
                    appendLine("Thread: ${evaluation.lastCrashThread}")
                }
                if (evaluation.lastCrashTimestampMs > 0L) {
                    appendLine("Time: ${StartupBootGuard.formatTimestamp(evaluation.lastCrashTimestampMs)}")
                }
                appendLine("Device: $deviceSummary")
                appendLine()
                append(evaluation.lastCrashStackTrace)
            }
            crashHistory.isNotEmpty() -> CrashDiagnosticsManager.formatSingleCrashReport(crashHistory.first())
            else -> buildString {
                appendLine("Reason: ${evaluation.reason}")
                appendLine("Device: $deviceSummary")
                appendLine()
                appendLine("No full Java stack trace was captured before process termination.")
                appendLine("Common causes: native crash, low memory kill, or interrupted initialization.")
            }
        }
    }

    val activeLogText = if (selectedTab == 0 || crashHistory.isEmpty()) {
        primaryTraceText
    } else {
        val safeIdx = selectedHistoryIdx.coerceIn(0, crashHistory.lastIndex)
        CrashDiagnosticsManager.formatSingleCrashReport(crashHistory[safeIdx])
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("startup_safe_mode_screen"),
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "STOREPOINT SAFE MODE",
                            color = Color(0xFFFCA5A5),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Startup Recovery Console",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Text(
                            text = evaluation.reason,
                            color = Color(0xFFFECACA),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        Text(
                            text = deviceSummary,
                            color = Color(0xFFFCA5A5).copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Inline feedback banner
                statusNotice?.let { notice ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E3A8A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = notice,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }

                // Crash summary card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Triggering Error",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = evaluation.lastCrashException.substringAfterLast('.'),
                            color = Color(0xFFF87171),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (evaluation.lastCrashMessage.isNotBlank()) {
                            Text(
                                text = evaluation.lastCrashMessage,
                                color = Color(0xFFE2E8F0),
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }

                // Log Viewer card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Crash Log Viewer",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${crashHistory.size} saved",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SafeModeTabButton(
                                label = "Latest Startup Error",
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 }
                            )
                            SafeModeTabButton(
                                label = "History (${crashHistory.size})",
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 }
                            )
                        }

                        if (selectedTab == 1 && crashHistory.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                crashHistory.take(6).forEachIndexed { idx, rec ->
                                    val isSelected = idx == selectedHistoryIdx
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) Color(0xFF334155) else Color(0xFF0F172A),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedHistoryIdx = idx }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = rec.exceptionName,
                                                color = if (isSelected) Color(0xFFFCA5A5) else Color(0xFFE2E8F0),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = rec.dateFormatted,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 300.dp)
                                .background(Color(0xFF020617), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = activeLogText,
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("StorePoint Crash Log", activeLogText))
                                        statusNotice = "Crash log copied to clipboard."
                                    } catch (e: Exception) {
                                        statusNotice = "Could not copy log: ${e.message}"
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Copy Log", color = Color.White, fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "StorePoint Safe Mode Crash Log")
                                            putExtra(Intent.EXTRA_TEXT, activeLogText)
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Crash Log"))
                                    } catch (e: Exception) {
                                        statusNotice = "Share unavailable on this device: ${e.message}"
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Share Log", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF334155))

                // Primary recovery actions
                Button(
                    onClick = {
                        StartupBootGuard.clearSafeModeForRetry(context)
                        onRetryNormalLaunch()
                    },
                    enabled = !isResettingDb,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("safe_mode_retry_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Retry Normal Launch", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                OutlinedButton(
                    onClick = { showResetDbDialog = true },
                    enabled = !isResettingDb,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("safe_mode_reset_db_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (isResettingDb) "Quarantining Database..." else "Quarantine & Reset Corrupted Database",
                        color = Color(0xFFFCA5A5),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showResetDbDialog) {
        AlertDialog(
            onDismissRequest = { if (!isResettingDb) showResetDbDialog = false },
            title = { Text("Quarantine & Reset Database?") },
            text = {
                Text(
                    "StorePoint will move the current SQLite database files into a safe quarantine folder, " +
                        "create a fresh database, and open the Backup Restore prompt so you can restore your latest snapshot."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isResettingDb = true
                        showResetDbDialog = false
                        scope.launch {
                            try {
                                val res = DatabaseRecoveryManager.quarantineAndResetDatabase(
                                    context = context,
                                    reason = "Operator triggered database reset from Safe Mode."
                                )
                                StartupBootGuard.clearSafeModeForRetry(context)
                                val prompt = DatabaseRecoveryManager.buildRecoveryPromptState(
                                    context = context,
                                    reason = res.reason,
                                    quarantinePath = res.quarantinedDir?.absolutePath
                                )
                                onDatabaseResetComplete(prompt)
                            } catch (e: Throwable) {
                                isResettingDb = false
                                statusNotice = "Database reset failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Quarantine & Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDbDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SafeModeTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color(0xFF2563EB) else Color(0xFF0F172A),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFF94A3B8),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}
