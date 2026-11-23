import { describe, expect, it } from "vitest";
import {
  ERP_NAVIGATION_SECTIONS,
  buildErpCatalogInventoryOverview,
  buildErpExecutivePnlView,
  buildErpFinanceCrmView,
  buildErpPurchasingAndBomView,
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

  it("Renders matraic/m3e Purchasing, Bulk Repackaging BOM & Accounts Payable Calendar view", () => {
    const vm = buildErpPurchasingAndBomView({
      currencyCode: "USD",
      purchaseOrders: [
        {
          id: "po_1",
          poNumber: "PO-00001",
          organizationId: "org_1",
          branchId: "br_1",
          warehouseId: "wh_1",
          supplierId: "sup_1",
          supplierName: "Pacific Foods",
          currencyCode: "USD",
          status: "ORDERED",
          paymentStatus: "UNPAID",
          subtotalCostMinor: 50000,
          landedFreightCostMinor: 2500,
          totalCostMinor: 52500,
          lines: [],
          expectedDeliveryDate: "2026-12-01",
          receivedAt: null,
          createdAt: new Date().toISOString()
        }
      ],
      overheads: [
        {
          id: "ovh_1",
          organizationId: "org_1",
          title: "Store Electricity Bill",
          category: "UTILITIES",
          amountMinor: 18500,
          currencyCode: "USD",
          dueDate: "2026-12-05",
          priority: "HIGH",
          referenceId: "",
          isPaid: false,
          paidAt: null
        }
      ]
    });

    expect(vm.openPoCount).toBe(1);
    expect(vm.unpaidApTotalFormatted).toBe("$185.00");
    expect(vm.m3eTemplateHtml).toContain("PO-00001 — Pacific Foods");
    expect(vm.m3eTemplateHtml).toContain("Store Electricity Bill");
  });

  it("Renders matraic/m3e Customer AR Credit Tab & Generalized Float Services Hub view", () => {
    const vm = buildErpFinanceCrmView({
      currencyCode: "PHP",
      customers: [
        {
          id: "cus_1",
          organizationId: "org_1",
          name: "Lola Nena",
          phone: "09171234567",
          email: "",
          creditLimitMinor: 200000,
          currentCreditBalanceMinor: 75000,
          loyaltyPoints: 75,
          isActive: true,
          createdAt: new Date().toISOString()
        }
      ],
      floatAccounts: [
        {
          id: "flt_1",
          organizationId: "org_1",
          branchId: "br_1",
          providerCode: "GCASH",
          providerName: "GCash E-Wallet",
          category: "EWALLET",
          currencyCode: "PHP",
          currentBalanceMinor: 1500000,
          defaultServiceFeeMinor: 1000,
          commissionBps: 0,
          updatedAt: new Date().toISOString()
        }
      ]
    });

    expect(vm.activeCreditCustomersCount).toBe(1);
    expect(vm.m3eTemplateHtml).toContain("Lola Nena");
    expect(vm.m3eTemplateHtml).toContain("GCash E-Wallet");
  });

  it("Renders matraic/m3e Executive Profit & Loss (P&L) & Multi-Branch Stock Transfer dashboard view", () => {
    const vm = buildErpExecutivePnlView({
      report: {
        organizationId: "org_1",
        currencyCode: "USD",
        salesRevenueMinor: 500000,
        digitalServiceIncomeMinor: 25000,
        totalRevenueMinor: 525000,
        cogsMinor: 315000,
        grossProfitMinor: 210000,
        grossMarginBps: 4000,
        operatingExpensesMinor: 60000,
        netProfitMinor: 150000,
        taxPayableMinor: 42000,
        journalEntryCount: 18
      }
    });

    expect(vm.grossProfitFormatted).toBe("$2,100.00");
    expect(vm.netProfitFormatted).toBe("$1,500.00");
    expect(vm.grossMarginPercent).toBe("40.00%");
    expect(vm.taxPayableFormatted).toBe("$420.00");
    expect(vm.m3eTemplateHtml).toContain("Net Operating Profit");
  });
});
