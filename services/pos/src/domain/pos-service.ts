import type {
  CashierShiftDto,
  CompletePosSaleInput,
  DenominationCountItem,
  DrawerMovementDto,
  PosReturnDto,
  PosSaleDto,
  ProcessPosReturnInput,
  SaleLineItemDto
} from "@storepoint/contracts";
import { Money, calculateTax } from "@storepoint/money";
import { type EventBus, createDomainEvent } from "@storepoint/service-kit";
import { randomUUID } from "node:crypto";
import type { PosRepository } from "../repositories/pos-repository.js";

export class PosDomainError extends Error {
  readonly code: string;
  readonly statusCode: number;

  constructor(code: string, message: string, statusCode = 400) {
    super(message);
    this.code = code;
    this.statusCode = statusCode;
  }
}

export class PosDomainService {
  private readonly repo: PosRepository;
  private readonly eventBus: EventBus;

  constructor(repo: PosRepository, eventBus: EventBus) {
    this.repo = repo;
    this.eventBus = eventBus;
  }

  async openShift(input: {
    organizationId: string;
    branchId: string;
    registerId: string;
    cashierUserId: string;
    currencyCode: string;
    openingFloatMinor: number;
  }): Promise<CashierShiftDto> {
    if (input.openingFloatMinor < 0) {
      throw new PosDomainError("ERR_NEGATIVE_FLOAT", "Opening float cannot be negative");
    }
    const existing = await this.repo.getActiveShiftForRegister(
      input.organizationId,
      input.registerId
    );
    if (existing) {
      throw new PosDomainError(
        "ERR_SHIFT_ALREADY_OPEN",
        `Register ${input.registerId} already has an open shift (${existing.id})`,
        409
      );
    }

    return this.repo.saveShift({
      id: `shf_${randomUUID()}`,
      organizationId: input.organizationId,
      branchId: input.branchId,
      registerId: input.registerId,
      cashierUserId: input.cashierUserId,
      currencyCode: input.currencyCode,
      status: "OPEN",
      openingFloatMinor: input.openingFloatMinor,
      expectedCashMinor: input.openingFloatMinor,
      countedClosingCashMinor: null,
      varianceMinor: null,
      denominations: [],
      openedAt: new Date().toISOString(),
      closedAt: null,
      notes: ""
    });
  }

  async recordDrawerMovement(input: {
    organizationId: string;
    shiftId: string;
    cashierUserId: string;
    type: "CASH_IN" | "CASH_OUT";
    amountMinor: number;
    reason: string;
  }): Promise<{ movement: DrawerMovementDto; shift: CashierShiftDto }> {
    if (input.amountMinor <= 0) {
      throw new PosDomainError("ERR_INVALID_DRAWER_AMOUNT", "Drawer amount must be positive");
    }
    const shift = await this.requireOpenShift(input.organizationId, input.shiftId);
    const signedDelta = input.type === "CASH_IN" ? input.amountMinor : -input.amountMinor;
    const nextExpected = shift.expectedCashMinor + signedDelta;
    if (nextExpected < 0) {
      throw new PosDomainError("ERR_DRAWER_INSUFFICIENT_CASH", "Cash drawer cannot go negative", 409);
    }

    const movement = await this.repo.appendDrawerMovement({
      id: `drw_${randomUUID()}`,
      organizationId: input.organizationId,
      shiftId: input.shiftId,
      cashierUserId: input.cashierUserId,
      type: input.type,
      amountMinor: input.amountMinor,
      reason: input.reason.trim() || input.type,
      occurredAt: new Date().toISOString()
    });

    const updatedShift = await this.repo.saveShift({
      ...shift,
      expectedCashMinor: nextExpected
    });
    return { movement, shift: updatedShift };
  }

  async closeShiftBlind(input: {
    organizationId: string;
    shiftId: string;
    denominations?: readonly DenominationCountItem[];
    countedClosingCashMinor?: number;
    notes?: string;
  }): Promise<CashierShiftDto> {
    const shift = await this.requireOpenShift(input.organizationId, input.shiftId);
    const denoms = input.denominations ?? [];
    const countedMinor =
      denoms.length > 0
        ? denoms.reduce((sum, d) => sum + d.denominationMinor * d.count, 0)
        : (input.countedClosingCashMinor ?? 0);

    const varianceMinor = countedMinor - shift.expectedCashMinor;
    return this.repo.saveShift({
      ...shift,
      status: "CLOSED",
      countedClosingCashMinor: countedMinor,
      varianceMinor,
      denominations: denoms,
      closedAt: new Date().toISOString(),
      notes: input.notes?.trim() ?? ""
    });
  }

  async completeSale(input: CompletePosSaleInput): Promise<PosSaleDto> {
    const existing = await this.repo.getSaleByIdempotencyKey(
      input.organizationId,
      input.idempotencyKey
    );
    if (existing) {
      return existing;
    }

    if (input.items.length === 0) {
      throw new PosDomainError("ERR_EMPTY_CART", "Cannot complete a sale with zero items");
    }

    const shift = await this.requireOpenShift(input.organizationId, input.shiftId);
    const builtItems: SaleLineItemDto[] = [];
    let subtotalMinor = 0;
    let totalNetMinor = 0;
    let totalTaxMinor = 0;

    for (const item of input.items) {
      if (item.quantity <= 0 || item.conversionFactorToBase <= 0) {
        throw new PosDomainError("ERR_INVALID_LINE_QTY", "Line quantity and UOM factor must be > 0");
      }
      const rawLineMinor = item.unitPriceMinor * item.quantity;
      const lineDiscMinor = Math.min(rawLineMinor, Math.max(0, item.lineDiscountMinor ?? 0));
      const discountedLineMinor = rawLineMinor - lineDiscMinor;

      const taxCalc = calculateTax(
        Money.fromMinor(discountedLineMinor, input.currencyCode),
        item.taxRules ?? []
      );

      subtotalMinor += rawLineMinor;
      totalNetMinor += taxCalc.netAmount.amountMinor;
      totalTaxMinor += taxCalc.taxAmount.amountMinor;

      builtItems.push({
        productId: item.productId,
        productName: item.productName,
        uomCode: item.uomCode,
        uomName: item.uomName,
        conversionFactorToBase: item.conversionFactorToBase,
        quantity: item.quantity,
        baseUnitsDeducted: item.quantity * item.conversionFactorToBase,
        unitPriceMinor: item.unitPriceMinor,
        lineDiscountMinor: lineDiscMinor,
        lineNetMinor: taxCalc.netAmount.amountMinor,
        lineTaxMinor: taxCalc.taxAmount.amountMinor,
        lineGrossMinor: taxCalc.grossAmount.amountMinor
      });
    }

    const cartDiscountMinor = Math.max(0, input.cartDiscountMinor ?? 0);
    const totalAmountMinor = Math.max(0, totalNetMinor + totalTaxMinor - cartDiscountMinor);
    const totalPaidMinor = input.payments.reduce((sum, p) => sum + p.amountMinor, 0);

    if (totalPaidMinor < totalAmountMinor) {
      throw new PosDomainError(
        "ERR_INSUFFICIENT_PAYMENT",
        `Total paid (${totalPaidMinor}) is less than total due (${totalAmountMinor})`
      );
    }

    const changeDueMinor = totalPaidMinor - totalAmountMinor;
    const cashPaidMinor = input.payments
      .filter((p) => p.tenderType === "CASH")
      .reduce((sum, p) => sum + p.amountMinor, 0);
    const netCashToDrawer = Math.max(0, cashPaidMinor - changeDueMinor);

    if (netCashToDrawer > 0) {
      await this.repo.saveShift({
        ...shift,
        expectedCashMinor: shift.expectedCashMinor + netCashToDrawer
      });
    }

    const receiptNumber = await this.repo.nextReceiptNumber(input.organizationId);
    const sale = await this.repo.saveSale({
      id: `sal_${randomUUID()}`,
      receiptNumber,
      idempotencyKey: input.idempotencyKey,
      organizationId: input.organizationId,
      branchId: input.branchId,
      warehouseId: input.warehouseId,
      registerId: input.registerId,
      shiftId: input.shiftId,
      cashierUserId: input.cashierUserId,
      customerId: input.customerId ?? null,
      currencyCode: input.currencyCode,
      subtotalMinor,
      cartDiscountMinor,
      netAmountMinor: Math.max(0, totalNetMinor - cartDiscountMinor),
      taxAmountMinor: totalTaxMinor,
      totalAmountMinor,
      totalPaidMinor,
      changeDueMinor,
      items: builtItems,
      payments: input.payments,
      status: "COMPLETED",
      createdAt: input.occurredOfflineAt ?? new Date().toISOString()
    });

    await this.eventBus.publish(
      createDomainEvent({
        eventType: "pos.sale.completed",
        organizationId: input.organizationId,
        branchId: input.branchId,
        payload: {
          saleId: sale.id,
          warehouseId: sale.warehouseId,
          currencyCode: sale.currencyCode,
          cashierUserId: sale.cashierUserId,
          items: sale.items.map((i) => ({
            productId: i.productId,
            baseUnitsDeducted: i.baseUnitsDeducted
          }))
        }
      })
    );

    return sale;
  }

  async syncOfflineSalesBatch(
    sales: readonly CompletePosSaleInput[]
  ): Promise<readonly PosSaleDto[]> {
    const synced: PosSaleDto[] = [];
    for (const saleInput of sales) {
      synced.push(await this.completeSale(saleInput));
    }
    return synced;
  }

  async processReturn(input: ProcessPosReturnInput): Promise<PosReturnDto> {
    const sale = await this.repo.getSaleById(input.organizationId, input.originalSaleId);
    if (!sale) {
      throw new PosDomainError("ERR_SALE_NOT_FOUND", "Original sale not found", 404);
    }
    const shift = await this.requireOpenShift(input.organizationId, input.shiftId);

    let totalRefundMinor = 0;
    const returnLines: {
      productId: string;
      productName: string;
      quantity: number;
      baseUnitsReturned: number;
      refundAmountMinor: number;
      restockToInventory: boolean;
    }[] = [];

    for (const reqLine of input.lines) {
      const origLine = sale.items.find((i) => i.productId === reqLine.productId);
      if (!origLine || reqLine.quantity <= 0 || reqLine.quantity > origLine.quantity) {
        throw new PosDomainError(
          "ERR_INVALID_RETURN_LINE",
          `Invalid return quantity for product ${reqLine.productId}`
        );
      }
      const perUnitGrossMinor = Math.round(origLine.lineGrossMinor / origLine.quantity);
      const refundAmountMinor = perUnitGrossMinor * reqLine.quantity;
      totalRefundMinor += refundAmountMinor;

      returnLines.push({
        productId: origLine.productId,
        productName: origLine.productName,
        quantity: reqLine.quantity,
        baseUnitsReturned: reqLine.quantity * origLine.conversionFactorToBase,
        refundAmountMinor,
        restockToInventory: reqLine.restockToInventory
      });
    }

    if (input.refundTender === "CASH") {
      await this.repo.saveShift({
        ...shift,
        expectedCashMinor: Math.max(0, shift.expectedCashMinor - totalRefundMinor)
      });
    }

    const isFullRefund = totalRefundMinor >= sale.totalAmountMinor;
    await this.repo.saveSale({
      ...sale,
      status: isFullRefund ? "REFUNDED" : "PARTIALLY_REFUNDED"
    });

    const posReturn = await this.repo.saveReturn({
      id: `ret_${randomUUID()}`,
      organizationId: input.organizationId,
      originalSaleId: sale.id,
      warehouseId: sale.warehouseId,
      shiftId: input.shiftId,
      cashierUserId: input.cashierUserId,
      currencyCode: sale.currencyCode,
      reason: input.reason,
      refundTender: input.refundTender,
      totalRefundMinor,
      lines: returnLines,
      createdAt: new Date().toISOString()
    });

    await this.eventBus.publish(
      createDomainEvent({
        eventType: "pos.sale.returned",
        organizationId: input.organizationId,
        branchId: sale.branchId,
        payload: {
          returnId: posReturn.id,
          originalSaleId: sale.id,
          warehouseId: sale.warehouseId,
          currencyCode: sale.currencyCode,
          cashierUserId: input.cashierUserId,
          lines: returnLines
        }
      })
    );

    return posReturn;
  }

  private async requireOpenShift(
    organizationId: string,
    shiftId: string
  ): Promise<CashierShiftDto> {
    const shift = await this.repo.getShiftById(organizationId, shiftId);
    if (!shift || shift.status !== "OPEN") {
      throw new PosDomainError("ERR_SHIFT_NOT_OPEN", "An active open shift is required", 409);
    }
    return shift;
  }
}
