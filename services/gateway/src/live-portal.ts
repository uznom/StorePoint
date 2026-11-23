import {
  buildErpCatalogInventoryOverview,
  buildErpExecutivePnlView,
  buildErpExtensionsAndMigrationView,
  buildErpFinanceCrmView,
  buildErpPurchasingAndBomView,
  createErpShellConfig
} from "@storepoint/erp-web";
import {
  buildCustomerFacingDisplayView,
  buildPosTerminalM3eHtml
} from "@storepoint/pos-web";
import { buildPrintableShelfLabelsHtml } from "@storepoint/print-bridge";
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
 * Seeds a complete end-to-end Global Demo Store across all 7 decoupled microservices
 * and renders the unified Material 3 Expressive (`matraic/m3e`) Live ERP & POS Portal HTML.
 */
export async function seedAndRenderLivePortal(
  services: Omit<ReturnType<typeof buildGatewayService>, "gateway">
): Promise<{
  readonly seed: DemoStoreSeedResult;
  readonly html: string;
}> {
  const onboarded = await services.identity.domain.onboardOrganization({
    name: "StorePoint Global Showcase Mart",
    defaultCurrencyCode: "USD",
    locale: "en-US",
    timezone: "UTC",
    templateType: "GROCERY_MINIMART",
    ownerUsername: "admin",
    ownerDisplayName: "Store Owner",
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

  const cat = await services.catalog.domain.createCategory({
    organizationId: orgId,
    name: "Staples & Beverages"
  });

  const prod = await services.catalog.domain.createProduct({
    organizationId: orgId,
    categoryId: cat.id,
    sku: "COF-ARABICA",
    name: "Arabica Roast Coffee",
    baseUomCode: "bag",
    baseUomName: "250g Bag",
    baseBarcode: "89010001001",
    basePriceMinor: 800,
    initialCostMinor: 450,
    additionalUoms: [
      {
        uomCode: "case12",
        uomName: "12-Bag Case",
        conversionFactorToBase: 12,
        barcode: "89010001012",
        priceMinor: 9000
      }
    ]
  });

  await services.inventory.domain.recordMovement({
    organizationId: orgId,
    warehouseId: wh.id,
    productId: prod.id,
    currencyCode: "USD",
    reason: "INITIAL_STOCK",
    deltaBaseUnits: 48,
    unitCostMinor: 450,
    batchNumber: "BATCH-2026-A",
    expirationDate: "2027-06-30",
    actorUserId: ownerId
  });

  const sup = await services.purchasing.domain.createSupplier({
    organizationId: orgId,
    name: "Global Harvest Roasters",
    paymentTerms: "NET_15"
  });

  const po = await services.purchasing.domain.createPurchaseOrder({
    organizationId: orgId,
    branchId,
    warehouseId: wh.id,
    supplierId: sup.id,
    currencyCode: "USD",
    lines: [
      {
        productId: prod.id,
        productName: prod.name,
        uomCode: "case12",
        conversionFactorToBase: 12,
        quantityOrdered: 2,
        unitCostMinor: 5400
      }
    ]
  });

  const shift = await services.pos.domain.openShift({
    organizationId: orgId,
    branchId,
    registerId,
    cashierUserId: ownerId,
    currencyCode: "USD",
    openingFloatMinor: 15000
  });

  const sale = await services.pos.domain.completeSale({
    idempotencyKey: `demo_sale_${orgId}`,
    organizationId: orgId,
    branchId,
    warehouseId: wh.id,
    registerId,
    shiftId: shift.id,
    cashierUserId: ownerId,
    currencyCode: "USD",
    items: [
      {
        productId: prod.id,
        productName: prod.name,
        uomCode: "bag",
        uomName: "250g Bag",
        conversionFactorToBase: 1,
        quantity: 3,
        unitPriceMinor: 800
      }
    ],
    payments: [{ tenderType: "CASH", amountMinor: 3000 }]
  });

  const customer = await services.financeCrm.domain.createCustomer({
    organizationId: orgId,
    name: "Elena Martinez",
    creditLimitMinor: 50000
  });

  const floatAcc = await services.financeCrm.domain.createFloatAccount({
    organizationId: orgId,
    branchId,
    providerCode: "MOBILE_TOPUP",
    providerName: "Global Airtime & Bill Pay Float",
    category: "AIRTIME_LOAD",
    currencyCode: "USD",
    initialBalanceMinor: 100000,
    defaultServiceFeeMinor: 100,
    commissionBps: 200
  });

  await services.financeCrm.accounting.postSaleAndCogsJournal({
    organizationId: orgId,
    branchId,
    currencyCode: "USD",
    saleId: sale.id,
    netSalesMinor: sale.netAmountMinor,
    taxMinor: sale.taxAmountMinor,
    cogsMinor: 3 * 450
  });

  const pnl = await services.financeCrm.accounting.generateProfitAndLossReport(orgId, "USD");
  const products = await services.catalog.domain.listProducts(orgId);
  const balances = await services.inventory.repo.listBalances(orgId, wh.id);

  const shell = createErpShellConfig({
    storeName: onboarded.organization.name,
    currencyCode: "USD",
    templateType: "GROCERY_MINIMART",
    theme: "emerald"
  });

  const catalogVm = buildErpCatalogInventoryOverview({
    currencyCode: "USD",
    products,
    balances,
    lowStockAlerts: [],
    expiringBatches: []
  });

  const purchasingVm = buildErpPurchasingAndBomView({
    currencyCode: "USD",
    purchaseOrders: [po],
    overheads: []
  });

  const financeVm = buildErpFinanceCrmView({
    currencyCode: "USD",
    customers: [customer],
    floatAccounts: [floatAcc]
  });

  const pnlVm = buildErpExecutivePnlView({ report: pnl });
  const extVm = buildErpExtensionsAndMigrationView({
    countryPacks: services.extensions.domain.listCountryPacks()
  });

  const sampleCartLines = [
    {
      lineId: "l1",
      productId: prod.id,
      productName: prod.name,
      uomName: "250g Bag",
      uomMultiplier: 1,
      quantity: 2,
      unitPriceMinor: 800
    }
  ];

  const posHtml = buildPosTerminalM3eHtml({
    registerName: onboarded.defaultRegister.name,
    currencyCode: "USD",
    isOnline: true,
    lines: sampleCartLines,
    parkedCount: 0,
    pendingOutboxCount: 0
  });

  const cfdVm = buildCustomerFacingDisplayView({
    storeName: onboarded.organization.name,
    registerName: onboarded.defaultRegister.name,
    currencyCode: "USD",
    lines: sampleCartLines
  });

  const labelsVm = buildPrintableShelfLabelsHtml({
    storeName: onboarded.organization.name,
    currencyCode: "USD",
    products
  });

  const html = [
    `<!DOCTYPE html>`,
    `<html lang="en">`,
    `<head>`,
    `  <meta charset="utf-8" />`,
    `  <meta name="viewport" content="width=device-width, initial-scale=1" />`,
    `  <title>${shell.appTitle}</title>`,
    `  <style>`,
    shell.m3eThemeCss,
    `    body { font-family: system-ui, sans-serif; background: var(--m3e-color-surface); color: var(--m3e-color-on-surface); margin: 0; padding: 24px; }`,
    `    .sp-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 20px; margin-top: 20px; }`,
    `    m3e-card { display: block; background: var(--m3e-color-surface-container); border-radius: var(--m3e-shape-corner-large); padding: 16px; margin-bottom: 12px; }`,
    `    m3e-badge, m3e-chip { display: inline-block; background: var(--m3e-color-primary-container); color: var(--m3e-color-on-primary-container); padding: 4px 10px; border-radius: 999px; font-size: 12px; margin-right: 6px; }`,
    `    m3e-button { display: inline-block; background: var(--m3e-color-primary); color: var(--m3e-color-on-primary); padding: 8px 16px; border-radius: 999px; font-weight: 600; cursor: pointer; }`,
    `  </style>`,
    `</head>`,
    `<body>`,
    `  <header><h1>${shell.appTitle}</h1><p>7/7 Decoupled Microservices Active • Material 3 Expressive (matraic/m3e)</p></header>`,
    `  <div class="sp-grid">`,
    pnlVm.m3eTemplateHtml,
    catalogVm.m3eTemplateHtml,
    posHtml,
    cfdVm.m3eCfdHtml,
    purchasingVm.m3eTemplateHtml,
    financeVm.m3eTemplateHtml,
    labelsVm.printableHtml,
    extVm.m3eTemplateHtml,
    `  </div>`,
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
      currencyCode: "USD"
    },
    html
  };
}
