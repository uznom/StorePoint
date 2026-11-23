export interface TaxRuleDto {
  readonly id: string;
  readonly name: string;
  readonly rateBps: number;
  readonly mode: "INCLUSIVE" | "EXCLUSIVE" | "EXEMPT";
  readonly compound?: boolean;
}

export interface CategoryDto {
  readonly id: string;
  readonly organizationId: string;
  readonly name: string;
  readonly colorAccent: string;
  readonly iconBadge: string;
  readonly isArchived: boolean;
  readonly createdAt: string;
}

export interface TaxGroupDto {
  readonly id: string;
  readonly organizationId: string;
  readonly name: string;
  readonly countryCode: string;
  readonly rules: readonly TaxRuleDto[];
  readonly isDefault: boolean;
}

/**
 * Normalized Multi-UOM definition replacing StorePoint v1's hardcoded cigarette/pack columns.
 * Any product can have unlimited UOMs (e.g. Single Piece = 1, 6-Pack = 6, Master Carton = 72,
 * or 250g = 250 base grams) each with its own barcode and selling price.
 */
export interface ProductUomDto {
  readonly id: string;
  readonly productId: string;
  readonly uomCode: string;
  readonly uomName: string;
  /** Number of base inventory units deducted when 1 of this UOM is sold/moved */
  readonly conversionFactorToBase: number;
  readonly barcode: string;
  readonly priceMinor: number;
  readonly isBase: boolean;
}

export interface ProductDto {
  readonly id: string;
  readonly organizationId: string;
  readonly categoryId: string;
  readonly taxGroupId: string | null;
  readonly sku: string;
  readonly name: string;
  readonly description: string;
  readonly baseUomCode: string;
  readonly baseUomName: string;
  readonly baseBarcode: string;
  readonly basePriceMinor: number;
  readonly initialCostMinor: number;
  readonly trackInventory: boolean;
  readonly trackExpiry: boolean;
  readonly isArchived: boolean;
  readonly uoms: readonly ProductUomDto[];
  readonly createdAt: string;
}

export interface CreateCategoryInput {
  readonly organizationId: string;
  readonly name: string;
  readonly colorAccent?: string;
  readonly iconBadge?: string;
}

export interface CreateTaxGroupInput {
  readonly organizationId: string;
  readonly name: string;
  readonly countryCode: string;
  readonly rules: readonly TaxRuleDto[];
  readonly isDefault?: boolean;
}

export interface CreateProductUomInput {
  readonly uomCode: string;
  readonly uomName: string;
  readonly conversionFactorToBase: number;
  readonly barcode: string;
  readonly priceMinor: number;
}

export interface CreateProductInput {
  readonly organizationId: string;
  readonly categoryId: string;
  readonly taxGroupId?: string | null;
  readonly sku: string;
  readonly name: string;
  readonly description?: string;
  readonly baseUomCode: string;
  readonly baseUomName: string;
  readonly baseBarcode: string;
  readonly basePriceMinor: number;
  readonly initialCostMinor?: number;
  readonly trackInventory?: boolean;
  readonly trackExpiry?: boolean;
  readonly additionalUoms?: readonly CreateProductUomInput[];
}

export interface BarcodeLookupResultDto {
  readonly product: ProductDto;
  readonly matchedUom: ProductUomDto;
}
