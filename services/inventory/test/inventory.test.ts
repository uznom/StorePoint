import { describe, expect, it } from "vitest";
import { buildInventoryService } from "../src/index.js";

describe("@storepoint/inventory-service — Immutable Stock Ledger, WAC, FEFO Expiry & Stocktake", () => {
  it("Maintains Weighted Average Cost across receipts, prevents negative stock, flags expiring batches, and reconciles stocktakes", async () => {
    const { app } = buildInventoryService();
    const orgId = "org_inv_01";

    // 1. Create Warehouse
    const whRes = await app.inject({
      method: "POST",
      url: "/v1/warehouses",
      payload: {
        organizationId: orgId,
        branchId: "br_01",
        code: "WH-MAIN",
        name: "Main Backroom Warehouse"
      }
    });
    expect(whRes.statusCode).toBe(201);
    const warehouseId = whRes.json().data.id as string;

    // 2. Receive 10 units @ 40.00 (4000 minor) with Batch LOT-A expiring soon
    const rec1 = await app.inject({
      method: "POST",
      url: "/v1/movements",
      payload: {
        organizationId: orgId,
        warehouseId,
        productId: "prd_milk",
        currencyCode: "USD",
        reason: "INITIAL_STOCK",
        deltaBaseUnits: 10,
        unitCostMinor: 4000,
        batchNumber: "LOT-2026-A",
        expirationDate: "2026-12-01",
        actorUserId: "usr_owner"
      }
    });
    expect(rec1.statusCode).toBe(201);

    // 3. Receive 10 units @ 50.00 (5000 minor) -> WAC must become 45.00 (4500 minor), total valuation = 900.00 (90000 minor)
    const rec2 = await app.inject({
      method: "POST",
      url: "/v1/movements",
      payload: {
        organizationId: orgId,
        warehouseId,
        productId: "prd_milk",
        currencyCode: "USD",
        reason: "PO_RECEIPT",
        deltaBaseUnits: 10,
        unitCostMinor: 5000,
        batchNumber: "LOT-2026-B",
        expirationDate: "2027-06-01",
        actorUserId: "usr_owner"
      }
    });
    expect(rec2.statusCode).toBe(201);
    expect(rec2.json().data.balance.quantityOnHandBase).toBe(20);
    expect(rec2.json().data.balance.wacUnitCostMinor).toBe(4500);
    expect(rec2.json().data.balance.totalValuationMinor).toBe(90000);

    // 4. Reject sale exceeding on-hand stock
    const overSale = await app.inject({
      method: "POST",
      url: "/v1/movements",
      payload: {
        organizationId: orgId,
        warehouseId,
        productId: "prd_milk",
        currencyCode: "USD",
        reason: "POS_SALE",
        deltaBaseUnits: -25,
        actorUserId: "usr_cashier"
      }
    });
    expect(overSale.statusCode).toBe(409);

    // 5. Physical Stocktake discovers 18 units on shelf (2 units variance) and sets reorder point to 20
    const stocktakeRes = await app.inject({
      method: "POST",
      url: "/v1/stocktakes",
      payload: {
        organizationId: orgId,
        warehouseId,
        currencyCode: "USD",
        actorUserId: "usr_owner",
        lines: [{ productId: "prd_milk", countedBaseUnits: 18 }]
      }
    });
    expect(stocktakeRes.statusCode).toBe(200);
    expect(stocktakeRes.json().data[0].deltaBaseUnits).toBe(-2);
    expect(stocktakeRes.json().data[0].balanceAfterBaseUnits).toBe(18);

    await app.inject({
      method: "PUT",
      url: "/v1/reorder-points",
      payload: {
        organizationId: orgId,
        warehouseId,
        productId: "prd_milk",
        reorderPointBase: 20
      }
    });

    const lowStockRes = await app.inject({
      method: "GET",
      url: `/v1/alerts/low-stock?organizationId=${orgId}`
    });
    expect(lowStockRes.json().data).toHaveLength(1);

    // 6. FEFO Expiring batch alert flags LOT-2026-A within 30 days
    const expRes = await app.inject({
      method: "GET",
      url: `/v1/alerts/expiring?organizationId=${orgId}&withinDays=30`
    });
    expect(expRes.statusCode).toBe(200);
    expect(expRes.json().data).toHaveLength(1);
    expect(expRes.json().data[0].batchNumber).toBe("LOT-2026-A");
  });
});
