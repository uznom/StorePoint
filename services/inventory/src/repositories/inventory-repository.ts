import type {
  BatchLotBalanceDto,
  StockBalanceDto,
  StockMovementDto,
  WarehouseDto
} from "@storepoint/contracts";

export interface InventoryRepository {
  saveWarehouse(warehouse: WarehouseDto): Promise<WarehouseDto>;
  listWarehouses(organizationId: string): Promise<readonly WarehouseDto[]>;
  getBalance(
    organizationId: string,
    warehouseId: string,
    productId: string
  ): Promise<StockBalanceDto | null>;
  saveBalance(balance: StockBalanceDto): Promise<StockBalanceDto>;
  listBalances(organizationId: string, warehouseId?: string): Promise<readonly StockBalanceDto[]>;
  appendMovement(movement: StockMovementDto): Promise<StockMovementDto>;
  listMovements(
    organizationId: string,
    productId?: string,
    warehouseId?: string
  ): Promise<readonly StockMovementDto[]>;
  upsertBatchBalance(batch: BatchLotBalanceDto): Promise<BatchLotBalanceDto>;
  listBatchBalances(organizationId: string): Promise<readonly BatchLotBalanceDto[]>;
}

/**
 * Immutable repository for the `inventory` service (`inventory.*` PostgreSQL schema boundary).
 */
export class InMemoryInventoryRepository implements InventoryRepository {
  private readonly warehouses = new Map<string, WarehouseDto>();
  private readonly balances = new Map<string, StockBalanceDto>();
  private readonly movements: StockMovementDto[] = [];
  private readonly batches = new Map<string, BatchLotBalanceDto>();

  private balanceKey(orgId: string, warehouseId: string, productId: string): string {
    return `${orgId}:${warehouseId}:${productId}`;
  }

  private batchKey(orgId: string, warehouseId: string, productId: string, batchNo: string): string {
    return `${orgId}:${warehouseId}:${productId}:${batchNo}`;
  }

  async saveWarehouse(warehouse: WarehouseDto): Promise<WarehouseDto> {
    const frozen = Object.freeze({ ...warehouse });
    this.warehouses.set(frozen.id, frozen);
    return frozen;
  }

  async listWarehouses(organizationId: string): Promise<readonly WarehouseDto[]> {
    return [...this.warehouses.values()].filter((w) => w.organizationId === organizationId);
  }

  async getBalance(
    organizationId: string,
    warehouseId: string,
    productId: string
  ): Promise<StockBalanceDto | null> {
    return this.balances.get(this.balanceKey(organizationId, warehouseId, productId)) ?? null;
  }

  async saveBalance(balance: StockBalanceDto): Promise<StockBalanceDto> {
    const frozen = Object.freeze({ ...balance });
    this.balances.set(
      this.balanceKey(frozen.organizationId, frozen.warehouseId, frozen.productId),
      frozen
    );
    return frozen;
  }

  async listBalances(
    organizationId: string,
    warehouseId?: string
  ): Promise<readonly StockBalanceDto[]> {
    return [...this.balances.values()].filter(
      (b) =>
        b.organizationId === organizationId &&
        (!warehouseId || b.warehouseId === warehouseId)
    );
  }

  async appendMovement(movement: StockMovementDto): Promise<StockMovementDto> {
    const frozen = Object.freeze({ ...movement });
    this.movements.push(frozen);
    return frozen;
  }

  async listMovements(
    organizationId: string,
    productId?: string,
    warehouseId?: string
  ): Promise<readonly StockMovementDto[]> {
    return this.movements.filter(
      (m) =>
        m.organizationId === organizationId &&
        (!productId || m.productId === productId) &&
        (!warehouseId || m.warehouseId === warehouseId)
    );
  }

  async upsertBatchBalance(batch: BatchLotBalanceDto): Promise<BatchLotBalanceDto> {
    const frozen = Object.freeze({ ...batch });
    this.batches.set(
      this.batchKey(
        frozen.organizationId,
        frozen.warehouseId,
        frozen.productId,
        frozen.batchNumber
      ),
      frozen
    );
    return frozen;
  }

  async listBatchBalances(organizationId: string): Promise<readonly BatchLotBalanceDto[]> {
    return [...this.batches.values()].filter(
      (b) => b.organizationId === organizationId && b.quantityOnHandBase > 0
    );
  }
}
