import type {
  ArLedgerEntryDto,
  CustomerDto,
  ExecuteFloatTransactionInput,
  FloatAccountDto,
  FloatLedgerEntryDto,
  FloatServiceCategory,
  ReconcileFloatAccountInput
} from "@storepoint/contracts";
import { type EventBus, createDomainEvent } from "@storepoint/service-kit";
import { randomUUID } from "node:crypto";
import type { FinanceCrmRepository } from "../repositories/finance-crm-repository.js";

export class FinanceCrmDomainError extends Error {
  readonly code: string;
  readonly statusCode: number;

  constructor(code: string, message: string, statusCode = 400) {
    super(message);
    this.code = code;
    this.statusCode = statusCode;
  }
}

export class FinanceCrmDomainService {
  private readonly repo: FinanceCrmRepository;
  private readonly eventBus: EventBus;

  constructor(repo: FinanceCrmRepository, eventBus: EventBus) {
    this.repo = repo;
    this.eventBus = eventBus;
  }

  async createCustomer(input: {
    organizationId: string;
    name: string;
    phone?: string;
    email?: string;
    creditLimitMinor?: number;
  }): Promise<CustomerDto> {
    if (!input.name.trim()) {
      throw new FinanceCrmDomainError("ERR_CUSTOMER_NAME", "Customer name is required");
    }
    return this.repo.saveCustomer({
      id: `cus_${randomUUID()}`,
      organizationId: input.organizationId,
      name: input.name.trim(),
      phone: input.phone?.trim() ?? "",
      email: input.email?.trim() ?? "",
      creditLimitMinor: Math.max(0, input.creditLimitMinor ?? 0),
      currentCreditBalanceMinor: 0,
      loyaltyPoints: 0,
      isActive: true,
      createdAt: new Date().toISOString()
    });
  }

  async chargeCustomerCredit(input: {
    organizationId: string;
    customerId: string;
    amountMinor: number;
    referenceId: string;
    actorUserId: string;
    notes?: string;
  }): Promise<{ customer: CustomerDto; entry: ArLedgerEntryDto }> {
    if (input.amountMinor <= 0) {
      throw new FinanceCrmDomainError("ERR_INVALID_CHARGE", "Credit charge must be positive");
    }
    const customer = await this.requireCustomer(input.organizationId, input.customerId);
    const nextBalance = customer.currentCreditBalanceMinor + input.amountMinor;
    if (nextBalance > customer.creditLimitMinor) {
      throw new FinanceCrmDomainError(
        "ERR_CREDIT_LIMIT_EXCEEDED",
        `Charge exceeds customer credit limit (${customer.creditLimitMinor} minor units)`,
        409
      );
    }

    const updatedCustomer = await this.repo.saveCustomer({
      ...customer,
      currentCreditBalanceMinor: nextBalance,
      loyaltyPoints: customer.loyaltyPoints + Math.floor(input.amountMinor / 1000)
    });

    const entry = await this.repo.appendArEntry({
      id: `ar_${randomUUID()}`,
      organizationId: input.organizationId,
      customerId: customer.id,
      type: "CREDIT_SALE",
      deltaMinor: input.amountMinor,
      balanceAfterMinor: nextBalance,
      referenceId: input.referenceId,
      actorUserId: input.actorUserId,
      notes: input.notes ?? "POS Store Credit Sale",
      occurredAt: new Date().toISOString()
    });

    await this.eventBus.publish(
      createDomainEvent({
        eventType: "finance.ar.updated",
        organizationId: input.organizationId,
        payload: {
          customerId: customer.id,
          deltaMinor: input.amountMinor,
          balanceAfterMinor: nextBalance
        }
      })
    );

    return { customer: updatedCustomer, entry };
  }

  async recordCustomerPayment(input: {
    organizationId: string;
    customerId: string;
    amountMinor: number;
    referenceId?: string;
    actorUserId: string;
    notes?: string;
  }): Promise<{ customer: CustomerDto; entry: ArLedgerEntryDto }> {
    if (input.amountMinor <= 0) {
      throw new FinanceCrmDomainError("ERR_INVALID_PAYMENT", "Payment amount must be positive");
    }
    const customer = await this.requireCustomer(input.organizationId, input.customerId);
    const nextBalance = Math.max(0, customer.currentCreditBalanceMinor - input.amountMinor);

    const updatedCustomer = await this.repo.saveCustomer({
      ...customer,
      currentCreditBalanceMinor: nextBalance
    });

    const entry = await this.repo.appendArEntry({
      id: `ar_${randomUUID()}`,
      organizationId: input.organizationId,
      customerId: customer.id,
      type: "PAYMENT_RECEIVED",
      deltaMinor: -input.amountMinor,
      balanceAfterMinor: nextBalance,
      referenceId: input.referenceId ?? "",
      actorUserId: input.actorUserId,
      notes: input.notes ?? "Customer Credit Repayment",
      occurredAt: new Date().toISOString()
    });

    await this.eventBus.publish(
      createDomainEvent({
        eventType: "finance.ar.updated",
        organizationId: input.organizationId,
        payload: {
          customerId: customer.id,
          deltaMinor: -input.amountMinor,
          balanceAfterMinor: nextBalance
        }
      })
    );

    return { customer: updatedCustomer, entry };
  }

  async createFloatAccount(input: {
    organizationId: string;
    branchId: string;
    providerCode: string;
    providerName: string;
    category: FloatServiceCategory;
    currencyCode: string;
    initialBalanceMinor?: number;
    defaultServiceFeeMinor?: number;
    commissionBps?: number;
  }): Promise<FloatAccountDto> {
    if (!input.providerCode.trim() || !input.providerName.trim()) {
      throw new FinanceCrmDomainError("ERR_FLOAT_PROVIDER", "Float provider code and name required");
    }
    return this.repo.saveFloatAccount({
      id: `flt_${randomUUID()}`,
      organizationId: input.organizationId,
      branchId: input.branchId,
      providerCode: input.providerCode.trim().toUpperCase(),
      providerName: input.providerName.trim(),
      category: input.category,
      currencyCode: input.currencyCode,
      currentBalanceMinor: Math.max(0, input.initialBalanceMinor ?? 0),
      defaultServiceFeeMinor: Math.max(0, input.defaultServiceFeeMinor ?? 0),
      commissionBps: Math.max(0, input.commissionBps ?? 0),
      updatedAt: new Date().toISOString()
    });
  }

  /**
   * Executes an auditable Float / Digital Services movement:
   * - `OWNER_RELOAD`: Owner funds the digital float (+principalMinor).
   * - `CUSTOMER_CASH_IN` / `TOPUP_SALE`: Store sends digital money/airtime to customer (-principalMinor + retailer commission rebate), collects cash + fee.
   * - `CUSTOMER_CASH_OUT`: Customer transfers digital money to store wallet (+principalMinor), store pays out cash and collects fee.
   */
  async executeFloatTransaction(input: ExecuteFloatTransactionInput): Promise<{
    account: FloatAccountDto;
    entry: FloatLedgerEntryDto;
  }> {
    if (input.principalMinor <= 0) {
      throw new FinanceCrmDomainError("ERR_FLOAT_AMOUNT", "Principal amount must be positive");
    }
    const account = await this.requireFloatAccount(input.organizationId, input.floatAccountId);

    const storeCommissionMinor =
      input.type === "TOPUP_SALE"
        ? Math.round((input.principalMinor * account.commissionBps) / 10000)
        : 0;

    const floatDeltaMinor =
      input.type === "OWNER_RELOAD" || input.type === "CUSTOMER_CASH_OUT"
        ? input.principalMinor
        : -(input.principalMinor - storeCommissionMinor);

    const nextBalance = account.currentBalanceMinor + floatDeltaMinor;
    if (nextBalance < 0) {
      throw new FinanceCrmDomainError(
        "ERR_INSUFFICIENT_FLOAT",
        `Insufficient ${account.providerName} float balance (${account.currentBalanceMinor})`,
        409
      );
    }

    const customerFeeMinor =
      input.type === "OWNER_RELOAD"
        ? 0
        : (input.customerFeeMinor ?? account.defaultServiceFeeMinor);

    const now = new Date().toISOString();
    const updatedAccount = await this.repo.saveFloatAccount({
      ...account,
      currentBalanceMinor: nextBalance,
      updatedAt: now
    });

    const entry = await this.repo.appendFloatEntry({
      id: `fltl_${randomUUID()}`,
      organizationId: input.organizationId,
      floatAccountId: account.id,
      providerCode: account.providerCode,
      type: input.type,
      principalMinor: input.principalMinor,
      floatDeltaMinor,
      balanceAfterMinor: nextBalance,
      customerFeeMinor,
      storeCommissionMinor,
      reference: input.reference ?? "",
      actorUserId: input.actorUserId,
      notes: input.notes ?? "",
      occurredAt: now
    });

    return { account: updatedAccount, entry };
  }

  async reconcileFloatAccount(input: ReconcileFloatAccountInput): Promise<{
    account: FloatAccountDto;
    entry: FloatLedgerEntryDto;
  }> {
    const account = await this.requireFloatAccount(input.organizationId, input.floatAccountId);
    const deltaMinor = input.countedBalanceMinor - account.currentBalanceMinor;
    const now = new Date().toISOString();

    const updatedAccount = await this.repo.saveFloatAccount({
      ...account,
      currentBalanceMinor: input.countedBalanceMinor,
      updatedAt: now
    });

    const entry = await this.repo.appendFloatEntry({
      id: `fltl_${randomUUID()}`,
      organizationId: input.organizationId,
      floatAccountId: account.id,
      providerCode: account.providerCode,
      type: "SHIFT_RECONCILE",
      principalMinor: Math.abs(deltaMinor),
      floatDeltaMinor: deltaMinor,
      balanceAfterMinor: input.countedBalanceMinor,
      customerFeeMinor: 0,
      storeCommissionMinor: 0,
      reference: "SHIFT-CLOSE",
      actorUserId: input.actorUserId,
      notes: input.notes ?? `Reconciled from ${account.currentBalanceMinor} to ${input.countedBalanceMinor}`,
      occurredAt: now
    });

    return { account: updatedAccount, entry };
  }

  private async requireCustomer(organizationId: string, customerId: string): Promise<CustomerDto> {
    const customer = await this.repo.getCustomerById(organizationId, customerId);
    if (!customer) {
      throw new FinanceCrmDomainError("ERR_CUSTOMER_NOT_FOUND", "Customer not found", 404);
    }
    return customer;
  }

  private async requireFloatAccount(
    organizationId: string,
    floatAccountId: string
  ): Promise<FloatAccountDto> {
    const account = await this.repo.getFloatAccountById(organizationId, floatAccountId);
    if (!account) {
      throw new FinanceCrmDomainError("ERR_FLOAT_NOT_FOUND", "Float account not found", 404);
    }
    return account;
  }
}
