# StorePoint Architecture Documentation

## Overview

StorePoint is an offline-first Point of Sale (POS) and inventory tracking application built for Android devices. The system is designed to provide high reliability, fast checkout speeds, zero-network dependency, and resilience against sudden power loss or process termination.

---

## Architectural Principles

1. **Offline-First / Local Master**: The local SQLite database (via Android Jetpack Room) is the authoritative source of truth. No cloud sync is required for operation.
2. **Unidirectional Data Flow (UDF)**: The UI observes state streams emitted by `StorePointViewModel` via Kotlin `StateFlow`. UI events (cart adds, refunds, checkout) trigger ViewModel functions that mutate data via `StorePointRepository`.
3. **Declarative UI**: 100% Jetpack Compose with Material Design 3. No XML views or legacy layouts.
4. **Data Integrity & Safe Migrations**: Incremental schema migrations (`MIGRATION_1_2` through `MIGRATION_9_10`) safeguard historical retail data. Schema mismatches fail-closed (no destructive fallback migration).

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
