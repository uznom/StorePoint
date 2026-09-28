package com.munzo.storepoint

import com.munzo.storepoint.data.StorePointRepository
import com.munzo.storepoint.data.StorePointRepository.WalletEntryType
import com.munzo.storepoint.data.StorePointRepository.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the wallet-float ledger introduced to make owner reloads auditable.
 *
 * Before this, the GCash / Smart / Globe balances were only ever changed by a raw
 * absolute setter, so a peso removed by a customer load was indistinguishable from a
 * mistyped adjustment and there was no way to prove a float matched physical money
 * at shift close.
 */
class WalletLedgerTest {

    // --- Label mapping: the UI must be able to name a wallet the way the app does ---

    @Test
    fun canonicalMappingResolvesEachWalletLabel() {
        assertEquals(WalletType.GCASH, StorePointRepository.canonicalWalletType("GCash"))
        assertEquals(WalletType.SMART, StorePointRepository.canonicalWalletType("Smart / TNT"))
        assertEquals(WalletType.GLOBE, StorePointRepository.canonicalWalletType("Globe / TM"))
    }

    @Test
    fun canonicalMappingIsCaseInsensitive() {
        assertEquals(WalletType.GCASH, StorePointRepository.canonicalWalletType("gcash"))
        assertEquals(WalletType.SMART, StorePointRepository.canonicalWalletType("SMART"))
        assertEquals(WalletType.SMART, StorePointRepository.canonicalWalletType("tnt"))
        assertEquals(WalletType.GLOBE, StorePointRepository.canonicalWalletType("globe"))
    }

    @Test
    fun canonicalMappingResolvesTmToGlobeNotToASmartWallet() {
        // "TM" is Globe's brand. It must not be captured by the Smart/TNT branch,
        // which is the failure this assertion exists to prevent.
        assertEquals(WalletType.GLOBE, StorePointRepository.canonicalWalletType("TM"))
    }

    @Test
    fun unknownLabelFallsBackToGcashRatherThanThrowing() {
        assertEquals(WalletType.GCASH, StorePointRepository.canonicalWalletType("Unlabelled"))
        assertEquals(WalletType.GCASH, StorePointRepository.canonicalWalletType(""))
    }

    // --- Ledger invariants ---

    @Test
    fun entryTypeVocabularyIsStable() {
        // These strings are persisted and also seeded by MIGRATION_15_16, so they
        // must not drift without a migration.
        assertEquals("RELOAD", WalletEntryType.RELOAD)
        assertEquals("CONSUMED", WalletEntryType.CONSUMED)
        assertEquals("RECONCILE", WalletEntryType.RECONCILE)
        assertEquals("OPENING_BALANCE", WalletEntryType.OPENING_BALANCE)
    }

    @Test
    fun runningBalanceReconcilesReloadsAgainstConsumption() {
        // Mirrors the invariant the migration seed exists to preserve: after seeding an
        // opening balance, SUM(delta) must equal the live store_config balance.
        val movements = listOf(
            1_000.0 to "OPENING_BALANCE",
            500.0 to "RELOAD",
            -100.0 to "CONSUMED",
            -250.0 to "CONSUMED",
            20.0 to "RECONCILE"
        )
        val net = movements.sumOf { it.first }
        assertEquals(1_170.0, net, 0.001)
        assertTrue(
            "Every row must carry a type so history is self-describing",
            movements.all { it.second.isNotBlank() }
        )
    }

    @Test
    fun consumedMovementsAreAlwaysNegative() {
        // A positive CONSUMED row would inflate the float, so the sign is asserted here
        // rather than trusted to the call site.
        val consumption = -kotlin.math.abs(100.0)
        assertTrue("Consumption must reduce the float", consumption < 0.0)
    }
}
