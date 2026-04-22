import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client.js'
import { DEFAULT_KOREA_CENTER, getUserLatLng, resolveLatLng } from '../lib/kakaoGeocode.js'
import { loadKakaoSdk } from '../lib/kakaoMapSdk.js'
import { fetchGuestMatchRequests } from '../lib/matchingGuest.js'
import { parseCourseDetail } from './GuideCoursePanel.jsx'

import './GuideMatchedCoursePage.css'
import './MypageMemberPages.css'

const KAKAO_APP_KEY = import.meta.env.VITE_KAKAO_MAP_APP_KEY

function parseApiErrorMessage(text) {
  if (!text) return '요청 실패'
  try {
    const j = JSON.parse(text)
    return (j.message ?? text) || '요청 실패'
  } catch {
    return text || '요청 실패'
  }
}

function fallbackLatLng(idx) {
  const base = DEFAULT_KOREA_CENTER
  return { lat: base.lat + idx * 0.007, lng: base.lng + (idx % 2 === 0 ? 0.009 : -0.006) }
}

function createSpotOverlay(kakao, map, latlng, idx, name) {
  const root = document.createElement('div')
  root.className = 'gmc-pin'
  root.innerHTML = `<span class="gmc-pin-badge">${idx + 1}</span><span class="gmc-pin-label">${name}</span>`
  return new kakao.maps.CustomOverlay({ map, position: latlng, yAnchor: 1.25, content: root })
}

function renderStaticFallbackMap(kakao, container, centerPoint, points) {
  if (!container) return
  container.innerHTML = ''
  const center = new kakao.maps.LatLng(centerPoint.lat, centerPoint.lng)
  const marker = points.map((p, idx) => ({
    position: new kakao.maps.LatLng(p.lat, p.lng),
    text: `SPOT ${idx + 1}`,
  }))
  new kakao.maps.StaticMap(container, {
    center,
    level: 5,
    marker: marker.length > 0 ? marker : undefined,
  })
}

async function fetchMyReviewMap() {
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

export function MypageScrapbookTicketDetailPage() {
  const { requestId } = useParams()
  const navigate = useNavigate()
  const mapRef = useRef(null)
  const reviewFileInputRef = useRef(null)

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [row, setRow] = useState(null)
  const [profile, setProfile] = useState(null)
  const [formData, setFormData] = useState(null)
  const [reviewByMatch, setReviewByMatch] = useState({})
  const [mapErr, setMapErr] = useState('')
  const [reviewModal, setReviewModal] = useState(false)
  const [tripOrder, setTripOrder] = useState(1)
  const [rating, setRating] = useState(5)
  const [content, setContent] = useState('')
  const [reviewBusy, setReviewBusy] = useState(false)
  const [reviewError, setReviewError] = useState('')
  const [reviewPhotos, setReviewPhotos] = useState([])

  const load = useCallback(async () => {
    const rid = Number(requestId)
    if (!Number.isFinite(rid)) throw new Error('잘못된 여행 기록 ID입니다.')
    const [all, reviews] = await Promise.all([
      fetchGuestMatchRequests(apiRequest),
      fetchMyReviewMap().catch(() => ({})),
    ])
    const completed = (Array.isArray(all) ? all : [])
      .filter((r) => r.status === 'COMPLETED')
      .sort((a, b) => String(b.desiredDate ?? '').localeCompare(String(a.desiredDate ?? '')))
    const found = completed.find((r) => Number(r.requestId) === rid)
    if (!found) throw new Error('해당 여행 기록을 찾을 수 없습니다.')
    setRow(found)
    setReviewByMatch(reviews)
    const idx = completed.findIndex((r) => Number(r.requestId) === rid)
    setTripOrder(idx >= 0 ? idx + 1 : 1)

    if (found.guideId != null) {
      const detailRes = await apiRequest(`/guides/${found.guideId}/detail`, { method: 'GET', skipAuth: true })
      const detailText = await detailRes.text()
      if (detailRes.ok && detailText) {
        const detail = JSON.parse(detailText)
        setProfile(detail?.profile ?? null)
      }
    }

    if (found.guideId != null && found.scheduleId != null) {
      const formRes = await apiRequest(`/guides/${found.guideId}/schedules/${found.scheduleId}/form`, { method: 'GET' })
      const formText = await formRes.text()
      if (formRes.ok) setFormData(formText ? JSON.parse(formText) : null)
    }
  }, [requestId])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        await load()
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '불러오기 실패')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [load])

  const courseText = useMemo(() => {
    return String(formData?.courseDetail ?? row?.proposedSchedule ?? '').trim()
  }, [formData?.courseDetail, row?.proposedSchedule])
  const spots = useMemo(() => {
    const parsed = parseCourseDetail(courseText)
      .map((spot) => ({
        name: String(spot?.name ?? '').trim(),
        time: String(spot?.time ?? '').trim(),
        desc: String(spot?.desc ?? '').trim(),
      }))
      .filter((spot) => Boolean(spot.name))
    if (parsed.length === 1 && !parsed[0].time && !parsed[0].desc) {
      const expanded = parsed[0].name
        .split(/->|→|,/g)
        .map((s) => s.trim())
        .filter(Boolean)
      if (expanded.length > 1) {
        return expanded.map((name) => ({ name, time: '', desc: '' }))
      }
    }
    return parsed
  }, [courseText])
  const hasReview = reviewByMatch[Number(row?.requestId)] != null

  useEffect(() => {
    if (loading || !row) return
    if (!mapRef.current) return
    let cancelled = false

    const drawMap = async () => {
      try {
        setMapErr('')
        const el = mapRef.current
        if (!el) return
        el.innerHTML = ''
        const kakao = await loadKakaoSdk(KAKAO_APP_KEY)
        if (cancelled || !mapRef.current) return

        let center = DEFAULT_KOREA_CENTER
        const userPos = await getUserLatLng()
        if (userPos) {
          center = userPos
        } else if (formData?.meetingPoint?.trim()) {
          const byMeeting = await resolveLatLng(kakao, formData.meetingPoint.trim(), { regionHint: String(profile?.region ?? '').trim() })
          if (byMeeting) center = byMeeting
        } else if (spots[0]?.name) {
          const firstSpot = await resolveLatLng(kakao, spots[0].name, { regionHint: String(profile?.region ?? '').trim() })
          if (firstSpot) center = firstSpot
        } else if (row?.destination) {
          const byDestination = await resolveLatLng(kakao, row.destination, { regionHint: String(profile?.region ?? '').trim() })
          if (byDestination) center = byDestination
        }

        const map = new kakao.maps.Map(el, {
          center: new kakao.maps.LatLng(center.lat, center.lng),
          level: 8,
        })

        const regionHint = String(profile?.region ?? '').trim()
        const spotNames = spots.map((spot) => spot.name).filter(Boolean)
        if (spotNames.length === 0 && formData?.meetingPoint?.trim()) {
          spotNames.push(formData.meetingPoint.trim())
        } else if (spotNames.length === 0 && row?.destination) {
          spotNames.push(String(row.destination))
        }

        const resolved = await Promise.all(
          spotNames.map(async (name, idx) => {
            const point = await resolveLatLng(kakao, name, { regionHint })
            return point ?? fallbackLatLng(idx)
          }),
        )
        if (cancelled || resolved.length === 0) return

        const path = []
        const bounds = new kakao.maps.LatLngBounds()
        resolved.forEach((p, idx) => {
          const latlng = new kakao.maps.LatLng(p.lat, p.lng)
          path.push(latlng)
          bounds.extend(latlng)
          createSpotOverlay(kakao, map, latlng, idx, spotNames[idx] || `SPOT ${idx + 1}`)
        })
        if (path.length > 1) {
          new kakao.maps.Polyline({ map, path, strokeWeight: 8, strokeColor: '#f9fafb', strokeOpacity: 0.95, strokeStyle: 'solid' })
          new kakao.maps.Polyline({ map, path, strokeWeight: 3, strokeColor: '#1f2328', strokeOpacity: 0.85, strokeStyle: 'shortdash' })
        }
        map.setBounds(bounds)

        // Some environments keep dynamic Kakao tiles gray on this page.
        // If tiles don't load quickly, fallback to a static map image.
        let tilesLoaded = false
        const onTilesLoaded = () => { tilesLoaded = true }
        kakao.maps.event.addListener(map, 'tilesloaded', onTilesLoaded)
        window.setTimeout(() => {
          if (cancelled || tilesLoaded || !mapRef.current) return
          const fallbackPoints = resolved.length > 0 ? resolved : [{ lat: center.lat, lng: center.lng }]
          renderStaticFallbackMap(kakao, mapRef.current, center, fallbackPoints)
          setMapErr('')
        }, 1500)
      } catch (e) {
        setMapErr(e instanceof Error ? e.message : '지도를 불러오지 못했습니다.')
      }
    }

    void drawMap()
    return () => {
      cancelled = true
      if (mapRef.current) {
        mapRef.current.innerHTML = ''
      }
    }
  }, [loading, row, spots, profile?.region, formData?.meetingPoint, row?.destination])

  const companionName = useMemo(() => String(profile?.nickname ?? '가이드').trim() || '가이드', [profile?.nickname])
  const tripTitle = useMemo(() => `여행기 #${tripOrder}`, [tripOrder])

  const ratingLabel = useMemo(() => {
    const labels = {
      5: '5점 - 최고의 여행이었어요!',
      4: '4점 - 아주 좋았어요!',
      3: '3점 - 만족스러웠어요!',
      2: '2점 - 아쉬운 점이 있어요',
      1: '1점 - 기대에 못 미쳤어요',
    }
    return labels[rating] ?? ''
  }, [rating])

  const submitReview = async () => {
    if (!row) return
    const trimmed = content.trim()
    if (trimmed.length < 10) {
      setReviewError('후기는 10자 이상 입력해 주세요.')
      return
    }
    setReviewBusy(true)
    setReviewError('')
    try {
      const res = await apiRequest('/reviews', {
        method: 'POST',
        json: {
          guideId: Number(row.guideId),
          matchRequestId: Number(row.requestId),
          rating,
          content: trimmed,
        },
      })
      const text = await res.text()
      if (!res.ok) {
        setReviewError(parseApiErrorMessage(text))
        return
      }
      setReviewModal(false)
      setContent('')
      setReviewPhotos([])
      const reviews = await fetchMyReviewMap().catch(() => ({}))
      setReviewByMatch(reviews)
    } catch {
      setReviewError('리뷰 등록 중 네트워크 오류가 발생했습니다.')
    } finally {
      setReviewBusy(false)
    }
  }

  if (loading) return <PageLoading />
  if (error || !row) return <PageError message={error || '여행 기록을 찾을 수 없습니다.'} onRetry={() => void load()} />

  return (
    <div className="gmc">
      <button type="button" className="mp-back-chip" onClick={() => navigate('/mypage/scrapbook')} aria-label="스크랩북으로 돌아가기">
        <span className="mp-back-chip__icon" aria-hidden>
          ←
        </span>
        스크랩북으로 돌아가기
      </button>

      <section className="gmc-course">
        <h2 className="gmc-title">{tripTitle}</h2>
        <p className="gmc-hint">
          {row.guideId != null ? (
            <Link to={`/guides/${row.guideId}`} className="mp-guide-link">
              {companionName}
            </Link>
          ) : (
            companionName
          )}
          님과 함께했어요! · {row.desiredDate}
        </p>

        {(formData?.meetingPoint || formData?.guideMessage) && (
          <div className="gmc-pre-info">
            {formData?.meetingPoint && (
              <div className="gmc-pre-card">
                <span className="gmc-pre-label">집합 장소</span>
                <p className="gmc-pre-value">{formData.meetingPoint}</p>
              </div>
            )}
            {formData?.guideMessage && (
              <div className="gmc-pre-card">
                <span className="gmc-pre-label">가이드 안내</span>
                <p className="gmc-pre-value">{formData.guideMessage}</p>
              </div>
            )}
          </div>
        )}

        <div className="gmc-grid">
          <div className="gmc-map-wrap">
            <div ref={mapRef} className="gmc-map" />
            {mapErr && <p className="gmc-err gmc-map-err">{mapErr}</p>}
          </div>

          <aside className="gmc-timeline">
            {spots.length > 0 ? (
              spots.slice(0, 6).map((spot, idx) => (
                <article key={idx} className="gmc-spot">
                  <p className="gmc-spot-label">SPOT {idx + 1}</p>
                  <h3 className="gmc-spot-name">{spot.name}{spot.time ? ` (${spot.time})` : ''}</h3>
                  {spot.desc && <p className="gmc-spot-desc">{spot.desc}</p>}
                </article>
              ))
            ) : (
              <article className="gmc-spot">
                <p className="gmc-spot-label">SPOT 1 ~ N</p>
                <h3 className="gmc-spot-name">상세 코스가 준비 중입니다.</h3>
              </article>
            )}
          </aside>
        </div>
      </section>

      <div className="mp-trip-actions mp-trip-actions--under-route" style={{ marginTop: '1rem' }}>
        {hasReview ? (
          <>
            <p className="mp-review-done">후기 작성 완료</p>
            <Link to="/mypage/reviews" className="mp-btn mp-btn--line">내 후기 보기</Link>
          </>
        ) : (
          <button
            type="button"
            className="mp-btn mp-review-open-btn"
            onClick={() => {
              setReviewError('')
              setRating(5)
              setContent('')
              setReviewPhotos([])
              setReviewModal(true)
            }}
          >
            후기 작성하기
          </button>
        )}
      </div>

      {reviewModal && (
        <div className="mp-modal-overlay" role="dialog" aria-modal="true" aria-label="후기 작성" onClick={() => !reviewBusy && setReviewModal(false)}>
          <div className="mp-modal mp-review-modal" onClick={(e) => e.stopPropagation()}>
            <button type="button" className="mp-review-close" aria-label="후기 모달 닫기" onClick={() => setReviewModal(false)} disabled={reviewBusy}>
              ×
            </button>
            <div className="mp-review-tape" aria-hidden />
            <h2 className="mp-review-title">투어는 어떠셨나요? ✨</h2>
            <p className="mp-review-sub">{row.destination} · {companionName}님과의 추억을 남겨주세요.</p>

            <div className="mp-review-stars" role="radiogroup" aria-label="별점 선택">
              {[1, 2, 3, 4, 5].map((n) => (
                <button
                  key={n}
                  type="button"
                  className={`mp-review-star${n <= rating ? ' is-on' : ''}`}
                  aria-label={`${n}점`}
                  aria-checked={n === rating}
                  role="radio"
                  onClick={() => setRating(n)}
                  disabled={reviewBusy}
                >
                  🌟
                </button>
              ))}
            </div>
            <p className="mp-review-rating-label">{ratingLabel}</p>

            <textarea
              className="mp-modal-text mp-review-textarea"
              rows={6}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="좋았던 점, 아쉬운 점, 다시 가고 싶은 이유를 자유롭게 남겨주세요."
              disabled={reviewBusy}
            />

            <div className="mp-review-upload-row">
              <input
                ref={reviewFileInputRef}
                type="file"
                accept="image/*"
                multiple
                style={{ display: 'none' }}
                onChange={(e) => setReviewPhotos(Array.from(e.target.files ?? []))}
              />
              <button type="button" className="mp-btn mp-btn--line" disabled={reviewBusy} onClick={() => reviewFileInputRef.current?.click()}>
                📷 사진 첨부
              </button>
              <span className="mp-review-upload-note">
                {reviewPhotos.length > 0 ? `${reviewPhotos.length}장의 사진이 첨부되었습니다.` : '사진을 첨부할 수 있습니다.'}
              </span>
            </div>

            {reviewError && <p className="err" style={{ marginTop: '0.55rem', marginBottom: 0 }}>{reviewError}</p>}
            <div className="mp-modal-actions">
              <button type="button" className="mp-btn mp-review-submit" onClick={() => void submitReview()} disabled={reviewBusy}>
                {reviewBusy ? '리뷰 등록 중…' : '리뷰 등록하기'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
