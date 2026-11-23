import { type EventBus, InMemoryEventBus } from "@storepoint/service-kit";
import Fastify, { type FastifyInstance } from "fastify";
import { CatalogDomainService } from "./domain/catalog-service.js";
import {
  type CatalogRepository,
  InMemoryCatalogRepository
} from "./repositories/catalog-repository.js";
import { registerCatalogRoutes } from "./routes/catalog-routes.js";

export interface BuildCatalogServerOptions {
  readonly repo?: CatalogRepository;
  readonly eventBus?: EventBus;
}

export function buildCatalogService(options: BuildCatalogServerOptions = {}): {
  app: FastifyInstance;
  domain: CatalogDomainService;
  repo: CatalogRepository;
  eventBus: EventBus;
} {
  const repo = options.repo ?? new InMemoryCatalogRepository();
  const eventBus = options.eventBus ?? new InMemoryEventBus();
  const domain = new CatalogDomainService(repo, eventBus);
  const app = Fastify({ logger: false });

  registerCatalogRoutes(app, domain);
  return { app, domain, repo, eventBus };
}

export * from "./domain/catalog-service.js";
export * from "./repositories/catalog-repository.js";
export * from "./routes/catalog-routes.js";
