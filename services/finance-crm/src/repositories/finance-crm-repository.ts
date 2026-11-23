import type {
  ArLedgerEntryDto,
  CustomerDto,
  FloatAccountDto,
  FloatLedgerEntryDto
} from "@storepoint/contracts";

export interface FinanceCrmRepository {
  saveCustomer(customer: CustomerDto): Promise<CustomerDto>;
  getCustomerById(organizationId: string, customerId: string): Promise<CustomerDto | null>;
  listCustomers(organizationId: string): Promise<readonly CustomerDto[]>;
  appendArEntry(entry: ArLedgerEntryDto): Promise<ArLedgerEntryDto>;
  listArEntries(organizationId: string, customerId?: string): Promise<readonly ArLedgerEntryDto[]>;
  saveFloatAccount(account: FloatAccountDto): Promise<FloatAccountDto>;
  getFloatAccountById(
    organizationId: string,
    floatAccountId: string
  ): Promise<FloatAccountDto | null>;
  listFloatAccounts(organizationId: string): Promise<readonly FloatAccountDto[]>;
  appendFloatEntry(entry: FloatLedgerEntryDto): Promise<FloatLedgerEntryDto>;
  listFloatEntries(
    organizationId: string,
    floatAccountId?: string
  ): Promise<readonly FloatLedgerEntryDto[]>;
}

/**
 * Immutable repository for `finance-crm-service` (`finance_crm.*` PostgreSQL schema boundary).
 */
export class InMemoryFinanceCrmRepository implements FinanceCrmRepository {
  private readonly customers = new Map<string, CustomerDto>();
  private readonly arLedger: ArLedgerEntryDto[] = [];
  private readonly floatAccounts = new Map<string, FloatAccountDto>();
  private readonly floatLedger: FloatLedgerEntryDto[] = [];

  async saveCustomer(customer: CustomerDto): Promise<CustomerDto> {
    const frozen = Object.freeze({ ...customer });
    this.customers.set(frozen.id, frozen);
    return frozen;
  }

  async getCustomerById(organizationId: string, customerId: string): Promise<CustomerDto | null> {
    const found = this.customers.get(customerId);
    if (!found || found.organizationId !== organizationId) return null;
    return found;
  }

  async listCustomers(organizationId: string): Promise<readonly CustomerDto[]> {
    return [...this.customers.values()].filter((c) => c.organizationId === organizationId);
  }

  async appendArEntry(entry: ArLedgerEntryDto): Promise<ArLedgerEntryDto> {
    const frozen = Object.freeze({ ...entry });
    this.arLedger.push(frozen);
    return frozen;
  }

  async listArEntries(
    organizationId: string,
    customerId?: string
  ): Promise<readonly ArLedgerEntryDto[]> {
    return this.arLedger.filter(
      (e) =>
        e.organizationId === organizationId &&
        (!customerId || e.customerId === customerId)
    );
  }

  async saveFloatAccount(account: FloatAccountDto): Promise<FloatAccountDto> {
    const frozen = Object.freeze({ ...account });
    this.floatAccounts.set(frozen.id, frozen);
    return frozen;
  }

  async getFloatAccountById(
    organizationId: string,
    floatAccountId: string
  ): Promise<FloatAccountDto | null> {
    const found = this.floatAccounts.get(floatAccountId);
    if (!found || found.organizationId !== organizationId) return null;
    return found;
  }

  async listFloatAccounts(organizationId: string): Promise<readonly FloatAccountDto[]> {
    return [...this.floatAccounts.values()].filter((f) => f.organizationId === organizationId);
  }

  async appendFloatEntry(entry: FloatLedgerEntryDto): Promise<FloatLedgerEntryDto> {
    const frozen = Object.freeze({ ...entry });
    this.floatLedger.push(frozen);
    return frozen;
  }

  async listFloatEntries(
    organizationId: string,
    floatAccountId?: string
  ): Promise<readonly FloatLedgerEntryDto[]> {
    return this.floatLedger.filter(
      (e) =>
        e.organizationId === organizationId &&
        (!floatAccountId || e.floatAccountId === floatAccountId)
    );
  }
}
