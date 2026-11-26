package com.munzo.storepoint.ui.screens

import android.app.Activity
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroButtonVariant
import com.munzo.storepoint.ui.components.HeroCard
import com.munzo.storepoint.ui.components.HeroChip
import com.munzo.storepoint.ui.components.HeroChipVariant
import com.munzo.storepoint.ui.components.HeroColor
import com.munzo.storepoint.ui.components.HeroSize
import com.munzo.storepoint.ui.components.HeroTextButton
import com.munzo.storepoint.ui.theme.HeroUITheme
import com.munzo.storepoint.ui.theme.PinPadEntry
import com.munzo.storepoint.ui.theme.TactileHapticRole
import com.munzo.storepoint.ui.theme.celebratePulse
import com.munzo.storepoint.ui.theme.rememberTactileFeedback
import com.munzo.storepoint.ui.theme.springStretchPress

@Composable
internal fun LoginBrandEmblem(
    size: Dp,
    pulseTrigger: Int,
    onLogoTap: () -> Unit
) {
    val tactile = rememberTactileFeedback()
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .size(size)
            .celebratePulse(trigger = pulseTrigger)
            .springStretchPress(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    tactile.perform(TactileHapticRole.Tap)
                    onLogoTap()
                }
            ),
        shape = HeroUITheme.radius.xl,
        color = HeroUITheme.colors.accent,
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "SP",
                color = HeroUITheme.colors.accentForeground,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
            )
        }
    }
}

@Composable
internal fun LoginExpandedBrandPanel(
    storeName: String,
    pulseTrigger: Int,
    onLogoTap: () -> Unit,
    onScanBadgeClick: () -> Unit,
    onAboutNavigate: () -> Unit,
    modifier: Modifier = Modifier
) {
    HeroCard(
        modifier = modifier.fillMaxHeight(),
        backgroundColor = HeroUITheme.colors.surface,
        borderColor = HeroUITheme.colors.border
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LoginBrandEmblem(
                    size = 96.dp,
                    pulseTrigger = pulseTrigger,
                    onLogoTap = onLogoTap
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = storeName,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = HeroUITheme.colors.foreground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                HeroChip(
                    text = "SECURE COMMERCIAL POS TERMINAL",
                    color = HeroColor.Primary,
                    variant = HeroChipVariant.Flat
                )
            }

            LoginBadgeScanCard(
                expanded = true,
                onScanBadgeClick = onScanBadgeClick
            )

            HeroButton(
                onClick = onAboutNavigate,
                color = HeroColor.Default,
                variant = HeroButtonVariant.Light,
                size = HeroSize.Sm,
                icon = Icons.Default.Info,
                text = "About StorePoint & Compliance",
                modifier = Modifier.testTag("about_storepoint_button")
            )
        }
    }
}

@Composable
internal fun LoginBadgeScanCard(
    expanded: Boolean,
    onScanBadgeClick: () -> Unit
) {
    HeroCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = if (expanded) HeroUITheme.colors.surfaceSecondary else HeroUITheme.colors.surface,
        borderColor = HeroUITheme.colors.border
    ) {
        Column(
            modifier = Modifier.padding(if (expanded) 20.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (expanded) 12.dp else 10.dp)
        ) {
            if (expanded) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = HeroUITheme.colors.accent,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                text = if (expanded) "Instant Cashier Badge Sign-In" else "Scan ID Badge for Instant Login",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (expanded) HeroUITheme.colors.foreground else HeroUITheme.colors.accent,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (expanded) {
                    "Hold your employee ID card to the barcode camera to unlock register access."
                } else {
                    "Tap below to scan employee barcode ID badge via camera scanner."
                },
                style = MaterialTheme.typography.bodySmall,
                color = HeroUITheme.colors.muted,
                textAlign = TextAlign.Center
            )
            HeroButton(
                onClick = onScanBadgeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scan_badge_login_button"),
                color = HeroColor.Primary,
                variant = HeroButtonVariant.Solid,
                size = if (expanded) HeroSize.Lg else HeroSize.Md,
                icon = Icons.Default.QrCodeScanner,
                text = if (expanded) "Scan Staff Badge" else "Tap to Scan Badge"
            )
        }
    }
}

@Composable
internal fun KioskEmergencyUnlockDialog(
    viewModel: StorePointViewModel,
    pinInput: String,
    errorText: String,
    onPinChange: (String) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tactile = rememberTactileFeedback()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kiosk Emergency Unlock", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Enter the Admin 4-Digit PIN to immediately stop Lock Task Mode and return to normal operation.")
                Spacer(Modifier.height(4.dp))
                PinPadEntry(
                    pin = pinInput,
                    onPinChange = onPinChange,
                    isMasked = true,
                    isError = errorText.isNotEmpty(),
                    errorMessage = errorText.ifEmpty { null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("kiosk_unlock_pin_input")
                )
            }
        },
        confirmButton = {
            HeroButton(
                onClick = {
                    viewModel.verifyKioskUnlock(
                        pin = pinInput,
                        onSuccess = {
                            tactile.perform(TactileHapticRole.ConfirmSuccess)
                            viewModel.toggleKioskMode(false)
                            onDismiss()

                            var actContext = context
                            var activity: Activity? = null
                            while (actContext is ContextWrapper) {
                                if (actContext is Activity) {
                                    activity = actContext
                                    break
                                }
                                actContext = actContext.baseContext
                            }

                            if (activity != null) {
                                try {
                                    activity.stopLockTask()
                                    Toast.makeText(
                                        context,
                                        "Kiosk Lockdown Mode successfully disabled by Admin.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Kiosk Mode disabled (stopLockTask failed: ${e.localizedMessage})",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                Toast.makeText(context, "Kiosk Mode disabled in ViewModel.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onFailure = { err ->
                            tactile.perform(TactileHapticRole.WarningReject)
                            onError(err)
                        }
                    )
                },
                text = "Unlock"
            )
        },
        dismissButton = {
            HeroTextButton(
                onClick = onDismiss,
                text = "Cancel"
            )
        }
    )
}
