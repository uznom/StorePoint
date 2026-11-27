package com.munzo.storepoint.ui

import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.munzo.storepoint.data.StoreConfig
import com.munzo.storepoint.data.StorePointRepository
import com.munzo.storepoint.data.WalletLedgerEntry
import kotlinx.coroutines.launch

// --- Shift & Wallet Float Extensions ---

internal fun StorePointViewModel.shiftStartCashierShiftImpl(
    startingCash: Double,
    startingSmartLoad: Double? = null,
    startingGlobeLoad: Double? = null
) {
    viewModelScope.launch {
        if (!startingCash.isFinite() || startingCash < 0.0) {
            Toast.makeText(context, "Starting cash must be zero or greater.", Toast.LENGTH_SHORT).show()
            return@launch
        }
        val username = activeUser.value?.username ?: "system_user"
        val normCash = StorePointRepository.roundMoney(startingCash)
        val started = repository.startSession(username, normCash)
        if (started) {
            if (startingSmartLoad != null && startingSmartLoad.isFinite() && startingSmartLoad >= 0.0) {
                repository.reconcileWallet(
                    walletType = StorePointRepository.WalletType.SMART,
                    countedBalance = StorePointRepository.roundMoney(startingSmartLoad),
                    actorUsername = username,
                    notes = "Shift start float verification"
                )
            }
            if (startingGlobeLoad != null && startingGlobeLoad.isFinite() && startingGlobeLoad >= 0.0) {
                repository.reconcileWallet(
                    walletType = StorePointRepository.WalletType.GLOBE,
                    countedBalance = StorePointRepository.roundMoney(startingGlobeLoad),
                    actorUsername = username,
                    notes = "Shift start float verification"
                )
            }
            Toast.makeText(context, "Shift session started with ${storeConfig.value?.currencySymbol}$normCash starting cash drawer.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Cannot start shift: an active session is already running. End it first.", Toast.LENGTH_LONG).show()
        }
    }
}

internal fun StorePointViewModel.shiftCloseCashierShiftImpl(
    endingCash: Double,
    endingGCash: Double? = null,
    endingSmartLoad: Double? = null,
    endingGlobeLoad: Double? = null
) {
    viewModelScope.launch {
        if (!endingCash.isFinite() || endingCash < 0.0) {
            Toast.makeText(context, "Ending cash must be zero or greater.", Toast.LENGTH_SHORT).show()
            return@launch
        }
        val actor = activeUser.value?.username ?: "shift-close"
        val normEndingCash = StorePointRepository.roundMoney(endingCash)
        repository.endActiveSession(normEndingCash)
        if (endingGCash != null && endingGCash.isFinite() && endingGCash >= 0.0) {
            repository.reconcileWallet(
                walletType = StorePointRepository.WalletType.GCASH,
                countedBalance = StorePointRepository.roundMoney(endingGCash),
                actorUsername = actor,
                notes = "Shift close reconciliation"
            )
        }
        if (endingSmartLoad != null && endingSmartLoad.isFinite() && endingSmartLoad >= 0.0) {
            repository.reconcileWallet(
                walletType = StorePointRepository.WalletType.SMART,
                countedBalance = StorePointRepository.roundMoney(endingSmartLoad),
                actorUsername = actor,
                notes = "Shift close reconciliation"
            )
        }
        if (endingGlobeLoad != null && endingGlobeLoad.isFinite() && endingGlobeLoad >= 0.0) {
            repository.reconcileWallet(
                walletType = StorePointRepository.WalletType.GLOBE,
                countedBalance = StorePointRepository.roundMoney(endingGlobeLoad),
                actorUsername = actor,
                notes = "Shift close reconciliation"
            )
        }
        clearCart()
        Toast.makeText(context, "Shift session closed. Drawer totals updated offline.", Toast.LENGTH_SHORT).show()
    }
}

internal fun StorePointViewModel.shiftGetDrawerPayoutsInActiveSessionImpl(): Double {
    val session = activeSession.value ?: return 0.0
    return allDrawerTransactions.value.filter {
        it.sessionId == session.id
    }.sumOf { it.amount }
}

internal fun StorePointViewModel.shiftGetCurrentDrawerCashImpl(): Double {
    val session = activeSession.value ?: return 0.0
    val sales = allTransactions.value.filter {
        it.timestamp >= session.startTime && it.paymentMethod == "CASH" && it.status != "VOIDED" && it.status != "CANCELLED"
    }.sumOf { it.totalAmount }
    
    val payouts = allDrawerTransactions.value.filter {
        it.sessionId == session.id
    }.sumOf { it.amount }
    
    return StorePointRepository.roundMoney(session.startingCash + sales + payouts)
}

internal fun StorePointViewModel.shiftIsAdminActorImpl(): Boolean =
    activeUser.value?.role?.equals("ADMIN", ignoreCase = true) == true

internal fun StorePointViewModel.shiftReloadWalletImpl(
    walletLabel: String,
    amount: Double,
    reference: String = "",
    notes: String = "",
    onSuccess: (Double) -> Unit = {},
    onFailure: (String) -> Unit = {}
) {
    viewModelScope.launch {
        if (!shiftIsAdminActorImpl()) {
            onFailure("Only an Admin can reload a wallet float.")
            return@launch
        }
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            onFailure("Enter an amount greater than zero.")
            return@launch
        }
        val actor = activeUser.value?.username ?: "admin"
        val newBalance = repository.reloadWallet(
            walletType = StorePointRepository.canonicalWalletType(walletLabel),
            amount = amount,
            actorUsername = actor,
            reference = reference,
            notes = notes
        )
        if (newBalance == null) {
            onFailure("Could not reload the wallet. Check the amount and try again.")
        } else {
            Toast.makeText(
                context,
                "${StorePointRepository.canonicalWalletType(walletLabel)} reloaded: ${StorePointRepository.roundMoney(newBalance)}",
                Toast.LENGTH_SHORT
            ).show()
            onSuccess(newBalance)
        }
    }
}

internal fun StorePointViewModel.shiftReconcileWalletBalanceImpl(
    walletLabel: String,
    countedBalance: Double,
    notes: String = "",
    onSuccess: (Double) -> Unit = {},
    onFailure: (String) -> Unit = {}
) {
    viewModelScope.launch {
        if (countedBalance.isNaN() || countedBalance.isInfinite() || countedBalance < 0.0) {
            onFailure("Enter a valid counted balance (zero or greater).")
            return@launch
        }
        val actor = activeUser.value?.username ?: "shift-close"
        val newBalance = repository.reconcileWallet(
            walletType = StorePointRepository.canonicalWalletType(walletLabel),
            countedBalance = countedBalance,
            actorUsername = actor,
            notes = notes
        )
        if (newBalance == null) {
            onFailure("Could not reconcile the wallet balance.")
        } else {
            onSuccess(newBalance)
        }
    }
}

internal suspend fun StorePointViewModel.shiftGetSystemDerivedWalletBalanceImpl(walletLabel: String): Double =
    repository.getSystemDerivedBalanceSync(StorePointRepository.canonicalWalletType(walletLabel))

internal suspend fun StorePointViewModel.shiftGetRecentWalletHistoryImpl(walletLabel: String, limit: Int = 50): List<WalletLedgerEntry> =
    repository.getRecentWalletEntriesSync(StorePointRepository.canonicalWalletType(walletLabel), limit)

internal suspend fun StorePointViewModel.shiftRecordWalletConsumptionImpl(walletLabel: String, amount: Double, reference: String = "") {
    val actor = activeUser.value?.username ?: "cashier"
    repository.recordWalletConsumption(
        walletType = StorePointRepository.canonicalWalletType(walletLabel),
        amount = amount,
        actorUsername = actor,
        reference = reference
    )
}

internal fun StorePointViewModel.shiftUpdateDigitalServiceFeesImpl(
    loadFee: Double,
    gcashFee: Double,
    mayaFee: Double,
    mayaBankFee: Double
) {
    viewModelScope.launch {
        val currentConfig = repository.getStoreConfigSync()
        if (currentConfig != null) {
            val updated = currentConfig.copy(
                loadServiceFee = StorePointRepository.roundMoney(loadFee),
                gcashServiceFee = StorePointRepository.roundMoney(gcashFee),
                mayaServiceFee = StorePointRepository.roundMoney(mayaFee),
                mayaBankFee = StorePointRepository.roundMoney(mayaBankFee)
            )
            repository.saveStoreConfig(updated)
            Toast.makeText(context, "Digital services service charges updated!", Toast.LENGTH_SHORT).show()
        }
    }
}
