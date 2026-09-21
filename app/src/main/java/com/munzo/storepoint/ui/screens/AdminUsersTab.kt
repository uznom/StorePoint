package com.munzo.storepoint.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.User
import com.munzo.storepoint.ui.theme.glassPanel

// Staff user lists tab
@Composable
fun UsersTab(
    users: List<User>,
    onDelete: (String) -> Unit,
    onAddClick: () -> Unit,
    onEdit: (User) -> Unit,
    onPrintBadge: (User) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Staff Roles & Access",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Button(onClick = onAddClick, modifier = Modifier.testTag("add_user_button")) {
                Icon(Icons.Default.PersonAdd, null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Register Staff", maxLines = 1)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(users) { usr ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassPanel(cornerRadius = 16.dp)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        val containerCol = when (usr.role) {
                            "ADMIN" -> MaterialTheme.colorScheme.primaryContainer
                            "INVENTORY" -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                        val contentCol = when (usr.role) {
                            "ADMIN" -> MaterialTheme.colorScheme.onPrimaryContainer
                            "INVENTORY" -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                        val iconVec = when (usr.role) {
                            "ADMIN" -> Icons.Default.AdminPanelSettings
                            "INVENTORY" -> Icons.Default.Inventory2
                            else -> Icons.Default.Badge
                        }
                        Surface(
                            shape = CircleShape,
                            color = containerCol,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = iconVec,
                                    contentDescription = null,
                                    tint = contentCol,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(usr.username.uppercase(), fontWeight = FontWeight.Bold)
                            Text("Assigned Role: ${usr.role}", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = if (usr.barcodeId.isNotEmpty()) "ID Barcode: ${usr.barcodeId}" else "No Barcode Badge",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (usr.barcodeId.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { onPrintBadge(usr) },
                            modifier = Modifier.size(36.dp).testTag("print_user_${usr.username}"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode, 
                                contentDescription = "Export & Print Cashier ID Badge", 
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { onEdit(usr) },
                            modifier = Modifier.size(36.dp).testTag("edit_user_${usr.username}"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Staff Barcode & Credentials", modifier = Modifier.size(18.dp))
                        }

                        // Protect standard default admin and last remaining admin profile from deletion
                        if (usr.username != "admin" && (usr.role != "ADMIN" || users.count { it.role == "ADMIN" } > 1)) {
                            FilledTonalIconButton(
                                onClick = { onDelete(usr.username) },
                                modifier = Modifier.size(36.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Staff", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
