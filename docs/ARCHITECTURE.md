# StorePoint Architecture Documentation

## Overview

StorePoint is an offline-first Point of Sale (POS) and inventory tracking application built for Android devices. The system is designed to provide high reliability, fast checkout speeds, zero-network dependency, and resilience against sudden power loss or process termination.

---

## Architectural Principles

1. **Offline-First / Local Master**: The local SQLite database (via Android Jetpack Room) is the authoritative source of truth. No cloud sync is required for operation.
2. **Unidirectional Data Flow (UDF)**: The UI observes state streams emitted by `StorePointViewModel` via Kotlin `StateFlow`. UI events (cart adds, refunds, checkout) trigger ViewModel functions that mutate data via `StorePointRepository`.
3. **Declarative UI**: 100% Jetpack Compose with Material Design 3. No XML views or legacy layouts.
4. **Data Integrity & Safe Migrations**: Incremental schema migrations (`MIGRATION_1_2` through `MIGRATION_14_15`) safeguard historical retail data. Schema mismatches fail-closed (no destructive fallback migration).

---

## Authentication Architecture

### Credential Model
- PINs are **4 digits** (`SecurityHelper.PIN_LENGTH`), salted and hashed with PBKDF2-HMAC-SHA256 at 120,000 iterations.
- A 4-digit PIN has a 10,000-combination keyspace, so it is **not** the sole security boundary. Compensating controls:
  - `SecurityHelper.isWeakPin` refuses repeats, ascending/descending runs, and keyboard-walk patterns at creation time.
  - `StorePointViewModel.lockoutScheduleMs` applies an escalating, **per-account** backoff (30s → 60s → 120s → 300s).
  - Biometric sign-in is the primary path for enrolled users.

### Biometric Sign-In
- `BiometricAuthHelper` is built on `androidx.biometric` (not the deprecated platform `android.hardware.biometrics` API) and requests **only `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`**. Weak biometrics are rejected because this gate protects a cash register and kiosk lockdown.
- `checkAvailability()` distinguishes *no hardware* (hide the affordance) from *nothing enrolled* (offer enrolment), which a boolean cannot.
- `authenticate()` returns a classified `BiometricOutcome` (`SUCCESS` / `FALLBACK` / `RECOVERABLE_ERROR` / `FATAL_ERROR` / `UNAVAILABLE`) so the UI never string-matches error text.
- Fingerprint resolves to **exactly one** enrolled account. It does not fall back to "the first non-admin user".
- `MainActivity` extends `FragmentActivity` (a `ComponentActivity` subclass) purely so the prompt can attach its fragment.
- Accounts are auto-enrolled on first successful PIN login; the admin can revoke per-user via `setBiometricEnrolledForUser`.

### Legacy 6-Digit Migration
- `MIGRATION_13_14` adds `biometricEnrolled` and `pinResetRequired`, marking every pre-existing account as needing a reset.
- `login()` **refuses** accounts flagged `pinResetRequired`.
- `PinResetScreen` is a 3-step chunked wizard: verify the existing credential → choose → confirm. Step 1 requires the *current* PIN, so the screen cannot be used to hijack another account.
- `SecurityHelper.verifyPinLenient` accepts 4 or 6 digits and is used **only** by the reset gate and kiosk unlock — the latter so an un-migrated admin can still exit kiosk lockdown.

---

## Session State Cache

`SessionStateCache` (`util/SessionStateCache.kt`) persists UI state that does not belong in the database, backed by `SharedPreferences` to match the existing `storepoint_sys_prefs` convention:

| Key | Purpose |
|---|---|
| `cart_draft` | In-progress basket, mirrored to disk on every cart mutation |
| `last_payment_method` | Removes a repeated choice per transaction (Hick's Law) |
| `last_pos_tab` / `last_admin_tab` / `last_search_query` | Reopens the workspace where it was left |
| `did_restore_cart` | Drives the "Restored your basket" acknowledgement |

Restore re-resolves product **ids** against the live catalogue and drops any line whose quantity now exceeds stock, so stale prices and quantities are never resurrected. `logout()` deliberately does not clear the cart (Zeigarnik Effect); only an explicit clear or a completed checkout discards the draft.

---

## Analytics Performance

Room only emits `CREATE INDEX` for a *fresh* install, so `MIGRATION_14_15` creates the analytics hot-path indices on upgrade:

| Index | Backs |
|---|---|
| `transactions(timestamp)` | Daily/period sales rollups |
| `transactions(cashierUsername)` | Per-cashier breakdown |
| `transactions(paymentMethod, timestamp)` | Payment split over a date range |
| `transaction_items(productId, transactionId)` | Top-sellers grouping |
| `products(name)` | POS catalogue search (hottest read in the app) |
| `products(stockCount)` | Low-stock badges |

The migration is purely additive and fail-closed: no blanket `try/catch`, so a genuine failure rolls the transaction back rather than committing a half-indexed schema.

---

## High-Level Component Diagram

```
+--------------------------------------------------------------------------+
|                           Jetpack Compose UI                             |
|  CashierPOSScreen      AdminDashboardScreen       SuppliersAndPurchases  |
|  ProductVariantsDialog ReturnRefundDialog         LoginScreen            |
+--------------------------------------------------------------------------+
                                    |
                            StateFlow / Events
                                    v
+--------------------------------------------------------------------------+
|                          StorePointViewModel                             |
|  • Active Cart & Multi-UOM computation                                   |
|  • Shift Session & Cash Drawer tracking                                  |
|  • Barcode Scanning & Hardware Event dispatcher                          |
|  • Cashier & Admin Authentication (PIN verification)                     |
+--------------------------------------------------------------------------+
                                    |
                            Coroutines / Flow
                                    v
+--------------------------------------------------------------------------+
|                          StorePointRepository                            |
|  • Aggregates Room DAOs                                                  |
|  • Handles transactional operations (e.g. checkout, returns, PO receipt) |
|  • Prepopulates default store configurations and sample catalogs        |
+--------------------------------------------------------------------------+
                                    |
                             Room SQLite
                                    v
+--------------------------------------------------------------------------+
|                       AppDatabase (Version 10)                           |
|  • store_config                  • user_accounts                         |
|  • categories                    • products                              |
|  • product_variants              • cashier_sessions                      |
|  • transactions                  • transaction_items                     |
|  • parked_transactions           • parked_transaction_items              |
|  • drawer_transactions           • payment_schedules                     |
|  • return_transactions           • return_items                          |
|  • suppliers                     • purchase_orders                       |
|  • purchase_order_items                                                  |
+--------------------------------------------------------------------------+
```

---

## Database Entities & Relationships

### 1. Catalog & Inventory
- `Category`: Categorization for quick POS tab filtering (e.g. Beverages, Canned Goods, Snacks).
- `Product`: The base product entity. Contains base prices, costs, stock count, and legacy multi-pack pricing (e.g. `hasStick10s`, `hasStick20s`, `hasReam`).
- `ProductVariant`: Flexible packaging units (e.g. *Tingi / Piece*, *Pack of 6*, *Box of 24*). Defines unit multipliers, distinct barcodes, and unit-specific sale prices.

### 2. POS Transactions & Cart
- `Transaction`: Represents completed sales. Records timestamp, cashier username, subtotal, tax, discount, payment method, cash paid, change, and customer name.
- `TransactionItem`: Individual line items within a transaction, including `price`, `quantity`, `cost`, and the active `uomName`.
- `ParkedTransaction` & `ParkedTransactionItem`: Holds unfinished orders temporarily without locking the register.

### 3. Shift & Cash Drawer Reconciliation
- `CashierSession`: Tracks cashier shift life cycle from opening float (`startingCash`) to closing drawer reconciliation (`endingCash`).
- `DrawerTransaction`: Logs cash drops, paid-outs, petty cash drawings, or top-ups during an active shift.

### 4. Returns & Refunds
- `ReturnTransaction`: Metadata for returns (original receipt ID, cashier, total refunded, refund method, reason).
- `ReturnItem`: Individual returned products with quantities, refund amounts, and a flag indicating whether the item was restocked into inventory (`restockToInventory`).

### 5. Supply Chain & Procurement
- `Supplier`: Supplier directory with contact info, delivery schedules, and payment terms.
- `PurchaseOrder`: Purchase orders tracking order date, delivery date, landed costs, payment status, and fulfillment state (`DRAFT`, `ORDERED`, `RECEIVED`, `CANCELLED`).
- `PurchaseOrderItem`: Line items for purchase orders tracking ordered vs. received quantities and negotiated unit costs.

---

## State Management Flow

### Cart Calculation Engine
1. When an item is added to the cart, the system checks whether the product has multiple UOM variants.
2. If multiple variants exist, the UI triggers the `ProductVariantsDialog` to let the cashier select the unit (*Piece*, *Pack*, *Case*).
3. The cart tracks quantity, unit price, applied packaging multiplier, and line totals.
4. During checkout, `StorePointRepository.checkout()` runs a single SQLite transaction that:
   - Validates fresh stock availability (`quantity * uomMultiplier <= live stock`) and rejects underpayment — no clamping.
   - Inserts the `Transaction` header and each `TransactionItem`.
   - Decrements product inventory by `(quantity * uomMultiplier)`.
   - Applies any GCash wallet delta atomically (rejecting overdrafts).
   Any failure rolls the entire sale back.

---

## Adaptive UI & Window Layout

All responsive behavior derives from a single contract in `ui/layout/AdaptiveLayout.kt`:

- `rememberWindowLayout()` maps the window width to `WindowLayout.Compact` (< 600dp), `Medium` (600–839dp), or `Expanded` (>= 840dp).
- **Admin Center**: on `Compact` widths, the 8-item scrollable pill carousel is replaced by a fixed bottom `NavigationBar` (Analytics, Catalog, Suppliers, Staff) plus a "More" `ModalBottomSheet` (Categories, Payment Calendar, Security, About). On `Medium`/`Expanded` widths the pill carousel is retained.
- **Cashier POS**: uses a `BoxWithConstraints` portrait/landscape split — portrait shows a tabbed single-column workspace; landscape shows the catalog and cart side by side. The navigation drawer sheet width adapts (85% of width, capped at 320dp).
- Screens must not introduce ad-hoc `screenWidthDp >= 600` checks; they should consume `WindowLayout` so breakpoints stay consistent across phone, foldable, and tablet form factors.

---

## Hardware Integrations

### 1. Camera Barcode Scanning (`CameraPreviewView.kt`)
- Utilizes Android **CameraX** with an `ImageAnalysis` analyzer stream.
- Analyzes preview frames using Google's **ML Kit Barcode Scanning SDK**.
- Debounces duplicate scans within a 1.2-second window to prevent multiple additions of the same product.

### 2. ESC/POS Thermal Printing (`EscPosHelper.kt`)
- Direct Bluetooth RFCOMM connection using the standard Serial Port Profile (SPP) UUID `00001101-0000-1000-8000-00805F9B34FB`.
- Translates receipt line items, store headers, tax breakdown, and QR codes into native ESC/POS bytecode.
- Supports both 58mm (32 characters per line) and 80mm (48 characters per line) paper widths.

---

## Backup & Recovery System (`DatabaseBackupManager.kt`)

StorePoint includes a zero-dependency JSON serialization engine:
- **Export**: Extracts every table into structured JSON with schema version metadata and timestamps.
- **Import**: Executes a transactional wipe-and-reload with foreign key safety to restore historical data on replacement hardware.
- **Fail-Safe Crash Diagnostics**: `CrashDiagnosticsManager` intercepts uncaught exceptions on background threads, writes a stack trace to disk, and presents a recovery dialog instead of an OS crash prompt.
