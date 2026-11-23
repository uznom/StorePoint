import type {
  CreateCategoryInput,
  CreateProductInput,
  CreateTaxGroupInput,
  ServiceHealthDto
} from "@storepoint/contracts";
import { fail, ok } from "@storepoint/service-kit";
import type { FastifyInstance } from "fastify";
import { CatalogDomainError, type CatalogDomainService } from "../domain/catalog-service.js";

export function registerCatalogRoutes(app: FastifyInstance, domain: CatalogDomainService): void {
  app.get("/health", async () => {
    const health: ServiceHealthDto = {
      service: "catalog-service",
      status: "healthy",
      version: "2.0.0-alpha.0",
      timestamp: new Date().toISOString()
    };
    return ok(health);
  });

  app.post<{ Body: CreateCategoryInput }>("/v1/categories", async (req, reply) => {
    try {
      const cat = await domain.createCategory(req.body);
      return reply.code(201).send(ok(cat));
    } catch (err) {
      return handleCatalogError(err, reply);
    }
  });

  app.get<{ Querystring: { organizationId: string } }>("/v1/categories", async (req, reply) => {
    try {
      const list = await domain.listCategories(req.query.organizationId);
      return reply.code(200).send(ok(list));
    } catch (err) {
      return handleCatalogError(err, reply);
    }
  });

  app.post<{ Body: CreateTaxGroupInput }>("/v1/tax-groups", async (req, reply) => {
    try {
      const tg = await domain.createTaxGroup(req.body);
      return reply.code(201).send(ok(tg));
    } catch (err) {
      return handleCatalogError(err, reply);
    }
  });

  app.post<{
    Body: { organizationId: string; countryCode: "PH" | "IN" | "US" | "EU" };
  }>("/v1/tax-groups/seed-preset", async (req, reply) => {
    try {
      const groups = await domain.seedCountryTaxPreset(
        req.body.organizationId,
        req.body.countryCode
      );
      return reply.code(201).send(ok(groups));
    } catch (err) {
      return handleCatalogError(err, reply);
    }
  });

  app.post<{ Body: CreateProductInput }>("/v1/products", async (req, reply) => {
    try {
      const product = await domain.createProduct(req.body);
      return reply.code(201).send(ok(product));
    } catch (err) {
      return handleCatalogError(err, reply);
    }
  });

  app.get<{ Querystring: { organizationId: string; q?: string } }>(
    "/v1/products",
    async (req, reply) => {
      try {
        const list = await domain.listProducts(req.query.organizationId, req.query.q);
        return reply.code(200).send(ok(list));
      } catch (err) {
        return handleCatalogError(err, reply);
      }
    }
  );

  app.get<{ Querystring: { organizationId: string; barcode: string } }>(
    "/v1/products/lookup",
    async (req, reply) => {
      try {
        const found = await domain.lookupBarcode(req.query.organizationId, req.query.barcode);
        return reply.code(200).send(ok(found));
      } catch (err) {
        return handleCatalogError(err, reply);
      }
    }
  );
}

function handleCatalogError(
  err: unknown,
  reply: { code: (s: number) => { send: (p: unknown) => unknown } }
) {
  if (err instanceof CatalogDomainError) {
    return reply.code(err.statusCode).send(fail(err.code, err.message));
  }
  const message = err instanceof Error ? err.message : "Unexpected catalog service error";
  return reply.code(400).send(fail("ERR_CATALOG_BAD_REQUEST", message));
}
