package com.munzo.storepoint.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.ui.StorePointViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarTab(viewModel: StorePointViewModel) {
    val items by viewModel.allPaymentSchedules.collectAsState()
    val config by viewModel.storeConfig.collectAsState()
    val curr = config?.currencySymbol ?: "₱"

    // Calendar state
    var selectedYear by remember { mutableStateOf(2026) }
    var selectedMonth by remember { mutableStateOf(6) } // June
    var selectedDay by remember { mutableStateOf<Int?>(17) } // Default selected day

    // Dialog state
    var showAddDialog by remember { mutableStateOf(false) }

    // Forms
    var newTitle by remember { mutableStateOf("") }
    var newAmount by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("Supplier") }
    var newPriority by remember { mutableStateOf("Medium") }
    var newPayMethod by remember { mutableStateOf("CASH") }

    // Date computation
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.YEAR, selectedYear)
        set(java.util.Calendar.MONTH, selectedMonth - 1)
        set(java.util.Calendar.DAY_OF_MONTH, 1)
    }
    val firstDayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday...
    val maxDays = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)

    val monthName = when(selectedMonth) {
        1 -> "January"; 2 -> "February"; 3 -> "March"; 4 -> "April"; 5 -> "May"; 6 -> "June"
        7 -> "July"; 8 -> "August"; 9 -> "September"; 10 -> "October"; 11 -> "November"; else -> "December"
    }

    Column(modifier = Modifier.fillMaxSize().padding(2.dp)) {
        // Top action row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Store Payables Ledger",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Schedule Rent, Utilities, Salaries & Re-orders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("add_schedule_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Bill", maxLines = 1)
            }
        }

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isTablet = configuration.screenWidthDp >= 600

        @Composable
        fun CalendarGridCard(modifier: Modifier) {
            Card(
                modifier = modifier,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Month controller
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (selectedMonth == 1) {
                                selectedMonth = 12
                                selectedYear--
                            } else {
                                selectedMonth--
                            }
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Prev")
                        }
                        
                        Text(
                            text = "$monthName $selectedYear",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(onClick = {
                            if (selectedMonth == 12) {
                                selectedMonth = 1
                                selectedYear++
                            } else {
                                selectedMonth++
                            }
                        }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Days label (Sun, Mon, Tue...)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
                        dayLabels.forEach { label ->
                            Text(
                                text = label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Calendar Grid Layout
                    val days = mutableListOf<Int?>()
                    for (i in 1 until firstDayOfWeek) {
                        days.add(null)
                    }
                    for (i in 1..maxDays) {
                        days.add(i)
                    }
                    while (days.size % 7 != 0) {
                        days.add(null)
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(days.size) { index ->
                            val day = days[index]
                            if (day != null) {
                                val dateStr = String.format("%04d-%02d-%02d", selectedYear, selectedMonth, day)
                                val hasPaymentDue = items.any { it.dueDate == dateStr && !it.isPaid }
                                val hasPaidPayment = items.any { it.dueDate == dateStr && it.isPaid }
                                val isSelected = selectedDay == day

                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                hasPaymentDue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                                hasPaidPayment -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else if (hasPaymentDue) 1.5.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else if (hasPaymentDue) MaterialTheme.colorScheme.error else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedDay = day },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = day.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected || hasPaymentDue) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                                hasPaymentDue -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        if (hasPaymentDue) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Red)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.aspectRatio(1f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Indicator Legends", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Unpaid", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paid", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).border(1.dp, MaterialTheme.colorScheme.primary).background(MaterialTheme.colorScheme.primaryContainer))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chosen", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        @Composable
        fun ScheduleListCard(modifier: Modifier) {
            val filterDateStr = selectedDay?.let { String.format("%04d-%02d-%02d", selectedYear, selectedMonth, it) }
            val filteredItems = items.filter { item ->
                filterDateStr == null || item.dueDate == filterDateStr
            }

            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedDay != null) "Schedules for $monthName $selectedDay, $selectedYear" else "All Scheduled Sinking Funds",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (selectedDay != null) {
                        TextButton(onClick = { selectedDay = null }) {
                            Text("Clear Date Filter", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EventNote, "Empty", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No Scheduled Payments Registered", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            Text("Tap 'Add Bill' at top right to log a due standard.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredItems) { item ->
                            val isPastDue = !item.isPaid
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (item.isPaid) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, if (isPastDue) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (item.isPaid) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                                            )
                                            
                                            val (badgeBg, badgeText) = when (item.priority) {
                                                "High" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                                                "Medium" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                                                else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(badgeBg)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    item.priority,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = badgeText,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        Text(
                                            text = "Category: ${item.category} • Method: ${item.paymentMethod}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )

                                        Text(
                                            text = "Due Date: ${item.dueDate}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isPastDue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "$curr${String.format("%.2f", item.amount)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (item.isPaid) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = { viewModel.togglePaymentScheduleStatus(item) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (item.isPaid) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = "Toggle Paid",
                                                    tint = if (item.isPaid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.deletePaymentSchedule(item) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Schedule",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isTablet) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CalendarGridCard(modifier = Modifier.weight(1.2f).fillMaxHeight())
                ScheduleListCard(modifier = Modifier.weight(1.5f).fillMaxHeight())
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CalendarGridCard(modifier = Modifier.fillMaxWidth().wrapContentHeight())
                ScheduleListCard(modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    // Add Payment Schedule Dialog Modal
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Schedule Upcoming Store Bill", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Bill Description / Vendor Name") },
                        placeholder = { Text("e.g. Meralco Rent, Coca-Cola Deliveries") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newAmount,
                        onValueChange = { newAmount = it },
                        label = { Text("Amount Due ($curr)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("Urgency Level / Priority:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Low", "Medium", "High").forEach { item ->
                            val isSelected = newPriority == item
                            FilterChip(
                                selected = isSelected,
                                onClick = { newPriority = item },
                                label = { Text(item) }
                            )
                        }
                    }

                    Text("Category Type:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Supplier", "Utilities", "Rent").forEach { item ->
                                val isSelected = newCategory == item
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { newCategory = item },
                                    label = { Text(item) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Salaries", "Taxes", "Replenishment").forEach { item ->
                                val isSelected = newCategory == item
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { newCategory = item },
                                    label = { Text(item) }
                                )
                            }
                        }
                    }

                    Text("Fund Source (Outflow Deduct):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("CASH" to "Cash Register", "GCASH" to "Store GCash Account").forEach { (code, label) ->
                            val isSelected = newPayMethod == code
                            FilterChip(
                                selected = isSelected,
                                onClick = { newPayMethod = code },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = newAmount.toDoubleOrNull() ?: 0.0
                        if (newTitle.isNotBlank() && amt > 0) {
                            val activeDay = selectedDay ?: 1
                            val dateStr = String.format("%04d-%02d-%02d", selectedYear, selectedMonth, activeDay)
                            viewModel.addPaymentSchedule(
                                title = newTitle,
                                amount = amt,
                                dueDate = dateStr,
                                category = newCategory,
                                priority = newPriority,
                                paymentMethod = newPayMethod
                            )
                            showAddDialog = false
                            newTitle = ""
                            newAmount = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Add Schedule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
