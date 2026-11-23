export interface CurrencyDefinition {
  readonly code: string;
  readonly symbol: string;
  readonly decimals: 0 | 2 | 3;
  readonly name: string;
}

export const CURRENCIES: Readonly<Record<string, CurrencyDefinition>> = Object.freeze({
  USD: { code: "USD", symbol: "$", decimals: 2, name: "US Dollar" },
  EUR: { code: "EUR", symbol: "€", decimals: 2, name: "Euro" },
  GBP: { code: "GBP", symbol: "£", decimals: 2, name: "British Pound" },
  PHP: { code: "PHP", symbol: "₱", decimals: 2, name: "Philippine Peso" },
  JPY: { code: "JPY", symbol: "¥", decimals: 0, name: "Japanese Yen" },
  KRW: { code: "KRW", symbol: "₩", decimals: 0, name: "South Korean Won" },
  INR: { code: "INR", symbol: "₹", decimals: 2, name: "Indian Rupee" },
  BRL: { code: "BRL", symbol: "R$", decimals: 2, name: "Brazilian Real" },
  MXN: { code: "MXN", symbol: "Mex$", decimals: 2, name: "Mexican Peso" },
  KES: { code: "KES", symbol: "KSh", decimals: 2, name: "Kenyan Shilling" },
  NGN: { code: "NGN", symbol: "₦", decimals: 2, name: "Nigerian Naira" },
  IDR: { code: "IDR", symbol: "Rp", decimals: 0, name: "Indonesian Rupiah" },
  VND: { code: "VND", symbol: "₫", decimals: 0, name: "Vietnamese Dong" },
  SGD: { code: "SGD", symbol: "S$", decimals: 2, name: "Singapore Dollar" },
  AUD: { code: "AUD", symbol: "A$", decimals: 2, name: "Australian Dollar" },
  CAD: { code: "CAD", symbol: "CA$", decimals: 2, name: "Canadian Dollar" },
  KWD: { code: "KWD", symbol: "KD", decimals: 3, name: "Kuwaiti Dinar" }
});

export function getCurrency(code: string): CurrencyDefinition {
  const normalized = code.trim().toUpperCase();
  const found = CURRENCIES[normalized];
  if (!found) {
    throw new Error(`Unsupported ISO 4217 currency code: ${code}`);
  }
  return found;
}

/**
 * Immutable fixed-point Money value object represented in integer minor units.
 * Eliminates IEEE-754 floating-point drift across all POS and ERP calculations.
 */
export class Money {
  readonly amountMinor: number;
  readonly currency: CurrencyDefinition;

  private constructor(amountMinor: number, currency: CurrencyDefinition) {
    if (!Number.isSafeInteger(amountMinor)) {
      throw new Error(`Money minor unit must be a safe integer, received: ${amountMinor}`);
    }
    this.amountMinor = amountMinor;
    this.currency = currency;
    Object.freeze(this);
  }

  static fromMinor(amountMinor: number, currencyCode: string): Money {
    return new Money(Math.round(amountMinor), getCurrency(currencyCode));
  }

  static fromMajor(amountMajor: number | string, currencyCode: string): Money {
    const currency = getCurrency(currencyCode);
    const numeric = typeof amountMajor === "string" ? Number(amountMajor) : amountMajor;
    if (!Number.isFinite(numeric)) {
      throw new Error(`Invalid monetary amount: ${amountMajor}`);
    }
    const factor = 10 ** currency.decimals;
    return new Money(Math.round(numeric * factor), currency);
  }

  static zero(currencyCode: string): Money {
    return new Money(0, getCurrency(currencyCode));
  }

  private assertSameCurrency(other: Money): void {
    if (this.currency.code !== other.currency.code) {
      throw new Error(`Currency mismatch: ${this.currency.code} vs ${other.currency.code}`);
    }
  }

  add(other: Money): Money {
    this.assertSameCurrency(other);
    return new Money(this.amountMinor + other.amountMinor, this.currency);
  }

  subtract(other: Money): Money {
    this.assertSameCurrency(other);
    return new Money(this.amountMinor - other.amountMinor, this.currency);
  }

  multiply(factor: number): Money {
    if (!Number.isFinite(factor)) {
      throw new Error(`Invalid multiplier: ${factor}`);
    }
    return new Money(Math.round(this.amountMinor * factor), this.currency);
  }

  /**
   * Splits an amount into N parts without losing or inventing a single minor unit (penny/centavo).
   */
  allocate(ratios: readonly number[]): Money[] {
    const totalRatio = ratios.reduce((acc, r) => acc + r, 0);
    if (totalRatio <= 0) {
      throw new Error("Allocation ratios must sum to a positive number");
    }
    let remainder = this.amountMinor;
    const shares = ratios.map((ratio) => {
      const share = Math.trunc((this.amountMinor * ratio) / totalRatio);
      remainder -= share;
      return share;
    });
    const step = remainder >= 0 ? 1 : -1;
    for (let i = 0; i < Math.abs(remainder); i += 1) {
      shares[i % shares.length] = (shares[i % shares.length] ?? 0) + step;
    }
    return shares.map((s) => new Money(s, this.currency));
  }

  toMajorString(): string {
    const factor = 10 ** this.currency.decimals;
    return (this.amountMinor / factor).toFixed(this.currency.decimals);
  }

  format(locale = "en-US"): string {
    const factor = 10 ** this.currency.decimals;
    return new Intl.NumberFormat(locale, {
      style: "currency",
      currency: this.currency.code,
      minimumFractionDigits: this.currency.decimals,
      maximumFractionDigits: this.currency.decimals
    }).format(this.amountMinor / factor);
  }
}
