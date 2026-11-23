import { describe, expect, it } from "vitest";
import { ERP_NAVIGATION_SECTIONS, createErpShellConfig } from "../src/index.js";

describe("@storepoint/erp-web — Material 3 Expressive Back-Office ERP Shell", () => {
  it("Builds M3E ERP shell config with decoupled service navigation and currency context", () => {
    const shell = createErpShellConfig({
      storeName: "Aloha Neighborhood Mart",
      currencyCode: "USD",
      templateType: "GROCERY_MINIMART",
      theme: "emerald"
    });
    expect(shell.appTitle).toContain("Aloha Neighborhood Mart");
    expect(shell.currency.symbol).toBe("$");
    expect(shell.m3eThemeCss).toContain("--m3e-color-primary:");
    expect(ERP_NAVIGATION_SECTIONS).toHaveLength(8);
  });
});
