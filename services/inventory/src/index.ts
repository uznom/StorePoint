import { type EventBus, InMemoryEventBus } from "@storepoint/service-kit";
import Fastify, { type FastifyInstance } from "fastify";
import { InventoryDomainService } from "./domain/inventory-service.js";
import {
  InMemoryInventoryRepository,
  type InventoryRepository
} from "./repositories/inventory-repository.js";
import { registerInventoryRoutes } from "./routes/inventory-routes.js";

export interface BuildInventoryServerOptions {
  readonly repo?: InventoryRepository;
  readonly eventBus?: EventBus;
}

export function buildInventoryService(options: BuildInventoryServerOptions = {}): {
  app: FastifyInstance;
  domain: InventoryDomainService;
  repo: InventoryRepository;
  eventBus: EventBus;
} {
  const repo = options.repo ?? new InMemoryInventoryRepository();
  const eventBus = options.eventBus ?? new InMemoryEventBus();
  const domain = new InventoryDomainService(repo, eventBus);
  const app = Fastify({ logger: false });

  registerInventoryRoutes(app, domain);
  return { app, domain, repo, eventBus };
}

export * from "./domain/inventory-service.js";
export * from "./repositories/inventory-repository.js";
export * from "./routes/inventory-routes.js";
