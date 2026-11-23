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

export interface ParkedCartTicket {
  readonly ticketId: string;
  readonly customerNote: string;
  readonly parkedAt: string;
  readonly lines: readonly OfflineCartLine[];
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

/**
 * Manages Cart Parking (Hold & Resume) and the Local Offline Sync Outbox Queue.
 */
export class OfflinePosTerminalStore {
  private readonly parkedCarts = new Map<string, ParkedCartTicket>();
  private readonly outboxQueue = new Map<string, OfflineQueuedSale>();

  parkCart(customerNote: string, lines: readonly OfflineCartLine[]): ParkedCartTicket {
    if (lines.length === 0) {
      throw new Error("Cannot park an empty cart");
    }
    const ticket: ParkedCartTicket = Object.freeze({
      ticketId: `park_${randomUUID()}`,
      customerNote: customerNote.trim() || "Walk-in Customer",
      parkedAt: new Date().toISOString(),
      lines: Object.freeze(lines.map((l) => Object.freeze({ ...l })))
    });
    this.parkedCarts.set(ticket.ticketId, ticket);
    return ticket;
  }

  resumeCart(ticketId: string): ParkedCartTicket {
    const ticket = this.parkedCarts.get(ticketId);
    if (!ticket) {
      throw new Error(`Parked cart ${ticketId} not found`);
    }
    this.parkedCarts.delete(ticketId);
    return ticket;
  }

  listParkedCarts(): readonly ParkedCartTicket[] {
    return [...this.parkedCarts.values()];
  }

  enqueueOfflineSale(sale: OfflineQueuedSale): void {
    this.outboxQueue.set(sale.idempotencyKey, sale);
  }

  markSynced(idempotencyKeys: readonly string[]): void {
    for (const key of idempotencyKeys) {
      this.outboxQueue.delete(key);
    }
  }

  listPendingOutbox(): readonly OfflineQueuedSale[] {
    return [...this.outboxQueue.values()];
  }
}

export function buildPosTerminalM3eHtml(params: {
  registerName: string;
  currencyCode: string;
  isOnline: boolean;
  lines: readonly OfflineCartLine[];
  parkedCount: number;
  pendingOutboxCount: number;
  locale?: string;
}): string {
  const locale = params.locale ?? "en-US";
  const fmt = (minor: number) => Money.fromMinor(minor, params.currencyCode).format(locale);
  const totalMinor = params.lines.reduce((s, l) => s + l.unitPriceMinor * l.quantity, 0);

  const lineRows = params.lines
    .map(
      (l) =>
        `<m3e-card variant="outlined" class="sp-pos-line" data-line="${l.lineId}">` +
        `<span>${l.productName} <m3e-chip variant="input">${l.uomName} (x${l.uomMultiplier})</m3e-chip></span>` +
        `<strong>${l.quantity} × ${fmt(l.unitPriceMinor)} = ${fmt(l.unitPriceMinor * l.quantity)}</strong>` +
        `</m3e-card>`
    )
    .join("\n");

  return [
    `<main class="sp-m3e-pos-terminal" data-online="${params.isOnline}">`,
    `  <header class="sp-pos-topbar">`,
    `    <h2>${params.registerName}</h2>`,
    `    <m3e-chip variant="assist" data-status="${params.isOnline ? "online" : "offline"}">${params.isOnline ? "Online" : "Offline Mode"}</m3e-chip>`,
    `    <m3e-badge data-parked="${params.parkedCount}">Parked: ${params.parkedCount}</m3e-badge>`,
    `    <m3e-badge data-outbox="${params.pendingOutboxCount}">Outbox: ${params.pendingOutboxCount}</m3e-badge>`,
    `  </header>`,
    `  <section class="sp-pos-cart">${lineRows}</section>`,
    `  <footer class="sp-pos-checkout">`,
    `    <div class="sp-pos-total">Total Due: <strong>${fmt(totalMinor)}</strong></div>`,
    `    <m3e-button variant="tonal" data-action="park-cart">Hold / Park</m3e-button>`,
    `    <m3e-button variant="filled" data-action="charge">Charge (${fmt(totalMinor)})</m3e-button>`,
    `  </footer>`,
    `</main>`
  ].join("\n");
}

export function getPosTerminalThemeCss(theme: "emerald" | "indigo" | "amber" = "emerald"): string {
  return buildM3ExpressiveCssVariables(theme);
}
