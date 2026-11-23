import { type EventBus, InMemoryEventBus } from "@storepoint/service-kit";
import Fastify, { type FastifyInstance } from "fastify";
import { PurchasingDomainService } from "./domain/purchasing-service.js";
import {
  InMemoryPurchasingRepository,
  type PurchasingRepository
} from "./repositories/purchasing-repository.js";
import { registerPurchasingRoutes } from "./routes/purchasing-routes.js";

export interface BuildPurchasingServerOptions {
  readonly repo?: PurchasingRepository;
  readonly eventBus?: EventBus;
}

export function buildPurchasingService(options: BuildPurchasingServerOptions = {}): {
  app: FastifyInstance;
  domain: PurchasingDomainService;
  repo: PurchasingRepository;
  eventBus: EventBus;
} {
  const repo = options.repo ?? new InMemoryPurchasingRepository();
  const eventBus = options.eventBus ?? new InMemoryEventBus();
  const domain = new PurchasingDomainService(repo, eventBus);
  const app = Fastify({ logger: false });

  registerPurchasingRoutes(app, domain);
  return { app, domain, repo, eventBus };
}

export * from "./domain/purchasing-service.js";
export * from "./repositories/purchasing-repository.js";
export * from "./routes/purchasing-routes.js";
