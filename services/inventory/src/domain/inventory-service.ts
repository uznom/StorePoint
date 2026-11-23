import type {
  BatchLotBalanceDto,
  ExecuteStocktakeInput,
  RecordStockMovementInput,
  SetReorderPointInput,
  StockBalanceDto,
  StockMovementDto,
  WarehouseDto
} from "@storepoint/contracts";
import { Money, calculateWeightedAverageCost } from "@storepoint/money";
import { type EventBus, createDomainEvent } from "@storepoint/service-kit";
import { randomUUID } from "node:crypto";
import type { InventoryRepository } from "../repositories/inventory-repository.js";

export class InventoryDomainError extends Error {
  readonly code: string;
  readonly statusCode: number;

  constructor(code: string, message: string, statusCode = 400) {
    super(message);
    this.code = code;
    this.statusCode = statusCode;
  }
}

export class InventoryDomainService {
  private readonly repo: InventoryRepository;
  private readonly eventBus: EventBus;

  constructor(repo: InventoryRepository, eventBus: EventBus) {
    this.repo = repo;
    this.eventBus = eventBus;
  }

  async createWarehouse(input: {
    organizationId: string;
    branchId: string;
    code: string;
    name: string;
    isDefault?: boolean;
  }): Promise<WarehouseDto> {
    if (!input.name.trim()) {
      throw new InventoryDomainError("ERR_WAREHOUSE_NAME", "Warehouse name is required");
    }
    return this.repo.saveWarehouse({
      id: `wh_${randomUUID()}`,
      organizationId: input.organizationId,
      branchId: input.branchId,
      code: input.code.trim().toUpperCase() || "MAIN-WH",
      name: input.name.trim(),
      isDefault: input.isDefault ?? true,
      createdAt: new Date().toISOString()
    });
  }

  async recordMovement(input: RecordStockMovementInput): Promise<{
    movement: StockMovementDto;
    balance: StockBalanceDto;
  }> {
    if (input.deltaBaseUnits === 0) {
      throw new InventoryDomainError(
        "ERR_ZERO_MOVEMENT",
        "Stock movement deltaBaseUnits cannot be zero"
      );
    }

    const existing = await this.repo.getBalance(
      input.organizationId,
      input.warehouseId,
      input.productId
    );
    const currentQty = existing?.quantityOnHandBase ?? 0;
    const currentWacMinor = existing?.wacUnitCostMinor ?? 0;
    const nextQty = currentQty + input.deltaBaseUnits;

    if (nextQty < 0) {
      throw new InventoryDomainError(
        "ERR_INSUFFICIENT_STOCK",
        `Insufficient stock for product ${input.productId}: on-hand ${currentQty}, requested ${Math.abs(input.deltaBaseUnits)}`,
        409
      );
    }

    const incomingCostMinor = input.unitCostMinor ?? currentWacMinor;
    let nextWacMinor = currentWacMinor;

    if (input.deltaBaseUnits > 0 && input.unitCostMinor !== undefined) {
      const wacMoney = calculateWeightedAverageCost(
        currentQty,
        Money.fromMinor(currentWacMinor, input.currencyCode),
        input.deltaBaseUnits,
        Money.fromMinor(incomingCostMinor, input.currencyCode)
      );
      nextWacMinor = wacMoney.amountMinor;
    }

    const now = new Date().toISOString();
    const balance = await this.repo.saveBalance({
      organizationId: input.organizationId,
      warehouseId: input.warehouseId,
      productId: input.productId,
      quantityOnHandBase: nextQty,
      reorderPointBase: existing?.reorderPointBase ?? 0,
      wacUnitCostMinor: nextWacMinor,
      totalValuationMinor: nextQty * nextWacMinor,
      updatedAt: now
    });

    const movement = await this.repo.appendMovement({
      id: `mov_${randomUUID()}`,
      organizationId: input.organizationId,
      warehouseId: input.warehouseId,
      productId: input.productId,
      reason: input.reason,
      deltaBaseUnits: input.deltaBaseUnits,
      balanceAfterBaseUnits: nextQty,
      unitCostMinor: incomingCostMinor,
      wacAfterMinor: nextWacMinor,
      batchNumber: input.batchNumber ?? null,
      expirationDate: input.expirationDate ?? null,
      referenceId: input.referenceId ?? "",
      actorUserId: input.actorUserId,
      notes: input.notes ?? "",
      occurredAt: now
    });

    if (input.batchNumber) {
      const allBatches = await this.repo.listBatchBalances(input.organizationId);
      const prevBatch = allBatches.find(
        (b) =>
          b.warehouseId === input.warehouseId &&
          b.productId === input.productId &&
          b.batchNumber === input.batchNumber
      );
      await this.repo.upsertBatchBalance({
        organizationId: input.organizationId,
        warehouseId: input.warehouseId,
        productId: input.productId,
        batchNumber: input.batchNumber,
        expirationDate: input.expirationDate ?? prevBatch?.expirationDate ?? null,
        quantityOnHandBase: Math.max(0, (prevBatch?.quantityOnHandBase ?? 0) + input.deltaBaseUnits)
      });
    }

    await this.eventBus.publish(
      createDomainEvent({
        eventType: "inventory.stock.adjusted",
        organizationId: input.organizationId,
        payload: {
          warehouseId: input.warehouseId,
          productId: input.productId,
          reason: input.reason,
          deltaBaseUnits: input.deltaBaseUnits,
          balanceAfterBaseUnits: nextQty,
          wacUnitCostMinor: nextWacMinor
        }
      })
    );

    return { movement, balance };
  }

  async setReorderPoint(input: SetReorderPointInput): Promise<StockBalanceDto> {
    const existing = await this.repo.getBalance(
      input.organizationId,
      input.warehouseId,
      input.productId
    );
    return this.repo.saveBalance({
      organizationId: input.organizationId,
      warehouseId: input.warehouseId,
      productId: input.productId,
      quantityOnHandBase: existing?.quantityOnHandBase ?? 0,
      reorderPointBase: Math.max(0, input.reorderPointBase),
      wacUnitCostMinor: existing?.wacUnitCostMinor ?? 0,
      totalValuationMinor: existing?.totalValuationMinor ?? 0,
      updatedAt: new Date().toISOString()
    });
  }

  async listLowStockAlerts(
    organizationId: string,
    warehouseId?: string
  ): Promise<readonly StockBalanceDto[]> {
    const balances = await this.repo.listBalances(organizationId, warehouseId);
    return balances.filter(
      (b) => b.reorderPointBase > 0 && b.quantityOnHandBase <= b.reorderPointBase
    );
  }

  async listExpiringBatches(
    organizationId: string,
    withinDays = 30,
    asOfDate = new Date()
  ): Promise<readonly BatchLotBalanceDto[]> {
    const batches = await this.repo.listBatchBalances(organizationId);
    const cutoffMs = asOfDate.getTime() + withinDays * 86_400_000;
    return batches
      .filter((b) => {
        if (!b.expirationDate) return false;
        const expMs = Date.parse(b.expirationDate);
        return Number.isFinite(expMs) && expMs <= cutoffMs;
      })
      .sort((a, b) => (a.expirationDate ?? "").localeCompare(b.expirationDate ?? ""));
  }

  async executeStocktake(
    input: ExecuteStocktakeInput
  ): Promise<readonly StockMovementDto[]> {
    const adjustments: StockMovementDto[] = [];
    for (const line of input.lines) {
      const current = await this.repo.getBalance(
        input.organizationId,
        input.warehouseId,
        line.productId
      );
      const systemQty = current?.quantityOnHandBase ?? 0;
      const variance = line.countedBaseUnits - systemQty;
      if (variance === 0) continue;

      const { movement } = await this.recordMovement({
        organizationId: input.organizationId,
        warehouseId: input.warehouseId,
        productId: line.productId,
        currencyCode: input.currencyCode,
        reason: "STOCKTAKE_RECONCILIATION",
        deltaBaseUnits: variance,
        actorUserId: input.actorUserId,
        notes: input.notes ?? `Physical count ${line.countedBaseUnits} vs system ${systemQty}`
      });
      adjustments.push(movement);
    }
    return adjustments;
  }

  async listMovements(
    organizationId: string,
    productId?: string,
    warehouseId?: string
  ): Promise<readonly StockMovementDto[]> {
    return this.repo.listMovements(organizationId, productId, warehouseId);
  }
}
