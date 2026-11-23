import type {
  AuditLogEntryDto,
  BranchDto,
  OrganizationDto,
  RegisterDto,
  UserDto
} from "@storepoint/contracts";

export interface StoredUserRecord extends UserDto {
  readonly pinHash: string;
}

export interface IdentityRepository {
  saveOrganization(org: OrganizationDto): Promise<OrganizationDto>;
  getOrganizationById(id: string): Promise<OrganizationDto | null>;
  saveBranch(branch: BranchDto): Promise<BranchDto>;
  listBranchesByOrg(organizationId: string): Promise<readonly BranchDto[]>;
  saveRegister(register: RegisterDto): Promise<RegisterDto>;
  listRegistersByBranch(branchId: string): Promise<readonly RegisterDto[]>;
  saveUser(user: StoredUserRecord): Promise<UserDto>;
  getUserById(userId: string): Promise<StoredUserRecord | null>;
  getUserByUsername(organizationId: string, username: string): Promise<StoredUserRecord | null>;
  listUsersByOrg(organizationId: string): Promise<readonly UserDto[]>;
  appendAuditLog(entry: AuditLogEntryDto): Promise<AuditLogEntryDto>;
  listAuditLogs(organizationId: string): Promise<readonly AuditLogEntryDto[]>;
}

function stripPinHash(record: StoredUserRecord): UserDto {
  const { pinHash: _unused, ...publicUser } = record;
  return Object.freeze(publicUser);
}

/**
 * Immutable in-memory repository implementing the `identity` service schema contract.
 * Ready to swap or wrap with Drizzle PostgreSQL (`identity.*` schema) adapter.
 */
export class InMemoryIdentityRepository implements IdentityRepository {
  private readonly orgs = new Map<string, OrganizationDto>();
  private readonly branches = new Map<string, BranchDto>();
  private readonly registers = new Map<string, RegisterDto>();
  private readonly users = new Map<string, StoredUserRecord>();
  private readonly auditLogs: AuditLogEntryDto[] = [];

  async saveOrganization(org: OrganizationDto): Promise<OrganizationDto> {
    const frozen = Object.freeze({ ...org, enabledCountryPacks: [...org.enabledCountryPacks] });
    this.orgs.set(frozen.id, frozen);
    return frozen;
  }

  async getOrganizationById(id: string): Promise<OrganizationDto | null> {
    return this.orgs.get(id) ?? null;
  }

  async saveBranch(branch: BranchDto): Promise<BranchDto> {
    const frozen = Object.freeze({ ...branch });
    this.branches.set(frozen.id, frozen);
    return frozen;
  }

  async listBranchesByOrg(organizationId: string): Promise<readonly BranchDto[]> {
    return [...this.branches.values()].filter((b) => b.organizationId === organizationId);
  }

  async saveRegister(register: RegisterDto): Promise<RegisterDto> {
    const frozen = Object.freeze({ ...register });
    this.registers.set(frozen.id, frozen);
    return frozen;
  }

  async listRegistersByBranch(branchId: string): Promise<readonly RegisterDto[]> {
    return [...this.registers.values()].filter((r) => r.branchId === branchId);
  }

  async saveUser(user: StoredUserRecord): Promise<UserDto> {
    const frozen = Object.freeze({ ...user });
    this.users.set(frozen.id, frozen);
    return stripPinHash(frozen);
  }

  async getUserById(userId: string): Promise<StoredUserRecord | null> {
    return this.users.get(userId) ?? null;
  }

  async getUserByUsername(
    organizationId: string,
    username: string
  ): Promise<StoredUserRecord | null> {
    const normalized = username.trim().toLowerCase();
    for (const user of this.users.values()) {
      if (
        user.organizationId === organizationId &&
        user.username.toLowerCase() === normalized
      ) {
        return user;
      }
    }
    return null;
  }

  async listUsersByOrg(organizationId: string): Promise<readonly UserDto[]> {
    return [...this.users.values()]
      .filter((u) => u.organizationId === organizationId)
      .map(stripPinHash);
  }

  async appendAuditLog(entry: AuditLogEntryDto): Promise<AuditLogEntryDto> {
    const frozen = Object.freeze({ ...entry, metadata: Object.freeze({ ...entry.metadata }) });
    this.auditLogs.push(frozen);
    return frozen;
  }

  async listAuditLogs(organizationId: string): Promise<readonly AuditLogEntryDto[]> {
    return this.auditLogs.filter((a) => a.organizationId === organizationId);
  }
}
