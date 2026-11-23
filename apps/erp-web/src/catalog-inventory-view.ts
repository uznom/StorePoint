import type {
  BatchLotBalanceDto,
  ProductDto,
  StockBalanceDto
} from "@storepoint/contracts";
import { Money } from "@storepoint/money";

export interface ErpInventoryOverviewVm {
  readonly currencyCode: string;
  readonly totalSkuCount: number;
  readonly totalValuationFormatted: string;
  readonly lowStockCount: number;
  readonly expiringBatchCount: number;
  readonly m3eTemplateHtml: string;
}

/**
 * Builds the Material 3 Expressive (`matraic/m3e` Web Components) Back-Office
 * Catalog & Inventory view model and declarative `<m3e-*>` markup.
 */
export function buildErpCatalogInventoryOverview(params: {
  currencyCode: string;
  locale?: string;
  products: readonly ProductDto[];
  balances: readonly StockBalanceDto[];
  lowStockAlerts: readonly StockBalanceDto[];
  expiringBatches: readonly BatchLotBalanceDto[];
}): ErpInventoryOverviewVm {
  const locale = params.locale ?? "en-US";
  const totalValuationMinor = params.balances.reduce(
    (sum, b) => sum + b.totalValuationMinor,
    0
  );
  const totalValuationFormatted = Money.fromMinor(
    totalValuationMinor,
    params.currencyCode
  ).format(locale);

  const productCardsHtml = params.products
    .map((product) => {
      const balance = params.balances.find((b) => b.productId === product.id);
      const onHand = balance?.quantityOnHandBase ?? 0;
      const wacFormatted = Money.fromMinor(
        balance?.wacUnitCostMinor ?? product.initialCostMinor,
        params.currencyCode
      ).format(locale);
      const uomChips = product.uoms
        .map(
          (u) =>
            `<m3e-chip variant="assist" data-barcode="${u.barcode}">${u.uomName} (x${u.conversionFactorToBase})</m3e-chip>`
        )
        .join("");

      return [
        `<m3e-card variant="elevated" class="sp-product-card" data-sku="${product.sku}">`,
        `  <div class="sp-card-header">`,
        `    <h3>${product.name}</h3>`,
        `    <m3e-badge>${onHand} ${product.baseUomCode}</m3e-badge>`,
        `  </div>`,
        `  <p class="sp-wac">WAC Unit Cost: <strong>${wacFormatted}</strong></p>`,
        `  <m3e-chip-set>${uomChips}</m3e-chip-set>`,
        `</m3e-card>`
      ].join("\n");
    })
    .join("\n");

  const m3eTemplateHtml = [
    `<section class="sp-m3e-inventory-dashboard">`,
    `  <header class="sp-m3e-kpi-row">`,
    `    <m3e-card variant="filled"><span class="label">Active SKUs</span><strong>${params.products.length}</strong></m3e-card>`,
    `    <m3e-card variant="filled"><span class="label">Total Inventory Valuation (WAC)</span><strong>${totalValuationFormatted}</strong></m3e-card>`,
    `    <m3e-card variant="tonal" data-alert="low-stock"><span class="label">Low-Stock Alerts</span><strong>${params.lowStockAlerts.length}</strong></m3e-card>`,
    `    <m3e-card variant="tonal" data-alert="expiring"><span class="label">Expiring Batches (FEFO)</span><strong>${params.expiringBatches.length}</strong></m3e-card>`,
    `  </header>`,
    `  <div class="sp-m3e-product-grid">`,
    productCardsHtml,
    `  </div>`,
    `</section>`
  ].join("\n");

  return Object.freeze({
    currencyCode: params.currencyCode,
    totalSkuCount: params.products.length,
    totalValuationFormatted,
    lowStockCount: params.lowStockAlerts.length,
    expiringBatchCount: params.expiringBatches.length,
    m3eTemplateHtml
  });
}
