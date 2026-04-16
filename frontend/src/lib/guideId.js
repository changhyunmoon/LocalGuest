import { parseJwtPayload } from './jwt.js'

const STORAGE_KEY = 'localguest_guide_id'
const STORAGE_OWNER_MEMBER_KEY = 'localguest_guide_owner_member_id'
const STORAGE_OWNER_EMAIL_KEY = 'localguest_guide_owner_email'
const TOKEN_KEY = 'localguest_access_token'

export function getStoredGuideId() {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw || !/^\d+$/.test(raw)) {
    return null
  }

  const ownerMember = localStorage.getItem(STORAGE_OWNER_MEMBER_KEY)
  const ownerEmail = localStorage.getItem(STORAGE_OWNER_EMAIL_KEY)
  const auth = readAuthOwnerFromTokenClaims()

  // 계정이 바뀐 상태면 이전 계정의 guideId 캐시를 무효화한다.
  if (auth) {
    if (ownerMember && auth.memberId != null && ownerMember !== String(auth.memberId)) {
      clearStoredGuideId()
      return null
    }
    if (ownerEmail && auth.email && ownerEmail !== auth.email) {
      clearStoredGuideId()
      return null
    }
  }

  return Number(raw)
}

export function setStoredGuideId(guideId) {
  if (guideId != null && Number.isFinite(Number(guideId))) {
    localStorage.setItem(STORAGE_KEY, String(guideId))
    const auth = readAuthOwnerFromTokenClaims()
    if (auth?.memberId != null) {
      localStorage.setItem(STORAGE_OWNER_MEMBER_KEY, String(auth.memberId))
    }
    if (auth?.email) {
      localStorage.setItem(STORAGE_OWNER_EMAIL_KEY, auth.email)
    }
  }
}

export function clearStoredGuideId() {
  localStorage.removeItem(STORAGE_KEY)
  localStorage.removeItem(STORAGE_OWNER_MEMBER_KEY)
  localStorage.removeItem(STORAGE_OWNER_EMAIL_KEY)
}

function toFiniteNumber(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

function readGuideIdFromTokenClaims() {
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token) return null
  const claims = parseJwtPayload(token)
  if (!claims || typeof claims !== 'object') return null

  // Backend claim naming can vary by environment/version.
  const directGuideKeys = ['guideId', 'guide_id', 'guideProfileId', 'profileId', 'gid']
  for (const key of directGuideKeys) {
    const n = toFiniteNumber(claims[key])
    if (n != null) return n
  }
  return null
}

function readMemberIdFromTokenClaims() {
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token) return null
  const claims = parseJwtPayload(token)
  if (!claims || typeof claims !== 'object') return null

  const memberKeys = ['memberId', 'member_id', 'userId', 'user_id', 'id']
  for (const key of memberKeys) {
    const n = toFiniteNumber(claims[key])
    if (n != null) return n
  }
  return null
}

function readAuthOwnerFromTokenClaims() {
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token) return null
  const claims = parseJwtPayload(token)
  if (!claims || typeof claims !== 'object') return null
  const memberId = toFiniteNumber(
    claims.memberId ?? claims.member_id ?? claims.userId ?? claims.user_id ?? claims.id,
  )
  const email = typeof claims.sub === 'string' ? claims.sub : null
  return { memberId, email }
}

/**
 * @param {(path: string, init?: RequestInit) => Promise<Response>} apiRequest
 */
export async function resolveGuideId(apiRequest) {
  const stored = getStoredGuideId()
  if (stored != null) {
    return stored
  }

  const fromToken = readGuideIdFromTokenClaims()
  if (fromToken != null) {
    setStoredGuideId(fromToken)
    return fromToken
  }

  // Most reliable path: authenticated "my guide profile" endpoint.
  try {
    const res = await apiRequest('/guides/me', { method: 'GET' })
    const text = await res.text()
    if (res.ok) {
      const body = text ? JSON.parse(text) : null
      const id = Number(body?.guideId)
      if (Number.isFinite(id)) {
        setStoredGuideId(id)
        return id
      }
    }
  } catch {
    /* ignore */
  }

  try {
    const candidatePaths = ['/matching/requests/guide/list', '/matching/requests/guide', '/matching/requests/guide/me']
    for (const path of candidatePaths) {
      const res = await apiRequest(path, { method: 'GET' })
      const text = await res.text()
      if (!res.ok) {
        // 권한 오류는 즉시 중단하고, 그 외에는 다음 fallback 경로를 시도한다.
        if (res.status === 401 || res.status === 403) {
          return null
        }
        continue
      }
      const arr = text ? JSON.parse(text) : []
      if (Array.isArray(arr) && arr.length > 0 && arr[0].guideId != null) {
        const id = Number(arr[0].guideId)
        if (Number.isFinite(id)) {
          setStoredGuideId(id)
          return id
        }
      }
    }
  } catch {
    /* ignore */
  }

  // Fallback: derive guideId by matching token's memberId with /guides list.
  const memberId = readMemberIdFromTokenClaims()
  if (memberId != null) {
    try {
      const res = await apiRequest('/guides', { method: 'GET', skipAuth: true })
      const text = await res.text()
      if (res.ok) {
        const arr = text ? JSON.parse(text) : []
        if (Array.isArray(arr)) {
          const matched = arr.find((g) => Number(g?.memberId) === memberId && g?.guideId != null)
          if (matched) {
            const id = Number(matched.guideId)
            if (Number.isFinite(id)) {
              setStoredGuideId(id)
              return id
            }
          }
        }
      }
    } catch {
      /* ignore */
    }
  }

  return null
}
