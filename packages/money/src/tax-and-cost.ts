import { Money } from "./money.js";

export type TaxMode = "INCLUSIVE" | "EXCLUSIVE" | "EXEMPT";

export interface TaxRule {
  readonly id: string;
  readonly name: string;
  /** Basis points: e.g. 1200 = 12.00% VAT, 825 = 8.25% Sales Tax */
  readonly rateBps: number;
  readonly mode: TaxMode;
  /** If true, applies on top of subtotal + previous non-compound taxes (e.g. Quebec QST / compound duties) */
  readonly compound?: boolean;
}

export interface TaxBreakdownLine {
  readonly ruleId: string;
  readonly name: string;
  readonly rateBps: number;
  readonly taxAmount: Money;
}

export interface TaxCalculationResult {
  readonly netAmount: Money;
  readonly taxAmount: Money;
  readonly grossAmount: Money;
  readonly breakdown: readonly TaxBreakdownLine[];
}

/**
 * Global tax engine supporting VAT (inclusive/exclusive), multi-component GST (CGST+SGST),
 * US Sales Tax, compound taxes, and tax-exempt items across any ISO 4217 currency.
 */
export function calculateTax(baseAmount: Money, rules: readonly TaxRule[]): TaxCalculationResult {
  const activeRules = rules.filter((r) => r.mode !== "EXEMPT" && r.rateBps > 0);
  if (activeRules.length === 0) {
    return {
      netAmount: baseAmount,
      taxAmount: Money.zero(baseAmount.currency.code),
      grossAmount: baseAmount,
      breakdown: []
    };
  }

  const allInclusive = activeRules.every((r) => r.mode === "INCLUSIVE");
  if (allInclusive) {
    const totalBps = activeRules.reduce((sum, r) => sum + r.rateBps, 0);
    const grossMinor = baseAmount.amountMinor;
    const netMinor = Math.round((grossMinor * 10000) / (10000 + totalBps));
    const totalTaxMinor = grossMinor - netMinor;
    const totalTax = Money.fromMinor(totalTaxMinor, baseAmount.currency.code);
    const allocated = totalTax.allocate(activeRules.map((r) => r.rateBps));
    const breakdown = activeRules.map((rule, idx) => ({
      ruleId: rule.id,
      name: rule.name,
      rateBps: rule.rateBps,
      taxAmount: allocated[idx] ?? Money.zero(baseAmount.currency.code)
    }));

    return {
      netAmount: Money.fromMinor(netMinor, baseAmount.currency.code),
      taxAmount: totalTax,
      grossAmount: baseAmount,
      breakdown
    };
  }

  // Exclusive / Compound tax calculation
  let runningTaxMinor = 0;
  const breakdown: TaxBreakdownLine[] = [];
  for (const rule of activeRules) {
    if (rule.mode === "INCLUSIVE") {
      throw new Error("Cannot mix INCLUSIVE and EXCLUSIVE tax rules on the same line item");
    }
    const taxableBaseMinor = rule.compound
      ? baseAmount.amountMinor + runningTaxMinor
      : baseAmount.amountMinor;
    const lineTaxMinor = Math.round((taxableBaseMinor * rule.rateBps) / 10000);
    runningTaxMinor += lineTaxMinor;
    breakdown.push({
      ruleId: rule.id,
      name: rule.name,
      rateBps: rule.rateBps,
      taxAmount: Money.fromMinor(lineTaxMinor, baseAmount.currency.code)
    });
  }

  const totalTax = Money.fromMinor(runningTaxMinor, baseAmount.currency.code);
  return {
    netAmount: baseAmount,
    taxAmount: totalTax,
    grossAmount: baseAmount.add(totalTax),
    breakdown
  };
}

/**
 * Calculates updated Weighted Average Cost (WAC) when receiving new stock or repackaging via BOM.
 */
export function calculateWeightedAverageCost(
  currentQtyBaseUnits: number,
  currentUnitCost: Money,
  receivedQtyBaseUnits: number,
  receivedUnitCost: Money
): Money {
  if (receivedQtyBaseUnits <= 0) {
    return currentUnitCost;
  }
  if (currentQtyBaseUnits <= 0) {
    return receivedUnitCost;
  }
  const totalExistingValue = currentUnitCost.multiply(currentQtyBaseUnits);
  const totalReceivedValue = receivedUnitCost.multiply(receivedQtyBaseUnits);
  const combinedQty = currentQtyBaseUnits + receivedQtyBaseUnits;
  const newUnitMinor = Math.round(
    (totalExistingValue.amountMinor + totalReceivedValue.amountMinor) / combinedQty
  );
  return Money.fromMinor(newUnitMinor, currentUnitCost.currency.code);
}
