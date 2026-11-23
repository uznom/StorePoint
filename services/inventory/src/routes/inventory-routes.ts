import type {
  ExecuteStocktakeInput,
  RecordStockMovementInput,
  ServiceHealthDto,
  SetReorderPointInput
} from "@storepoint/contracts";
import { fail, ok } from "@storepoint/service-kit";
import type { FastifyInstance } from "fastify";
import { InventoryDomainError, type InventoryDomainService } from "../domain/inventory-service.js";

export function registerInventoryRoutes(
  app: FastifyInstance,
  domain: InventoryDomainService
): void {
  app.get("/health", async () => {
    const health: ServiceHealthDto = {
      service: "inventory-service",
      status: "healthy",
      version: "2.0.0-alpha.0",
      timestamp: new Date().toISOString()
    };
    return ok(health);
  });

  app.post<{
    Body: {
      organizationId: string;
      branchId: string;
      code: string;
      name: string;
      isDefault?: boolean;
    };
  }>("/v1/warehouses", async (req, reply) => {
    try {
      const wh = await domain.createWarehouse(req.body);
      return reply.code(201).send(ok(wh));
    } catch (err) {
      return handleInventoryError(err, reply);
    }
  });

  app.post<{ Body: RecordStockMovementInput }>("/v1/movements", async (req, reply) => {
    try {
      const res = await domain.recordMovement(req.body);
      return reply.code(201).send(ok(res));
    } catch (err) {
      return handleInventoryError(err, reply);
    }
  });

  app.get<{
    Querystring: { organizationId: string; productId?: string; warehouseId?: string };
  }>("/v1/movements", async (req, reply) => {
    try {
      const list = await domain.listMovements(
        req.query.organizationId,
        req.query.productId,
        req.query.warehouseId
      );
      return reply.code(200).send(ok(list));
    } catch (err) {
      return handleInventoryError(err, reply);
    }
  });

  app.put<{ Body: SetReorderPointInput }>("/v1/reorder-points", async (req, reply) => {
    try {
      const balance = await domain.setReorderPoint(req.body);
      return reply.code(200).send(ok(balance));
    } catch (err) {
      return handleInventoryError(err, reply);
    }
  });

  app.get<{ Querystring: { organizationId: string; warehouseId?: string } }>(
    "/v1/alerts/low-stock",
    async (req, reply) => {
      try {
        const alerts = await domain.listLowStockAlerts(
          req.query.organizationId,
          req.query.warehouseId
        );
        return reply.code(200).send(ok(alerts));
      } catch (err) {
        return handleInventoryError(err, reply);
      }
    }
  );

  app.get<{ Querystring: { organizationId: string; withinDays?: string } }>(
    "/v1/alerts/expiring",
    async (req, reply) => {
      try {
        const days = req.query.withinDays ? Number(req.query.withinDays) : 30;
        const expiring = await domain.listExpiringBatches(req.query.organizationId, days);
        return reply.code(200).send(ok(expiring));
      } catch (err) {
        return handleInventoryError(err, reply);
      }
    }
  );

  app.post<{ Body: ExecuteStocktakeInput }>("/v1/stocktakes", async (req, reply) => {
    try {
      const adjustments = await domain.executeStocktake(req.body);
      return reply.code(200).send(ok(adjustments));
    } catch (err) {
      return handleInventoryError(err, reply);
    }
  });
}

function handleInventoryError(
  err: unknown,
  reply: { code: (s: number) => { send: (p: unknown) => unknown } }
) {
  if (err instanceof InventoryDomainError) {
    return reply.code(err.statusCode).send(fail(err.code, err.message));
  }
  const message = err instanceof Error ? err.message : "Unexpected inventory service error";
  return reply.code(400).send(fail("ERR_INVENTORY_BAD_REQUEST", message));
}
