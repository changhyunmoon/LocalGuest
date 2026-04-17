import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'

import { PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client'

import './GuideMatchedCoursePage.css'

const KAKAO_APP_KEY = import.meta.env.VITE_KAKAO_MAP_APP_KEY

const DEFAULT_TIMES = ['오전 10시', '오후 1시', '오후 3시', '오후 5시']

const REGION_BASE = {
  제주: { lat: 33.4996, lng: 126.5312 },
  부산: { lat: 35.1796, lng: 129.0756 },
  강릉: { lat: 37.7519, lng: 128.8761 },
  여수: { lat: 34.7604, lng: 127.6622 },
}

function parseCourseCandidates(raw) {
  if (!raw || !String(raw).trim()) return []
  const text = String(raw).trim()
  if (text.includes('->')) {
    return text
      .split('->')
      .map((s) => s.trim())
      .filter(Boolean)
  }
  return text
    .split(/\n+/)
    .map((s) => s.trim())
    .filter(Boolean)
}

function toSpots(requestData, region) {
  const fromSchedule = parseCourseCandidates(requestData?.proposedSchedule)
  const fromMessage = parseCourseCandidates(requestData?.proposeMessage)
  const picked = (fromSchedule.length > 0 ? fromSchedule : fromMessage).slice(0, 4)

  const names =
    picked.length > 0
      ? picked
      : ['비밀의 숲', '로컬 해물라면', '노을 해변 산책']

  return names.map((name, idx) => ({
    id: idx + 1,
    name: String(name).replace(/^[-0-9.\s]+/, ''),
    timeLabel: DEFAULT_TIMES[idx] ?? `오후 ${2 + idx}시`,
    desc:
      idx === 0
        ? '사람이 많지 않아 사진 찍기 좋아요.'
        : idx === 1
          ? '관광객은 모르는 진짜 로컬 단골 스팟입니다.'
          : '동선과 분위기를 고려해 이어지는 추천 코스예요.',
    query: `${name} ${region ?? ''}`.trim(),
  }))
}

function fallbackLatLng(region, idx) {
  const hit = Object.entries(REGION_BASE).find(([key]) => String(region ?? '').includes(key))
  const base = hit?.[1] ?? { lat: 33.4996, lng: 126.5312 }
  return {
    lat: base.lat + idx * 0.007,
    lng: base.lng + (idx % 2 === 0 ? 0.009 : -0.006),
  }
}

function loadKakaoSdk(appKey) {
  if (!appKey) return Promise.reject(new Error('카카오맵 앱 키가 없습니다.'))
  if (window.kakao?.maps?.services) return Promise.resolve(window.kakao)

  return new Promise((resolve, reject) => {
    const existing = document.getElementById('kakao-map-sdk')
    if (existing) {
      existing.addEventListener('load', () => {
        window.kakao.maps.load(() => resolve(window.kakao))
      })
      existing.addEventListener('error', () => reject(new Error('카카오맵 SDK 로드 실패')))
      return
    }

    const script = document.createElement('script')
    script.id = 'kakao-map-sdk'
    script.async = true
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false&libraries=services`
    script.onload = () => {
      window.kakao.maps.load(() => resolve(window.kakao))
    }
    script.onerror = () => reject(new Error('카카오맵 SDK 로드 실패'))
    document.head.appendChild(script)
  })
}

function geocodeAddress(kakao, query) {
  return new Promise((resolve) => {
    const geocoder = new kakao.maps.services.Geocoder()
    geocoder.addressSearch(query, (result, status) => {
      if (status !== kakao.maps.services.Status.OK || !Array.isArray(result) || result.length === 0) {
        resolve(null)
        return
      }
      const first = result[0]
      resolve({
        lat: Number(first.y),
        lng: Number(first.x),
      })
    })
  })
}

function createSpotOverlay(kakao, map, latlng, idx, name) {
  const root = document.createElement('div')
  root.className = 'gmc-pin'

  const badge = document.createElement('span')
  badge.className = 'gmc-pin-badge'
  badge.textContent = String(idx + 1)

  const label = document.createElement('span')
  label.className = 'gmc-pin-label'
  label.textContent = name

  root.appendChild(badge)
  root.appendChild(label)

  return new kakao.maps.CustomOverlay({
    map,
    position: latlng,
    yAnchor: 1.25,
    content: root,
  })
}

function findTargetRequest(list, guideId, requestId) {
  if (!Array.isArray(list)) return null
  if (requestId != null && !Number.isNaN(Number(requestId))) {
    return list.find((r) => Number(r.requestId) === Number(requestId)) ?? null
  }
  const gid = Number(guideId)
  const rows = list.filter((r) => Number(r.guideId) === gid)
  rows.sort((a, b) => {
    const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0
    const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0
    return tb - ta
  })
  return rows[0] ?? null
}

export function GuideMatchedCoursePage() {
  const { guideId } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const mapRef = useRef(null)

  const requestId = searchParams.get('requestId')
  const paymentId = searchParams.get('paymentId')

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [chatBusy, setChatBusy] = useState(false)
  const [chatErr, setChatErr] = useState('')
  const [profile, setProfile] = useState(null)
  const [requestData, setRequestData] = useState(null)
  const [mapErr, setMapErr] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [detailRes, reqRes] = await Promise.all([
        apiRequest(`/guides/${guideId}/detail`, { method: 'GET', skipAuth: true }),
        apiRequest('/matching/requests/guest/list', { method: 'GET' }),
      ])

      const detailText = await detailRes.text()
      if (!detailRes.ok) throw new Error(detailText || '가이드 정보를 불러오지 못했습니다.')
      const detail = detailText ? JSON.parse(detailText) : null
      setProfile(detail?.profile ?? null)

      const reqText = await reqRes.text()
      if (reqRes.ok) {
        const list = reqText ? JSON.parse(reqText) : []
        setRequestData(findTargetRequest(list, guideId, requestId))
      } else {
        setRequestData(null)
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '오류')
    } finally {
      setLoading(false)
    }
  }, [guideId, requestId])

  useEffect(() => {
    void load()
  }, [load])

  const spots = useMemo(() => toSpots(requestData, profile?.region), [requestData, profile])

  useEffect(() => {
    if (!mapRef.current || spots.length === 0) return
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
            const point = await geocodeAddress(kakao, spot.query)
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
          // 배경 흰 실선 + 상단 점선 오버레이로 시안 느낌을 맞춘다.
          new kakao.maps.Polyline({
            map,
            path,
            strokeWeight: 8,
            strokeColor: '#f9fafb',
            strokeOpacity: 0.95,
            strokeStyle: 'solid',
          })
          new kakao.maps.Polyline({
            map,
            path,
            strokeWeight: 3,
            strokeColor: '#1f2328',
            strokeOpacity: 0.85,
            strokeStyle: 'shortdash',
          })
        }
        map.setBounds(bounds)
      } catch (e) {
        setMapErr(e instanceof Error ? e.message : '지도를 불러오지 못했습니다.')
      }
    }

    void draw()
    return () => {
      cancelled = true
    }
  }, [spots, profile?.region])

  const rating =
    profile?.averageRating != null && profile?.averageRating !== ''
      ? Number(profile.averageRating).toFixed(1)
      : '—'
  const rc = profile?.reviewCount != null ? profile.reviewCount : 0
  const likes = useMemo(() => Math.max(Math.round(rc * 2.8) + 40, 12), [rc])

  const matchRequestIdForChat =
    requestId != null && String(requestId).trim() !== ''
      ? String(requestId)
      : requestData?.requestId != null
        ? String(requestData.requestId)
        : ''

  const handleOpenMatchChat = async () => {
    const rid = matchRequestIdForChat.trim()
    if (!rid) {
      setChatErr('매칭 요청 정보를 찾을 수 없습니다. URL에 requestId가 있는지 확인해 주세요.')
      return
    }
    setChatBusy(true)
    setChatErr('')
    try {
      const res = await apiRequest(`/chat/rooms/for-match-request/${encodeURIComponent(rid)}`, { method: 'POST' })
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

  if (loading) {
    return (
      <div className="gmc">
        <PageLoading />
      </div>
    )
  }
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
          <p className="gmc-meta">
            📍 {profile.region ?? ''} · ⭐ {rating} ({rc} 리뷰)
          </p>
          <p className="gmc-quote">“{profile.bio?.slice(0, 120) || '관광객은 모르는 사진 찍기 좋은 조용한 루트를 안내합니다.'}”</p>
          <p className="gmc-hero-cta">프로필·피드 보기 →</p>
        </div>
        <div className="gmc-likes">♥ {likes}</div>
      </Link>

      <section className="gmc-course">
        <h2 className="gmc-title">매칭 완료! 상세 코스</h2>
        {paymentId && <p className="gmc-hint">결제 #{paymentId} 완료 기준으로 코스를 표시합니다.</p>}
        <div className="gmc-grid">
          <div className="gmc-map-wrap">
            <div ref={mapRef} className="gmc-map" />
            {mapErr && <p className="gmc-err gmc-map-err">{mapErr}</p>}
          </div>
          <aside className="gmc-timeline">
            {spots.slice(0, 4).map((spot, idx) => (
              <article key={spot.id} className="gmc-spot">
                <p className="gmc-spot-label">SPOT {idx + 1}</p>
                <h3 className="gmc-spot-name">
                  {spot.name} ({spot.timeLabel})
                </h3>
                <p className="gmc-spot-desc">{spot.desc}</p>
              </article>
            ))}
            <button
              type="button"
              className="gmc-chat-btn"
              disabled={chatBusy || !matchRequestIdForChat.trim()}
              onClick={() => void handleOpenMatchChat()}
            >
              {chatBusy ? '채팅방 연결 중…' : '가이드와 채팅방 입장하기'}
            </button>
            {chatErr ? <p className="gmc-err gmc-chat-err">{chatErr}</p> : null}
          </aside>
        </div>
      </section>
    </div>
  )
}
