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

  it("Consumes pos.sale.completed and pos.sale.returned events from EventBus to deduct and restock base units", async () => {
    const { app, eventBus, repo } = buildInventoryService();
    const orgId = "org_inv_evt";

    const whRes = await app.inject({
      method: "POST",
      url: "/v1/warehouses",
      payload: {
        organizationId: orgId,
        branchId: "br_01",
        code: "WH-EVT",
        name: "Event Warehouse"
      }
    });
    const warehouseId = whRes.json().data.id as string;

    await app.inject({
      method: "POST",
      url: "/v1/movements",
      payload: {
        organizationId: orgId,
        warehouseId,
        productId: "prd_tea",
        currencyCode: "USD",
        reason: "INITIAL_STOCK",
        deltaBaseUnits: 20,
        unitCostMinor: 300,
        actorUserId: "usr_owner"
      }
    });

    await eventBus.publish({
      eventId: "evt_sale_1",
      eventType: "pos.sale.completed",
      organizationId: orgId,
      branchId: "br_01",
      occurredAt: new Date().toISOString(),
      correlationId: "corr_1",
      payload: {
        saleId: "sal_1",
        warehouseId,
        currencyCode: "USD",
        cashierUserId: "usr_cashier",
        items: [{ productId: "prd_tea", baseUnitsDeducted: 5 }]
      }
    });
    expect((await repo.getBalance(orgId, warehouseId, "prd_tea"))?.quantityOnHandBase).toBe(15);

    await eventBus.publish({
      eventId: "evt_ret_1",
      eventType: "pos.sale.returned",
      organizationId: orgId,
      branchId: "br_01",
      occurredAt: new Date().toISOString(),
      correlationId: "corr_2",
      payload: {
        returnId: "ret_1",
        originalSaleId: "sal_1",
        warehouseId,
        currencyCode: "USD",
        cashierUserId: "usr_cashier",
        lines: [{ productId: "prd_tea", baseUnitsReturned: 2, restockToInventory: true }]
      }
    });
    expect((await repo.getBalance(orgId, warehouseId, "prd_tea"))?.quantityOnHandBase).toBe(17);
  });

  it("Executes Bulk Repackaging BOM (e.g. 50kg Rice Sack + 50 Polybags -> 50x 1kg Retail Packs) transferring exact WAC cost", async () => {
    const { app, repo } = buildInventoryService();
    const orgId = "org_bom_01";

    const whRes = await app.inject({
      method: "POST",
      url: "/v1/warehouses",
      payload: {
        organizationId: orgId,
        branchId: "br_01",
        code: "WH-BOM",
        name: "Repackaging Room"
      }
    });
    const warehouseId = whRes.json().data.id as string;

    // Seed 100kg bulk rice @ 40.00/kg (4000 minor) and 100 empty 1kg bags @ 1.00/bag (100 minor)
    await app.inject({
      method: "POST",
      url: "/v1/movements",
      payload: {
        organizationId: orgId,
        warehouseId,
        productId: "prd_bulk_rice_kg",
        currencyCode: "PHP",
        reason: "PO_RECEIPT",
        deltaBaseUnits: 100,
        unitCostMinor: 4000,
        actorUserId: "usr_owner"
      }
    });
    await app.inject({
      method: "POST",
      url: "/v1/movements",
      payload: {
        organizationId: orgId,
        warehouseId,
        productId: "prd_empty_bag",
        currencyCode: "PHP",
        reason: "PO_RECEIPT",
        deltaBaseUnits: 100,
        unitCostMinor: 100,
        actorUserId: "usr_owner"
      }
    });

    // Repackage 50kg bulk rice + 50 empty bags -> 50 x 1kg retail packs (exact unit cost must be 41.00 = 4100 minor!)
    const bomRes = await app.inject({
      method: "POST",
      url: "/v1/bom/repackage",
      payload: {
        organizationId: orgId,
        warehouseId,
        currencyCode: "PHP",
        actorUserId: "usr_owner",
        recipeName: "50kg Sack to 50x 1kg Retail Rice Packs",
        rawInputs: [
          { rawProductId: "prd_bulk_rice_kg", quantityConsumedBaseUnits: 50 },
          { rawProductId: "prd_empty_bag", quantityConsumedBaseUnits: 50 }
        ],
        finishedProductId: "prd_retail_rice_1kg",
        finishedQuantityProducedBaseUnits: 50,
        batchNumber: "REPACK-2026-01"
      }
    });
    expect(bomRes.statusCode).toBe(201);
    expect(bomRes.json().data.totalRawCostMinor).toBe(205000);
    expect(bomRes.json().data.finishedUnitCostMinor).toBe(4100);
    expect(bomRes.json().data.finishedBalance.quantityOnHandBase).toBe(50);
    expect(
      (await repo.getBalance(orgId, warehouseId, "prd_bulk_rice_kg"))?.quantityOnHandBase
    ).toBe(50);
  });
});
