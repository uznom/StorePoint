package com.munzo.storepoint.ui

import androidx.lifecycle.viewModelScope
import com.munzo.storepoint.data.Transaction
import com.munzo.storepoint.data.TransactionItem
import com.munzo.storepoint.util.EscPosHelper
import kotlinx.coroutines.launch

internal fun StorePointViewModel.hardwareUpdatePrinterSettingsImpl(type: String, ip: String, port: Int, btMac: String, autoKick: Boolean, is80mm: Boolean) {
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

internal fun StorePointViewModel.hardwareSetPaperWidth80mmImpl(is80: Boolean) {
    hardwareUpdatePrinterSettingsImpl(printerType.value, printerIpAddress.value, printerPort.value, printerBtMac.value, isAutoKickDrawerEnabled.value, is80)
}

internal fun StorePointViewModel.hardwareSetAutoKickDrawerEnabledImpl(enabled: Boolean) {
    hardwareUpdatePrinterSettingsImpl(printerType.value, printerIpAddress.value, printerPort.value, printerBtMac.value, enabled, is80mmThermal.value)
}

internal fun StorePointViewModel.hardwareTestPrinterImpl(onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch {
        val testBytes = EscPosHelper.buildTestTicket(is80mm = is80mmThermal.value, kickDrawer = isAutoKickDrawerEnabled.value)
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
                    onSuccess = { onResult(true, "Test ticket printed successfully (Bluetooth).") },
                    onFailure = { onResult(false, "Bluetooth test failed: ${it.localizedMessage}") }
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
        val type = printerType.value
        val kickBytes = EscPosHelper.buildDrawerKickBytes()
        when (type) {
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
        val receiptBytes = EscPosHelper.buildReceiptEscPos(cfg, transaction, items, is80mm = is80, kickDrawerOnPrint = autoKick)

        when (type) {
            EscPosHelper.PrinterType.NETWORK_ESCPOS.name -> {
                val res = EscPosHelper.printOverNetwork(printerIpAddress.value, printerPort.value, receiptBytes)
                res.fold(
                    onSuccess = { onCompleted(true, "Receipt printed over Network ESC/POS.") },
                    onFailure = { onCompleted(false, "Network printer error: ${it.localizedMessage}") }
                )
            }
            EscPosHelper.PrinterType.BLUETOOTH_ESCPOS.name -> {
                val res = EscPosHelper.printOverBluetooth(printerBtMac.value, receiptBytes)
                res.fold(
                    onSuccess = { onCompleted(true, "Receipt printed over Bluetooth ESC/POS.") },
                    onFailure = { onCompleted(false, "Bluetooth printer error: ${it.localizedMessage}") }
                )
            }
            else -> {
                onCompleted(false, "Printer type set to System Spooler. Use standard Print Dialog.")
            }
        }
    }
}
