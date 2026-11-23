import type {
  ExecuteFloatTransactionInput,
  FloatServiceCategory,
  GlJournalEntryDto,
  GlJournalLineDto,
  ReconcileFloatAccountInput,
  ServiceHealthDto
} from "@storepoint/contracts";
import { fail, ok } from "@storepoint/service-kit";
import type { FastifyInstance } from "fastify";
import type { AccountingGlDomainService } from "../domain/accounting-gl-service.js";
import {
  FinanceCrmDomainError,
  type FinanceCrmDomainService
} from "../domain/finance-crm-service.js";

export function registerFinanceCrmRoutes(
  app: FastifyInstance,
  domain: FinanceCrmDomainService,
  accounting: AccountingGlDomainService
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

  app.post<{
    Body: {
      organizationId: string;
      branchId?: string | null;
      currencyCode: string;
      referenceType: GlJournalEntryDto["referenceType"];
      referenceId: string;
      description: string;
      lines: readonly GlJournalLineDto[];
    };
  }>("/v1/accounting/journals", async (req, reply) => {
    try {
      const entry = await accounting.postJournalEntry(req.body);
      return reply.code(201).send(ok(entry));
    } catch (err) {
      return handleFinanceCrmError(err, reply);
    }
  });

  app.post<{
    Body: {
      organizationId: string;
      branchId?: string | null;
      currencyCode: string;
      saleId: string;
      netSalesMinor: number;
      taxMinor: number;
      cogsMinor: number;
      isCreditSale?: boolean;
    };
  }>("/v1/accounting/journals/sale", async (req, reply) => {
    try {
      const entry = await accounting.postSaleAndCogsJournal(req.body);
      return reply.code(201).send(ok(entry));
    } catch (err) {
      return handleFinanceCrmError(err, reply);
    }
  });

  app.get<{
    Querystring: { organizationId: string; currencyCode: string; branchId?: string };
  }>("/v1/accounting/reports/pnl", async (req, reply) => {
    try {
      const report = await accounting.generateProfitAndLossReport(
        req.query.organizationId,
        req.query.currencyCode,
        req.query.branchId
      );
      return reply.code(200).send(ok(report));
    } catch (err) {
      return handleFinanceCrmError(err, reply);
    }
  });
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
