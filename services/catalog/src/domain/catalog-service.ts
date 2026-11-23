import type {
  BarcodeLookupResultDto,
  CategoryDto,
  CreateCategoryInput,
  CreateProductInput,
  CreateTaxGroupInput,
  ProductDto,
  ProductUomDto,
  TaxGroupDto
} from "@storepoint/contracts";
import { type EventBus, createDomainEvent } from "@storepoint/service-kit";
import { randomUUID } from "node:crypto";
import type { CatalogRepository } from "../repositories/catalog-repository.js";

export class CatalogDomainError extends Error {
  readonly code: string;
  readonly statusCode: number;

  constructor(code: string, message: string, statusCode = 400) {
    super(message);
    this.code = code;
    this.statusCode = statusCode;
  }
}

export class CatalogDomainService {
  private readonly repo: CatalogRepository;
  private readonly eventBus: EventBus;

  constructor(repo: CatalogRepository, eventBus: EventBus) {
    this.repo = repo;
    this.eventBus = eventBus;
  }

  async createCategory(input: CreateCategoryInput): Promise<CategoryDto> {
    if (!input.name.trim()) {
      throw new CatalogDomainError("ERR_CATEGORY_NAME", "Category name is required");
    }
    return this.repo.saveCategory({
      id: `cat_${randomUUID()}`,
      organizationId: input.organizationId,
      name: input.name.trim(),
      colorAccent: input.colorAccent ?? "#006C4C",
      iconBadge: input.iconBadge ?? "category",
      isArchived: false,
      createdAt: new Date().toISOString()
    });
  }

  async listCategories(organizationId: string): Promise<readonly CategoryDto[]> {
    return this.repo.listCategories(organizationId);
  }

  async createTaxGroup(input: CreateTaxGroupInput): Promise<TaxGroupDto> {
    if (!input.name.trim()) {
      throw new CatalogDomainError("ERR_TAX_GROUP_NAME", "Tax group name is required");
    }
    return this.repo.saveTaxGroup({
      id: `tax_${randomUUID()}`,
      organizationId: input.organizationId,
      name: input.name.trim(),
      countryCode: input.countryCode.trim().toUpperCase() || "GLOBAL",
      rules: input.rules,
      isDefault: input.isDefault ?? false
    });
  }

  async seedCountryTaxPreset(
    organizationId: string,
    countryCode: "PH" | "IN" | "US" | "EU"
  ): Promise<readonly TaxGroupDto[]> {
    const presets: Record<"PH" | "IN" | "US" | "EU", CreateTaxGroupInput[]> = {
      PH: [
        {
          organizationId,
          name: "PH 12% VAT (Inclusive)",
          countryCode: "PH",
          isDefault: true,
          rules: [{ id: "ph_vat_12", name: "12% VAT", rateBps: 1200, mode: "INCLUSIVE" }]
        },
        {
          organizationId,
          name: "PH VAT-Exempt (Basic Necessities)",
          countryCode: "PH",
          rules: [{ id: "ph_exempt", name: "VAT Exempt", rateBps: 0, mode: "EXEMPT" }]
        }
      ],
      IN: [
        {
          organizationId,
          name: "India GST 18% (9% CGST + 9% SGST)",
          countryCode: "IN",
          isDefault: true,
          rules: [
            { id: "in_cgst_9", name: "CGST 9%", rateBps: 900, mode: "EXCLUSIVE" },
            { id: "in_sgst_9", name: "SGST 9%", rateBps: 900, mode: "EXCLUSIVE" }
          ]
        }
      ],
      US: [
        {
          organizationId,
          name: "US Standard Sales Tax (8.25%)",
          countryCode: "US",
          isDefault: true,
          rules: [{ id: "us_sales_825", name: "Sales Tax 8.25%", rateBps: 825, mode: "EXCLUSIVE" }]
        }
      ],
      EU: [
        {
          organizationId,
          name: "EU Standard VAT (20% Inclusive)",
          countryCode: "EU",
          isDefault: true,
          rules: [{ id: "eu_vat_20", name: "20% VAT", rateBps: 2000, mode: "INCLUSIVE" }]
        }
      ]
    };

    const created: TaxGroupDto[] = [];
    for (const preset of presets[countryCode]) {
      created.push(await this.createTaxGroup(preset));
    }
    return created;
  }

  async createProduct(input: CreateProductInput): Promise<ProductDto> {
    if (!input.name.trim()) {
      throw new CatalogDomainError("ERR_PRODUCT_NAME", "Product name is required");
    }
    if (input.basePriceMinor < 0) {
      throw new CatalogDomainError("ERR_NEGATIVE_PRICE", "Base price cannot be negative");
    }

    const productId = `prd_${randomUUID()}`;
    const baseBarcode = input.baseBarcode.trim();
    if (baseBarcode) {
      const clash = await this.repo.findByBarcode(input.organizationId, baseBarcode);
      if (clash) {
        throw new CatalogDomainError(
          "ERR_DUPLICATE_BARCODE",
          `Barcode ${baseBarcode} is already assigned to ${clash.product.name}`,
          409
        );
      }
    }

    const baseUom: ProductUomDto = {
      id: `uom_${randomUUID()}`,
      productId,
      uomCode: input.baseUomCode.trim() || "pc",
      uomName: input.baseUomName.trim() || "Piece",
      conversionFactorToBase: 1,
      barcode: baseBarcode,
      priceMinor: input.basePriceMinor,
      isBase: true
    };

    const extraUoms: ProductUomDto[] = [];
    for (const extra of input.additionalUoms ?? []) {
      if (extra.conversionFactorToBase <= 0) {
        throw new CatalogDomainError(
          "ERR_INVALID_UOM_FACTOR",
          "UOM conversion factor must be positive"
        );
      }
      const extraBarcode = extra.barcode.trim();
      if (extraBarcode) {
        const clash = await this.repo.findByBarcode(input.organizationId, extraBarcode);
        if (clash || extraBarcode === baseBarcode) {
          throw new CatalogDomainError(
            "ERR_DUPLICATE_BARCODE",
            `UOM barcode ${extraBarcode} conflicts with an existing barcode`,
            409
          );
        }
      }
      extraUoms.push({
        id: `uom_${randomUUID()}`,
        productId,
        uomCode: extra.uomCode.trim(),
        uomName: extra.uomName.trim(),
        conversionFactorToBase: extra.conversionFactorToBase,
        barcode: extraBarcode,
        priceMinor: extra.priceMinor,
        isBase: false
      });
    }

    const product = await this.repo.saveProduct({
      id: productId,
      organizationId: input.organizationId,
      categoryId: input.categoryId,
      taxGroupId: input.taxGroupId ?? null,
      sku: input.sku.trim() || `SKU-${productId.slice(4, 12).toUpperCase()}`,
      name: input.name.trim(),
      description: input.description?.trim() ?? "",
      baseUomCode: baseUom.uomCode,
      baseUomName: baseUom.uomName,
      baseBarcode,
      basePriceMinor: input.basePriceMinor,
      initialCostMinor: input.initialCostMinor ?? 0,
      trackInventory: input.trackInventory ?? true,
      trackExpiry: input.trackExpiry ?? false,
      isArchived: false,
      uoms: [baseUom, ...extraUoms],
      createdAt: new Date().toISOString()
    });

    await this.eventBus.publish(
      createDomainEvent({
        eventType: "catalog.product.upserted",
        organizationId: input.organizationId,
        payload: {
          productId: product.id,
          sku: product.sku,
          uomCount: product.uoms.length
        }
      })
    );

    return product;
  }

  async listProducts(organizationId: string, query?: string): Promise<readonly ProductDto[]> {
    return this.repo.listProducts(organizationId, query);
  }

  async lookupBarcode(
    organizationId: string,
    barcode: string
  ): Promise<BarcodeLookupResultDto> {
    const result = await this.repo.findByBarcode(organizationId, barcode);
    if (!result) {
      throw new CatalogDomainError(
        "ERR_BARCODE_NOT_FOUND",
        `No product or UOM matched barcode ${barcode}`,
        404
      );
    }
    return result;
  }
}
