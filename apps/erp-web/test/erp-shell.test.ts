import { describe, expect, it } from "vitest";
import {
  ERP_NAVIGATION_SECTIONS,
  buildErpCatalogInventoryOverview,
  createErpShellConfig
} from "../src/index.js";

describe("@storepoint/erp-web — Material 3 Expressive Back-Office ERP Shell & Catalog/Inventory Views", () => {
  it("Builds M3E ERP shell config with decoupled service navigation and currency context", () => {
    const shell = createErpShellConfig({
      storeName: "Aloha Neighborhood Mart",
      currencyCode: "USD",
      templateType: "GROCERY_MINIMART",
      theme: "emerald"
    });
    expect(shell.appTitle).toContain("Aloha Neighborhood Mart");
    expect(shell.currency.symbol).toBe("$");
    expect(shell.m3eThemeCss).toContain("--m3e-color-primary:");
    expect(ERP_NAVIGATION_SECTIONS).toHaveLength(8);
  });

  it("Renders matraic/m3e (<m3e-card>, <m3e-chip>, <m3e-badge>) Catalog & WAC Inventory overview", () => {
    const vm = buildErpCatalogInventoryOverview({
      currencyCode: "USD",
      products: [
        {
          id: "prd_1",
          organizationId: "org_1",
          categoryId: "cat_1",
          taxGroupId: null,
          sku: "COF-01",
          name: "Organic Roast Coffee",
          description: "",
          baseUomCode: "bag",
          baseUomName: "250g Bag",
          baseBarcode: "10001",
          basePriceMinor: 850,
          initialCostMinor: 500,
          trackInventory: true,
          trackExpiry: true,
          isArchived: false,
          uoms: [
            {
              id: "uom_1",
              productId: "prd_1",
              uomCode: "bag",
              uomName: "250g Bag",
              conversionFactorToBase: 1,
              barcode: "10001",
              priceMinor: 850,
              isBase: true
            },
            {
              id: "uom_2",
              productId: "prd_1",
              uomCode: "box12",
              uomName: "12-Bag Case",
              conversionFactorToBase: 12,
              barcode: "10012",
              priceMinor: 9600,
              isBase: false
            }
          ],
          createdAt: new Date().toISOString()
        }
      ],
      balances: [
        {
          organizationId: "org_1",
          warehouseId: "wh_1",
          productId: "prd_1",
          quantityOnHandBase: 24,
          reorderPointBase: 10,
          wacUnitCostMinor: 500,
          totalValuationMinor: 12000,
          updatedAt: new Date().toISOString()
        }
      ],
      lowStockAlerts: [],
      expiringBatches: []
    });

    expect(vm.totalSkuCount).toBe(1);
    expect(vm.totalValuationFormatted).toBe("$120.00");
    expect(vm.m3eTemplateHtml).toContain("<m3e-card");
    expect(vm.m3eTemplateHtml).toContain("<m3e-chip");
    expect(vm.m3eTemplateHtml).toContain("12-Bag Case (x12)");
  });
});
