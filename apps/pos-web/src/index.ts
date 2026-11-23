import { Money, type TaxRule, calculateTax } from "@storepoint/money";
import { buildM3ExpressiveCssVariables } from "@storepoint/ui-kit";
import { randomUUID } from "node:crypto";

export interface OfflineCartLine {
  readonly lineId: string;
  readonly productId: string;
  readonly productName: string;
  readonly uomName: string;
  readonly uomMultiplier: number;
  readonly quantity: number;
  readonly unitPriceMinor: number;
}

export interface OfflineQueuedSale {
  readonly idempotencyKey: string;
  readonly registerId: string;
  readonly currencyCode: string;
  readonly lines: readonly OfflineCartLine[];
  readonly netMinor: number;
  readonly taxMinor: number;
  readonly grossMinor: number;
  readonly createdOfflineAt: string;
}

/**
 * Offline-First POS Outbox & Cart Engine for the `matraic/m3e` POS Terminal.
 * Computes exact totals locally with zero network dependency and queues idempotent sales for sync.
 */
export function buildOfflineSaleRecord(params: {
  registerId: string;
  currencyCode: string;
  lines: readonly OfflineCartLine[];
  taxRules: readonly TaxRule[];
}): OfflineQueuedSale {
  if (params.lines.length === 0) {
    throw new Error("Cannot complete an empty POS cart");
  }
  const subtotal = params.lines.reduce((acc, line) => {
    const lineTotal = Money.fromMinor(line.unitPriceMinor, params.currencyCode).multiply(
      line.quantity
    );
    return acc.add(lineTotal);
  }, Money.zero(params.currencyCode));

  const taxResult = calculateTax(subtotal, params.taxRules);

  return Object.freeze({
    idempotencyKey: `pos_sale_${randomUUID()}`,
    registerId: params.registerId,
    currencyCode: params.currencyCode,
    lines: [...params.lines],
    netMinor: taxResult.netAmount.amountMinor,
    taxMinor: taxResult.taxAmount.amountMinor,
    grossMinor: taxResult.grossAmount.amountMinor,
    createdOfflineAt: new Date().toISOString()
  });
}

export function getPosTerminalThemeCss(theme: "emerald" | "indigo" | "amber" = "emerald"): string {
  return buildM3ExpressiveCssVariables(theme);
}
