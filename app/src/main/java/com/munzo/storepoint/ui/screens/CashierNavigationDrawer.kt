package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munzo.storepoint.data.CashierSession
import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.data.User
import com.munzo.storepoint.ui.KioskStatusBar
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroButtonVariant
import com.munzo.storepoint.ui.components.HeroColor
import com.munzo.storepoint.ui.components.HeroDivider
import com.munzo.storepoint.ui.components.HeroSize
import com.munzo.storepoint.ui.theme.ExpressiveButtonShape
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.elasticValuePop
import com.munzo.storepoint.ui.theme.expressiveGlassCard
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.util.APP_VERSION

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CashierTopAppBar(
    viewModel: StorePointViewModel,
    isKioskActive: Boolean,
    storeName: String,
    activeUser: User?,
    parkedCount: Int,
    pendingPrintCount: Int,
    onOpenDrawer: () -> Unit,
    onCallAdmin: () -> Unit,
    onReturns: () -> Unit,
    onParked: () -> Unit,
    onPrintQueue: () -> Unit,
    onTerminalApps: () -> Unit,
    onAbout: () -> Unit
) {
    val tactile = rememberTactileFeedback()

    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        if (isKioskActive) {
            KioskStatusBar(viewModel = viewModel)
        }
        TopAppBar(
            windowInsets = if (isKioskActive) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
            navigationIcon = {
                IconButton(
                    onClick = {
                        tactile.perform(TactileHapticRole.Tap)
                        onOpenDrawer()
                    },
                    modifier = Modifier.testTag("hamburger_menu_button")
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Open Sidebar Navigation")
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = ExpressiveButtonShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "SP",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = storeName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Transacting: ${activeUser?.username ?: "Cashier"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            actions = {
                if (activeUser?.role?.equals("ADMIN", ignoreCase = true) != true) {
                    IconButton(
                        onClick = {
                            tactile.perform(TactileHapticRole.Tap)
                            onCallAdmin()
                        },
                        modifier = Modifier.testTag("top_bar_call_admin_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Call Admin / Supervisor Assistance",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                IconButton(
                    onClick = {
                        tactile.perform(TactileHapticRole.Tap)
                        onReturns()
                    },
                    modifier = Modifier.testTag("top_bar_return_btn")
                ) {
                    Icon(Icons.Default.AssignmentReturn, contentDescription = "Returns & Refunds")
                }
                IconButton(
                    onClick = {
                        tactile.perform(TactileHapticRole.Tap)
                        onParked()
                    },
                    modifier = Modifier.testTag("parked_sidebar_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (parkedCount > 0) {
                                Badge(modifier = Modifier.elasticValuePop(parkedCount)) {
                                    Text("$parkedCount")
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.HourglassEmpty, contentDescription = "Held Transactions ($parkedCount)")
                    }
                }
                IconButton(
                    onClick = {
                        tactile.perform(TactileHapticRole.Tap)
                        onPrintQueue()
                    },
                    modifier = Modifier.testTag("top_bar_print_queue_btn")
                ) {
                    BadgedBox(
                        badge = {
                            if (pendingPrintCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.elasticValuePop(pendingPrintCount)
                                ) {
                                    Text("$pendingPrintCount")
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Thermal Print Queue ($pendingPrintCount)")
                    }
                }
                IconButton(
                    onClick = {
                        tactile.perform(TactileHapticRole.Tap)
                        onTerminalApps()
                    },
                    modifier = Modifier.testTag("top_bar_terminal_apps_btn")
                ) {
                    Icon(Icons.Default.Apps, contentDescription = "Terminal Apps")
                }
                IconButton(
                    onClick = {
                        tactile.perform(TactileHapticRole.Tap)
                        onAbout()
                    },
                    modifier = Modifier.testTag("top_bar_about_btn")
                ) {
                    Icon(Icons.Default.Info, contentDescription = "About Creator")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
internal fun CashierDrawerContent(
    storeConfig: StoreConfig?,
    activeUser: User?,
    activeSession: CashierSession?,
    curr: String,
    isSensitiveDataVisible: Boolean,
    viewModel: StorePointViewModel,
    onCloseDrawer: () -> Unit,
    onFastCashierSwitch: () -> Unit,
    onCashInOut: () -> Unit,
    onSukiTab: () -> Unit,
    onXReading: () -> Unit,
    onReturns: () -> Unit,
    onBulkProduction: () -> Unit,
    onCallAdmin: () -> Unit,
    onAdminDashboardNavigate: () -> Unit,
    onRequestShiftClose: () -> Unit,
    onRequestLogout: () -> Unit,
    onShowAbout: () -> Unit
) {
    val context = LocalContext.current

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .widthIn(max = 320.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Drawer Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = ExpressiveButtonShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 3.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "SP",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }
                Column {
                    Text(
                        text = storeConfig?.storeName ?: "StorePoint POS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                    Text(
                        text = "Secure Cashier Drawer",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HeroDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "ACTIVE CASHIER PROFILE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                letterSpacing = 0.5.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .expressiveGlassCard(
                        cornerRadius = 16.dp,
                        elevation = 1.dp,
                        accentGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    )
                    .padding(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeUser?.username ?: "Cashier",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activeUser?.role ?: "CASHIER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                FilledTonalIconButton(
                    onClick = onFastCashierSwitch,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("switch_cashier_btn")
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Fast Cashier Switch", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "SECURE CASH & DIGITAL BALANCES",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                letterSpacing = 0.5.sp
            )

            activeSession?.let { session ->
                CashierDrawerBalancesCard(
                    session = session,
                    storeConfig = storeConfig,
                    curr = curr,
                    isSensitiveDataVisible = isSensitiveDataVisible,
                    viewModel = viewModel,
                    onCashInOut = onCashInOut
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HeroDivider()

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.AssignmentReturn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                label = { Text("Returns & Refunds", fontWeight = FontWeight.SemiBold) },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onReturns()
                },
                modifier = Modifier.testTag("drawer_returns_refunds")
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Transform, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                label = { Text("Bulk Repackaging (BOM)", fontWeight = FontWeight.SemiBold) },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onBulkProduction()
                },
                modifier = Modifier.testTag("drawer_bulk_production")
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.PointOfSale, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                label = { Text("Cash In / Cash Out", fontWeight = FontWeight.SemiBold) },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onCashInOut()
                },
                modifier = Modifier.testTag("drawer_cash_in_out")
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                label = { Text("Suki Tab (Utang)", fontWeight = FontWeight.SemiBold) },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onSukiTab()
                },
                modifier = Modifier.testTag("drawer_suki_tab")
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                label = { Text("Mid-Shift X-Reading", fontWeight = FontWeight.SemiBold) },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onXReading()
                },
                modifier = Modifier.testTag("drawer_x_reading")
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                label = { Text("Switch Staff / Cashier", fontWeight = FontWeight.SemiBold) },
                selected = false,
                onClick = {
                    onCloseDrawer()
                    onFastCashierSwitch()
                },
                modifier = Modifier.testTag("drawer_switch_cashier")
            )

            if (activeUser?.role?.equals("ADMIN", ignoreCase = true) != true) {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Call Admin", fontWeight = FontWeight.Bold)
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = "HELP",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    selected = false,
                    onClick = {
                        onCloseDrawer()
                        onCallAdmin()
                    },
                    modifier = Modifier.testTag("drawer_call_admin")
                )
            }

            if (activeUser?.role == "ADMIN") {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                    label = { Text("Admin Dashboard") },
                    selected = false,
                    onClick = {
                        onCloseDrawer()
                        onAdminDashboardNavigate()
                    },
                    modifier = Modifier.testTag("drawer_admin_dashboard")
                )
            }

            HeroDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shift Session",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = CircleShape,
                    color = if (activeSession != null) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = if (activeSession != null) "SHIFT ACTIVE" else "NO ACTIVE SHIFT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = if (activeSession != null) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            HeroButton(
                onClick = {
                    onCloseDrawer()
                    if (activeSession != null) {
                        onRequestShiftClose()
                    } else {
                        Toast.makeText(context, "No active shift running. Start a shift first.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_end_shift_btn"),
                text = "End of Day Close Shift",
                icon = Icons.Default.Assessment,
                color = HeroColor.Danger,
                variant = HeroButtonVariant.Solid,
                size = HeroSize.Md,
                enabled = activeSession != null
            )

            HeroButton(
                onClick = {
                    onCloseDrawer()
                    onRequestLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_logout_btn"),
                text = "Log Out Terminal",
                icon = Icons.Default.ExitToApp,
                color = HeroColor.Default,
                variant = HeroButtonVariant.Bordered,
                size = HeroSize.Md
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "StorePoint POS Terminal v$APP_VERSION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = "Developed by uznom (GitHub: @uznom)",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp)
                    .clickable {
                        onCloseDrawer()
                        onShowAbout()
                    }
            )
        }
    }
}
