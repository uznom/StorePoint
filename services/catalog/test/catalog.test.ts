import { describe, expect, it } from "vitest";
import { buildCatalogService } from "../src/index.js";

describe("@storepoint/catalog-service — Global Tax Presets & Multi-UOM Barcode Engine", () => {
  it("Seeds country tax presets and resolves single-piece vs pack vs case barcodes to the right UOM multiplier", async () => {
    const { app } = buildCatalogService();
    const orgId = "org_global_01";

    // 1. Seed India GST 18% (9% CGST + 9% SGST) preset
    const taxRes = await app.inject({
      method: "POST",
      url: "/v1/tax-groups/seed-preset",
      payload: { organizationId: orgId, countryCode: "IN" }
    });
    expect(taxRes.statusCode).toBe(201);
    expect(taxRes.json().data[0].rules).toHaveLength(2);

    // 2. Create a Category
    const catRes = await app.inject({
      method: "POST",
      url: "/v1/categories",
      payload: { organizationId: orgId, name: "Beverages & Instant Coffee" }
    });
    expect(catRes.statusCode).toBe(201);
    const categoryId = catRes.json().data.id as string;

    // 3. Create a Multi-UOM product (1 Sachet = 1 base pc, 12-Pack Tie = 12 base pcs, Master Case = 144 base pcs)
    const prodRes = await app.inject({
      method: "POST",
      url: "/v1/products",
      payload: {
        organizationId: orgId,
        categoryId,
        sku: "COF-NES-3IN1",
        name: "3-in-1 Instant Coffee",
        baseUomCode: "sachet",
        baseUomName: "Single Sachet",
        baseBarcode: "480001110001",
        basePriceMinor: 1500,
        initialCostMinor: 1100,
        additionalUoms: [
          {
            uomCode: "pack12",
            uomName: "Dozen Tie (12s)",
            conversionFactorToBase: 12,
            barcode: "480001110012",
            priceMinor: 17000
          },
          {
            uomCode: "case144",
            uomName: "Master Carton (144s)",
            conversionFactorToBase: 144,
            barcode: "480001110144",
            priceMinor: 198000
          }
        ]
      }
    });
    expect(prodRes.statusCode).toBe(201);
    expect(prodRes.json().data.uoms).toHaveLength(3);

    // 4. Scan the 12-Pack barcode at POS -> resolves to conversionFactorToBase = 12
    const lookupPackRes = await app.inject({
      method: "GET",
      url: `/v1/products/lookup?organizationId=${orgId}&barcode=480001110012`
    });
    expect(lookupPackRes.statusCode).toBe(200);
    expect(lookupPackRes.json().data.matchedUom.uomCode).toBe("pack12");
    expect(lookupPackRes.json().data.matchedUom.conversionFactorToBase).toBe(12);

    // 5. Reject duplicate barcode collision
    const dupRes = await app.inject({
      method: "POST",
      url: "/v1/products",
      payload: {
        organizationId: orgId,
        categoryId,
        sku: "DUP-01",
        name: "Conflicting Item",
        baseUomCode: "pc",
        baseUomName: "Piece",
        baseBarcode: "480001110012",
        basePriceMinor: 1000
      }
    });
    expect(dupRes.statusCode).toBe(409);
  });
});
