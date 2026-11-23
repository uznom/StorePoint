import { buildCatalogService } from "@storepoint/catalog-service";
import type {
  CreateCategoryInput,
  CreateOrganizationInput,
  CreateProductInput,
  CreateUserInput,
  PinAuthInput,
  RecordStockMovementInput,
  ServiceHealthDto
} from "@storepoint/contracts";
import { buildIdentityService } from "@storepoint/identity-service";
import { buildInventoryService } from "@storepoint/inventory-service";
import { InMemoryEventBus, ok } from "@storepoint/service-kit";
import Fastify, { type FastifyInstance } from "fastify";
import { randomUUID } from "node:crypto";

export interface GatewayTopologyStatus {
  readonly gateway: ServiceHealthDto;
  readonly upstreamServices: readonly {
    readonly name: string;
    readonly phase: string;
    readonly status: "online" | "planned";
  }[];
}

/**
 * Decoupled API Gateway & Auth Edge.
 * Routes traffic across independent domain services (`identity`, `catalog`, `inventory`)
 * connected via the shared EventBus.
 */
export function buildGatewayService(): {
  gateway: FastifyInstance;
  eventBus: InMemoryEventBus;
  identity: ReturnType<typeof buildIdentityService>;
  catalog: ReturnType<typeof buildCatalogService>;
  inventory: ReturnType<typeof buildInventoryService>;
} {
  const eventBus = new InMemoryEventBus();
  const identity = buildIdentityService({ eventBus });
  const catalog = buildCatalogService({ eventBus });
  const inventory = buildInventoryService({ eventBus });
  const gateway = Fastify({ logger: false });

  gateway.addHook("onRequest", async (req, reply) => {
    const incomingCorrId = req.headers["x-correlation-id"];
    const correlationId =
      typeof incomingCorrId === "string" && incomingCorrId.trim()
        ? incomingCorrId
        : `corr_${randomUUID()}`;
    reply.header("x-correlation-id", correlationId);
  });

  gateway.get("/health", async () => {
    const status: GatewayTopologyStatus = {
      gateway: {
        service: "gateway-service",
        status: "healthy",
        version: "2.0.0-alpha.0",
        timestamp: new Date().toISOString()
      },
      upstreamServices: [
        { name: "identity-service", phase: "Phase 0", status: "online" },
        { name: "catalog-service", phase: "Phase 1", status: "online" },
        { name: "inventory-service", phase: "Phase 1", status: "online" },
        { name: "pos-service", phase: "Phase 2", status: "planned" },
        { name: "purchasing-service", phase: "Phase 3", status: "planned" },
        { name: "finance-crm-service", phase: "Phase 4", status: "planned" },
        { name: "extensions-service", phase: "Phase 6", status: "planned" }
      ]
    };
    return ok(status);
  });

  // Identity routes
  gateway.post<{ Body: CreateOrganizationInput }>(
    "/api/v1/identity/organizations/onboard",
    async (req, reply) => {
      const upstream = await identity.app.inject({
        method: "POST",
        url: "/v1/organizations/onboard",
        payload: req.body
      });
      return reply.code(upstream.statusCode).send(upstream.json());
    }
  );

  gateway.post<{ Body: CreateUserInput }>("/api/v1/identity/users", async (req, reply) => {
    const upstream = await identity.app.inject({
      method: "POST",
      url: "/v1/users",
      payload: req.body
    });
    return reply.code(upstream.statusCode).send(upstream.json());
  });

  gateway.post<{ Body: PinAuthInput }>("/api/v1/identity/auth/pin", async (req, reply) => {
    const upstream = await identity.app.inject({
      method: "POST",
      url: "/v1/auth/pin",
      payload: req.body
    });
    return reply.code(upstream.statusCode).send(upstream.json());
  });

  // Catalog routes
  gateway.post<{ Body: CreateCategoryInput }>("/api/v1/catalog/categories", async (req, reply) => {
    const upstream = await catalog.app.inject({
      method: "POST",
      url: "/v1/categories",
      payload: req.body
    });
    return reply.code(upstream.statusCode).send(upstream.json());
  });

  gateway.post<{ Body: CreateProductInput }>("/api/v1/catalog/products", async (req, reply) => {
    const upstream = await catalog.app.inject({
      method: "POST",
      url: "/v1/products",
      payload: req.body
    });
    return reply.code(upstream.statusCode).send(upstream.json());
  });

  gateway.get<{ Querystring: { organizationId: string; barcode: string } }>(
    "/api/v1/catalog/products/lookup",
    async (req, reply) => {
      const upstream = await catalog.app.inject({
        method: "GET",
        url: `/v1/products/lookup?organizationId=${encodeURIComponent(req.query.organizationId)}&barcode=${encodeURIComponent(req.query.barcode)}`
      });
      return reply.code(upstream.statusCode).send(upstream.json());
    }
  );

  // Inventory routes
  gateway.post<{
    Body: { organizationId: string; branchId: string; code: string; name: string };
  }>("/api/v1/inventory/warehouses", async (req, reply) => {
    const upstream = await inventory.app.inject({
      method: "POST",
      url: "/v1/warehouses",
      payload: req.body
    });
    return reply.code(upstream.statusCode).send(upstream.json());
  });

  gateway.post<{ Body: RecordStockMovementInput }>(
    "/api/v1/inventory/movements",
    async (req, reply) => {
      const upstream = await inventory.app.inject({
        method: "POST",
        url: "/v1/movements",
        payload: req.body
      });
      return reply.code(upstream.statusCode).send(upstream.json());
    }
  );

  return { gateway, eventBus, identity, catalog, inventory };
}
