import type {
  DomainEventType,
  LegacyV1BackupPayload,
  ServiceHealthDto
} from "@storepoint/contracts";
import { fail, ok } from "@storepoint/service-kit";
import type { FastifyInstance } from "fastify";
import {
  ExtensionsDomainError,
  type ExtensionsDomainService
} from "../domain/extensions-service.js";

export function registerExtensionsRoutes(
  app: FastifyInstance,
  domain: ExtensionsDomainService
): void {
  app.get("/health", async () => {
    const health: ServiceHealthDto = {
      service: "extensions-service",
      status: "healthy",
      version: "2.0.0-alpha.0",
      timestamp: new Date().toISOString()
    };
    return ok(health);
  });

  app.get("/v1/country-packs", async () => {
    return ok(domain.listCountryPacks());
  });

  app.post<{ Body: { organizationId: string; packId: string } }>(
    "/v1/country-packs/activate",
    async (req, reply) => {
      try {
        const pack = domain.activateCountryPack(req.body.organizationId, req.body.packId);
        return reply.code(200).send(ok(pack));
      } catch (err) {
        return handleExtensionsError(err, reply);
      }
    }
  );

  app.post<{
    Body: {
      organizationId: string;
      targetUrl: string;
      eventTypes: readonly DomainEventType[];
    };
  }>("/v1/webhooks", async (req, reply) => {
    try {
      const sub = domain.registerWebhook(req.body);
      return reply.code(201).send(ok(sub));
    } catch (err) {
      return handleExtensionsError(err, reply);
    }
  });

  app.post<{
    Body: {
      organizationId: string;
      currencyCode?: string;
      backup: LegacyV1BackupPayload;
    };
  }>("/v1/migrations/v1-import", async (req, reply) => {
    try {
      const plan = domain.buildLegacyV1MigrationPlan(
        req.body.organizationId,
        req.body.backup,
        req.body.currencyCode ?? "PHP"
      );
      return reply.code(200).send(ok(plan));
    } catch (err) {
      return handleExtensionsError(err, reply);
    }
  });
}

function handleExtensionsError(
  err: unknown,
  reply: { code: (s: number) => { send: (p: unknown) => unknown } }
) {
  if (err instanceof ExtensionsDomainError) {
    return reply.code(err.statusCode).send(fail(err.code, err.message));
  }
  const message = err instanceof Error ? err.message : "Unexpected extensions service error";
  return reply.code(400).send(fail("ERR_EXTENSIONS_BAD_REQUEST", message));
}
