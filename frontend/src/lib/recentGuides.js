const RECENT_GUIDES_KEY = 'localguest_recent_guides_v1'
const MAX_RECENT_GUIDES = 3

/**
 * @returns {number[]}
 */
export function readRecentGuideIds() {
  try {
    const raw = localStorage.getItem(RECENT_GUIDES_KEY)
    if (!raw) return []
    const arr = JSON.parse(raw)
    if (!Array.isArray(arr)) return []
    return arr.map((v) => Number(v)).filter((v) => Number.isFinite(v) && v > 0).slice(0, MAX_RECENT_GUIDES)
  } catch {
    return []
  }
}

/**
 * @param {number} guideId
 */
export function rememberRecentGuide(guideId) {
  const id = Number(guideId)
  if (!Number.isFinite(id) || id <= 0) return
  try {
    const prev = readRecentGuideIds().filter((v) => v !== id)
    const next = [id, ...prev].slice(0, MAX_RECENT_GUIDES)
    localStorage.setItem(RECENT_GUIDES_KEY, JSON.stringify(next))
  } catch {
    // ignore
  }
}

