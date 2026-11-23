import { type EventBus, InMemoryEventBus } from "@storepoint/service-kit";
import Fastify, { type FastifyInstance } from "fastify";
import { IdentityDomainService } from "./domain/identity-service.js";
import {
  type IdentityRepository,
  InMemoryIdentityRepository
} from "./repositories/identity-repository.js";
import { registerIdentityRoutes } from "./routes/identity-routes.js";

export interface BuildIdentityServerOptions {
  readonly repo?: IdentityRepository;
  readonly eventBus?: EventBus;
}

export function buildIdentityService(options: BuildIdentityServerOptions = {}): {
  app: FastifyInstance;
  domain: IdentityDomainService;
  repo: IdentityRepository;
  eventBus: EventBus;
} {
  const repo = options.repo ?? new InMemoryIdentityRepository();
  const eventBus = options.eventBus ?? new InMemoryEventBus();
  const domain = new IdentityDomainService(repo, eventBus);
  const app = Fastify({ logger: false });

  registerIdentityRoutes(app, domain);
  return { app, domain, repo, eventBus };
}

export * from "./domain/identity-service.js";
export * from "./repositories/identity-repository.js";
export * from "./routes/identity-routes.js";
