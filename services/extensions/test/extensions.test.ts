import { describe, expect, it } from "vitest";
import { buildExtensionsService } from "../src/index.js";

describe("@storepoint/extensions-service — Global Country Packs, Webhooks & Android v1 -> v2 Migration Engine", () => {
  it("Lists global Country Packs (PH, IN, KE, MX, US), activates a pack, and registers webhooks", async () => {
    const { app } = buildExtensionsService();

    const listRes = await app.inject({ method: "GET", url: "/v1/country-packs" });
    expect(listRes.statusCode).toBe(200);
    expect(listRes.json().data.length).toBeGreaterThanOrEqual(5);

    const actRes = await app.inject({
      method: "POST",
      url: "/v1/country-packs/activate",
      payload: { organizationId: "org_1", packId: "pack-ke-duka" }
    });
    expect(actRes.statusCode).toBe(200);
    expect(actRes.json().data.defaultCurrencyCode).toBe("KES");

    const whkRes = await app.inject({
      method: "POST",
      url: "/v1/webhooks",
      payload: {
        organizationId: "org_1",
        targetUrl: "https://hooks.storepoint.example/pos-sync",
        eventTypes: ["pos.sale.completed", "inventory.stock.adjusted"]
      }
    });
    expect(whkRes.statusCode).toBe(201);
  });

  it("Migrates a StorePoint v1 Android JSON backup (legacy cigarette/custom UOMs & GCash/Load balances) into v2 schemas", async () => {
    const { app } = buildExtensionsService();

    const migrateRes = await app.inject({
      method: "POST",
      url: "/v1/migrations/v1-import",
      payload: {
        organizationId: "org_migrated_ph",
        currencyCode: "PHP",
        backup: {
          storeConfig: {
            storeName: "Aling Nena Sari-Sari Store",
            currencySymbol: "₱",
            taxPercentage: 12.0,
            gcashBalance: 15000.5,
            smartLoadBalance: 3200.0,
            gcashServiceFee: 10.0,
            loadServiceFee: 2.0
          },
          categories: [{ id: 1, name: "Tobacco & Sundries" }],
          products: [
            {
              id: 101,
              name: "Menthol Cigarette",
              categoryId: 1,
              price: 9.0, // 1 stick (tingi)
              cost: 7.0,
              stockCount: 400,
              barcode: "48099901",
              baseUom: "stick",
              hasStick10s: true,
              priceStick10s: 85.0,
              barcode10s: "48099910",
              hasStick20s: true,
              priceStick20s: 165.0,
              barcode20s: "48099920",
              hasReam: true,
              priceReam: 1600.0,
              barcodeReam: "48099200"
            }
          ]
        }
      }
    });

    expect(migrateRes.statusCode).toBe(200);
    const plan = migrateRes.json().data;
    expect(plan.normalizedProducts).toHaveLength(1);
    // Base stick + 3 additional UOMs (10s, 20s, Ream 200s)
    expect(plan.normalizedProducts[0].additionalUoms).toHaveLength(3);
    expect(plan.normalizedProducts[0].basePriceMinor).toBe(900);
    // GCash (15,000.50 -> 1500050 minor) + Smart Load (3,200.00 -> 320000 minor)
    expect(plan.floatAccountsToCreate).toHaveLength(2);
    expect(plan.floatAccountsToCreate[0].initialBalanceMinor).toBe(1500050);
    expect(plan.initialStockMovements[0].quantityBaseUnits).toBe(400);
  });
});
