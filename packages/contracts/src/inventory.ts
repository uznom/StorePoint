export type StockMovementReason =
  | "INITIAL_STOCK"
  | "PO_RECEIPT"
  | "POS_SALE"
  | "POS_RETURN_RESTOCK"
  | "MANUAL_ADJUSTMENT"
  | "STOCKTAKE_RECONCILIATION"
  | "TRANSFER_OUT"
  | "TRANSFER_IN"
  | "BOM_CONSUME"
  | "BOM_PRODUCE"
  | "SHRINKAGE_EXPIRED";

export interface WarehouseDto {
  readonly id: string;
  readonly organizationId: string;
  readonly branchId: string;
  readonly code: string;
  readonly name: string;
  readonly isDefault: boolean;
  readonly createdAt: string;
}

/**
 * Immutable Stock Movement Ledger entry.
 * Stock levels are never overwritten blindly; every mutation is an auditable delta.
 */
export interface StockMovementDto {
  readonly id: string;
  readonly organizationId: string;
  readonly warehouseId: string;
  readonly productId: string;
  readonly reason: StockMovementReason;
  /** Signed change in base UOM units (positive adds stock, negative deducts stock) */
  readonly deltaBaseUnits: number;
  readonly balanceAfterBaseUnits: number;
  /** Unit cost in minor units at the time of movement */
  readonly unitCostMinor: number;
  /** Resulting Weighted Average Cost (WAC) in minor units after this movement */
  readonly wacAfterMinor: number;
  readonly batchNumber: string | null;
  readonly expirationDate: string | null;
  readonly referenceId: string;
  readonly actorUserId: string;
  readonly notes: string;
  readonly occurredAt: string;
}

export interface StockBalanceDto {
  readonly organizationId: string;
  readonly warehouseId: string;
  readonly productId: string;
  readonly quantityOnHandBase: number;
  readonly reorderPointBase: number;
  readonly wacUnitCostMinor: number;
  readonly totalValuationMinor: number;
  readonly updatedAt: string;
}

export interface BatchLotBalanceDto {
  readonly organizationId: string;
  readonly warehouseId: string;
  readonly productId: string;
  readonly batchNumber: string;
  readonly expirationDate: string | null;
  readonly quantityOnHandBase: number;
}

export interface RecordStockMovementInput {
  readonly organizationId: string;
  readonly warehouseId: string;
  readonly productId: string;
  readonly currencyCode: string;
  readonly reason: StockMovementReason;
  readonly deltaBaseUnits: number;
  readonly unitCostMinor?: number;
  readonly batchNumber?: string | null;
  readonly expirationDate?: string | null;
  readonly referenceId?: string;
  readonly actorUserId: string;
  readonly notes?: string;
}

export interface SetReorderPointInput {
  readonly organizationId: string;
  readonly warehouseId: string;
  readonly productId: string;
  readonly reorderPointBase: number;
}

export interface StocktakeCountLineInput {
  readonly productId: string;
  readonly countedBaseUnits: number;
}

export interface ExecuteStocktakeInput {
  readonly organizationId: string;
  readonly warehouseId: string;
  readonly currencyCode: string;
  readonly actorUserId: string;
  readonly notes?: string;
  readonly lines: readonly StocktakeCountLineInput[];
}
