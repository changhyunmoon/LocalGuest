import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'

import { PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client'
import { parseCourseDetail } from './GuideCoursePanel.jsx'

import './GuideMatchedCoursePage.css'

// ── 상수 ──────────────────────────────────────────────────────────────────
const KAKAO_APP_KEY = import.meta.env.VITE_KAKAO_MAP_APP_KEY

const REGION_BASE = {
  제주: { lat: 33.4996, lng: 126.5312 },
  부산: { lat: 35.1796, lng: 129.0756 },
  강릉: { lat: 37.7519, lng: 128.8761 },
  여수: { lat: 34.7604, lng: 127.6622 },
}

// ── 유틸 (기존 GuideMatchedCoursePage와 동일) ─────────────────────────────
function loadKakaoSdk(appKey) {
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

function geocodeAddress(kakao, query) {
  return new Promise((resolve) => {
    const geocoder = new kakao.maps.services.Geocoder()
    geocoder.addressSearch(query, (result, status) => {
      if (status !== kakao.maps.services.Status.OK || !result?.length) { resolve(null); return }
      resolve({ lat: Number(result[0].y), lng: Number(result[0].x) })
    })
  })
}

function fallbackLatLng(region, idx) {
  const hit = Object.entries(REGION_BASE).find(([key]) => String(region ?? '').includes(key))
  const base = hit?.[1] ?? { lat: 33.4996, lng: 126.5312 }
  return { lat: base.lat + idx * 0.007, lng: base.lng + (idx % 2 === 0 ? 0.009 : -0.006) }
}

function createSpotOverlay(kakao, map, latlng, idx, name) {
  const root = document.createElement('div')
  root.className = 'gmc-pin'
  root.innerHTML = `<span class="gmc-pin-badge">${idx + 1}</span><span class="gmc-pin-label">${name}</span>`
  return new kakao.maps.CustomOverlay({ map, position: latlng, yAnchor: 1.25, content: root })
}

function findLatestRequest(list, guideId, requestId) {
  if (!Array.isArray(list)) return null
  if (requestId != null && !Number.isNaN(Number(requestId))) {
    return list.find((r) => Number(r.requestId) === Number(requestId)) ?? null
  }
  const rows = list.filter((r) => Number(r.guideId) === Number(guideId))
  rows.sort((a, b) => (b.createdAt ? new Date(b.createdAt).getTime() : 0) - (a.createdAt ? new Date(a.createdAt).getTime() : 0))
  return rows[0] ?? null
}

// ── 메인 컴포넌트 ─────────────────────────────────────────────────────────
export function GuideMatchedCoursePage() {
  const { guideId } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const mapRef = useRef(null)

  const requestId = searchParams.get('requestId')
  const paymentId = searchParams.get('paymentId')

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [profile, setProfile] = useState(null)
  const [scheduleId, setScheduleId] = useState(null)
  // schedule form 데이터 (백엔드 GuideScheduleFormResponse)
  const [formData, setFormData] = useState(null)
  const [mapErr, setMapErr] = useState('')

  // ── 데이터 로드 ─────────────────────────────────────────────────────────
  // 변경 포인트:
  //   기존 → matching/requests/guest/list의 proposedSchedule/proposeMessage 파싱
  //   변경 → scheduleId 특정 후 GET /guides/{guideId}/schedules/{scheduleId}/form 호출
  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      // 1) 가이드 프로필
      const [detailRes, reqRes] = await Promise.all([
        apiRequest(`/guides/${guideId}/detail`, { method: 'GET', skipAuth: true }),
        apiRequest('/matching/requests/guest/list', { method: 'GET' }),
      ])
      const detailText = await detailRes.text()
      if (!detailRes.ok) throw new Error(detailText || '가이드 정보를 불러오지 못했습니다.')
      const detail = detailText ? JSON.parse(detailText) : null
      setProfile(detail?.profile ?? null)

      // 2) 매칭 요청에서 scheduleId 추출
      let sid = null
      if (reqRes.ok) {
        const reqText = await reqRes.text()
        const list = reqText ? JSON.parse(reqText) : []
        const req = findLatestRequest(list, guideId, requestId)
        // scheduleId는 매칭 요청 응답에 포함되어 있어야 함
        // (matching 도메인 팀에서 scheduleId를 응답에 포함하는 것 전제)
        sid = req?.scheduleId != null ? Number(req.scheduleId) : null
        setScheduleId(sid)
      }

      // 3) schedule form 조회 (scheduleId가 있을 때만)
      if (sid != null) {
        const formRes = await apiRequest(
          `/guides/${guideId}/schedules/${sid}/form`,
          { method: 'GET' },
        )
        const formText = await formRes.text()
        if (formRes.ok) {
          setFormData(formText ? JSON.parse(formText) : null)
        } else {
          // 404 등 → formData null (결제 전이거나 아직 미작성)
          setFormData(null)
        }
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '오류')
    } finally {
      setLoading(false)
    }
  }, [guideId, requestId])

  useEffect(() => { void load() }, [load])

  // ── isPaid 판단 ─────────────────────────────────────────────────────────
  // 백엔드 GuideScheduleFormResponse: isPaid=false이면 courseDetail=null으로 마스킹
  const isPaid = formData?.isPaid === true
  const meetingPoint = formData?.meetingPoint ?? ''
  const guideMessage = formData?.guideMessage ?? ''
  // courseDetail: isPaid=true일 때만 내려옴
  const spots = useMemo(() => {
    if (!isPaid || !formData?.courseDetail) return []
    return parseCourseDetail(formData.courseDetail)
  }, [isPaid, formData])

  // ── 지도 ───────────────────────────────────────────────────────────────
  useEffect(() => {
    if (!mapRef.current) return
    // isPaid=false면 지도에 마커 없이 흐린 처리만
    if (!isPaid || spots.length === 0) return
    let cancelled = false

    const draw = async () => {
      try {
        setMapErr('')
        const kakao = await loadKakaoSdk(KAKAO_APP_KEY)
        if (cancelled || !mapRef.current) return

        const center = fallbackLatLng(profile?.region, 0)
        const map = new kakao.maps.Map(mapRef.current, {
          center: new kakao.maps.LatLng(center.lat, center.lng),
          level: 8,
        })

        const resolved = await Promise.all(
          spots.map(async (spot, idx) => {
            const point = await geocodeAddress(kakao, `${spot.name} ${profile?.region ?? ''}`.trim())
            return point ?? fallbackLatLng(profile?.region, idx)
          }),
        )
        if (cancelled) return

        const path = []
        const bounds = new kakao.maps.LatLngBounds()
        resolved.forEach((p, idx) => {
          const latlng = new kakao.maps.LatLng(p.lat, p.lng)
          path.push(latlng)
          bounds.extend(latlng)
          createSpotOverlay(kakao, map, latlng, idx, spots[idx].name)
        })
        if (path.length > 1) {
          new kakao.maps.Polyline({ map, path, strokeWeight: 8, strokeColor: '#f9fafb', strokeOpacity: 0.95, strokeStyle: 'solid' })
          new kakao.maps.Polyline({ map, path, strokeWeight: 3, strokeColor: '#1f2328', strokeOpacity: 0.85, strokeStyle: 'shortdash' })
        }
        map.setBounds(bounds)
      } catch (e) {
        setMapErr(e instanceof Error ? e.message : '지도를 불러오지 못했습니다.')
      }
    }

    void draw()
    return () => { cancelled = true }
  }, [spots, isPaid, profile?.region])

  const rating = profile?.averageRating != null && profile?.averageRating !== ''
    ? Number(profile.averageRating).toFixed(1) : '—'
  const rc = profile?.reviewCount != null ? profile.reviewCount : 0
  const likes = useMemo(() => Math.max(Math.round(rc * 2.8) + 40, 12), [rc])

  if (loading) return <div className="gmc"><PageLoading /></div>
  if (error || !profile) {
    return (
      <div className="gmc">
        <PageError message={error || '가이드 정보를 찾을 수 없습니다.'}>
          <Link to="/ai-search">검색으로 돌아가기</Link>
        </PageError>
      </div>
    )
  }

  return (
    <div className="gmc">
      <button type="button" className="gmc-back" onClick={() => navigate('/ai-search')}>
        ← Back to Search
      </button>

      {/* 가이드 프로필 헤더 (기존 유지) */}
      <Link
        to={`/guides/${guideId}`}
        className="gmc-hero gmc-hero--link"
        aria-label={`${profile.nickname ?? '가이드'} 프로필 및 피드 보기`}
      >
        <div
          className="gmc-avatar"
          style={profile.profileImage ? { backgroundImage: `url(${profile.profileImage})` } : undefined}
        />
        <div className="gmc-hero-body">
          <h1 className="gmc-name">{profile.nickname ?? '가이드'}</h1>
          <p className="gmc-meta">📍 {profile.region ?? ''} · ⭐ {rating} ({rc} 리뷰)</p>
          <p className="gmc-quote">"{profile.bio?.slice(0, 120) || '관광객은 모르는 사진 찍기 좋은 조용한 루트를 안내합니다.'}"</p>
          <p className="gmc-hero-cta">프로필·피드 보기 →</p>
        </div>
        <div className="gmc-likes">♥ {likes}</div>
      </Link>

      {/* 코스 섹션 */}
      <section className="gmc-course">
        <h2 className="gmc-title">매칭 완료! 상세 코스</h2>
        {paymentId && <p className="gmc-hint">결제 #{paymentId} 완료 기준으로 코스를 표시합니다.</p>}

        {/* ── 집합 장소 & 안내 메시지: 결제 전에도 공개 ── */}
        {(meetingPoint || guideMessage) && (
          <div className="gmc-pre-info">
            {meetingPoint && (
              <div className="gmc-pre-card">
                <span className="gmc-pre-label">집합 장소</span>
                <p className="gmc-pre-value">{meetingPoint}</p>
              </div>
            )}
            {guideMessage && (
              <div className="gmc-pre-card">
                <span className="gmc-pre-label">가이드 안내</span>
                <p className="gmc-pre-value">{guideMessage}</p>
              </div>
            )}
          </div>
        )}

        <div className="gmc-grid">
          {/* 지도 */}
          <div className="gmc-map-wrap">
            <div ref={mapRef} className="gmc-map" />
            {mapErr && <p className="gmc-err gmc-map-err">{mapErr}</p>}
            {/* 결제 전 지도 잠금 오버레이 */}
            {!isPaid && (
              <div className="gmc-map-lock">
                <span className="gmc-lock-icon">🔒</span>
                <p className="gmc-lock-title">결제 완료 후 코스 공개</p>
                <p className="gmc-lock-desc">상세 코스와 지도는 결제 후 확인할 수 있어요</p>
              </div>
            )}
          </div>

          {/* 타임라인 */}
          <aside className="gmc-timeline">
            {/* 결제 완료: 실제 courseDetail 스팟 표시 */}
            {isPaid && spots.length > 0
              ? spots.slice(0, 4).map((spot, idx) => (
                  <article key={idx} className="gmc-spot">
                    <p className="gmc-spot-label">SPOT {idx + 1}</p>
                    <h3 className="gmc-spot-name">{spot.name}{spot.time ? ` (${spot.time})` : ''}</h3>
                    {spot.desc && <p className="gmc-spot-desc">{spot.desc}</p>}
                  </article>
                ))
              : !isPaid && (
                  // 결제 전: 잠금 안내
                  <article className="gmc-spot gmc-spot--locked">
                    <p className="gmc-spot-label">SPOT 1 ~ N</p>
                    <h3 className="gmc-spot-name">결제 후 공개됩니다</h3>
                    <p className="gmc-spot-desc">
                      가이드가 작성한 코스 스팟은 결제 완료 후 이 화면에 표시됩니다.
                    </p>
                  </article>
                )
            }
            {/* 가이드 미작성 케이스 (isPaid=true인데 spots=0) */}
            {isPaid && spots.length === 0 && (
              <article className="gmc-spot">
                <p className="gmc-spot-label">코스 준비 중</p>
                <h3 className="gmc-spot-name">가이드가 코스를 작성 중이에요</h3>
                <p className="gmc-spot-desc">곧 업데이트될 예정입니다.</p>
              </article>
            )}
            <Link to="/messages" className="gmc-chat-btn">
              가이드와 채팅방 입장하기
            </Link>
          </aside>
        </div>
      </section>
    </div>
  )
}
