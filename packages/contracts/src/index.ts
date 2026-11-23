export type DomainEventType =
  | "identity.organization.created"
  | "identity.branch.created"
  | "identity.register.created"
  | "identity.user.created"
  | "identity.user.authenticated"
  | "catalog.product.upserted"
  | "inventory.stock.adjusted"
  | "pos.sale.completed"
  | "pos.sale.returned"
  | "purchasing.po.received"
  | "finance.ar.updated";

export interface DomainEvent<TPayload = Readonly<Record<string, unknown>>> {
  readonly eventId: string;
  readonly eventType: DomainEventType;
  readonly organizationId: string;
  readonly branchId: string | null;
  readonly occurredAt: string;
  readonly correlationId: string;
  readonly payload: TPayload;
}

export interface PaginationMeta {
  readonly total: number;
  readonly page: number;
  readonly limit: number;
}

export interface ApiErrorPayload {
  readonly code: string;
  readonly message: string;
  readonly details?: Readonly<Record<string, unknown>>;
}

export interface ApiResponse<TData> {
  readonly success: boolean;
  readonly data: TData | null;
  readonly error: ApiErrorPayload | null;
  readonly meta?: PaginationMeta;
}

export interface ServiceHealthDto {
  readonly service: string;
  readonly status: "healthy" | "degraded" | "down";
  readonly version: string;
  readonly timestamp: string;
}

export * from "./identity.js";
export * from "./catalog.js";
export * from "./inventory.js";
export * from "./pos.js";


