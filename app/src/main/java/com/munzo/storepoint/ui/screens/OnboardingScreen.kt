package com.munzo.storepoint.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.util.APP_VERSION
import com.munzo.storepoint.ui.layout.WindowLayout
import com.munzo.storepoint.ui.layout.rememberWindowLayout
import com.munzo.storepoint.ui.theme.ExpressiveButtonShape
import com.munzo.storepoint.ui.theme.glassPanel
import com.munzo.storepoint.ui.theme.tactileBounce
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(viewModel: StorePointViewModel) {
    var activePage by remember { mutableStateOf(0) }
    var termsAccepted by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val windowLayout = rememberWindowLayout()
    val isCompact = windowLayout == WindowLayout.Compact

    // Bouncy scale for the main header / illustration
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(activePage) {
        isVisible = false
        delay(50)
        isVisible = true
    }

    val bounceScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.7f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounceScale"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .fillMaxSize()
                    .padding(horizontal = if (isCompact) 20.dp else 28.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // SP Brand Header at top
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .tactileBounce(),
                        shape = ExpressiveButtonShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 3.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "SP",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "StorePoint POS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.SansSerif
                    )
                }

            // Main interactive page slider centered
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = activePage,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally(
                                animationSpec = spring(
                                    dampingRatio = 0.65f,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) { width -> width } + fadeIn() togetherWith
                                    slideOutHorizontally(
                                        animationSpec = spring(stiffness = Spring.StiffnessHigh)
                                    ) { width -> -width } + fadeOut()
                        } else {
                            slideInHorizontally(
                                animationSpec = spring(
                                    dampingRatio = 0.65f,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) { width -> -width } + fadeIn() togetherWith
                                    slideOutHorizontally(
                                        animationSpec = spring(stiffness = Spring.StiffnessHigh)
                                    ) { width -> width } + fadeOut()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "pageTransition"
                ) { page ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(bounceScale)
                    ) {
                        when (page) {
                            0 -> WalkthroughSlide(
                                icon = Icons.Default.Storefront,
                                title = "Complete Point of Sale",
                                subtitle = "Works Fully Offline",
                                description = "Ring up sales, manage stock, and keep every record on this device. No internet connection and no monthly subscription required."
                            )
                            1 -> WalkthroughSlide(
                                icon = Icons.Default.QrCodeScanner,
                                title = "Barcode Scanner & Checkout",
                                subtitle = "Fast Checkout",
                                description = "Scan barcodes to add items instantly. Handles wholesale and retail units such as packs, boxes, or reams, and updates stock and prices automatically."
                            )
                            2 -> WalkthroughSlide(
                                icon = Icons.Default.MonitorHeart,
                                title = "Sales Reports & Profit Tracking",
                                subtitle = "Know Your Numbers",
                                description = "Record supplier costs, see the real profit on each sale, export PDF sales reports, and protect the app with a PIN or barcode login."
                            )
                            3 -> TermsAndConditionsSlide(
                                accepted = termsAccepted,
                                onAcceptChanged = { termsAccepted = it }
                            )
                        }
                    }
                }
            }

            // Bottom Actions Bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { idx ->
                        val isPageActive = idx == activePage
                        val indicatorWidth by animateDpAsState(
                            targetValue = if (isPageActive) 28.dp else 8.dp,
                            animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                            label = "indicatorWidth"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(height = 8.dp, width = indicatorWidth)
                                .clip(CircleShape)
                                .background(
                                    color = if (isPageActive) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activePage > 0) {
                        TextButton(
                            onClick = { activePage-- },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("onboarding_back_btn")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Back", fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(80.dp))
                    }

                    if (activePage < 3) {
                        Button(
                            onClick = { activePage++ },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("onboarding_next_btn"),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "Next",
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (termsAccepted) {
                                    viewModel.setOnboardingCompleted(true)
                                }
                            },
                            enabled = termsAccepted,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("onboarding_finish_btn"),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Finish")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Complete & Proceed",
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun WalkthroughSlide(
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon Container with elegant bouncing breathing effect
            val infiniteTransition = rememberInfiniteTransition(label = "bouncing_icon")
            val iconScale by infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "iconScale"
            )

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(iconScale)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = subtitle.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun TermsAndConditionsSlide(
    accepted: Boolean,
    onAcceptChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Gavel,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Text(
                text = "Terms & Conditions",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif
            )

            Text(
                text = "Version $APP_VERSION · Effective September 2026",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Scrollable terms container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    termsSection(
                        "1. Acceptance of Terms",
                        "By installing, activating, or using StorePoint, you (the store owner or authorized operator) agree to be bound by these Terms and Conditions. If you do not agree, discontinue use and remove the application from your device."
                    )
                    termsSection(
                        "2. Offline Operation & Data Ownership",
                        "StorePoint operates entirely offline. All sales records, product catalogs, supplier data, cashier sessions, and wallet balance registers are stored locally on your device's SQLite database. You retain full ownership of and responsibility for your business data. Nothing is transmitted to, hosted by, or visible to the StorePoint developers."
                    )
                    
                    termsSection(
                        "3. Data Backup & Loss Risk",
                        "Because StorePoint uses no cloud synchronization, wiping app data, performing a system reset, losing your device, or uninstalling the app will permanently and irreversibly destroy all records. You are responsible for creating regular database backups via Admin Center → Settings → Database Backup & Restore, and for storing those backup files safely. StorePoint shall not be liable for any loss of sales history, inventory data, or business records resulting from failure to back up."
                    )

                    termsSection(
                        "4. Tax & Regulatory Compliance",
                        "StorePoint is a facilitating accounting utility only. You are solely responsible for the accuracy of your tax entries, receipt issuance, and compliance with all applicable local laws and regulations — including, for Philippine retailers, BIR invoicing and documentation requirements. StorePoint does not provide legal, tax, or accounting advice and makes no representation that reports generated by the app satisfy any regulatory filing."
                    )

                    termsSection(
                        "5. Payments, Wallets & Cash Handling",
                        "GCash, Maya, and other wallet balances recorded in StorePoint are bookkeeping records only — they are not actual financial accounts and hold no real funds. Cash drawer figures reflect what you physically count and enter. You are responsible for counting the opening float, reconciling the drawer at shift close, and investigating any discrepancies."
                    )

                    termsSection(
                        "6. Returns, Refunds & Exchanges",
                        "StorePoint provides tools to record returns, refunds, exchanges, and restocking decisions. The returns, refund, and exchange policy of your store is set and enforced by you; the application merely records it. Refunds paid from the cash drawer are drawn from cash you have confirmed is present."
                    )

                    termsSection(
                        "7. License & Permitted Use",
                        "StorePoint is licensed under the Apache License 2.0. You may use, copy, and modify the software for operating your own store or stores. You may not misrepresent the origin of the software, remove copyright notices, or hold the contributors liable in any redistribution."
                    )

                    termsSection(
                        "8. Privacy & Security",
                        "No personal or business data leaves your device. Admin PINs and staff credentials are hashed locally with PBKDF2 and salted before storage. You are responsible for the physical security of your device, for safeguarding administrator PINs, and for managing staff accounts with appropriate roles."
                    )

                    termsSection(
                        "9. Warranty Disclaimer & Limitation of Liability",
                        "StorePoint is provided \"AS IS\", without warranty of any kind, express or implied, including merchantability and fitness for a particular purpose. To the maximum extent permitted by law, the StorePoint contributors shall not be liable for any lost profits, lost data, business interruption, or any indirect, incidental, or consequential damages arising from use of, or inability to use, the application."
                    )

                    termsSection(
                        "10. Changes to These Terms",
                        "These terms may be updated alongside app releases. Continued use of a new version after an update constitutes acceptance of the revised terms. The latest effective version is always shown at the top of this screen."
                    )
                }
            }

            // Agreement checkbox with dynamic spring feedback
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAcceptChanged(!accepted) },
                shape = RoundedCornerShape(16.dp),
                color = if (accepted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (accepted) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Checkbox(
                        checked = accepted,
                        onCheckedChange = { onAcceptChanged(it) },
                        modifier = Modifier.testTag("terms_cb")
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "I have read and accept the StorePoint Terms & Conditions.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (accepted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

@Composable
private fun termsSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

