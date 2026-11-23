export type UserRole = "OWNER" | "MANAGER" | "CASHIER" | "INVENTORY_CLERK";

export type StoreTemplateType =
  | "GROCERY_MINIMART"
  | "PHARMACY"
  | "HARDWARE"
  | "APPAREL"
  | "CAFE_BAKERY"
  | "GENERAL_RETAIL";

export interface OrganizationDto {
  readonly id: string;
  readonly name: string;
  readonly defaultCurrencyCode: string;
  readonly locale: string;
  readonly timezone: string;
  readonly templateType: StoreTemplateType;
  readonly enabledCountryPacks: readonly string[];
  readonly createdAt: string;
}

export interface BranchDto {
  readonly id: string;
  readonly organizationId: string;
  readonly code: string;
  readonly name: string;
  readonly address: string;
  readonly taxIdentificationNumber: string;
  readonly isDefault: boolean;
  readonly createdAt: string;
}

export interface RegisterDto {
  readonly id: string;
  readonly organizationId: string;
  readonly branchId: string;
  readonly name: string;
  readonly thermalPaperWidthMm: 58 | 80;
  readonly isActive: boolean;
  readonly createdAt: string;
}

export interface UserDto {
  readonly id: string;
  readonly organizationId: string;
  readonly branchId: string | null;
  readonly username: string;
  readonly displayName: string;
  readonly role: UserRole;
  readonly badgeBarcode: string | null;
  readonly isActive: boolean;
  readonly createdAt: string;
}

export interface AuditLogEntryDto {
  readonly id: string;
  readonly organizationId: string;
  readonly actorUserId: string;
  readonly action: string;
  readonly targetType: string;
  readonly targetId: string;
  readonly metadata: Readonly<Record<string, unknown>>;
  readonly occurredAt: string;
}

export interface CreateOrganizationInput {
  readonly name: string;
  readonly defaultCurrencyCode: string;
  readonly locale: string;
  readonly timezone: string;
  readonly templateType: StoreTemplateType;
  readonly ownerUsername: string;
  readonly ownerDisplayName: string;
  readonly ownerPin: string;
  readonly initialBranchName?: string;
}

export interface CreateUserInput {
  readonly organizationId: string;
  readonly branchId?: string | null;
  readonly actorUserId: string;
  readonly username: string;
  readonly displayName: string;
  readonly role: UserRole;
  readonly pin: string;
  readonly badgeBarcode?: string | null;
}

export interface PinAuthInput {
  readonly organizationId: string;
  readonly username: string;
  readonly pin: string;
}
