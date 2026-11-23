import type {
  BarcodeLookupResultDto,
  CategoryDto,
  ProductDto,
  TaxGroupDto
} from "@storepoint/contracts";

export interface CatalogRepository {
  saveCategory(category: CategoryDto): Promise<CategoryDto>;
  listCategories(organizationId: string): Promise<readonly CategoryDto[]>;
  saveTaxGroup(taxGroup: TaxGroupDto): Promise<TaxGroupDto>;
  listTaxGroups(organizationId: string): Promise<readonly TaxGroupDto[]>;
  saveProduct(product: ProductDto): Promise<ProductDto>;
  getProductById(organizationId: string, productId: string): Promise<ProductDto | null>;
  listProducts(organizationId: string, query?: string): Promise<readonly ProductDto[]>;
  findByBarcode(organizationId: string, barcode: string): Promise<BarcodeLookupResultDto | null>;
}

/**
 * Immutable repository for the `catalog` service (`catalog.*` PostgreSQL schema boundary).
 */
export class InMemoryCatalogRepository implements CatalogRepository {
  private readonly categories = new Map<string, CategoryDto>();
  private readonly taxGroups = new Map<string, TaxGroupDto>();
  private readonly products = new Map<string, ProductDto>();

  async saveCategory(category: CategoryDto): Promise<CategoryDto> {
    const frozen = Object.freeze({ ...category });
    this.categories.set(frozen.id, frozen);
    return frozen;
  }

  async listCategories(organizationId: string): Promise<readonly CategoryDto[]> {
    return [...this.categories.values()].filter((c) => c.organizationId === organizationId);
  }

  async saveTaxGroup(taxGroup: TaxGroupDto): Promise<TaxGroupDto> {
    const frozen = Object.freeze({
      ...taxGroup,
      rules: Object.freeze(taxGroup.rules.map((r) => Object.freeze({ ...r })))
    });
    this.taxGroups.set(frozen.id, frozen);
    return frozen;
  }

  async listTaxGroups(organizationId: string): Promise<readonly TaxGroupDto[]> {
    return [...this.taxGroups.values()].filter((t) => t.organizationId === organizationId);
  }

  async saveProduct(product: ProductDto): Promise<ProductDto> {
    const frozen = Object.freeze({
      ...product,
      uoms: Object.freeze(product.uoms.map((u) => Object.freeze({ ...u })))
    });
    this.products.set(frozen.id, frozen);
    return frozen;
  }

  async getProductById(organizationId: string, productId: string): Promise<ProductDto | null> {
    const found = this.products.get(productId);
    if (!found || found.organizationId !== organizationId) return null;
    return found;
  }

  async listProducts(organizationId: string, query?: string): Promise<readonly ProductDto[]> {
    const normalizedQuery = query?.trim().toLowerCase() ?? "";
    return [...this.products.values()]
      .filter((p) => {
        if (p.organizationId !== organizationId || p.isArchived) return false;
        if (!normalizedQuery) return true;
        return (
          p.name.toLowerCase().includes(normalizedQuery) ||
          p.sku.toLowerCase().includes(normalizedQuery) ||
          p.uoms.some((u) => u.barcode.toLowerCase() === normalizedQuery)
        );
      })
      .sort((a, b) => a.name.localeCompare(b.name));
  }

  async findByBarcode(
    organizationId: string,
    barcode: string
  ): Promise<BarcodeLookupResultDto | null> {
    const target = barcode.trim();
    if (!target) return null;
    for (const product of this.products.values()) {
      if (product.organizationId !== organizationId || product.isArchived) continue;
      const matchedUom = product.uoms.find((u) => u.barcode === target);
      if (matchedUom) {
        return Object.freeze({ product, matchedUom });
      }
    }
    return null;
  }
}
