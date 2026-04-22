import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'

import { PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client'
import { DEFAULT_KOREA_CENTER, getUserLatLng, resolveLatLng } from '../lib/kakaoGeocode.js'
import { loadKakaoSdk } from '../lib/kakaoMapSdk.js'
import { parseCourseDetail } from './GuideCoursePanel.jsx'

import './GuideMatchedCoursePage.css'

// ── 상수 ──────────────────────────────────────────────────────────────────
const KAKAO_APP_KEY = import.meta.env.VITE_KAKAO_MAP_APP_KEY

function fallbackLatLng(idx) {
  const base = DEFAULT_KOREA_CENTER
  return { lat: base.lat + idx * 0.007, lng: base.lng + (idx % 2 === 0 ? 0.009 : -0.006) }
}

function createSpotOverlay(kakao, map, latlng, idx, name) {
  const root = document.createElement('div')
  root.className = 'gmc-pin'
  root.innerHTML = `<span class="gmc-pin-badge"><span class="gmc-pin-badge-inner">${idx + 1}</span></span><span class="gmc-pin-label">${name}</span>`
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

function parsePreviewSpots(proposedSchedule) {
  const raw = String(proposedSchedule ?? '').trim()
  if (!raw) return []
  const normalized = raw.replace(/\s*->\s*/g, '\n')
  return normalized
    .split(/\r?\n|,/)
    .map((v) => v.trim())
    .filter(Boolean)
}

// ── 메인 컴포넌트 ─────────────────────────────────────────────────────────
export function GuideMatchedCoursePage() {
  const { guideId } = useParams()
  const [searchParams] = useSearchParams()
  const location = useLocation()
  const navigate = useNavigate()
  const mapRef = useRef(null)

  const requestId = searchParams.get('requestId')
  const paymentId = searchParams.get('paymentId')

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [profile, setProfile] = useState(null)
  const [scheduleId, setScheduleId] = useState(null)
  const [matchRequest, setMatchRequest] = useState(null)
  // schedule form 데이터 (백엔드 GuideScheduleFormResponse)
  const [formData, setFormData] = useState(null)
  const [mapErr, setMapErr] = useState('')
  const [chatBusy, setChatBusy] = useState(false)
  const [chatErr, setChatErr] = useState('')

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
        setMatchRequest(req)
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
  const previewSpots = useMemo(() => parsePreviewSpots(matchRequest?.proposedSchedule), [matchRequest?.proposedSchedule])
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

        let center = DEFAULT_KOREA_CENTER
        const userPos = await getUserLatLng()
        if (userPos) {
          center = userPos
        } else if (meetingPoint.trim()) {
          const byMeeting = await resolveLatLng(kakao, meetingPoint.trim(), { regionHint: String(profile?.region ?? '').trim() })
          if (byMeeting) center = byMeeting
        } else if (spots[0]?.name) {
          const firstSpot = await resolveLatLng(kakao, spots[0].name, { regionHint: String(profile?.region ?? '').trim() })
          if (firstSpot) center = firstSpot
        }
        const map = new kakao.maps.Map(mapRef.current, {
          center: new kakao.maps.LatLng(center.lat, center.lng),
          level: 8,
        })

        const regionHint = String(profile?.region ?? '').trim()
        const resolved = await Promise.all(
          spots.map(async (spot, idx) => {
            const point = await resolveLatLng(kakao, spot.name, { regionHint })
            return point ?? fallbackLatLng(idx)
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
  }, [spots, isPaid, profile?.region, meetingPoint])

  const handleOpenMatchChat = async () => {
    setChatBusy(true)
    setChatErr('')
    try {
      const res = await apiRequest(
        `/matching/chat/rooms/for-guide/${encodeURIComponent(guideId)}`,
        { method: 'POST' },
      )
      const text = await res.text()
      if (!res.ok) throw new Error(text || '채팅방을 열 수 없습니다.')
      const data = text ? JSON.parse(text) : {}
      const roomId = data?.roomId
      if (!roomId) throw new Error('채팅방 정보가 올바르지 않습니다.')
      navigate(`/messages?roomId=${encodeURIComponent(roomId)}`)
    } catch (e) {
      setChatErr(e instanceof Error ? e.message : '오류')
    } finally {
      setChatBusy(false)
    }
  }

  const rating = profile?.averageRating != null && profile?.averageRating !== ''
    ? Number(profile.averageRating).toFixed(1) : '—'
  const rc = profile?.reviewCount != null ? profile.reviewCount : 0

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
        state={{
          fromMatchedCourse: true,
          hideMatchRequest: true,
          returnTo: `${location.pathname}${location.search}`,
        }}
        className="gmc-hero gmc-hero--link"
        aria-label={`${profile.nickname ?? '가이드'} 프로필 및 피드 보기`}
      >
        <div
          className="gmc-avatar"
          style={profile.profileImage ? { backgroundImage: `url(${profile.profileImage})` } : undefined}
        />
        <div className="gmc-hero-body">
          <h1 className="gmc-name">{profile.nickname ?? '가이드'}</h1>
          <p className="gmc-meta">{profile.region ?? ''} · 평점 {rating} ({rc} 리뷰)</p>
          <p className="gmc-quote">"{profile.bio?.slice(0, 120) || '관광객은 모르는 사진 찍기 좋은 조용한 루트를 안내합니다.'}"</p>
          <p className="gmc-hero-cta">프로필·피드 보기 →</p>
        </div>
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
                <span className="gmc-lock-icon"><span className="gmc-lock-glyph" /></span>
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
                    {previewSpots.length > 0 && (
                      <p className="gmc-spot-desc">
                        미리보기: {previewSpots.slice(0, 3).join(' → ')}
                        {previewSpots.length > 3 ? ' …' : ''}
                      </p>
                    )}
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
            <button
              type="button"
              className="gmc-chat-btn"
              disabled={chatBusy}
              onClick={() => void handleOpenMatchChat()}
            >
              {chatBusy ? '채팅방 연결 중…' : '가이드와 채팅방 입장하기'}
            </button>
            {chatErr && <p className="gmc-err gmc-chat-err">{chatErr}</p>}
          </aside>
        </div>
      </section>
    </div>
  )
}
