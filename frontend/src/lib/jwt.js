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

export function getRoleFromToken(token) {
  const p = parseJwtPayload(token)
  if (!p?.role) return null
  return String(p.role).toUpperCase()
}

export function getEmailFromToken(token) {
  const p = parseJwtPayload(token)
  return p?.sub ?? null
}
