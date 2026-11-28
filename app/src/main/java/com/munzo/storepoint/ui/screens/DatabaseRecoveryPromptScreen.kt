package com.munzo.storepoint.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munzo.storepoint.data.DatabaseRecoveryManager
import com.munzo.storepoint.ui.StorePointViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Guided recovery prompt displayed immediately after [DatabaseRecoveryManager] detects
 * a corrupted local database, safely quarantines the damaged SQLite files, and initializes
 * a clean v20 database.
 */
@Composable
fun DatabaseRecoveryPromptScreen(
    prompt: DatabaseRecoveryManager.DatabaseRecoveryPromptState,
    viewModel: StorePointViewModel,
    onContinueFresh: () -> Unit,
    onRestoreSucceeded: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var passphrase by remember { mutableStateOf("") }
    var isRestoring by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isErrorFeedback by remember { mutableStateOf(false) }
    var pendingImportedJson by remember { mutableStateOf<String?>(null) }
    var pendingImportRequiresPassword by remember { mutableStateOf(false) }

    fun runRestore(jsonContent: String, password: String) {
        isRestoring = true
        feedbackMessage = null
        isErrorFeedback = false
        viewModel.restoreDatabaseBackup(
            jsonContent = jsonContent,
            passphrase = password,
            onResult = { summary ->
                isRestoring = false
                if (summary.success) {
                    feedbackMessage = summary.message
                    isErrorFeedback = false
                    onRestoreSucceeded()
                } else {
                    feedbackMessage = summary.message
                    isErrorFeedback = true
                }
            }
        )
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }
                if (content.isNullOrBlank()) {
                    feedbackMessage = "Selected backup file is empty or unreadable."
                    isErrorFeedback = true
                    return@launch
                }
                val encrypted = try {
                    JSONObject(content).optInt("enc", 0) == 1
                } catch (_: Exception) {
                    false
                }
                if (encrypted && passphrase.isBlank()) {
                    pendingImportedJson = content
                    pendingImportRequiresPassword = true
                    feedbackMessage = "This backup is password-protected. Enter the backup password below and tap Restore Imported File."
                    isErrorFeedback = false
                } else {
                    pendingImportedJson = null
                    pendingImportRequiresPassword = false
                    runRestore(content, passphrase)
                }
            } catch (e: Exception) {
                feedbackMessage = "Could not read backup file: ${e.localizedMessage ?: e.javaClass.simpleName}"
                isErrorFeedback = true
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("database_recovery_prompt_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "DATABASE REPAIRED",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            letterSpacing = 1.1.sp
                        )
                        Text(
                            text = "Corrupted Database Was Reset Safely",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "StorePoint detected an unreadable database during startup, moved the damaged files into quarantine, and initialized a clean database so the app can run safely.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Diagnostic: ${prompt.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
                }

                feedbackMessage?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isErrorFeedback) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isErrorFeedback) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                val latestBackup = prompt.latestBackup
                if (latestBackup != null) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Latest Local Backup Found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${latestBackup.storeName} · ${latestBackup.dateTime}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!latestBackup.isEncrypted) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "${latestBackup.productsCount} products",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${latestBackup.transactionsCount} sales",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${latestBackup.usersCount} staff",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (latestBackup.isEncrypted || pendingImportRequiresPassword) {
                                OutlinedTextField(
                                    value = passphrase,
                                    onValueChange = { passphrase = it },
                                    label = { Text("Backup Password") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        val content = withContext(Dispatchers.IO) {
                                            try {
                                                latestBackup.file.readText(Charsets.UTF_8)
                                            } catch (_: Exception) {
                                                null
                                            }
                                        }
                                        if (content.isNullOrBlank()) {
                                            feedbackMessage = "Local snapshot could not be read."
                                            isErrorFeedback = true
                                        } else {
                                            runRestore(content, passphrase)
                                        }
                                    }
                                },
                                enabled = !isRestoring,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("recovery_restore_latest_btn"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = if (isRestoring) "Restoring Backup..." else "Restore Latest Backup",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else if (pendingImportRequiresPassword) {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Backup Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (pendingImportedJson != null) {
                    Button(
                        onClick = {
                            val raw = pendingImportedJson ?: return@Button
                            runRestore(raw, passphrase)
                        },
                        enabled = !isRestoring && passphrase.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Restore Imported File", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = { openBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    enabled = !isRestoring,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("recovery_import_file_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Import Backup File (.json)")
                }

                TextButton(
                    onClick = onContinueFresh,
                    enabled = !isRestoring,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recovery_start_fresh_btn")
                ) {
                    Text("Start Fresh (Keep Clean Database)")
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
