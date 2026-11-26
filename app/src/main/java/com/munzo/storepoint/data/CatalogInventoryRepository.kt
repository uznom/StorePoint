package com.munzo.storepoint.data

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

    // --- Categories ---
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun saveCategory(category: Category) {
        categoryDao.insertCategory(category)
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
        productDao.insertProduct(product)
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
        productDao.updateStock(productId, newStock)
    }

    suspend fun updateStockAndCost(productId: Int, newStock: Int, newCost: Double) {
        productDao.updateStockAndCost(productId, newStock, newCost)
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
        productVariantDao.insertVariant(variant)

    suspend fun updateVariantStock(id: Int, newStock: Int) {
        productVariantDao.updateVariantStock(id, newStock)
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
