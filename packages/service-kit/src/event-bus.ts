import type {
  ApiResponse,
  DomainEvent,
  DomainEventType,
  PaginationMeta
} from "@storepoint/contracts";
import { randomUUID } from "node:crypto";

export function ok<TData>(data: TData, meta?: PaginationMeta): ApiResponse<TData> {
  return Object.freeze({
    success: true,
    data,
    error: null,
    ...(meta ? { meta } : {})
  });
}

export function fail<TData = never>(
  code: string,
  message: string,
  details?: Readonly<Record<string, unknown>>
): ApiResponse<TData> {
  return Object.freeze({
    success: false,
    data: null,
    error: Object.freeze({
      code,
      message,
      ...(details ? { details } : {})
    })
  });
}

export type DomainEventHandler<TPayload = Readonly<Record<string, unknown>>> = (
  event: DomainEvent<TPayload>
) => Promise<void> | void;

export interface EventBus {
  publish<TPayload>(event: DomainEvent<TPayload>): Promise<void>;
  subscribe<TPayload = Readonly<Record<string, unknown>>>(
    eventType: DomainEventType,
    handler: DomainEventHandler<TPayload>
  ): () => void;
}

/**
 * Idempotent event bus used across decoupled domain services.
 * Tracks processed eventIds to guarantee at-most-once side-effect execution.
 */
export class InMemoryEventBus implements EventBus {
  private readonly handlers = new Map<DomainEventType, Set<DomainEventHandler<any>>>();
  private readonly processedEventIds = new Set<string>();
  private readonly history: DomainEvent<any>[] = [];

  async publish<TPayload>(event: DomainEvent<TPayload>): Promise<void> {
    if (this.processedEventIds.has(event.eventId)) {
      return;
    }
    this.processedEventIds.add(event.eventId);
    this.history.push(event);
    const registered = this.handlers.get(event.eventType);
    if (!registered) return;
    for (const handler of registered) {
      await handler(event);
    }
  }

  subscribe<TPayload = Readonly<Record<string, unknown>>>(
    eventType: DomainEventType,
    handler: DomainEventHandler<TPayload>
  ): () => void {
    const existing = this.handlers.get(eventType) ?? new Set();
    existing.add(handler);
    this.handlers.set(eventType, existing);
    return () => {
      existing.delete(handler);
    };
  }

  getPublishedEvents(): readonly DomainEvent<any>[] {
    return [...this.history];
  }
}

export function createDomainEvent<TPayload>(params: {
  eventType: DomainEventType;
  organizationId: string;
  branchId?: string | null;
  correlationId?: string;
  payload: TPayload;
}): DomainEvent<TPayload> {
  return Object.freeze({
    eventId: `evt_${randomUUID()}`,
    eventType: params.eventType,
    organizationId: params.organizationId,
    branchId: params.branchId ?? null,
    occurredAt: new Date().toISOString(),
    correlationId: params.correlationId ?? `corr_${randomUUID()}`,
    payload: params.payload
  });
}
