import type { CashierShiftDto, PosSaleDto } from "@storepoint/contracts";
import { describe, expect, it } from "vitest";
import {
  ESCPOS_COMMANDS,
  buildEscPosReceipt,
  buildShiftReconciliationSlip
} from "../src/index.js";

describe("@storepoint/print-bridge — 58mm/80mm ESC/POS Thermal Receipt & Cash Drawer Engine", () => {
  const sampleSale: PosSaleDto = {
    id: "sale_1",
    receiptNumber: "OR-000101",
    idempotencyKey: "idem_1",
    organizationId: "org_1",
    branchId: "br_1",
    warehouseId: "wh_1",
    registerId: "reg_1",
    shiftId: "shf_1",
    cashierUserId: "usr_1",
    customerId: null,
    currencyCode: "USD",
    subtotalMinor: 1000,
    cartDiscountMinor: 0,
    netAmountMinor: 1000,
    taxAmountMinor: 83,
    totalAmountMinor: 1083,
    totalPaidMinor: 2000,
    changeDueMinor: 917,
    items: [
      {
        productId: "prd_1",
        productName: "Artisan Sourdough Bread",
        uomCode: "loaf",
        uomName: "Loaf",
        conversionFactorToBase: 1,
        quantity: 2,
        baseUnitsDeducted: 2,
        unitPriceMinor: 500,
        lineDiscountMinor: 0,
        lineNetMinor: 1000,
        lineTaxMinor: 83,
        lineGrossMinor: 1083
      }
    ],
    payments: [{ tenderType: "CASH", amountMinor: 2000 }],
    status: "COMPLETED",
    createdAt: "2026-11-23T16:00:00.000Z"
  };

  it("Enforces strict 32-char line widths on 58mm paper and includes RJ11 cash drawer kick bytes", () => {
    const receipt = buildEscPosReceipt(
      sampleSale,
      {
        storeName: "Cornerstone Bakery & Grocery Mart",
        branchName: "Downtown Branch",
        taxIdNumber: "123-456-789",
        paperWidthMm: 58
      },
      { kickCashDrawer: true }
    );

    for (const line of receipt.lines) {
      expect(line.length).toBeLessThanOrEqual(32);
    }
    // Starts with ESC @ (0x1b, 0x40) followed by ESC p drawer kick (0x1b, 0x70)
    expect(receipt.byteBuffer[0]).toBe(ESCPOS_COMMANDS.INIT[0]);
    expect(receipt.byteBuffer[2]).toBe(ESCPOS_COMMANDS.DRAWER_KICK_PIN2[0]);
    expect(receipt.byteBuffer[3]).toBe(ESCPOS_COMMANDS.DRAWER_KICK_PIN2[1]);
  });

  it("Enforces 48-char line widths on 80mm paper and formats shift Z-reconciliation slips", () => {
    const receipt80 = buildEscPosReceipt(sampleSale, {
      storeName: "Cornerstone Bakery & Grocery Mart",
      branchName: "Downtown Branch",
      paperWidthMm: 80
    });
    expect(receipt80.lines[3]?.length).toBe(48);

    const shift: CashierShiftDto = {
      id: "shf_123456789",
      organizationId: "org_1",
      branchId: "br_1",
      registerId: "reg_1",
      cashierUserId: "usr_1",
      currencyCode: "USD",
      status: "CLOSED",
      openingFloatMinor: 10000,
      expectedCashMinor: 25000,
      countedClosingCashMinor: 24950,
      varianceMinor: -50,
      denominations: [],
      openedAt: "2026-11-23T08:00:00.000Z",
      closedAt: "2026-11-23T16:00:00.000Z",
      notes: ""
    };
    const slip = buildShiftReconciliationSlip(shift, 58);
    expect(slip.every((l) => l.length <= 32)).toBe(true);
    expect(slip.some((l) => l.includes("VARIANCE"))).toBe(true);
  });
});
