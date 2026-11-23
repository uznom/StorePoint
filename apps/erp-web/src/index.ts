import type { StoreTemplateType } from "@storepoint/contracts";
import { CURRENCIES } from "@storepoint/money";
import { buildM3ExpressiveCssVariables } from "@storepoint/ui-kit";

export interface ErpNavSection {
  readonly id: string;
  readonly label: string;
  readonly icon: string;
  readonly serviceOwner: string;
}

export const ERP_NAVIGATION_SECTIONS: readonly ErpNavSection[] = Object.freeze([
  { id: "dashboard", label: "Overview & P&L", icon: "analytics", serviceOwner: "finance-crm-service" },
  { id: "catalog", label: "Products & Multi-UOM", icon: "inventory_2", serviceOwner: "catalog-service" },
  { id: "inventory", label: "Stock Ledger & BOM", icon: "warehouse", serviceOwner: "inventory-service" },
  { id: "purchasing", label: "Suppliers & POs", icon: "local_shipping", serviceOwner: "purchasing-service" },
  { id: "customers", label: "Customers & AR Credit", icon: "account_balance_wallet", serviceOwner: "finance-crm-service" },
  { id: "float", label: "Digital & Float Hub", icon: "currency_exchange", serviceOwner: "finance-crm-service" },
  { id: "branches", label: "Branches & Staff RBAC", icon: "storefront", serviceOwner: "identity-service" },
  { id: "extensions", label: "Country Packs & Tax", icon: "extension", serviceOwner: "extensions-service" }
]);

/**
 * Generates the Material 3 Expressive (`matraic/m3e`) Back-Office ERP shell descriptor.
 */
export function createErpShellConfig(options: {
  storeName: string;
  currencyCode: string;
  templateType: StoreTemplateType;
  theme?: "emerald" | "indigo" | "amber";
}) {
  const currency = CURRENCIES[options.currencyCode] ?? CURRENCIES.USD!;
  return Object.freeze({
    appTitle: `${options.storeName} — StorePoint ERP`,
    currency,
    templateType: options.templateType,
    m3eThemeCss: buildM3ExpressiveCssVariables(options.theme ?? "emerald"),
    navigation: ERP_NAVIGATION_SECTIONS
  });
}

export * from "./catalog-inventory-view.js";

