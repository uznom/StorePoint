import {
  STOREPOINT_CLIENT_RUNTIME_SCRIPT,
  STOREPOINT_INITIAL_SEED_SCRIPT,
  STOREPOINT_M3E_APP_CSS,
  buildStorePointAppBodyHtml
} from "@storepoint/erp-web";
import type { buildGatewayService } from "./index.js";

export interface DemoStoreSeedResult {
  readonly organizationId: string;
  readonly branchId: string;
  readonly warehouseId: string;
  readonly registerId: string;
  readonly ownerId: string;
  readonly storeName: string;
  readonly currencyCode: string;
}

/**
 * Seeds the 7 decoupled backend microservices and renders the full interactive
 * StorePoint Material 3 Expressive Application (Cashier POS, Digital Float Hub,
 * Bulk Repackaging BOM, Suppliers & POs, Admin ERP & P&L, and CFD / Barcode Labels).
 */
export async function seedAndRenderLivePortal(
  services: Omit<ReturnType<typeof buildGatewayService>, "gateway">
): Promise<{
  readonly seed: DemoStoreSeedResult;
  readonly html: string;
}> {
  const onboarded = await services.identity.domain.onboardOrganization({
    name: "StorePoint Sari-Sari & Mini-Mart",
    defaultCurrencyCode: "PHP",
    locale: "en-PH",
    timezone: "Asia/Manila",
    templateType: "GROCERY_MINIMART",
    ownerUsername: "admin",
    ownerDisplayName: "Jeff S. (Owner)",
    ownerPin: "8492"
  });

  const orgId = onboarded.organization.id;
  const branchId = onboarded.defaultBranch.id;
  const registerId = onboarded.defaultRegister.id;
  const ownerId = onboarded.owner.id;

  const wh = await services.inventory.domain.createWarehouse({
    organizationId: orgId,
    branchId,
    code: "WH-MAIN",
    name: "Main Store Warehouse"
  });

  const html = [
    `<!DOCTYPE html>`,
    `<html lang="en" data-theme="light">`,
    `<head>`,
    `  <meta charset="utf-8" />`,
    `  <meta name="viewport" content="width=device-width, initial-scale=1" />`,
    `  <title>StorePoint — Offline-First POS & Small-Store ERP</title>`,
    `  <style>${STOREPOINT_M3E_APP_CSS}</style>`,
    `</head>`,
    `<body>`,
    buildStorePointAppBodyHtml(),
    `  <script>${STOREPOINT_INITIAL_SEED_SCRIPT}</script>`,
    `  <script>${STOREPOINT_CLIENT_RUNTIME_SCRIPT}</script>`,
    `</body>`,
    `</html>`
  ].join("\n");

  return {
    seed: {
      organizationId: orgId,
      branchId,
      warehouseId: wh.id,
      registerId,
      ownerId,
      storeName: onboarded.organization.name,
      currencyCode: "PHP"
    },
    html
  };
}
