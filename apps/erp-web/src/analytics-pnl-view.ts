import type {
  ProfitAndLossReportDto,
  WarehouseTransferResultDto
} from "@storepoint/contracts";
import { Money } from "@storepoint/money";

export interface ErpExecutivePnlVm {
  readonly grossProfitFormatted: string;
  readonly netProfitFormatted: string;
  readonly grossMarginPercent: string;
  readonly taxPayableFormatted: string;
  readonly m3eTemplateHtml: string;
}

/**
 * Renders the Material 3 Expressive (`matraic/m3e`) Executive Profit & Loss (P&L),
 * Tax Filing Summary, and Multi-Branch Stock Transfer view.
 */
export function buildErpExecutivePnlView(params: {
  report: ProfitAndLossReportDto;
  locale?: string;
  recentTransfers?: readonly WarehouseTransferResultDto[];
}): ErpExecutivePnlVm {
  const locale = params.locale ?? "en-US";
  const fmt = (minor: number) => Money.fromMinor(minor, params.report.currencyCode).format(locale);

  const grossProfitFormatted = fmt(params.report.grossProfitMinor);
  const netProfitFormatted = fmt(params.report.netProfitMinor);
  const grossMarginPercent = `${(params.report.grossMarginBps / 100).toFixed(2)}%`;
  const taxPayableFormatted = fmt(params.report.taxPayableMinor);

  const transferRows = (params.recentTransfers ?? [])
    .map(
      (t) =>
        `<m3e-card variant="outlined" class="sp-transfer-card" data-transfer="${t.transferId}">` +
        `<span>Product ${t.productId}: <strong>${t.quantityBaseUnits} units</strong> @ WAC ${fmt(t.transferredUnitCostMinor)}</span>` +
        `</m3e-card>`
    )
    .join("\n");

  const m3eTemplateHtml = [
    `<section class="sp-m3e-executive-pnl">`,
    `  <header class="sp-m3e-kpi-row">`,
    `    <m3e-card variant="filled"><span>Total Revenue</span><strong>${fmt(params.report.totalRevenueMinor)}</strong></m3e-card>`,
    `    <m3e-card variant="filled"><span>Cost of Goods Sold (COGS)</span><strong>${fmt(params.report.cogsMinor)}</strong></m3e-card>`,
    `    <m3e-card variant="elevated"><span>Gross Profit</span><strong>${grossProfitFormatted}</strong><m3e-badge>${grossMarginPercent}</m3e-badge></m3e-card>`,
    `    <m3e-card variant="elevated"><span>Net Operating Profit</span><strong>${netProfitFormatted}</strong></m3e-card>`,
    `    <m3e-card variant="tonal"><span>Tax Payable</span><strong>${taxPayableFormatted}</strong></m3e-card>`,
    `  </header>`,
    `  <div class="sp-transfer-list">${transferRows}</div>`,
    `</section>`
  ].join("\n");

  return Object.freeze({
    grossProfitFormatted,
    netProfitFormatted,
    grossMarginPercent,
    taxPayableFormatted,
    m3eTemplateHtml
  });
}
