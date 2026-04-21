import { extractTravelDnaTags, loadTravelDna } from './travelDna.js'

const LS_NICK = 'localguest_mypage_nickname'
const LS_BOOK = 'localguest_mypage_notify_booking'
const LS_MSG = 'localguest_mypage_notify_guide_msg'
const LS_TAGS = 'localguest_mypage_travel_tags'

export const DEFAULT_TRAVEL_TAGS = []

function loadBool(key, fallback) {
  const v = localStorage.getItem(key)
  if (v === '1') return true
  if (v === '0') return false
  return fallback
}

function loadString(key) {
  const v = localStorage.getItem(key)
  return v != null && v !== '' ? v : null
}

export function loadTravelTags() {
  try {
    const raw = localStorage.getItem(LS_TAGS)
    if (!raw) {
      const fromDna = extractTravelDnaTags(loadTravelDna())
      return fromDna.length > 0 ? fromDna : [...DEFAULT_TRAVEL_TAGS]
    }
    const p = JSON.parse(raw)
    if (!Array.isArray(p) || p.length === 0) {
      const fromDna = extractTravelDnaTags(loadTravelDna())
      return fromDna.length > 0 ? fromDna : [...DEFAULT_TRAVEL_TAGS]
    }
    return p.map((t) => String(t).trim()).filter(Boolean)
  } catch {
    const fromDna = extractTravelDnaTags(loadTravelDna())
    return fromDna.length > 0 ? fromDna : [...DEFAULT_TRAVEL_TAGS]
  }
}

/**
 * 마이페이지 사이드바 등에 표시할 이름. 서버 닉네임 API가 생기면 여기서 대체합니다.
 * @param {string | null | undefined} email
 */
export function getGuestDisplayName(email) {
  const n = loadString(LS_NICK)
  if (n && n.trim()) return n.trim()
  return email ? email.split('@')[0] : '게스트'
}

/**
 * @param {string | null | undefined} email
 */
export function loadGuestPrivacyForm(email) {
  const fallbackNick = email ? email.split('@')[0] : ''
  return {
    nickname: loadString(LS_NICK) ?? fallbackNick,
    bookingNotify: loadBool(LS_BOOK, true),
    guideMessageNotify: loadBool(LS_MSG, true),
    tags: loadTravelTags(),
  }
}

/**
 * @param {{ nickname: string, bookingNotify: boolean, guideMessageNotify: boolean, tags: string[] }} data
 */
export function persistGuestPrivacyForm(data) {
  const nick = data.nickname.trim()
  if (nick) {
    localStorage.setItem(LS_NICK, nick)
  } else {
    localStorage.removeItem(LS_NICK)
  }
  localStorage.setItem(LS_BOOK, data.bookingNotify ? '1' : '0')
  localStorage.setItem(LS_MSG, data.guideMessageNotify ? '1' : '0')
  localStorage.setItem(LS_TAGS, JSON.stringify(data.tags.filter(Boolean)))
}
