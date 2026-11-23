import type {
  GlJournalEntryDto,
  GlJournalLineDto,
  ProfitAndLossReportDto
} from "@storepoint/contracts";
import { randomUUID } from "node:crypto";
import { FinanceCrmDomainError } from "./finance-crm-service.js";

/**
 * Double-Entry Accounting Lite & Profit-and-Loss (P&L) Engine.
 * Guarantees `sum(debits) === sum(credits)` on every journal entry and derives real-time
 * P&L, COGS, Gross Margin, Operating Expense, and Tax Payable statements for small stores.
 */
export class AccountingGlDomainService {
  private readonly journals: GlJournalEntryDto[] = [];

  async postJournalEntry(input: {
    organizationId: string;
    branchId?: string | null;
    currencyCode: string;
    referenceType: GlJournalEntryDto["referenceType"];
    referenceId: string;
    description: string;
    lines: readonly GlJournalLineDto[];
  }): Promise<GlJournalEntryDto> {
    if (input.lines.length < 2) {
      throw new FinanceCrmDomainError(
        "ERR_INVALID_JOURNAL",
        "Double-entry journal requires at least 2 lines"
      );
    }

    let totalDebit = 0;
    let totalCredit = 0;
    for (const line of input.lines) {
      if (line.debitMinor < 0 || line.creditMinor < 0) {
        throw new FinanceCrmDomainError(
          "ERR_NEGATIVE_JOURNAL_LINE",
          "Debit and credit amounts must be non-negative"
        );
      }
      totalDebit += line.debitMinor;
      totalCredit += line.creditMinor;
    }

    if (totalDebit <= 0 || totalDebit !== totalCredit) {
      throw new FinanceCrmDomainError(
        "ERR_UNBALANCED_JOURNAL",
        `Unbalanced journal entry: debits (${totalDebit}) !== credits (${totalCredit})`
      );
    }

    const entry: GlJournalEntryDto = Object.freeze({
      id: `gl_${randomUUID()}`,
      organizationId: input.organizationId,
      branchId: input.branchId ?? null,
      currencyCode: input.currencyCode,
      referenceType: input.referenceType,
      referenceId: input.referenceId,
      description: input.description.trim(),
      lines: Object.freeze(input.lines.map((l) => Object.freeze({ ...l }))),
      occurredAt: new Date().toISOString()
    });

    this.journals.push(entry);
    return entry;
  }

  async postSaleAndCogsJournal(input: {
    organizationId: string;
    branchId?: string | null;
    currencyCode: string;
    saleId: string;
    netSalesMinor: number;
    taxMinor: number;
    cogsMinor: number;
    isCreditSale?: boolean;
  }): Promise<GlJournalEntryDto> {
    const grossMinor = input.netSalesMinor + input.taxMinor;
    const lines: GlJournalLineDto[] = [
      {
        accountCode: input.isCreditSale ? "1100_ACCOUNTS_RECEIVABLE" : "1000_CASH_AND_BANK",
        debitMinor: grossMinor,
        creditMinor: 0,
        memo: "Gross sale proceeds"
      },
      {
        accountCode: "4000_SALES_REVENUE",
        debitMinor: 0,
        creditMinor: input.netSalesMinor,
        memo: "Net merchandise revenue"
      }
    ];

    if (input.taxMinor > 0) {
      lines.push({
        accountCode: "2100_TAX_PAYABLE",
        debitMinor: 0,
        creditMinor: input.taxMinor,
        memo: "Collected output tax"
      });
    }

    if (input.cogsMinor > 0) {
      lines.push(
        {
          accountCode: "5000_COST_OF_GOODS_SOLD",
          debitMinor: input.cogsMinor,
          creditMinor: 0,
          memo: "Cost of goods sold (WAC)"
        },
        {
          accountCode: "1200_INVENTORY_ASSET",
          debitMinor: 0,
          creditMinor: input.cogsMinor,
          memo: "Inventory asset relief"
        }
      );
    }

    return this.postJournalEntry({
      organizationId: input.organizationId,
      branchId: input.branchId,
      currencyCode: input.currencyCode,
      referenceType: "POS_SALE",
      referenceId: input.saleId,
      description: `POS Sale ${input.saleId}`,
      lines
    });
  }

  async generateProfitAndLossReport(
    organizationId: string,
    currencyCode: string,
    branchId?: string
  ): Promise<ProfitAndLossReportDto> {
    const matching = this.journals.filter(
      (j) =>
        j.organizationId === organizationId &&
        (!branchId || j.branchId === branchId)
    );

    let salesRevenueMinor = 0;
    let digitalServiceIncomeMinor = 0;
    let cogsMinor = 0;
    let operatingExpensesMinor = 0;
    let taxPayableMinor = 0;

    for (const entry of matching) {
      for (const line of entry.lines) {
        switch (line.accountCode) {
          case "4000_SALES_REVENUE":
            salesRevenueMinor += line.creditMinor - line.debitMinor;
            break;
          case "4100_DIGITAL_SERVICE_INCOME":
            digitalServiceIncomeMinor += line.creditMinor - line.debitMinor;
            break;
          case "5000_COST_OF_GOODS_SOLD":
            cogsMinor += line.debitMinor - line.creditMinor;
            break;
          case "6000_OPERATING_EXPENSES":
            operatingExpensesMinor += line.debitMinor - line.creditMinor;
            break;
          case "2100_TAX_PAYABLE":
            taxPayableMinor += line.creditMinor - line.debitMinor;
            break;
        }
      }
    }

    const totalRevenueMinor = salesRevenueMinor + digitalServiceIncomeMinor;
    const grossProfitMinor = totalRevenueMinor - cogsMinor;
    const grossMarginBps =
      totalRevenueMinor > 0 ? Math.round((grossProfitMinor * 10000) / totalRevenueMinor) : 0;
    const netProfitMinor = grossProfitMinor - operatingExpensesMinor;

    return Object.freeze({
      organizationId,
      currencyCode,
      salesRevenueMinor,
      digitalServiceIncomeMinor,
      totalRevenueMinor,
      cogsMinor,
      grossProfitMinor,
      grossMarginBps,
      operatingExpensesMinor,
      netProfitMinor,
      taxPayableMinor,
      journalEntryCount: matching.length
    });
  }
}
