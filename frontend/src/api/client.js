const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api'

function getStoredToken() {
  return localStorage.getItem('localguest_access_token')
}

/**
 * @param {string} path
 * @param {RequestInit & { json?: unknown, skipAuth?: boolean }} [options]
 */
export async function apiRequest(path, options = {}) {
  const { json, skipAuth, headers: initHeaders, ...rest } = options
  const headers = new Headers(initHeaders)

  if (json !== undefined) {
    headers.set('Content-Type', 'application/json')
  }

  if (!skipAuth) {
    const token = getStoredToken()
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }
  }

  const res = await fetch(`${API_BASE}${path}`, {
    ...rest,
    headers,
    body: json !== undefined ? JSON.stringify(json) : rest.body,
  })

  return res
}

/**
 * 로그인 요청/응답:
 * - 요청: { email, password, role }
 * - 응답: TokenResponse(JSON) 또는 plain JWT 문자열(레거시 호환)
 * @param {string} path
 * @param {{ email: string, password: string, role?: 'GUEST' | 'GUIDE' | string }} body
 */
export async function apiLogin(path, body) {
  const res = await apiRequest(path, {
    method: 'POST',
    json: body,
    skipAuth: true,
  })
  const text = (await res.text()).trim()
  return { res, text }
}
