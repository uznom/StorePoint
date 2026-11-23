import { describe, expect, it } from "vitest";
import {
  CURRENCIES,
  Money,
  calculateTax,
  calculateWeightedAverageCost
} from "../src/index.js";

describe("@storepoint/money — Money & Global Tax Engine", () => {
  it("Avoids IEEE-754 floating point drift (0.1 + 0.2 === 0.30)", () => {
    const a = Money.fromMajor("0.10", "USD");
    const b = Money.fromMajor("0.20", "USD");
    const sum = a.add(b);
    expect(sum.amountMinor).toBe(30);
    expect(sum.toMajorString()).toBe("0.30");
  });

  it("Supports zero-decimal (JPY), two-decimal (PHP/USD), and three-decimal (KWD) currencies", () => {
    const jpy = Money.fromMajor(1500, "JPY");
    const php = Money.fromMajor("99.50", "PHP");
    const kwd = Money.fromMajor("1.250", "KWD");

    expect(jpy.amountMinor).toBe(1500);
    expect(php.amountMinor).toBe(9950);
    expect(kwd.amountMinor).toBe(1250);
    expect(Object.keys(CURRENCIES).length).toBeGreaterThanOrEqual(15);
  });

  it("Prevents adding or subtracting different currencies", () => {
    const usd = Money.fromMajor(10, "USD");
    const php = Money.fromMajor(10, "PHP");
    expect(() => usd.add(php)).toThrow(/Currency mismatch/);
  });

  it("Allocates indivisible amounts without losing a single centavo", () => {
    const total = Money.fromMajor("100.00", "PHP");
    const parts = total.allocate([1, 1, 1]);
    expect(parts.map((p) => p.amountMinor)).toEqual([3334, 3333, 3333]);
    const recombined = parts.reduce((acc, p) => acc.add(p), Money.zero("PHP"));
    expect(recombined.amountMinor).toBe(10000);
  });

  it("Calculates inclusive VAT accurately (e.g. 12% PH VAT on 112.00 -> 100.00 net + 12.00 tax)", () => {
    const gross = Money.fromMajor("112.00", "PHP");
    const res = calculateTax(gross, [
      { id: "vat-12", name: "12% VAT", rateBps: 1200, mode: "INCLUSIVE" }
    ]);
    expect(res.netAmount.toMajorString()).toBe("100.00");
    expect(res.taxAmount.toMajorString()).toBe("12.00");
    expect(res.grossAmount.toMajorString()).toBe("112.00");
  });

  it("Calculates multi-component exclusive GST (e.g. India 9% CGST + 9% SGST) and compound tax", () => {
    const net = Money.fromMajor("1000.00", "INR");
    const res = calculateTax(net, [
      { id: "cgst-9", name: "CGST 9%", rateBps: 900, mode: "EXCLUSIVE" },
      { id: "sgst-9", name: "SGST 9%", rateBps: 900, mode: "EXCLUSIVE" }
    ]);
    expect(res.taxAmount.toMajorString()).toBe("180.00");
    expect(res.grossAmount.toMajorString()).toBe("1180.00");
    expect(res.breakdown).toHaveLength(2);
  });

  it("Computes Weighted Average Cost (WAC) on purchase order goods receipt", () => {
    const currentCost = Money.fromMajor("40.00", "PHP");
    const receivedCost = Money.fromMajor("50.00", "PHP");
    const wac = calculateWeightedAverageCost(10, currentCost, 10, receivedCost);
    expect(wac.toMajorString()).toBe("45.00");
  });
});
