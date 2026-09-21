package com.munzo.storepoint.ui

import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.munzo.storepoint.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

// --- Suppliers & Purchase Orders ---
internal fun StorePointViewModel.purchasesSaveSupplierImpl(supplier: Supplier, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
        if (supplier.id == 0) {
            repository.saveSupplier(supplier)
        } else {
            repository.updateSupplier(supplier)
        }
        onSuccess()
    }
}

internal fun StorePointViewModel.purchasesDeleteSupplierImpl(supplierId: Int, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
        repository.deleteSupplier(supplierId)
        onSuccess()
    }
}

internal fun StorePointViewModel.purchasesCreatePurchaseOrderImpl(
    supplier: Supplier,
    expectedDeliveryDate: Long?,
    items: List<Pair<Product, Pair<Int, Double>>>, // Product to (QuantityOrdered, UnitCost)
    notes: String,
    onSuccess: (PurchaseOrder) -> Unit
) {
    viewModelScope.launch {
        val totalCost = items.sumOf { it.second.first * it.second.second }
        val poNumber = "PO-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())

        val poItems = items.map { (prod, details) ->
            PurchaseOrderItem(
                purchaseOrderId = 0, // Overridden by createPurchaseOrderAtomic's raw SQL insert
                productId = prod.id,
                productName = prod.name,
                quantityOrdered = details.first,
                quantityReceived = 0,
                unitCost = details.second,
                totalCost = details.first * details.second,
                uomName = prod.baseUom
            )
        }

        // H1: PO header + all line items are written in one transaction.
        val po = repository.createPurchaseOrderAtomic(
            po = PurchaseOrder(
                poNumber = poNumber,
                supplierId = supplier.id,
                supplierName = supplier.name,
                orderDate = System.currentTimeMillis(),
                expectedDeliveryDate = expectedDeliveryDate,
                totalCost = totalCost,
                status = "ORDERED",
                paymentStatus = "UNPAID",
                notes = notes
            ),
            items = poItems
        )
        onSuccess(po)
    }
}

internal fun StorePointViewModel.purchasesReceivePurchaseOrderImpl(
    poId: Int,
    receivedItems: List<Pair<Int, Int>>, // ProductId to QuantityReceived
    paymentStatus: String, // "PAID" or "UNPAID"
    onSuccess: () -> Unit
) {
    viewModelScope.launch {
        try {
            val poItems = repository.getPurchaseOrderItemsSync(poId)
            val lines = receivedItems
                .filter { it.second > 0 }
                .map { (prodId, qtyRec) ->
                    val poi = poItems.find { it.productId == prodId }
                    val fallbackCost = allProducts.value.find { it.id == prodId }?.cost ?: 0.0
                    val unitCost = if (poi != null && poi.unitCost > 0.0) poi.unitCost else fallbackCost
                    StorePointRepository.PurchaseOrderReceiveLine(
                        productId = prodId,
                        quantityReceived = qtyRec,
                        newUnitCost = unitCost
                    )
                }

            // H1: stock/cost updates + PO header + PO line items in ONE transaction.
            repository.receivePurchaseOrderAtomic(
                poId = poId,
                receivedItems = lines,
                receivedDate = System.currentTimeMillis(),
                paymentStatus = paymentStatus
            )
            onSuccess()
        } catch (e: Exception) {
            Toast.makeText(context, e.localizedMessage ?: "Failed to receive purchase order.", Toast.LENGTH_LONG).show()
        }
    }
}

internal fun StorePointViewModel.purchasesDeletePurchaseOrderImpl(poId: Int, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
        repository.deletePurchaseOrder(poId)
        onSuccess()
    }
}
