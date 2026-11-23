import { describe, expect, it } from "vitest";
import {
  OfflinePosTerminalStore,
  buildCustomerFacingDisplayView,
  buildOfflineSaleRecord,
  buildPosTerminalM3eHtml,
  getPosTerminalThemeCss
} from "../src/index.js";

describe("@storepoint/pos-web — Offline-First POS Cart, Park/Resume, Outbox & matraic/m3e UI", () => {
  const sampleLines = [
    {
      lineId: "l1",
      productId: "prd_rice",
      productName: "Jasmine Rice",
      uomName: "1kg Pack",
      uomMultiplier: 1,
      quantity: 2,
      unitPriceMinor: 5600 // 56.00 PHP each -> 112.00 PHP gross
    }
  ];

  it("Computes multi-UOM cart totals offline and manages Cart Park/Resume and Outbox sync queue", () => {
    const store = new OfflinePosTerminalStore();

    // 1. Park cart while attending to another customer
    const ticket = store.parkCart("Table 3 / Maria", sampleLines);
    expect(store.listParkedCarts()).toHaveLength(1);

    // 2. Resume parked cart
    const resumed = store.resumeCart(ticket.ticketId);
    expect(resumed.customerNote).toBe("Table 3 / Maria");
    expect(store.listParkedCarts()).toHaveLength(0);

    // 3. Complete offline sale and queue in outbox
    const sale = buildOfflineSaleRecord({
      registerId: "reg_01",
      currencyCode: "PHP",
      lines: resumed.lines,
      taxRules: [{ id: "vat12", name: "12% VAT", rateBps: 1200, mode: "INCLUSIVE" }]
    });

    store.enqueueOfflineSale(sale);
    expect(store.listPendingOutbox()).toHaveLength(1);

    // 4. Render matraic/m3e POS terminal HTML in offline mode
    const html = buildPosTerminalM3eHtml({
      registerName: "Counter 1",
      currencyCode: "PHP",
      isOnline: false,
      lines: resumed.lines,
      parkedCount: store.listParkedCarts().length,
      pendingOutboxCount: store.listPendingOutbox().length
    });
    expect(html).toContain("Offline Mode");
    expect(html).toContain("<m3e-button");
    expect(html).toContain("Outbox: 1");

    // 5. Mark synced when internet reconnects
    store.markSynced([sale.idempotencyKey]);
    expect(store.listPendingOutbox()).toHaveLength(0);
    expect(getPosTerminalThemeCss("amber")).toContain("--m3e-color-primary:");

    // 6. Render Customer-Facing Display (CFD) with dynamic QR payment payload
    const cfd = buildCustomerFacingDisplayView({
      storeName: "Sunrise Mart",
      registerName: "Counter 1",
      currencyCode: "PHP",
      lines: sampleLines
    });
    expect(cfd.itemCount).toBe(2);
    expect(cfd.qrPaymentPayload).toContain("amountMinor=11200");
    expect(cfd.m3eCfdHtml).toContain("Scan to Pay via Instant QR");
  });
});
