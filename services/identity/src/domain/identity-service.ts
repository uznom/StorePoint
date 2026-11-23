import type {
  AuditLogEntryDto,
  BranchDto,
  CreateOrganizationInput,
  CreateUserInput,
  OrganizationDto,
  PinAuthInput,
  RegisterDto,
  UserDto
} from "@storepoint/contracts";
import { getCurrency } from "@storepoint/money";
import {
  type EventBus,
  ProgressiveLockoutTracker,
  createDomainEvent,
  hashSecret,
  validatePinStrength,
  verifySecret
} from "@storepoint/service-kit";
import { randomUUID } from "node:crypto";
import type { IdentityRepository } from "../repositories/identity-repository.js";

export interface OnboardingResult {
  readonly organization: OrganizationDto;
  readonly defaultBranch: BranchDto;
  readonly defaultRegister: RegisterDto;
  readonly owner: UserDto;
}

export class IdentityDomainError extends Error {
  readonly code: string;
  readonly statusCode: number;

  constructor(code: string, message: string, statusCode = 400) {
    super(message);
    this.code = code;
    this.statusCode = statusCode;
  }
}

export class IdentityDomainService {
  private readonly repo: IdentityRepository;
  private readonly eventBus: EventBus;
  private readonly lockout: ProgressiveLockoutTracker;

  constructor(
    repo: IdentityRepository,
    eventBus: EventBus,
    lockout = new ProgressiveLockoutTracker()
  ) {
    this.repo = repo;
    this.eventBus = eventBus;
    this.lockout = lockout;
  }

  async onboardOrganization(input: CreateOrganizationInput): Promise<OnboardingResult> {
    if (!input.name.trim()) {
      throw new IdentityDomainError("ERR_INVALID_ORG_NAME", "Organization name is required");
    }
    const currency = getCurrency(input.defaultCurrencyCode);
    const pinCheck = validatePinStrength(input.ownerPin);
    if (!pinCheck.valid) {
      throw new IdentityDomainError("ERR_WEAK_PIN", pinCheck.reason ?? "Invalid PIN");
    }

    const now = new Date().toISOString();
    const orgId = `org_${randomUUID()}`;
    const branchId = `br_${randomUUID()}`;
    const registerId = `reg_${randomUUID()}`;
    const ownerId = `usr_${randomUUID()}`;

    const organization = await this.repo.saveOrganization({
      id: orgId,
      name: input.name.trim(),
      defaultCurrencyCode: currency.code,
      locale: input.locale.trim() || "en-US",
      timezone: input.timezone.trim() || "UTC",
      templateType: input.templateType,
      enabledCountryPacks: currency.code === "PHP" ? ["pack-ph-retail"] : [],
      createdAt: now
    });

    const defaultBranch = await this.repo.saveBranch({
      id: branchId,
      organizationId: orgId,
      code: "MAIN",
      name: input.initialBranchName?.trim() || "Main Store",
      address: "",
      taxIdentificationNumber: "",
      isDefault: true,
      createdAt: now
    });

    const defaultRegister = await this.repo.saveRegister({
      id: registerId,
      organizationId: orgId,
      branchId,
      name: "Register 1",
      thermalPaperWidthMm: 58,
      isActive: true,
      createdAt: now
    });

    const owner = await this.repo.saveUser({
      id: ownerId,
      organizationId: orgId,
      branchId,
      username: input.ownerUsername.trim(),
      displayName: input.ownerDisplayName.trim(),
      role: "OWNER",
      badgeBarcode: null,
      isActive: true,
      pinHash: hashSecret(input.ownerPin),
      createdAt: now
    });

    await this.recordAudit(orgId, ownerId, "ORGANIZATION_ONBOARDED", "organization", orgId, {
      currencyCode: currency.code,
      templateType: input.templateType
    });

    await this.eventBus.publish(
      createDomainEvent({
        eventType: "identity.organization.created",
        organizationId: orgId,
        branchId,
        payload: { organizationId: orgId, defaultBranchId: branchId, ownerId }
      })
    );

    return { organization, defaultBranch, defaultRegister, owner };
  }

  async createUser(input: CreateUserInput): Promise<UserDto> {
    const actor = await this.repo.getUserById(input.actorUserId);
    if (!actor || actor.organizationId !== input.organizationId) {
      throw new IdentityDomainError("ERR_FORBIDDEN", "Actor not found in organization", 403);
    }
    if (actor.role !== "OWNER" && actor.role !== "MANAGER") {
      throw new IdentityDomainError(
        "ERR_FORBIDDEN",
        "Only OWNER or MANAGER roles can create staff accounts",
        403
      );
    }

    const existing = await this.repo.getUserByUsername(input.organizationId, input.username);
    if (existing) {
      throw new IdentityDomainError("ERR_DUPLICATE_USER", "Username already exists in store", 409);
    }

    const pinCheck = validatePinStrength(input.pin);
    if (!pinCheck.valid) {
      throw new IdentityDomainError("ERR_WEAK_PIN", pinCheck.reason ?? "Weak PIN");
    }

    const userId = `usr_${randomUUID()}`;
    const created = await this.repo.saveUser({
      id: userId,
      organizationId: input.organizationId,
      branchId: input.branchId ?? actor.branchId,
      username: input.username.trim(),
      displayName: input.displayName.trim(),
      role: input.role,
      badgeBarcode: input.badgeBarcode ?? null,
      isActive: true,
      pinHash: hashSecret(input.pin),
      createdAt: new Date().toISOString()
    });

    await this.recordAudit(
      input.organizationId,
      actor.id,
      "USER_CREATED",
      "user",
      created.id,
      { username: created.username, role: created.role }
    );

    await this.eventBus.publish(
      createDomainEvent({
        eventType: "identity.user.created",
        organizationId: input.organizationId,
        branchId: created.branchId,
        payload: { userId: created.id, role: created.role }
      })
    );

    return created;
  }

  async authenticateWithPin(input: PinAuthInput, nowMs = Date.now()): Promise<UserDto> {
    const accountKey = `${input.organizationId}:${input.username.trim().toLowerCase()}`;
    const lockState = this.lockout.isLocked(accountKey, nowMs);
    if (lockState.locked) {
      throw new IdentityDomainError(
        "ERR_ACCOUNT_LOCKED",
        `Account temporarily locked. Retry in ${Math.ceil(lockState.retryAfterMs / 1000)}s`,
        429
      );
    }

    const user = await this.repo.getUserByUsername(input.organizationId, input.username);
    if (!user || !user.isActive || !verifySecret(input.pin, user.pinHash)) {
      this.lockout.recordFailure(accountKey, nowMs);
      throw new IdentityDomainError("ERR_INVALID_CREDENTIALS", "Invalid username or PIN", 401);
    }

    this.lockout.recordSuccess(accountKey);
    await this.recordAudit(input.organizationId, user.id, "USER_PIN_LOGIN", "user", user.id, {
      username: user.username
    });

    const { pinHash: _unused, ...publicUser } = user;
    return Object.freeze(publicUser);
  }

  async listAuditLogs(organizationId: string): Promise<readonly AuditLogEntryDto[]> {
    return this.repo.listAuditLogs(organizationId);
  }

  private async recordAudit(
    organizationId: string,
    actorUserId: string,
    action: string,
    targetType: string,
    targetId: string,
    metadata: Readonly<Record<string, unknown>>
  ): Promise<void> {
    await this.repo.appendAuditLog({
      id: `aud_${randomUUID()}`,
      organizationId,
      actorUserId,
      action,
      targetType,
      targetId,
      metadata,
      occurredAt: new Date().toISOString()
    });
  }
}
