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
});
