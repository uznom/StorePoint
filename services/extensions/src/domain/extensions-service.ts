import type {
  CountryPackManifestDto,
  CreateProductInput,
  CreateProductUomInput,
  DomainEventType,
  LegacyV1BackupPayload,
  V1MigrationPlanDto,
  WebhookSubscriptionDto
} from "@storepoint/contracts";
import { Money } from "@storepoint/money";
import { randomUUID } from "node:crypto";
import { OFFICIAL_COUNTRY_PACKS } from "./country-packs.js";

export class ExtensionsDomainError extends Error {
  readonly code: string;
  readonly statusCode: number;

  constructor(code: string, message: string, statusCode = 400) {
    super(message);
    this.code = code;
    this.statusCode = statusCode;
  }
}

export class ExtensionsDomainService {
  private readonly webhooks: WebhookSubscriptionDto[] = [];
  private readonly activatedPacks = new Map<string, Set<string>>();

  listCountryPacks(): readonly CountryPackManifestDto[] {
    return OFFICIAL_COUNTRY_PACKS;
  }

  activateCountryPack(organizationId: string, packId: string): CountryPackManifestDto {
    const pack = OFFICIAL_COUNTRY_PACKS.find((p) => p.packId === packId);
    if (!pack) {
      throw new ExtensionsDomainError("ERR_PACK_NOT_FOUND", `Country pack ${packId} not found`, 404);
    }
    const existing = this.activatedPacks.get(organizationId) ?? new Set<string>();
    existing.add(pack.packId);
    this.activatedPacks.set(organizationId, existing);
    return pack;
  }

  registerWebhook(input: {
    organizationId: string;
    targetUrl: string;
    eventTypes: readonly DomainEventType[];
  }): WebhookSubscriptionDto {
    if (!input.targetUrl.startsWith("http://") && !input.targetUrl.startsWith("https://")) {
      throw new ExtensionsDomainError("ERR_INVALID_WEBHOOK_URL", "Webhook URL must be HTTP/HTTPS");
    }
    if (input.eventTypes.length === 0) {
      throw new ExtensionsDomainError("ERR_EMPTY_WEBHOOK_EVENTS", "Select at least one event type");
    }
    const sub: WebhookSubscriptionDto = Object.freeze({
      id: `whk_${randomUUID()}`,
      organizationId: input.organizationId,
      targetUrl: input.targetUrl,
      eventTypes: Object.freeze([...input.eventTypes]),
      isActive: true,
      createdAt: new Date().toISOString()
    });
    this.webhooks.push(sub);
    return sub;
  }

  listWebhooks(organizationId: string): readonly WebhookSubscriptionDto[] {
    return this.webhooks.filter((w) => w.organizationId === organizationId);
  }

  /**
   * Converts a legacy StorePoint v1 Android JSON backup (`DatabaseBackupManager.kt`)
   * into normalized StorePoint v2 Multi-UOM products, integer minor-unit Money values,
   * and Generalized Float Accounts.
   */
  buildLegacyV1MigrationPlan(
    organizationId: string,
    backup: LegacyV1BackupPayload,
    currencyCode = "PHP"
  ): V1MigrationPlanDto {
    const toMinor = (major = 0) => Money.fromMajor(major, currencyCode).amountMinor;

    const normalizedProducts: CreateProductInput[] = [];
    const initialStockMovements: {
      sku: string;
      quantityBaseUnits: number;
      unitCostMinor: number;
    }[] = [];

    for (const oldProd of backup.products) {
      const sku = `V1-SKU-${oldProd.id}`;
      const additionalUoms: CreateProductUomInput[] = [];

      if (oldProd.hasStick10s && oldProd.priceStick10s && oldProd.priceStick10s > 0) {
        additionalUoms.push({
          uomCode: "pack10",
          uomName: "Half-Pack (10s)",
          conversionFactorToBase: 10,
          barcode: oldProd.barcode10s ?? "",
          priceMinor: toMinor(oldProd.priceStick10s)
        });
      }
      if (oldProd.hasStick20s && oldProd.priceStick20s && oldProd.priceStick20s > 0) {
        additionalUoms.push({
          uomCode: "pack20",
          uomName: "Full Pack (20s)",
          conversionFactorToBase: 20,
          barcode: oldProd.barcode20s ?? "",
          priceMinor: toMinor(oldProd.priceStick20s)
        });
      }
      if (oldProd.hasReam && oldProd.priceReam && oldProd.priceReam > 0) {
        additionalUoms.push({
          uomCode: "ream200",
          uomName: "Ream (200s)",
          conversionFactorToBase: 200,
          barcode: oldProd.barcodeReam ?? "",
          priceMinor: toMinor(oldProd.priceReam)
        });
      }
      if (
        oldProd.hasCustomUom &&
        oldProd.customUomMultiplier &&
        oldProd.customUomMultiplier > 1 &&
        oldProd.customUomPrice
      ) {
        additionalUoms.push({
          uomCode: (oldProd.customUomName ?? "pack").toLowerCase().replace(/\s+/g, "_"),
          uomName: oldProd.customUomName ?? "Custom Pack",
          conversionFactorToBase: oldProd.customUomMultiplier,
          barcode: oldProd.barcodeCustomUom ?? "",
          priceMinor: toMinor(oldProd.customUomPrice)
        });
      }

      const unitCostMinor = toMinor(oldProd.cost ?? 0);
      normalizedProducts.push({
        organizationId,
        categoryId: `v1_cat_${oldProd.categoryId}`,
        sku,
        name: oldProd.name,
        baseUomCode: oldProd.baseUom ?? "pc",
        baseUomName: oldProd.baseUom ?? "Piece",
        baseBarcode: oldProd.barcode,
        basePriceMinor: toMinor(oldProd.price),
        initialCostMinor: unitCostMinor,
        additionalUoms
      });

      if (oldProd.stockCount > 0) {
        initialStockMovements.push({
          sku,
          quantityBaseUnits: oldProd.stockCount,
          unitCostMinor
        });
      }
    }

    const floatAccountsToCreate: V1MigrationPlanDto["floatAccountsToCreate"][number][] = [];
    const cfg = backup.storeConfig;

    if ((cfg.gcashBalance ?? 0) > 0) {
      floatAccountsToCreate.push({
        providerCode: "GCASH",
        providerName: "GCash E-Wallet (Migrated from v1)",
        category: "EWALLET",
        initialBalanceMinor: toMinor(cfg.gcashBalance),
        defaultServiceFeeMinor: toMinor(cfg.gcashServiceFee ?? 10),
        commissionBps: 0
      });
    }
    if ((cfg.smartLoadBalance ?? 0) > 0) {
      floatAccountsToCreate.push({
        providerCode: "SMART_LOAD",
        providerName: "Smart / TNT Load (Migrated from v1)",
        category: "AIRTIME_LOAD",
        initialBalanceMinor: toMinor(cfg.smartLoadBalance),
        defaultServiceFeeMinor: toMinor(cfg.loadServiceFee ?? 2),
        commissionBps: 200
      });
    }
    if ((cfg.globeLoadBalance ?? 0) > 0) {
      floatAccountsToCreate.push({
        providerCode: "GLOBE_LOAD",
        providerName: "Globe / TM Load (Migrated from v1)",
        category: "AIRTIME_LOAD",
        initialBalanceMinor: toMinor(cfg.globeLoadBalance),
        defaultServiceFeeMinor: toMinor(cfg.loadServiceFee ?? 2),
        commissionBps: 200
      });
    }

    return Object.freeze({
      organizationId,
      currencyCode,
      normalizedProducts: Object.freeze(normalizedProducts),
      floatAccountsToCreate: Object.freeze(floatAccountsToCreate),
      initialStockMovements: Object.freeze(initialStockMovements)
    });
  }
}
