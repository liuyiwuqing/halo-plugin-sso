import { axiosInstance } from '@halo-dev/api-client'

const consoleBase = '/apis/console.sso.muyin.site/v1alpha1'
const localRoleListPath = '/api/v1alpha1/roles'

export interface SsoClient {
  clientId: string
  displayName: string
  siteUrl: string
  redirectUris: string[]
  enabled?: boolean
  createdAt?: string
  updatedAt?: string
}

export interface CreateSsoClientRequest {
  displayName: string
  siteUrl: string
  redirectUris: string[]
  enabled: boolean
}

export interface CreateSsoClientResponse {
  client: SsoClient
  clientSecret: string
}

export interface UpdateSsoClientRequest {
  clientId: string
  displayName?: string
  siteUrl?: string
  redirectUris?: string[]
  enabled?: boolean
}

export interface SsoRoleMapping {
  centerRole: string
  localRole: string
  enabled?: boolean
  sort?: number
  metadata?: {
    name?: string
  }
}

export interface SsoGeneralRuntimeSetting {
  mode: string
  centerUrl?: string
}

export interface SsoPublicRole {
  name: string
  displayName?: string
  module?: string
  hidden?: boolean
}

export interface HaloRole {
  metadata?: {
    name?: string
    annotations?: Record<string, string>
  }
}

export interface HaloRoleList {
  items?: HaloRole[]
}

export interface RoleOption {
  label: string
  value: string
}

export interface SsoUserBinding {
  subject: string
  email: string
  localUsername: string
  displayName?: string
  avatar?: string
  boundAt?: string
  lastLoginAt?: string
  metadata?: {
    name?: string
  }
}

export interface SsoAuditLog {
  eventType: string
  outcome: string
  clientId?: string
  subject?: string
  email?: string
  message?: string
  ipAddress?: string
  createdAt?: string
  metadata?: {
    name?: string
  }
}

export interface SsoAuditLogQuery {
  outcome?: string
  clientId?: string
  keyword?: string
  page?: number
  size?: number
}

export interface SsoAuditLogPage {
  items: SsoAuditLog[]
  page: number
  size: number
  total: number
  totalPages: number
  hasPrevious: boolean
  hasNext: boolean
}

export interface SsoAuditFailureSummary {
  message: string
  count: number
  lastOccurredAt?: string
  clientIds?: string[]
}

export interface SsoAuditLogCleanupRequest {
  retentionDays: number
  dryRun: boolean
}

export interface SsoAuditLogCleanupResult {
  dryRun: boolean
  retentionDays: number
  cutoffAt?: string
  scanned: number
  matched: number
  deleted: number
  retained: number
}

export interface SsoAuditLogCleanupStatus {
  trigger: 'manual' | 'auto'
  success: boolean
  startedAt?: string
  finishedAt?: string
  message?: string
  result?: SsoAuditLogCleanupResult
}

export interface SsoAuditLogCleanupRecord {
  trigger?: 'manual' | 'auto'
  success?: boolean
  dryRun?: boolean
  startedAt?: string
  finishedAt?: string
  message?: string
  retentionDays?: number
  cutoffAt?: string
  scanned?: number
  matched?: number
  deleted?: number
  retained?: number
  metadata?: {
    name?: string
  }
}

export const ssoClientApi = {
  async list() {
    const { data } = await axiosInstance.get<SsoClient[]>(`${consoleBase}/clients/list`)
    return data
  },

  async create(payload: CreateSsoClientRequest) {
    const { data } = await axiosInstance.post<CreateSsoClientResponse>(
      `${consoleBase}/clients/create`,
      payload,
    )
    return data
  },

  async update(payload: UpdateSsoClientRequest) {
    const { data } = await axiosInstance.post<SsoClient>(`${consoleBase}/clients/update`, payload)
    return data
  },

  async remove(clientId: string) {
    await axiosInstance.delete(`${consoleBase}/clients/${clientId}`)
  },
}

export const ssoSettingApi = {
  async general() {
    const { data } = await axiosInstance.get<SsoGeneralRuntimeSetting>(
      `${consoleBase}/settings/general`,
    )
    return data
  },
}

export const ssoUserBindingApi = {
  async list() {
    const { data } = await axiosInstance.get<SsoUserBinding[]>(`${consoleBase}/user-bindings/list`)
    return data
  },
}

export const ssoRoleOptionApi = {
  async listCenterRoles() {
    const { data } = await axiosInstance.get<SsoPublicRole[]>(
      `${consoleBase}/settings/center-roles`,
    )
    return data.map((role) => toRoleOption(role.name, role.displayName)).filter(hasRoleValue)
  },

  async listLocalRoles() {
    const { data } = await axiosInstance.get<HaloRoleList>(localRoleListPath, {
      params: {
        labelSelector: '!halo.run/role-template',
      },
    })
    return (data.items || [])
      .map((role) =>
        toRoleOption(
          role.metadata?.name || '',
          role.metadata?.annotations?.['rbac.authorization.halo.run/display-name'],
        ),
      )
      .filter(hasRoleValue)
  },
}

export const ssoAuditLogApi = {
  async list(params: SsoAuditLogQuery = {}) {
    const { data } = await axiosInstance.get<SsoAuditLogPage>(`${consoleBase}/audit-logs/list`, {
      params,
    })
    return data
  },

  async recentFailures(limit = 5) {
    const { data } = await axiosInstance.get<SsoAuditFailureSummary[]>(
      `${consoleBase}/audit-logs/recent-failures`,
      {
        params: { limit },
      },
    )
    return data
  },

  async cleanup(payload: SsoAuditLogCleanupRequest) {
    const { data } = await axiosInstance.post<SsoAuditLogCleanupResult>(
      `${consoleBase}/audit-logs/cleanup`,
      payload,
    )
    return data
  },

  async cleanupStatus() {
    const response = await axiosInstance.get<SsoAuditLogCleanupStatus>(
      `${consoleBase}/audit-logs/cleanup-status`,
    )
    return response.status === 204 ? undefined : response.data
  },

  async cleanupRecords(limit = 5) {
    const { data } = await axiosInstance.get<SsoAuditLogCleanupRecord[]>(
      `${consoleBase}/audit-logs/cleanup-records`,
      {
        params: { limit },
      },
    )
    return data
  },
}

export const ssoRoleMappingApi = {
  async list() {
    const { data } = await axiosInstance.get<SsoRoleMapping[]>(`${consoleBase}/role-mappings/list`)
    return data
  },

  async create(payload: SsoRoleMapping) {
    const { data } = await axiosInstance.post<SsoRoleMapping>(
      `${consoleBase}/role-mappings/create`,
      payload,
    )
    return data
  },

  async update(payload: SsoRoleMapping) {
    const { data } = await axiosInstance.post<SsoRoleMapping>(
      `${consoleBase}/role-mappings/update`,
      payload,
    )
    return data
  },
}

function toRoleOption(value: string, displayName?: string) {
  const normalizedValue = value.trim()
  const normalizedDisplayName = displayName?.trim()
  return {
    label:
      normalizedDisplayName && normalizedDisplayName !== normalizedValue
        ? `${normalizedDisplayName}（${normalizedValue}）`
        : normalizedValue,
    value: normalizedValue,
  }
}

function hasRoleValue(option: RoleOption) {
  return Boolean(option.value)
}
