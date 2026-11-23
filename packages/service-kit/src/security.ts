import { pbkdf2Sync, randomBytes, timingSafeEqual } from "node:crypto";

const PBKDF2_ITERATIONS = 120_000;
const KEY_LENGTH = 32;
const DIGEST = "sha256";

/**
 * Weak 4-digit PINs rejected at creation (carried forward from StorePoint v1 security hardening).
 */
const WEAK_PINS = new Set([
  "0000", "1111", "2222", "3333", "4444",
  "5555", "6666", "7777", "8888", "9999",
  "1234", "4321", "1212", "2121", "2580",
  "0852", "1357", "2468", "1010", "2020"
]);

export function validatePinStrength(pin: string): { valid: boolean; reason?: string } {
  if (!/^\d{4}$/.test(pin)) {
    return { valid: false, reason: "PIN must be exactly 4 numeric digits" };
  }
  if (WEAK_PINS.has(pin)) {
    return { valid: false, reason: "PIN is too easily guessable; choose a non-trivial 4-digit PIN" };
  }
  return { valid: true };
}

export function hashSecret(secret: string): string {
  const salt = randomBytes(16).toString("hex");
  const derived = pbkdf2Sync(secret, salt, PBKDF2_ITERATIONS, KEY_LENGTH, DIGEST).toString("hex");
  return `pbkdf2_sha256$${PBKDF2_ITERATIONS}$${salt}$${derived}`;
}

export function verifySecret(secret: string, storedHash: string): boolean {
  const parts = storedHash.split("$");
  if (parts.length !== 4 || parts[0] !== "pbkdf2_sha256") {
    return false;
  }
  const iterations = Number(parts[1]);
  const salt = parts[2] ?? "";
  const expectedHex = parts[3] ?? "";
  if (!Number.isSafeInteger(iterations) || iterations <= 0 || !salt || !expectedHex) {
    return false;
  }
  const actualBuf = pbkdf2Sync(secret, salt, iterations, KEY_LENGTH, DIGEST);
  const expectedBuf = Buffer.from(expectedHex, "hex");
  if (actualBuf.length !== expectedBuf.length) {
    return false;
  }
  return timingSafeEqual(actualBuf, expectedBuf);
}

interface LockoutState {
  readonly failures: number;
  readonly lockedUntilMs: number;
}

const BACKOFF_STEPS_MS = [30_000, 60_000, 120_000, 300_000];
const FAILURE_THRESHOLD = 3;

/**
 * Progressive per-account lockout tracker (30s -> 60s -> 120s -> 300s) so an attacker
 * cannot brute-force a 4-digit PIN or lock out other cashiers on the same register.
 */
export class ProgressiveLockoutTracker {
  private readonly states = new Map<string, LockoutState>();

  isLocked(accountKey: string, nowMs = Date.now()): { locked: boolean; retryAfterMs: number } {
    const state = this.states.get(accountKey);
    if (!state || state.lockedUntilMs <= nowMs) {
      return { locked: false, retryAfterMs: 0 };
    }
    return { locked: true, retryAfterMs: state.lockedUntilMs - nowMs };
  }

  recordFailure(accountKey: string, nowMs = Date.now()): LockoutState {
    const prev = this.states.get(accountKey) ?? { failures: 0, lockedUntilMs: 0 };
    const failures = prev.failures + 1;
    let lockedUntilMs = prev.lockedUntilMs;
    if (failures >= FAILURE_THRESHOLD) {
      const stepIdx = Math.min(
        failures - FAILURE_THRESHOLD,
        BACKOFF_STEPS_MS.length - 1
      );
      const backoffMs = BACKOFF_STEPS_MS[stepIdx] ?? 30_000;
      lockedUntilMs = nowMs + backoffMs;
    }
    const nextState = Object.freeze({ failures, lockedUntilMs });
    this.states.set(accountKey, nextState);
    return nextState;
  }

  recordSuccess(accountKey: string): void {
    this.states.delete(accountKey);
  }
}
