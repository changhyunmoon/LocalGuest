function parseApiErrorMessage(text) {
  if (!text) return '요청 실패'
  try {
    const j = JSON.parse(text)
    return (j.message ?? text) || '요청 실패'
  } catch {
    return text || '요청 실패'
  }
}

/**
 * 매칭 요청 ID → 내가 작성한 리뷰 한 건(첫 페이지 순 기준).
 * @param {(path: string, init?: object) => Promise<Response>} apiRequest
 * @returns {Promise<Record<number, object>>}
 */
export async function fetchMyReviewsByMatchRequestId(apiRequest) {
  const map = {}
  let page = 0
  while (page < 20) {
    const res = await apiRequest(`/reviews/me?page=${page}&size=50`, { method: 'GET' })
    const text = await res.text()
    if (!res.ok) throw new Error(parseApiErrorMessage(text))
    const data = text ? JSON.parse(text) : {}
    const content = Array.isArray(data.content) ? data.content : []
    for (const row of content) {
      const key = Number(row?.matchRequestId)
      if (!Number.isNaN(key) && map[key] == null) map[key] = row
    }
    if (data.last === true) break
    page += 1
  }
  return map
}
