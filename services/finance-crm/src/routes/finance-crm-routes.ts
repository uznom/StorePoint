import type {
  ExecuteFloatTransactionInput,
  FloatServiceCategory,
  ReconcileFloatAccountInput,
  ServiceHealthDto
} from "@storepoint/contracts";
import { fail, ok } from "@storepoint/service-kit";
import type { FastifyInstance } from "fastify";
import {
  FinanceCrmDomainError,
  type FinanceCrmDomainService
} from "../domain/finance-crm-service.js";

export function registerFinanceCrmRoutes(
  app: FastifyInstance,
  domain: FinanceCrmDomainService
): void {
  app.get("/health", async () => {
    const health: ServiceHealthDto = {
      service: "finance-crm-service",
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
      phone?: string;
      email?: string;
      creditLimitMinor?: number;
    };
  }>("/v1/customers", async (req, reply) => {
    try {
      const cus = await domain.createCustomer(req.body);
      return reply.code(201).send(ok(cus));
    } catch (err) {
      return handleFinanceCrmError(err, reply);
    }
  });

  app.post<{
    Body: {
      organizationId: string;
      customerId: string;
      amountMinor: number;
      referenceId: string;
      actorUserId: string;
      notes?: string;
    };
  }>("/v1/customers/credit/charge", async (req, reply) => {
    try {
      const res = await domain.chargeCustomerCredit(req.body);
      return reply.code(201).send(ok(res));
    } catch (err) {
      return handleFinanceCrmError(err, reply);
    }
  });

  app.post<{
    Body: {
      organizationId: string;
      customerId: string;
      amountMinor: number;
      referenceId?: string;
      actorUserId: string;
      notes?: string;
    };
  }>("/v1/customers/credit/pay", async (req, reply) => {
    try {
      const res = await domain.recordCustomerPayment(req.body);
      return reply.code(200).send(ok(res));
    } catch (err) {
      return handleFinanceCrmError(err, reply);
    }
  });

  app.post<{
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
  }>("/v1/float-accounts", async (req, reply) => {
    try {
      const flt = await domain.createFloatAccount(req.body);
      return reply.code(201).send(ok(flt));
    } catch (err) {
      return handleFinanceCrmError(err, reply);
    }
  });

  app.post<{ Body: ExecuteFloatTransactionInput }>(
    "/v1/float-accounts/transactions",
    async (req, reply) => {
      try {
        const res = await domain.executeFloatTransaction(req.body);
        return reply.code(201).send(ok(res));
      } catch (err) {
        return handleFinanceCrmError(err, reply);
      }
    }
  );

  app.post<{ Body: ReconcileFloatAccountInput }>(
    "/v1/float-accounts/reconcile",
    async (req, reply) => {
      try {
        const res = await domain.reconcileFloatAccount(req.body);
        return reply.code(200).send(ok(res));
      } catch (err) {
        return handleFinanceCrmError(err, reply);
      }
    }
  );
}

function handleFinanceCrmError(
  err: unknown,
  reply: { code: (s: number) => { send: (p: unknown) => unknown } }
) {
  if (err instanceof FinanceCrmDomainError) {
    return reply.code(err.statusCode).send(fail(err.code, err.message));
  }
  const message = err instanceof Error ? err.message : "Unexpected finance-crm service error";
  return reply.code(400).send(fail("ERR_FINANCE_CRM_BAD_REQUEST", message));
}
