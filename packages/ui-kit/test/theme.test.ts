import { describe, expect, it } from "vitest";
import { STOREPOINT_M3E_THEMES, buildM3ExpressiveCssVariables } from "../src/index.js";

describe("@storepoint/ui-kit — Material 3 Expressive (matraic/m3e) Tokens", () => {
  it("Generates CSS custom properties for matraic/m3e components", () => {
    const css = buildM3ExpressiveCssVariables("emerald");
    expect(css).toContain("--m3e-color-primary: #006C4C;");
    expect(css).toContain("--m3e-motion-spring-fast:");
    expect(Object.keys(STOREPOINT_M3E_THEMES)).toEqual(["emerald", "indigo", "amber"]);
  });
});
