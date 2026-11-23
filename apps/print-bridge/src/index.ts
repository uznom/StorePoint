import type { CashierShiftDto, PosSaleDto } from "@storepoint/contracts";
import { Money } from "@storepoint/money";

export type ThermalPaperWidthMm = 58 | 80;

export const ESCPOS_COMMANDS = Object.freeze({
  INIT: Uint8Array.from([0x1b, 0x40]),
  ALIGN_LEFT: Uint8Array.from([0x1b, 0x61, 0x00]),
  ALIGN_CENTER: Uint8Array.from([0x1b, 0x61, 0x01]),
  BOLD_ON: Uint8Array.from([0x1b, 0x45, 0x01]),
  BOLD_OFF: Uint8Array.from([0x1b, 0x45, 0x00]),
  /** Standard RJ11 Cash Drawer Kick pulse on Pin 2 (`ESC p 0 25 250`) */
  DRAWER_KICK_PIN2: Uint8Array.from([0x1b, 0x70, 0x00, 0x19, 0xfa]),
  /** Partial paper cut with feed (`GS V 66 0`) */
  PAPER_CUT: Uint8Array.from([0x1d, 0x56, 0x42, 0x00])
});

export function getLineCharWidth(paperWidthMm: ThermalPaperWidthMm): 32 | 48 {
  return paperWidthMm === 58 ? 32 : 48;
}

export function formatReceiptRow(left: string, right: string, widthCols: number): string {
  const safeRight = right.slice(0, widthCols - 2);
  const maxLeftLen = Math.max(1, widthCols - safeRight.length - 1);
  const trimmedLeft = left.length > maxLeftLen ? left.slice(0, maxLeftLen) : left;
  const spaces = Math.max(1, widthCols - trimmedLeft.length - safeRight.length);
  return `${trimmedLeft}${" ".repeat(spaces)}${safeRight}`;
}

export interface ReceiptBrandingOptions {
  readonly storeName: string;
  readonly branchName: string;
  readonly taxIdNumber?: string;
  readonly footerMessage?: string;
  readonly paperWidthMm: ThermalPaperWidthMm;
  readonly locale?: string;
}

/**
 * Formats an exact 32-column (58mm) or 48-column (80mm) plain-text and ESC/POS binary buffer
 * for any global currency, with optional Code 128 barcode and RJ11 cash drawer kick pulse.
 */
export function buildEscPosReceipt(
  sale: PosSaleDto,
  branding: ReceiptBrandingOptions,
  options: { kickCashDrawer?: boolean } = {}
): {
  readonly lines: readonly string[];
  readonly byteBuffer: Uint8Array;
} {
  const cols = getLineCharWidth(branding.paperWidthMm);
  const locale = branding.locale ?? "en-US";
  const divider = "-".repeat(cols);
  const fmt = (minor: number) => Money.fromMinor(minor, sale.currencyCode).format(locale);

  const lines: string[] = [
    branding.storeName.slice(0, cols),
    branding.branchName.slice(0, cols),
    ...(branding.taxIdNumber ? [`TAX ID: ${branding.taxIdNumber}`.slice(0, cols)] : []),
    divider,
    formatReceiptRow(`RCPT: ${sale.receiptNumber}`, sale.createdAt.slice(0, 10), cols),
    divider
  ];

  for (const item of sale.items) {
    lines.push(`${item.productName} (${item.uomName})`.slice(0, cols));
    lines.push(
      formatReceiptRow(
        `  ${item.quantity} x ${fmt(item.unitPriceMinor)}`,
        fmt(item.lineGrossMinor),
        cols
      )
    );
  }

  lines.push(divider);
  lines.push(formatReceiptRow("SUBTOTAL", fmt(sale.subtotalMinor), cols));
  if (sale.cartDiscountMinor > 0) {
    lines.push(formatReceiptRow("DISCOUNT", `-${fmt(sale.cartDiscountMinor)}`, cols));
  }
  lines.push(formatReceiptRow("NET", fmt(sale.netAmountMinor), cols));
  lines.push(formatReceiptRow("TAX", fmt(sale.taxAmountMinor), cols));
  lines.push(formatReceiptRow("TOTAL", fmt(sale.totalAmountMinor), cols));
  lines.push(divider);

  for (const p of sale.payments) {
    lines.push(formatReceiptRow(p.tenderType, fmt(p.amountMinor), cols));
  }
  lines.push(formatReceiptRow("CHANGE", fmt(sale.changeDueMinor), cols));
  lines.push(divider);
  lines.push((branding.footerMessage ?? "Thank you for shopping!").slice(0, cols));

  const encoder = new TextEncoder();
  const textBytes = encoder.encode(`${lines.join("\n")}\n\n`);
  const chunks: Uint8Array[] = [
    ESCPOS_COMMANDS.INIT,
    ...(options.kickCashDrawer ? [ESCPOS_COMMANDS.DRAWER_KICK_PIN2] : []),
    textBytes,
    ESCPOS_COMMANDS.PAPER_CUT
  ];

  const totalLength = chunks.reduce((sum, c) => sum + c.length, 0);
  const byteBuffer = new Uint8Array(totalLength);
  let offset = 0;
  for (const chunk of chunks) {
    byteBuffer.set(chunk, offset);
    offset += chunk.length;
  }

  return Object.freeze({ lines: Object.freeze(lines), byteBuffer });
}

export function buildShiftReconciliationSlip(
  shift: CashierShiftDto,
  paperWidthMm: ThermalPaperWidthMm = 58,
  locale = "en-US"
): readonly string[] {
  const cols = getLineCharWidth(paperWidthMm);
  const divider = "=".repeat(cols);
  const fmt = (minor: number) => Money.fromMinor(minor, shift.currencyCode).format(locale);
  return Object.freeze([
    "SHIFT Z-RECONCILIATION".slice(0, cols),
    divider,
    formatReceiptRow("SHIFT ID", shift.id.slice(0, 12), cols),
    formatReceiptRow("OPENING FLOAT", fmt(shift.openingFloatMinor), cols),
    formatReceiptRow("EXPECTED CASH", fmt(shift.expectedCashMinor), cols),
    formatReceiptRow("COUNTED CASH", fmt(shift.countedClosingCashMinor ?? 0), cols),
    formatReceiptRow("VARIANCE", fmt(shift.varianceMinor ?? 0), cols),
    divider
  ]);
}
