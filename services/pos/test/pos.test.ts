import { describe, expect, it } from "vitest";
import { buildPosService } from "../src/index.js";

describe("@storepoint/pos-service — Shifts, Blind Drawer Reconciliation, Split Tenders, Idempotent Offline Sync & Returns", () => {
  it("Executes a complete cashier shift lifecycle with split payments, idempotent retry, item return, and blind denomination close", async () => {
    const { app } = buildPosService();
    const orgId = "org_pos_01";

    // 1. Open Shift with 100.00 (10000 minor) opening float
    const openRes = await app.inject({
      method: "POST",
      url: "/v1/shifts/open",
      payload: {
        organizationId: orgId,
        branchId: "br_01",
        registerId: "reg_01",
        cashierUserId: "usr_cashier",
        currencyCode: "USD",
        openingFloatMinor: 10000
      }
    });
    expect(openRes.statusCode).toBe(201);
    const shiftId = openRes.json().data.id as string;

    // 2. Record Petty Cash Out of 15.00 (1500 minor) for ice delivery -> expected cash = 8500
    const pettyRes = await app.inject({
      method: "POST",
      url: "/v1/shifts/drawer-movements",
      payload: {
        organizationId: orgId,
        shiftId,
        cashierUserId: "usr_cashier",
        type: "CASH_OUT",
        amountMinor: 1500,
        reason: "Petty cash for ice supplier"
      }
    });
    expect(pettyRes.statusCode).toBe(201);
    expect(pettyRes.json().data.shift.expectedCashMinor).toBe(8500);

    // 3. Complete a Multi-Tender Split Sale (Total = 50.00 -> Paid 20.00 MOBILE_WALLET + 40.00 CASH -> Change = 10.00, Net Cash to Drawer = +30.00 -> Expected Cash = 115.00)
    const salePayload = {
      idempotencyKey: "offline_sale_uuid_001",
      organizationId: orgId,
      branchId: "br_01",
      warehouseId: "wh_01",
      registerId: "reg_01",
      shiftId,
      cashierUserId: "usr_cashier",
      currencyCode: "USD",
      items: [
        {
          productId: "prd_coffee",
          productName: "Roast Coffee Pack",
          uomCode: "pack6",
          uomName: "6-Pack",
          conversionFactorToBase: 6,
          quantity: 2,
          unitPriceMinor: 2500
        }
      ],
      payments: [
        { tenderType: "MOBILE_WALLET", amountMinor: 2000, reference: "WALLET-991" },
        { tenderType: "CASH", amountMinor: 4000 }
      ]
    };

    const saleRes = await app.inject({
      method: "POST",
      url: "/v1/sales",
      payload: salePayload
    });
    expect(saleRes.statusCode).toBe(201);
    const saleId = saleRes.json().data.id as string;
    expect(saleRes.json().data.changeDueMinor).toBe(1000);
    expect(saleRes.json().data.items[0].baseUnitsDeducted).toBe(12);

    // 4. Offline Outbox retry with same idempotencyKey does NOT double-charge
    const retryRes = await app.inject({
      method: "POST",
      url: "/v1/sales/sync-batch",
      payload: { sales: [salePayload] }
    });
    expect(retryRes.statusCode).toBe(200);
    expect(retryRes.json().data[0].id).toBe(saleId);

    // 5. Customer returns 1 pack (25.00 cash refund) -> Expected Cash drops from 115.00 to 90.00 (9000 minor)
    const returnRes = await app.inject({
      method: "POST",
      url: "/v1/returns",
      payload: {
        organizationId: orgId,
        originalSaleId: saleId,
        shiftId,
        cashierUserId: "usr_cashier",
        reason: "CUSTOMER_CHANGED_MIND",
        refundTender: "CASH",
        lines: [{ productId: "prd_coffee", quantity: 1, restockToInventory: true }]
      }
    });
    expect(returnRes.statusCode).toBe(201);
    expect(returnRes.json().data.totalRefundMinor).toBe(2500);
    expect(returnRes.json().data.lines[0].baseUnitsReturned).toBe(6);

    // 6. Close Shift with Blind Denomination Count: 4 x $20 + 1 x $10 = $90.00 (9000 minor) -> 0 variance!
    const closeRes = await app.inject({
      method: "POST",
      url: "/v1/shifts/close-blind",
      payload: {
        organizationId: orgId,
        shiftId,
        denominations: [
          { denominationMinor: 2000, count: 4 },
          { denominationMinor: 1000, count: 1 }
        ]
      }
    });
    expect(closeRes.statusCode).toBe(200);
    expect(closeRes.json().data.status).toBe("CLOSED");
    expect(closeRes.json().data.expectedCashMinor).toBe(9000);
    expect(closeRes.json().data.countedClosingCashMinor).toBe(9000);
    expect(closeRes.json().data.varianceMinor).toBe(0);
  });
});
