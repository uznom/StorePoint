import type {
  OverheadCategory,
  OverheadScheduleDto,
  PaymentTermsCode,
  PurchaseOrderDto,
  PurchaseOrderLineDto,
  PurchaseOrderLineInput,
  ReceivePurchaseOrderInput,
  SupplierDto
} from "@storepoint/contracts";
import { type EventBus, createDomainEvent } from "@storepoint/service-kit";
import { randomUUID } from "node:crypto";
import type { PurchasingRepository } from "../repositories/purchasing-repository.js";

export class PurchasingDomainError extends Error {
  readonly code: string;
  readonly statusCode: number;

  constructor(code: string, message: string, statusCode = 400) {
    super(message);
    this.code = code;
    this.statusCode = statusCode;
  }
}

const NET_DAYS_BY_TERM: Readonly<Record<PaymentTermsCode, number>> = Object.freeze({
  COD: 0,
  NET_7: 7,
  NET_15: 15,
  NET_30: 30,
  NET_60: 60
});

export class PurchasingDomainService {
  private readonly repo: PurchasingRepository;
  private readonly eventBus: EventBus;

  constructor(repo: PurchasingRepository, eventBus: EventBus) {
    this.repo = repo;
    this.eventBus = eventBus;
  }

  async createSupplier(input: {
    organizationId: string;
    name: string;
    contactPerson?: string;
    phone?: string;
    email?: string;
    paymentTerms?: PaymentTermsCode;
    deliverySchedule?: string;
  }): Promise<SupplierDto> {
    if (!input.name.trim()) {
      throw new PurchasingDomainError("ERR_SUPPLIER_NAME", "Supplier name is required");
    }
    return this.repo.saveSupplier({
      id: `sup_${randomUUID()}`,
      organizationId: input.organizationId,
      name: input.name.trim(),
      contactPerson: input.contactPerson?.trim() ?? "",
      phone: input.phone?.trim() ?? "",
      email: input.email?.trim() ?? "",
      paymentTerms: input.paymentTerms ?? "COD",
      deliverySchedule: input.deliverySchedule?.trim() ?? "",
      createdAt: new Date().toISOString()
    });
  }

  async createPurchaseOrder(input: {
    organizationId: string;
    branchId: string;
    warehouseId: string;
    supplierId: string;
    currencyCode: string;
    status?: "DRAFT" | "ORDERED";
    expectedDeliveryDate?: string | null;
    lines: readonly PurchaseOrderLineInput[];
  }): Promise<PurchaseOrderDto> {
    if (input.lines.length === 0) {
      throw new PurchasingDomainError("ERR_EMPTY_PO", "Purchase order must have at least one line");
    }
    const supplier = await this.repo.getSupplierById(input.organizationId, input.supplierId);
    if (!supplier) {
      throw new PurchasingDomainError("ERR_SUPPLIER_NOT_FOUND", "Supplier not found", 404);
    }

    const builtLines: PurchaseOrderLineDto[] = input.lines.map((l) => {
      if (l.quantityOrdered <= 0 || l.conversionFactorToBase <= 0 || l.unitCostMinor < 0) {
        throw new PurchasingDomainError("ERR_INVALID_PO_LINE", "Invalid PO line quantity or cost");
      }
      return {
        ...l,
        id: `pol_${randomUUID()}`,
        quantityReceived: 0
      };
    });

    const subtotalCostMinor = builtLines.reduce(
      (sum, l) => sum + l.quantityOrdered * l.unitCostMinor,
      0
    );
    const poNumber = await this.repo.nextPoNumber(input.organizationId);

    return this.repo.savePurchaseOrder({
      id: `po_${randomUUID()}`,
      poNumber,
      organizationId: input.organizationId,
      branchId: input.branchId,
      warehouseId: input.warehouseId,
      supplierId: supplier.id,
      supplierName: supplier.name,
      currencyCode: input.currencyCode,
      status: input.status ?? "ORDERED",
      paymentStatus: "UNPAID",
      subtotalCostMinor,
      landedFreightCostMinor: 0,
      totalCostMinor: subtotalCostMinor,
      lines: builtLines,
      expectedDeliveryDate: input.expectedDeliveryDate ?? null,
      receivedAt: null,
      createdAt: new Date().toISOString()
    });
  }

  async receivePurchaseOrder(input: ReceivePurchaseOrderInput): Promise<PurchaseOrderDto> {
    const po = await this.repo.getPurchaseOrderById(input.organizationId, input.purchaseOrderId);
    if (!po) {
      throw new PurchasingDomainError("ERR_PO_NOT_FOUND", "Purchase order not found", 404);
    }
    if (po.status === "RECEIVED" || po.status === "CANCELLED") {
      throw new PurchasingDomainError(
        "ERR_PO_CLOSED",
        `Cannot receive items on a ${po.status} purchase order`,
        409
      );
    }

    const landedFreightMinor = Math.max(0, input.landedFreightCostMinor ?? 0);
    const totalBaseBeingReceived = input.lines.reduce((sum, reqLine) => {
      const poLine = po.lines.find((l) => l.productId === reqLine.productId);
      if (!poLine || reqLine.quantityReceivedNow <= 0) return sum;
      return sum + reqLine.quantityReceivedNow * poLine.conversionFactorToBase;
    }, 0);

    if (totalBaseBeingReceived <= 0) {
      throw new PurchasingDomainError(
        "ERR_NOTHING_RECEIVED",
        "Must receive a positive quantity on at least one PO line"
      );
    }

    const receivedEventLines: {
      productId: string;
      baseUnitsReceived: number;
      landedBaseUnitCostMinor: number;
      batchNumber: string | null;
      expirationDate: string | null;
    }[] = [];

    const updatedLines = po.lines.map((poLine) => {
      const reqLine = input.lines.find((r) => r.productId === poLine.productId);
      if (!reqLine || reqLine.quantityReceivedNow <= 0) return poLine;

      const baseUnitsReceived = reqLine.quantityReceivedNow * poLine.conversionFactorToBase;
      const rawBaseUnitCostMinor = Math.round(
        poLine.unitCostMinor / poLine.conversionFactorToBase
      );
      const freightPerBaseUnitMinor = Math.round(
        landedFreightMinor / totalBaseBeingReceived
      );

      receivedEventLines.push({
        productId: poLine.productId,
        baseUnitsReceived,
        landedBaseUnitCostMinor: rawBaseUnitCostMinor + freightPerBaseUnitMinor,
        batchNumber: reqLine.batchNumber ?? null,
        expirationDate: reqLine.expirationDate ?? null
      });

      return {
        ...poLine,
        quantityReceived: poLine.quantityReceived + reqLine.quantityReceivedNow
      };
    });

    const allFulfilled = updatedLines.every((l) => l.quantityReceived >= l.quantityOrdered);
    const nextLanded = po.landedFreightCostMinor + landedFreightMinor;
    const now = new Date().toISOString();

    const updatedPo = await this.repo.savePurchaseOrder({
      ...po,
      status: allFulfilled ? "RECEIVED" : "PARTIALLY_RECEIVED",
      landedFreightCostMinor: nextLanded,
      totalCostMinor: po.subtotalCostMinor + nextLanded,
      lines: updatedLines,
      receivedAt: now
    });

    // Auto-schedule Accounts Payable invoice when full receipt completes
    if (allFulfilled) {
      const supplier = await this.repo.getSupplierById(po.organizationId, po.supplierId);
      const netDays = supplier ? NET_DAYS_BY_TERM[supplier.paymentTerms] : 0;
      const dueDate = new Date(Date.now() + netDays * 86_400_000).toISOString().slice(0, 10);
      await this.createOverheadSchedule({
        organizationId: po.organizationId,
        title: `${po.poNumber} — ${po.supplierName}`,
        category: "SUPPLIER_INVOICE",
        amountMinor: updatedPo.totalCostMinor,
        currencyCode: po.currencyCode,
        dueDate,
        priority: "HIGH",
        referenceId: po.id
      });
    }

    await this.eventBus.publish(
      createDomainEvent({
        eventType: "purchasing.po.received",
        organizationId: po.organizationId,
        branchId: po.branchId,
        payload: {
          purchaseOrderId: po.id,
          poNumber: po.poNumber,
          warehouseId: po.warehouseId,
          currencyCode: po.currencyCode,
          actorUserId: input.actorUserId,
          lines: receivedEventLines
        }
      })
    );

    return updatedPo;
  }

  async createOverheadSchedule(input: {
    organizationId: string;
    title: string;
    category: OverheadCategory;
    amountMinor: number;
    currencyCode: string;
    dueDate: string;
    priority?: "HIGH" | "MEDIUM" | "LOW";
    referenceId?: string;
  }): Promise<OverheadScheduleDto> {
    if (!input.title.trim() || input.amountMinor <= 0) {
      throw new PurchasingDomainError("ERR_INVALID_OVERHEAD", "Overhead title and amount > 0 required");
    }
    return this.repo.saveOverhead({
      id: `ovh_${randomUUID()}`,
      organizationId: input.organizationId,
      title: input.title.trim(),
      category: input.category,
      amountMinor: input.amountMinor,
      currencyCode: input.currencyCode,
      dueDate: input.dueDate,
      priority: input.priority ?? "MEDIUM",
      referenceId: input.referenceId ?? "",
      isPaid: false,
      paidAt: null
    });
  }

  async markOverheadPaid(
    organizationId: string,
    overheadId: string
  ): Promise<OverheadScheduleDto> {
    const existing = await this.repo.getOverheadById(organizationId, overheadId);
    if (!existing) {
      throw new PurchasingDomainError("ERR_OVERHEAD_NOT_FOUND", "Overhead schedule not found", 404);
    }
    return this.repo.saveOverhead({
      ...existing,
      isPaid: true,
      paidAt: new Date().toISOString()
    });
  }

  async listOverheads(
    organizationId: string,
    unpaidOnly = false
  ): Promise<readonly OverheadScheduleDto[]> {
    return this.repo.listOverheads(organizationId, unpaidOnly);
  }
}
