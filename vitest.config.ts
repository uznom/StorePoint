import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    coverage: {
      provider: "v8",
      include: ["packages/*/src/**/*.ts", "services/*/src/**/*.ts", "apps/*/src/**/*.ts"],
      exclude: ["packages/contracts/src/**/*.ts", "**/*.d.ts"]
    }
  }
});
