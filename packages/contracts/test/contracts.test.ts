import { describe, expect, it } from "vitest";
import type { ApiResponse, DomainEvent, OrganizationDto } from "../src/index.js";

describe("@storepoint/contracts — DTO & Event Contract Integrity", () => {
  it("Constructs valid API response envelopes and domain events", () => {
    const org: OrganizationDto = {
      id: "org_01",
      name: "Sunrise Neighborhood Mart",
      defaultCurrencyCode: "USD",
      locale: "en-US",
      timezone: "America/New_York",
      templateType: "GROCERY_MINIMART",
      enabledCountryPacks: [],
      createdAt: new Date().toISOString()
    };

    const response: ApiResponse<OrganizationDto> = {
      success: true,
      data: org,
      error: null
    };

    const event: DomainEvent<{ organizationId: string }> = {
      eventId: "evt_01",
      eventType: "identity.organization.created",
      organizationId: org.id,
      branchId: null,
      occurredAt: org.createdAt,
      correlationId: "corr_01",
      payload: { organizationId: org.id }
    };

    expect(response.success).toBe(true);
    expect(response.data?.defaultCurrencyCode).toBe("USD");
    expect(event.eventType).toBe("identity.organization.created");
  });
});
