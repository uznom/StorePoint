import { Money } from "@storepoint/money";
import type { OfflineCartLine } from "./index.js";

export interface CustomerFacingDisplayVm {
  readonly storeName: string;
  readonly itemCount: number;
  readonly formattedTotalDue: string;
  readonly qrPaymentPayload: string;
  readonly m3eCfdHtml: string;
}

/**
 * Customer-Facing Display (CFD) Engine (`matraic/m3e`).
 * Turns any secondary tablet, phone, or dual-screen POS monitor into a live customer
 * checkout display with itemized cart feedback and dynamic QR payment code payload.
 */
export function buildCustomerFacingDisplayView(params: {
  storeName: string;
  registerName: string;
  currencyCode: string;
  locale?: string;
  lines: readonly OfflineCartLine[];
  qrMerchantId?: string;
}): CustomerFacingDisplayVm {
  const locale = params.locale ?? "en-US";
  const fmt = (minor: number) => Money.fromMinor(minor, params.currencyCode).format(locale);

  const totalMinor = params.lines.reduce(
    (sum, line) => sum + line.unitPriceMinor * line.quantity,
    0
  );
  const itemCount = params.lines.reduce((sum, line) => sum + line.quantity, 0);
  const formattedTotalDue = fmt(totalMinor);
  const qrPaymentPayload = `storepoint://pay?merchant=${encodeURIComponent(params.qrMerchantId ?? params.storeName)}&currency=${params.currencyCode}&amountMinor=${totalMinor}`;

  const lineCards = params.lines
    .map(
      (line) =>
        `<m3e-card variant="filled" class="sp-cfd-item">` +
        `<span>${line.productName} <m3e-chip variant="assist">${line.uomName}</m3e-chip></span>` +
        `<strong>${line.quantity} × ${fmt(line.unitPriceMinor)} = ${fmt(line.unitPriceMinor * line.quantity)}</strong>` +
        `</m3e-card>`
    )
    .join("\n");

  const m3eCfdHtml = [
    `<section class="sp-m3e-customer-display" data-items="${itemCount}">`,
    `  <header class="sp-cfd-header">`,
    `    <h2>Welcome to ${params.storeName}</h2>`,
    `    <m3e-badge>${params.registerName}</m3e-badge>`,
    `  </header>`,
    `  <div class="sp-cfd-lines">${lineCards || "<p>Ready for next customer...</p>"}</div>`,
    `  <footer class="sp-cfd-footer">`,
    `    <div class="sp-cfd-total"><span>Total to Pay (${itemCount} items)</span><h1>${formattedTotalDue}</h1></div>`,
    `    <m3e-card variant="outlined" class="sp-cfd-qr" data-qr="${qrPaymentPayload}">Scan to Pay via Instant QR</m3e-card>`,
    `  </footer>`,
    `</section>`
  ].join("\n");

  return Object.freeze({
    storeName: params.storeName,
    itemCount,
    formattedTotalDue,
    qrPaymentPayload,
    m3eCfdHtml
  });
}
