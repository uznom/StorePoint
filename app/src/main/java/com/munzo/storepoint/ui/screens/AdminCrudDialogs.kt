package com.munzo.storepoint.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.Category
import com.munzo.storepoint.data.User
import com.munzo.storepoint.ui.BarcodeScannerDialog
import com.munzo.storepoint.ui.components.ExpressiveButton
import com.munzo.storepoint.ui.components.ExpressiveButtonSize
import com.munzo.storepoint.ui.components.ExpressiveButtonVariant
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroButtonVariant
import com.munzo.storepoint.ui.components.HeroChip
import com.munzo.storepoint.ui.components.HeroChipVariant
import com.munzo.storepoint.ui.components.HeroColor
import com.munzo.storepoint.ui.components.HeroFilterChip
import com.munzo.storepoint.ui.components.HeroInput
import com.munzo.storepoint.ui.components.HeroSize
import com.munzo.storepoint.ui.components.HeroTextButton
import com.munzo.storepoint.ui.theme.ExpressiveOtpPinInput
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.util.SecurityHelper

@Composable
internal fun AdminDeleteConfirmDialog(
    targetName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val tactile = rememberTactileFeedback()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Confirm Deletion", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = "Are you absolutely sure you want to delete '$targetName'? This choice is permanent and cannot be undone."
            )
        },
        confirmButton = {
            ExpressiveButton(
                onClick = {
                    tactile.perform(TactileHapticRole.WarningReject)
                    onConfirm()
                },
                label = "Delete",
                variant = ExpressiveButtonVariant.FILLED,
                size = ExpressiveButtonSize.M,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        },
        dismissButton = {
            HeroTextButton(onClick = onDismiss, text = "Cancel")
        }
    )
}

@Composable
internal fun AdminCategoryCrudDialog(
    editingCategory: Category?,
    onSave: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    val tactile = rememberTactileFeedback()
    var categoryNameInput by remember(editingCategory) { mutableStateOf(editingCategory?.name ?: "") }
    var selectedColor by remember(editingCategory) {
        mutableStateOf(editingCategory?.colorAccent?.ifBlank { "#2563EB" } ?: "#2563EB")
    }
    var selectedIcon by remember(editingCategory) {
        mutableStateOf(editingCategory?.iconBadge?.ifBlank { "folder" } ?: "folder")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            HeroButton(
                onClick = {
                    if (categoryNameInput.isNotBlank()) {
                        tactile.perform(TactileHapticRole.ConfirmSuccess)
                        onSave(
                            Category(
                                id = editingCategory?.id ?: 0,
                                name = categoryNameInput.trim(),
                                colorAccent = selectedColor,
                                iconBadge = selectedIcon
                            )
                        )
                    } else {
                        tactile.perform(TactileHapticRole.WarningReject)
                    }
                },
                modifier = Modifier.testTag("category_submit"),
                text = "Save Category",
                color = HeroColor.Primary,
                variant = HeroButtonVariant.Solid,
                size = HeroSize.Md
            )
        },
        dismissButton = {
            HeroTextButton(onClick = onDismiss, text = "Cancel")
        },
        title = { Text(if (editingCategory == null) "Add Category Folder" else "Edit Category Details") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HeroInput(
                    value = categoryNameInput,
                    onValueChange = { categoryNameInput = it },
                    label = { Text("Category Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_name_input"),
                    singleLine = true
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Color Accent:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("category_color_picker"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CATEGORY_COLOR_PRESETS.forEach { (hex, label) ->
                            val isSelected = selectedColor.equals(hex, ignoreCase = true)
                            val chipColor = parseCategoryColor(hex)
                            Surface(
                                shape = CircleShape,
                                color = chipColor,
                                border = if (isSelected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.onSurface) else null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("category_color_chip_$hex")
                                    .clickable {
                                        tactile.perform(TactileHapticRole.Tap)
                                        selectedColor = hex
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = label,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Icon Badge:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("category_icon_picker"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CATEGORY_ICON_PRESETS.forEach { (iconKey, label) ->
                            val isSelected = selectedIcon.equals(iconKey, ignoreCase = true)
                            HeroFilterChip(
                                selected = isSelected,
                                onClick = { selectedIcon = iconKey },
                                icon = getCategoryIconVector(iconKey),
                                label = label,
                                modifier = Modifier.testTag("category_icon_chip_$iconKey")
                            )
                        }
                    }
                }

                val previewColor = parseCategoryColor(selectedColor, MaterialTheme.colorScheme.primary)
                val previewIcon = getCategoryIconVector(selectedIcon)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = previewColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, previewColor.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = previewColor,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = previewIcon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (categoryNameInput.isBlank()) "Category Preview" else categoryNameInput,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Color: $selectedColor • Icon: $selectedIcon",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
internal fun AdminUserCrudDialog(
    editingUser: User?,
    onSave: (User) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tactile = rememberTactileFeedback()
    var inputUsername by remember(editingUser) { mutableStateOf(editingUser?.username ?: "") }
    var inputPin by remember(editingUser) { mutableStateOf("") }
    var inputBarcodeId by remember(editingUser) { mutableStateOf(editingUser?.barcodeId ?: "") }
    var inputRole by remember(editingUser) { mutableStateOf(editingUser?.role ?: "CASHIER") }
    var showStaffCameraScanner by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroButton(
                    onClick = {
                        val pinToSave = if (inputPin.isNotEmpty()) inputPin else (editingUser?.pinHash ?: "")
                        if (inputUsername.isBlank()) {
                            tactile.perform(TactileHapticRole.WarningReject)
                            Toast.makeText(context, "Username cannot be empty.", Toast.LENGTH_SHORT).show()
                            return@HeroButton
                        }
                        if (editingUser == null && !SecurityHelper.isValidPin(inputPin)) {
                            tactile.perform(TactileHapticRole.WarningReject)
                            Toast.makeText(
                                context,
                                "A full ${SecurityHelper.PIN_LENGTH}-digit security PIN is required.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@HeroButton
                        }
                        if (inputPin.isNotEmpty() && !SecurityHelper.isValidPin(inputPin)) {
                            tactile.perform(TactileHapticRole.WarningReject)
                            Toast.makeText(
                                context,
                                "PIN must be exactly ${SecurityHelper.PIN_LENGTH} digits.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@HeroButton
                        }
                        if (inputPin.isNotEmpty() && SecurityHelper.isWeakPin(inputPin)) {
                            tactile.perform(TactileHapticRole.WarningReject)
                            Toast.makeText(
                                context,
                                "That PIN is too easy to guess. ${SecurityHelper.pinPolicyHint()}",
                                Toast.LENGTH_LONG
                            ).show()
                            return@HeroButton
                        }

                        val toSave = User(
                            username = inputUsername.trim(),
                            pinHash = if (inputPin.isNotEmpty()) SecurityHelper.hashPin(inputPin) else pinToSave,
                            role = inputRole,
                            barcodeId = inputBarcodeId.trim(),
                            biometricEnrolled = editingUser?.biometricEnrolled ?: false,
                            pinResetRequired = false
                        )
                        tactile.perform(TactileHapticRole.ConfirmSuccess)
                        onSave(toSave)
                        if (inputRole == "INVENTORY") {
                            Toast.makeText(
                                context,
                                "Inventory audit account successfully registered & certified!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_submit"),
                    text = if (editingUser != null) "Save Changes" else "Add Staff User",
                    icon = if (editingUser != null) Icons.Default.Save else Icons.Default.PersonAdd,
                    color = HeroColor.Primary,
                    variant = HeroButtonVariant.Solid,
                    size = HeroSize.Md
                )

                HeroButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_cancel"),
                    text = "Cancel",
                    icon = Icons.Default.Close,
                    color = HeroColor.Default,
                    variant = HeroButtonVariant.Light,
                    size = HeroSize.Md
                )
            }
        },
        dismissButton = null,
        title = {
            Text(
                text = if (editingUser != null) "Edit Staff Account" else "Add Local Staff Account",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeroInput(
                    value = inputUsername,
                    onValueChange = { inputUsername = it },
                    label = { Text("Username") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("staff_username_input"),
                    singleLine = true,
                    enabled = editingUser == null
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (editingUser != null) {
                            "New ${SecurityHelper.PIN_LENGTH}-Digit PIN (Leave blank to keep)"
                        } else {
                            "${SecurityHelper.PIN_LENGTH}-Digit Security PIN"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val pinIsWeak = inputPin.isNotEmpty() && SecurityHelper.isWeakPin(inputPin)
                    ExpressiveOtpPinInput(
                        pin = inputPin,
                        onPinChange = { inputPin = it },
                        isMasked = true,
                        isError = pinIsWeak,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("staff_password_input")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            pinIsWeak -> "Too easy to guess — ${SecurityHelper.pinPolicyHint()}"
                            inputPin.length == SecurityHelper.PIN_LENGTH -> "✓ PIN ready"
                            else -> "${inputPin.length}/${SecurityHelper.PIN_LENGTH} digits"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            pinIsWeak -> MaterialTheme.colorScheme.error
                            inputPin.length == SecurityHelper.PIN_LENGTH -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        }
                    )
                }

                val isTablet = LocalConfiguration.current.screenWidthDp >= 600
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroInput(
                        value = inputBarcodeId,
                        onValueChange = { inputBarcodeId = it },
                        label = { Text("Staff Badge Barcode ID") },
                        placeholder = { Text("e.g. EMP_ID_789") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("staff_barcode_input"),
                        singleLine = true,
                        trailingIcon = {
                            if (inputBarcodeId.isNotEmpty()) {
                                IconButton(onClick = { inputBarcodeId = "" }) {
                                    Icon(Icons.Default.Clear, "Clear")
                                }
                            }
                        }
                    )

                    if (isTablet) {
                        FilledIconButton(
                            onClick = { showStaffCameraScanner = true },
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("staff_barcode_scan_button")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan with Camera")
                        }
                    }
                }

                if (showStaffCameraScanner && isTablet) {
                    BarcodeScannerDialog(
                        title = "Scan Staff Badge Barcode",
                        instruction = "Scan the staff numeric badge serial or QR code to register their account badge ID.",
                        onBarcodeScanned = { code ->
                            inputBarcodeId = code
                            showStaffCameraScanner = false
                            tactile.perform(TactileHapticRole.ScanSuccess)
                            try {
                                val toneG = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                                toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                                Handler(Looper.getMainLooper()).postDelayed({
                                    try { toneG.release() } catch (_: Exception) {}
                                }, 250)
                            } catch (e: Exception) {
                                Log.w("AdminDashboard", "Tone generation failed: ${e.message}")
                            }
                        },
                        onDismiss = { showStaffCameraScanner = false }
                    )
                }

                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Privilege Role:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        HeroChip(
                            text = "Admin",
                            variant = if (inputRole == "ADMIN") HeroChipVariant.Solid else HeroChipVariant.Flat,
                            color = if (inputRole == "ADMIN") HeroColor.Primary else HeroColor.Default,
                            onClick = { inputRole = "ADMIN" },
                            modifier = Modifier.testTag("role_chip_admin")
                        )
                        HeroChip(
                            text = "Cashier",
                            variant = if (inputRole == "CASHIER") HeroChipVariant.Solid else HeroChipVariant.Flat,
                            color = if (inputRole == "CASHIER") HeroColor.Primary else HeroColor.Default,
                            onClick = { inputRole = "CASHIER" },
                            modifier = Modifier.testTag("role_chip_cashier")
                        )
                        HeroChip(
                            text = "Inventory",
                            variant = if (inputRole == "INVENTORY") HeroChipVariant.Solid else HeroChipVariant.Flat,
                            color = if (inputRole == "INVENTORY") HeroColor.Primary else HeroColor.Default,
                            onClick = { inputRole = "INVENTORY" },
                            modifier = Modifier.testTag("role_chip_inventory")
                        )
                    }
                }

                if (inputRole == "INVENTORY") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Admin Certified Inventory Role",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "Authorized by Administrator. Grants inventory auditing, price checks, and Quick Share sync.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
