import type {
  OverheadScheduleDto,
  PurchaseOrderDto,
  SupplierDto
} from "@storepoint/contracts";

export interface PurchasingRepository {
  saveSupplier(supplier: SupplierDto): Promise<SupplierDto>;
  getSupplierById(organizationId: string, supplierId: string): Promise<SupplierDto | null>;
  listSuppliers(organizationId: string): Promise<readonly SupplierDto[]>;
  savePurchaseOrder(po: PurchaseOrderDto): Promise<PurchaseOrderDto>;
  getPurchaseOrderById(
    organizationId: string,
    purchaseOrderId: string
  ): Promise<PurchaseOrderDto | null>;
  listPurchaseOrders(organizationId: string): Promise<readonly PurchaseOrderDto[]>;
  nextPoNumber(organizationId: string): Promise<string>;
  saveOverhead(schedule: OverheadScheduleDto): Promise<OverheadScheduleDto>;
  getOverheadById(
    organizationId: string,
    overheadId: string
  ): Promise<OverheadScheduleDto | null>;
  listOverheads(organizationId: string, unpaidOnly?: boolean): Promise<readonly OverheadScheduleDto[]>;
}

/**
 * Immutable repository for `purchasing-service` (`purchasing.*` PostgreSQL schema boundary).
 */
export class InMemoryPurchasingRepository implements PurchasingRepository {
  private readonly suppliers = new Map<string, SupplierDto>();
  private readonly purchaseOrders = new Map<string, PurchaseOrderDto>();
  private readonly overheads = new Map<string, OverheadScheduleDto>();
  private readonly poCounters = new Map<string, number>();

  async saveSupplier(supplier: SupplierDto): Promise<SupplierDto> {
    const frozen = Object.freeze({ ...supplier });
    this.suppliers.set(frozen.id, frozen);
    return frozen;
  }

  async getSupplierById(
    organizationId: string,
    supplierId: string
  ): Promise<SupplierDto | null> {
    const found = this.suppliers.get(supplierId);
    if (!found || found.organizationId !== organizationId) return null;
    return found;
  }

  async listSuppliers(organizationId: string): Promise<readonly SupplierDto[]> {
    return [...this.suppliers.values()].filter((s) => s.organizationId === organizationId);
  }

  async savePurchaseOrder(po: PurchaseOrderDto): Promise<PurchaseOrderDto> {
    const frozen = Object.freeze({
      ...po,
      lines: Object.freeze(po.lines.map((l) => Object.freeze({ ...l })))
    });
    this.purchaseOrders.set(frozen.id, frozen);
    return frozen;
  }

  async getPurchaseOrderById(
    organizationId: string,
    purchaseOrderId: string
  ): Promise<PurchaseOrderDto | null> {
    const found = this.purchaseOrders.get(purchaseOrderId);
    if (!found || found.organizationId !== organizationId) return null;
    return found;
  }

  async listPurchaseOrders(organizationId: string): Promise<readonly PurchaseOrderDto[]> {
    return [...this.purchaseOrders.values()].filter((p) => p.organizationId === organizationId);
  }

  async nextPoNumber(organizationId: string): Promise<string> {
    const next = (this.poCounters.get(organizationId) ?? 0) + 1;
    this.poCounters.set(organizationId, next);
    return `PO-${String(next).padStart(5, "0")}`;
  }

  async saveOverhead(schedule: OverheadScheduleDto): Promise<OverheadScheduleDto> {
    const frozen = Object.freeze({ ...schedule });
    this.overheads.set(frozen.id, frozen);
    return frozen;
  }

  async getOverheadById(
    organizationId: string,
    overheadId: string
  ): Promise<OverheadScheduleDto | null> {
    const found = this.overheads.get(overheadId);
    if (!found || found.organizationId !== organizationId) return null;
    return found;
  }

  async listOverheads(
    organizationId: string,
    unpaidOnly = false
  ): Promise<readonly OverheadScheduleDto[]> {
    return [...this.overheads.values()]
      .filter((o) => o.organizationId === organizationId && (!unpaidOnly || !o.isPaid))
      .sort((a, b) => a.dueDate.localeCompare(b.dueDate));
  }
}
