package com.munzo.storepoint.ui

import android.util.Base64
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.munzo.storepoint.data.CsvCatalogImportResult
import com.munzo.storepoint.data.CustomerAccount
import com.munzo.storepoint.data.CustomerCreditEntry
import com.munzo.storepoint.data.PendingPrintJob
import com.munzo.storepoint.data.ShiftXReadingSummary
import com.munzo.storepoint.util.CatalogCsvCodec
import com.munzo.storepoint.util.CatalogCsvImportPreview
import com.munzo.storepoint.util.CatalogCsvRow
import com.munzo.storepoint.util.EscPosHelper
import com.munzo.storepoint.util.EscPosXReadingFormatter
import com.munzo.storepoint.util.UsbPrinterHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cached copy of the most recently built thermal receipt for 1-tap Reprint Last Receipt. */
data class LastPrintedReceiptPayload(
    val label: String,
    val bytes: ByteArray,
    val createdAt: Long = System.currentTimeMillis()
)

// =========================================================================
// Shared ESC/POS Dispatch + Automatic Offline Queue Fallback
// =========================================================================

internal suspend fun StorePointViewModel.offlineSendRawEscPosBytes(bytes: ByteArray): Result<String> {
    return when (printerType.value) {
        EscPosHelper.PrinterType.NETWORK_ESCPOS.name -> {
            EscPosHelper.printOverNetwork(printerIpAddress.value, printerPort.value, bytes)
                .map { "Printed via Network ESC/POS." }
        }
        EscPosHelper.PrinterType.BLUETOOTH_ESCPOS.name -> {
            EscPosHelper.printOverBluetooth(printerBtMac.value, bytes)
                .map { "Printed via Bluetooth ESC/POS." }
        }
        EscPosHelper.PrinterType.USB_ESCPOS.name -> {
            UsbPrinterHelper.printOverUsb(context, printerUsbIdentifier.value, bytes)
                .map { "Printed via USB OTG ESC/POS." }
        }
        else -> {
            Result.failure(
                IllegalStateException("Thermal printer not connected (System Spooler active).")
            )
        }
    }
}

internal fun StorePointViewModel.offlineDispatchEscPosOrQueueImpl(
    jobType: String,
    label: String,
    bytes: ByteArray,
    enqueueOnFailure: Boolean = true,
    onCompleted: (Boolean, String) -> Unit
) {
    viewModelScope.launch {
        val result = offlineSendRawEscPosBytes(bytes)
        result.fold(
            onSuccess = { msg -> onCompleted(true, msg) },
            onFailure = { err ->
                val errorText = err.localizedMessage ?: "Printer communication failed"
                val shouldQueue = enqueueOnFailure &&
                    printerType.value != EscPosHelper.PrinterType.SYSTEM_SPOOLER.name
                if (shouldQueue) {
                    try {
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        repository.enqueuePrintJob(
                            jobType = jobType,
                            label = label,
                            payloadBase64 = b64,
                            error = errorText
                        )
                        onCompleted(false, "$errorText — Saved to Offline Print Queue for retry.")
                    } catch (_: Exception) {
                        onCompleted(false, errorText)
                    }
                } else {
                    onCompleted(false, errorText)
                }
            }
        )
    }
}

internal fun StorePointViewModel.offlineRetryPrintJobImpl(
    job: PendingPrintJob,
    onCompleted: (Boolean, String) -> Unit = { _, _ -> }
) {
    viewModelScope.launch {
        val bytes = try {
            Base64.decode(job.payloadBase64, Base64.DEFAULT)
        } catch (e: Exception) {
            repository.deletePrintJob(job.id)
            onCompleted(false, "Corrupted print payload removed.")
            return@launch
        }
        val res = offlineSendRawEscPosBytes(bytes)
        res.fold(
            onSuccess = { msg ->
                repository.deletePrintJob(job.id)
                onCompleted(true, "${job.label}: $msg")
            },
            onFailure = { err ->
                val errMsg = err.localizedMessage ?: "Retry failed"
                repository.markPrintJobRetryFailed(job.id, errMsg)
                onCompleted(false, "${job.label}: $errMsg")
            }
        )
    }
}

internal fun StorePointViewModel.offlineRetryAllPendingPrintJobsImpl(
    onCompleted: (Int, Int) -> Unit = { _, _ -> }
) {
    viewModelScope.launch {
        val jobs = repository.getAllPendingPrintJobsSync()
        var succeeded = 0
        var failed = 0
        for (job in jobs) {
            val bytes = try {
                Base64.decode(job.payloadBase64, Base64.DEFAULT)
            } catch (_: Exception) {
                repository.deletePrintJob(job.id)
                failed++
                continue
            }
            val res = offlineSendRawEscPosBytes(bytes)
            if (res.isSuccess) {
                repository.deletePrintJob(job.id)
                succeeded++
            } else {
                repository.markPrintJobRetryFailed(job.id, res.exceptionOrNull()?.localizedMessage ?: "Retry failed")
                failed++
            }
        }
        onCompleted(succeeded, failed)
    }
}

internal fun StorePointViewModel.offlineDeletePrintJobImpl(jobId: Int) {
    viewModelScope.launch { repository.deletePrintJob(jobId) }
}

internal fun StorePointViewModel.offlineClearPrintQueueImpl() {
    viewModelScope.launch { repository.clearAllPrintJobs() }
}

internal fun StorePointViewModel.offlineReprintLastReceiptImpl(
    onCompleted: (Boolean, String) -> Unit
) {
    viewModelScope.launch {
        val cached = lastPrintedReceipt.value
        if (cached != null) {
            offlineDispatchEscPosOrQueueImpl(
                jobType = "RECEIPT",
                label = "${cached.label} (Reprint)",
                bytes = cached.bytes,
                enqueueOnFailure = true,
                onCompleted = onCompleted
            )
            return@launch
        }
        // Fallback: rebuild ESC/POS bytes from the most recent completed transaction in SQLite
        val latestTx = allTransactions.value.firstOrNull()
        val cfg = storeConfig.value
        if (latestTx == null || cfg == null) {
            onCompleted(false, "No recent receipt available to reprint.")
            return@launch
        }
        val items = repository.getTransactionItemsSync(latestTx.id)
        val bytes = EscPosHelper.buildReceiptEscPos(
            storeConfig = cfg,
            transaction = latestTx,
            items = items,
            is80mm = is80mmThermal.value,
            kickDrawerOnPrint = false,
            hasAutoCutter = printerHasAutoCutter.value,
            customHeader = receiptHeader.value,
            customFooter = receiptFooter.value,
            storeTin = storeTin.value,
            storeAddress = storeAddress.value,
            printBarcode = printReceiptBarcode.value
        )
        lastPrintedReceipt.value = LastPrintedReceiptPayload("Receipt #TX-${latestTx.id}", bytes)
        offlineDispatchEscPosOrQueueImpl(
            jobType = "RECEIPT",
            label = "Receipt #TX-${latestTx.id} (Reprint)",
            bytes = bytes,
            enqueueOnFailure = true,
            onCompleted = onCompleted
        )
    }
}

// =========================================================================
// Suki Tab (Offline Customer Credit / Utang)
// =========================================================================

internal fun StorePointViewModel.offlineSaveCustomerAccountImpl(
    account: CustomerAccount,
    onResult: (Boolean, String, Long?) -> Unit = { _, _, _ -> }
) {
    viewModelScope.launch {
        if (account.name.isBlank()) {
            onResult(false, "Customer name is required.", null)
            return@launch
        }
        try {
            val id = repository.saveCustomerAccount(account)
            onResult(true, "Saved Suki account: ${account.name.trim()}", id)
        } catch (e: Exception) {
            onResult(false, e.localizedMessage ?: "Failed saving Suki account.", null)
        }
    }
}

internal fun StorePointViewModel.offlineDeleteCustomerAccountImpl(
    customerId: Int,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    viewModelScope.launch {
        try {
            val existing = repository.getCustomerByIdSync(customerId)
            if (existing != null && existing.currentBalance > 0.01) {
                onResult(false, "Cannot delete ${existing.name} with an unpaid balance. Settle balance first.")
                return@launch
            }
            repository.deleteCustomerAccount(customerId)
            onResult(true, "Customer account deleted.")
        } catch (e: Exception) {
            onResult(false, e.localizedMessage ?: "Failed deleting customer account.")
        }
    }
}

internal fun StorePointViewModel.offlineRecordCustomerPaymentImpl(
    customerId: Int,
    amount: Double,
    paymentMethod: String,
    notes: String = "",
    printSlip: Boolean = false,
    onResult: (Boolean, String, CustomerCreditEntry?) -> Unit = { _, _, _ -> }
) {
    viewModelScope.launch {
        val actor = activeUser.value?.username ?: "cashier"
        try {
            val entry = repository.recordCustomerPayment(
                customerId = customerId,
                amount = amount,
                paymentMethod = paymentMethod,
                actorUsername = actor,
                notes = notes
            )
            if (printSlip) {
                val updatedCustomer = repository.getCustomerByIdSync(customerId)
                val cfg = storeConfig.value
                if (updatedCustomer != null && cfg != null) {
                    val bytes = EscPosXReadingFormatter.buildSukiPaymentSlipTicket(
                        storeConfig = cfg,
                        customer = updatedCustomer,
                        entry = entry,
                        is80mm = is80mmThermal.value,
                        hasAutoCutter = printerHasAutoCutter.value
                    )
                    offlineDispatchEscPosOrQueueImpl(
                        jobType = "SUKI_PAYMENT",
                        label = "Suki Repayment: ${updatedCustomer.name}",
                        bytes = bytes,
                        enqueueOnFailure = true,
                        onCompleted = { _, _ -> }
                    )
                }
            }
            onResult(true, "Payment recorded successfully.", entry)
        } catch (e: Exception) {
            onResult(false, e.localizedMessage ?: "Failed to record payment.", null)
        }
    }
}

internal fun StorePointViewModel.offlineGetCustomerCreditLedgerImpl(customerId: Int): Flow<List<CustomerCreditEntry>> =
    repository.getLedgerForCustomer(customerId)

// =========================================================================
// Mid-Shift Live X-Reading (Non-Resetting)
// =========================================================================

internal fun StorePointViewModel.offlineComputeActiveShiftXReadingImpl(
    onResult: (ShiftXReadingSummary?) -> Unit
) {
    viewModelScope.launch {
        val summary = repository.computeActiveShiftXReading()
        onResult(summary)
    }
}

internal fun StorePointViewModel.offlinePrintXReadingHardwareImpl(
    summary: ShiftXReadingSummary,
    onCompleted: (Boolean, String) -> Unit
) {
    val cfg = storeConfig.value ?: run {
        onCompleted(false, "Store configuration unavailable.")
        return
    }
    val bytes = EscPosXReadingFormatter.buildMidShiftXReadingTicket(
        storeConfig = cfg,
        summary = summary,
        is80mm = is80mmThermal.value,
        hasAutoCutter = printerHasAutoCutter.value
    )
    offlineDispatchEscPosOrQueueImpl(
        jobType = "X_READING",
        label = "X-Reading #SES-${summary.sessionId}",
        bytes = bytes,
        enqueueOnFailure = true,
        onCompleted = onCompleted
    )
}

// =========================================================================
// Pinned Quick-Sell & Offline CSV Bulk Import / Export
// =========================================================================

internal fun StorePointViewModel.offlineSetProductQuickSellPinnedImpl(
    productId: Int,
    pinned: Boolean
) {
    viewModelScope.launch {
        repository.updateQuickSellPinned(productId, pinned)
        val msg = if (pinned) "Pinned to Quick-Sell bar" else "Removed from Quick-Sell bar"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}

internal fun StorePointViewModel.offlineBuildCatalogCsvStringImpl(): String =
    CatalogCsvCodec.exportProductsToCsv(allProducts.value, allCategories.value)

internal fun StorePointViewModel.offlinePreviewCatalogCsvImpl(
    csvContent: String,
    onResult: (CatalogCsvImportPreview) -> Unit
) {
    viewModelScope.launch {
        val preview = withContext(Dispatchers.Default) {
            CatalogCsvCodec.parseAndPreview(csvContent, allProducts.value)
        }
        onResult(preview)
    }
}

internal fun StorePointViewModel.offlineCommitCatalogCsvImportImpl(
    rows: List<CatalogCsvRow>,
    onResult: (Boolean, String, CsvCatalogImportResult?) -> Unit
) {
    viewModelScope.launch {
        if (rows.isEmpty()) {
            onResult(false, "No valid rows to import.", null)
            return@launch
        }
        try {
            val result = repository.applyCatalogCsvImport(rows)
            onResult(
                true,
                "Imported ${result.insertedCount} new & updated ${result.updatedCount} products.",
                result
            )
        } catch (e: Exception) {
            onResult(false, e.localizedMessage ?: "CSV import failed.", null)
        }
    }
}
