package com.munzo.storepoint.ui

import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.munzo.storepoint.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val STOREPOINT_INVENTORY_HEADER = "STOREPOINT_INVENTORY_V1"

// --- Inventory Backup File Import / Export & App-Only Quick Share ---
internal fun StorePointViewModel.inventoryExportJsonImpl(): String {
    return inventoryExportPackageJson(appOnlySealed = false)
}

internal fun StorePointViewModel.inventoryExportPackageImpl(): String {
    val rawJson = inventoryExportPackageJson(appOnlySealed = true)
    return "$STOREPOINT_INVENTORY_HEADER\n$rawJson"
}

private fun StorePointViewModel.inventoryExportPackageJson(appOnlySealed: Boolean): String {
    try {
        val categoriesJson = org.json.JSONArray()
        for (cat in allCategories.value) {
            val obj = org.json.JSONObject()
            obj.put("id", cat.id)
            obj.put("name", cat.name)
            categoriesJson.put(obj)
        }

        val productsJson = org.json.JSONArray()
        for (prod in allProducts.value) {
            val obj = org.json.JSONObject()
            obj.put("id", prod.id)
            obj.put("name", prod.name)
            obj.put("categoryId", prod.categoryId)
            obj.put("price", prod.price)
            obj.put("cost", prod.cost)
            obj.put("stockCount", prod.stockCount)
            obj.put("barcode", prod.barcode)
            obj.put("expirationDate", prod.expirationDate)
            
            // Multi-UOM details
            obj.put("baseUom", prod.baseUom)
            obj.put("hasStick10s", prod.hasStick10s)
            obj.put("priceStick10s", prod.priceStick10s)
            obj.put("barcode10s", prod.barcode10s)
            obj.put("hasStick20s", prod.hasStick20s)
            obj.put("priceStick20s", prod.priceStick20s)
            obj.put("barcode20s", prod.barcode20s)
            obj.put("hasReam", prod.hasReam)
            obj.put("priceReam", prod.priceReam)
            obj.put("barcodeReam", prod.barcodeReam)
            obj.put("hasMasterCase", prod.hasMasterCase)
            obj.put("priceMasterCase", prod.priceMasterCase)
            obj.put("barcodeMasterCase", prod.barcodeMasterCase)
            obj.put("hasCustomUom", prod.hasCustomUom)
            obj.put("customUomName", prod.customUomName)
            obj.put("customUomMultiplier", prod.customUomMultiplier)
            obj.put("customUomPrice", prod.customUomPrice)
            obj.put("barcodeCustomUom", prod.barcodeCustomUom)

            val catName = allCategories.value.find { it.id == prod.categoryId }?.name ?: ""
            obj.put("categoryName", catName)
            productsJson.put(obj)
        }

        val root = org.json.JSONObject()
        root.put("app", "StorePoint")
        root.put("format", if (appOnlySealed) "spinventory" else "json")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("exportedBy", activeUser.value?.username ?: "staff")
        root.put("categories", categoriesJson)
        root.put("products", productsJson)
        return root.toString(4)
    } catch (e: Exception) {
        android.util.Log.e("StorePointViewModel", "Export failed", e)
        return ""
    }
}

internal fun StorePointViewModel.inventoryShareViaQuickShareImpl(context: android.content.Context) {
    try {
        val payload = inventoryExportPackageImpl()
        if (payload.isEmpty()) {
            Toast.makeText(context, "No inventory data found to share.", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = "storepoint_inventory_${System.currentTimeMillis()}.spinventory"
        val file = java.io.File(context.cacheDir, fileName)
        file.writeText(payload)

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/vnd.storepoint.inventory"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "StorePoint Inventory Package")
            putExtra(android.content.Intent.EXTRA_TEXT, "StorePoint Inventory Package (.spinventory) for Quick Share.")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = android.content.Intent.createChooser(shareIntent, "Quick Share Inventory (StorePoint App Only)").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.util.Log.e("StorePointViewModel", "Quick Share dispatch failed", e)
        Toast.makeText(context, "Quick Share failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

internal fun StorePointViewModel.inventoryImportJsonImpl(jsonStr: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
    inventoryImportPackageDetailedImpl(
        rawContent = jsonStr,
        onSuccess = { _, _ -> onSuccess() },
        onFailure = onFailure
    )
}

internal fun StorePointViewModel.inventoryImportPackageDetailedImpl(
    rawContent: String,
    onSuccess: (catCount: Int, prodCount: Int) -> Unit,
    onFailure: (String) -> Unit
) {
    viewModelScope.launch {
        try {
            withContext(Dispatchers.Default) {
                // Strip app-only header if present
                var cleanContent = rawContent.trim()
                if (cleanContent.startsWith(STOREPOINT_INVENTORY_HEADER)) {
                    cleanContent = cleanContent.removePrefix(STOREPOINT_INVENTORY_HEADER).trim()
                }

                val root = org.json.JSONObject(cleanContent)
                var importedCats = 0
                var importedProds = 0

                // 1. Categories Import
                val catArray = root.optJSONArray("categories")
                if (catArray != null) {
                    for (i in 0 until catArray.length()) {
                        val obj = catArray.getJSONObject(i)
                        val name = obj.getString("name")
                        val trimmed = name.trim()
                        val existing = allCategories.value.find { it.name.trim().lowercase() == trimmed.lowercase() }
                        if (existing == null) {
                            repository.saveCategory(Category(name = trimmed))
                            importedCats++
                        }
                    }
                }

                // Wait for DB write list to complete and fetch refreshed categories
                val refreshedCats = repository.allCategories.first()

                // 2. Products Import
                val prodArray = root.optJSONArray("products")
                if (prodArray != null) {
                    for (i in 0 until prodArray.length()) {
                        val obj = prodArray.getJSONObject(i)
                        val name = obj.getString("name")
                        val price = obj.getDouble("price")
                        val cost = obj.optDouble("cost", 0.0)
                        val stockCount = obj.getInt("stockCount")
                        val barcode = obj.getString("barcode")
                        val expDate = if (obj.isNull("expirationDate")) null else obj.getString("expirationDate")

                        // Multi-UOM fields
                        val baseUom = obj.optString("baseUom", "pc")
                        val hasStick10s = obj.optBoolean("hasStick10s", false)
                        val priceStick10s = obj.optDouble("priceStick10s", 0.0)
                        val barcode10s = obj.optString("barcode10s", "")
                        val hasStick20s = obj.optBoolean("hasStick20s", false)
                        val priceStick20s = obj.optDouble("priceStick20s", 0.0)
                        val barcode20s = obj.optString("barcode20s", "")
                        val hasReam = obj.optBoolean("hasReam", false)
                        val priceReam = obj.optDouble("priceReam", 0.0)
                        val barcodeReam = obj.optString("barcodeReam", "")
                        val hasMasterCase = obj.optBoolean("hasMasterCase", false)
                        val priceMasterCase = obj.optDouble("priceMasterCase", 0.0)
                        val barcodeMasterCase = obj.optString("barcodeMasterCase", "")
                        val hasCustomUom = obj.optBoolean("hasCustomUom", false)
                        val customUomName = obj.optString("customUomName", "Pack")
                        val customUomMultiplier = obj.optInt("customUomMultiplier", 1)
                        val customUomPrice = obj.optDouble("customUomPrice", 0.0)
                        val barcodeCustomUom = obj.optString("barcodeCustomUom", "")

                        // Try resolve category
                        val categoryName = obj.optString("categoryName", "")
                        var catId = obj.optInt("categoryId", 1)
                        if (categoryName.isNotEmpty()) {
                            val matchedCat = refreshedCats.find { it.name.trim().lowercase() == categoryName.trim().lowercase() }
                            if (matchedCat != null) {
                                catId = matchedCat.id
                            }
                        }

                        // Save product model mapping
                        val existing = allProducts.value.find { it.barcode.isNotEmpty() && it.barcode == barcode }
                        if (existing != null) {
                            repository.saveProduct(
                                existing.copy(
                                    name = name,
                                    price = price,
                                    cost = cost,
                                    stockCount = stockCount,
                                    expirationDate = expDate,
                                    categoryId = catId,
                                    baseUom = baseUom,
                                    hasStick10s = hasStick10s,
                                    priceStick10s = priceStick10s,
                                    barcode10s = barcode10s,
                                    hasStick20s = hasStick20s,
                                    priceStick20s = priceStick20s,
                                    barcode20s = barcode20s,
                                    hasReam = hasReam,
                                    priceReam = priceReam,
                                    barcodeReam = barcodeReam,
                                    hasMasterCase = hasMasterCase,
                                    priceMasterCase = priceMasterCase,
                                    barcodeMasterCase = barcodeMasterCase,
                                    hasCustomUom = hasCustomUom,
                                    customUomName = customUomName,
                                    customUomMultiplier = customUomMultiplier,
                                    customUomPrice = customUomPrice,
                                    barcodeCustomUom = barcodeCustomUom
                                )
                            )
                        } else {
                            repository.saveProduct(
                                Product(
                                    name = name,
                                    categoryId = catId,
                                    price = price,
                                    cost = cost,
                                    stockCount = stockCount,
                                    barcode = barcode,
                                    expirationDate = expDate,
                                    baseUom = baseUom,
                                    hasStick10s = hasStick10s,
                                    priceStick10s = priceStick10s,
                                    barcode10s = barcode10s,
                                    hasStick20s = hasStick20s,
                                    priceStick20s = priceStick20s,
                                    barcode20s = barcode20s,
                                    hasReam = hasReam,
                                    priceReam = priceReam,
                                    barcodeReam = barcodeReam,
                                    hasMasterCase = hasMasterCase,
                                    priceMasterCase = priceMasterCase,
                                    barcodeMasterCase = barcodeMasterCase,
                                    hasCustomUom = hasCustomUom,
                                    customUomName = customUomName,
                                    customUomMultiplier = customUomMultiplier,
                                    customUomPrice = customUomPrice,
                                    barcodeCustomUom = barcodeCustomUom
                                )
                            )
                        }
                        importedProds++
                    }
                }
                withContext(Dispatchers.Main) {
                    onSuccess(importedCats, importedProds)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onFailure(e.localizedMessage ?: "Parsing backed up file failed.")
            }
        }
    }
}

// --- Product & Category Management (CRUD with full access logs) ---
internal fun StorePointViewModel.inventorySaveCategoryImpl(category: Category) {
    viewModelScope.launch {
        repository.saveCategory(category)
        Toast.makeText(context, "Category saved successfully.", Toast.LENGTH_SHORT).show()
    }
}

internal fun StorePointViewModel.inventoryDeleteCategoryImpl(id: Int) {
    viewModelScope.launch {
        repository.deleteCategory(id)
        Toast.makeText(context, "Category removed.", Toast.LENGTH_SHORT).show()
    }
}

internal fun StorePointViewModel.inventorySaveProductImpl(product: Product) {
    viewModelScope.launch {
        repository.saveProduct(product)
        Toast.makeText(context, "Product inventory registry updated.", Toast.LENGTH_SHORT).show()
    }
}

internal fun StorePointViewModel.inventoryDeleteProductImpl(id: Int) {
    viewModelScope.launch {
        repository.deleteProduct(id)
        Toast.makeText(context, "Product removed from database.", Toast.LENGTH_SHORT).show()
    }
}

internal fun StorePointViewModel.inventoryRestockProductWithUomImpl(
    product: Product,
    quantity: Int,
    multiplier: Int,
    totalCost: Double?,
    payFromDrawer: Boolean = false,
    onPayoutError: (String) -> Unit = {}
) {
    viewModelScope.launch {
        if (quantity <= 0 || multiplier <= 0) {
            onPayoutError("Quantity and multiplier must be greater than zero.")
            return@launch
        }
        val baseUnitsAdded = quantity * multiplier
        val newStock = product.stockCount + baseUnitsAdded
        val currSymbol = storeConfig.value?.currencySymbol ?: "$"
        
        if (payFromDrawer && totalCost != null && totalCost > 0.0) {
            val session = activeSession.value
            if (session == null) {
                onPayoutError("No active cashier shift session found to payout from.")
                return@launch
            }
            val currentCash = getCurrentDrawerCash()
            if (currentCash < totalCost) {
                onPayoutError("Insufficient cash in drawer to pay supplier (Drawer: $currSymbol${String.format("%.2f", currentCash)}, Required: $currSymbol${String.format("%.2f", totalCost)}).")
                return@launch
            }
            
            // Pre-check passed, proceed with Supplier Payout.
            val cashier = activeUser.value?.username ?: session.cashierUsername
            val tx = DrawerTransaction(
                sessionId = session.id,
                timestamp = System.currentTimeMillis(),
                cashierUsername = cashier,
                amount = -totalCost,
                type = "PAYOUT",
                reason = "Restock - ${product.name} (${quantity}x, Mult: ${multiplier}x)"
            )
            repository.logDrawerTransaction(tx)
            
            val newUnitCost = StorePointRepository.roundMoney(totalCost / baseUnitsAdded)
            repository.updateStockAndCost(product.id, newStock, newUnitCost)
            val costStr = "$currSymbol${String.format(java.util.Locale.getDefault(), "%.2f", newUnitCost)}"
            Toast.makeText(context, "Supplier paid from drawer! Received $baseUnitsAdded base units of ${product.name}! Updated unit cost to $costStr", Toast.LENGTH_LONG).show()
        } else {
            if (totalCost != null && totalCost > 0.0) {
                val newUnitCost = StorePointRepository.roundMoney(totalCost / baseUnitsAdded)
                repository.updateStockAndCost(product.id, newStock, newUnitCost)
                val costStr = "$currSymbol${String.format(java.util.Locale.getDefault(), "%.2f", newUnitCost)}"
                Toast.makeText(context, "Received $baseUnitsAdded base units of ${product.name}! Updated unit cost to $costStr", Toast.LENGTH_LONG).show()
            } else {
                repository.updateStock(product.id, newStock)
                Toast.makeText(context, "Received $baseUnitsAdded base units of ${product.name}!", Toast.LENGTH_LONG).show()
            }
        }
    }
}

internal fun StorePointViewModel.inventoryGetActiveUomsForProductImpl(product: Product): List<UomOption> {
    val categoriesList = allCategories.value
    val category = categoriesList.find { it.id == product.categoryId }
    val isCigarette = category?.name?.contains("Cigarette", ignoreCase = true) == true
    val list = mutableListOf<UomOption>()
    
    val baseName = if (isCigarette) "Stick" else if (product.baseUom.isNotBlank()) product.baseUom else "Piece"
    list.add(UomOption(baseName, 1, product.price))
    
    // Add dynamic product variants / pack sizes
    val variants = allProductVariants.value.filter { it.productId == product.id }
    for (v in variants) {
        list.add(UomOption(v.variantName, v.multiplier, v.price))
    }

    if (isCigarette) {
        if (product.hasStick10s) {
            list.add(UomOption("10s Pack", 10, product.priceStick10s))
        }
        if (product.hasStick20s) {
            list.add(UomOption("20s Pack", 20, product.priceStick20s))
        }
        if (product.hasReam) {
            list.add(UomOption("Ream", 200, product.priceReam))
        }
        if (product.hasMasterCase) {
            list.add(UomOption("Master Case", 10000, product.priceMasterCase))
        }
    } else {
        if (product.hasCustomUom) {
            list.add(UomOption(product.customUomName, product.customUomMultiplier, product.customUomPrice))
        }
    }
    return list
}

// --- Product Variants & Multi-UOM ---
internal fun StorePointViewModel.inventoryGetVariantsForProductImpl(productId: Int): Flow<List<ProductVariant>> {
    return repository.getVariantsForProduct(productId)
}

internal fun StorePointViewModel.inventorySaveProductVariantImpl(variant: ProductVariant, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
        repository.saveProductVariant(variant)
        onSuccess()
    }
}

internal fun StorePointViewModel.inventoryDeleteProductVariantImpl(id: Int, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
        repository.deleteProductVariant(id)
        onSuccess()
    }
}
