import type { CustomerDto, FloatAccountDto } from "@storepoint/contracts";
import { Money } from "@storepoint/money";

export interface ErpFinanceCrmOverviewVm {
  readonly totalReceivablesFormatted: string;
  readonly totalFloatBalanceFormatted: string;
  readonly activeCreditCustomersCount: number;
  readonly m3eTemplateHtml: string;
}

/**
 * Renders the Material 3 Expressive (`matraic/m3e`) Customer Accounts Receivable (AR / Store Tab)
 * and Generalized Digital Services Float Hub dashboard view.
 */
export function buildErpFinanceCrmView(params: {
  currencyCode: string;
  locale?: string;
  customers: readonly CustomerDto[];
  floatAccounts: readonly FloatAccountDto[];
}): ErpFinanceCrmOverviewVm {
  const locale = params.locale ?? "en-US";
  const fmt = (minor: number) => Money.fromMinor(minor, params.currencyCode).format(locale);

  const totalArMinor = params.customers.reduce(
    (sum, c) => sum + c.currentCreditBalanceMinor,
    0
  );
  const totalFloatMinor = params.floatAccounts.reduce(
    (sum, f) => sum + f.currentBalanceMinor,
    0
  );
  const activeCreditCustomersCount = params.customers.filter(
    (c) => c.currentCreditBalanceMinor > 0
  ).length;

  const customerCards = params.customers
    .map(
      (c) =>
        `<m3e-card variant="elevated" class="sp-customer-ar-card" data-customer="${c.id}">` +
        `<h4>${c.name}</h4>` +
        `<m3e-badge>${c.loyaltyPoints} pts</m3e-badge>` +
        `<p>Balance: <strong>${fmt(c.currentCreditBalanceMinor)}</strong> / Limit: ${fmt(c.creditLimitMinor)}</p>` +
        `</m3e-card>`
    )
    .join("\n");

  const floatCards = params.floatAccounts
    .map(
      (f) =>
        `<m3e-card variant="tonal" class="sp-float-card" data-provider="${f.providerCode}">` +
        `<h4>${f.providerName} (${f.category})</h4>` +
        `<strong>${fmt(f.currentBalanceMinor)}</strong>` +
        `<m3e-chip variant="assist">Fee: ${fmt(f.defaultServiceFeeMinor)}</m3e-chip>` +
        `</m3e-card>`
    )
    .join("\n");

  const m3eTemplateHtml = [
    `<section class="sp-m3e-finance-crm">`,
    `  <header class="sp-m3e-kpi-row">`,
    `    <m3e-card variant="filled"><span>Total Accounts Receivable (AR)</span><strong>${fmt(totalArMinor)}</strong></m3e-card>`,
    `    <m3e-card variant="filled"><span>Total Digital Float Balance</span><strong>${fmt(totalFloatMinor)}</strong></m3e-card>`,
    `  </header>`,
    `  <div class="sp-ar-grid">${customerCards}</div>`,
    `  <div class="sp-float-grid">${floatCards}</div>`,
    `</section>`
  ].join("\n");

  return Object.freeze({
    totalReceivablesFormatted: fmt(totalArMinor),
    totalFloatBalanceFormatted: fmt(totalFloatMinor),
    activeCreditCustomersCount,
    m3eTemplateHtml
  });
}
