/**
 * 카카오맵 지오코딩 — 주소 검색 실패 시 키워드(장소) 검색으로 보완
 */

/** 한반도 대략 중심 (제주 고정 방지용 기본값) */
export const DEFAULT_KOREA_CENTER = { lat: 36.34, lng: 127.77 }

/**
 * @returns {Promise<{ lat: number, lng: number } | null>}
 */
export function getUserLatLng() {
  return new Promise((resolve) => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      resolve(null)
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude })
      },
      () => resolve(null),
      { enableHighAccuracy: false, timeout: 10_000, maximumAge: 300_000 },
    )
  })
}

/**
 * "A -> B" 형태면 구간별로 나눠 순서대로 시도
 */
function spotQuerySegments(raw) {
  const s = String(raw ?? '').trim()
  if (!s) return []
  const parts = s.split(/\s*(?:->|→|➝|—|-|–)\s*/).map((p) => p.trim()).filter(Boolean)
  return parts.length ? parts : [s]
}

/**
 * 주소 검색만
 */
export function geocodeAddressOnly(kakao, query) {
  return new Promise((resolve) => {
    const q = String(query ?? '').trim()
    if (!q) {
      resolve(null)
      return
    }
    const geocoder = new kakao.maps.services.Geocoder()
    geocoder.addressSearch(q, (result, status) => {
      if (status !== kakao.maps.services.Status.OK || !result?.length) {
        resolve(null)
        return
      }
      resolve({ lat: Number(result[0].y), lng: Number(result[0].x) })
    })
  })
}

/**
 * 키워드(장소) 검색 — 역·시장 등 POI
 */
function keywordSearchPlace(kakao, query) {
  return new Promise((resolve) => {
    const q = String(query ?? '').trim()
    if (!q) {
      resolve(null)
      return
    }
    if (!kakao.maps.services.Places) {
      resolve(null)
      return
    }
    const ps = new kakao.maps.services.Places()
    ps.keywordSearch(q, (data, status) => {
      if (status !== kakao.maps.services.Status.OK || !data?.length) {
        resolve(null)
        return
      }
      resolve({ lat: Number(data[0].y), lng: Number(data[0].x) })
    })
  })
}

/**
 * 주소 → 키워드 순으로 시도. regionHint가 있으면 검색어 뒤에 붙여 재시도
 * @param {object} kakao
 * @param {string} query
 * @param {{ regionHint?: string }} [options]
 */
export async function resolveLatLng(kakao, query, options = {}) {
  const regionHint = String(options.regionHint ?? '').trim()
  const segments = spotQuerySegments(query)
  const tries = []
  for (const seg of segments) {
    tries.push(seg)
    if (regionHint && !seg.includes(regionHint)) tries.push(`${seg} ${regionHint}`)
  }
  const unique = [...new Set(tries.map((t) => t.trim()).filter(Boolean))]

  for (const t of unique) {
    let p = await geocodeAddressOnly(kakao, t)
    if (p) return p
    p = await keywordSearchPlace(kakao, t)
    if (p) return p
  }
  return null
}
