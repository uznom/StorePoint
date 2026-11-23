import { buildCatalogService } from "@storepoint/catalog-service";
import type {
  CompletePosSaleInput,
  CreateCategoryInput,
  CreateOrganizationInput,
  CreateProductInput,
  CreateUserInput,
  ExecuteBomRepackagingInput,
  ExecuteFloatTransactionInput,
  FloatServiceCategory,
  PaymentTermsCode,
  PinAuthInput,
  ProcessPosReturnInput,
  PurchaseOrderLineInput,
  ReceivePurchaseOrderInput,
  RecordStockMovementInput,
  ServiceHealthDto
} from "@storepoint/contracts";
import { buildFinanceCrmService } from "@storepoint/finance-crm-service";
import { buildIdentityService } from "@storepoint/identity-service";
import { buildInventoryService } from "@storepoint/inventory-service";
import { buildPosService } from "@storepoint/pos-service";
import { buildPurchasingService } from "@storepoint/purchasing-service";
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
 * Routes traffic across independent domain services (`identity`, `catalog`, `inventory`, `pos`, `purchasing`)
 * connected via the shared EventBus.
 */
export function buildGatewayService(): {
  gateway: FastifyInstance;
  eventBus: InMemoryEventBus;
  identity: ReturnType<typeof buildIdentityService>;
  catalog: ReturnType<typeof buildCatalogService>;
  inventory: ReturnType<typeof buildInventoryService>;
  pos: ReturnType<typeof buildPosService>;
  purchasing: ReturnType<typeof buildPurchasingService>;
  financeCrm: ReturnType<typeof buildFinanceCrmService>;
} {
  const eventBus = new InMemoryEventBus();
  const identity = buildIdentityService({ eventBus });
  const catalog = buildCatalogService({ eventBus });
  const inventory = buildInventoryService({ eventBus });
  const pos = buildPosService({ eventBus });
  const purchasing = buildPurchasingService({ eventBus });
  const financeCrm = buildFinanceCrmService({ eventBus });
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
        { name: "pos-service", phase: "Phase 2", status: "online" },
        { name: "purchasing-service", phase: "Phase 3", status: "online" },
        { name: "finance-crm-service", phase: "Phase 4", status: "online" },
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

  // Inventory & BOM Repackaging routes
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

  gateway.post<{ Body: ExecuteBomRepackagingInput }>(
    "/api/v1/inventory/bom/repackage",
    async (req, reply) => {
      const upstream = await inventory.app.inject({
        method: "POST",
        url: "/v1/bom/repackage",
        payload: req.body
      });
      return reply.code(upstream.statusCode).send(upstream.json());
    }
  );

  // POS routes
  gateway.post<{
    Body: {
      organizationId: string;
      branchId: string;
      registerId: string;
      cashierUserId: string;
      currencyCode: string;
      openingFloatMinor: number;
    };
  }>("/api/v1/pos/shifts/open", async (req, reply) => {
    const upstream = await pos.app.inject({
      method: "POST",
      url: "/v1/shifts/open",
      payload: req.body
    });
    return reply.code(upstream.statusCode).send(upstream.json());
  });

  gateway.post<{ Body: CompletePosSaleInput }>("/api/v1/pos/sales", async (req, reply) => {
    const upstream = await pos.app.inject({
      method: "POST",
      url: "/v1/sales",
      payload: req.body
    });
    return reply.code(upstream.statusCode).send(upstream.json());
  });

  gateway.post<{ Body: ProcessPosReturnInput }>("/api/v1/pos/returns", async (req, reply) => {
    const upstream = await pos.app.inject({
      method: "POST",
      url: "/v1/returns",
      payload: req.body
    });
    return reply.code(upstream.statusCode).send(upstream.json());
  });

  // Purchasing routes
  gateway.post<{
    Body: {
      organizationId: string;
      name: string;
      paymentTerms?: PaymentTermsCode;
    };
  }>("/api/v1/purchasing/suppliers", async (req, reply) => {
    const upstream = await purchasing.app.inject({
      method: "POST",
      url: "/v1/suppliers",
      payload: req.body
    });
    return reply.code(upstream.statusCode).send(upstream.json());
  });

  gateway.post<{
    Body: {
      organizationId: string;
      branchId: string;
      warehouseId: string;
      supplierId: string;
      currencyCode: string;
      lines: readonly PurchaseOrderLineInput[];
    };
  }>("/api/v1/purchasing/purchase-orders", async (req, reply) => {
    const upstream = await purchasing.app.inject({
      method: "POST",
      url: "/v1/purchase-orders",
      payload: req.body
    });
    return reply.code(upstream.statusCode).send(upstream.json());
  });

  gateway.post<{ Body: ReceivePurchaseOrderInput }>(
    "/api/v1/purchasing/purchase-orders/receive",
    async (req, reply) => {
      const upstream = await purchasing.app.inject({
        method: "POST",
        url: "/v1/purchase-orders/receive",
        payload: req.body
      });
      return reply.code(upstream.statusCode).send(upstream.json());
    }
  );

  // Finance & CRM routes
  gateway.post<{
    Body: {
      organizationId: string;
      name: string;
      phone?: string;
      creditLimitMinor?: number;
    };
  }>("/api/v1/finance-crm/customers", async (req, reply) => {
    const upstream = await financeCrm.app.inject({
      method: "POST",
      url: "/v1/customers",
      payload: req.body
    });
    return reply.code(upstream.statusCode).send(upstream.json());
  });

  gateway.post<{
    Body: {
      organizationId: string;
      customerId: string;
      amountMinor: number;
      referenceId: string;
      actorUserId: string;
    };
  }>("/api/v1/finance-crm/customers/credit/charge", async (req, reply) => {
    const upstream = await financeCrm.app.inject({
      method: "POST",
      url: "/v1/customers/credit/charge",
      payload: req.body
    });
    return reply.code(upstream.statusCode).send(upstream.json());
  });

  gateway.post<{
    Body: {
      organizationId: string;
      branchId: string;
      providerCode: string;
      providerName: string;
      category: FloatServiceCategory;
      currencyCode: string;
      initialBalanceMinor?: number;
      defaultServiceFeeMinor?: number;
      commissionBps?: number;
    };
  }>("/api/v1/finance-crm/float-accounts", async (req, reply) => {
    const upstream = await financeCrm.app.inject({
      method: "POST",
      url: "/v1/float-accounts",
      payload: req.body
    });
    return reply.code(upstream.statusCode).send(upstream.json());
  });

  gateway.post<{ Body: ExecuteFloatTransactionInput }>(
    "/api/v1/finance-crm/float-accounts/transactions",
    async (req, reply) => {
      const upstream = await financeCrm.app.inject({
        method: "POST",
        url: "/v1/float-accounts/transactions",
        payload: req.body
      });
      return reply.code(upstream.statusCode).send(upstream.json());
    }
  );

  return { gateway, eventBus, identity, catalog, inventory, pos, purchasing, financeCrm };
}
