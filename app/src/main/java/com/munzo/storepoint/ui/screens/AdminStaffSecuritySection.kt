package com.munzo.storepoint.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.*
import com.munzo.storepoint.util.BiometricAuthHelper

@Composable
fun AdminStaffSecuritySection(viewModel: StorePointViewModel) {
    val context = LocalContext.current
    val activeUser by viewModel.activeUser.collectAsState()
    var adminResetCurrentPin by remember { mutableStateOf("") }
    var adminResetNewPin by remember { mutableStateOf("") }
    var adminResetConfirmPin by remember { mutableStateOf("") }
    var adminResetError by remember { mutableStateOf<String?>(null) }

    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val isBiometricSupported = remember(context) { BiometricAuthHelper.isBiometricSupported(context) }

        // --- ADMIN SECURITY & PIN RESET MECHANISM ---
        HeroCard(
            modifier = Modifier.fillMaxWidth().testTag("admin_pin_reset_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockReset,
                        contentDescription = "Admin PIN Reset",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "Admin Security & PIN Reset",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Change administrator PIN by verifying your current PIN.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                adminResetError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }

                HeroInput(
                    value = adminResetCurrentPin,
                    onValueChange = { adminResetCurrentPin = it.take(6); adminResetError = null },
                    label = { Text("Current Admin PIN") },
                    placeholder = { Text("Enter current PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("reset_admin_current_pin_input"),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroInput(
                        value = adminResetNewPin,
                        onValueChange = { adminResetNewPin = it.take(4); adminResetError = null },
                        label = { Text("New 4-Digit PIN") },
                        placeholder = { Text("4 digits") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f).testTag("reset_admin_new_pin_input"),
                        singleLine = true
                    )

                    HeroInput(
                        value = adminResetConfirmPin,
                        onValueChange = { adminResetConfirmPin = it.take(4); adminResetError = null },
                        label = { Text("Confirm PIN") },
                        placeholder = { Text("4 digits") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f).testTag("reset_admin_confirm_pin_input"),
                        singleLine = true
                    )
                }

                HeroButton(
                    onClick = {
                        if (adminResetNewPin != adminResetConfirmPin) {
                            adminResetError = "New PIN and Confirm PIN do not match."
                            return@HeroButton
                        }
                        val currentAdmin = activeUser?.username ?: "admin"
                        viewModel.resetAdminPin(
                            adminUsername = currentAdmin,
                            currentPinOrRecoveryKey = adminResetCurrentPin,
                            newPin = adminResetNewPin
                        ) { success, msg ->
                            if (success) {
                                adminResetCurrentPin = ""
                                adminResetNewPin = ""
                                adminResetConfirmPin = ""
                                adminResetError = null
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            } else {
                                adminResetError = msg
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("reset_admin_pin_submit_btn"),
                    text = "Reset & Update Admin PIN",
                    icon = Icons.Default.Key
                )
            }
        }


        // --- BIOMETRIC SECURITY MANAGEMENT ---
        HeroCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Biometric Security",
                            tint = if (isBiometricEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "Biometric Authentication",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (isBiometricSupported) "Hardware fingerprint / face unlock supported on this terminal" else "Biometric sensor unavailable on this device",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isBiometricSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    HeroSwitch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !isBiometricSupported) {
                                Toast.makeText(context, "Biometric hardware is not configured or available on this device.", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.setBiometricEnabled(enabled)
                            }
                        },
                        enabled = isBiometricSupported
                    )
                }

                Text(
                    text = "Allow cashiers and managers to authenticate at login and wake terminal locks instantly using fingerprint biometrics.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }



}
