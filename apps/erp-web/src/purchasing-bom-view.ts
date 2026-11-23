import type {
  BomRepackagingResultDto,
  OverheadScheduleDto,
  PurchaseOrderDto
} from "@storepoint/contracts";
import { Money } from "@storepoint/money";

export interface ErpPurchasingBomOverviewVm {
  readonly openPoCount: number;
  readonly unpaidApTotalFormatted: string;
  readonly m3eTemplateHtml: string;
}

/**
 * Renders the Material 3 Expressive (`matraic/m3e`) Purchasing, Bulk Repackaging BOM,
 * and Accounts Payable / Store Overhead Calendar view.
 */
export function buildErpPurchasingAndBomView(params: {
  currencyCode: string;
  locale?: string;
  purchaseOrders: readonly PurchaseOrderDto[];
  overheads: readonly OverheadScheduleDto[];
  recentBomConversions?: readonly BomRepackagingResultDto[];
}): ErpPurchasingBomOverviewVm {
  const locale = params.locale ?? "en-US";
  const fmt = (minor: number) => Money.fromMinor(minor, params.currencyCode).format(locale);

  const unpaidOverheads = params.overheads.filter((o) => !o.isPaid);
  const unpaidTotalMinor = unpaidOverheads.reduce((sum, o) => sum + o.amountMinor, 0);
  const openPos = params.purchaseOrders.filter(
    (p) => p.status === "ORDERED" || p.status === "PARTIALLY_RECEIVED"
  );

  const poCards = params.purchaseOrders
    .map(
      (po) =>
        `<m3e-card variant="elevated" class="sp-po-card" data-po="${po.poNumber}">` +
        `<h4>${po.poNumber} — ${po.supplierName}</h4>` +
        `<m3e-badge>${po.status}</m3e-badge>` +
        `<p>Total Landed: <strong>${fmt(po.totalCostMinor)}</strong></p>` +
        `</m3e-card>`
    )
    .join("\n");

  const overheadCards = unpaidOverheads
    .map(
      (ovh) =>
        `<m3e-card variant="tonal" class="sp-ap-card" data-category="${ovh.category}">` +
        `<span>${ovh.title} (Due ${ovh.dueDate})</span>` +
        `<strong>${fmt(ovh.amountMinor)}</strong>` +
        `<m3e-button variant="filled" data-pay="${ovh.id}">Mark Paid</m3e-button>` +
        `</m3e-card>`
    )
    .join("\n");

  const bomCards = (params.recentBomConversions ?? [])
    .map(
      (bom) =>
        `<m3e-card variant="outlined" class="sp-bom-card">` +
        `<h4>${bom.recipeName}</h4>` +
        `<m3e-chip variant="assist">Unit COGS: ${fmt(bom.finishedUnitCostMinor)}</m3e-chip>` +
        `</m3e-card>`
    )
    .join("\n");

  const m3eTemplateHtml = [
    `<section class="sp-m3e-purchasing-bom">`,
    `  <header class="sp-m3e-kpi-row">`,
    `    <m3e-card variant="filled"><span>Open POs</span><strong>${openPos.length}</strong></m3e-card>`,
    `    <m3e-card variant="filled"><span>Unpaid AP & Overheads</span><strong>${fmt(unpaidTotalMinor)}</strong></m3e-card>`,
    `  </header>`,
    `  <div class="sp-po-list">${poCards}</div>`,
    `  <div class="sp-bom-list">${bomCards}</div>`,
    `  <div class="sp-ap-calendar">${overheadCards}</div>`,
    `</section>`
  ].join("\n");

  return Object.freeze({
    openPoCount: openPos.length,
    unpaidApTotalFormatted: fmt(unpaidTotalMinor),
    m3eTemplateHtml
  });
}
