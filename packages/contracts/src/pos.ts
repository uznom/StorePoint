import type { TaxRuleDto } from "./catalog.js";

export type PaymentTenderType =
  | "CASH"
  | "CARD"
  | "MOBILE_WALLET"
  | "STORE_CREDIT"
  | "BANK_TRANSFER";

export type ReturnReasonCode =
  | "DEFECTIVE_SPOILED"
  | "EXPIRED"
  | "WRONG_ITEM"
  | "CUSTOMER_CHANGED_MIND";

export interface DenominationCountItem {
  readonly denominationMinor: number;
  readonly count: number;
}

export interface CashierShiftDto {
  readonly id: string;
  readonly organizationId: string;
  readonly branchId: string;
  readonly registerId: string;
  readonly cashierUserId: string;
  readonly currencyCode: string;
  readonly status: "OPEN" | "CLOSED";
  readonly openingFloatMinor: number;
  readonly expectedCashMinor: number;
  readonly countedClosingCashMinor: number | null;
  readonly varianceMinor: number | null;
  readonly denominations: readonly DenominationCountItem[];
  readonly openedAt: string;
  readonly closedAt: string | null;
  readonly notes: string;
}

export interface DrawerMovementDto {
  readonly id: string;
  readonly organizationId: string;
  readonly shiftId: string;
  readonly cashierUserId: string;
  readonly type: "CASH_IN" | "CASH_OUT";
  /** Positive minor units */
  readonly amountMinor: number;
  readonly reason: string;
  readonly occurredAt: string;
}

export interface SalePaymentLineDto {
  readonly tenderType: PaymentTenderType;
  readonly amountMinor: number;
  readonly reference?: string;
}

export interface SaleLineItemInput {
  readonly productId: string;
  readonly productName: string;
  readonly uomCode: string;
  readonly uomName: string;
  readonly conversionFactorToBase: number;
  readonly quantity: number;
  readonly unitPriceMinor: number;
  readonly lineDiscountMinor?: number;
  readonly taxRules?: readonly TaxRuleDto[];
}

export interface SaleLineItemDto {
  readonly productId: string;
  readonly productName: string;
  readonly uomCode: string;
  readonly uomName: string;
  readonly conversionFactorToBase: number;
  readonly quantity: number;
  readonly baseUnitsDeducted: number;
  readonly unitPriceMinor: number;
  readonly lineDiscountMinor: number;
  readonly lineNetMinor: number;
  readonly lineTaxMinor: number;
  readonly lineGrossMinor: number;
}

export interface CompletePosSaleInput {
  readonly idempotencyKey: string;
  readonly organizationId: string;
  readonly branchId: string;
  readonly warehouseId: string;
  readonly registerId: string;
  readonly shiftId: string;
  readonly cashierUserId: string;
  readonly customerId?: string | null;
  readonly currencyCode: string;
  readonly cartDiscountMinor?: number;
  readonly items: readonly SaleLineItemInput[];
  readonly payments: readonly SalePaymentLineDto[];
  readonly occurredOfflineAt?: string;
}

export interface PosSaleDto {
  readonly id: string;
  readonly receiptNumber: string;
  readonly idempotencyKey: string;
  readonly organizationId: string;
  readonly branchId: string;
  readonly warehouseId: string;
  readonly registerId: string;
  readonly shiftId: string;
  readonly cashierUserId: string;
  readonly customerId: string | null;
  readonly currencyCode: string;
  readonly subtotalMinor: number;
  readonly cartDiscountMinor: number;
  readonly netAmountMinor: number;
  readonly taxAmountMinor: number;
  readonly totalAmountMinor: number;
  readonly totalPaidMinor: number;
  readonly changeDueMinor: number;
  readonly items: readonly SaleLineItemDto[];
  readonly payments: readonly SalePaymentLineDto[];
  readonly status: "COMPLETED" | "PARTIALLY_REFUNDED" | "REFUNDED";
  readonly createdAt: string;
}

export interface PosReturnLineInput {
  readonly productId: string;
  readonly quantity: number;
  readonly restockToInventory: boolean;
}

export interface ProcessPosReturnInput {
  readonly organizationId: string;
  readonly originalSaleId: string;
  readonly shiftId: string;
  readonly cashierUserId: string;
  readonly reason: ReturnReasonCode;
  readonly refundTender: "CASH" | "STORE_CREDIT" | "EXCHANGE";
  readonly lines: readonly PosReturnLineInput[];
  readonly notes?: string;
}

export interface PosReturnDto {
  readonly id: string;
  readonly organizationId: string;
  readonly originalSaleId: string;
  readonly warehouseId: string;
  readonly shiftId: string;
  readonly cashierUserId: string;
  readonly currencyCode: string;
  readonly reason: ReturnReasonCode;
  readonly refundTender: "CASH" | "STORE_CREDIT" | "EXCHANGE";
  readonly totalRefundMinor: number;
  readonly lines: readonly {
    readonly productId: string;
    readonly productName: string;
    readonly quantity: number;
    readonly baseUnitsReturned: number;
    readonly refundAmountMinor: number;
    readonly restockToInventory: boolean;
  }[];
  readonly createdAt: string;
}
