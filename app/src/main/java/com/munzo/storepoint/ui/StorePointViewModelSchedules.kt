package com.munzo.storepoint.ui

import androidx.lifecycle.viewModelScope
import com.munzo.storepoint.data.DrawerTransaction
import com.munzo.storepoint.data.PaymentSchedule
import kotlinx.coroutines.launch

// --- Payment Schedule methods ---
internal fun StorePointViewModel.schedulesAddPaymentScheduleImpl(title: String, amount: Double, dueDate: String, category: String, priority: String, paymentMethod: String = "CASH") {
    viewModelScope.launch {
        val schedule = PaymentSchedule(
            title = title,
            amount = amount,
            dueDate = dueDate,
            category = category,
            priority = priority,
            isPaid = false,
            paymentMethod = paymentMethod
        )
        repository.savePaymentSchedule(schedule)
    }
}

internal fun StorePointViewModel.schedulesTogglePaymentScheduleStatusImpl(schedule: PaymentSchedule) {
    viewModelScope.launch {
        val updated = schedule.copy(isPaid = !schedule.isPaid)
        repository.updatePaymentSchedule(updated)
        
        if (updated.isPaid) {
            if (updated.paymentMethod == "CASH") {
                val activeSess = activeSession.value
                val currentCashier = activeUser.value?.username ?: "ADMIN"
                if (activeSess != null) {
                    repository.logDrawerTransaction(
                        DrawerTransaction(
                            sessionId = activeSess.id,
                            timestamp = System.currentTimeMillis(),
                            cashierUsername = currentCashier,
                            amount = -updated.amount,
                            type = "PAYOUT",
                            reason = "Bill Paid: ${updated.category} - ${updated.title}"
                        )
                    )
                }
            } else if (updated.paymentMethod == "GCASH") {
                val config = storeConfig.value
                if (config != null) {
                    val nextGCash = (config.gcashBalance - updated.amount).coerceAtLeast(0.0)
                    repository.saveStoreConfig(config.copy(gcashBalance = nextGCash))
                }
            }
        } else {
            // Reversal when marked unpaid
            if (schedule.paymentMethod == "CASH") {
                val activeSess = activeSession.value
                val currentCashier = activeUser.value?.username ?: "ADMIN"
                if (activeSess != null) {
                    repository.logDrawerTransaction(
                        DrawerTransaction(
                            sessionId = activeSess.id,
                            timestamp = System.currentTimeMillis(),
                            cashierUsername = currentCashier,
                            amount = updated.amount,
                            type = "PAYOUT_REVERSAL",
                            reason = "Bill Payment Reversed: ${updated.category} - ${updated.title}"
                        )
                    )
                }
            } else if (schedule.paymentMethod == "GCASH") {
                val config = storeConfig.value
                if (config != null) {
                    val nextGCash = config.gcashBalance + updated.amount
                    repository.saveStoreConfig(config.copy(gcashBalance = nextGCash))
                }
            }
        }
    }
}

internal fun StorePointViewModel.schedulesDeletePaymentScheduleImpl(schedule: PaymentSchedule) {
    viewModelScope.launch {
        repository.deletePaymentSchedule(schedule)
    }
}
