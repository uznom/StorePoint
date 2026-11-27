package com.munzo.storepoint.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Domain delegate handling catalog entities: categories, products, variants, and inventory movements.
 */
internal class CatalogInventoryRepository(private val db: AppDatabase) {

    private val categoryDao = db.categoryDao
    private val productDao = db.productDao
    private val productVariantDao = db.productVariantDao
    private val inventoryMovementDao = db.inventoryMovementDao
    private val drawerTransactionDao = db.drawerTransactionDao

    // --- Categories ---
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun saveCategory(category: Category) {
        if (category.id > 0) {
            categoryDao.updateCategory(category)
        } else {
            categoryDao.insertCategory(category)
        }
    }

    suspend fun deleteCategory(id: Int, softDelete: Boolean = true) {
        if (softDelete) {
            categoryDao.archiveCategory(id)
        } else {
            categoryDao.deleteCategory(id)
        }
    }

    suspend fun unarchiveCategory(id: Int) {
        categoryDao.unarchiveCategory(id)
    }

    // --- Products ---
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()
    val archivedProducts: Flow<List<Product>> = productDao.getArchivedProducts()

    fun getProductById(id: Int): Flow<Product?> = productDao.getProductById(id)

    suspend fun getProductByBarcode(barcode: String): Product? = productDao.getProductByBarcode(barcode)

    suspend fun saveProduct(product: Product) {
        // CRITICAL: Never use INSERT OR REPLACE on an existing product row.
        // With PRAGMA foreign_keys = ON, SQLite executes DELETE + INSERT for REPLACE,
        // which triggers ON DELETE CASCADE and wipes product_variants and bill_of_materials.
        if (product.id > 0) {
            productDao.updateProduct(product)
        } else {
            productDao.insertProduct(product)
        }
    }

    suspend fun deleteProduct(id: Int, softDelete: Boolean = true) {
        if (softDelete) {
            productDao.archiveProduct(id)
        } else {
            productDao.deleteProduct(id)
        }
    }

    suspend fun unarchiveProduct(id: Int) {
        productDao.unarchiveProduct(id)
    }

    suspend fun updateStock(productId: Int, newStock: Int) {
        productDao.updateStock(productId, newStock.coerceAtLeast(0))
    }

    suspend fun updateStockAndCost(productId: Int, newStock: Int, newCost: Double) {
        val safeCost = StorePointRepository.roundMoney(newCost.coerceAtLeast(0.0))
        productDao.updateStockAndCost(productId, newStock.coerceAtLeast(0), safeCost)
    }

    suspend fun updateQuickSellPinned(productId: Int, pinned: Boolean) {
        productDao.updateQuickSellPinned(productId, pinned)
    }

    /**
     * Atomically applies a stock-in restock, optional unit-cost update, optional drawer payout,
     * and inventory movement log in one SQLite transaction using relative `stockCount + delta`
     * so concurrent checkouts cannot be overwritten by stale UI snapshots.
     */
    suspend fun restockProductAtomic(
        productId: Int,
        productName: String,
        baseUnitsAdded: Int,
        newUnitCost: Double?,
        actorUsername: String,
        reference: String,
        notes: String,
        drawerPayout: DrawerTransaction? = null
    ): Int = withContext(Dispatchers.IO) {
        require(baseUnitsAdded > 0) { "Restock quantity must be greater than zero." }
        if (newUnitCost != null) {
            require(!newUnitCost.isNaN() && !newUnitCost.isInfinite() && newUnitCost >= 0.0) {
                "Unit cost must be a valid non-negative number."
            }
        }
        var stockAfter = 0
        db.withTransaction {
            val stockBefore = queryProductStock(productId)
            if (drawerPayout != null) {
                drawerTransactionDao.insertDrawerTransaction(
                    drawerPayout.copy(amount = StorePointRepository.roundMoney(drawerPayout.amount))
                )
            }
            if (newUnitCost != null && newUnitCost > 0.0) {
                productDao.incrementStockAndUpdateCost(
                    productId,
                    baseUnitsAdded,
                    StorePointRepository.roundMoney(newUnitCost)
                )
            } else {
                productDao.incrementStock(productId, baseUnitsAdded)
            }
            stockAfter = queryProductStock(productId)
            inventoryMovementDao.insertMovementBlocking(
                InventoryMovementEntity(
                    productId = productId,
                    productName = productName,
                    timestamp = System.currentTimeMillis(),
                    actorUsername = actorUsername,
                    movementType = "RESTOCK",
                    quantityDelta = baseUnitsAdded.toDouble(),
                    stockBefore = stockBefore,
                    stockAfter = stockAfter,
                    reference = reference,
                    notes = notes
                )
            )
        }
        stockAfter
    }

    private fun queryProductStock(productId: Int): Int {
        val cursor = db.query("SELECT stockCount AS s FROM products WHERE id = ?", arrayOf<Any?>(productId))
        return cursor.use { if (it.moveToNext()) it.getInt(it.getColumnIndexOrThrow("s")) else 0 }
    }

    // --- Product Variants & Multi-UOM ---
    fun getVariantsForProduct(productId: Int): Flow<List<ProductVariant>> =
        productVariantDao.getVariantsForProduct(productId)

    suspend fun getVariantsForProductSync(productId: Int): List<ProductVariant> =
        productVariantDao.getVariantsForProductSync(productId)

    val allProductVariants: Flow<List<ProductVariant>> = productVariantDao.getAllVariants()

    suspend fun getVariantByBarcode(barcode: String): ProductVariant? =
        productVariantDao.getVariantByBarcode(barcode)

    suspend fun saveProductVariant(variant: ProductVariant): Long =
        if (variant.id > 0) {
            productVariantDao.updateVariant(variant)
            variant.id.toLong()
        } else {
            productVariantDao.insertVariant(variant)
        }

    suspend fun updateVariantStock(id: Int, newStock: Int) {
        productVariantDao.updateVariantStock(id, newStock.coerceAtLeast(0))
    }

    suspend fun deleteProductVariant(id: Int) {
        productVariantDao.deleteVariant(id)
    }

    suspend fun deleteVariantsForProduct(productId: Int) {
        productVariantDao.deleteVariantsForProduct(productId)
    }

    // --- Inventory Movements ---
    val allInventoryMovements: Flow<List<InventoryMovementEntity>> = inventoryMovementDao.getAllMovements()

    suspend fun logInventoryMovement(movement: InventoryMovementEntity): Long = withContext(Dispatchers.IO) {
        inventoryMovementDao.insertMovement(movement)
    }

    suspend fun getInventoryMovementsForProduct(productId: Int): List<InventoryMovementEntity> =
        withContext(Dispatchers.IO) {
            inventoryMovementDao.getMovementsForProductSync(productId)
        }

    // --- Relational Catalog Queries ---
    val allCategoriesWithProducts: Flow<List<CategoryWithProducts>> = categoryDao.getCategoriesWithProducts()
    val allProductsWithVariants: Flow<List<ProductWithVariants>> = productDao.getAllProductsWithVariants()
    fun getProductWithVariants(id: Int): Flow<ProductWithVariants?> = productDao.getProductWithVariants(id)
}
