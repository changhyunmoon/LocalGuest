/**
 * Spring `Page<>` / 커스텀 래퍼 등 응답 차이를 흡수해 리뷰 배열로 만든다.
 * @param {unknown} page
 * @returns {Array<Record<string, unknown>>}
 */
export function extractReviewListFromPage(page) {
  if (!page || typeof page !== 'object') return []
  if (Array.isArray(page)) return page
  const p = /** @type {Record<string, unknown>} */ (page)
  if (Array.isArray(p.content)) return p.content
  if (Array.isArray(p.reviews)) return p.reviews
  if (p.data && typeof p.data === 'object' && Array.isArray(/** @type {any} */ (p.data).content)) {
    return /** @type {any[]} */ (p.data).content
  }
  return []
}

/**
 * @param {Record<string, unknown>} r
 * @param {number} idx
 */
export function reviewStableKey(r, idx) {
  if (r == null) return `r-${idx}`
  const id = r.id ?? r.reviewId ?? r.matchRequestId
  if (id != null && id !== '') return `r-${String(id)}`
  return `r-${idx}`
}
