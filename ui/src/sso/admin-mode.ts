export type SsoRuntimeMode = 'center' | 'client'

export type SsoAdminTabKey = 'clients' | 'roleMappings' | 'userBindings' | 'audit'

export interface SsoAdminTab {
  key: SsoAdminTabKey
  label: string
  description: string
}

const centerTabs: SsoAdminTab[] = [
  {
    key: 'clients',
    label: '接入站',
    description: '维护中心站信任的 Halo 站点。',
  },
  {
    key: 'audit',
    label: '审计日志',
    description: '查看中心站授权和接入站回调结果。',
  },
]

const clientTabs: SsoAdminTab[] = [
  {
    key: 'roleMappings',
    label: '角色映射',
    description: '把中心标准角色映射为本站本地角色。',
  },
  {
    key: 'userBindings',
    label: '用户绑定',
    description: '查看中心身份用户和本站用户的绑定关系。',
  },
  {
    key: 'audit',
    label: '审计日志',
    description: '排查本站 SSO 登录、回调和用户绑定结果。',
  },
]

export function getAdminTabs(mode: string): SsoAdminTab[] {
  return mode === 'client' ? clientTabs : centerTabs
}

export function getDefaultAdminTab(mode: string): SsoAdminTabKey {
  return getAdminTabs(mode)[0].key
}

export function getModeLabel(mode: string) {
  return mode === 'client' ? '接入站模式' : '身份中心模式'
}
