package com.munzo.storepoint.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munzo.storepoint.ui.theme.ExpressiveButtonShape
import com.munzo.storepoint.ui.theme.ExpressiveSectionHeader
import com.munzo.storepoint.ui.theme.glassPanel
import com.munzo.storepoint.ui.theme.tactileBounce
import com.munzo.storepoint.util.APP_VERSION

import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.platform.LocalContext
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.util.AppUpdateInfo
import com.munzo.storepoint.util.UpdateDownloadState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: StorePointViewModel? = null,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About StorePoint", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("about_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        AboutScreenContent(
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Composable
fun AboutScreenContent(
    viewModel: StorePointViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var privacyExpanded by remember { mutableStateOf(false) }
    var termsExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Updater states
    val isCheckingUpdate = viewModel?.updateCheckInProgress?.collectAsState()?.value ?: false
    val updateInfo = viewModel?.updateInfo?.collectAsState()?.value
    val updateError = viewModel?.updateErrorMessage?.collectAsState()?.value
    val downloadState = viewModel?.updateDownloadState?.collectAsState()?.value ?: UpdateDownloadState.Idle

    var showUpdateDialog by remember { mutableStateOf(false) }
    var currentVersion = APP_VERSION

    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- HEADER BRAND LOGO CARD ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(84.dp)
                        .tactileBounce(),
                    shape = ExpressiveButtonShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Store",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Point",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                Text(
                    text = "StorePoint POS",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Version $currentVersion • Turbo Edition (Max Uncapped RAM)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // In-App Updater Button
                if (viewModel != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FilledTonalButton(
                        onClick = {
                            viewModel.checkForAppUpdates(currentVersion) { info ->
                                showUpdateDialog = true
                            }
                        },
                        enabled = !isCheckingUpdate,
                        shape = CircleShape,
                        modifier = Modifier.testTag("check_for_updates_button")
                    ) {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking GitHub Releases...")
                    } else {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check for Updates")
                    }
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // --- DEVELOPER GITHUB PROFILE CARD ---
        ExpressiveSectionHeader(
            title = "GitHub Profile",
            subtitle = "Open Source Creator & Maintainer",
            icon = Icons.Default.Code,
            modifier = Modifier.align(Alignment.Start)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("developer_profile_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // GitHub Avatar Box
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "GitHub Icon",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "uznom",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "GitHub: @uznom",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                // Bio info
                Text(
                    text = "Builds reliable, offline-first open-source software for small business point-of-sale terminals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                // Quick links to GitHub
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/uznom"))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Profile", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/uznom/StorePoint"))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Repository", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // --- INTERACTIVE / EXPANDABLE PRIVACY POLICY CARD ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { privacyExpanded = !privacyExpanded }
                .testTag("privacy_policy_expandable_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PrivacyTip,
                            contentDescription = "Privacy Policy Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Google Play User-Data Compliance",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    IconButton(
                        onClick = { privacyExpanded = !privacyExpanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (privacyExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (privacyExpanded) "Collapse" else "Expand"
                        )
                    }
                }

                Text(
                    text = "StorePoint keeps all of your data on this device. Tap to see how your information is handled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AnimatedVisibility(
                    visible = privacyExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))

                        // Policy 1: Offline Processing
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "100% Local Offline Processing",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                "Your PINs, product catalog, sales records, and staff accounts never leave this device. Everything is stored in a local database protected by Android.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }

                        // Policy 2: Scoped Media Storage
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderZip,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Scoped Secure Media Access",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                "Backups and PDF reports are saved only to this app's own storage folders, and are shared only when you choose to share them.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }

                        // Policy 3: Zero Telemetry
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Zero Data Telemetry & Analytics Tracker",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                "StorePoint does not collect crash reports, usage statistics, advertising identifiers, or any other telemetry. The app has no analytics and no tracking of any kind.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "No user data is collected, shared, or sold.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // --- INTERACTIVE / EXPANDABLE TERMS AND CONDITIONS CARD ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { termsExpanded = !termsExpanded }
                .testTag("terms_conditions_expandable_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gavel,
                            contentDescription = "Terms and Conditions Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Terms & Conditions of System Use",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    IconButton(
                        onClick = { termsExpanded = !termsExpanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (termsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (termsExpanded) "Collapse" else "Expand"
                        )
                    }
                }

                Text(
                    text = "By using the system, you acknowledge and agree to operational, data custody, and liability policies. Tap to read.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AnimatedVisibility(
                    visible = termsExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))

                        Text(
                            text = "By using the system, you acknowledge and agree that:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Term 1: Local Sovereignty
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "1. Local Data Sovereignty & Backup Custody",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "StorePoint runs 100% locally on your hardware. You maintain exclusive custody and control of all sales transactions, customer records, and inventory data. You are solely responsible for exporting periodic database backups and safeguarding terminal PINs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }

                        // Term 2: As-Is Provision
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "2. 'As-Is' Software Disclaimer",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "StorePoint POS is provided 'as is' and 'as available' without warranties of any kind, whether express or implied, including merchantability, fitness for a particular business purpose, or uninterrupted error-free operation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }

                        // Term 3: Limitation of Liability
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "3. Operational Limitation of Liability",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "In no event shall the developer (@uznom) or open-source contributors be liable for any loss of revenue, financial discrepancies, inventory imbalances, hardware failures, or business interruptions arising from terminal use or downtime.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }

                        // Term 4: Regulatory & Tax Compliance
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "4. Tax & Commercial Compliance",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Terminal operators are solely responsible for ensuring that all issued receipts, applied tax percentages (e.g., VAT or sales tax exemptions), and business reports conform to applicable regional commercial and tax authority requirements.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Usage constitutes full acceptance of these terms.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Footer branding
        Text(
            text = "StorePoint POS is free and open source, licensed under the Apache License 2.0.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // --- SOFTWARE UPDATE MODAL DIALOGS ---
    if (showUpdateDialog && viewModel != null) {
        if (updateError != null) {
            AlertDialog(
                onDismissRequest = {
                    showUpdateDialog = false
                    viewModel.clearUpdateState()
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text("Update Check Error", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(updateError, style = MaterialTheme.typography.bodyMedium)
                        val helpText = when {
                            updateError.contains("404") ->
                                "The release or repository was not found on GitHub. If the repository is private, ensure your access token is configured, or publish an initial release via GitHub Actions."
                            updateError.contains("401") || updateError.contains("403") ->
                                "GitHub authentication failed or rate limit exceeded. Verify your GitHub access token permissions."
                            updateError.contains("network", ignoreCase = true) || updateError.contains("connection", ignoreCase = true) ->
                                "Unable to communicate with GitHub. Verify your device's Wi-Fi or mobile data connection."
                            else ->
                                "Verify your internet connection and that GitHub is reachable."
                        }
                        Text(
                            helpText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showUpdateDialog = false
                            viewModel.clearUpdateState()
                        }
                    ) {
                        Text("Dismiss")
                    }
                }
            )
        } else if (updateInfo != null) {
            if (!updateInfo.isNewer) {
                // App is up to date
                AlertDialog(
                    onDismissRequest = {
                        showUpdateDialog = false
                        viewModel.clearUpdateState()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = { Text("StorePoint is Up to Date", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "You are currently running the latest version (v$currentVersion).",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (updateInfo.latestVersionTag.isNotEmpty()) {
                                Text(
                                    "Latest GitHub tag: ${updateInfo.latestVersionTag}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showUpdateDialog = false
                                viewModel.clearUpdateState()
                            }
                        ) {
                            Text("Great!")
                        }
                    }
                )
            } else {
                // Newer version available!
                when (downloadState) {
                    is UpdateDownloadState.Idle -> {
                        AlertDialog(
                            onDismissRequest = {
                                showUpdateDialog = false
                                viewModel.clearUpdateState()
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            },
                            title = {
                                Text("New Update Available", fontWeight = FontWeight.Bold)
                            },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 280.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    "Current: v$currentVersion",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                                Text(
                                                    "Available: ${updateInfo.latestVersionTag}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            if (updateInfo.assetSize > 0) {
                                                val mb = updateInfo.assetSize / (1024.0 * 1024.0)
                                                Text(
                                                    String.format("%.1f MB", mb),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }

                                    if (updateInfo.releaseNotes.isNotEmpty()) {
                                        Text(
                                            "Release Notes:",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            updateInfo.releaseNotes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.startUpdateDownload(
                                            context = context,
                                            downloadUrl = updateInfo.downloadUrl,
                                            fileName = "StorePoint-${updateInfo.latestVersionTag}.apk"
                                        )
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.testTag("download_update_button")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download & Install", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showUpdateDialog = false
                                        viewModel.clearUpdateState()
                                    }
                                ) {
                                    Text("Later")
                                }
                            }
                        )
                    }

                    is UpdateDownloadState.Downloading -> {
                        val progress = downloadState.progress
                        val downloadedMb = downloadState.downloadedBytes / (1024.0 * 1024.0)
                        val totalMb = if (downloadState.totalBytes > 0) downloadState.totalBytes / (1024.0 * 1024.0) else 0.0

                        AlertDialog(
                            onDismissRequest = { /* Non dismissable while downloading */ },
                            icon = {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            },
                            title = { Text("Downloading Update...", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (progress >= 0f) {
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                String.format("%.1f MB / %.1f MB", downloadedMb, totalMb),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                String.format("%.0f%%", progress * 100),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                        Text(
                                            String.format("%.1f MB downloaded...", downloadedMb),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            },
                            confirmButton = {}
                        )
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        AlertDialog(
                            onDismissRequest = {
                                showUpdateDialog = false
                                viewModel.clearUpdateState()
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            },
                            title = { Text("Download Complete!", fontWeight = FontWeight.Bold) },
                            text = {
                                Text(
                                    "The updated APK is ready to install. Click below to launch the Android package installer.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.installDownloadedApk(context, downloadState.apkFile)
                                    },
                                    modifier = Modifier.testTag("install_apk_button")
                                ) {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Install Now")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showUpdateDialog = false
                                        viewModel.clearUpdateState()
                                    }
                                ) {
                                    Text("Close")
                                }
                            }
                        )
                    }

                    is UpdateDownloadState.Error -> {
                        AlertDialog(
                            onDismissRequest = {
                                showUpdateDialog = false
                                viewModel.clearUpdateState()
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(36.dp)
                                )
                            },
                            title = { Text("Download Failed", fontWeight = FontWeight.Bold) },
                            text = {
                                Text(
                                    "Error downloading APK: ${downloadState.message}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showUpdateDialog = false
                                        viewModel.clearUpdateState()
                                    }
                                ) {
                                    Text("OK")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
}

