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

export type GlAccountCode =
  | "1000_CASH_AND_BANK"
  | "1100_ACCOUNTS_RECEIVABLE"
  | "1200_INVENTORY_ASSET"
  | "1300_DIGITAL_FLOAT_ASSET"
  | "2000_ACCOUNTS_PAYABLE"
  | "2100_TAX_PAYABLE"
  | "3000_OWNERS_EQUITY"
  | "4000_SALES_REVENUE"
  | "4100_DIGITAL_SERVICE_INCOME"
  | "5000_COST_OF_GOODS_SOLD"
  | "6000_OPERATING_EXPENSES";

export interface GlJournalLineDto {
  readonly accountCode: GlAccountCode;
  readonly debitMinor: number;
  readonly creditMinor: number;
  readonly memo?: string;
}

export interface GlJournalEntryDto {
  readonly id: string;
  readonly organizationId: string;
  readonly branchId: string | null;
  readonly currencyCode: string;
  readonly referenceType: "POS_SALE" | "FLOAT_SERVICE" | "OPERATING_EXPENSE" | "MANUAL_JOURNAL";
  readonly referenceId: string;
  readonly description: string;
  readonly lines: readonly GlJournalLineDto[];
  readonly occurredAt: string;
}

export interface ProfitAndLossReportDto {
  readonly organizationId: string;
  readonly currencyCode: string;
  readonly salesRevenueMinor: number;
  readonly digitalServiceIncomeMinor: number;
  readonly totalRevenueMinor: number;
  readonly cogsMinor: number;
  readonly grossProfitMinor: number;
  /** Gross profit margin in basis points (e.g. 3500 = 35.00%) */
  readonly grossMarginBps: number;
  readonly operatingExpensesMinor: number;
  readonly netProfitMinor: number;
  readonly taxPayableMinor: number;
  readonly journalEntryCount: number;
}

