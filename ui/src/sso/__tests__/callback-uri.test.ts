import { describe, expect, it } from 'vitest'

import {
  CLIENT_CALLBACK_PATH,
  buildClientCallbackUri,
  isAllowedSsoUrl,
} from '../callback-uri'

describe('callback uri helpers', () => {
  it('builds the client callback uri with the public API callback path', () => {
    expect(buildClientCallbackUri('https://b.example.com/')).toBe(
      'https://b.example.com/apis/public.sso.muyin.site/v1alpha1/client/callback',
    )
    expect(CLIENT_CALLBACK_PATH).toBe(
      '/apis/public.sso.muyin.site/v1alpha1/client/callback',
    )
  })

  it('matches backend URL policy for HTTPS and localhost HTTP', () => {
    expect(isAllowedSsoUrl('https://b.example.com')).toBe(true)
    expect(isAllowedSsoUrl('http://127.0.0.1:8090')).toBe(true)
    expect(isAllowedSsoUrl('http://localhost:8090')).toBe(true)
    expect(isAllowedSsoUrl('http://b.example.com')).toBe(false)
  })
})
