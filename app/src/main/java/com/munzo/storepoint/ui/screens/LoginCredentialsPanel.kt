package com.munzo.storepoint.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.ui.components.HeroButton
import com.munzo.storepoint.ui.components.HeroButtonVariant
import com.munzo.storepoint.ui.components.HeroCard
import com.munzo.storepoint.ui.components.HeroCardHeader
import com.munzo.storepoint.ui.components.HeroChip
import com.munzo.storepoint.ui.components.HeroChipVariant
import com.munzo.storepoint.ui.components.HeroColor
import com.munzo.storepoint.ui.components.HeroInput
import com.munzo.storepoint.ui.components.HeroSize
import com.munzo.storepoint.ui.theme.HeroUITheme
import com.munzo.storepoint.ui.theme.PinPadEntry
import com.munzo.storepoint.ui.theme.elasticValuePop
import com.munzo.storepoint.util.SecurityHelper

@Composable
internal fun LoginCredentialsCardContent(
    username: String,
    password: String,
    loginError: String,
    isAuthenticating: Boolean,
    showPinResetBanner: Boolean,
    showExpandedHeader: Boolean,
    canOfferBiometric: Boolean,
    showSwitchUserInCard: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onPinResetNavigate: () -> Unit,
    onBiometricClick: () -> Unit,
    onSwitchUserClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (showExpandedHeader) 16.dp else 14.dp)
    ) {
        if (showExpandedHeader) {
            HeroCardHeader(
                title = "Sign In to Terminal",
                subtitle = "Enter your username and ${SecurityHelper.PIN_LENGTH}-digit PIN",
                icon = Icons.Default.Lock,
                iconColor = HeroUITheme.colors.accent
            )
        }

        if (showPinResetBanner) {
            HeroCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pin_reset_banner"),
                backgroundColor = HeroUITheme.colors.warning.copy(alpha = 0.12f),
                borderColor = HeroUITheme.colors.warning.copy(alpha = 0.3f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "This account still uses an old 6-digit PIN.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HeroUITheme.colors.foreground,
                        fontWeight = FontWeight.Medium
                    )
                    HeroButton(
                        onClick = onPinResetNavigate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_reset_start_button"),
                        color = HeroColor.Warning,
                        variant = HeroButtonVariant.Flat,
                        size = HeroSize.Md,
                        icon = Icons.Default.LockReset,
                        text = "Set up new 4-digit PIN"
                    )
                }
            }
        }

        HeroInput(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Account Username") },
            leadingIcon = { Icon(Icons.Default.AccountCircle, null, tint = HeroUITheme.colors.accent) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("username_input"),
            singleLine = true,
            shape = HeroUITheme.radius.md
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${SecurityHelper.PIN_LENGTH}-Digit Security PIN",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HeroUITheme.colors.foreground
                )
                if (password.length == SecurityHelper.PIN_LENGTH) {
                    HeroChip(
                        text = "PIN Ready",
                        color = HeroColor.Success,
                        variant = HeroChipVariant.Flat,
                        modifier = Modifier.elasticValuePop(password.length)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            PinPadEntry(
                pin = password,
                onPinChange = onPasswordChange,
                onPinComplete = { completed -> onSubmit(completed) },
                isMasked = true,
                isError = loginError.isNotEmpty(),
                errorMessage = loginError.ifEmpty { null },
                isEnabled = !isAuthenticating,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_input")
            )
            if (!showExpandedHeader) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        isAuthenticating -> "Verifying…"
                        password.length == SecurityHelper.PIN_LENGTH -> "✓ PIN ready — verifying automatically"
                        else -> "${password.length}/${SecurityHelper.PIN_LENGTH} digits"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isAuthenticating -> HeroUITheme.colors.accent
                        password.length == SecurityHelper.PIN_LENGTH -> HeroUITheme.colors.success
                        else -> HeroUITheme.colors.muted
                    },
                    modifier = Modifier.elasticValuePop(password.length)
                )
            }
        }

        HeroButton(
            onClick = { onSubmit(password) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("submit_login_button"),
            color = HeroColor.Primary,
            variant = HeroButtonVariant.Solid,
            size = HeroSize.Lg,
            icon = Icons.Default.Login,
            text = if (isAuthenticating) "Authorizing…" else "Sign In",
            enabled = !isAuthenticating
        )

        if (showExpandedHeader && canOfferBiometric) {
            HeroButton(
                onClick = onBiometricClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("biometric_login_button"),
                color = HeroColor.Success,
                variant = HeroButtonVariant.Flat,
                size = HeroSize.Lg,
                icon = Icons.Default.Fingerprint,
                text = "Sign In with Fingerprint"
            )
        }

        if (showSwitchUserInCard) {
            HeroButton(
                onClick = onSwitchUserClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("switch_user_button"),
                color = HeroColor.Default,
                variant = HeroButtonVariant.Light,
                size = HeroSize.Sm,
                icon = Icons.Default.PersonOutline,
                text = "Sign in as someone else"
            )
        }
    }
}
