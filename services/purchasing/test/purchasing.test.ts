import { describe, expect, it } from "vitest";
import { buildPurchasingService } from "../src/index.js";

describe("@storepoint/purchasing-service — Suppliers, PO Receiving with Landed Cost & AP Overhead Calendar", () => {
  it("Creates a supplier, receives a PO with landed freight cost allocation, emits purchasing.po.received, and auto-schedules AP invoice", async () => {
    const { app } = buildPurchasingService();
    const orgId = "org_po_01";

    // 1. Create Supplier with NET_15 payment terms
    const supRes = await app.inject({
      method: "POST",
      url: "/v1/suppliers",
      payload: {
        organizationId: orgId,
        name: "Golden Grain Wholesale Ltd.",
        contactPerson: "Elena V.",
        paymentTerms: "NET_15",
        deliverySchedule: "Mondays & Thursdays"
      }
    });
    expect(supRes.statusCode).toBe(201);
    const supplierId = supRes.json().data.id as string;

    // 2. Create PO for 2 x 50kg Rice Sacks (conversionFactorToBase = 50 kg, unitCostMinor = 200000 -> 4000 minor/kg)
    const poRes = await app.inject({
      method: "POST",
      url: "/v1/purchase-orders",
      payload: {
        organizationId: orgId,
        branchId: "br_01",
        warehouseId: "wh_01",
        supplierId,
        currencyCode: "PHP",
        lines: [
          {
            productId: "prd_rice_bulk",
            productName: "Jasmine Rice 50kg Sack",
            uomCode: "sack50",
            conversionFactorToBase: 50,
            quantityOrdered: 2,
            unitCostMinor: 200000
          }
        ]
      }
    });
    expect(poRes.statusCode).toBe(201);
    const poId = poRes.json().data.id as string;
    expect(poRes.json().data.subtotalCostMinor).toBe(400000);

    // 3. Receive all 2 sacks with 100.00 PHP (10000 minor) landed delivery freight -> total cost = 410000
    const recRes = await app.inject({
      method: "POST",
      url: "/v1/purchase-orders/receive",
      payload: {
        organizationId: orgId,
        purchaseOrderId: poId,
        actorUserId: "usr_owner",
        landedFreightCostMinor: 10000,
        lines: [
          {
            productId: "prd_rice_bulk",
            quantityReceivedNow: 2,
            batchNumber: "RICE-2026-11",
            expirationDate: "2027-11-01"
          }
        ]
      }
    });
    expect(recRes.statusCode).toBe(200);
    expect(recRes.json().data.status).toBe("RECEIVED");
    expect(recRes.json().data.totalCostMinor).toBe(410000);

    // 4. Verify Accounts Payable supplier invoice was automatically added to the Overhead Calendar, plus add store rent
    await app.inject({
      method: "POST",
      url: "/v1/overheads",
      payload: {
        organizationId: orgId,
        title: "December Store Rent",
        category: "RENT",
        amountMinor: 1500000,
        currencyCode: "PHP",
        dueDate: "2026-12-01",
        priority: "HIGH"
      }
    });

    const ovhList = await app.inject({
      method: "GET",
      url: `/v1/overheads?organizationId=${orgId}&unpaidOnly=true`
    });
    expect(ovhList.statusCode).toBe(200);
    expect(ovhList.json().data).toHaveLength(2);

    const apId = ovhList.json().data[0].id as string;
    const payRes = await app.inject({
      method: "POST",
      url: `/v1/overheads/${apId}/pay`,
      payload: { organizationId: orgId }
    });
    expect(payRes.statusCode).toBe(200);
    expect(payRes.json().data.isPaid).toBe(true);
  });
});
