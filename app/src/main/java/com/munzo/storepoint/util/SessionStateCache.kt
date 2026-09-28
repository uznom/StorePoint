package com.munzo.storepoint.util

import android.content.Context
import com.munzo.storepoint.data.Product
import com.munzo.storepoint.data.UomOption
import org.json.JSONArray
import org.json.JSONObject

/** A single restored cart line, before its [Product] has been re-resolved. */
internal data class CachedCartLine(
    val productId: Int,
    val quantity: Int,
    val uomName: String?,
    val uomMultiplier: Int,
    val uomPrice: Double
)

/**
 * Lightweight persistence for UI/session state that does not belong in the database.
 *
 * Motivation (Laws of UX):
 *  - **Zeigarnik Effect** — an interrupted, unfinished task stays in memory far better
 *    than a completed one. A cashier whose terminal dies mid-sale should find their
 *    basket intact on relaunch, not an empty cart.
 *  - **Doherty Threshold** — remembering the last payment method and POS tab removes a
 *    decision from every subsequent transaction.
 *
 * Deliberately built on [android.content.SharedPreferences] to match the existing
 * `storepoint_sys_prefs` convention rather than introducing a DataStore dependency.
 *
 * The cart stores product *ids* and quantities, never full product rows. On restore the
 * ids are re-resolved against the live catalogue, so a price or stock change is picked
 * up instead of resurrecting stale data.
 */
class SessionStateCache(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("storepoint_session_state", Context.MODE_PRIVATE)

    // --- Last-used payment method (Hick's Law: removes a repeated choice) ---

    var lastPaymentMethod: String?
        get() = prefs.getString(KEY_LAST_PAYMENT, null)
        set(value) = prefs.edit().putString(KEY_LAST_PAYMENT, value).apply()

    // --- Last visited tabs / search, so the register reopens where it was left ---

    var lastPosTab: Int
        get() = prefs.getInt(KEY_LAST_POS_TAB, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_POS_TAB, value).apply()

    var lastAdminTab: Int
        get() = prefs.getInt(KEY_LAST_ADMIN_TAB, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_ADMIN_TAB, value).apply()

    var lastSearchQuery: String
        get() = prefs.getString(KEY_LAST_SEARCH, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_SEARCH, value).apply()

    /**
     * Whether an in-progress cart was recovered on the last launch.
     *
     * The POS screen reads this to show a "Restored your basket" notice, which closes
     * the loop for the user instead of silently repopulating lines.
     */
    var didRestoreCart: Boolean
        get() = prefs.getBoolean(KEY_DID_RESTORE, false)
        set(value) = prefs.edit().putBoolean(KEY_DID_RESTORE, value).apply()

    /**
     * Persists the current cart draft.
     *
     * Safe to call on every cart mutation: it writes a small JSON blob with `apply()`,
     * so it never blocks the UI thread on disk I/O.
     */
    fun saveCartDraft(
        lines: Map<Int, Pair<Product, Int>>,
        uoms: Map<Int, UomOption>
    ) {
        if (lines.isEmpty()) {
            clearCartDraft()
            return
        }
        try {
            val array = JSONArray()
            lines.forEach { (productId, pair) ->
                val uom = uoms[productId]
                array.put(
                    JSONObject().apply {
                        put("id", productId)
                        put("name", pair.first.name)
                        put("qty", pair.second)
                        put("uomName", uom?.name ?: "")
                        put("uomMult", uom?.multiplier ?: 1)
                        put("uomPrice", uom?.price ?: pair.first.price)
                    }
                )
            }
            prefs.edit()
                .putString(KEY_CART_DRAFT, array.toString())
                .putLong(KEY_CART_SAVED_AT, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            // A failed draft write must never break an in-progress sale.
            android.util.Log.w("SessionStateCache", "Cart draft save skipped: ${e.message}")
        }
    }

    /**
     * Reads the stored draft, or null when there is nothing to restore.
     *
     * Also records that a restore happened so the POS screen can acknowledge it.
     */
    internal fun loadCartDraft(): List<CachedCartLine>? {
        val raw = prefs.getString(KEY_CART_DRAFT, null) ?: return null
        return try {
            val array = JSONArray(raw)
            if (array.length() == 0) return null
            val lines = (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optInt("id", 0)
                if (id == 0) return@mapNotNull null
                CachedCartLine(
                    productId = id,
                    quantity = obj.optInt("qty", 0),
                    uomName = obj.optString("uomName").takeIf { it.isNotBlank() },
                    uomMultiplier = obj.optInt("uomMult", 1),
                    uomPrice = obj.optDouble("uomPrice", 0.0)
                )
            }
            if (lines.isEmpty()) {
                clearCartDraft()
                null
            } else {
                didRestoreCart = true
                lines
            }
        } catch (e: Exception) {
            android.util.Log.w("SessionStateCache", "Cart draft load skipped: ${e.message}")
            clearCartDraft()
            null
        }
    }

    /** Removes the draft. Called after a successful checkout or an explicit clear. */
    fun clearCartDraft() {
        prefs.edit()
            .remove(KEY_CART_DRAFT)
            .remove(KEY_CART_SAVED_AT)
            .putBoolean(KEY_DID_RESTORE, false)
            .apply()
    }

    /** Wipes all session state. Used on explicit logout and backup restore. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_CART_DRAFT = "cart_draft"
        const val KEY_CART_SAVED_AT = "cart_saved_at"
        const val KEY_LAST_PAYMENT = "last_payment_method"
        const val KEY_LAST_POS_TAB = "last_pos_tab"
        const val KEY_LAST_ADMIN_TAB = "last_admin_tab"
        const val KEY_LAST_SEARCH = "last_search_query"
        const val KEY_DID_RESTORE = "did_restore_cart"
    }
}