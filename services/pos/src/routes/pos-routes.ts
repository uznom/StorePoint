import type {
  CompletePosSaleInput,
  DenominationCountItem,
  ProcessPosReturnInput,
  ServiceHealthDto
} from "@storepoint/contracts";
import { fail, ok } from "@storepoint/service-kit";
import type { FastifyInstance } from "fastify";
import { PosDomainError, type PosDomainService } from "../domain/pos-service.js";

export function registerPosRoutes(app: FastifyInstance, domain: PosDomainService): void {
  app.get("/health", async () => {
    const health: ServiceHealthDto = {
      service: "pos-service",
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
      registerId: string;
      cashierUserId: string;
      currencyCode: string;
      openingFloatMinor: number;
    };
  }>("/v1/shifts/open", async (req, reply) => {
    try {
      const shift = await domain.openShift(req.body);
      return reply.code(201).send(ok(shift));
    } catch (err) {
      return handlePosError(err, reply);
    }
  });

  app.post<{
    Body: {
      organizationId: string;
      shiftId: string;
      cashierUserId: string;
      type: "CASH_IN" | "CASH_OUT";
      amountMinor: number;
      reason: string;
    };
  }>("/v1/shifts/drawer-movements", async (req, reply) => {
    try {
      const res = await domain.recordDrawerMovement(req.body);
      return reply.code(201).send(ok(res));
    } catch (err) {
      return handlePosError(err, reply);
    }
  });

  app.post<{
    Body: {
      organizationId: string;
      shiftId: string;
      denominations?: readonly DenominationCountItem[];
      countedClosingCashMinor?: number;
      notes?: string;
    };
  }>("/v1/shifts/close-blind", async (req, reply) => {
    try {
      const shift = await domain.closeShiftBlind(req.body);
      return reply.code(200).send(ok(shift));
    } catch (err) {
      return handlePosError(err, reply);
    }
  });

  app.post<{ Body: CompletePosSaleInput }>("/v1/sales", async (req, reply) => {
    try {
      const sale = await domain.completeSale(req.body);
      return reply.code(201).send(ok(sale));
    } catch (err) {
      return handlePosError(err, reply);
    }
  });

  app.post<{ Body: { sales: readonly CompletePosSaleInput[] } }>(
    "/v1/sales/sync-batch",
    async (req, reply) => {
      try {
        const synced = await domain.syncOfflineSalesBatch(req.body.sales);
        return reply.code(200).send(ok(synced));
      } catch (err) {
        return handlePosError(err, reply);
      }
    }
  );

  app.post<{ Body: ProcessPosReturnInput }>("/v1/returns", async (req, reply) => {
    try {
      const posReturn = await domain.processReturn(req.body);
      return reply.code(201).send(ok(posReturn));
    } catch (err) {
      return handlePosError(err, reply);
    }
  });
}

function handlePosError(
  err: unknown,
  reply: { code: (s: number) => { send: (p: unknown) => unknown } }
) {
  if (err instanceof PosDomainError) {
    return reply.code(err.statusCode).send(fail(err.code, err.message));
  }
  const message = err instanceof Error ? err.message : "Unexpected POS service error";
  return reply.code(400).send(fail("ERR_POS_BAD_REQUEST", message));
}
