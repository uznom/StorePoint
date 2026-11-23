import { describe, expect, it } from "vitest";
import { buildIdentityService } from "../src/index.js";

describe("@storepoint/identity-service — Global Onboarding, RBAC, PIN Lockout & Audit", () => {
  it("Onboards a store in any global currency, creates staff via RBAC, and records audit logs", async () => {
    const { app } = buildIdentityService();

    // 1. Onboard a store in Kenyan Shillings (KES)
    const onboardRes = await app.inject({
      method: "POST",
      url: "/v1/organizations/onboard",
      payload: {
        name: "Nairobi Fresh Mini-Mart",
        defaultCurrencyCode: "KES",
        locale: "en-KE",
        timezone: "Africa/Nairobi",
        templateType: "GROCERY_MINIMART",
        ownerUsername: "amara",
        ownerDisplayName: "Amara O.",
        ownerPin: "8492"
      }
    });

    expect(onboardRes.statusCode).toBe(201);
    const onboardBody = onboardRes.json();
    expect(onboardBody.success).toBe(true);
    const orgId = onboardBody.data.organization.id as string;
    const ownerId = onboardBody.data.owner.id as string;

    // 2. Owner creates a Cashier account
    const createCashierRes = await app.inject({
      method: "POST",
      url: "/v1/users",
      payload: {
        organizationId: orgId,
        actorUserId: ownerId,
        username: "david",
        displayName: "David K.",
        role: "CASHIER",
        pin: "5931"
      }
    });
    expect(createCashierRes.statusCode).toBe(201);
    const cashierId = createCashierRes.json().data.id as string;

    // 3. Cashier cannot create another user (RBAC enforcement)
    const forbiddenRes = await app.inject({
      method: "POST",
      url: "/v1/users",
      payload: {
        organizationId: orgId,
        actorUserId: cashierId,
        username: "hacker",
        displayName: "Unauthorized",
        role: "MANAGER",
        pin: "7483"
      }
    });
    expect(forbiddenRes.statusCode).toBe(403);

    // 4. Verify audit trail recorded onboarding + user creation
    const auditRes = await app.inject({
      method: "GET",
      url: `/v1/organizations/${orgId}/audit-logs`
    });
    expect(auditRes.statusCode).toBe(200);
    expect(auditRes.json().data.length).toBeGreaterThanOrEqual(2);
  });

  it("Blocks weak PINs and enforces progressive lockout after 3 failed PIN attempts", async () => {
    const { app } = buildIdentityService();

    const weakRes = await app.inject({
      method: "POST",
      url: "/v1/organizations/onboard",
      payload: {
        name: "Weak PIN Store",
        defaultCurrencyCode: "USD",
        locale: "en-US",
        timezone: "UTC",
        templateType: "GENERAL_RETAIL",
        ownerUsername: "owner",
        ownerDisplayName: "Owner",
        ownerPin: "1234"
      }
    });
    expect(weakRes.statusCode).toBe(400);
    expect(weakRes.json().error.code).toBe("ERR_WEAK_PIN");

    const onboardRes = await app.inject({
      method: "POST",
      url: "/v1/organizations/onboard",
      payload: {
        name: "Secure Mart",
        defaultCurrencyCode: "PHP",
        locale: "en-PH",
        timezone: "Asia/Manila",
        templateType: "GROCERY_MINIMART",
        ownerUsername: "maria",
        ownerDisplayName: "Maria S.",
        ownerPin: "9184"
      }
    });
    const orgId = onboardRes.json().data.organization.id as string;

    // 3 wrong attempts trigger 429 lockout
    for (let i = 0; i < 3; i += 1) {
      const bad = await app.inject({
        method: "POST",
        url: "/v1/auth/pin",
        payload: { organizationId: orgId, username: "maria", pin: "0999" }
      });
      expect(bad.statusCode).toBe(401);
    }

    const lockedRes = await app.inject({
      method: "POST",
      url: "/v1/auth/pin",
      payload: { organizationId: orgId, username: "maria", pin: "9184" }
    });
    expect(lockedRes.statusCode).toBe(429);
    expect(lockedRes.json().error.code).toBe("ERR_ACCOUNT_LOCKED");
  });
});
