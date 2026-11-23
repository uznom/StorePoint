export type ArMovementType =
  | "CREDIT_SALE"
  | "PAYMENT_RECEIVED"
  | "RETURN_CREDIT"
  | "ADJUSTMENT";

export type FloatServiceCategory =
  | "EWALLET"
  | "AIRTIME_LOAD"
  | "BILL_PAY"
  | "REMITTANCE";

export type FloatMovementType =
  | "OWNER_RELOAD"
  | "CUSTOMER_CASH_IN"
  | "CUSTOMER_CASH_OUT"
  | "TOPUP_SALE"
  | "SHIFT_RECONCILE";

export interface CustomerDto {
  readonly id: string;
  readonly organizationId: string;
  readonly name: string;
  readonly phone: string;
  readonly email: string;
  readonly creditLimitMinor: number;
  readonly currentCreditBalanceMinor: number;
  readonly loyaltyPoints: number;
  readonly isActive: boolean;
  readonly createdAt: string;
}

export interface ArLedgerEntryDto {
  readonly id: string;
  readonly organizationId: string;
  readonly customerId: string;
  readonly type: ArMovementType;
  /** Positive increases customer debt (charge), negative reduces debt (payment/return) */
  readonly deltaMinor: number;
  readonly balanceAfterMinor: number;
  readonly referenceId: string;
  readonly actorUserId: string;
  readonly notes: string;
  readonly occurredAt: string;
}

/**
 * Generalized Float & Digital Services Account (replaces StorePoint v1's hardcoded GCash/Smart/Globe columns).
 * Usable anywhere in the world (GCash/Maya in PH, M-Pesa in Kenya, UPI in India, OXXO Pay in LatAm, etc.).
 */
export interface FloatAccountDto {
  readonly id: string;
  readonly organizationId: string;
  readonly branchId: string;
  readonly providerCode: string;
  readonly providerName: string;
  readonly category: FloatServiceCategory;
  readonly currencyCode: string;
  readonly currentBalanceMinor: number;
  readonly defaultServiceFeeMinor: number;
  /** Retailer commission rebate in basis points (e.g. 200 = 2.00% airtime load rebate) */
  readonly commissionBps: number;
  readonly updatedAt: string;
}

export interface FloatLedgerEntryDto {
  readonly id: string;
  readonly organizationId: string;
  readonly floatAccountId: string;
  readonly providerCode: string;
  readonly type: FloatMovementType;
  readonly principalMinor: number;
  /** Signed change to the digital wallet float balance */
  readonly floatDeltaMinor: number;
  readonly balanceAfterMinor: number;
  /** Service fee collected from the customer */
  readonly customerFeeMinor: number;
  /** Retailer rebate/commission earned on the transaction */
  readonly storeCommissionMinor: number;
  readonly reference: string;
  readonly actorUserId: string;
  readonly notes: string;
  readonly occurredAt: string;
}

export interface ExecuteFloatTransactionInput {
  readonly organizationId: string;
  readonly floatAccountId: string;
  readonly type: "OWNER_RELOAD" | "CUSTOMER_CASH_IN" | "CUSTOMER_CASH_OUT" | "TOPUP_SALE";
  readonly principalMinor: number;
  readonly customerFeeMinor?: number;
  readonly reference?: string;
  readonly actorUserId: string;
  readonly notes?: string;
}

export interface ReconcileFloatAccountInput {
  readonly organizationId: string;
  readonly floatAccountId: string;
  readonly countedBalanceMinor: number;
  readonly actorUserId: string;
  readonly notes?: string;
}
