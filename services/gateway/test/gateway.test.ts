import { describe, expect, it } from "vitest";
import { buildGatewayService } from "../src/index.js";

describe("@storepoint/gateway-service — API Gateway Routing Across Identity, Catalog & Inventory", () => {
  it("Reports 3 active microservices (identity, catalog, inventory) and injects correlation IDs", async () => {
    const { gateway } = buildGatewayService();

    const healthRes = await gateway.inject({ method: "GET", url: "/health" });
    expect(healthRes.statusCode).toBe(200);
    expect(healthRes.headers["x-correlation-id"]).toMatch(/^corr_/);

    const body = healthRes.json();
    expect(body.success).toBe(true);
    const online = body.data.upstreamServices.filter(
      (s: { status: string }) => s.status === "online"
    );
    expect(online).toHaveLength(3);
  });

  it("Routes end-to-end store onboarding -> product creation -> stock movement across decoupled services", async () => {
    const { gateway, eventBus } = buildGatewayService();

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
    expect(onboardRes.statusCode).toBe(201);
    const orgId = onboardRes.json().data.organization.id as string;
    const branchId = onboardRes.json().data.defaultBranch.id as string;
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
    expect(prodRes.statusCode).toBe(201);
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

    const movRes = await gateway.inject({
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
    expect(movRes.statusCode).toBe(201);
    expect(movRes.json().data.balance.quantityOnHandBase).toBe(50);

    // Event bus captured events from all 3 decoupled services
    const eventTypes = eventBus.getPublishedEvents().map((e) => e.eventType);
    expect(eventTypes).toContain("identity.organization.created");
    expect(eventTypes).toContain("catalog.product.upserted");
    expect(eventTypes).toContain("inventory.stock.adjusted");
  });
});
