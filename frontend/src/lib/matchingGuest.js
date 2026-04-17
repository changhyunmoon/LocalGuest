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

/** @param {(path: string, init?: object) => Promise<Response>} apiRequest */
export async function loadGuideNicknames(apiRequest, guideIds) {
  const map = {}
  for (const id of [...new Set(guideIds.filter(Boolean))]) {
    try {
      const res = await apiRequest(`/guides/${id}`, { method: 'GET', skipAuth: true })
      const text = await res.text()
      if (res.ok && text) {
        const p = JSON.parse(text)
        map[id] = p.nickname ?? `가이드 #${id}`
      }
    } catch {
      map[id] = `가이드 #${id}`
    }
  }
  return map
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

/**
 * 매칭 요청별 완료 결제 ID (여러 건이면 가장 큰 paymentId).
 * @param {unknown[]} payments
 * @param {number} requestId
 * @returns {number | null}
 */
export function pickLatestCompletedPaymentIdForRequest(payments, requestId) {
  const rid = Number(requestId)
  if (Number.isNaN(rid)) return null
  let best = null
  for (const p of Array.isArray(payments) ? payments : []) {
    if (Number(p.requestId) !== rid) continue
    if (String(p.status).toUpperCase() !== 'COMPLETED') continue
    const pid = Number(p.paymentId)
    if (Number.isNaN(pid)) continue
    if (best == null || pid > best) best = pid
  }
  return best
}
