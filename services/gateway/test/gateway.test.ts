import { describe, expect, it } from "vitest";
import { buildGatewayService } from "../src/index.js";

describe("@storepoint/gateway-service — API Gateway & Identity Routing", () => {
  it("Reports decoupled service topology health and injects correlation IDs", async () => {
    const { gateway } = buildGatewayService();

    const healthRes = await gateway.inject({ method: "GET", url: "/health" });
    expect(healthRes.statusCode).toBe(200);
    expect(healthRes.headers["x-correlation-id"]).toMatch(/^corr_/);

    const body = healthRes.json();
    expect(body.success).toBe(true);
    expect(body.data.gateway.service).toBe("gateway-service");
    expect(body.data.upstreamServices).toHaveLength(7);
  });

  it("Routes store onboarding and cashier PIN login through the gateway to identity-service", async () => {
    const { gateway } = buildGatewayService();

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

    const loginRes = await gateway.inject({
      method: "POST",
      url: "/api/v1/identity/auth/pin",
      payload: {
        organizationId: orgId,
        username: "carlos",
        pin: "6814"
      }
    });
    expect(loginRes.statusCode).toBe(200);
    expect(loginRes.json().data.role).toBe("OWNER");
  });
});
