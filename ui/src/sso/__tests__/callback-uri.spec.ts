import { describe, expect, it } from 'vitest'

import {
  addClientCallbackUri,
  buildClientCallbackUri,
  getSsoClientUrlIssues,
} from '../callback-uri'

describe('SSO client callback URI', () => {
  it('accepts exact callbacks from multiple domains of the same site', () => {
    const issues = getSsoClientUrlIssues('https://blog.muyin.site', [
      buildClientCallbackUri('https://blog.muyin.site'),
      buildClientCallbackUri('https://lywq.muyin.site'),
    ])

    expect(issues).toEqual([])
  })

  it('adds an alias-domain callback once and normalizes its trailing slash', () => {
    const primaryCallback = buildClientCallbackUri('https://blog.muyin.site')
    const aliasCallback = buildClientCallbackUri('https://lywq.muyin.site')

    const withAlias = addClientCallbackUri([primaryCallback], ' https://lywq.muyin.site/ ')

    expect(withAlias).toEqual([primaryCallback, aliasCallback])
    expect(addClientCallbackUri(withAlias, 'https://lywq.muyin.site')).toEqual(withAlias)
  })
})
