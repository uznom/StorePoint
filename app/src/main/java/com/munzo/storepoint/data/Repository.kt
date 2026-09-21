package com.munzo.storepoint.data

import androidx.room.withTransaction
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class StorePointRepository(private val db: AppDatabase) {

    companion object {
        fun roundMoney(amount: Double): Double {
            if (amount.isNaN() || amount.isInfinite()) return 0.0
            return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).toDouble()
        }

        /**
         * Calculates Philippine VAT (default 12%) from a VAT-inclusive gross retail amount.
         * Formula: amount / 1.12 * 0.12
         */
        fun calculateVatFromInclusive(amount: Double, vatPercent: Double = 12.0): Double {
            if (amount <= 0.0 || vatPercent <= 0.0 || amount.isNaN() || amount.isInfinite()) return 0.0
            val rateFraction = vatPercent / 100.0
            val vat = (amount / (1.0 + rateFraction)) * rateFraction
            return roundMoney(vat)
        }

        /**
         * Calculates Net VATable sales from a VAT-inclusive gross retail amount.
         * Formula: amount / 1.12 (or amount - VAT)
         */
        fun calculateVatableSalesFromInclusive(amount: Double, vatPercent: Double = 12.0): Double {
            if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) return 0.0
            val vat = calculateVatFromInclusive(amount, vatPercent)
            return roundMoney(amount - vat)
        }
    }

    // DAOs
    private val storeConfigDao = db.storeConfigDao
    private val userDao = db.userDao
    private val categoryDao = db.categoryDao
    private val productDao = db.productDao
    private val cashierSessionDao = db.cashierSessionDao
    private val transactionDao = db.transactionDao
    private val parkedTransactionDao = db.parkedTransactionDao
    private val drawerTransactionDao = db.drawerTransactionDao
    private val paymentScheduleDao = db.paymentScheduleDao
    private val returnDao = db.returnDao
    private val productVariantDao = db.productVariantDao
    private val supplierDao = db.supplierDao
    private val purchaseOrderDao = db.purchaseOrderDao

    // --- Store Config Operations ---
    val storeConfig: Flow<StoreConfig?> = storeConfigDao.getStoreConfig()
    
    suspend fun getStoreConfigSync(): StoreConfig? {
        return storeConfigDao.getStoreConfigSync()
    }

    suspend fun saveStoreConfig(config: StoreConfig) {
        storeConfigDao.insertStoreConfig(config)
    }

    // --- User Accounts ---
    val allUsers: Flow<List<User>> = userDao.getAllUsers()

    suspend fun getAllUsersSync(): List<User> {
        return userDao.getAllUsersSync()
    }

    suspend fun getUserByUsername(username: String): User? {
        return userDao.getUserSync(username)
    }

    suspend fun getUserByBarcode(barcode: String): User? {
        return userDao.getUserByBarcodeSync(barcode)
    }

    suspend fun saveUser(user: User) {
        userDao.insertUser(user)
    }

    suspend fun deleteUser(username: String) {
        userDao.deleteUser(username)
    }

    // --- Categories ---
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun saveCategory(category: Category) {
        categoryDao.insertCategory(category)
    }

    suspend fun deleteCategory(id: Int) {
        categoryDao.deleteCategory(id)
    }

    // --- Products ---
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()

    fun getProductById(id: Int): Flow<Product?> {
        return productDao.getProductById(id)
    }

    suspend fun getProductByBarcode(barcode: String): Product? {
        return productDao.getProductByBarcode(barcode)
    }

    suspend fun saveProduct(product: Product) {
        productDao.insertProduct(product)
    }

    suspend fun deleteProduct(id: Int) {
        productDao.deleteProduct(id)
    }

    suspend fun updateStock(productId: Int, newStock: Int) {
        productDao.updateStock(productId, newStock)
    }

    suspend fun updateStockAndCost(productId: Int, newStock: Int, newCost: Double) {
        productDao.updateStockAndCost(productId, newStock, newCost)
    }

    // --- Cashier Sessions ---
    val activeSession: Flow<CashierSession?> = cashierSessionDao.getActiveSession()
    val allCashierSessions: Flow<List<CashierSession>> = cashierSessionDao.getAllSessions()

    suspend fun getActiveSessionSync(): CashierSession? {
        return cashierSessionDao.getActiveSessionSync()
    }

    /**
     * Starts a drawer session only if no other ACTIVE session exists (M3 — single active drawer
     * session at a time, so drawer reconciliation stays well-defined).
     * Uses INSERT … WHERE NOT EXISTS to prevent the TOCTOU race where two coroutines could both
     * pass the check-then-insert window.
     * Returns true when the session started; false when an ACTIVE session is already running.
     */
    suspend fun startSession(username: String, startingCash: Double): Boolean = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val normStartingCash = roundMoney(startingCash)
        var started = false
        db.withTransaction {
            // Atomic guard: only insert if zero ACTIVE sessions exist.
            db.openHelper.writableDatabase.execSQL(
                """INSERT INTO cashier_sessions (cashierUsername, startTime, startingCash, status)
                   SELECT ?, ?, ?, 'ACTIVE'
                   WHERE NOT EXISTS (SELECT 1 FROM cashier_sessions WHERE status = 'ACTIVE')""",
                arrayOf<Any?>(username, timestamp, normStartingCash)
            )
            started = db.openHelper.writableDatabase.compileStatement("SELECT changes()").simpleQueryForLong() > 0
        }
        started
    }

    suspend fun endActiveSession(endingCash: Double) = withContext(Dispatchers.IO) {
        val active = cashierSessionDao.getActiveSessionSync()
        if (active != null) {
            val normEndingCash = roundMoney(endingCash)
            val updated = active.copy(
                endTime = System.currentTimeMillis(),
                endingCash = normEndingCash,
                status = "CLOSED"
            )
            cashierSessionDao.updateSession(updated)
        }
    }

    // --- Transactions ---
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val allTransactionItems: Flow<List<TransactionItem>> = transactionDao.getAllTransactionItems()

    fun getTransactionsByCashier(cashier: String): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByCashier(cashier)
    }

    fun getTransactionItems(transactionId: Int): Flow<List<TransactionItem>> {
        return transactionDao.getTransactionItems(transactionId)
    }

    /**
     * Executes a checkout ATOMICALLY:
     * 1. Rejects underpayment at the data boundary (H3) — never clamps.
     * 2. Validates every line against FRESH stock read inside the transaction; requires
     *    effectiveQty = quantity * uomMultiplier <= live stock (H2) — never clamps to 0.
     * 3. Inserts the header + all items, deducts stock, and applies the GCash wallet delta
     *    (M4) as ONE SQLite transaction. Any rejection rolls back everything — no orphaned
     *    headers, no partial stock deductions, no lost GCash updates.
     * @param gcashDelta Signed GCash wallet movement applied atomically (negative = payout
     *                   direction / cash-out). Rejects the sale if it would overdraw the wallet.
     */
    suspend fun checkout(
        cashierUsername: String,
        cartItems: List<CartItemDetails>, // Custom details including selected UOM and price
        paymentMethod: String,
        cashPaid: Double,
        subtotal: Double,
        taxAmount: Double,
        totalAmount: Double,
        gcashDelta: Double = 0.0,
        smartLoadDelta: Double = 0.0,
        globeLoadDelta: Double = 0.0,
        customerName: String = ""
    ): Transaction = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        var createdTransactionId = 0

        // Normalize all monetary amounts to exactly 2 decimal places
        val normSubtotal = roundMoney(subtotal)
        val normTax = roundMoney(taxAmount)
        val normTotal = roundMoney(totalAmount)
        val normCashPaid = roundMoney(cashPaid)
        val normChange = if (paymentMethod == "CASH") roundMoney(normCashPaid - normTotal) else 0.0

        db.withTransaction {
            // H3: Underpayment is rejected for cash sales with positive totals.
            if (paymentMethod == "CASH" && normTotal > 0.0 && normCashPaid < normTotal) {
                throw IllegalArgumentException("Insufficient payment: received $normCashPaid but the total is $normTotal.")
            }

            // H2: Pre-validate quantities (fail-fast with descriptive messages).
            for (item in cartItems) {
                val effectiveQty = item.quantity * item.multiplier
                if (effectiveQty <= 0) {
                    throw IllegalArgumentException("Invalid quantity for ${item.product.name}.")
                }
            }

            // M4: Validate the GCash wallet against a FRESH read; reject overdraft instead of clamping to 0.
            if (gcashDelta != 0.0) {
                val balance = queryGcashBalance()
                if (balance + gcashDelta < 0.0) {
                    throw IllegalStateException(
                        "Insufficient GCash wallet balance for this transaction (balance: $balance, required: ${-gcashDelta})."
                    )
                }
            }

            // Validate Smart Load wallet against a fresh read; reject overdraft
            if (smartLoadDelta != 0.0) {
                val balance = querySmartLoadBalance()
                if (balance + smartLoadDelta < 0.0) {
                    throw IllegalStateException(
                        "Insufficient Smart Load wallet balance for this transaction (balance: $balance, required: ${-smartLoadDelta})."
                    )
                }
            }

            // Validate Globe Load wallet against a fresh read; reject overdraft
            if (globeLoadDelta != 0.0) {
                val balance = queryGlobeLoadBalance()
                if (balance + globeLoadDelta < 0.0) {
                    throw IllegalStateException(
                        "Insufficient Globe Load wallet balance for this transaction (balance: $balance, required: ${-globeLoadDelta})."
                    )
                }
            }

            // Insert transaction header and capture the generated rowid.
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO transactions (timestamp, cashierUsername, subtotal, taxAmount, totalAmount, paymentMethod, cashPaid, changeAmount, status, customerName) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    timestamp, cashierUsername, normSubtotal, normTax, normTotal, paymentMethod, normCashPaid, normChange, "COMPLETED", customerName
                )
            )
            createdTransactionId = lastInsertRowId()

            // Insert transaction items & deduct stock.
            // H2 (TOCTOU fix): Use a conditional UPDATE with a WHERE guard so the stock can
            // never go below zero, even under concurrent coroutine dispatchers (audit C3).
            for (item in cartItems) {
                val product = item.product
                val uomName = item.uomName
                val productName = if (uomName.equals("Base Unit", ignoreCase = true) || uomName.equals("Piece", ignoreCase = true) || uomName.equals("Stick", ignoreCase = true)) {
                    product.name
                } else {
                    "${product.name} ($uomName)"
                }
                val normItemPrice = roundMoney(item.selectedPrice)
                val normItemCost = roundMoney(product.cost * item.multiplier)
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO transaction_items (transactionId, productId, productName, price, quantity, cost, uomName) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(createdTransactionId, product.id, productName, normItemPrice, item.quantity, normItemCost, uomName)
                )
                // Deduct physical inventory only for genuine catalog products (id > 0).
                // Digital retailing services (GCash Cash-In/Out, Load, Maya) have synthetic id <= 0.
                if (product.id > 0) {
                    val effectiveQty = item.quantity * item.multiplier
                    db.openHelper.writableDatabase.execSQL(
                        "UPDATE products SET stockCount = stockCount - ? WHERE id = ? AND stockCount >= ?",
                        arrayOf<Any?>(effectiveQty, product.id, effectiveQty)
                    )
                    // Verify the UPDATE actually touched a row; if not, stock was insufficient.
                    val changed = queryChanges()
                    if (changed == 0) {
                        val currentStock = queryStock(product.id)
                        throw IllegalStateException(
                            "Insufficient stock for ${product.name}: requires $effectiveQty base units, only $currentStock available."
                        )
                    }
                }
            }

            // M4: Persist the GCash wallet delta in the same transaction.
            if (gcashDelta != 0.0) {
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE store_config SET gcashBalance = gcashBalance + ? WHERE id = 1",
                    arrayOf<Any?>(gcashDelta)
                )
            }

            // Persist Smart Load wallet delta
            if (smartLoadDelta != 0.0) {
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE store_config SET smartLoadBalance = smartLoadBalance + ? WHERE id = 1",
                    arrayOf<Any?>(smartLoadDelta)
                )
            }

            // Persist Globe Load wallet delta
            if (globeLoadDelta != 0.0) {
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE store_config SET globeLoadBalance = globeLoadBalance + ? WHERE id = 1",
                    arrayOf<Any?>(globeLoadDelta)
                )
            }
        }

        Transaction(
            id = createdTransactionId,
            timestamp = timestamp,
            cashierUsername = cashierUsername,
            subtotal = normSubtotal,
            taxAmount = normTax,
            totalAmount = normTotal,
            paymentMethod = paymentMethod,
            cashPaid = normCashPaid,
            changeAmount = normChange,
            status = "COMPLETED",
            customerName = customerName
        )
    }

    // --- Raw transactional helpers (safe to call ONLY inside a db.withTransaction block) ---

    private fun queryStock(productId: Int): Int {
        val cursor = db.query("SELECT stockCount AS s FROM products WHERE id = ?", arrayOf<Any?>(productId))
        try {
            return if (cursor.moveToNext()) cursor.getInt(cursor.getColumnIndexOrThrow("s")) else 0
        } finally {
            cursor.close()
        }
    }

    private fun queryGcashBalance(): Double {
        val cursor = db.query("SELECT gcashBalance AS bal FROM store_config WHERE id = 1", null)
        try {
            return if (cursor.moveToNext()) cursor.getDouble(cursor.getColumnIndexOrThrow("bal")) else 0.0
        } finally {
            cursor.close()
        }
    }

    private fun querySmartLoadBalance(): Double {
        val cursor = db.query("SELECT smartLoadBalance AS bal FROM store_config WHERE id = 1", null)
        try {
            return if (cursor.moveToNext()) cursor.getDouble(cursor.getColumnIndexOrThrow("bal")) else 0.0
        } finally {
            cursor.close()
        }
    }

    private fun queryGlobeLoadBalance(): Double {
        val cursor = db.query("SELECT globeLoadBalance AS bal FROM store_config WHERE id = 1", null)
        try {
            return if (cursor.moveToNext()) cursor.getDouble(cursor.getColumnIndexOrThrow("bal")) else 0.0
        } finally {
            cursor.close()
        }
    }

    private fun lastInsertRowId(): Int {
        return db.openHelper.writableDatabase.compileStatement("SELECT last_insert_rowid()").simpleQueryForLong().toInt()
    }

    /** Returns the number of rows modified by the most recent INSERT/UPDATE/DELETE on the writable connection. */
    private fun queryChanges(): Int {
        return db.openHelper.writableDatabase.compileStatement("SELECT changes()").simpleQueryForLong().toInt()
    }

    /**
     * Resolves the UOM multiplier for a return item by looking up the product's
     * configured UOM options. Returns 1 for base units ("pc", "Piece", "Stick", etc.).
     */
    private fun resolveUomMultiplier(productId: Int, uomName: String): Int {
        // Base unit names → multiplier 1
        if (uomName.equals("pc", ignoreCase = true) ||
            uomName.equals("Piece", ignoreCase = true) ||
            uomName.equals("Stick", ignoreCase = true) ||
            uomName.equals("Base Unit", ignoreCase = true)) {
            return 1
        }

        // Check built-in UOM options on the product
        val cursor = db.query(
            """SELECT hasStick10s, hasStick20s, hasReam, hasMasterCase,
                      hasCustomUom, customUomName, customUomMultiplier
               FROM products WHERE id = ?""",
            arrayOf<Any?>(productId)
        )
        try {
            if (cursor.moveToNext()) {
                if (cursor.getInt(cursor.getColumnIndexOrThrow("hasStick10s")) == 1 &&
                    uomName.equals("10s Pack", ignoreCase = true)) return 10
                if (cursor.getInt(cursor.getColumnIndexOrThrow("hasStick20s")) == 1 &&
                    uomName.equals("20s Pack", ignoreCase = true)) return 20
                if (cursor.getInt(cursor.getColumnIndexOrThrow("hasReam")) == 1 &&
                    uomName.equals("Ream", ignoreCase = true)) return 200
                if (cursor.getInt(cursor.getColumnIndexOrThrow("hasMasterCase")) == 1 &&
                    uomName.equals("Master Case", ignoreCase = true)) return 10000
                if (cursor.getInt(cursor.getColumnIndexOrThrow("hasCustomUom")) == 1) {
                    val customName = cursor.getString(cursor.getColumnIndexOrThrow("customUomName"))
                    if (uomName.equals(customName, ignoreCase = true)) {
                        return cursor.getInt(cursor.getColumnIndexOrThrow("customUomMultiplier"))
                    }
                }
            }
        } finally {
            cursor.close()
        }

        // Check dynamic product_variants table
        val variantCursor = db.query(
            "SELECT multiplier FROM product_variants WHERE productId = ? AND variantName = ? LIMIT 1",
            arrayOf<Any?>(productId, uomName)
        )
        try {
            if (variantCursor.moveToNext()) {
                return variantCursor.getInt(variantCursor.getColumnIndexOrThrow("multiplier"))
            }
        } finally {
            variantCursor.close()
        }

        return 1 // Fallback: treat as base unit
    }

    private fun hashSha256(str: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(str.toByteArray())
        .fold("") { acc, it -> acc + "%02x".format(it) }

    // --- Drawer Transactions / Cash Flow ---
    val allDrawerTransactions: Flow<List<DrawerTransaction>> = drawerTransactionDao.getAllDrawerTransactions()

    fun getDrawerTransactionsBySession(sessionId: Int): Flow<List<DrawerTransaction>> {
        return drawerTransactionDao.getDrawerTransactionsBySession(sessionId)
    }

    suspend fun getDrawerTransactionsBySessionSync(sessionId: Int): List<DrawerTransaction> {
        return drawerTransactionDao.getDrawerTransactionsBySessionSync(sessionId)
    }

    suspend fun logDrawerTransaction(tx: DrawerTransaction): Long {
        return drawerTransactionDao.insertDrawerTransaction(tx.copy(amount = roundMoney(tx.amount)))
    }

    // --- Parked / Held Transactions ---
    val allParkedTransactions: Flow<List<ParkedTransaction>> = parkedTransactionDao.getAllParkedTransactions()

    suspend fun getParkedItems(parkedId: Int): List<ParkedTransactionItem> {
        return parkedTransactionDao.getParkedItems(parkedId)
    }

    /**
     * Parks a checkout in a single transaction (header + all items atomically) so a crash
     * can never leave an itemless parked header (H1).
     */
    suspend fun parkTransaction(note: String, items: List<Pair<Product, Int>>) = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        db.withTransaction {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO parked_transactions (timestamp, note) VALUES (?, ?)",
                arrayOf<Any?>(timestamp, note)
            )
            val parkedId = lastInsertRowId()
            for (item in items) {
                val product = item.first
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO parked_transaction_items (parkedTransactionId, productId, productName, price, quantity) VALUES (?, ?, ?, ?, ?)",
                    arrayOf<Any?>(parkedId, product.id, product.name, product.price, item.second)
                )
            }
        }
    }

    /**
     * Deletes a parked transaction. The CASCADE foreign key on parked_transaction_items
     * ensures child rows are removed atomically by SQLite (audit C2).
     */
    suspend fun deleteParkedTransaction(parkedId: Int) = withContext(Dispatchers.IO) {
        db.withTransaction {
            parkedTransactionDao.deleteParkedItems(parkedId)
            parkedTransactionDao.deleteParked(parkedId)
        }
    }
    
    // Setup Initial/Prepopulated Data
    //
    // SECURITY (C2): No default accounts (admin/admin, cashier/cashier) are seeded.
    // The first-launch SetupScreen flow (completeInitialSetup) creates the sole ADMIN
    // account with a PBKDF2 hashed password before any other feature is usable.
    // On a FRESH install the users table remains empty until the admin is created,
    // and the MainActivity routes to SetupScreen while store_config.setupCompleted=false.
    // On an UPGRADE of an existing install, previously created accounts are preserved.
    suspend fun prePopulateData() = withContext(Dispatchers.IO) {
        // SECURITY (C2): Purge any LEGACY default accounts (admin/admin, cashier/cashier) that
        // still carry the original weak SHA-256 hash of their username. Accounts whose password
        // was later changed (PBKDF2) are intentionally preserved. Fresh installs never seed them.
        for (legacy in listOf("admin", "cashier")) {
            val user = userDao.getUserSync(legacy)
            if (user != null && hashSha256(legacy).equals(user.passwordHash, ignoreCase = true)) {
                userDao.deleteUser(legacy)
            }
        }

        // Prepopulate StoreConfig with setupCompleted = false if empty
        if (storeConfigDao.getStoreConfigSync() == null) {
            storeConfigDao.insertStoreConfig(
                StoreConfig(
                    id = 1,
                    storeName = "",
                    currencySymbol = "₱",
                    taxPercentage = 12.0,
                    setupCompleted = false,
                    hasGCash = true,
                    hasMaya = true,
                    hasLoad = true
                )
            )
        }
        
        // Prepopulate standard Categories if empty
        val firstCategories = categoryDao.getAllCategoriesSync()
        if (firstCategories.isEmpty()) {
            categoryDao.insertCategory(Category(name = "Groceries"))
            categoryDao.insertCategory(Category(name = "Beverages"))
            categoryDao.insertCategory(Category(name = "Electronics"))
            categoryDao.insertCategory(Category(name = "Apparel"))
            categoryDao.insertCategory(Category(name = "GCash & Load (Digital)"))
        }

        // Prepopulate default products including GCash/Load and perishable groceries if empty
        if (productDao.getAllProductsSync().isEmpty()) {
            val categoriesNow = categoryDao.getAllCategoriesSync()
            val groceryCatId = categoriesNow.find { it.name == "Groceries" }?.id ?: 1
            val digitalCatId = categoriesNow.find { it.name == "GCash & Load (Digital)" }?.id ?: 5

            // Fresh Milk (groceries) with expiration date
            productDao.insertProduct(
                Product(
                    name = "Magnolia Fresh Milk 1L",
                    categoryId = groceryCatId,
                    price = 110.0,
                    stockCount = 20,
                    barcode = "4800012345678",
                    expirationDate = "2026-07-28"
                )
            )
            // GCash Cash-In
            productDao.insertProduct(
                Product(
                    name = "GCash Cash-In",
                    categoryId = digitalCatId,
                    price = 15.0, // convenience fee
                    stockCount = 9999,
                    barcode = "" // no barcode
                )
            )
            // GCash Cash-Out
            productDao.insertProduct(
                Product(
                    name = "GCash Cash-Out",
                    categoryId = digitalCatId,
                    price = 20.0, // convenience fee
                    stockCount = 9999,
                    barcode = "" // no barcode
                )
            )
            // Smart Load
            productDao.insertProduct(
                Product(
                    name = "Smart Load (Regular 50)",
                    categoryId = digitalCatId,
                    price = 53.0, // P3 convenience charge standard
                    stockCount = 500,
                    barcode = ""
                )
            )
            // Globe Load
            productDao.insertProduct(
                Product(
                    name = "Globe Load (Regular 50)",
                    categoryId = digitalCatId,
                    price = 53.0, // P3 convenience charge standard
                    stockCount = 500,
                    barcode = ""
                )
            )
        }
    }

    // --- Payment Schedules ---
    val allPaymentSchedules: Flow<List<PaymentSchedule>> = paymentScheduleDao.getAllPaymentSchedules()

    suspend fun savePaymentSchedule(schedule: PaymentSchedule) {
        paymentScheduleDao.insertPaymentSchedule(schedule)
    }

    suspend fun updatePaymentSchedule(schedule: PaymentSchedule) {
        paymentScheduleDao.updatePaymentSchedule(schedule)
    }

    suspend fun deletePaymentSchedule(schedule: PaymentSchedule) {
        paymentScheduleDao.deletePaymentSchedule(schedule)
    }

    suspend fun deletePaymentScheduleById(id: Int) {
        paymentScheduleDao.deletePaymentScheduleById(id)
    }

    // --- Product Variants & Multi-UOM ---
    fun getVariantsForProduct(productId: Int): Flow<List<ProductVariant>> {
        return productVariantDao.getVariantsForProduct(productId)
    }

    suspend fun getVariantsForProductSync(productId: Int): List<ProductVariant> {
        return productVariantDao.getVariantsForProductSync(productId)
    }

    val allProductVariants: Flow<List<ProductVariant>> = productVariantDao.getAllVariants()

    suspend fun getVariantByBarcode(barcode: String): ProductVariant? {
        return productVariantDao.getVariantByBarcode(barcode)
    }

    suspend fun saveProductVariant(variant: ProductVariant): Long {
        return productVariantDao.insertVariant(variant)
    }

    suspend fun deleteProductVariant(id: Int) {
        productVariantDao.deleteVariant(id)
    }

    suspend fun deleteVariantsForProduct(productId: Int) {
        productVariantDao.deleteVariantsForProduct(productId)
    }

    // --- Returns & Refunds ---
    val allReturns: Flow<List<ReturnTransaction>> = returnDao.getAllReturns()

    suspend fun getAllReturnsSync(): List<ReturnTransaction> {
        return returnDao.getAllReturnsSync()
    }

    fun getReturnsForTransaction(transactionId: Int): Flow<List<ReturnTransaction>> {
        return returnDao.getReturnsForTransaction(transactionId)
    }

    fun getReturnItems(returnTxId: Int): Flow<List<ReturnItem>> {
        return returnDao.getReturnItems(returnTxId)
    }

    suspend fun getReturnItemsSync(returnTxId: Int): List<ReturnItem> {
        return returnDao.getReturnItemsSync(returnTxId)
    }

    suspend fun insertReturnTransaction(returnTx: ReturnTransaction): Long {
        return returnDao.insertReturnTransaction(returnTx)
    }

    suspend fun insertReturnItem(item: ReturnItem) {
        return returnDao.insertReturnItem(item)
    }

    suspend fun updateTransactionStatus(txId: Int, status: String) {
        transactionDao.updateTransactionStatus(txId, status)
    }

    suspend fun getTransactionById(txId: Int): Transaction? {
        return transactionDao.getTransactionById(txId)
    }

    suspend fun getTransactionItemsSync(txId: Int): List<TransactionItem> {
        return transactionDao.getTransactionItemsSync(txId)
    }

    // --- Suppliers & Vendors ---
    val allSuppliers: Flow<List<Supplier>> = supplierDao.getAllSuppliers()

    suspend fun getAllSuppliersSync(): List<Supplier> {
        return supplierDao.getAllSuppliersSync()
    }

    suspend fun getSupplierById(id: Int): Supplier? {
        return supplierDao.getSupplierById(id)
    }

    suspend fun saveSupplier(supplier: Supplier): Long {
        return supplierDao.insertSupplier(supplier)
    }

    suspend fun updateSupplier(supplier: Supplier) {
        supplierDao.updateSupplier(supplier)
    }

    suspend fun deleteSupplier(id: Int) {
        supplierDao.deleteSupplier(id)
    }

    // --- Purchase Orders (PO) & Receiving ---
    val allPurchaseOrders: Flow<List<PurchaseOrder>> = purchaseOrderDao.getAllPurchaseOrders()

    suspend fun getAllPurchaseOrdersSync(): List<PurchaseOrder> {
        return purchaseOrderDao.getAllPurchaseOrdersSync()
    }

    suspend fun getPurchaseOrderById(id: Int): PurchaseOrder? {
        return purchaseOrderDao.getPurchaseOrderById(id)
    }

    fun getPurchaseOrderItems(poId: Int): Flow<List<PurchaseOrderItem>> {
        return purchaseOrderDao.getPurchaseOrderItems(poId)
    }

    suspend fun getPurchaseOrderItemsSync(poId: Int): List<PurchaseOrderItem> {
        return purchaseOrderDao.getPurchaseOrderItemsSync(poId)
    }

    suspend fun savePurchaseOrder(po: PurchaseOrder): Long {
        return purchaseOrderDao.insertPurchaseOrder(po)
    }

    suspend fun updatePurchaseOrder(po: PurchaseOrder) {
        purchaseOrderDao.updatePurchaseOrder(po)
    }

    suspend fun savePurchaseOrderItem(item: PurchaseOrderItem) {
        purchaseOrderDao.insertPurchaseOrderItem(item)
    }

    suspend fun markPurchaseOrderReceived(id: Int, status: String, receivedDate: Long, paymentStatus: String) {
        purchaseOrderDao.markPurchaseOrderReceived(id, status, receivedDate, paymentStatus)
    }

    /**
     * Deletes a purchase order. The CASCADE foreign key on purchase_order_items
     * ensures child rows are removed atomically by SQLite (same pattern as audit C2).
     */
    suspend fun deletePurchaseOrder(id: Int) = withContext(Dispatchers.IO) {
        db.withTransaction {
            purchaseOrderDao.deletePurchaseOrderItems(id)
            purchaseOrderDao.deletePurchaseOrder(id)
        }
    }

    /**
     * Processes a return ATOMICALLY (H1): inserts the return header + items, restocks inventory,
     * records the cash drawer payout, and updates the original transaction status in ONE transaction.
     */
    suspend fun processReturnAtomic(
        returnTx: ReturnTransaction,
        items: List<ReturnItem>,
        cashRefund: Boolean,
        sessionId: Int?,
        newOriginalStatus: String
    ): ReturnTransaction = withContext(Dispatchers.IO) {
        var createdReturnId = 0
        val normTotalRefund = roundMoney(returnTx.totalRefundAmount)
        db.withTransaction {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO return_transactions (originalTransactionId, timestamp, cashierUsername, totalRefundAmount, refundMethod, returnReason, customerName, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(returnTx.originalTransactionId, returnTx.timestamp, returnTx.cashierUsername, normTotalRefund, returnTx.refundMethod, returnTx.returnReason, returnTx.customerName, returnTx.notes)
            )
            createdReturnId = lastInsertRowId()

            for (item in items) {
                val normUnitPrice = roundMoney(item.unitPrice)
                val normRefundAmt = roundMoney(item.refundAmount)
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO return_items (returnTransactionId, productId, productName, quantity, unitPrice, refundAmount, uomName, restockToInventory) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(createdReturnId, item.productId, item.productName, item.quantity, normUnitPrice, normRefundAmt, item.uomName, if (item.restockToInventory) 1 else 0)
                )
                if (item.restockToInventory) {
                    // Restock using the correct base-unit quantity.
                    // During checkout, stock was deducted as (quantity * uomMultiplier),
                    // so we must restore the same calculation (audit: UOM restock fix).
                    val uomMultiplier = resolveUomMultiplier(item.productId, item.uomName)
                    val baseUnitsToRestock = item.quantity * uomMultiplier
                    db.openHelper.writableDatabase.execSQL(
                        "UPDATE products SET stockCount = stockCount + ? WHERE id = ?",
                        arrayOf<Any?>(baseUnitsToRestock, item.productId)
                    )
                }
            }

            if (cashRefund && sessionId != null) {
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO drawer_transactions (sessionId, timestamp, cashierUsername, amount, type, reason) VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(sessionId, System.currentTimeMillis(), returnTx.cashierUsername, -normTotalRefund, "PAYOUT", "Refund Return #$createdReturnId (Tx #${returnTx.originalTransactionId})")
                )
            }

            db.openHelper.writableDatabase.execSQL(
                "UPDATE transactions SET status = ? WHERE id = ?",
                arrayOf<Any?>(newOriginalStatus, returnTx.originalTransactionId)
            )
        }
        returnTx.copy(id = createdReturnId, totalRefundAmount = normTotalRefund)
    }

    /**
     * Creates a purchase order and ALL of its line items in a single transaction (H1).
     */
    suspend fun createPurchaseOrderAtomic(po: PurchaseOrder, items: List<PurchaseOrderItem>): PurchaseOrder = withContext(Dispatchers.IO) {
        var createdPoId = 0
        db.withTransaction {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO purchase_orders (poNumber, supplierId, supplierName, orderDate, expectedDeliveryDate, totalCost, status, paymentStatus, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(po.poNumber, po.supplierId, po.supplierName, po.orderDate, po.expectedDeliveryDate, po.totalCost, po.status, po.paymentStatus, po.notes)
            )
            createdPoId = lastInsertRowId()
            for (item in items) {
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO purchase_order_items (purchaseOrderId, productId, productName, quantityOrdered, quantityReceived, unitCost, totalCost, uomName) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(createdPoId, item.productId, item.productName, item.quantityOrdered, item.quantityReceived, item.unitCost, item.totalCost, item.uomName)
                )
            }
        }
        po.copy(id = createdPoId)
    }

    data class PurchaseOrderReceiveLine(
        val productId: Int,
        val quantityReceived: Int,
        val newUnitCost: Double
    )

    /**
     * Receives a purchase order ATOMICALLY (H1): applies stock/cost updates to FRESH product rows
     * (no stale in-memory reads, H2) and updates the PO header + line items in ONE transaction.
     */
    suspend fun receivePurchaseOrderAtomic(
        poId: Int,
        receivedItems: List<PurchaseOrderReceiveLine>,
        receivedDate: Long,
        paymentStatus: String
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            for (line in receivedItems) {
                if (line.quantityReceived <= 0) continue
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE products SET stockCount = stockCount + ? WHERE id = ?",
                    arrayOf<Any?>(line.quantityReceived, line.productId)
                )
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE products SET cost = ? WHERE id = ?",
                    arrayOf<Any?>(line.newUnitCost, line.productId)
                )
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE purchase_order_items SET quantityReceived = ? WHERE purchaseOrderId = ? AND productId = ?",
                    arrayOf<Any?>(line.quantityReceived, poId, line.productId)
                )
            }
            db.openHelper.writableDatabase.execSQL(
                "UPDATE purchase_orders SET status = ?, receivedDate = ?, paymentStatus = ? WHERE id = ?",
                arrayOf<Any?>("RECEIVED", receivedDate, paymentStatus, poId)
            )
        }
    }

    // --- Relational Queries with @Relation ---
    val allCategoriesWithProducts: Flow<List<CategoryWithProducts>> = categoryDao.getCategoriesWithProducts()
    val allProductsWithVariants: Flow<List<ProductWithVariants>> = productDao.getAllProductsWithVariants()
    fun getProductWithVariants(id: Int): Flow<ProductWithVariants?> = productDao.getProductWithVariants(id)
    val allSessionsWithDrawers: Flow<List<CashierSessionWithDrawers>> = cashierSessionDao.getAllSessionsWithDrawers()
    suspend fun getSessionWithDrawers(id: Int): CashierSessionWithDrawers? = cashierSessionDao.getSessionWithDrawers(id)
    val allTransactionsWithItems: Flow<List<TransactionWithItems>> = transactionDao.getAllTransactionsWithItems()
    suspend fun getTransactionWithItems(id: Int): TransactionWithItems? = transactionDao.getTransactionWithItems(id)
    val allParkedWithItems: Flow<List<ParkedTransactionWithItems>> = parkedTransactionDao.getAllParkedWithItems()
    val allReturnsWithItems: Flow<List<ReturnTransactionWithItems>> = returnDao.getAllReturnsWithItems()
    suspend fun getReturnWithItems(id: Int): ReturnTransactionWithItems? = returnDao.getReturnWithItems(id)
    val allSuppliersWithOrders: Flow<List<SupplierWithPurchaseOrders>> = supplierDao.getAllSuppliersWithOrders()
    val allPurchaseOrdersWithItems: Flow<List<PurchaseOrderWithItems>> = purchaseOrderDao.getAllPurchaseOrdersWithItems()
    suspend fun getPurchaseOrderWithItems(id: Int): PurchaseOrderWithItems? = purchaseOrderDao.getPurchaseOrderWithItems(id)
}
