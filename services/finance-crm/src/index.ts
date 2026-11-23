import { type EventBus, InMemoryEventBus } from "@storepoint/service-kit";
import Fastify, { type FastifyInstance } from "fastify";
import { AccountingGlDomainService } from "./domain/accounting-gl-service.js";
import { FinanceCrmDomainService } from "./domain/finance-crm-service.js";
import {
  type FinanceCrmRepository,
  InMemoryFinanceCrmRepository
} from "./repositories/finance-crm-repository.js";
import { registerFinanceCrmRoutes } from "./routes/finance-crm-routes.js";

export interface BuildFinanceCrmServerOptions {
  readonly repo?: FinanceCrmRepository;
  readonly eventBus?: EventBus;
}

export function buildFinanceCrmService(options: BuildFinanceCrmServerOptions = {}): {
  app: FastifyInstance;
  domain: FinanceCrmDomainService;
  accounting: AccountingGlDomainService;
  repo: FinanceCrmRepository;
  eventBus: EventBus;
} {
  const repo = options.repo ?? new InMemoryFinanceCrmRepository();
  const eventBus = options.eventBus ?? new InMemoryEventBus();
  const domain = new FinanceCrmDomainService(repo, eventBus);
  const accounting = new AccountingGlDomainService();
  const app = Fastify({ logger: false });

  registerFinanceCrmRoutes(app, domain, accounting);
  return { app, domain, accounting, repo, eventBus };
}

export * from "./domain/accounting-gl-service.js";
export * from "./domain/finance-crm-service.js";
export * from "./repositories/finance-crm-repository.js";
export * from "./routes/finance-crm-routes.js";
