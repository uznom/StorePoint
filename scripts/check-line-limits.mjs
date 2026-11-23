#!/usr/bin/env node
/**
 * Mechanically enforces the < 800 lines per source file ceiling across the StorePoint v2 monorepo.
 * Soft target: 200-400 lines. Hard ceiling: 799 lines.
 */
import { readdirSync, readFileSync, statSync } from "node:fs";
import { extname, join, relative } from "node:path";

const HARD_CEILING = 799;
const SOFT_WARNING = 400;
const SOURCE_EXTENSIONS = new Set([".ts", ".tsx", ".js", ".mjs", ".css", ".html", ".kt", ".kts"]);
const IGNORED_DIRS = new Set([
  "node_modules",
  "dist",
  "build",
  "coverage",
  "assets",
  ".git",
  ".turbo",
  ".agents"
]);

const rootDir = process.cwd();
const violations = [];
const warnings = [];
let checkedCount = 0;

function walk(dir) {
  for (const entry of readdirSync(dir)) {
    if (IGNORED_DIRS.has(entry)) continue;
    const fullPath = join(dir, entry);
    const stat = statSync(fullPath);
    if (stat.isDirectory()) {
      walk(fullPath);
      continue;
    }
    const ext = extname(entry);
    if (!SOURCE_EXTENSIONS.has(ext)) continue;
    if (entry.endsWith(".d.ts")) continue;

    checkedCount += 1;
    const content = readFileSync(fullPath, "utf8");
    const lineCount = content.split(/\r?\n/).length;
    const relPath = relative(rootDir, fullPath);

    if (lineCount > HARD_CEILING) {
      violations.push({ relPath, lineCount });
    } else if (lineCount > SOFT_WARNING) {
      warnings.push({ relPath, lineCount });
    }
  }
}

walk(rootDir);

if (warnings.length > 0) {
  console.warn(`⚠️  ${warnings.length} file(s) exceed soft target (${SOFT_WARNING} lines):`);
  for (const w of warnings) {
    console.warn(`   - ${w.relPath} (${w.lineCount} lines)`);
  }
}

if (violations.length > 0) {
  console.error(`❌ Line limit violation: ${violations.length} file(s) exceed hard ceiling (< 800 lines):`);
  for (const v of violations) {
    console.error(`   - ${v.relPath} (${v.lineCount} lines > ${HARD_CEILING})`);
  }
  process.exit(1);
}

console.log(`✅ Checked ${checkedCount} source files — all strictly < 800 lines.`);
