/**
 * 카카오맵 JS SDK 동적 로드 (여러 페이지 공통).
 * @param {string} appKey
 * @returns {Promise<typeof window.kakao>}
 */
export function loadKakaoSdk(appKey) {
  if (!appKey) return Promise.reject(new Error('카카오맵 앱 키가 없습니다.'))
  if (window.kakao?.maps?.services) return Promise.resolve(window.kakao)
  return new Promise((resolve, reject) => {
    const existing = document.getElementById('kakao-map-sdk')
    if (existing) {
      existing.addEventListener('load', () => window.kakao.maps.load(() => resolve(window.kakao)))
      existing.addEventListener('error', () => reject(new Error('카카오맵 SDK 로드 실패')))
      return
    }
    const script = document.createElement('script')
    script.id = 'kakao-map-sdk'
    script.async = true
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false&libraries=services`
    script.onload = () => window.kakao.maps.load(() => resolve(window.kakao))
    script.onerror = () => reject(new Error('카카오맵 SDK 로드 실패'))
    document.head.appendChild(script)
  })
}
