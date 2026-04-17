const API_BASE = normalizeApiBase(import.meta.env.VITE_API_BASE_URL ?? '/api')

function normalizeApiBase(rawBase) {
  if (!rawBase) return '/api'
  const trimmed = rawBase.trim()
  if (!trimmed) return '/api'
  return trimmed.replace(/\/+$/, '')
}

function joinApiUrl(path) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${API_BASE}${normalizedPath}`
}

function getStoredToken() {
  return localStorage.getItem('localguest_access_token')
}

/** @type {null | (() => void)} */
let unauthorizedHandler = null

/**
 * Register a global handler invoked when an authenticated request returns 401.
 * This is intentionally UI-agnostic; AuthProvider wires it to logout UX.
 * @param {null | (() => void)} handler
 */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = typeof handler === 'function' ? handler : null
}

export class ApiError extends Error {
  /** @param {string} message @param {{ status?: number, code?: string, raw?: unknown }} [meta] */
  constructor(message, meta) {
    super(message)
    this.name = 'ApiError'
    this.status = meta?.status
    this.code = meta?.code
    this.raw = meta?.raw
  }
}

/**
 * @param {number} status
 * @param {unknown} payload
 */
export function toUserErrorMessage(status, payload) {
  if (payload && typeof payload === 'object') {
    // Spring error wrapper often uses { message }, sometimes { errorCode }
    // eslint-disable-next-line no-unused-vars
    const p = /** @type {{ message?: unknown }} */ (payload)
    if (typeof p.message === 'string' && p.message.trim()) return p.message.trim()
  }
  if (typeof payload === 'string' && payload.trim()) return payload.trim()

  if (status === 401) return '로그인이 만료됐어요. 다시 로그인해주세요.'
  if (status === 403) return '권한이 없어요.'
  if (status >= 500) return '잠시 후 다시 시도해주세요.'
  return '요청을 처리할 수 없습니다.'
}

async function readJsonOrText(res) {
  const text = (await res.text()).trim()
  if (!text) return { text: '', json: null }
  if (!text.startsWith('{') && !text.startsWith('[')) return { text, json: null }
  try {
    return { text, json: JSON.parse(text) }
  } catch {
    return { text, json: null }
  }
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

  const res = await fetch(joinApiUrl(path), {
    ...rest,
    headers,
    body: json !== undefined ? JSON.stringify(json) : rest.body,
  })

  // 가이드 신청(프로필 생성) 직후 재로그인 시 /guide/apply 로 돌아가지 않도록 로그인 목적지 힌트 저장
  const method = String(rest.method ?? 'GET').toUpperCase()
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  if (res.ok && method === 'POST' && normalizedPath === '/guides') {
    try {
      sessionStorage.setItem('localguest_login_return_override', '/guide/mypage/fees')
    } catch {
      /* ignore */
    }
  }

  if (!skipAuth && res.status === 401 && unauthorizedHandler) {
    try {
      unauthorizedHandler()
    } catch {
      /* ignore */
    }
  }

  return res
}

/**
 * apiRequest + error normalization (throws ApiError when !ok).
 * @param {string} path
 * @param {RequestInit & { json?: unknown, skipAuth?: boolean }} [options]
 */
export async function apiRequestOrThrow(path, options = {}) {
  const res = await apiRequest(path, options)
  if (res.ok) return res
  const { text, json } = await readJsonOrText(res)
  const payload = json ?? text
  throw new ApiError(toUserErrorMessage(res.status, payload), { status: res.status, raw: payload })
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

/**
 * 회원가입 단계 — 이메일 인증번호 발송 (백엔드 구현 후 `SecurityConfig`에 permitAll 필요)
 * POST /members/email-verification/send  body: { email }
 * 응답 예: { "expiresInSeconds": 180 } 또는 본문 없음(기본 180초)
 * @param {string} email
 * @returns {Promise<{ expiresInSeconds: number }>}
 */
export async function sendSignupEmailVerification(email) {
  const res = await apiRequest('/members/email-verification/send', {
    method: 'POST',
    json: { email },
    skipAuth: true,
  })
  const text = (await res.text()).trim()
  if (!res.ok) {
    let payload = text
    if (text?.startsWith('{') || text?.startsWith('[')) {
      try {
        payload = JSON.parse(text)
      } catch {
        payload = text
      }
    }
    throw new Error(toUserErrorMessage(res.status, payload))
  }
  if (!text) return { expiresInSeconds: 180 }
  try {
    const j = JSON.parse(text)
    const sec = Number(j?.expiresInSeconds)
    return { expiresInSeconds: Number.isFinite(sec) && sec > 0 ? sec : 180 }
  } catch {
    return { expiresInSeconds: 180 }
  }
}

/**
 * 회원가입 단계 — 이메일 인증번호 확인
 * POST /members/email-verification/confirm  body: { email, code }
 * @param {string} email
 * @param {string} code
 */
export async function confirmSignupEmailVerification(email, code) {
  const res = await apiRequest('/members/email-verification/confirm', {
    method: 'POST',
    json: { email, code },
    skipAuth: true,
  })
  const text = (await res.text()).trim()
  if (!res.ok) {
    let payload = text
    if (text?.startsWith('{') || text?.startsWith('[')) {
      try {
        payload = JSON.parse(text)
      } catch {
        payload = text
      }
    }
    throw new Error(toUserErrorMessage(res.status, payload))
  }
}

/**
 * 닉네임(아이디) 사용 가능 여부 — 가입 전 조회
 * GET /members/nickname-availability?nickname=
 * 응답 예: { "available": true }
 * @param {string} nickname
 * @returns {Promise<boolean>}
 */
export async function fetchNicknameAvailable(nickname) {
  const q = encodeURIComponent(nickname.trim())
  const res = await apiRequest(`/members/nickname-availability?nickname=${q}`, {
    method: 'GET',
    skipAuth: true,
  })
  const text = (await res.text()).trim()
  if (!res.ok) {
    let payload = text
    if (text?.startsWith('{') || text?.startsWith('[')) {
      try {
        payload = JSON.parse(text)
      } catch {
        payload = text
      }
    }
    throw new Error(toUserErrorMessage(res.status, payload))
  }
  if (!text) return false
  try {
    const j = JSON.parse(text)
    return Boolean(j?.available)
  } catch {
    return false
  }
}

/**
 * Google OAuth2 로그인 시작.
 * - 백엔드 CustomOauth2UserService가 role 쿠키를 읽으므로 짧게 저장한다.
 * - 콜백 이후 이동 경로를 sessionStorage에 저장한다.
 * @param {'GUEST' | 'GUIDE' | string} role
 * @param {string} [returnTo]
 */
export function beginGoogleOAuth(role = 'GUEST', returnTo = '/mypage') {
  const normalizedRole = String(role || 'GUEST').toUpperCase() === 'GUIDE' ? 'GUIDE' : 'GUEST'
  try {
    document.cookie = `role=${normalizedRole}; path=/; max-age=600; samesite=lax`
    sessionStorage.setItem('oauth_return_to', returnTo)
  } catch {
    /* ignore */
  }
  window.location.href = joinApiUrl('/oauth2/authorization/google')
}
