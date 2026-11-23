import { describe, expect, it } from "vitest";
import { buildFinanceCrmService } from "../src/index.js";

describe("@storepoint/finance-crm-service — Customer AR Credit Ledger & Generalized Float Services Hub", () => {
  it("Enforces customer credit limits, records partial AR repayments, and awards loyalty points", async () => {
    const { app } = buildFinanceCrmService();
    const orgId = "org_fin_01";

    // 1. Create Customer with 500.00 (50000 minor) credit limit
    const cusRes = await app.inject({
      method: "POST",
      url: "/v1/customers",
      payload: {
        organizationId: orgId,
        name: "Rosa Hernandez",
        phone: "+52-55-1234-5678",
        creditLimitMinor: 50000
      }
    });
    expect(cusRes.statusCode).toBe(201);
    const customerId = cusRes.json().data.id as string;

    // 2. Charge 300.00 (30000 minor) to store credit tab
    const chargeRes = await app.inject({
      method: "POST",
      url: "/v1/customers/credit/charge",
      payload: {
        organizationId: orgId,
        customerId,
        amountMinor: 30000,
        referenceId: "OR-000105",
        actorUserId: "usr_cashier"
      }
    });
    expect(chargeRes.statusCode).toBe(201);
    expect(chargeRes.json().data.customer.currentCreditBalanceMinor).toBe(30000);
    expect(chargeRes.json().data.customer.loyaltyPoints).toBe(30);

    // 3. Reject charge that exceeds 500.00 credit limit
    const overRes = await app.inject({
      method: "POST",
      url: "/v1/customers/credit/charge",
      payload: {
        organizationId: orgId,
        customerId,
        amountMinor: 25000,
        referenceId: "OR-000106",
        actorUserId: "usr_cashier"
      }
    });
    expect(overRes.statusCode).toBe(409);
    expect(overRes.json().error.code).toBe("ERR_CREDIT_LIMIT_EXCEEDED");

    // 4. Record partial repayment of 120.00 (12000 minor) -> remaining balance = 180.00 (18000 minor)
    const payRes = await app.inject({
      method: "POST",
      url: "/v1/customers/credit/pay",
      payload: {
        organizationId: orgId,
        customerId,
        amountMinor: 12000,
        actorUserId: "usr_cashier"
      }
    });
    expect(payRes.statusCode).toBe(200);
    expect(payRes.json().data.customer.currentCreditBalanceMinor).toBe(18000);
  });

  it("Tracks Generalized Float & Digital Service transactions (M-Pesa / GCash / Airtime Topup with 2% rebate & shift reconcile)", async () => {
    const { app } = buildFinanceCrmService();
    const orgId = "org_fin_02";

    // 1. Create a generalized Airtime Load Float account with 2% (200 bps) retailer commission rebate
    const fltRes = await app.inject({
      method: "POST",
      url: "/v1/float-accounts",
      payload: {
        organizationId: orgId,
        branchId: "br_01",
        providerCode: "AIRTIME_TOPUP",
        providerName: "Prepaid Mobile Airtime",
        category: "AIRTIME_LOAD",
        currencyCode: "KES",
        initialBalanceMinor: 100000, // 1,000.00 KES initial float
        defaultServiceFeeMinor: 500, // 5.00 KES customer fee
        commissionBps: 200 // 2% rebate
      }
    });
    expect(fltRes.statusCode).toBe(201);
    const floatAccountId = fltRes.json().data.id as string;

    // 2. Customer buys 100.00 KES (10000 minor) airtime top-up -> 2% rebate (200 minor) stays in float, so net float consumed = 9800 -> balanceAfter = 90200!
    const topupRes = await app.inject({
      method: "POST",
      url: "/v1/float-accounts/transactions",
      payload: {
        organizationId: orgId,
        floatAccountId,
        type: "TOPUP_SALE",
        principalMinor: 10000,
        actorUserId: "usr_cashier",
        reference: "TOPUP-9901"
      }
    });
    expect(topupRes.statusCode).toBe(201);
    expect(topupRes.json().data.entry.storeCommissionMinor).toBe(200);
    expect(topupRes.json().data.entry.customerFeeMinor).toBe(500);
    expect(topupRes.json().data.account.currentBalanceMinor).toBe(90200);

    // 3. Reconcile float at shift close
    const recRes = await app.inject({
      method: "POST",
      url: "/v1/float-accounts/reconcile",
      payload: {
        organizationId: orgId,
        floatAccountId,
        countedBalanceMinor: 90200,
        actorUserId: "usr_owner"
      }
    });
    expect(recRes.statusCode).toBe(200);
    expect(recRes.json().data.entry.floatDeltaMinor).toBe(0);
  });

  it("Enforces balanced double-entry journals and computes real-time Profit & Loss (P&L), Gross Margin, and Tax Payable", async () => {
    const { app } = buildFinanceCrmService();
    const orgId = "org_fin_gl_01";

    // 1. Reject unbalanced journal entry (debits !== credits)
    const unbalRes = await app.inject({
      method: "POST",
      url: "/v1/accounting/journals",
      payload: {
        organizationId: orgId,
        currencyCode: "USD",
        referenceType: "MANUAL_JOURNAL",
        referenceId: "MAN-1",
        description: "Unbalanced Entry",
        lines: [
          { accountCode: "1000_CASH_AND_BANK", debitMinor: 5000, creditMinor: 0 },
          { accountCode: "4000_SALES_REVENUE", debitMinor: 0, creditMinor: 4000 }
        ]
      }
    });
    expect(unbalRes.statusCode).toBe(400);
    expect(unbalRes.json().error.code).toBe("ERR_UNBALANCED_JOURNAL");

    // 2. Post Sale + COGS Journal (Net Sales = $200.00, Tax = $24.00, COGS = $120.00 -> Gross Profit = $80.00 / 40% margin)
    const saleGlRes = await app.inject({
      method: "POST",
      url: "/v1/accounting/journals/sale",
      payload: {
        organizationId: orgId,
        currencyCode: "USD",
        saleId: "sal_101",
        netSalesMinor: 20000,
        taxMinor: 2400,
        cogsMinor: 12000
      }
    });
    expect(saleGlRes.statusCode).toBe(201);

    // 3. Post Operating Expense Journal ($30.00 Utilities)
    await app.inject({
      method: "POST",
      url: "/v1/accounting/journals",
      payload: {
        organizationId: orgId,
        currencyCode: "USD",
        referenceType: "OPERATING_EXPENSE",
        referenceId: "ovh_1",
        description: "Store Electricity Bill",
        lines: [
          { accountCode: "6000_OPERATING_EXPENSES", debitMinor: 3000, creditMinor: 0 },
          { accountCode: "1000_CASH_AND_BANK", debitMinor: 0, creditMinor: 3000 }
        ]
      }
    });

    // 4. Generate P&L Report -> Gross Profit = $80.00 (8000 minor), Gross Margin = 4000 bps (40%), Net Profit = $50.00 (5000 minor), Tax Payable = $24.00 (2400 minor)
    const pnlRes = await app.inject({
      method: "GET",
      url: `/v1/accounting/reports/pnl?organizationId=${orgId}&currencyCode=USD`
    });
    expect(pnlRes.statusCode).toBe(200);
    const pnl = pnlRes.json().data;
    expect(pnl.salesRevenueMinor).toBe(20000);
    expect(pnl.cogsMinor).toBe(12000);
    expect(pnl.grossProfitMinor).toBe(8000);
    expect(pnl.grossMarginBps).toBe(4000);
    expect(pnl.operatingExpensesMinor).toBe(3000);
    expect(pnl.netProfitMinor).toBe(5000);
    expect(pnl.taxPayableMinor).toBe(2400);
  });
});
