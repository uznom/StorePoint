import { describe, expect, it } from "vitest";
import { buildGatewayService } from "../src/index.js";

describe("@storepoint/gateway-service — Decoupled Routing Across Identity, Catalog, Inventory & POS", () => {
  it("Reports 6 active microservices (identity, catalog, inventory, pos, purchasing, finance-crm) and injects correlation IDs", async () => {
    const { gateway } = buildGatewayService();

    const healthRes = await gateway.inject({ method: "GET", url: "/health" });
    expect(healthRes.statusCode).toBe(200);
    expect(healthRes.headers["x-correlation-id"]).toMatch(/^corr_/);

    const body = healthRes.json();
    expect(body.success).toBe(true);
    const online = body.data.upstreamServices.filter(
      (s: { status: string }) => s.status === "online"
    );
    expect(online).toHaveLength(6);
  });

  it("Executes end-to-end onboarding -> product creation -> initial stock -> POS multi-UOM sale -> event-driven inventory deduction -> return restock", async () => {
    const { gateway, inventory } = buildGatewayService();

    const onboardRes = await gateway.inject({
      method: "POST",
      url: "/api/v1/identity/organizations/onboard",
      payload: {
        name: "Bodega Mi Barrio",
        defaultCurrencyCode: "MXN",
        locale: "es-MX",
        timezone: "America/Mexico_City",
        templateType: "GROCERY_MINIMART",
        ownerUsername: "carlos",
        ownerDisplayName: "Carlos R.",
        ownerPin: "6814"
      }
    });
    const orgId = onboardRes.json().data.organization.id as string;
    const branchId = onboardRes.json().data.defaultBranch.id as string;
    const registerId = onboardRes.json().data.defaultRegister.id as string;
    const ownerId = onboardRes.json().data.owner.id as string;

    const catRes = await gateway.inject({
      method: "POST",
      url: "/api/v1/catalog/categories",
      payload: { organizationId: orgId, name: "Abarrotes" }
    });
    const categoryId = catRes.json().data.id as string;

    const prodRes = await gateway.inject({
      method: "POST",
      url: "/api/v1/catalog/products",
      payload: {
        organizationId: orgId,
        categoryId,
        sku: "ARR-1KG",
        name: "Arroz Extra 1kg",
        baseUomCode: "kg",
        baseUomName: "1 Kilogram Bag",
        baseBarcode: "750100011122",
        basePriceMinor: 3200
      }
    });
    const productId = prodRes.json().data.id as string;

    const whRes = await gateway.inject({
      method: "POST",
      url: "/api/v1/inventory/warehouses",
      payload: {
        organizationId: orgId,
        branchId,
        code: "WH-01",
        name: "Almacen Principal"
      }
    });
    const warehouseId = whRes.json().data.id as string;

    // Receive 50 base units
    await gateway.inject({
      method: "POST",
      url: "/api/v1/inventory/movements",
      payload: {
        organizationId: orgId,
        warehouseId,
        productId,
        currencyCode: "MXN",
        reason: "INITIAL_STOCK",
        deltaBaseUnits: 50,
        unitCostMinor: 2400,
        actorUserId: ownerId
      }
    });

    // Open POS Shift
    const shiftRes = await gateway.inject({
      method: "POST",
      url: "/api/v1/pos/shifts/open",
      payload: {
        organizationId: orgId,
        branchId,
        registerId,
        cashierUserId: ownerId,
        currencyCode: "MXN",
        openingFloatMinor: 50000
      }
    });
    const shiftId = shiftRes.json().data.id as string;

    // Complete POS Sale for 3 x 1kg bags -> EventBus automatically deducts 3 base units in inventory-service (50 -> 47)!
    const saleRes = await gateway.inject({
      method: "POST",
      url: "/api/v1/pos/sales",
      payload: {
        idempotencyKey: "pos_sale_e2e_01",
        organizationId: orgId,
        branchId,
        warehouseId,
        registerId,
        shiftId,
        cashierUserId: ownerId,
        currencyCode: "MXN",
        items: [
          {
            productId,
            productName: "Arroz Extra 1kg",
            uomCode: "kg",
            uomName: "1 Kilogram Bag",
            conversionFactorToBase: 1,
            quantity: 3,
            unitPriceMinor: 3200
          }
        ],
        payments: [{ tenderType: "CASH", amountMinor: 10000 }]
      }
    });
    expect(saleRes.statusCode).toBe(201);
    const saleId = saleRes.json().data.id as string;

    const balanceAfterSale = await inventory.repo.getBalance(orgId, warehouseId, productId);
    expect(balanceAfterSale?.quantityOnHandBase).toBe(47);

    // Process Return of 1 unit with restockToInventory: true -> EventBus automatically restores stock to 48!
    const retRes = await gateway.inject({
      method: "POST",
      url: "/api/v1/pos/returns",
      payload: {
        organizationId: orgId,
        originalSaleId: saleId,
        shiftId,
        cashierUserId: ownerId,
        reason: "CUSTOMER_CHANGED_MIND",
        refundTender: "CASH",
        lines: [{ productId, quantity: 1, restockToInventory: true }]
      }
    });
    expect(retRes.statusCode).toBe(201);

    const balanceAfterReturn = await inventory.repo.getBalance(orgId, warehouseId, productId);
    expect(balanceAfterReturn?.quantityOnHandBase).toBe(48);

    // Phase 3 E2E: Create Supplier -> Receive PO (50kg bulk sack) -> EventBus credits inventory -> BOM Repackage into 50x 1kg packs
    const supRes = await gateway.inject({
      method: "POST",
      url: "/api/v1/purchasing/suppliers",
      payload: {
        organizationId: orgId,
        name: "Distribuidora del Valle",
        paymentTerms: "NET_15"
      }
    });
    const supplierId = supRes.json().data.id as string;

    const poRes = await gateway.inject({
      method: "POST",
      url: "/api/v1/purchasing/purchase-orders",
      payload: {
        organizationId: orgId,
        branchId,
        warehouseId,
        supplierId,
        currencyCode: "MXN",
        lines: [
          {
            productId: "prd_bulk_sugar_kg",
            productName: "Azucar Bulto 50kg",
            uomCode: "sack50",
            conversionFactorToBase: 50,
            quantityOrdered: 1,
            unitCostMinor: 100000 // 20.00 MXN per kg
          }
        ]
      }
    });
    const poId = poRes.json().data.id as string;

    const recPoRes = await gateway.inject({
      method: "POST",
      url: "/api/v1/purchasing/purchase-orders/receive",
      payload: {
        organizationId: orgId,
        purchaseOrderId: poId,
        actorUserId: ownerId,
        landedFreightCostMinor: 5000, // +1.00 MXN/kg freight -> 21.00 MXN/kg landed WAC!
        lines: [{ productId: "prd_bulk_sugar_kg", quantityReceivedNow: 1 }]
      }
    });
    expect(recPoRes.statusCode).toBe(200);

    const bulkSugarBal = await inventory.repo.getBalance(
      orgId,
      warehouseId,
      "prd_bulk_sugar_kg"
    );
    expect(bulkSugarBal?.quantityOnHandBase).toBe(50);
    expect(bulkSugarBal?.wacUnitCostMinor).toBe(2100);

    const bomRes = await gateway.inject({
      method: "POST",
      url: "/api/v1/inventory/bom/repackage",
      payload: {
        organizationId: orgId,
        warehouseId,
        currencyCode: "MXN",
        actorUserId: ownerId,
        recipeName: "Repack 50kg Sugar into 50x 1kg Bags",
        rawInputs: [{ rawProductId: "prd_bulk_sugar_kg", quantityConsumedBaseUnits: 50 }],
        finishedProductId: "prd_retail_sugar_1kg",
        finishedQuantityProducedBaseUnits: 50
      }
    });
    expect(bomRes.statusCode).toBe(201);
    expect(bomRes.json().data.finishedUnitCostMinor).toBe(2100);

    // Phase 4 E2E: Create Customer Credit Tab & Charge Store Credit via Gateway
    const cusRes = await gateway.inject({
      method: "POST",
      url: "/api/v1/finance-crm/customers",
      payload: {
        organizationId: orgId,
        name: "Juan Perez",
        creditLimitMinor: 100000
      }
    });
    expect(cusRes.statusCode).toBe(201);
    const customerId = cusRes.json().data.id as string;

    const arChargeRes = await gateway.inject({
      method: "POST",
      url: "/api/v1/finance-crm/customers/credit/charge",
      payload: {
        organizationId: orgId,
        customerId,
        amountMinor: 25000,
        referenceId: saleId,
        actorUserId: ownerId
      }
    });
    expect(arChargeRes.statusCode).toBe(201);
    expect(arChargeRes.json().data.customer.currentCreditBalanceMinor).toBe(25000);
  });
});
