package com.munzo.storepoint.ui

import androidx.lifecycle.viewModelScope
import com.munzo.storepoint.data.ReturnItem
import com.munzo.storepoint.data.ReturnTransaction
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.data.TransactionItem
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

internal fun StorePointViewModel.returnsGetTransactionWithItemsImpl(txId: Int, onResult: (Transaction?, List<TransactionItem>) -> Unit) {
    viewModelScope.launch {
        val tx = repository.getTransactionById(txId)
        val items = repository.getTransactionItemsSync(txId)
        onResult(tx, items)
    }
}

internal fun StorePointViewModel.returnsGetReturnItemsForReturnTxImpl(returnTxId: Int, onResult: (List<ReturnItem>) -> Unit) {
    viewModelScope.launch {
        val items = repository.getReturnItemsSync(returnTxId)
        onResult(items)
    }
}

internal fun StorePointViewModel.returnsProcessReturnImpl(
    originalTransactionId: Int,
    returnedItems: List<StorePointViewModel.ReturnItemDraft>,
    refundMethod: String, // "CASH", "STORE_CREDIT", "EXCHANGE"
    returnReason: String,
    customerName: String,
    notes: String,
    onSuccess: (ReturnTransaction) -> Unit,
    onFailure: (String) -> Unit
) {
    viewModelScope.launch {
        try {
            if (returnedItems.isEmpty()) {
                onFailure("No items selected for return.")
                return@launch
            }
            val totalRefund = returnedItems.sumOf { it.returnQuantity * it.unitRefundPrice }
            val cashier = activeUser.value?.username ?: "system_user"

            val returnItems = returnedItems.map { draft ->
                ReturnItem(
                    returnTransactionId = 0, // Overridden by processReturnAtomic's raw SQL insert
                    productId = draft.originalItem.productId,
                    productName = draft.originalItem.productName,
                    quantity = draft.returnQuantity,
                    unitPrice = draft.unitRefundPrice,
                    refundAmount = draft.returnQuantity * draft.unitRefundPrice,
                    uomName = draft.originalItem.uomName,
                    restockToInventory = draft.restockToInventory
                )
            }

            val originalItems = repository.getTransactionItemsSync(originalTransactionId)
            val totalOriginalQty = originalItems.sumOf { it.quantity }
            val pastReturns = repository.getReturnsForTransaction(originalTransactionId).firstOrNull() ?: emptyList()
            val previouslyReturnedQty = pastReturns.sumOf { pastReturn ->
                repository.getReturnItemsSync(pastReturn.id).sumOf { it.quantity }
            }
            val totalReturnedQty = previouslyReturnedQty + returnedItems.sumOf { it.returnQuantity }
            val newStatus = if (totalReturnedQty >= totalOriginalQty) "REFUNDED" else "PARTIALLY_REFUNDED"

            // H1: the whole return (header + items + restock + drawer payout + status) is one transaction.
            val returnTx = repository.processReturnAtomic(
                returnTx = ReturnTransaction(
                    originalTransactionId = originalTransactionId,
                    timestamp = System.currentTimeMillis(),
                    cashierUsername = cashier,
                    totalRefundAmount = totalRefund,
                    refundMethod = refundMethod,
                    returnReason = returnReason,
                    customerName = customerName,
                    notes = notes
                ),
                items = returnItems,
                cashRefund = refundMethod == "CASH",
                sessionId = activeSession.value?.id,
                newOriginalStatus = newStatus
            )

            playBeep()
            onSuccess(returnTx)
        } catch (e: Exception) {
            onFailure(e.localizedMessage ?: "Return processing failed.")
        }
    }
}
