import { describe, expect, it } from "vitest";
import { buildOfflineSaleRecord, getPosTerminalThemeCss } from "../src/index.js";

describe("@storepoint/pos-web — Offline-First POS Cart & Outbox Engine", () => {
  it("Computes multi-UOM cart totals offline and stamps an idempotency key for sync", () => {
    const sale = buildOfflineSaleRecord({
      registerId: "reg_01",
      currencyCode: "PHP",
      lines: [
        {
          lineId: "l1",
          productId: "prd_rice",
          productName: " Jasmine Rice",
          uomName: "1kg Pack",
          uomMultiplier: 1,
          quantity: 2,
          unitPriceMinor: 5600 // 56.00 PHP each -> 112.00 PHP gross
        }
      ],
      taxRules: [{ id: "vat12", name: "12% VAT", rateBps: 1200, mode: "INCLUSIVE" }]
    });

    expect(sale.idempotencyKey).toMatch(/^pos_sale_/);
    expect(sale.grossMinor).toBe(11200);
    expect(sale.netMinor).toBe(10000);
    expect(sale.taxMinor).toBe(1200);
    expect(getPosTerminalThemeCss("amber")).toContain("--m3e-color-primary:");
  });
});
