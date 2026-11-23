import { type EventBus, InMemoryEventBus } from "@storepoint/service-kit";
import Fastify, { type FastifyInstance } from "fastify";
import { PosDomainService } from "./domain/pos-service.js";
import { InMemoryPosRepository, type PosRepository } from "./repositories/pos-repository.js";
import { registerPosRoutes } from "./routes/pos-routes.js";

export interface BuildPosServerOptions {
  readonly repo?: PosRepository;
  readonly eventBus?: EventBus;
}

export function buildPosService(options: BuildPosServerOptions = {}): {
  app: FastifyInstance;
  domain: PosDomainService;
  repo: PosRepository;
  eventBus: EventBus;
} {
  const repo = options.repo ?? new InMemoryPosRepository();
  const eventBus = options.eventBus ?? new InMemoryEventBus();
  const domain = new PosDomainService(repo, eventBus);
  const app = Fastify({ logger: false });

  registerPosRoutes(app, domain);
  return { app, domain, repo, eventBus };
}

export * from "./domain/pos-service.js";
export * from "./repositories/pos-repository.js";
export * from "./routes/pos-routes.js";
