import { describe, expect, it } from "vitest";
import {
  InMemoryEventBus,
  ProgressiveLockoutTracker,
  createDomainEvent,
  fail,
  hashSecret,
  ok,
  validatePinStrength,
  verifySecret
} from "../src/index.js";

describe("@storepoint/service-kit — Security, Envelopes & Event Bus", () => {
  it("Rejects trivial 4-digit PINs and accepts strong PINs", () => {
    expect(validatePinStrength("1234").valid).toBe(false);
    expect(validatePinStrength("0000").valid).toBe(false);
    expect(validatePinStrength("2580").valid).toBe(false);
    expect(validatePinStrength("7392").valid).toBe(true);
  });

  it("Hashes and verifies secrets with PBKDF2-HMAC-SHA256", () => {
    const hash = hashSecret("7392");
    expect(verifySecret("7392", hash)).toBe(true);
    expect(verifySecret("7391", hash)).toBe(false);
  });

  it("Enforces progressive per-account lockout without locking other users", () => {
    const tracker = new ProgressiveLockoutTracker();
    const now = 1_700_000_000_000;

    tracker.recordFailure("org1:cashierA", now);
    tracker.recordFailure("org1:cashierA", now);
    expect(tracker.isLocked("org1:cashierA", now).locked).toBe(false);

    // 3rd failure triggers 30s lockout
    tracker.recordFailure("org1:cashierA", now);
    const lock1 = tracker.isLocked("org1:cashierA", now);
    expect(lock1.locked).toBe(true);
    expect(lock1.retryAfterMs).toBe(30_000);

    // Other cashier on same register is unaffected
    expect(tracker.isLocked("org1:cashierB", now).locked).toBe(false);

    // 4th failure escalates to 60s lockout
    tracker.recordFailure("org1:cashierA", now + 31_000);
    expect(tracker.isLocked("org1:cashierA", now + 31_000).retryAfterMs).toBe(60_000);
  });

  it("Deduplicates domain events by eventId and delivers to subscribers", async () => {
    const bus = new InMemoryEventBus();
    const received: string[] = [];

    bus.subscribe<{ userId: string }>("identity.user.created", (evt) => {
      received.push(evt.payload.userId);
    });

    const evt = createDomainEvent({
      eventType: "identity.user.created",
      organizationId: "org_1",
      payload: { userId: "usr_1" }
    });

    await bus.publish(evt);
    await bus.publish(evt); // Duplicate publish ignored via idempotency set

    expect(received).toEqual(["usr_1"]);
    expect(ok({ id: 1 }).success).toBe(true);
    expect(fail("ERR_AUTH", "Invalid PIN").success).toBe(false);
  });
});
