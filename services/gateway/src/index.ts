import type {
  CreateOrganizationInput,
  CreateUserInput,
  PinAuthInput,
  ServiceHealthDto
} from "@storepoint/contracts";
import { buildIdentityService } from "@storepoint/identity-service";
import { ok } from "@storepoint/service-kit";
import Fastify, { type FastifyInstance } from "fastify";
import { randomUUID } from "node:crypto";

export interface GatewayTopologyStatus {
  readonly gateway: ServiceHealthDto;
  readonly upstreamServices: readonly {
    readonly name: string;
    readonly phase: string;
    readonly status: "online" | "planned";
  }[];
}

/**
 * Decoupled API Gateway & Auth Edge.
 * Injects correlation IDs, aggregates health across domain services,
 * and routes `/api/v1/identity/*` requests to `identity-service`.
 */
export function buildGatewayService(): {
  gateway: FastifyInstance;
  identity: ReturnType<typeof buildIdentityService>;
} {
  const identity = buildIdentityService();
  const gateway = Fastify({ logger: false });

  gateway.addHook("onRequest", async (req, reply) => {
    const incomingCorrId = req.headers["x-correlation-id"];
    const correlationId =
      typeof incomingCorrId === "string" && incomingCorrId.trim()
        ? incomingCorrId
        : `corr_${randomUUID()}`;
    reply.header("x-correlation-id", correlationId);
  });

  gateway.get("/health", async () => {
    const status: GatewayTopologyStatus = {
      gateway: {
        service: "gateway-service",
        status: "healthy",
        version: "2.0.0-alpha.0",
        timestamp: new Date().toISOString()
      },
      upstreamServices: [
        { name: "identity-service", phase: "Phase 0", status: "online" },
        { name: "catalog-service", phase: "Phase 1", status: "planned" },
        { name: "inventory-service", phase: "Phase 1", status: "planned" },
        { name: "pos-service", phase: "Phase 2", status: "planned" },
        { name: "purchasing-service", phase: "Phase 3", status: "planned" },
        { name: "finance-crm-service", phase: "Phase 4", status: "planned" },
        { name: "extensions-service", phase: "Phase 6", status: "planned" }
      ]
    };
    return ok(status);
  });

  gateway.post<{ Body: CreateOrganizationInput }>(
    "/api/v1/identity/organizations/onboard",
    async (req, reply) => {
      const upstream = await identity.app.inject({
        method: "POST",
        url: "/v1/organizations/onboard",
        payload: req.body
      });
      return reply.code(upstream.statusCode).send(upstream.json());
    }
  );

  gateway.post<{ Body: CreateUserInput }>("/api/v1/identity/users", async (req, reply) => {
    const upstream = await identity.app.inject({
      method: "POST",
      url: "/v1/users",
      payload: req.body
    });
    return reply.code(upstream.statusCode).send(upstream.json());
  });

  gateway.post<{ Body: PinAuthInput }>("/api/v1/identity/auth/pin", async (req, reply) => {
    const upstream = await identity.app.inject({
      method: "POST",
      url: "/v1/auth/pin",
      payload: req.body
    });
    return reply.code(upstream.statusCode).send(upstream.json());
  });

  return { gateway, identity };
}
