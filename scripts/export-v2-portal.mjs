#!/usr/bin/env node
/**
 * Exports the StorePoint v2 Material 3 Expressive (matraic/m3e) ERP & Offline-First POS Portal
 * into `app/src/main/assets/storepoint-v2-portal.html` for packaging inside the Android APK.
 */
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { buildGatewayService } from "../services/gateway/dist/index.js";

const { gateway } = buildGatewayService();
const res = await gateway.inject({ method: "GET", url: "/" });
if (res.statusCode !== 200) {
  throw new Error(`Failed to render v2 portal asset: HTTP ${res.statusCode}`);
}

const outPath = resolve(process.cwd(), "app/src/main/assets/storepoint-v2-portal.html");
mkdirSync(dirname(outPath), { recursive: true });
writeFileSync(outPath, res.body, "utf8");
console.log(`✅ Exported StorePoint v2 matraic/m3e portal to ${outPath} (${res.body.split("\n").length} lines)`);
