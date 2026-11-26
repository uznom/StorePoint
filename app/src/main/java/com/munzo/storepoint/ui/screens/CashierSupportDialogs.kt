package com.munzo.storepoint.ui.screens

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.User
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroFilterChip
import com.munzo.storepoint.ui.components.HeroInput
import com.munzo.storepoint.ui.components.HeroTextButton

@Composable
internal fun CashierCallAdminDialog(
    activeUser: User?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedReason by remember { mutableStateOf("Manager Override / Void") }
    var assistanceNote by remember { mutableStateOf("") }
    val adminPrefs = remember { context.getSharedPreferences("storepoint_admin_prefs", Context.MODE_PRIVATE) }
    var adminPhoneInput by remember { mutableStateOf(adminPrefs.getString("admin_phone_number", "") ?: "") }
    val reasons = listOf(
        "Manager Override / Void",
        "Price Check / Discount",
        "Cash Drawer Float Refill",
        "Hardware / Scanner Issue",
        "Customer Dispute / Inquiries"
    )

    val triggerDialAdmin = {
        val clean = adminPhoneInput.filter { it.isDigit() || it == '+' }
        adminPrefs.edit().putString("admin_phone_number", adminPhoneInput).apply()
        val dialIntent = if (clean.isNotBlank()) {
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
        } else {
            Intent(Intent.ACTION_DIAL)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(dialIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open phone dialer: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = { Text("Call Admin / Supervisor", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Request immediate manager or admin assistance. Confirming will dial the administrator's phone number directly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (adminPhoneInput.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Column {
                                Text(
                                    text = "Admin Contact Number:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = adminPhoneInput,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Text(
                                "No admin phone number is configured yet. Admin can set it under Admin Center > Security, or you may enter it below:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    HeroInput(
                        value = adminPhoneInput,
                        onValueChange = {
                            adminPhoneInput = it
                            adminPrefs.edit().putString("admin_phone_number", it).apply()
                        },
                        label = { Text("Admin / Supervisor Phone Number") },
                        placeholder = { Text("e.g. 09171234567 or +639171234567") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_phone_input")
                    )
                }

                Text(
                    "Reason for Assistance:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reasons.forEach { reason ->
                        HeroFilterChip(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                            label = reason,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                HeroInput(
                    value = assistanceNote,
                    onValueChange = { assistanceNote = it },
                    label = { Text("Note / Description (Optional)") },
                    placeholder = { Text("e.g. Needs manager PIN for void") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            HeroButton(
                onClick = {
                    onDismiss()
                    triggerDialAdmin()
                    try {
                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(500)
                        }
                    } catch (_: Exception) {
                    }
                    try {
                        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 600)
                    } catch (_: Exception) {
                    }
                    val cashierName = activeUser?.username ?: "Register"
                    val noteText = if (assistanceNote.isNotBlank()) " ($assistanceNote)" else ""
                    Toast.makeText(
                        context,
                        "Calling Admin for Cashier $cashierName... [$selectedReason]$noteText",
                        Toast.LENGTH_LONG
                    ).show()
                },
                modifier = Modifier.testTag("submit_call_admin_btn"),
                icon = Icons.Default.Call,
                text = "Dial Admin Now"
            )
        },
        dismissButton = {
            HeroTextButton(onClick = onDismiss, text = "Cancel")
        }
    )
}

@Composable
internal fun CashierRestoredCartOverlay(
    visible: Boolean,
    restoredLineCount: Int
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("cart_restored_notice")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Restored $restoredLineCount item${if (restoredLineCount == 1) "" else "s"} from your last session",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}
