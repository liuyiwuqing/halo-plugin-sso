export const CLIENT_CALLBACK_PATH = '/apis/public.sso.muyin.site/v1alpha1/client/callback'

export function normalizeSiteUrl(value: string) {
  return value.trim().replace(/\/+$/, '')
}

export function buildClientCallbackUri(siteUrl: string) {
  const normalized = normalizeSiteUrl(siteUrl)
  return normalized ? `${normalized}${CLIENT_CALLBACK_PATH}` : ''
}

export function isAllowedSsoUrl(value: string) {
  try {
    const url = new URL(value)
    return url.protocol === 'https:' || (url.protocol === 'http:' && isLocalhost(url.hostname))
  } catch {
    return false
  }
}

function isLocalhost(hostname: string) {
  return hostname === 'localhost' || hostname === '127.0.0.1'
}
