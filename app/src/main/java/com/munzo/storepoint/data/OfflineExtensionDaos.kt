package com.munzo.storepoint.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerCreditDao {
    @Query("SELECT * FROM customer_accounts WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveCustomers(): Flow<List<CustomerAccount>>

    @Query("SELECT * FROM customer_accounts ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerAccount>>

    @Query("SELECT * FROM customer_accounts ORDER BY name ASC")
    suspend fun getAllCustomersSync(): List<CustomerAccount>

    @Query("SELECT * FROM customer_accounts WHERE id = :id")
    suspend fun getCustomerByIdSync(id: Int): CustomerAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerAccount): Long

    @Update
    suspend fun updateCustomer(customer: CustomerAccount)

    @Query("UPDATE customer_accounts SET isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archiveCustomer(id: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM customer_accounts WHERE id = :id")
    suspend fun deleteCustomer(id: Int)

    @Query("SELECT * FROM customer_credit_ledger WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getLedgerForCustomer(customerId: Int): Flow<List<CustomerCreditEntry>>

    @Query("SELECT * FROM customer_credit_ledger WHERE customerId = :customerId ORDER BY timestamp DESC")
    suspend fun getLedgerForCustomerSync(customerId: Int): List<CustomerCreditEntry>

    @Query("SELECT * FROM customer_credit_ledger ORDER BY timestamp DESC")
    suspend fun getAllLedgerSync(): List<CustomerCreditEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCreditEntry(entry: CustomerCreditEntry): Long
}

@Dao
interface PendingPrintJobDao {
    @Query("SELECT * FROM pending_print_jobs ORDER BY createdAt DESC")
    fun getAllPendingJobs(): Flow<List<PendingPrintJob>>

    @Query("SELECT * FROM pending_print_jobs ORDER BY createdAt ASC")
    suspend fun getAllPendingJobsSync(): List<PendingPrintJob>

    @Query("SELECT * FROM pending_print_jobs WHERE id = :id")
    suspend fun getJobByIdSync(id: Int): PendingPrintJob?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: PendingPrintJob): Long

    @Query("UPDATE pending_print_jobs SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun markRetryFailed(id: Int, error: String)

    @Query("DELETE FROM pending_print_jobs WHERE id = :id")
    suspend fun deleteJob(id: Int)

    @Query("DELETE FROM pending_print_jobs")
    suspend fun clearAllJobs()
}
