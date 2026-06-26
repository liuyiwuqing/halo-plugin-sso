<script setup lang="ts">
import { Toast, VButton, VEmpty, VLoading } from '@halo-dev/components'
import { utils } from '@halo-dev/ui-shared'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ssoAuditLogApi,
  ssoClientApi,
  ssoRoleOptionApi,
  ssoRoleMappingApi,
  ssoSettingApi,
  ssoUserBindingApi,
  type RoleOption,
  type SsoAuditFailureSummary,
  type SsoAuditLog,
  type SsoAuditLogCleanupRecord,
  type SsoAuditLogCleanupResult,
  type SsoAuditLogCleanupStatus,
  type SsoClient,
  type SsoGeneralRuntimeSetting,
  type SsoRoleMapping,
  type SsoUserBinding,
} from '@/api'
import {
  getAdminTabs,
  getDefaultAdminTab,
  getModeLabel,
  type SsoAdminTabKey,
} from '@/sso/admin-mode'
import { buildClientCallbackUri, isAllowedSsoUrl, normalizeSiteUrl } from '@/sso/callback-uri'

const runtimeSetting = ref<SsoGeneralRuntimeSetting>({ mode: 'center' })
const clients = ref<SsoClient[]>([])
const roleMappings = ref<SsoRoleMapping[]>([])
const centerRoleOptions = ref<RoleOption[]>([])
const localRoleOptions = ref<RoleOption[]>([])
const userBindings = ref<SsoUserBinding[]>([])
const auditLogs = ref<SsoAuditLog[]>([])
const auditFailureSummaries = ref<SsoAuditFailureSummary[]>([])
const auditCleanupPreview = ref<SsoAuditLogCleanupResult>()
const auditCleanupStatus = ref<SsoAuditLogCleanupStatus>()
const auditCleanupRecords = ref<SsoAuditLogCleanupRecord[]>([])
const runtimeSettingLoading = ref(false)
const loading = ref(false)
const roleMappingsLoading = ref(false)
const centerRoleOptionsLoading = ref(false)
const localRoleOptionsLoading = ref(false)
const userBindingsLoading = ref(false)
const auditLogsLoading = ref(false)
const auditFailureSummariesLoading = ref(false)
const auditCleanupLoading = ref(false)
const auditCleanupStatusLoading = ref(false)
const auditCleanupRecordsLoading = ref(false)
const savingClient = ref(false)
const savingRoleMapping = ref(false)
const createdSecret = ref('')
const createdClientId = ref('')
const editingClientId = ref('')
const editingRoleMappingCenterRole = ref('')
const lastAutoFilledClientCallbackUri = ref('')
const centerRoleOptionsError = ref('')
const localRoleOptionsError = ref('')
const auditLogTotal = ref(0)
const auditLogTotalPages = ref(1)
const runtimeSettingError = ref('')
const activeTab = ref<SsoAdminTabKey>('clients')

const form = reactive({
  clientId: '',
  displayName: '',
  siteUrl: '',
  redirectUrisText: '',
  enabled: true,
})

const roleMappingForm = reactive({
  centerRole: '',
  localRole: '',
  sort: 0,
  enabled: true,
})

const auditLogFilters = reactive({
  outcome: '',
  clientId: '',
  keyword: '',
  page: 1,
  size: 10,
})

const auditCleanupForm = reactive({
  retentionDays: 90,
})

const enabledClients = computed(
  () => clients.value.filter((client) => client.enabled !== false).length,
)
const enabledRoleMappings = computed(
  () => roleMappings.value.filter((mapping) => mapping.enabled !== false).length,
)
const successfulAuditLogs = computed(
  () => auditLogs.value.filter((log) => log.outcome === 'success').length,
)
const currentMode = computed(() => (runtimeSetting.value.mode === 'client' ? 'client' : 'center'))
const currentModeLabel = computed(() => getModeLabel(currentMode.value))
const currentModeDescription = computed(() =>
  currentMode.value === 'client'
    ? '当前站点作为接入站工作，负责跳转身份中心、绑定本站用户、映射本地角色。'
    : '当前站点作为身份中心工作，负责维护可信接入站、签发登录授权和下发中心标准角色。',
)
const isCenterMode = computed(() => currentMode.value === 'center')
const isClientMode = computed(() => currentMode.value === 'client')
const adminTabs = computed(() => getAdminTabs(currentMode.value))
const visibleStats = computed(() => {
  if (isClientMode.value) {
    return [
      `${roleMappings.value.length} 条角色映射`,
      `${enabledRoleMappings.value} 条生效`,
      `${userBindings.value.length} 个绑定用户`,
      `${auditLogTotal.value} 条审计日志`,
    ]
  }
  return [
    `${clients.value.length} 个接入站`,
    `${enabledClients.value} 个启用`,
    `${auditLogTotal.value} 条审计日志`,
  ]
})
const clientFormTitle = computed(() => (editingClientId.value ? '编辑接入站' : '新增接入站'))
const clientSubmitLabel = computed(() => (editingClientId.value ? '保存接入站' : '创建接入站'))
const roleMappingFormTitle = computed(() =>
  editingRoleMappingCenterRole.value ? '编辑角色映射' : '新增角色映射',
)
const roleMappingOptionsLoading = computed(
  () => centerRoleOptionsLoading.value || localRoleOptionsLoading.value,
)
const recommendedClientCallbackUri = computed(() => buildClientCallbackUri(form.siteUrl))
const canUseRecommendedClientCallbackUri = computed(
  () => Boolean(recommendedClientCallbackUri.value) && isAllowedSsoUrl(form.siteUrl),
)
const redirectUrisContainRecommendation = computed(() =>
  parseRedirectUris().includes(recommendedClientCallbackUri.value),
)
const centerRoleSelectOptions = computed(() =>
  withCurrentRoleOption(centerRoleOptions.value, roleMappingForm.centerRole),
)
const localRoleSelectOptions = computed(() =>
  withCurrentRoleOption(localRoleOptions.value, roleMappingForm.localRole),
)
const hasAuditFilters = computed(
  () =>
    Boolean(auditLogFilters.outcome) ||
    Boolean(auditLogFilters.clientId.trim()) ||
    Boolean(auditLogFilters.keyword.trim()),
)

async function loadRuntimeSetting() {
  runtimeSettingLoading.value = true
  runtimeSettingError.value = ''
  try {
    runtimeSetting.value = await ssoSettingApi.general()
    activeTab.value = getDefaultAdminTab(currentMode.value)
  } catch (error) {
    runtimeSettingError.value = '运行模式加载失败，已按身份中心模式展示'
    runtimeSetting.value = { mode: 'center' }
    activeTab.value = getDefaultAdminTab(currentMode.value)
    Toast.error('运行模式加载失败')
    console.error(error)
  } finally {
    runtimeSettingLoading.value = false
  }
}

function selectTab(tab: SsoAdminTabKey) {
  activeTab.value = tab
  void loadActiveTabData(tab)
}

async function loadActiveTabData(tab = activeTab.value) {
  if (tab === 'clients' && isCenterMode.value) {
    await loadClients()
    return
  }
  if (tab === 'roleMappings' && isClientMode.value) {
    await refreshRoleMappingArea()
    return
  }
  if (tab === 'userBindings' && isClientMode.value) {
    await loadUserBindings()
    return
  }
  if (tab === 'audit') {
    await refreshAuditArea()
  }
}

async function loadVisibleAreas() {
  if (isClientMode.value) {
    await Promise.all([refreshRoleMappingArea(), loadUserBindings(), refreshAuditArea()])
    return
  }
  await Promise.all([loadClients(), refreshAuditArea()])
}

function parseRedirectUris() {
  return form.redirectUrisText
    .split('\n')
    .map((uri) => uri.trim())
    .filter(Boolean)
}

function resetClientForm() {
  editingClientId.value = ''
  createdSecret.value = ''
  createdClientId.value = ''
  lastAutoFilledClientCallbackUri.value = ''
  form.clientId = ''
  form.displayName = ''
  form.siteUrl = ''
  form.redirectUrisText = ''
  form.enabled = true
}

function editClient(client: SsoClient) {
  editingClientId.value = client.clientId
  createdSecret.value = ''
  createdClientId.value = ''
  lastAutoFilledClientCallbackUri.value = ''
  form.clientId = client.clientId
  form.displayName = client.displayName
  form.siteUrl = client.siteUrl
  form.redirectUrisText = (client.redirectUris || []).join('\n')
  form.enabled = client.enabled !== false
}

function fillRecommendedClientCallbackUri() {
  const callbackUri = recommendedClientCallbackUri.value
  if (!callbackUri || !canUseRecommendedClientCallbackUri.value) {
    return
  }
  const redirectUris = parseRedirectUris()
  if (redirectUris.includes(callbackUri)) {
    return
  }
  form.redirectUrisText = [...redirectUris, callbackUri].join('\n')
  lastAutoFilledClientCallbackUri.value = callbackUri
}

watch(
  () => form.siteUrl,
  () => {
    const callbackUri = recommendedClientCallbackUri.value
    if (!callbackUri || !canUseRecommendedClientCallbackUri.value) {
      return
    }
    const currentRedirectUrisText = form.redirectUrisText.trim()
    if (
      currentRedirectUrisText &&
      currentRedirectUrisText !== lastAutoFilledClientCallbackUri.value
    ) {
      return
    }
    form.redirectUrisText = callbackUri
    lastAutoFilledClientCallbackUri.value = callbackUri
  },
)

async function loadClients() {
  loading.value = true
  try {
    clients.value = await ssoClientApi.list()
  } catch (error) {
    Toast.error('接入站列表加载失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

async function loadRoleMappings() {
  roleMappingsLoading.value = true
  try {
    roleMappings.value = await ssoRoleMappingApi.list()
  } catch (error) {
    Toast.error('角色映射列表加载失败')
    console.error(error)
  } finally {
    roleMappingsLoading.value = false
  }
}

async function loadCenterRoleOptions() {
  centerRoleOptionsLoading.value = true
  centerRoleOptionsError.value = ''
  try {
    const setting = runtimeSetting.value
    const centerUrl = setting.centerUrl?.trim()
    if (setting.mode !== 'client') {
      centerRoleOptions.value = []
      centerRoleOptionsError.value = '当前插件不是接入站模式，无法从身份中心加载角色'
      return
    }
    if (!centerUrl) {
      centerRoleOptions.value = []
      centerRoleOptionsError.value = '请先在插件设置中配置身份中心地址'
      return
    }
    centerRoleOptions.value = await ssoRoleOptionApi.listCenterRoles()
  } catch (error) {
    centerRoleOptionsError.value = '中心角色加载失败'
    Toast.error('中心角色加载失败')
    console.error(error)
  } finally {
    centerRoleOptionsLoading.value = false
  }
}

async function loadLocalRoleOptions() {
  localRoleOptionsLoading.value = true
  localRoleOptionsError.value = ''
  try {
    localRoleOptions.value = await ssoRoleOptionApi.listLocalRoles()
  } catch (error) {
    localRoleOptionsError.value = '本地角色加载失败'
    Toast.error('本地角色加载失败')
    console.error(error)
  } finally {
    localRoleOptionsLoading.value = false
  }
}

async function loadRoleOptions() {
  await Promise.all([loadCenterRoleOptions(), loadLocalRoleOptions()])
}

async function refreshRoleMappingArea() {
  await Promise.all([loadRoleMappings(), loadRoleOptions()])
}

async function loadUserBindings() {
  userBindingsLoading.value = true
  try {
    userBindings.value = await ssoUserBindingApi.list()
  } catch (error) {
    Toast.error('用户绑定列表加载失败')
    console.error(error)
  } finally {
    userBindingsLoading.value = false
  }
}

async function loadAuditLogs() {
  auditLogsLoading.value = true
  try {
    const result = await ssoAuditLogApi.list({
      outcome: auditLogFilters.outcome || undefined,
      clientId: auditLogFilters.clientId.trim() || undefined,
      keyword: auditLogFilters.keyword.trim() || undefined,
      page: auditLogFilters.page,
      size: auditLogFilters.size,
    })
    auditLogs.value = result.items || []
    auditLogFilters.page = result.page
    auditLogFilters.size = result.size
    auditLogTotal.value = result.total
    auditLogTotalPages.value = Math.max(1, result.totalPages)
  } catch (error) {
    Toast.error('审计日志加载失败')
    console.error(error)
  } finally {
    auditLogsLoading.value = false
  }
}

async function loadAuditFailureSummaries() {
  auditFailureSummariesLoading.value = true
  try {
    auditFailureSummaries.value = await ssoAuditLogApi.recentFailures(5)
  } catch (error) {
    Toast.error('失败原因统计加载失败')
    console.error(error)
  } finally {
    auditFailureSummariesLoading.value = false
  }
}

async function loadAuditCleanupStatus() {
  auditCleanupStatusLoading.value = true
  try {
    auditCleanupStatus.value = await ssoAuditLogApi.cleanupStatus()
  } catch (error) {
    Toast.error('审计清理状态加载失败')
    console.error(error)
  } finally {
    auditCleanupStatusLoading.value = false
  }
}

async function loadAuditCleanupRecords() {
  auditCleanupRecordsLoading.value = true
  try {
    auditCleanupRecords.value = await ssoAuditLogApi.cleanupRecords(5)
  } catch (error) {
    Toast.error('审计清理历史加载失败')
    console.error(error)
  } finally {
    auditCleanupRecordsLoading.value = false
  }
}

async function refreshAuditArea() {
  await Promise.all([
    loadAuditLogs(),
    loadAuditFailureSummaries(),
    loadAuditCleanupStatus(),
    loadAuditCleanupRecords(),
  ])
}

function applyAuditFilters() {
  auditLogFilters.page = 1
  void loadAuditLogs()
}

function resetAuditFilters() {
  auditLogFilters.outcome = ''
  auditLogFilters.clientId = ''
  auditLogFilters.keyword = ''
  auditLogFilters.page = 1
  void loadAuditLogs()
}

function previousAuditPage() {
  if (auditLogFilters.page <= 1) {
    return
  }
  auditLogFilters.page -= 1
  void loadAuditLogs()
}

function nextAuditPage() {
  if (auditLogFilters.page >= auditLogTotalPages.value) {
    return
  }
  auditLogFilters.page += 1
  void loadAuditLogs()
}

async function previewAuditCleanup() {
  auditCleanupLoading.value = true
  try {
    auditCleanupPreview.value = await ssoAuditLogApi.cleanup({
      retentionDays: normalizeAuditRetentionDays(),
      dryRun: true,
    })
    await Promise.all([loadAuditCleanupStatus(), loadAuditCleanupRecords()])
    Toast.success('审计日志清理预览已生成')
  } catch (error) {
    Toast.error('审计日志清理预览失败')
    console.error(error)
  } finally {
    auditCleanupLoading.value = false
  }
}

async function cleanupAuditLogs() {
  const retentionDays = normalizeAuditRetentionDays()
  const previewMatched = auditCleanupPreview.value?.matched ?? 0
  if (previewMatched <= 0) {
    Toast.warning('没有需要清理的审计日志')
    return
  }
  const confirmed = window.confirm(
    `确认删除 ${previewMatched} 条 ${retentionDays} 天前的审计日志？`,
  )
  if (!confirmed) {
    return
  }

  auditCleanupLoading.value = true
  try {
    const result = await ssoAuditLogApi.cleanup({
      retentionDays,
      dryRun: false,
    })
    auditCleanupPreview.value = result
    Toast.success(`已清理 ${result.deleted} 条审计日志`)
    auditLogFilters.page = 1
    await refreshAuditArea()
  } catch (error) {
    Toast.error('审计日志清理失败')
    console.error(error)
  } finally {
    auditCleanupLoading.value = false
  }
}

function normalizeAuditRetentionDays() {
  const days = Number(auditCleanupForm.retentionDays)
  if (!Number.isFinite(days)) {
    auditCleanupForm.retentionDays = 90
    return 90
  }
  const normalized = Math.min(3650, Math.max(1, Math.trunc(days)))
  auditCleanupForm.retentionDays = normalized
  return normalized
}

async function saveClient() {
  const redirectUris = parseRedirectUris()
  const isEditing = Boolean(editingClientId.value)
  if (
    (isEditing && !form.clientId) ||
    !form.displayName ||
    !form.siteUrl ||
    redirectUris.length === 0
  ) {
    Toast.warning('请补齐接入站信息')
    return
  }

  savingClient.value = true
  createdSecret.value = ''
  createdClientId.value = ''
  try {
    const payload = {
      displayName: form.displayName.trim(),
      siteUrl: form.siteUrl.trim(),
      redirectUris,
      enabled: form.enabled,
    }
    if (isEditing) {
      await ssoClientApi.update({
        clientId: form.clientId.trim(),
        ...payload,
      })
      Toast.success('接入站已更新')
    } else {
      const result = await ssoClientApi.create(payload)
      createdClientId.value = result.client.clientId
      createdSecret.value = result.clientSecret
      Toast.success('接入站已创建')
    }
    const clientId = createdClientId.value
    const secret = createdSecret.value
    resetClientForm()
    createdClientId.value = clientId
    createdSecret.value = secret
    await loadClients()
  } catch (error) {
    Toast.error(isEditing ? '接入站更新失败' : '接入站创建失败')
    console.error(error)
  } finally {
    savingClient.value = false
  }
}

async function toggleClient(client: SsoClient) {
  savingClient.value = true
  try {
    await ssoClientApi.update({
      clientId: client.clientId,
      enabled: client.enabled === false,
    })
    Toast.success(client.enabled === false ? '接入站已启用' : '接入站已停用')
    if (editingClientId.value === client.clientId) {
      form.enabled = client.enabled === false
    }
    await loadClients()
  } catch (error) {
    Toast.error('接入站状态更新失败')
    console.error(error)
  } finally {
    savingClient.value = false
  }
}

async function deleteClient(client: SsoClient) {
  const confirmed = window.confirm(
    `确认删除接入站「${client.displayName || client.clientId}」？删除后该站点将无法继续发起 SSO 登录。`,
  )
  if (!confirmed) {
    return
  }

  savingClient.value = true
  try {
    await ssoClientApi.remove(client.clientId)
    Toast.success('接入站已删除')
    if (editingClientId.value === client.clientId) {
      resetClientForm()
    }
    await loadClients()
  } catch (error) {
    Toast.error('接入站删除失败')
    console.error(error)
  } finally {
    savingClient.value = false
  }
}

function resetRoleMappingForm() {
  editingRoleMappingCenterRole.value = ''
  roleMappingForm.centerRole = ''
  roleMappingForm.localRole = ''
  roleMappingForm.sort = 0
  roleMappingForm.enabled = true
}

function editRoleMapping(mapping: SsoRoleMapping) {
  editingRoleMappingCenterRole.value = mapping.centerRole
  roleMappingForm.centerRole = mapping.centerRole
  roleMappingForm.localRole = mapping.localRole
  roleMappingForm.sort = mapping.sort ?? 0
  roleMappingForm.enabled = mapping.enabled !== false
}

async function saveRoleMapping() {
  if (!roleMappingForm.centerRole.trim() || !roleMappingForm.localRole.trim()) {
    Toast.warning('请补齐角色映射信息')
    return
  }

  savingRoleMapping.value = true
  try {
    const payload = {
      centerRole: roleMappingForm.centerRole.trim(),
      localRole: roleMappingForm.localRole.trim(),
      sort: Number.isFinite(roleMappingForm.sort) ? roleMappingForm.sort : 0,
      enabled: roleMappingForm.enabled,
    }
    if (editingRoleMappingCenterRole.value) {
      await ssoRoleMappingApi.update(payload)
      Toast.success('角色映射已更新')
    } else {
      await ssoRoleMappingApi.create(payload)
      Toast.success('角色映射已创建')
    }
    resetRoleMappingForm()
    await loadRoleMappings()
  } catch (error) {
    Toast.error(editingRoleMappingCenterRole.value ? '角色映射更新失败' : '角色映射创建失败')
    console.error(error)
  } finally {
    savingRoleMapping.value = false
  }
}

function getClientIssues(client: SsoClient) {
  const issues = new Set<string>()
  const redirectUris = client.redirectUris || []
  if (client.enabled === false) {
    issues.add('接入站已停用')
  }
  if (!isAllowedUrl(client.siteUrl)) {
    issues.add('站点地址需使用 HTTPS 或 localhost HTTP')
  }
  if (!redirectUris.length) {
    issues.add('缺少回调地址')
  }
  const expectedCallbackUri = buildClientCallbackUri(client.siteUrl || '')
  if (expectedCallbackUri && !redirectUris.includes(expectedCallbackUri)) {
    issues.add('缺少标准 SSO 回调地址')
  }
  for (const uri of redirectUris) {
    if (!isAllowedUrl(uri)) {
      issues.add('回调地址需使用 HTTPS 或 localhost HTTP')
    }
    if (client.siteUrl && !uri.startsWith(normalizeSiteUrl(client.siteUrl))) {
      issues.add('回调地址与站点地址不一致')
    }
  }
  return Array.from(issues)
}

function isAllowedUrl(value: string) {
  return isAllowedSsoUrl(value)
}

function withCurrentRoleOption(options: RoleOption[], currentRole: string) {
  const normalizedRole = currentRole.trim()
  if (!normalizedRole || options.some((option) => option.value === normalizedRole)) {
    return options
  }
  return [
    {
      label: normalizedRole,
      value: normalizedRole,
      displayName: normalizedRole,
    },
    ...options,
  ]
}

function roleDisplayName(options: RoleOption[], role: string) {
  return options.find((option) => option.value === role)?.displayName || role
}

function formatDate(value?: string) {
  return value ? utils.date.format(value) : '-'
}

function auditOutcomeLabel(outcome: string) {
  return outcome === 'success' ? '成功' : '失败'
}

function cleanupTriggerLabel(trigger?: string) {
  return trigger === 'auto' ? '自动清理' : '手动清理'
}

function cleanupStatusLabel(status: SsoAuditLogCleanupStatus) {
  if (!status.success) {
    return '失败'
  }
  return status.result?.dryRun ? '预览完成' : '清理完成'
}

function cleanupStatusSummary(status: SsoAuditLogCleanupStatus) {
  if (!status.success) {
    return status.message || '清理执行失败'
  }
  const result = status.result
  if (!result) {
    return '-'
  }
  return `扫描 ${result.scanned} 条，匹配 ${result.matched} 条，删除 ${result.deleted} 条`
}

function cleanupRecordLabel(record: SsoAuditLogCleanupRecord) {
  if (record.success === false) {
    return '失败'
  }
  return record.dryRun ? '预览完成' : '清理完成'
}

function cleanupRecordSummary(record: SsoAuditLogCleanupRecord) {
  if (record.success === false) {
    return record.message || '清理执行失败'
  }
  return `扫描 ${record.scanned ?? 0} 条，匹配 ${record.matched ?? 0} 条，删除 ${
    record.deleted ?? 0
  } 条`
}

onMounted(async () => {
  await loadRuntimeSetting()
  await loadVisibleAreas()
})
</script>

<template>
  <section class="sso-admin-shell">
    <div class="sso-admin-shell__header">
      <div>
        <p class="sso-admin-eyebrow">SSO Plugin</p>
        <h1>统一身份认证</h1>
      </div>
      <div class="sso-admin-stats">
        <span v-for="stat in visibleStats" :key="stat">{{ stat }}</span>
      </div>
    </div>

    <div class="sso-admin-mode-strip">
      <div>
        <span class="sso-admin-mode-strip__label">当前运行模式</span>
        <strong>{{ currentModeLabel }}</strong>
        <p>{{ currentModeDescription }}</p>
        <p v-if="runtimeSettingError" class="sso-admin-mode-strip__error">
          {{ runtimeSettingError }}
        </p>
      </div>
      <span class="sso-admin-mode-badge" :class="{ 'is-client': isClientMode }">
        {{ runtimeSettingLoading ? '识别中' : currentModeLabel }}
      </span>
    </div>

    <nav class="sso-admin-tabs" aria-label="SSO 管理分区">
      <button
        v-for="tab in adminTabs"
        :key="tab.key"
        class="sso-admin-tab"
        :class="{ 'is-active': activeTab === tab.key }"
        type="button"
        @click="selectTab(tab.key)"
      >
        <strong>{{ tab.label }}</strong>
        <span>{{ tab.description }}</span>
      </button>
    </nav>

    <div v-if="activeTab === 'clients' && isCenterMode" class="sso-admin-grid">
      <article class="sso-admin-panel sso-admin-panel--list">
        <div class="sso-admin-panel__header">
          <div>
            <h2>接入站</h2>
            <p>中心站信任的 Halo 站点。</p>
          </div>
          <VButton size="sm" type="secondary" :loading="loading" @click="loadClients">刷新</VButton>
        </div>

        <VLoading v-if="loading" />
        <VEmpty
          v-else-if="clients.length === 0"
          title="暂无接入站"
          message="创建第一个接入站后开始接入 SSO。"
        />
        <div v-else class="sso-admin-client-list">
          <div v-for="client in clients" :key="client.clientId" class="sso-admin-client">
            <div class="sso-admin-client__main">
              <strong>{{ client.displayName }}</strong>
              <span>{{ client.clientId }}</span>
            </div>
            <div class="sso-admin-client__meta">
              <span class="sso-admin-badge" :class="{ 'is-disabled': client.enabled === false }">
                {{ client.enabled === false ? '停用' : '启用' }}
              </span>
              <span>{{ client.siteUrl }}</span>
            </div>
            <div class="sso-admin-client__uris">
              <code v-for="uri in client.redirectUris || []" :key="uri">{{ uri }}</code>
            </div>
            <div v-if="getClientIssues(client).length" class="sso-admin-issues">
              <span v-for="issue in getClientIssues(client)" :key="issue">{{ issue }}</span>
            </div>
            <div class="sso-admin-client__actions">
              <VButton size="sm" type="secondary" @click="editClient(client)">编辑</VButton>
              <VButton
                size="sm"
                :type="client.enabled === false ? 'primary' : 'danger'"
                :disabled="savingClient"
                @click="toggleClient(client)"
              >
                {{ client.enabled === false ? '启用' : '停用' }}
              </VButton>
              <VButton
                size="sm"
                type="danger"
                :disabled="savingClient"
                @click="deleteClient(client)"
              >
                删除
              </VButton>
            </div>
          </div>
        </div>
      </article>

      <article class="sso-admin-panel">
        <div class="sso-admin-panel__header">
          <div>
            <h2>{{ clientFormTitle }}</h2>
            <p>
              {{
                editingClientId
                  ? 'Client ID 保持稳定，密钥不会重新显示。'
                  : '系统自动生成 Client ID，密钥只在创建后显示一次。'
              }}
            </p>
          </div>
        </div>

        <form class="sso-admin-form" @submit.prevent="saveClient">
          <label v-if="editingClientId">
            <span>Client ID</span>
            <input v-model="form.clientId" disabled autocomplete="off" />
            <small class="sso-admin-field-hint">
              接入站唯一标识由身份中心生成，创建后保持稳定。
            </small>
          </label>
          <label>
            <span>站点名称</span>
            <input v-model="form.displayName" autocomplete="off" placeholder="B 站" />
            <small class="sso-admin-field-hint"> 用于后台展示，填能让人一眼认出的站点名称。 </small>
          </label>
          <label>
            <span>站点地址</span>
            <input v-model="form.siteUrl" autocomplete="off" placeholder="https://b.example.com" />
            <small
              class="sso-admin-field-hint"
              :class="{ 'is-error': form.siteUrl.trim() && !isAllowedUrl(form.siteUrl) }"
            >
              填接入站的对外访问地址，线上必须 HTTPS；本地调试可用 localhost 或 127.0.0.1 的 HTTP。
            </small>
          </label>
          <label>
            <span>回调地址白名单</span>
            <textarea
              v-model="form.redirectUrisText"
              rows="4"
              placeholder="https://b.example.com/apis/public.sso.muyin.site/v1alpha1/client/callback"
            />
            <small class="sso-admin-field-hint">
              一行一个精确地址，不能写通配符。中心站只允许登录成功后跳回白名单里的地址。
            </small>
          </label>
          <div class="sso-admin-callback-helper">
            <div>
              <strong>推荐回调地址</strong>
              <span v-if="recommendedClientCallbackUri">
                由站点地址自动拼接当前插件的接入站回调路径。
              </span>
              <span v-else>先填写站点地址，再生成推荐回调地址。</span>
            </div>
            <code v-if="recommendedClientCallbackUri">{{ recommendedClientCallbackUri }}</code>
            <p>
              如果接入站在反向代理后面，站点地址要填用户真实访问的公网域名；否则 redirect_uri
              对不上，登录会被中心站拒掉。
            </p>
            <button
              class="sso-admin-link-button"
              type="button"
              :disabled="!canUseRecommendedClientCallbackUri || redirectUrisContainRecommendation"
              @click="fillRecommendedClientCallbackUri"
            >
              {{ redirectUrisContainRecommendation ? '已在白名单中' : '填入推荐地址' }}
            </button>
          </div>
          <label class="sso-admin-check">
            <input v-model="form.enabled" type="checkbox" />
            <span>启用接入站</span>
          </label>
          <div class="sso-admin-form-actions">
            <button
              class="sso-admin-action-button is-primary"
              type="button"
              :disabled="savingClient"
              @click="saveClient"
            >
              {{ savingClient ? '处理中' : clientSubmitLabel }}
            </button>
            <VButton
              v-if="editingClientId"
              type="secondary"
              :disabled="savingClient"
              @click="resetClientForm"
            >
              取消
            </VButton>
          </div>
        </form>

        <div v-if="createdClientId || createdSecret" class="sso-admin-secret">
          <div v-if="createdClientId" class="sso-admin-secret__item">
            <span>Client ID</span>
            <code>{{ createdClientId }}</code>
          </div>
          <div v-if="createdSecret" class="sso-admin-secret__item">
            <span>Client Secret</span>
            <code>{{ createdSecret }}</code>
          </div>
        </div>
      </article>
    </div>

    <div
      v-if="activeTab === 'roleMappings' && isClientMode"
      class="sso-admin-grid sso-admin-grid--role-mappings"
    >
      <article class="sso-admin-panel sso-admin-panel--list">
        <div class="sso-admin-panel__header">
          <div>
            <h2>角色映射</h2>
            <p>把中心身份站角色转换为当前站点的 Halo 角色。</p>
          </div>
          <VButton
            size="sm"
            type="secondary"
            :loading="roleMappingsLoading || roleMappingOptionsLoading"
            @click="refreshRoleMappingArea"
          >
            刷新
          </VButton>
        </div>

        <VLoading v-if="roleMappingsLoading" />
        <VEmpty
          v-else-if="roleMappings.length === 0"
          title="暂无角色映射"
          message="未匹配时会使用接入站配置里的默认角色。"
        />
        <div v-else class="sso-admin-role-list">
          <div
            v-for="mapping in roleMappings"
            :key="mapping.metadata?.name || mapping.centerRole"
            class="sso-admin-role"
          >
            <div class="sso-admin-role__main">
              <div>
                <strong class="sso-admin-role__title">
                  <span>{{ roleDisplayName(centerRoleOptions, mapping.centerRole) }}</span>
                  <span class="sso-admin-role__arrow">映射到</span>
                  <span>{{ roleDisplayName(localRoleOptions, mapping.localRole) }}</span>
                </strong>
                <span class="sso-admin-role__codes">
                  <code>{{ mapping.centerRole }}</code>
                  <span>→</span>
                  <code>{{ mapping.localRole }}</code>
                </span>
              </div>
              <span class="sso-admin-badge" :class="{ 'is-disabled': mapping.enabled === false }">
                {{ mapping.enabled === false ? '停用' : '启用' }}
              </span>
            </div>
            <div class="sso-admin-role__meta">
              <span>排序 {{ mapping.sort ?? 0 }}</span>
              <VButton size="sm" type="secondary" @click="editRoleMapping(mapping)">编辑</VButton>
            </div>
          </div>
        </div>
      </article>

      <article class="sso-admin-panel">
        <div class="sso-admin-panel__header">
          <div>
            <h2>{{ roleMappingFormTitle }}</h2>
            <p>中心角色保持稳定，本地角色可按站点差异映射。</p>
          </div>
        </div>

        <form class="sso-admin-form" @submit.prevent="saveRoleMapping">
          <label>
            <span>中心角色</span>
            <select
              v-model="roleMappingForm.centerRole"
              :disabled="Boolean(editingRoleMappingCenterRole) || centerRoleOptionsLoading"
            >
              <option value="" disabled>
                {{ centerRoleOptionsLoading ? '正在加载中心角色' : '请选择中心角色' }}
              </option>
              <option
                v-for="option in centerRoleSelectOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
            <small v-if="centerRoleOptionsError" class="sso-admin-field-hint is-error">
              {{ centerRoleOptionsError }}
            </small>
          </label>
          <label>
            <span>本地角色</span>
            <select v-model="roleMappingForm.localRole" :disabled="localRoleOptionsLoading">
              <option value="" disabled>
                {{ localRoleOptionsLoading ? '正在加载本地角色' : '请选择本地角色' }}
              </option>
              <option
                v-for="option in localRoleSelectOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
            <small v-if="localRoleOptionsError" class="sso-admin-field-hint is-error">
              {{ localRoleOptionsError }}
            </small>
          </label>
          <label>
            <span>排序</span>
            <input v-model.number="roleMappingForm.sort" type="number" min="0" step="1" />
          </label>
          <label class="sso-admin-check">
            <input v-model="roleMappingForm.enabled" type="checkbox" />
            <span>启用映射</span>
          </label>
          <div class="sso-admin-form-actions">
            <button
              class="sso-admin-action-button is-primary"
              type="button"
              :disabled="savingRoleMapping"
              @click="saveRoleMapping"
            >
              {{
                savingRoleMapping
                  ? '处理中'
                  : editingRoleMappingCenterRole
                    ? '保存映射'
                    : '创建映射'
              }}
            </button>
            <VButton
              v-if="editingRoleMappingCenterRole"
              type="secondary"
              :disabled="savingRoleMapping"
              @click="resetRoleMappingForm"
            >
              取消
            </VButton>
          </div>
        </form>
      </article>
    </div>

    <div
      v-if="activeTab === 'userBindings' && isClientMode"
      class="sso-admin-grid sso-admin-grid--single"
    >
      <article class="sso-admin-panel sso-admin-panel--list">
        <div class="sso-admin-panel__header">
          <div>
            <h2>用户绑定</h2>
            <p>中心身份用户与当前站点本地用户的绑定关系。</p>
          </div>
          <VButton
            size="sm"
            type="secondary"
            :loading="userBindingsLoading"
            @click="loadUserBindings"
          >
            刷新
          </VButton>
        </div>

        <VLoading v-if="userBindingsLoading" />
        <VEmpty
          v-else-if="userBindings.length === 0"
          title="暂无用户绑定"
          message="用户首次通过 SSO 登录后会生成绑定记录。"
        />
        <div v-else class="sso-admin-user-list">
          <div
            v-for="binding in userBindings"
            :key="binding.metadata?.name || binding.subject"
            class="sso-admin-user"
          >
            <div class="sso-admin-user__main">
              <div>
                <strong>{{ binding.displayName || binding.localUsername }}</strong>
                <span>{{ binding.localUsername }} · {{ binding.email }}</span>
              </div>
              <code>{{ binding.subject }}</code>
            </div>
            <div class="sso-admin-user__meta">
              <span>绑定 {{ formatDate(binding.boundAt) }}</span>
              <span>最后登录 {{ formatDate(binding.lastLoginAt) }}</span>
            </div>
          </div>
        </div>
      </article>
    </div>

    <div v-if="activeTab === 'audit'" class="sso-admin-grid sso-admin-grid--single">
      <article class="sso-admin-panel sso-admin-panel--list">
        <div class="sso-admin-panel__header">
          <div>
            <h2>审计日志</h2>
            <p>接入站登录结果，失败记录优先看这里。</p>
          </div>
          <VButton
            size="sm"
            type="secondary"
            :loading="
              auditLogsLoading ||
              auditFailureSummariesLoading ||
              auditCleanupStatusLoading ||
              auditCleanupRecordsLoading
            "
            @click="refreshAuditArea"
          >
            刷新
          </VButton>
        </div>

        <form class="sso-admin-audit-filters" @submit.prevent="applyAuditFilters">
          <label>
            <span>结果</span>
            <select v-model="auditLogFilters.outcome">
              <option value="">全部</option>
              <option value="success">成功</option>
              <option value="failure">失败</option>
            </select>
          </label>
          <label>
            <span>Client ID</span>
            <input v-model="auditLogFilters.clientId" autocomplete="off" placeholder="site-b" />
          </label>
          <label>
            <span>关键词</span>
            <input
              v-model="auditLogFilters.keyword"
              autocomplete="off"
              placeholder="邮箱 / Subject / 原因"
            />
          </label>
          <div class="sso-admin-audit-filters__actions">
            <button
              class="sso-admin-action-button sso-admin-action-button--sm is-primary"
              type="button"
              :disabled="auditLogsLoading"
              @click="applyAuditFilters"
            >
              {{ auditLogsLoading ? '筛选中' : '筛选' }}
            </button>
            <VButton
              size="sm"
              type="secondary"
              :disabled="!hasAuditFilters || auditLogsLoading"
              @click="resetAuditFilters"
            >
              重置
            </VButton>
          </div>
        </form>

        <div class="sso-admin-failure-summary">
          <div class="sso-admin-failure-summary__header">
            <strong>最近失败原因</strong>
            <span v-if="auditFailureSummaries.length"> {{ auditFailureSummaries.length }} 类 </span>
          </div>
          <VLoading v-if="auditFailureSummariesLoading" />
          <p v-else-if="auditFailureSummaries.length === 0">暂无失败记录。</p>
          <div v-else class="sso-admin-failure-summary__list">
            <div
              v-for="summary in auditFailureSummaries"
              :key="summary.message"
              class="sso-admin-failure-summary__item"
            >
              <div>
                <strong>{{ summary.message }}</strong>
                <span>{{ summary.clientIds?.join(', ') || '-' }}</span>
              </div>
              <span>{{ summary.count }} 次</span>
            </div>
          </div>
        </div>

        <div class="sso-admin-audit-cleanup">
          <div class="sso-admin-audit-cleanup__header">
            <div>
              <strong>日志保留策略</strong>
              <span>先预览影响数量，再执行清理。</span>
            </div>
            <label>
              <span>保留天数</span>
              <input
                v-model.number="auditCleanupForm.retentionDays"
                type="number"
                min="1"
                max="3650"
                step="1"
              />
            </label>
          </div>
          <div v-if="auditCleanupPreview" class="sso-admin-audit-cleanup__result">
            <span>扫描 {{ auditCleanupPreview.scanned }} 条</span>
            <span>可清理 {{ auditCleanupPreview.matched }} 条</span>
            <span>保留 {{ auditCleanupPreview.retained }} 条</span>
            <span v-if="auditCleanupPreview.deleted"
              >已删除 {{ auditCleanupPreview.deleted }} 条</span
            >
          </div>
          <VLoading v-if="auditCleanupStatusLoading" />
          <div v-else class="sso-admin-audit-cleanup__status">
            <template v-if="auditCleanupStatus">
              <div>
                <strong>{{ cleanupTriggerLabel(auditCleanupStatus.trigger) }}</strong>
                <span>{{ formatDate(auditCleanupStatus.finishedAt) }}</span>
              </div>
              <div>
                <span class="sso-admin-badge" :class="{ 'is-error': !auditCleanupStatus.success }">
                  {{ cleanupStatusLabel(auditCleanupStatus) }}
                </span>
                <span>{{ cleanupStatusSummary(auditCleanupStatus) }}</span>
              </div>
            </template>
            <span v-else>暂无清理记录</span>
          </div>
          <div class="sso-admin-audit-cleanup__records">
            <div class="sso-admin-audit-cleanup__records-header">
              <strong>最近清理历史</strong>
              <span v-if="auditCleanupRecords.length">{{ auditCleanupRecords.length }} 条</span>
            </div>
            <VLoading v-if="auditCleanupRecordsLoading" />
            <p v-else-if="auditCleanupRecords.length === 0">暂无持久化清理历史。</p>
            <div v-else class="sso-admin-audit-cleanup__records-list">
              <div
                v-for="record in auditCleanupRecords"
                :key="
                  record.metadata?.name ||
                  `${record.trigger || 'unknown'}-${record.finishedAt || record.startedAt}`
                "
                class="sso-admin-audit-cleanup__record"
              >
                <div>
                  <strong>{{ cleanupTriggerLabel(record.trigger) }}</strong>
                  <span>{{ formatDate(record.finishedAt) }}</span>
                </div>
                <div>
                  <span class="sso-admin-badge" :class="{ 'is-error': record.success === false }">
                    {{ cleanupRecordLabel(record) }}
                  </span>
                  <span>{{ cleanupRecordSummary(record) }}</span>
                </div>
              </div>
            </div>
          </div>
          <div class="sso-admin-audit-cleanup__actions">
            <VButton
              size="sm"
              type="secondary"
              :loading="auditCleanupLoading"
              @click="previewAuditCleanup"
            >
              预览清理
            </VButton>
            <VButton
              size="sm"
              type="danger"
              :loading="auditCleanupLoading"
              :disabled="!auditCleanupPreview?.matched"
              @click="cleanupAuditLogs"
            >
              清理过期日志
            </VButton>
          </div>
        </div>

        <VLoading v-if="auditLogsLoading" />
        <VEmpty
          v-else-if="auditLogs.length === 0"
          :title="hasAuditFilters ? '无匹配审计日志' : '暂无审计日志'"
          :message="hasAuditFilters ? '换个筛选条件再试。' : '接入站回调处理后会记录登录结果。'"
        />
        <template v-else>
          <div class="sso-admin-audit-list">
            <div
              v-for="log in auditLogs"
              :key="log.metadata?.name || `${log.createdAt}-${log.subject || log.clientId}`"
              class="sso-admin-audit"
            >
              <div class="sso-admin-audit__main">
                <div>
                  <strong>{{ log.eventType }}</strong>
                  <span>{{ log.message || '-' }}</span>
                </div>
                <span class="sso-admin-badge" :class="{ 'is-error': log.outcome !== 'success' }">
                  {{ auditOutcomeLabel(log.outcome) }}
                </span>
              </div>
              <div class="sso-admin-audit__meta">
                <span>Client {{ log.clientId || '-' }}</span>
                <span>{{ log.email || log.subject || '-' }}</span>
                <span>{{ formatDate(log.createdAt) }}</span>
              </div>
            </div>
          </div>
          <div class="sso-admin-pagination">
            <span>
              第 {{ auditLogFilters.page }}/{{ auditLogTotalPages }} 页，共
              {{ auditLogTotal }} 条，本页成功 {{ successfulAuditLogs }} 条
            </span>
            <div>
              <VButton
                size="sm"
                type="secondary"
                :disabled="auditLogFilters.page <= 1 || auditLogsLoading"
                @click="previousAuditPage"
              >
                上一页
              </VButton>
              <VButton
                size="sm"
                type="secondary"
                :disabled="auditLogFilters.page >= auditLogTotalPages || auditLogsLoading"
                @click="nextAuditPage"
              >
                下一页
              </VButton>
            </div>
          </div>
        </template>
      </article>
    </div>
  </section>
</template>

<style lang="scss" scoped>
.sso-admin-shell {
  min-height: 100%;
  padding: 24px;
  background: #f6f7f9;
  color: #111827;
}

.sso-admin-shell__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;

  h1 {
    margin: 4px 0 0;
    font-size: 24px;
    line-height: 32px;
    font-weight: 700;
  }
}

.sso-admin-eyebrow {
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: #64748b;
}

.sso-admin-stats {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;

  span {
    min-height: 28px;
    padding: 5px 10px;
    border: 1px solid #dbe3ea;
    border-radius: 6px;
    background: #fff;
    color: #334155;
    font-size: 12px;
    font-weight: 600;
  }
}

.sso-admin-mode-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #fff;
  padding: 14px 16px;

  strong {
    display: block;
    color: #0f172a;
    font-size: 15px;
    line-height: 22px;
  }

  p {
    margin: 2px 0 0;
    color: #64748b;
    font-size: 13px;
    line-height: 20px;
  }
}

.sso-admin-mode-strip__label {
  display: block;
  margin-bottom: 2px;
  color: #64748b;
  font-size: 12px;
  line-height: 18px;
}

.sso-admin-mode-strip__error {
  color: #b91c1c !important;
}

.sso-admin-mode-badge {
  flex: 0 0 auto;
  min-width: 96px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
  line-height: 26px;
  text-align: center;
}

.sso-admin-mode-badge.is-client {
  background: #ecfdf5;
  color: #047857;
}

.sso-admin-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  overflow-x: auto;
}

.sso-admin-tab {
  display: grid;
  gap: 2px;
  min-width: 180px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  background: #fff;
  color: #334155;
  cursor: pointer;
  padding: 10px 12px;
  text-align: left;
  transition:
    background-color 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;

  strong {
    font-size: 13px;
    line-height: 20px;
  }

  span {
    color: #64748b;
    font-size: 12px;
    line-height: 18px;
  }

  &:hover {
    border-color: #94a3b8;
  }

  &.is-active {
    border-color: #14b8a6;
    background: #f0fdfa;
    color: #0f766e;
  }
}

.sso-admin-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 16px;
  align-items: start;
}

.sso-admin-grid--role-mappings {
  margin-top: 16px;
}

.sso-admin-grid--observability {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 16px;
}

.sso-admin-grid--single {
  grid-template-columns: minmax(0, 1fr);
}

.sso-admin-panel {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  padding: 18px;
}

.sso-admin-panel--list {
  min-height: 420px;
}

.sso-admin-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;

  h2 {
    margin: 0;
    font-size: 16px;
    line-height: 24px;
    font-weight: 700;
  }

  p {
    margin: 4px 0 0;
    color: #64748b;
    font-size: 13px;
    line-height: 20px;
  }
}

.sso-admin-client-list {
  display: grid;
  gap: 10px;
}

.sso-admin-role-list,
.sso-admin-user-list,
.sso-admin-audit-list {
  display: grid;
  gap: 10px;
}

.sso-admin-audit-filters {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr) minmax(0, 1.4fr) auto;
  gap: 10px;
  align-items: end;
  margin-bottom: 12px;

  label {
    display: grid;
    gap: 6px;
  }

  span {
    color: #334155;
    font-size: 12px;
    font-weight: 700;
    line-height: 18px;
  }

  input,
  select {
    width: 100%;
    box-sizing: border-box;
    min-height: 36px;
    border: 1px solid #cbd5e1;
    border-radius: 6px;
    padding: 7px 10px;
    background: #fff;
    color: #0f172a;
    font-size: 13px;
    line-height: 20px;
    outline: none;
  }
}

.sso-admin-audit-filters__actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.sso-admin-failure-summary {
  display: grid;
  gap: 10px;
  margin-bottom: 12px;
  border: 1px solid #fee2e2;
  border-radius: 8px;
  padding: 12px;
  background: #fff7f7;

  p {
    margin: 0;
    color: #64748b;
    font-size: 13px;
    line-height: 20px;
  }
}

.sso-admin-failure-summary__header,
.sso-admin-failure-summary__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.sso-admin-failure-summary__header {
  strong {
    color: #7f1d1d;
    font-size: 13px;
    line-height: 20px;
  }

  span {
    color: #991b1b;
    font-size: 12px;
    font-weight: 700;
    line-height: 18px;
  }
}

.sso-admin-failure-summary__list {
  display: grid;
  gap: 8px;
}

.sso-admin-failure-summary__item {
  border-top: 1px solid #fecaca;
  padding-top: 8px;

  strong {
    display: block;
    color: #111827;
    font-size: 13px;
    line-height: 20px;
  }

  div > span {
    display: block;
    margin-top: 2px;
    color: #64748b;
    font-size: 12px;
    line-height: 18px;
  }

  > span {
    min-width: 44px;
    color: #991b1b;
    font-size: 12px;
    font-weight: 700;
    line-height: 18px;
    text-align: right;
  }
}

.sso-admin-audit-cleanup {
  display: grid;
  gap: 10px;
  margin-bottom: 12px;
  border: 1px solid #dbe3ea;
  border-radius: 8px;
  padding: 12px;
  background: #f8fafc;
}

.sso-admin-audit-cleanup__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 12px;

  strong {
    display: block;
    color: #111827;
    font-size: 13px;
    line-height: 20px;
  }

  div > span,
  label > span {
    display: block;
    color: #64748b;
    font-size: 12px;
    line-height: 18px;
  }

  label {
    display: grid;
    gap: 6px;
    min-width: 120px;
  }

  input {
    width: 100%;
    box-sizing: border-box;
    min-height: 34px;
    border: 1px solid #cbd5e1;
    border-radius: 6px;
    padding: 6px 9px;
    background: #fff;
    color: #0f172a;
    font-size: 13px;
    line-height: 20px;
    outline: none;
  }
}

.sso-admin-audit-cleanup__result {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;

  span {
    border-radius: 6px;
    padding: 4px 8px;
    background: #eef2f7;
    color: #334155;
    font-size: 12px;
    font-weight: 700;
    line-height: 18px;
  }
}

.sso-admin-audit-cleanup__status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-top: 1px solid #e2e8f0;
  padding-top: 10px;
  color: #475569;
  font-size: 12px;
  line-height: 18px;

  > div {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  strong {
    color: #111827;
    font-size: 13px;
    line-height: 20px;
  }

  > span {
    color: #64748b;
  }
}

.sso-admin-audit-cleanup__records {
  display: grid;
  gap: 8px;
  border-top: 1px solid #e2e8f0;
  padding-top: 10px;

  p {
    margin: 0;
    color: #64748b;
    font-size: 12px;
    line-height: 18px;
  }
}

.sso-admin-audit-cleanup__records-header,
.sso-admin-audit-cleanup__record {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.sso-admin-audit-cleanup__records-header {
  strong {
    color: #111827;
    font-size: 13px;
    line-height: 20px;
  }

  span {
    color: #64748b;
    font-size: 12px;
    font-weight: 700;
    line-height: 18px;
  }
}

.sso-admin-audit-cleanup__records-list {
  display: grid;
  gap: 8px;
}

.sso-admin-audit-cleanup__record {
  border-radius: 6px;
  background: #fff;
  padding: 8px 10px;

  > div {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  strong {
    color: #111827;
    font-size: 13px;
    line-height: 20px;
  }

  span {
    color: #475569;
    font-size: 12px;
    line-height: 18px;
  }
}

.sso-admin-audit-cleanup__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.sso-admin-client {
  display: grid;
  gap: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 14px;
  background: #fbfdff;
}

.sso-admin-role,
.sso-admin-user,
.sso-admin-audit {
  display: grid;
  gap: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 14px;
  background: #fbfdff;
}

.sso-admin-client__main,
.sso-admin-client__meta,
.sso-admin-role__main,
.sso-admin-role__meta,
.sso-admin-user__main,
.sso-admin-user__meta,
.sso-admin-audit__main,
.sso-admin-audit__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.sso-admin-client__main {
  strong {
    font-size: 14px;
    line-height: 20px;
  }

  span {
    color: #64748b;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
  }
}

.sso-admin-role__main,
.sso-admin-user__main,
.sso-admin-audit__main {
  strong {
    display: block;
    font-size: 14px;
    line-height: 20px;
  }

  > div > span {
    display: block;
    margin-top: 2px;
    color: #64748b;
    font-size: 13px;
    line-height: 20px;
  }
}

.sso-admin-role__title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.sso-admin-role__arrow {
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

.sso-admin-role__codes {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;

  code {
    max-width: 180px;
    padding: 2px 6px;
    border-radius: 6px;
    background: #eef2f7;
    color: #334155;
    font-size: 12px;
    line-height: 18px;
    overflow-wrap: anywhere;
  }
}

.sso-admin-user__main,
.sso-admin-audit__main {
  code {
    max-width: 220px;
    padding: 4px 6px;
    border-radius: 6px;
    background: #eef2f7;
    color: #334155;
    font-size: 12px;
    line-height: 18px;
    overflow-wrap: anywhere;
  }
}

.sso-admin-client__meta {
  color: #475569;
  font-size: 13px;
  line-height: 20px;
}

.sso-admin-client__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.sso-admin-role__meta,
.sso-admin-user__meta,
.sso-admin-audit__meta {
  color: #475569;
  font-size: 13px;
  line-height: 20px;
  flex-wrap: wrap;
}

.sso-admin-badge {
  min-width: 44px;
  border-radius: 999px;
  background: #dcfce7;
  color: #166534;
  text-align: center;
  font-size: 12px;
  font-weight: 700;
  line-height: 22px;
}

.sso-admin-badge.is-disabled {
  background: #f1f5f9;
  color: #64748b;
}

.sso-admin-badge.is-error {
  background: #fee2e2;
  color: #991b1b;
}

.sso-admin-client__uris {
  display: grid;
  gap: 6px;

  code {
    padding: 7px 9px;
    border-radius: 6px;
    background: #eef2f7;
    color: #0f172a;
    font-size: 12px;
    line-height: 18px;
    overflow-wrap: anywhere;
  }
}

.sso-admin-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  color: #475569;
  font-size: 13px;
  line-height: 20px;

  > div {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }
}

.sso-admin-issues {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;

  span {
    border: 1px solid #fed7aa;
    border-radius: 6px;
    padding: 4px 8px;
    background: #fff7ed;
    color: #9a3412;
    font-size: 12px;
    line-height: 18px;
    font-weight: 600;
  }
}

.sso-admin-form {
  display: grid;
  gap: 12px;

  label {
    display: grid;
    gap: 6px;
  }

  span {
    color: #334155;
    font-size: 13px;
    font-weight: 600;
    line-height: 20px;
  }

  input,
  textarea,
  select {
    width: 100%;
    box-sizing: border-box;
    border: 1px solid #cbd5e1;
    border-radius: 6px;
    padding: 8px 10px;
    color: #0f172a;
    font-size: 13px;
    line-height: 20px;
    outline: none;
  }

  input:disabled,
  select:disabled {
    background: #f1f5f9;
    color: #64748b;
    cursor: not-allowed;
  }

  textarea {
    resize: vertical;
  }
}

.sso-admin-field-hint {
  color: #64748b;
  font-size: 12px;
  line-height: 18px;

  &.is-error {
    color: #b91c1c;
  }
}

.sso-admin-callback-helper {
  display: grid;
  gap: 8px;
  border-left: 3px solid #14b8a6;
  padding: 2px 0 2px 12px;

  strong {
    display: block;
    color: #0f172a;
    font-size: 13px;
    line-height: 20px;
  }

  span,
  p {
    margin: 0;
    color: #64748b;
    font-size: 12px;
    line-height: 18px;
  }

  code {
    border-radius: 6px;
    background: #eef2f7;
    padding: 7px 9px;
    color: #0f172a;
    font-size: 12px;
    line-height: 18px;
    overflow-wrap: anywhere;
  }
}

.sso-admin-link-button {
  width: fit-content;
  min-height: 30px;
  border: 1px solid #99f6e4;
  border-radius: 6px;
  background: #f0fdfa;
  color: #0f766e;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
  padding: 5px 10px;
  transition:
    background-color 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;

  &:disabled {
    border-color: #e2e8f0;
    background: #f8fafc;
    color: #94a3b8;
    cursor: not-allowed;
  }

  &:not(:disabled):hover {
    border-color: #5eead4;
    background: #ccfbf1;
    color: #115e59;
  }
}

.sso-admin-check {
  display: flex !important;
  grid-template-columns: none !important;
  align-items: center;
  gap: 8px !important;

  input {
    width: 16px;
    height: 16px;
  }
}

.sso-admin-form-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.sso-admin-action-button {
  min-height: 36px;
  border: 1px solid transparent;
  border-radius: 6px;
  padding: 7px 14px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
  transition:
    background-color 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;

  &:disabled {
    cursor: not-allowed;
    opacity: 0.65;
  }

  &.is-primary {
    background: #14b8a6;
    color: #fff;
  }

  &.is-primary:not(:disabled):hover {
    background: #0d9488;
  }
}

.sso-admin-action-button--sm {
  min-height: 32px;
  padding: 5px 11px;
  font-size: 12px;
  line-height: 18px;
}

.sso-admin-secret {
  display: grid;
  gap: 8px;
  margin-top: 16px;
  border: 1px solid #bae6fd;
  border-radius: 8px;
  padding: 12px;
  background: #f0f9ff;

  .sso-admin-secret__item {
    display: grid;
    gap: 4px;
  }

  span {
    color: #075985;
    font-size: 12px;
    font-weight: 700;
  }

  code {
    color: #0f172a;
    font-size: 12px;
    line-height: 18px;
    overflow-wrap: anywhere;
  }
}

@media (max-width: 980px) {
  .sso-admin-shell {
    padding: 16px;
  }

  .sso-admin-shell__header,
  .sso-admin-mode-strip,
  .sso-admin-grid {
    display: grid;
    grid-template-columns: 1fr;
  }

  .sso-admin-mode-badge {
    width: fit-content;
  }

  .sso-admin-client__main,
  .sso-admin-client__meta,
  .sso-admin-client__actions,
  .sso-admin-audit-filters,
  .sso-admin-pagination,
  .sso-admin-role__main,
  .sso-admin-role__meta,
  .sso-admin-user__main,
  .sso-admin-user__meta,
  .sso-admin-audit__main,
  .sso-admin-audit__meta,
  .sso-admin-audit-cleanup__record {
    align-items: flex-start;
    display: grid;
  }

  .sso-admin-audit-filters {
    grid-template-columns: 1fr;
  }

  .sso-admin-audit-filters__actions,
  .sso-admin-audit-cleanup__actions,
  .sso-admin-audit-cleanup__status,
  .sso-admin-pagination > div {
    justify-content: flex-start;
  }

  .sso-admin-audit-cleanup__header {
    align-items: flex-start;
    display: grid;
  }
}
</style>
