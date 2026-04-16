/**
 * @param {string} text
 */
export function parseMatchingApiError(text) {
  if (!text) return '요청 실패'
  try {
    const j = JSON.parse(text)
    return (j.message ?? text) || '요청 실패'
  } catch {
    return text || '요청 실패'
  }
}

async function fetchWithFallback(apiRequest, paths) {
  let lastText = ''
  for (const path of paths) {
    const res = await apiRequest(path, { method: 'GET' })
    const text = await res.text()
    if (res.ok) {
      return text ? JSON.parse(text) : []
    }
    // 인증/권한 오류는 즉시 반환한다.
    if (res.status === 401 || res.status === 403) {
      throw new Error(parseMatchingApiError(text))
    }
    lastText = text
  }
  throw new Error(parseMatchingApiError(lastText))
}

/** @param {(path: string, init?: object) => Promise<Response>} apiRequest */
export async function fetchGuestMatchRequests(apiRequest) {
  return fetchWithFallback(apiRequest, [
    '/matching/requests/guest/list',
    // 구버전 호환 경로
    '/matching/requests/me',
  ])
}

/** @param {(path: string, init?: object) => Promise<Response>} apiRequest */
export async function fetchGuideMatchRequests(apiRequest) {
  return fetchWithFallback(apiRequest, [
    '/matching/requests/guide/list',
    // 구버전 호환 경로
    '/matching/requests/guide',
    '/matching/requests/guide/me',
  ])
}

/** @param {(path: string, init?: object) => Promise<Response>} apiRequest */
export async function fetchGuestPayments(apiRequest) {
  return fetchWithFallback(apiRequest, [
    '/matching/payments/guest/list',
    // 구버전 호환 경로
    '/matching/payments/me',
  ])
}

/**
 * @param {string} dateStr `YYYY-MM-DD`
 */
export function daysUntil(dateStr) {
  if (!dateStr) return null
  const d = new Date(`${dateStr}T00:00:00`)
  if (Number.isNaN(d.getTime())) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const diff = Math.round((d.getTime() - today.getTime()) / 86400000)
  return diff
}
