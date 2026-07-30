export const CLIENT_CALLBACK_PATH = '/apis/public.sso.muyin.site/v1alpha1/client/callback'

export function normalizeSiteUrl(value: string) {
  return value.trim().replace(/\/+$/, '')
}

export function buildClientCallbackUri(siteUrl: string) {
  const normalized = normalizeSiteUrl(siteUrl)
  return normalized ? `${normalized}${CLIENT_CALLBACK_PATH}` : ''
}

export function addClientCallbackUri(redirectUris: string[], siteUrl: string) {
  const normalizedRedirectUris = redirectUris.map((uri) => uri.trim()).filter(Boolean)
  if (!isAllowedSsoUrl(siteUrl)) {
    return Array.from(new Set(normalizedRedirectUris))
  }

  return Array.from(new Set([...normalizedRedirectUris, buildClientCallbackUri(siteUrl)]))
}

export function getSsoClientUrlIssues(siteUrl: string, redirectUris: string[]) {
  const issues = new Set<string>()
  const normalizedRedirectUris = redirectUris.map((uri) => uri.trim()).filter(Boolean)

  if (!isAllowedSsoUrl(siteUrl)) {
    issues.add('主站地址需使用 HTTPS 或 localhost HTTP')
  }
  if (normalizedRedirectUris.length === 0) {
    issues.add('缺少回调地址')
  }

  const primaryCallbackUri = buildClientCallbackUri(siteUrl)
  if (primaryCallbackUri && !normalizedRedirectUris.includes(primaryCallbackUri)) {
    issues.add('缺少主站域名的标准 SSO 回调地址')
  }
  if (normalizedRedirectUris.some((uri) => !isAllowedSsoUrl(uri))) {
    issues.add('回调地址需使用 HTTPS 或 localhost HTTP')
  }

  return Array.from(issues)
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
