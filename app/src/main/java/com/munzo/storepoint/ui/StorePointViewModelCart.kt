package com.munzo.storepoint.ui

import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.munzo.storepoint.data.*
import com.munzo.storepoint.util.DatabaseBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/** Effective base units for a cart line, accounting for the selected UOM multiplier (H2). */
internal fun StorePointViewModel.cartEffectiveUnits(productId: Int, quantity: Int): Int {
    val multiplier = _selectedCartUoms.value[productId]?.multiplier ?: 1
    return quantity * multiplier
}

internal fun StorePointViewModel.cartSelectUomImpl(productId: Int, uom: UomOption) {
    val currentMap = _selectedCartUoms.value.toMutableMap()
    currentMap[productId] = uom
    _selectedCartUoms.value = currentMap
}

internal fun StorePointViewModel.cartAddToCartImpl(product: Product, quantity: Int = 1) {
    if (product.stockCount <= 0) {
        Toast.makeText(context, "${product.name} is out of stock!", Toast.LENGTH_SHORT).show()
        return
    }

    val currentMap = _cartMap.value.toMutableMap()
    val existing = currentMap[product.id]

    if (existing != null) {
        val targetQty = existing.second + quantity
        if (cartEffectiveUnits(product.id, targetQty) > product.stockCount) {
            Toast.makeText(context, "Cannot add more. Limit of ${product.stockCount} base units reached for the selected pack size.", Toast.LENGTH_SHORT).show()
            return
        }
        currentMap[product.id] = existing.copy(second = targetQty)
    } else {
        val effectiveUnits = cartEffectiveUnits(product.id, quantity)
        if (effectiveUnits > product.stockCount) {
            val uomLabel = _selectedCartUoms.value[product.id]?.name ?: "this unit"
            Toast.makeText(context, "Cannot add $quantity x ${uomLabel}: requires $effectiveUnits base units but only ${product.stockCount} in stock.", Toast.LENGTH_SHORT).show()
            return
        }
        currentMap[product.id] = Pair(product, quantity)
    }
    _cartMap.value = currentMap
}

internal fun StorePointViewModel.cartUpdateQuantityImpl(product: Product, quantity: Int) {
    if (quantity <= 0) {
        cartRemoveFromCartImpl(product)
        return
    }
    if (cartEffectiveUnits(product.id, quantity) > product.stockCount) {
        Toast.makeText(context, "Limit of ${product.stockCount} base units reached for the selected pack size.", Toast.LENGTH_SHORT).show()
        return
    }

    val currentMap = _cartMap.value.toMutableMap()
    if (currentMap.containsKey(product.id)) {
        currentMap[product.id] = Pair(product, quantity)
        _cartMap.value = currentMap
    }
}

internal fun StorePointViewModel.cartRemoveFromCartImpl(product: Product) {
    val currentMap = _cartMap.value.toMutableMap()
    currentMap.remove(product.id)
    _cartMap.value = currentMap
    val uoms = _selectedCartUoms.value.toMutableMap()
    uoms.remove(product.id)
    _selectedCartUoms.value = uoms
}

internal fun StorePointViewModel.cartClearCartImpl() {
    _cartMap.value = emptyMap()
    _selectedCartUoms.value = emptyMap()
}

internal fun StorePointViewModel.cartHandleBarcodeScanImpl(barcode: String, quantity: Int = 1) {
    viewModelScope.launch {
        var product: Product? = null
        var matchedUom: UomOption? = null
        
        val trimmedBarcode = barcode.trim()
        if (trimmedBarcode.isNotEmpty()) {
            for (p in allProducts.value) {
                if (p.barcode.equals(trimmedBarcode, ignoreCase = true)) {
                    product = p
                    break
                }
                if (p.hasStick10s && p.barcode10s.isNotBlank() && p.barcode10s.equals(trimmedBarcode, ignoreCase = true)) {
                    product = p
                    matchedUom = UomOption("10s Pack", 10, p.priceStick10s)
                    break
                }
                if (p.hasStick20s && p.barcode20s.isNotBlank() && p.barcode20s.equals(trimmedBarcode, ignoreCase = true)) {
                    product = p
                    matchedUom = UomOption("20s Pack", 20, p.priceStick20s)
                    break
                }
                if (p.hasReam && p.barcodeReam.isNotBlank() && p.barcodeReam.equals(trimmedBarcode, ignoreCase = true)) {
                    product = p
                    matchedUom = UomOption("Ream", 200, p.priceReam)
                    break
                }
                if (p.hasMasterCase && p.barcodeMasterCase.isNotBlank() && p.barcodeMasterCase.equals(trimmedBarcode, ignoreCase = true)) {
                    product = p
                    matchedUom = UomOption("Master Case", 10000, p.priceMasterCase)
                    break
                }
                if (p.hasCustomUom && p.barcodeCustomUom.isNotBlank() && p.barcodeCustomUom.equals(trimmedBarcode, ignoreCase = true)) {
                    product = p
                    matchedUom = UomOption(p.customUomName, p.customUomMultiplier, p.customUomPrice)
                    break
                }
            }
        }
        
        if (product == null && trimmedBarcode.isNotEmpty()) {
            val variant = repository.getVariantByBarcode(trimmedBarcode)
            if (variant != null) {
                product = allProducts.value.find { it.id == variant.productId } ?: repository.getProductById(variant.productId).firstOrNull()
                if (product != null) {
                    matchedUom = UomOption(variant.variantName, variant.multiplier, variant.price)
                }
            }
        }

        if (product == null && trimmedBarcode.isNotEmpty()) {
            product = repository.getProductByBarcode(trimmedBarcode)
        }
        
        if (product == null && trimmedBarcode.isNotEmpty()) {
            product = allProducts.value.find { prod ->
                prod.name.contains(trimmedBarcode, ignoreCase = true)
            }
        }

        if (product != null) {
            cartAddToCartImpl(product, quantity)
            if (matchedUom != null) {
                cartSelectUomImpl(product.id, matchedUom)
            }
            playBeep()
            val tagStr = if (matchedUom != null) " (${matchedUom.name})" else ""
            Toast.makeText(context, "Added: $quantity x ${product.name}$tagStr", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No product matches scan query '$barcode'.", Toast.LENGTH_LONG).show()
        }
    }
}

internal fun StorePointViewModel.cartParkActiveTransactionImpl(note: String) {
    if (_cartMap.value.isEmpty()) {
        Toast.makeText(context, "Cannot park an empty cart.", Toast.LENGTH_SHORT).show()
        return
    }
    viewModelScope.launch {
        repository.parkTransaction(note, _cartMap.value.values.toList())
        cartClearCartImpl()
        Toast.makeText(context, "Transaction successfully parked ($note)", Toast.LENGTH_SHORT).show()
    }
}

internal fun StorePointViewModel.cartResumeParkedTransactionImpl(parkedId: Int, onComplete: () -> Unit = {}) {
    viewModelScope.launch {
        val items = repository.getParkedItems(parkedId)
        val resumedCartItems = mutableListOf<Pair<Product, Int>>()

        for (item in items) {
            val dbProd = allProducts.value.find { it.id == item.productId }
            if (dbProd != null) {
                resumedCartItems.add(Pair(dbProd, item.quantity))
            } else {
                val stubProduct = Product(
                    id = item.productId,
                    name = item.productName,
                    categoryId = 0,
                    price = item.price,
                    stockCount = item.quantity,
                    barcode = ""
                )
                resumedCartItems.add(Pair(stubProduct, item.quantity))
            }
        }

        _cartMap.value = resumedCartItems.associateBy { it.first.id }.toMutableMap()
        repository.deleteParkedTransaction(parkedId)
        onComplete()
        Toast.makeText(context, "Parked transaction resumed successfully.", Toast.LENGTH_SHORT).show()
    }
}

internal fun StorePointViewModel.cartDeleteParkedTransactionImpl(parkedId: Int) {
    viewModelScope.launch {
        repository.deleteParkedTransaction(parkedId)
        Toast.makeText(context, "Parked transaction cleared.", Toast.LENGTH_SHORT).show()
    }
}

internal fun StorePointViewModel.cartExecuteCheckoutImpl(
    paymentMethod: String,
    cashPaid: Double,
    subtotal: Double,
    taxAmount: Double,
    totalAmount: Double,
    checkoutItems: List<Pair<Product, Int>>? = null,
    customerName: String = "",
    onSuccess: (Transaction) -> Unit
) {
    val itemsToProcess = checkoutItems ?: _cartMap.value.values.toList()
    if (itemsToProcess.isEmpty()) {
        Toast.makeText(context, "Shopping cart is empty.", Toast.LENGTH_SHORT).show()
        return
    }

    viewModelScope.launch {
        val cashier = activeUser.value?.username ?: "system_user"
        
        val mappedItemsForCheckout = itemsToProcess.map { item ->
            val prod = item.first
            val qty = item.second
            val uom = _selectedCartUoms.value[prod.id] ?: UomOption("Base Unit", 1, prod.price)
            CartItemDetails(
                product = prod,
                quantity = qty,
                uomName = uom.name,
                multiplier = uom.multiplier,
                selectedPrice = uom.price
            )
        }

        var gcashDelta = 0.0
        var smartLoadDelta = 0.0
        var globeLoadDelta = 0.0

        for (item in mappedItemsForCheckout) {
            val prod = item.product
            val qty = item.quantity
            
            val isCashIn = prod.name.contains("Cash In", ignoreCase = true) || prod.name.contains("Cash-In", ignoreCase = true)
            val isCashOut = prod.name.contains("Cash Out", ignoreCase = true) || prod.name.contains("Cash-Out", ignoreCase = true)
            val isLoad = prod.name.contains("Load", ignoreCase = true)
            
            val amtMatch = """(?:Amt|Amount):\s*([\d.]+)""".toRegex(RegexOption.IGNORE_CASE).find(prod.name)
            val amt = amtMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

            val feeMatch = """Fee:\s*([\d.]+)""".toRegex(RegexOption.IGNORE_CASE).find(prod.name)
            val parsedFee = feeMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 2.0

            val bankFeeMatch = """BankFee:\s*([\d.]+)""".toRegex(RegexOption.IGNORE_CASE).find(prod.name)
            val bankFee = bankFeeMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            
            if (isLoad) {
                // Retailer load wallet cost has 2% rebate: e.g. load 20 costs retailer 19.60
                val loadAmt = if (amt > 0.0) amt else (item.selectedPrice - parsedFee).coerceAtLeast(0.0)
                val retailerWalletCost = StorePointRepository.roundMoney(loadAmt * 0.98)
                val isSmart = prod.name.contains("Smart", ignoreCase = true) || prod.name.contains("TNT", ignoreCase = true)
                if (isSmart) {
                    smartLoadDelta -= retailerWalletCost * qty
                } else {
                    // Globe / TM
                    globeLoadDelta -= retailerWalletCost * qty
                }
            } else if (isCashIn && amt > 0.0) {
                gcashDelta -= (amt + bankFee) * qty
            } else if (isCashOut && amt > 0.0) {
                gcashDelta += amt * qty
            } else if (prod.name.contains("GCash Cash-In", ignoreCase = true)) {
                gcashDelta -= item.selectedPrice * qty
            } else if (prod.name.contains("GCash Cash-Out", ignoreCase = true)) {
                gcashDelta += item.selectedPrice * qty
            }
        }
        val normGcashDelta = StorePointRepository.roundMoney(gcashDelta)
        val normSmartLoadDelta = StorePointRepository.roundMoney(smartLoadDelta)
        val normGlobeLoadDelta = StorePointRepository.roundMoney(globeLoadDelta)

        try {
            val tx = repository.checkout(
                cashierUsername = cashier,
                cartItems = mappedItemsForCheckout,
                paymentMethod = paymentMethod,
                cashPaid = cashPaid,
                subtotal = subtotal,
                taxAmount = taxAmount,
                totalAmount = totalAmount,
                gcashDelta = normGcashDelta,
                smartLoadDelta = normSmartLoadDelta,
                globeLoadDelta = normGlobeLoadDelta,
                customerName = customerName.trim()
            )

            if (checkoutItems != null) {
                val checkedOutIds = checkoutItems.map { it.first.id }.toSet()
                _cartMap.value = _cartMap.value.filterKeys { it !in checkedOutIds }.toMutableMap()
            } else {
                cartClearCartImpl()
            }
            onSuccess(tx)

            // Silently persist auto-backup snapshot to public internal storage for disaster recovery
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    DatabaseBackupManager.savePersistentSnapshot(context, db, tx.id)
                } catch (e: Throwable) {
                    android.util.Log.e("StorePointCheckout", "Silent transaction auto-backup failed: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, e.localizedMessage ?: "Checkout failed.", Toast.LENGTH_LONG).show()
        }
    }
}
