import type { ProductDto } from "@storepoint/contracts";
import { Money } from "@storepoint/money";

export interface ShelfLabelItemVm {
  readonly sku: string;
  readonly productName: string;
  readonly uomName: string;
  readonly barcode: string;
  readonly formattedPrice: string;
  readonly svgBarcodeBars: string;
}

/**
 * Generates deterministic Code-128 style SVG bar patterns and printable
 * Material 3 Expressive shelf price tags / thermal barcode stickers.
 */
export function buildDeterministicBarcodeSvg(barcode: string): string {
  const clean = barcode.trim() || "00000000";
  const bars: string[] = [];
  let x = 6;
  for (let i = 0; i < clean.length; i += 1) {
    const code = clean.charCodeAt(i);
    const w1 = (code % 3) + 1;
    const gap1 = ((code >> 2) % 2) + 1;
    const w2 = ((code >> 4) % 2) + 1;
    bars.push(`<rect x="${x}" y="4" width="${w1 * 2}" height="36" fill="#171D1A"/>`);
    x += (w1 + gap1) * 2;
    bars.push(`<rect x="${x}" y="4" width="${w2 * 2}" height="36" fill="#171D1A"/>`);
    x += (w2 + 1) * 2;
  }
  return `<svg class="sp-barcode-svg" viewBox="0 0 ${Math.max(120, x + 6)} 52" xmlns="http://www.w3.org/2000/svg">${bars.join("")}<text x="50%" y="49" text-anchor="middle" font-size="9" font-family="monospace">${clean}</text></svg>`;
}

export function buildPrintableShelfLabelsHtml(params: {
  storeName: string;
  currencyCode: string;
  locale?: string;
  products: readonly ProductDto[];
}): {
  readonly labels: readonly ShelfLabelItemVm[];
  readonly printableHtml: string;
} {
  const locale = params.locale ?? "en-US";
  const fmt = (minor: number) => Money.fromMinor(minor, params.currencyCode).format(locale);
  const labels: ShelfLabelItemVm[] = [];

  for (const product of params.products) {
    for (const uom of product.uoms) {
      labels.push({
        sku: product.sku,
        productName: product.name,
        uomName: uom.uomName,
        barcode: uom.barcode || product.sku,
        formattedPrice: fmt(uom.priceMinor),
        svgBarcodeBars: buildDeterministicBarcodeSvg(uom.barcode || product.sku)
      });
    }
  }

  const cardsHtml = labels
    .map(
      (lbl) =>
        `<article class="sp-shelf-label" data-sku="${lbl.sku}" data-barcode="${lbl.barcode}">` +
        `<div class="sp-label-store">${params.storeName}</div>` +
        `<div class="sp-label-name">${lbl.productName} <span class="sp-label-uom">(${lbl.uomName})</span></div>` +
        `<div class="sp-label-price">${lbl.formattedPrice}</div>` +
        `<div class="sp-label-barcode">${lbl.svgBarcodeBars}</div>` +
        `</article>`
    )
    .join("\n");

  const printableHtml = `<section class="sp-shelf-label-sheet">\n${cardsHtml}\n</section>`;
  return Object.freeze({ labels: Object.freeze(labels), printableHtml });
}
