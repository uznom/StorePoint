import type { EventBus } from "@storepoint/service-kit";
import type { InventoryDomainService } from "../domain/inventory-service.js";

export interface PosSaleCompletedEventPayload {
  readonly saleId: string;
  readonly warehouseId: string;
  readonly currencyCode: string;
  readonly cashierUserId: string;
  readonly items: readonly {
    readonly productId: string;
    readonly baseUnitsDeducted: number;
  }[];
}

export interface PosSaleReturnedEventPayload {
  readonly returnId: string;
  readonly originalSaleId: string;
  readonly warehouseId: string;
  readonly currencyCode: string;
  readonly cashierUserId: string;
  readonly lines: readonly {
    readonly productId: string;
    readonly baseUnitsReturned: number;
    readonly restockToInventory: boolean;
  }[];
}

/**
 * Decoupled EventBus consumers inside `inventory-service`.
 * Automatically deducts or restocks base UOM inventory when `pos-service` emits sale/return events.
 */
export function registerInventoryEventConsumers(
  eventBus: EventBus,
  domain: InventoryDomainService
): void {
  eventBus.subscribe<PosSaleCompletedEventPayload>("pos.sale.completed", async (event) => {
    for (const item of event.payload.items) {
      if (item.baseUnitsDeducted <= 0) continue;
      await domain.recordMovement({
        organizationId: event.organizationId,
        warehouseId: event.payload.warehouseId,
        productId: item.productId,
        currencyCode: event.payload.currencyCode,
        reason: "POS_SALE",
        deltaBaseUnits: -item.baseUnitsDeducted,
        referenceId: event.payload.saleId,
        actorUserId: event.payload.cashierUserId
      });
    }
  });

  eventBus.subscribe<PosSaleReturnedEventPayload>("pos.sale.returned", async (event) => {
    for (const line of event.payload.lines) {
      if (!line.restockToInventory || line.baseUnitsReturned <= 0) continue;
      await domain.recordMovement({
        organizationId: event.organizationId,
        warehouseId: event.payload.warehouseId,
        productId: line.productId,
        currencyCode: event.payload.currencyCode,
        reason: "POS_RETURN_RESTOCK",
        deltaBaseUnits: line.baseUnitsReturned,
        referenceId: event.payload.returnId,
        actorUserId: event.payload.cashierUserId
      });
    }
  });
}
