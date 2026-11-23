# StorePoint v2 — Open-Source Global Small-Store ERP & Offline-First POS

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.8-blue.svg)](https://www.typescriptlang.org/)
[![UI](https://img.shields.io/badge/UI-matraic%2Fm3e%20(Material%203%20Expressive)-006C4C.svg)](https://github.com/matraic/m3e)
[![Maintainability](https://img.shields.io/badge/Code%20Ceiling-%3C800%20Lines%2FFile-brightgreen.svg)](scripts/check-line-limits.mjs)
[![Microservices](https://img.shields.io/badge/Services-7%2F7%20Online-006C4C.svg)](services/gateway/src/index.ts)

**StorePoint v2** is a local-first, self-hostable, decoupled Service-Oriented **Open-Source ERP & Point of Sale (POS)** designed for small retail stores, neighborhood groceries, pharmacies, hardware shops, and mini-marts anywhere in the world.

> **Looking for the standalone Android v1 APK codebase?**  
> StorePoint v1 (Jetpack Compose + Room SQLite) is preserved on the `legacy-android` branch and tagged at `v1-android-archive`. Use `@storepoint/extensions-service`'s `POST /v1/migrations/v1-import` endpoint to migrate v1 JSON backups directly into StorePoint v2!

---

## Decoupled Microservice Architecture (7 Domain Services + API Gateway)

| Service / Package | Schema | Responsibilities |
| :--- | :--- | :--- |
| **`services/gateway`** | — | API Gateway, Correlation IDs, 7-Service Health Topology & Unified Routing |
| **`services/identity`** | `identity.*` | Organizations, Branches, Registers, Staff RBAC, PBKDF2 4-Digit PIN Progressive Lockout, Audit Logs |
| **`services/catalog`** | `catalog.*` | Categories, Products, Normalized Multi-UOM Barcodes & Multipliers, Global Tax Group Presets |
| **`services/inventory`** | `inventory.*` | Immutable Stock Movement Ledger, Weighted Average Cost (WAC), FEFO Expiry Alerts, Stocktakes, Bulk Repackaging BOM, Multi-Branch Transfers |
| **`services/pos`** | `pos.*` | Cashier Shifts, Petty Cash Drawers, Blind Denomination Closing Counts, Multi-Tender Split Checkout, Idempotent Offline Outbox Sync, Item-Level Returns |
| **`services/purchasing`** | `purchasing.*` | Suppliers, Purchase Orders, Partial/Full Goods Receipt with Landed Freight Allocation, Accounts Payable & Overhead Calendar |
| **`services/finance-crm`** | `finance_crm.*` | Customer Directory, Accounts Receivable (AR / Store Credit Tab) Ledger, Generalized Float & Digital Services Hub, Double-Entry General Ledger & Real-Time P&L |
| **`services/extensions`** | `extensions.*` | Global Country Packs (`pack-ph-retail`, `pack-in-kirana`, `pack-ke-duka`, `pack-mx-abarrotes`, `pack-us-general`), Outbound Webhooks & v1 JSON → v2 Migration Importer |
| **`apps/erp-web`** | — | Material 3 Expressive (`matraic/m3e`) Back-Office ERP Views (`<m3e-card>`, `<m3e-chip>`, `<m3e-badge>`, `<m3e-button>`) |
| **`apps/pos-web`** | — | Offline-First POS Cart, Cart Park/Resume, Offline Outbox Queue & `matraic/m3e` Terminal UI |
| **`apps/print-bridge`** | — | 58mm (32-col) & 80mm (48-col) ESC/POS Thermal Receipt Formatter, Shift Z-Slips & RJ11 Cash Drawer Kick Engine |

---

## Quick Start

```bash
# 1. Start PostgreSQL 16 (with schema-per-service isolation) & Redis Streams
docker compose -f docker/docker-compose.yml up -d

# 2. Install workspace dependencies
corepack pnpm install

# 3. Run <800-line ceiling check, typecheck, unit/integration tests, and build
corepack pnpm verify
```
