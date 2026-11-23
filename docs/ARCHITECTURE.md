# StorePoint v2 — Decoupled Service Architecture

## 1. Architectural Principles

1. **Decoupled Domain Services (Non-Monolithic)**:
   - Each business capability lives in its own service under `services/<domain>` with feature-sliced internals (`routes/`, `domain/`, `repositories/`, `events/`).
   - Services never reach across database tables owned by another service. Cross-service workflows communicate via domain events (`@storepoint/contracts` + `@storepoint/service-kit` Event Bus) and explicit gateway/API contracts.
2. **Schema-per-Service PostgreSQL Isolation**:
   - In self-hosted single-server deployments (`docker/docker-compose.yml`), a single PostgreSQL 16 instance hosts isolated schemas (`identity`, `catalog`, `inventory`, `pos`, `purchasing`, `finance_crm`, `extensions`), giving microservice boundary discipline with zero operational bloat.
3. **Zero Floating-Point Money**:
   - All prices, costs, taxes, and balances use integer minor units via `@storepoint/money` (`Money` value object) and fixed-point database columns.
4. **Mechanical `< 800` Line File Ceiling**:
   - Enforced on every build via `node scripts/check-line-limits.mjs` (`pnpm check:limits`). Soft target is 200–400 lines per file; hard failure ceiling is 799 lines.
5. **Material 3 Expressive UI (`matraic/m3e`)**:
   - Shared theme tokens in `@storepoint/ui-kit` generate `--m3e-*` CSS custom properties consumed by both `apps/erp-web` and `apps/pos-web`.
