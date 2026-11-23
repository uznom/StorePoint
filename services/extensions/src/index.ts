import Fastify, { type FastifyInstance } from "fastify";
import { ExtensionsDomainService } from "./domain/extensions-service.js";
import { registerExtensionsRoutes } from "./routes/extensions-routes.js";

export function buildExtensionsService(): {
  app: FastifyInstance;
  domain: ExtensionsDomainService;
} {
  const domain = new ExtensionsDomainService();
  const app = Fastify({ logger: false });

  registerExtensionsRoutes(app, domain);
  return { app, domain };
}

export * from "./domain/country-packs.js";
export * from "./domain/extensions-service.js";
export * from "./routes/extensions-routes.js";
