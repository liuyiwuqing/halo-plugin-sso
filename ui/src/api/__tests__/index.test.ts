import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@halo-dev/api-client', () => ({
  axiosInstance: {
    get: mocks.get,
    post: mocks.post,
    delete: mocks.delete,
  },
}))

import { ssoClientApi, ssoRoleOptionApi } from '../index'

describe('ssoRoleOptionApi', () => {
  beforeEach(() => {
    mocks.get.mockReset()
    mocks.post.mockReset()
    mocks.delete.mockReset()
  })

  it('creates clients without sending a caller supplied client id', async () => {
    mocks.post.mockResolvedValueOnce({
      data: {
        client: {
          clientId: 'sso-generated-client',
          displayName: 'B 站',
          siteUrl: 'https://b.example.com',
          redirectUris: [
            'https://b.example.com/apis/public.sso.muyin.site/v1alpha1/client/callback',
          ],
          enabled: true,
        },
        clientSecret: 'secret-001',
      },
    })

    const result = await ssoClientApi.create({
      displayName: 'B 站',
      siteUrl: 'https://b.example.com',
      redirectUris: ['https://b.example.com/apis/public.sso.muyin.site/v1alpha1/client/callback'],
      enabled: true,
    })

    expect(mocks.post).toHaveBeenCalledWith(
      '/apis/console.sso.muyin.site/v1alpha1/clients/create',
      {
        displayName: 'B 站',
        siteUrl: 'https://b.example.com',
        redirectUris: ['https://b.example.com/apis/public.sso.muyin.site/v1alpha1/client/callback'],
        enabled: true,
      },
    )
    expect(result.client.clientId).toBe('sso-generated-client')
    expect(result.clientSecret).toBe('secret-001')
  })

  it('deletes clients by client id', async () => {
    mocks.delete.mockResolvedValueOnce({ data: undefined })

    await ssoClientApi.remove('site-b')

    expect(mocks.delete).toHaveBeenCalledWith(
      '/apis/console.sso.muyin.site/v1alpha1/clients/site-b',
    )
  })

  it('loads center roles through same-origin console proxy', async () => {
    mocks.get.mockResolvedValueOnce({
      data: [
        {
          name: 'author',
          displayName: '作者',
          module: '内容管理',
        },
      ],
    })

    const options = await ssoRoleOptionApi.listCenterRoles()

    expect(mocks.get).toHaveBeenCalledWith(
      '/apis/console.sso.muyin.site/v1alpha1/settings/center-roles',
    )
    expect(options).toEqual([
      {
        label: '作者（author）',
        value: 'author',
      },
    ])
  })

  it('loads local roles from current site role api without role templates', async () => {
    mocks.get.mockResolvedValueOnce({
      data: {
        items: [
          {
            metadata: {
              name: 'authenticated',
              annotations: {
                'rbac.authorization.halo.run/display-name': '登录用户',
              },
            },
          },
        ],
      },
    })

    const options = await ssoRoleOptionApi.listLocalRoles()

    expect(mocks.get).toHaveBeenCalledWith('/api/v1alpha1/roles', {
      params: {
        labelSelector: '!halo.run/role-template',
      },
    })
    expect(options).toEqual([
      {
        label: '登录用户（authenticated）',
        value: 'authenticated',
      },
    ])
  })
})
