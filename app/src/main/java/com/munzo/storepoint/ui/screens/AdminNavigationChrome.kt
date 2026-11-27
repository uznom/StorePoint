package com.munzo.storepoint.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.ui.KioskStatusBar
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.HeroDivider
import com.munzo.storepoint.ui.layout.AdminNavMode
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.ui.theme.springStretchPress

internal data class AdminTabDestination(
    val id: String,
    val label: String,
    val shortLabel: String,
    val icon: ImageVector
)

internal val ADMIN_TAB_DESTINATIONS: List<AdminTabDestination> = listOf(
    AdminTabDestination("analytics", "Analytics", "Analytics", Icons.Default.Assessment),
    AdminTabDestination("products", "Catalog Inventory", "Catalog", Icons.Default.Inventory),
    AdminTabDestination("suki", "Suki Credit", "Suki", Icons.Default.MenuBook),
    AdminTabDestination("suppliers", "Suppliers & POs", "Suppliers", Icons.Default.LocalShipping),
    AdminTabDestination("categories", "Categories", "Categories", Icons.Default.Category),
    AdminTabDestination("users", "Staff Accounts", "Staff", Icons.Default.People),
    AdminTabDestination("calendar", "Payment Calendar", "Calendar", Icons.Default.CalendarMonth),
    AdminTabDestination("security", "Security", "Security", Icons.Default.Security),
    AdminTabDestination("about", "About Information", "About", Icons.Default.Info)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminTopAppBar(
    viewModel: StorePointViewModel,
    isKioskActive: Boolean,
    onBackToPOS: () -> Unit,
    onNavigateToInventoryPortal: (() -> Unit)?,
    onOpenBulkProduction: () -> Unit,
    onExportReportPdf: () -> Unit
) {
    val tactile = rememberTactileFeedback()

    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        if (isKioskActive) {
            KioskStatusBar(viewModel = viewModel)
        }
        TopAppBar(
            windowInsets = if (isKioskActive) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SP",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = TextUnit.Unspecified
                            ),
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    Text("Admin Center", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        tactile.perform(TactileHapticRole.Tap)
                        onBackToPOS()
                    }
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "POS Register")
                }
            },
            actions = {
                if (onNavigateToInventoryPortal != null) {
                    IconButton(
                        onClick = {
                            tactile.perform(TactileHapticRole.Tap)
                            onNavigateToInventoryPortal()
                        },
                        modifier = Modifier.testTag("admin_open_inventory_portal_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = "Inventory Portal",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(
                    onClick = {
                        tactile.perform(TactileHapticRole.Tap)
                        onOpenBulkProduction()
                    },
                    modifier = Modifier.testTag("admin_open_production_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PrecisionManufacturing,
                        contentDescription = "Bulk Repackaging & Production",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = {
                        tactile.perform(TactileHapticRole.Tap)
                        onExportReportPdf()
                    },
                    modifier = Modifier.testTag("export_report_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "Export Audit PDF",
                        tint = MaterialTheme.colorScheme.primary
                    )
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
internal fun AdminCompactBottomBar(
    tabs: List<AdminTabDestination>,
    activeTab: String,
    onSelectTab: (String) -> Unit,
    onOpenMore: () -> Unit
) {
    val tactile = rememberTactileFeedback()
    val primaryTabs = tabs.take(4)
    val moreTabs = tabs.drop(4)

    Column(modifier = Modifier.fillMaxWidth()) {
        HeroDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            primaryTabs.forEach { tab ->
                NavigationBarItem(
                    selected = activeTab == tab.id,
                    onClick = {
                        tactile.perform(TactileHapticRole.Tap)
                        onSelectTab(tab.id)
                    },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.shortLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("tab_${tab.id}")
                )
            }
            NavigationBarItem(
                selected = moreTabs.any { it.id == activeTab },
                onClick = {
                    tactile.perform(TactileHapticRole.Tap)
                    onOpenMore()
                },
                icon = {
                    BadgedBox(
                        badge = {
                            if (moreTabs.any { it.id == activeTab }) {
                                Badge { Text("•") }
                            }
                        }
                    ) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = "More sections")
                    }
                },
                label = { Text("More", maxLines = 1) }
            )
        }
    }
}

@Composable
internal fun AdminSideNavigationRail(
    navMode: AdminNavMode,
    tabs: List<AdminTabDestination>,
    activeTab: String,
    onSelectTab: (String) -> Unit
) {
    val tactile = rememberTactileFeedback()

    Row(modifier = Modifier.fillMaxHeight()) {
        if (navMode == AdminNavMode.LabeledRail) {
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "MANAGEMENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
                tabs.forEach { tab ->
                    val selected = activeTab == tab.id
                    val interactionSource = remember { MutableInteractionSource() }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        border = if (selected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .springStretchPress(interactionSource = interactionSource)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    tactile.perform(TactileHapticRole.Tap)
                                    onSelectTab(tab.id)
                                }
                            )
                            .testTag("tab_${tab.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        } else {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(8.dp))
                tabs.forEach { tab ->
                    val selected = activeTab == tab.id
                    NavigationRailItem(
                        selected = selected,
                        onClick = {
                            tactile.perform(TactileHapticRole.Tap)
                            onSelectTab(tab.id)
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = {
                            Text(
                                text = tab.shortLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        alwaysShowLabel = true,
                        modifier = Modifier.testTag("tab_${tab.id}")
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminMoreNavigationSheet(
    moreTabs: List<AdminTabDestination>,
    activeTab: String,
    onSelectTab: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tactile = rememberTactileFeedback()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            moreTabs.forEach { tab ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = tab.label,
                            fontWeight = if (activeTab == tab.id) FontWeight.Bold else FontWeight.Normal,
                            color = if (activeTab == tab.id) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (activeTab == tab.id) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .clickable {
                            tactile.perform(TactileHapticRole.Tap)
                            onSelectTab(tab.id)
                            onDismiss()
                        }
                        .testTag("admin_more_tab_${tab.id}")
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
