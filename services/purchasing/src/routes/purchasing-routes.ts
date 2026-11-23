import type {
  OverheadCategory,
  PaymentTermsCode,
  PurchaseOrderLineInput,
  ReceivePurchaseOrderInput,
  ServiceHealthDto
} from "@storepoint/contracts";
import { fail, ok } from "@storepoint/service-kit";
import type { FastifyInstance } from "fastify";
import {
  PurchasingDomainError,
  type PurchasingDomainService
} from "../domain/purchasing-service.js";

export function registerPurchasingRoutes(
  app: FastifyInstance,
  domain: PurchasingDomainService
): void {
  app.get("/health", async () => {
    const health: ServiceHealthDto = {
      service: "purchasing-service",
      status: "healthy",
      version: "2.0.0-alpha.0",
      timestamp: new Date().toISOString()
    };
    return ok(health);
  });

  app.post<{
    Body: {
      organizationId: string;
      name: string;
      contactPerson?: string;
      phone?: string;
      email?: string;
      paymentTerms?: PaymentTermsCode;
      deliverySchedule?: string;
    };
  }>("/v1/suppliers", async (req, reply) => {
    try {
      const sup = await domain.createSupplier(req.body);
      return reply.code(201).send(ok(sup));
    } catch (err) {
      return handlePurchasingError(err, reply);
    }
  });

  app.post<{
    Body: {
      organizationId: string;
      branchId: string;
      warehouseId: string;
      supplierId: string;
      currencyCode: string;
      status?: "DRAFT" | "ORDERED";
      expectedDeliveryDate?: string | null;
      lines: readonly PurchaseOrderLineInput[];
    };
  }>("/v1/purchase-orders", async (req, reply) => {
    try {
      const po = await domain.createPurchaseOrder(req.body);
      return reply.code(201).send(ok(po));
    } catch (err) {
      return handlePurchasingError(err, reply);
    }
  });

  app.post<{ Body: ReceivePurchaseOrderInput }>(
    "/v1/purchase-orders/receive",
    async (req, reply) => {
      try {
        const po = await domain.receivePurchaseOrder(req.body);
        return reply.code(200).send(ok(po));
      } catch (err) {
        return handlePurchasingError(err, reply);
      }
    }
  );

  app.post<{
    Body: {
      organizationId: string;
      title: string;
      category: OverheadCategory;
      amountMinor: number;
      currencyCode: string;
      dueDate: string;
      priority?: "HIGH" | "MEDIUM" | "LOW";
      referenceId?: string;
    };
  }>("/v1/overheads", async (req, reply) => {
    try {
      const ovh = await domain.createOverheadSchedule(req.body);
      return reply.code(201).send(ok(ovh));
    } catch (err) {
      return handlePurchasingError(err, reply);
    }
  });

  app.post<{ Params: { overheadId: string }; Body: { organizationId: string } }>(
    "/v1/overheads/:overheadId/pay",
    async (req, reply) => {
      try {
        const ovh = await domain.markOverheadPaid(
          req.body.organizationId,
          req.params.overheadId
        );
        return reply.code(200).send(ok(ovh));
      } catch (err) {
        return handlePurchasingError(err, reply);
      }
    }
  );

  app.get<{ Querystring: { organizationId: string; unpaidOnly?: string } }>(
    "/v1/overheads",
    async (req, reply) => {
      try {
        const list = await domain.listOverheads(
          req.query.organizationId,
          req.query.unpaidOnly === "true"
        );
        return reply.code(200).send(ok(list));
      } catch (err) {
        return handlePurchasingError(err, reply);
      }
    }
  );

  app.post<{
    Body: {
      organizationId: string;
      branchId: string;
      warehouseId: string;
      supplierId: string;
      currencyCode: string;
      lowStockItems: readonly {
        readonly productId: string;
        readonly productName: string;
        readonly uomCode: string;
        readonly quantityOnHandBase: number;
        readonly reorderPointBase: number;
        readonly lastUnitCostMinor: number;
      }[];
    };
  }>("/v1/purchase-orders/smart-reorder", async (req, reply) => {
    try {
      const draftPo = await domain.generateSmartReorderDraftPo(req.body);
      return reply.code(201).send(ok(draftPo));
    } catch (err) {
      return handlePurchasingError(err, reply);
    }
  });
}

function handlePurchasingError(
  err: unknown,
  reply: { code: (s: number) => { send: (p: unknown) => unknown } }
) {
  if (err instanceof PurchasingDomainError) {
    return reply.code(err.statusCode).send(fail(err.code, err.message));
  }
  const message = err instanceof Error ? err.message : "Unexpected purchasing service error";
  return reply.code(400).send(fail("ERR_PURCHASING_BAD_REQUEST", message));
}
