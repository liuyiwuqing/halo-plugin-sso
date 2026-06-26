import { describe, expect, it } from 'vitest'

import { getAdminTabs, getDefaultAdminTab, getModeLabel } from '../admin-mode'

describe('admin mode helpers', () => {
  it('shows center station tabs for center mode', () => {
    expect(getModeLabel('center')).toBe('身份中心模式')
    expect(getAdminTabs('center').map((tab) => tab.key)).toEqual(['clients', 'audit'])
    expect(getDefaultAdminTab('center')).toBe('clients')
  })

  it('shows client station tabs for client mode', () => {
    expect(getModeLabel('client')).toBe('接入站模式')
    expect(getAdminTabs('client').map((tab) => tab.key)).toEqual([
      'roleMappings',
      'userBindings',
      'audit',
    ])
    expect(getDefaultAdminTab('client')).toBe('roleMappings')
  })
})
