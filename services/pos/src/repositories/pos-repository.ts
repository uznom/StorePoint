import type {
  CashierShiftDto,
  DrawerMovementDto,
  PosReturnDto,
  PosSaleDto
} from "@storepoint/contracts";

export interface PosRepository {
  saveShift(shift: CashierShiftDto): Promise<CashierShiftDto>;
  getShiftById(organizationId: string, shiftId: string): Promise<CashierShiftDto | null>;
  getActiveShiftForRegister(
    organizationId: string,
    registerId: string
  ): Promise<CashierShiftDto | null>;
  appendDrawerMovement(movement: DrawerMovementDto): Promise<DrawerMovementDto>;
  listDrawerMovements(shiftId: string): Promise<readonly DrawerMovementDto[]>;
  saveSale(sale: PosSaleDto): Promise<PosSaleDto>;
  getSaleById(organizationId: string, saleId: string): Promise<PosSaleDto | null>;
  getSaleByIdempotencyKey(
    organizationId: string,
    idempotencyKey: string
  ): Promise<PosSaleDto | null>;
  listSales(organizationId: string, shiftId?: string): Promise<readonly PosSaleDto[]>;
  saveReturn(posReturn: PosReturnDto): Promise<PosReturnDto>;
  listReturnsBySale(originalSaleId: string): Promise<readonly PosReturnDto[]>;
  nextReceiptNumber(organizationId: string): Promise<string>;
}

/**
 * Immutable repository for the `pos` service (`pos.*` PostgreSQL schema boundary).
 */
export class InMemoryPosRepository implements PosRepository {
  private readonly shifts = new Map<string, CashierShiftDto>();
  private readonly drawerMovements: DrawerMovementDto[] = [];
  private readonly sales = new Map<string, PosSaleDto>();
  private readonly returns: PosReturnDto[] = [];
  private readonly receiptCounters = new Map<string, number>();

  async saveShift(shift: CashierShiftDto): Promise<CashierShiftDto> {
    const frozen = Object.freeze({
      ...shift,
      denominations: Object.freeze(shift.denominations.map((d) => Object.freeze({ ...d })))
    });
    this.shifts.set(frozen.id, frozen);
    return frozen;
  }

  async getShiftById(organizationId: string, shiftId: string): Promise<CashierShiftDto | null> {
    const found = this.shifts.get(shiftId);
    if (!found || found.organizationId !== organizationId) return null;
    return found;
  }

  async getActiveShiftForRegister(
    organizationId: string,
    registerId: string
  ): Promise<CashierShiftDto | null> {
    for (const shift of this.shifts.values()) {
      if (
        shift.organizationId === organizationId &&
        shift.registerId === registerId &&
        shift.status === "OPEN"
      ) {
        return shift;
      }
    }
    return null;
  }

  async appendDrawerMovement(movement: DrawerMovementDto): Promise<DrawerMovementDto> {
    const frozen = Object.freeze({ ...movement });
    this.drawerMovements.push(frozen);
    return frozen;
  }

  async listDrawerMovements(shiftId: string): Promise<readonly DrawerMovementDto[]> {
    return this.drawerMovements.filter((m) => m.shiftId === shiftId);
  }

  async saveSale(sale: PosSaleDto): Promise<PosSaleDto> {
    const frozen = Object.freeze({
      ...sale,
      items: Object.freeze(sale.items.map((i) => Object.freeze({ ...i }))),
      payments: Object.freeze(sale.payments.map((p) => Object.freeze({ ...p })))
    });
    this.sales.set(frozen.id, frozen);
    return frozen;
  }

  async getSaleById(organizationId: string, saleId: string): Promise<PosSaleDto | null> {
    const found = this.sales.get(saleId);
    if (!found || found.organizationId !== organizationId) return null;
    return found;
  }

  async getSaleByIdempotencyKey(
    organizationId: string,
    idempotencyKey: string
  ): Promise<PosSaleDto | null> {
    for (const sale of this.sales.values()) {
      if (
        sale.organizationId === organizationId &&
        sale.idempotencyKey === idempotencyKey
      ) {
        return sale;
      }
    }
    return null;
  }

  async listSales(organizationId: string, shiftId?: string): Promise<readonly PosSaleDto[]> {
    return [...this.sales.values()].filter(
      (s) => s.organizationId === organizationId && (!shiftId || s.shiftId === shiftId)
    );
  }

  async saveReturn(posReturn: PosReturnDto): Promise<PosReturnDto> {
    const frozen = Object.freeze({
      ...posReturn,
      lines: Object.freeze(posReturn.lines.map((l) => Object.freeze({ ...l })))
    });
    this.returns.push(frozen);
    return frozen;
  }

  async listReturnsBySale(originalSaleId: string): Promise<readonly PosReturnDto[]> {
    return this.returns.filter((r) => r.originalSaleId === originalSaleId);
  }

  async nextReceiptNumber(organizationId: string): Promise<string> {
    const next = (this.receiptCounters.get(organizationId) ?? 0) + 1;
    this.receiptCounters.set(organizationId, next);
    return `OR-${String(next).padStart(6, "0")}`;
  }
}
