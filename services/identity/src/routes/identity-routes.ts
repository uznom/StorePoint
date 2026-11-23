import type {
  CreateOrganizationInput,
  CreateUserInput,
  PinAuthInput,
  ServiceHealthDto
} from "@storepoint/contracts";
import { fail, ok } from "@storepoint/service-kit";
import type { FastifyInstance } from "fastify";
import { IdentityDomainError, type IdentityDomainService } from "../domain/identity-service.js";

export function registerIdentityRoutes(
  app: FastifyInstance,
  domain: IdentityDomainService
): void {
  app.get("/health", async () => {
    const health: ServiceHealthDto = {
      service: "identity-service",
      status: "healthy",
      version: "2.0.0-alpha.0",
      timestamp: new Date().toISOString()
    };
    return ok(health);
  });

  app.post<{ Body: CreateOrganizationInput }>("/v1/organizations/onboard", async (req, reply) => {
    try {
      const result = await domain.onboardOrganization(req.body);
      return reply.code(201).send(ok(result));
    } catch (err) {
      return handleIdentityError(err, reply);
    }
  });

  app.post<{ Body: CreateUserInput }>("/v1/users", async (req, reply) => {
    try {
      const user = await domain.createUser(req.body);
      return reply.code(201).send(ok(user));
    } catch (err) {
      return handleIdentityError(err, reply);
    }
  });

  app.post<{ Body: PinAuthInput }>("/v1/auth/pin", async (req, reply) => {
    try {
      const user = await domain.authenticateWithPin(req.body);
      return reply.code(200).send(ok(user));
    } catch (err) {
      return handleIdentityError(err, reply);
    }
  });

  app.get<{ Params: { organizationId: string } }>(
    "/v1/organizations/:organizationId/audit-logs",
    async (req, reply) => {
      try {
        const logs = await domain.listAuditLogs(req.params.organizationId);
        return reply.code(200).send(ok(logs));
      } catch (err) {
        return handleIdentityError(err, reply);
      }
    }
  );
}

function handleIdentityError(err: unknown, reply: { code: (s: number) => { send: (p: unknown) => unknown } }) {
  if (err instanceof IdentityDomainError) {
    return reply.code(err.statusCode).send(fail(err.code, err.message));
  }
  const message = err instanceof Error ? err.message : "Unexpected identity service error";
  return reply.code(400).send(fail("ERR_IDENTITY_BAD_REQUEST", message));
}
