package com.munzo.storepoint.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.munzo.storepoint.data.User
import com.munzo.storepoint.ui.StorePointViewModel
import com.munzo.storepoint.ui.components.ExpressiveButton
import com.munzo.storepoint.ui.components.ExpressiveButtonSize
import com.munzo.storepoint.ui.components.ExpressiveButtonVariant
import com.munzo.storepoint.ui.theme.ExpressiveCardShape
import com.munzo.storepoint.ui.theme.ExpressiveOtpPinInput
import com.munzo.storepoint.ui.theme.PinPadEntry
import com.munzo.storepoint.ui.theme.shakeOnTrigger
import com.munzo.storepoint.util.SecurityHelper

/**
 * Progressive disclosure: the reset task is chunked into small, single-purpose steps.
 *
 * Chunking (Laws of UX) — asking for "old PIN, new PIN, confirm new PIN" in one dense
 * form raises cognitive load and error rate. Splitting it means each screen asks for
 * exactly one thing, while the Goal-Gradient Effect keeps the user moving because the
 * remaining work is visibly shrinking.
 */
internal enum class PinResetStep {
    /** Prove you are the account holder using the existing credential. */
    VERIFY,

    /** Choose a new 4-digit PIN. */
    CHOOSE,

    /** Re-enter it to catch typos before committing. */
    CONFIRM,

    /** Done. */
    COMPLETE
}

/**
 * Progress header.
 *
 * Always renders, so the user can see how much work remains (Goal-Gradient Effect) and
 * which step they are on (Recognition over Recall).
 */
@Composable
private fun ResetHeader(step: PinResetStep, username: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = if (step == PinResetStep.COMPLETE) Icons.Default.CheckCircle
                              else Icons.Default.LockReset,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(14.dp).size(32.dp)
            )
        }
        Text(
            text = when (step) {
                PinResetStep.VERIFY -> "Set up your new PIN"
                PinResetStep.CHOOSE -> "Choose a ${SecurityHelper.PIN_LENGTH}-digit PIN"
                PinResetStep.CONFIRM -> "Confirm your new PIN"
                PinResetStep.COMPLETE -> "You're all set"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        if (username.isNotBlank() && step != PinResetStep.COMPLETE) {
            Text(
                text = "Account: $username",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (step != PinResetStep.COMPLETE) {
            Text(
                text = "Step ${step.ordinal + 1} of 3",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("pin_reset_step_indicator")
            )
        }
    }
}

/**
 * One-time forced migration from the legacy 6-digit credential to the 4-digit standard.
 *
 * Reached only when `login()` refuses an account flagged `pinResetRequired`. The user
 * must prove ownership with their *current* PIN before a new one can be set, so this
 * screen can never be used to hijack someone else's account.
 */
@Composable
fun PinResetScreen(
    viewModel: StorePointViewModel,
    onCompleted: () -> Unit
) {
    val pendingUsers by viewModel.usersPendingPinReset.collectAsState()

    // Default to the last user when they are a candidate, or the sole candidate.
    // Otherwise the user picks (Recognition over Recall: show the options rather than
    // demanding the cashier recall a username).
    var username by remember {
        mutableStateOf(
            viewModel.lastLoggedInUser.value
                ?.takeIf { name -> pendingUsers.any { it.username == name } }
                ?: pendingUsers.singleOrNull()?.username
                ?: ""
        )
    }
    var step by remember { mutableStateOf(PinResetStep.VERIFY) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var shakeTrigger by remember { mutableIntStateOf(0) }
    var isWorking by remember { mutableStateOf(false) }
    var authorizedUser by remember { mutableStateOf<User?>(null) }

    fun fail(message: String) {
        error = message
        shakeTrigger++
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ResetHeader(step = step, username = username)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shakeOnTrigger(trigger = shakeTrigger),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = ExpressiveCardShape
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AnimatedContent(
                        targetState = step,
                        transitionSpec = {
                            // Directional slide communicates forward/backward progress
                            // rather than a disorienting cross-fade.
                            val forward = targetState.ordinal > initialState.ordinal
                            (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn())
                                .togetherWith(
                                    slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut()
                                )
                        },
                        label = "pinResetStep"
                    ) { current ->
                        when (current) {
                            PinResetStep.VERIFY -> VerifyStep(
                                pendingUsers = pendingUsers,
                                username = username,
                                onUsernameChange = { username = it; error = "" },
                                currentPin = currentPin,
                                onPinChange = { currentPin = it; error = "" },
                                onContinue = {
                                    if (username.isBlank()) {
                                        fail("Select your account first.")
                                    } else {
                                        isWorking = true
                                        viewModel.verifyPinResetAuthorization(
                                            username = username.trim(),
                                            currentPin = currentPin,
                                            onSuccess = { user ->
                                                isWorking = false
                                                currentPin = ""
                                                authorizedUser = user
                                                error = ""
                                                step = PinResetStep.CHOOSE
                                            },
                                            onFailure = {
                                                isWorking = false
                                                currentPin = ""
                                                fail(it)
                                            }
                                        )
                                    }
                                },
                                isWorking = isWorking
                            )

                            PinResetStep.CHOOSE -> ChooseStep(
                                newPin = newPin,
                                onPinChange = { newPin = it; error = "" },
                                onContinue = {
                                    if (SecurityHelper.isWeakPin(newPin)) {
                                        fail("Too easy to guess. ${SecurityHelper.pinPolicyHint()}")
                                    } else {
                                        isWorking = true
                                        error = ""
                                        step = PinResetStep.CONFIRM
                                    }
                                },
                                onBack = { step = PinResetStep.VERIFY; error = "" }
                            )

                            PinResetStep.CONFIRM -> ConfirmStep(
                                confirmPin = confirmPin,
                                newPin = newPin,
                                onPinChange = { confirmPin = it; error = "" },
                                onContinue = {
                                    when {
                                        confirmPin != newPin ->
                                            fail("PINs do not match. Re-enter to confirm.")

                                        authorizedUser == null -> {
                                            fail("Session expired. Start over.")
                                            step = PinResetStep.VERIFY
                                        }

                                        else -> {
                                            isWorking = true
                                            viewModel.resetPin(
                                                username = authorizedUser!!.username,
                                                newPin = newPin,
                                                onSuccess = {
                                                    isWorking = false
                                                    error = ""
                                                    step = PinResetStep.COMPLETE
                                                },
                                                onFailure = {
                                                    isWorking = false
                                                    fail(it)
                                                }
                                            )
                                        }
                                    }
                                },
                                onBack = { step = PinResetStep.CHOOSE; error = "" }
                            )

                            PinResetStep.COMPLETE -> CompleteStep(onDone = onCompleted)
                        }
                    }
                }
            }

            if (error.isNotEmpty()) {
                // Persistent, non-blocking error: deliberately not auto-dismissed,
                // because the user must be able to read any lockout timing before
                // deciding whether to retry.
                Surface(
                    shape = ExpressiveCardShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_reset_error")
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

/**
 * Step 1 — prove ownership with the existing credential.
 *
 * Accepts the legacy 6-digit PIN via `verifyPinResetAuthorization`. When several
 * accounts are pending, the choices are rendered as a list rather than a free-text
 * field so the user recognises their name instead of recalling it.
 */
@Composable
private fun VerifyStep(
    pendingUsers: List<User>,
    username: String,
    onUsernameChange: (String) -> Unit,
    currentPin: String,
    onPinChange: (String) -> Unit,
    onContinue: () -> Unit,
    isWorking: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "StorePoint now uses a ${SecurityHelper.PIN_LENGTH}-digit PIN. Enter your " +
                "current PIN to confirm it's you, then choose a new one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (pendingUsers.size > 1) {
            // Serial Position Effect: the most likely account is listed first.
            val ordered = listOfNotNull(
                pendingUsers.firstOrNull { it.username == username }
            ) + pendingUsers.filter { it.username != username }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Which account needs updating?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                ordered.forEach { candidate ->
                    val selected = candidate.username == username
                    Surface(
                        shape = ExpressiveCardShape,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_reset_user_${candidate.username}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUsernameChange(candidate.username) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    candidate.username,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                           else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    candidate.role,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Account Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("pin_reset_username")
            )
        }

        Column(Modifier.fillMaxWidth()) {
            Text(
                "Current PIN",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            // Keypad, not the IME: this wizard is the only way an admin proves
            // ownership, and a system keyboard would cover the wizard's instructions.
            PinPadEntry(
                pin = currentPin,
                onPinChange = onPinChange,
                isMasked = true,
                isEnabled = !isWorking,
                modifier = Modifier.fillMaxWidth().testTag("pin_reset_current_pin")
            )
        }

        ExpressiveButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().testTag("pin_reset_verify_continue"),
            label = if (isWorking) "Checking…" else "Continue",
            icon = Icons.Default.Shield,
            variant = ExpressiveButtonVariant.FILLED,
            size = ExpressiveButtonSize.L,
            enabled = !isWorking
        )
    }
}

/** Step 2 — choose the new 4-digit PIN, with live weak-PIN feedback. */
@Composable
private fun ChooseStep(
    newPin: String,
    onPinChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val isWeak = newPin.isNotEmpty() && SecurityHelper.isWeakPin(newPin)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Pick a ${SecurityHelper.PIN_LENGTH}-digit PIN you'll remember. " +
                "You'll use it to sign in if your fingerprint doesn't work.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(Modifier.fillMaxWidth()) {
            Text(
                "New ${SecurityHelper.PIN_LENGTH}-Digit PIN",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            ExpressiveOtpPinInput(
                pin = newPin,
                onPinChange = onPinChange,
                isMasked = true,
                isError = isWeak,
                modifier = Modifier.fillMaxWidth().testTag("pin_reset_new_pin")
            )
        }

        // Immediate, specific guidance beats a generic error after submission
        // (Doherty Threshold: tell the user what is wrong while they can still fix it).
        Text(
            text = when {
                isWeak -> "Avoid that one — it's one of the first tried."
                newPin.isNotEmpty() -> "✓ Good choice"
                else -> SecurityHelper.pinPolicyHint()
            },
            style = MaterialTheme.typography.labelSmall,
            color = when {
                isWeak -> MaterialTheme.colorScheme.error
                newPin.isNotEmpty() -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExpressiveButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).testTag("pin_reset_back"),
                label = "Back",
                icon = Icons.Default.ArrowBack,
                variant = ExpressiveButtonVariant.TEXT,
                size = ExpressiveButtonSize.M
            )
            ExpressiveButton(
                onClick = onContinue,
                modifier = Modifier.weight(1f).testTag("pin_reset_choose_continue"),
                label = "Continue",
                variant = ExpressiveButtonVariant.FILLED,
                size = ExpressiveButtonSize.L,
                enabled = newPin.length == SecurityHelper.PIN_LENGTH
            )
        }
    }
}

/** Step 3 — re-enter the new PIN to catch typos before committing. */
@Composable
private fun ConfirmStep(
    confirmPin: String,
    newPin: String,
    onPinChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val mismatch = confirmPin.isNotEmpty() && confirmPin != newPin
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Enter your new PIN once more to confirm.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(Modifier.fillMaxWidth()) {
            Text(
                "Confirm PIN",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            ExpressiveOtpPinInput(
                pin = confirmPin,
                onPinChange = onPinChange,
                isMasked = true,
                isError = mismatch,
                modifier = Modifier.fillMaxWidth().testTag("pin_reset_confirm_pin")
            )
        }

        Text(
            text = when {
                mismatch -> "These don't match yet."
                confirmPin.length == SecurityHelper.PIN_LENGTH -> "✓ Match confirmed"
                else -> "${confirmPin.length}/${SecurityHelper.PIN_LENGTH} digits"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when {
                mismatch -> MaterialTheme.colorScheme.error
                confirmPin.length == SecurityHelper.PIN_LENGTH -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExpressiveButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).testTag("pin_reset_confirm_back"),
                label = "Back",
                icon = Icons.Default.ArrowBack,
                variant = ExpressiveButtonVariant.TEXT,
                size = ExpressiveButtonSize.M
            )
            ExpressiveButton(
                onClick = onContinue,
                modifier = Modifier.weight(1f).testTag("pin_reset_confirm_continue"),
                label = "Save PIN",
                icon = Icons.Default.CheckCircle,
                variant = ExpressiveButtonVariant.FILLED,
                size = ExpressiveButtonSize.L,
                enabled = confirmPin.length == SecurityHelper.PIN_LENGTH && !mismatch
            )
        }
    }
}

/**
 * Step 4 — success.
 *
 * Peak-End Rule: the flow ends on a clear, celebratory confirmation so the migration is
 * remembered as a completed task rather than an ordeal.
 */
@Composable
private fun CompleteStep(onDone: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Text(
            "Your new PIN is ready.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Sign in with it next time, or enrol a fingerprint for one-tap access.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ExpressiveButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().testTag("pin_reset_done"),
            label = "Continue to Sign In",
            icon = Icons.Default.Login,
            variant = ExpressiveButtonVariant.FILLED,
            size = ExpressiveButtonSize.L
        )
    }
}
