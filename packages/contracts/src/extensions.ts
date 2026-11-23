import type {
  CreateProductInput,
  TaxRuleDto
} from "./catalog.js";
import type { FloatServiceCategory } from "./finance-crm.js";
import type { DomainEventType } from "./index.js";

export interface FloatTemplatePresetDto {
  readonly providerCode: string;
  readonly providerName: string;
  readonly category: FloatServiceCategory;
  readonly defaultServiceFeeMinor: number;
  readonly commissionBps: number;
}

export interface CountryPackManifestDto {
  readonly packId: string;
  readonly name: string;
  readonly region: string;
  readonly countryCode: string;
  readonly defaultCurrencyCode: string;
  readonly taxIdLabel: string;
  readonly defaultTaxRules: readonly TaxRuleDto[];
  readonly floatTemplates: readonly FloatTemplatePresetDto[];
}

export interface WebhookSubscriptionDto {
  readonly id: string;
  readonly organizationId: string;
  readonly targetUrl: string;
  readonly eventTypes: readonly DomainEventType[];
  readonly isActive: boolean;
  readonly createdAt: string;
}

/**
 * Legacy StorePoint v1 Android (`DatabaseBackupManager.kt`) JSON export schema subset
 * used by the v1 -> v2 Migration Importer.
 */
export interface LegacyV1BackupPayload {
  readonly storeConfig: {
    readonly storeName: string;
    readonly currencySymbol: string;
    readonly taxPercentage: number;
    readonly gcashBalance?: number;
    readonly smartLoadBalance?: number;
    readonly globeLoadBalance?: number;
    readonly gcashServiceFee?: number;
    readonly loadServiceFee?: number;
  };
  readonly categories: readonly {
    readonly id: number;
    readonly name: string;
    readonly colorAccent?: string;
  }[];
  readonly products: readonly {
    readonly id: number;
    readonly name: string;
    readonly categoryId: number;
    readonly price: number;
    readonly cost?: number;
    readonly stockCount: number;
    readonly barcode: string;
    readonly baseUom?: string;
    readonly hasStick10s?: boolean;
    readonly priceStick10s?: number;
    readonly barcode10s?: string;
    readonly hasStick20s?: boolean;
    readonly priceStick20s?: number;
    readonly barcode20s?: string;
    readonly hasReam?: boolean;
    readonly priceReam?: number;
    readonly barcodeReam?: string;
    readonly hasCustomUom?: boolean;
    readonly customUomName?: string;
    readonly customUomMultiplier?: number;
    readonly customUomPrice?: number;
    readonly barcodeCustomUom?: string;
  }[];
}

export interface V1MigrationPlanDto {
  readonly organizationId: string;
  readonly currencyCode: string;
  readonly normalizedProducts: readonly CreateProductInput[];
  readonly floatAccountsToCreate: readonly {
    readonly providerCode: string;
    readonly providerName: string;
    readonly category: FloatServiceCategory;
    readonly initialBalanceMinor: number;
    readonly defaultServiceFeeMinor: number;
    readonly commissionBps: number;
  }[];
  readonly initialStockMovements: readonly {
    readonly sku: string;
    readonly quantityBaseUnits: number;
    readonly unitCostMinor: number;
  }[];
}
