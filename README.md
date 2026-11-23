# StorePoint v2 — Open-Source Global Small-Store ERP & Offline-First POS

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.8-blue.svg)](https://www.typescriptlang.org/)
[![UI](https://img.shields.io/badge/UI-matraic%2Fm3e%20(Material%203%20Expressive)-006C4C.svg)](https://github.com/matraic/m3e)
[![Maintainability](https://img.shields.io/badge/Code%20Ceiling-%3C800%20Lines%2FFile-brightgreen.svg)](scripts/check-line-limits.mjs)

**StorePoint v2** is a local-first, self-hostable, decoupled Service-Oriented **Open-Source ERP & Point of Sale (POS)** designed for small retail stores, neighborhood groceries, pharmacies, hardware shops, and mini-marts anywhere in the world.

> **Looking for the standalone Android v1 APK codebase?**  
> StorePoint v1 (Jetpack Compose + Room SQLite) is preserved on the `legacy-android` branch and tagged at `v1-android-archive`.

---

## Why StorePoint v2?

- **Built for Small Stores Everywhere**: Native multi-currency (ISO 4217), configurable Tax Engine (inclusive/exclusive VAT, multi-component GST, US Sales Tax, compound taxes), and optional Country Packs (e.g., `pack-ph-retail`).
- **Decoupled Non-Monolithic Services**: 7 focused domain services (`identity`, `catalog`, `inventory`, `pos`, `purchasing`, `finance-crm`, `extensions`) behind an API Gateway, communicating over an Event Bus with schema-per-service PostgreSQL isolation.
- **Material 3 Expressive UI (`matraic/m3e`)**: Fast, tactile Back-Office ERP (`apps/erp-web`) and Offline-First POS Terminal (`apps/pos-web`).
- **Strict `< 800` Lines/File Ceiling**: Every source file is mechanically checked in CI via `pnpm check:limits` (`scripts/check-line-limits.mjs`) to guarantee long-term readability and contributor friendliness.

---

## Monorepo Layout

```text
StorePoint/
├── apps/
│   ├── erp-web/              # Back-Office ERP UI (matraic/m3e Material 3 Expressive)
│   └── pos-web/              # Offline-First POS Terminal & Outbox Engine (matraic/m3e)
├── services/
│   ├── gateway/              # API Gateway, Correlation IDs, Service Topology & Routing
│   └── identity/             # Orgs, Branches, Registers, Staff RBAC, PIN Lockout, Audit Logs
├── packages/
│   ├── contracts/            # Shared DTOs, API Envelopes & Domain Event contracts
│   ├── money/                # Fixed-point Money math, ISO 4217 currencies, Tax & WAC engine
│   ├── service-kit/          # PBKDF2 PIN security, progressive lockout, idempotent Event Bus
│   └── ui-kit/               # matraic/m3e Material 3 Expressive theme palettes & CSS tokens
├── docker/                   # One-command docker-compose.yml + schema-per-service SQL init
└── scripts/
    └── check-line-limits.mjs # Enforces <800 lines per file ceiling across the workspace
```

---

## Quick Start (Phase 0 Foundation)

```bash
# 1. Start PostgreSQL (with schema-per-service isolation) & Redis Streams
docker compose -f docker/docker-compose.yml up -d

# 2. Install dependencies
corepack pnpm install

# 3. Run <800-line ceiling check, typecheck, unit/integration tests, and build
corepack pnpm verify
```
