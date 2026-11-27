package com.munzo.storepoint.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Forward non-destructive schema migration 19 -> 20 introducing:
 *  - Product quick-sell pinning (`products.isQuickSellPinned`)
 *  - Line-item discount persistence (`transaction_items.discountAmount`)
 *  - Parked cart UOM & discount persistence (`parked_transaction_items.uomName/uomMultiplier/discountAmount`)
 *  - Offline Suki Tab credit accounts & ledger (`customer_accounts`, `customer_credit_ledger`)
 *  - Offline ESC/POS thermal print retry queue (`pending_print_jobs`)
 */
object DatabaseMigrationsV20 {

    private fun SupportSQLiteDatabase.columnExists(table: String, column: String): Boolean {
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) return true
            }
        }
        return false
    }

    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!db.columnExists("products", "isQuickSellPinned")) {
                db.execSQL("ALTER TABLE `products` ADD COLUMN `isQuickSellPinned` INTEGER NOT NULL DEFAULT 0")
            }
            if (!db.columnExists("transaction_items", "discountAmount")) {
                db.execSQL("ALTER TABLE `transaction_items` ADD COLUMN `discountAmount` REAL NOT NULL DEFAULT 0.0")
            }
            if (!db.columnExists("parked_transaction_items", "uomName")) {
                db.execSQL("ALTER TABLE `parked_transaction_items` ADD COLUMN `uomName` TEXT NOT NULL DEFAULT 'Base Unit'")
            }
            if (!db.columnExists("parked_transaction_items", "uomMultiplier")) {
                db.execSQL("ALTER TABLE `parked_transaction_items` ADD COLUMN `uomMultiplier` INTEGER NOT NULL DEFAULT 1")
            }
            if (!db.columnExists("parked_transaction_items", "discountAmount")) {
                db.execSQL("ALTER TABLE `parked_transaction_items` ADD COLUMN `discountAmount` REAL NOT NULL DEFAULT 0.0")
            }

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `customer_accounts` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `phone` TEXT NOT NULL,
                    `address` TEXT NOT NULL,
                    `creditLimit` REAL NOT NULL,
                    `currentBalance` REAL NOT NULL,
                    `notes` TEXT NOT NULL,
                    `isActive` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_accounts_name` ON `customer_accounts` (`name`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_accounts_phone` ON `customer_accounts` (`phone`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `customer_credit_ledger` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `customerId` INTEGER NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `actorUsername` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `amount` REAL NOT NULL,
                    `balanceAfter` REAL NOT NULL,
                    `paymentMethod` TEXT NOT NULL,
                    `transactionId` INTEGER,
                    `notes` TEXT NOT NULL,
                    FOREIGN KEY(`customerId`) REFERENCES `customer_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_credit_ledger_customerId` ON `customer_credit_ledger` (`customerId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_credit_ledger_timestamp` ON `customer_credit_ledger` (`timestamp`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `pending_print_jobs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `jobType` TEXT NOT NULL,
                    `label` TEXT NOT NULL,
                    `payloadBase64` TEXT NOT NULL,
                    `lastError` TEXT NOT NULL,
                    `retryCount` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_print_jobs_createdAt` ON `pending_print_jobs` (`createdAt`)")
        }
    }
}
