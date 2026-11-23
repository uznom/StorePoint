import type {
  CountryPackManifestDto,
  V1MigrationPlanDto
} from "@storepoint/contracts";

export interface ErpExtensionsOverviewVm {
  readonly availablePacksCount: number;
  readonly migratedProductCount: number;
  readonly m3eTemplateHtml: string;
}

/**
 * Renders the Material 3 Expressive (`matraic/m3e`) Global Country Pack Marketplace
 * and StorePoint v1 Android JSON -> v2 Migration wizard view.
 */
export function buildErpExtensionsAndMigrationView(params: {
  countryPacks: readonly CountryPackManifestDto[];
  activePackIds?: readonly string[];
  lastMigrationPlan?: V1MigrationPlanDto | null;
}): ErpExtensionsOverviewVm {
  const activeSet = new Set(params.activePackIds ?? []);
  const migratedProductCount = params.lastMigrationPlan?.normalizedProducts.length ?? 0;

  const packCards = params.countryPacks
    .map((pack) => {
      const isActive = activeSet.has(pack.packId);
      return [
        `<m3e-card variant="${isActive ? "tonal" : "elevated"}" class="sp-country-pack-card" data-pack="${pack.packId}">`,
        `  <h4>${pack.name} (${pack.countryCode})</h4>`,
        `  <m3e-chip variant="assist">${pack.defaultCurrencyCode} • ${pack.taxIdLabel}</m3e-chip>`,
        `  <m3e-badge>${pack.floatTemplates.length} Float Presets</m3e-badge>`,
        `  <m3e-button variant="${isActive ? "outlined" : "filled"}">${isActive ? "Installed" : "Activate Pack"}</m3e-button>`,
        `</m3e-card>`
      ].join("\n");
    })
    .join("\n");

  const m3eTemplateHtml = [
    `<section class="sp-m3e-extensions-hub">`,
    `  <header class="sp-m3e-kpi-row">`,
    `    <m3e-card variant="filled"><span>Global Country Packs</span><strong>${params.countryPacks.length}</strong></m3e-card>`,
    `    <m3e-card variant="filled"><span>v1 Migrated SKUs</span><strong>${migratedProductCount}</strong></m3e-card>`,
    `  </header>`,
    `  <div class="sp-country-pack-grid">${packCards}</div>`,
    `</section>`
  ].join("\n");

  return Object.freeze({
    availablePacksCount: params.countryPacks.length,
    migratedProductCount,
    m3eTemplateHtml
  });
}
