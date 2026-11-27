package com.munzo.storepoint.ui

import androidx.lifecycle.viewModelScope
import com.munzo.storepoint.data.DrawerTransaction
import com.munzo.storepoint.data.ReturnItem
import com.munzo.storepoint.data.ReturnTransaction
import com.munzo.storepoint.data.StorePointRepository
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.data.TransactionItem
import com.munzo.storepoint.util.EscPosHelper
import com.munzo.storepoint.util.UsbPrinterHelper
import kotlinx.coroutines.launch

internal fun StorePointViewModel.hardwareUpdatePrinterSettingsImpl(
    type: String,
    ip: String,
    port: Int,
    btMac: String,
    autoKick: Boolean,
    is80mm: Boolean
) {
    prefs.edit()
        .putString("printer_type", type)
        .putString("printer_ip", ip)
        .putInt("printer_port", port)
        .putString("printer_bt_mac", btMac)
        .putBoolean("auto_kick_drawer", autoKick)
        .putBoolean("is_80mm_thermal", is80mm)
        .apply()
    printerType.value = type
    printerIpAddress.value = ip
    printerPort.value = port
    printerBtMac.value = btMac
    isAutoKickDrawerEnabled.value = autoKick
    is80mmThermal.value = is80mm
}

internal fun StorePointViewModel.hardwareSetPrinterTypeImpl(type: String) {
    hardwareUpdatePrinterSettingsImpl(type, printerIpAddress.value, printerPort.value, printerBtMac.value, isAutoKickDrawerEnabled.value, is80mmThermal.value)
}

internal fun StorePointViewModel.hardwareSetPrinterNetworkConfigImpl(ip: String, port: Int) {
    hardwareUpdatePrinterSettingsImpl(printerType.value, ip, port, printerBtMac.value, isAutoKickDrawerEnabled.value, is80mmThermal.value)
}

internal fun StorePointViewModel.hardwareSetPrinterBluetoothMacImpl(mac: String) {
    hardwareUpdatePrinterSettingsImpl(printerType.value, printerIpAddress.value, printerPort.value, mac, isAutoKickDrawerEnabled.value, is80mmThermal.value)
}

internal fun StorePointViewModel.hardwareSetPrinterBluetoothConfigImpl(mac: String, name: String) {
    prefs.edit()
        .putString("printer_type", EscPosHelper.PrinterType.BLUETOOTH_ESCPOS.name)
        .putString("printer_bt_mac", mac)
        .putString("printer_device_name", name)
        .apply()
    printerType.value = EscPosHelper.PrinterType.BLUETOOTH_ESCPOS.name
    printerBtMac.value = mac
    printerDeviceName.value = name
}

internal fun StorePointViewModel.hardwareSetPrinterUsbConfigImpl(identifier: String, name: String) {
    prefs.edit()
        .putString("printer_type", EscPosHelper.PrinterType.USB_ESCPOS.name)
        .putString("printer_usb_id", identifier)
        .putString("printer_device_name", name)
        .apply()
    printerType.value = EscPosHelper.PrinterType.USB_ESCPOS.name
    printerUsbIdentifier.value = identifier
    printerDeviceName.value = name
}

internal fun StorePointViewModel.hardwareSetPrinterAutoCutterImpl(hasCutter: Boolean) {
    prefs.edit().putBoolean("printer_has_cutter", hasCutter).apply()
    printerHasAutoCutter.value = hasCutter
}

internal fun StorePointViewModel.hardwareSetPaperWidth80mmImpl(is80: Boolean) {
    hardwareUpdatePrinterSettingsImpl(printerType.value, printerIpAddress.value, printerPort.value, printerBtMac.value, isAutoKickDrawerEnabled.value, is80)
    // By default, 80mm printers typically have auto-cutters, while 58mm XP-58 Plus use manual tear bars
    if (!is80) {
        hardwareSetPrinterAutoCutterImpl(false)
    }
}

internal fun StorePointViewModel.hardwareSetAutoKickDrawerEnabledImpl(enabled: Boolean) {
    hardwareUpdatePrinterSettingsImpl(printerType.value, printerIpAddress.value, printerPort.value, printerBtMac.value, enabled, is80mmThermal.value)
}

internal fun StorePointViewModel.hardwareTestPrinterImpl(onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch {
        val testBytes = EscPosHelper.buildTestTicket(
            is80mm = is80mmThermal.value,
            kickDrawer = isAutoKickDrawerEnabled.value,
            hasAutoCutter = printerHasAutoCutter.value
        )
        when (printerType.value) {
            EscPosHelper.PrinterType.NETWORK_ESCPOS.name -> {
                val res = EscPosHelper.printOverNetwork(printerIpAddress.value, printerPort.value, testBytes)
                res.fold(
                    onSuccess = { onResult(true, "Test ticket printed successfully (Network LAN).") },
                    onFailure = { onResult(false, "Network test failed: ${it.localizedMessage}") }
                )
            }
            EscPosHelper.PrinterType.BLUETOOTH_ESCPOS.name -> {
                val res = EscPosHelper.printOverBluetooth(printerBtMac.value, testBytes)
                res.fold(
                    onSuccess = { onResult(true, "Test ticket printed successfully (Bluetooth XP-58/Thermal).") },
                    onFailure = { onResult(false, "Bluetooth test failed: ${it.localizedMessage}") }
                )
            }
            EscPosHelper.PrinterType.USB_ESCPOS.name -> {
                val res = UsbPrinterHelper.printOverUsb(context, printerUsbIdentifier.value, testBytes)
                res.fold(
                    onSuccess = { onResult(true, "Test ticket printed successfully (USB OTG).") },
                    onFailure = { onResult(false, "USB print failed: ${it.localizedMessage}") }
                )
            }
            else -> {
                onResult(true, "System Spooler mode active. Connect thermal printer for direct testing.")
            }
        }
    }
}

internal fun StorePointViewModel.hardwareKickCashDrawerImpl(onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch {
        val kickBytes = EscPosHelper.buildDrawerKickBytes()
        when (printerType.value) {
            EscPosHelper.PrinterType.NETWORK_ESCPOS.name -> {
                val res = EscPosHelper.printOverNetwork(printerIpAddress.value, printerPort.value, kickBytes)
                res.fold(
                    onSuccess = { onResult(true, "Cash drawer kick pulse sent (Network).") },
                    onFailure = { onResult(false, "Network drawer kick failed: ${it.localizedMessage}") }
                )
            }
            EscPosHelper.PrinterType.BLUETOOTH_ESCPOS.name -> {
                val res = EscPosHelper.printOverBluetooth(printerBtMac.value, kickBytes)
                res.fold(
                    onSuccess = { onResult(true, "Cash drawer kick pulse sent (Bluetooth).") },
                    onFailure = { onResult(false, "Bluetooth drawer kick failed: ${it.localizedMessage}") }
                )
            }
            EscPosHelper.PrinterType.USB_ESCPOS.name -> {
                val res = UsbPrinterHelper.printOverUsb(context, printerUsbIdentifier.value, kickBytes)
                res.fold(
                    onSuccess = { onResult(true, "Cash drawer kick pulse sent (USB).") },
                    onFailure = { onResult(false, "USB drawer kick failed: ${it.localizedMessage}") }
                )
            }
            else -> {
                onResult(true, "Cash drawer manual release signal dispatched.")
            }
        }
    }
}

internal fun StorePointViewModel.hardwarePrintReceiptImpl(
    transaction: Transaction,
    items: List<TransactionItem>,
    onCompleted: (Boolean, String) -> Unit
) {
    viewModelScope.launch {
        val cfg = storeConfig.value ?: run {
            onCompleted(false, "Store configuration not found.")
            return@launch
        }
        val type = printerType.value
        val autoKick = isAutoKickDrawerEnabled.value
        val is80 = is80mmThermal.value
        val hasCutter = printerHasAutoCutter.value
        val receiptBytes = EscPosHelper.buildReceiptEscPos(
            storeConfig = cfg,
            transaction = transaction,
            items = items,
            is80mm = is80,
            kickDrawerOnPrint = autoKick,
            hasAutoCutter = hasCutter,
            customHeader = receiptHeader.value,
            customFooter = receiptFooter.value,
            storeTin = storeTin.value,
            storeAddress = storeAddress.value,
            printBarcode = printReceiptBarcode.value
        )

        val label = "Receipt #TX-${transaction.id}"
        lastPrintedReceipt.value = LastPrintedReceiptPayload(label, receiptBytes)
        offlineDispatchEscPosOrQueueImpl(
            jobType = "RECEIPT",
            label = label,
            bytes = receiptBytes,
            enqueueOnFailure = true,
            onCompleted = onCompleted
        )
    }
}

internal fun StorePointViewModel.hardwarePrintRefundReceiptImpl(
    returnTransaction: ReturnTransaction,
    items: List<ReturnItem>,
    onCompleted: (Boolean, String) -> Unit
) {
    viewModelScope.launch {
        val cfg = storeConfig.value ?: run {
            onCompleted(false, "Store configuration not found.")
            return@launch
        }
        val type = printerType.value
        val autoKick = isAutoKickDrawerEnabled.value && returnTransaction.refundMethod.contains("CASH", ignoreCase = true)
        val is80 = is80mmThermal.value
        val hasCutter = printerHasAutoCutter.value
        val receiptBytes = EscPosHelper.buildRefundReceiptEscPos(
            storeConfig = cfg,
            returnTransaction = returnTransaction,
            items = items,
            is80mm = is80,
            kickDrawerOnPrint = autoKick,
            hasAutoCutter = hasCutter,
            customHeader = receiptHeader.value,
            customFooter = receiptFooter.value,
            storeTin = storeTin.value,
            storeAddress = storeAddress.value,
            printBarcode = printReceiptBarcode.value
        )

        offlineDispatchEscPosOrQueueImpl(
            jobType = "REFUND",
            label = "Refund #RET-${returnTransaction.id}",
            bytes = receiptBytes,
            enqueueOnFailure = true,
            onCompleted = onCompleted
        )
    }
}

internal fun StorePointViewModel.hardwarePrintDrawerSlipImpl(
    drawerTransaction: DrawerTransaction,
    balanceBefore: Double? = null,
    balanceAfter: Double? = null,
    onCompleted: (Boolean, String) -> Unit = { _, _ -> }
) {
    viewModelScope.launch {
        val cfg = storeConfig.value ?: run {
            onCompleted(false, "Store configuration not found.")
            return@launch
        }
        val type = printerType.value
        val is80 = is80mmThermal.value
        val hasCutter = printerHasAutoCutter.value
        val slipBytes = EscPosHelper.buildDrawerSlipEscPos(
            storeConfig = cfg,
            drawerTransaction = drawerTransaction,
            balanceBefore = balanceBefore,
            balanceAfter = balanceAfter,
            is80mm = is80,
            kickDrawerOnPrint = false,
            hasAutoCutter = hasCutter,
            customHeader = receiptHeader.value,
            storeTin = storeTin.value,
            printBarcode = printReceiptBarcode.value
        )

        offlineDispatchEscPosOrQueueImpl(
            jobType = "DRAWER_SLIP",
            label = "Drawer Slip #DRW-${drawerTransaction.id}",
            bytes = slipBytes,
            enqueueOnFailure = true,
            onCompleted = onCompleted
        )
    }
}

internal fun StorePointViewModel.hardwareLogCashInOutDrawerTransactionImpl(
    type: String, // "CASH_IN" or "CASH_OUT"
    amount: Double,
    reason: String,
    kickDrawer: Boolean = true,
    printSlip: Boolean = true,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    viewModelScope.launch {
        val session = activeSession.value
        if (session == null) {
            onResult(false, "No active cashier shift session. Open a session before drawer cash movements.")
            return@launch
        }
        if (amount <= 0.0) {
            onResult(false, "Amount must be greater than zero.")
            return@launch
        }
        val cleanReason = reason.trim().ifBlank {
            if (type == "CASH_IN") "Petty cash float injection" else "Operational cash disbursement"
        }

        val currentCash = getCurrentDrawerCash()
        val isCashOut = type.equals("CASH_OUT", ignoreCase = true) || type.equals("PAYOUT", ignoreCase = true)
        if (isCashOut && currentCash < amount) {
            val curr = storeConfig.value?.currencySymbol ?: "₱"
            onResult(false, "Insufficient cash in drawer (Available: $curr${String.format(java.util.Locale.US, "%.2f", currentCash)}, Required: $curr${String.format(java.util.Locale.US, "%.2f", amount)})")
            return@launch
        }

        val signedAmount = if (isCashOut) -StorePointRepository.roundMoney(amount) else StorePointRepository.roundMoney(amount)
        val balanceBefore = currentCash
        val balanceAfter = StorePointRepository.roundMoney(currentCash + signedAmount)
        val cashier = activeUser.value?.username ?: session.cashierUsername

        val tx = DrawerTransaction(
            sessionId = session.id,
            timestamp = System.currentTimeMillis(),
            cashierUsername = cashier,
            amount = signedAmount,
            type = if (isCashOut) "CASH_OUT" else "CASH_IN",
            reason = cleanReason
        )

        try {
            val txId = repository.logDrawerTransaction(tx)
            val insertedTx = tx.copy(id = txId.toInt())

            if (kickDrawer) {
                hardwareKickCashDrawerImpl { _, _ -> }
            }

            if (printSlip) {
                hardwarePrintDrawerSlipImpl(insertedTx, balanceBefore, balanceAfter) { _, _ -> }
            }

            val actionDesc = if (isCashOut) "Cash-Out disbursement" else "Cash-In injection"
            val curr = storeConfig.value?.currencySymbol ?: "₱"
            onResult(true, "Successfully logged $actionDesc of $curr${String.format(java.util.Locale.US, "%.2f", amount)}.")
        } catch (e: Exception) {
            onResult(false, "Failed to log drawer transaction: ${e.localizedMessage}")
        }
    }
}

internal fun StorePointViewModel.hardwarePrintZReadingImpl(
    zTicketBytes: ByteArray,
    onCompleted: (Boolean, String) -> Unit
) {
    offlineDispatchEscPosOrQueueImpl(
        jobType = "Z_READING",
        label = "Shift Z-Reading",
        bytes = zTicketBytes,
        enqueueOnFailure = true,
        onCompleted = onCompleted
    )
}
