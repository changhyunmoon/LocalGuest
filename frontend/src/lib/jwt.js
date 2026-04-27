function base64UrlToJson(segment) {
  const base64 = segment.replace(/-/g, '+').replace(/_/g, '/')
  const pad = base64.length % 4 === 0 ? '' : '='.repeat(4 - (base64.length % 4))
  const json = atob(base64 + pad)
  return JSON.parse(json)
}

/**
 * @param {string} token
 * @returns {{ sub?: string, role?: string, exp?: number } | null}
 */
export function parseJwtPayload(token) {
  if (!token || typeof token !== 'string') return null
  const parts = token.split('.')
  if (parts.length < 2) return null
  try {
    return base64UrlToJson(parts[1])
  } catch {
    return null
  }
}

/**
 * `GUEST` | `GUIDE` 로 통일해 반환한다. (이메일 로그인 JWT는 `GUEST`/`GUIDE`, OAuth는 `ROLE_*` 를 쓰는 경우가 있어 둘 다 수용)
 * @param {string} token
 * @returns {string | null}
 */
export function getRoleFromToken(token) {
  const p = parseJwtPayload(token)
  if (!p?.role) return null
  let r = String(p.role).trim().toUpperCase()
  if (r.startsWith('ROLE_')) r = r.slice(5)
  return r
}

/** 백엔드가 싣는 최초 소셜(게스트) 가입용 플래그 */
export function getNeedsTravelOnboardingFromToken(token) {
  const p = parseJwtPayload(token)
  return p?.onboarding === true
}

export function getEmailFromToken(token) {
  const p = parseJwtPayload(token)
  return p?.sub ?? null
}
