import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client.js'
import { DEFAULT_KOREA_CENTER, resolveLatLng } from '../lib/kakaoGeocode.js'
import { loadKakaoSdk } from '../lib/kakaoMapSdk.js'
import { fetchMyReviewsByMatchRequestId } from '../lib/guestMyReviews.js'
import { daysUntil, fetchGuestMatchRequests } from '../lib/matchingGuest.js'

import './MypageMemberPages.css'

const KAKAO_APP_KEY = import.meta.env.VITE_KAKAO_MAP_APP_KEY

async function loadGuideNicknames(apiRequest, guideIds) {
  const map = {}
  const ids = [...new Set(guideIds.filter(Boolean))]
  await Promise.all(
    ids.map(async (id) => {
      try {
        const res = await apiRequest(`/guides/${id}`, { method: 'GET', skipAuth: true })
        const text = await res.text()
        if (res.ok && text) {
          const p = JSON.parse(text)
          map[id] = p.nickname ?? `가이드 #${id}`
        }
      } catch {
        map[id] = `가이드 #${id}`
      }
    }),
  )
  return map
}

function galleryCaption(row, review) {
  const c = review?.content != null ? String(review.content).trim() : ''
  if (c) {
    const line = c.split(/\r?\n/).map((s) => s.trim()).find(Boolean) ?? c
    return line.length > 72 ? `${line.slice(0, 69)}…` : line
  }
  return String(row.destination ?? '여행').trim() || '여행'
}

function parseRouteStops(proposedSchedule) {
  const raw = String(proposedSchedule ?? '').trim()
  if (!raw) return []
  return raw
    .split(/->|→|\r?\n|,/g)
    .map((s) => s.trim())
    .filter(Boolean)
}

export function MypageScrapbookPage() {
  const navigate = useNavigate()
  const [rows, setRows] = useState([])
  const [names, setNames] = useState({})
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [tearingRequestId, setTearingRequestId] = useState(null)
  const [reviewByMatch, setReviewByMatch] = useState({})
  const overviewMapRef = useRef(null)
  const [overviewMapErr, setOverviewMapErr] = useState('')

  const loadScrapbook = useCallback(async () => {
    const [all, reviews] = await Promise.all([
      fetchGuestMatchRequests(apiRequest),
      fetchMyReviewsByMatchRequestId(apiRequest).catch(() => ({})),
    ])
    const completed = (Array.isArray(all) ? all : [])
      .filter((r) => r.status === 'COMPLETED')
      .sort((a, b) => String(b.desiredDate).localeCompare(String(a.desiredDate)))
    setRows(completed)
    setReviewByMatch(reviews && typeof reviews === 'object' ? reviews : {})
    const gids = completed.map((r) => r.guideId)
    const nm = await loadGuideNicknames(apiRequest, gids)
    setNames(nm)
  }, [])

  const refetch = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      await loadScrapbook()
    } catch (e) {
      setError(e instanceof Error ? e.message : '불러오기 실패')
    } finally {
      setLoading(false)
    }
  }, [loadScrapbook])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        await loadScrapbook()
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '불러오기 실패')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [loadScrapbook])

  useEffect(() => {
    if (loading || error || rows.length === 0) return
    if (!overviewMapRef.current) return
    let cancelled = false

    const drawOverview = async () => {
      try {
        setOverviewMapErr('')
        const el = overviewMapRef.current
        if (!el) return
        el.innerHTML = ''
        const kakao = await loadKakaoSdk(KAKAO_APP_KEY)
        if (cancelled || !overviewMapRef.current) return

        const dests = [...new Set(rows.map((r) => String(r.destination ?? '').trim()).filter(Boolean))].slice(0, 15)
        const hint = dests[0] ?? ''
        const points =
          dests.length > 0
            ? await Promise.all(
                dests.map(async (d, idx) => {
                  const p = await resolveLatLng(kakao, d, { regionHint: hint || d })
                  return (
                    p ?? {
                      lat: DEFAULT_KOREA_CENTER.lat + idx * 0.04,
                      lng: DEFAULT_KOREA_CENTER.lng + (idx % 2 === 0 ? 0.03 : -0.03),
                    }
                  )
                }),
              )
            : [{ ...DEFAULT_KOREA_CENTER }]

        const center = points[0]
        const map = new kakao.maps.Map(el, {
          center: new kakao.maps.LatLng(center.lat, center.lng),
          level: 10,
        })
        const bounds = new kakao.maps.LatLngBounds()
        points.forEach((p, idx) => {
          const latlng = new kakao.maps.LatLng(p.lat, p.lng)
          bounds.extend(latlng)
          const marker = new kakao.maps.Marker({
            map,
            position: latlng,
            title: dests[idx] ? `${dests[idx]} · 여행 ${idx + 1}` : `여행 ${idx + 1}`,
          })
          void marker
        })
        if (points.length > 1) {
          map.setBounds(bounds)
        }
      } catch (e) {
        if (!cancelled) setOverviewMapErr(e instanceof Error ? e.message : '지도를 불러오지 못했습니다.')
      }
    }

    void drawOverview()
    return () => {
      cancelled = true
      if (overviewMapRef.current) {
        overviewMapRef.current.innerHTML = ''
      }
    }
  }, [loading, error, rows])

  const count = rows.length

  const subtitle = useMemo(() => {
    if (count === 0) return '아직 완료된 로컬 투어 기록이 없어요!'
    return `총 ${count}번의 로컬 여행을 다녀왔어요 ✈️`
  }, [count])

  return (
    <div className="mp-member">
      <h1>📒 스크랩북</h1>
      <p className="sub">투어가 완료된 매칭을 시간 순으로 모아 보여 줍니다.</p>
      {loading && <PageLoading />}
      {!loading && error && <PageError message={error} onRetry={() => void refetch()} />}

      {!loading && !error && (
        <>
          <div className="mp-scrap-hero">
            <div>
              <h2>나의 스크랩북</h2>
              <p>{subtitle}</p>
            </div>
          </div>

          {rows.length > 0 && (
            <section className="mp-scrap-map-block" aria-labelledby="mp-scrap-map-title">
              <h2 id="mp-scrap-map-title" className="mp-scrap-map-title">
                여행 지도
              </h2>
              <p className="mp-scrap-map-desc">완료된 여행의 목적지를 한 지도에 모았어요.</p>
              <div className="mp-scrap-map-wrap">
                <div ref={overviewMapRef} className="mp-scrap-overview-map" />
                {overviewMapErr && <p className="mp-scrap-map-err">{overviewMapErr}</p>}
              </div>
            </section>
          )}

          {rows.length === 0 && (
            <PageEmpty title="완료된 투어 기록이 아직 없어요!" className="mp-scrap-empty">
              첫 로컬 투어가 끝나면, 추억이 이곳에 예쁘게 쌓여요.
            </PageEmpty>
          )}

          {rows.length > 0 && (
            <div className="mp-cards">
              {rows.map((r, idx) => {
                const d = daysUntil(r.desiredDate)
                const nick = names[r.guideId] ?? `가이드 #${r.guideId}`
                const tripTitle = `여행기 #${idx + 1}`
                const routeStops = parseRouteStops(r.proposedSchedule)
                const previewText = routeStops.slice(0, 2).join(' → ') || '등록된 확정 루트가 없습니다.'
                const detailPreview = String(r.proposedSchedule ?? '').trim().split(/\r?\n/).filter(Boolean)[0] ?? ''
                return (
                  <article key={r.requestId} className="mp-scrap-ticket">
                    <div className="mp-scrap-ticket-left">
                      <span className="mp-dday" style={{ color: d != null && d < 0 ? '#6b7280' : '#dc2626' }}>
                        {r.desiredDate} · {nick}
                      </span>
                      <h2 className="mp-trip-title">{tripTitle}</h2>
                      <p className="mp-trip-detail">{nick}님과 함께했어요!</p>
                      <p className="mp-trip-detail"><strong>여행지:</strong> {r.destination}</p>
                      <p className="mp-trip-detail">{r.conceptSummary ?? r.concept ?? '—'}</p>
                      <p className="mp-trip-detail">
                        <strong>확정 루트 미리보기:</strong> {previewText}
                      </p>
                      {detailPreview && (
                        <p className="mp-trip-detail">
                          <strong>상세 코스:</strong> {detailPreview}
                        </p>
                      )}
                    </div>
                    <button
                      type="button"
                      className={`mp-scrap-ticket-right mp-ticket-tear-zone${Number(tearingRequestId) === Number(r.requestId) ? ' is-tearing' : ''}`}
                      disabled={tearingRequestId != null}
                      onClick={() => {
                        setTearingRequestId(Number(r.requestId))
                        window.setTimeout(() => {
                          navigate(`/mypage/scrapbook/${r.requestId}`)
                        }, 430)
                      }}
                      aria-label={`여행 기록 #${r.requestId} 상세 보기`}
                    >
                      <span className="mp-ticket-tear-zone-label">티켓 뜯기</span>
                    </button>
                  </article>
                )
              })}
            </div>
          )}

          {rows.length > 1 && (
            <>
              <h2 style={{ margin: '1.5rem 0 0.75rem', fontSize: '0.95rem', fontWeight: 800 }}>지난 여행 갤러리</h2>
              <div className="mp-gallery">
                {rows.slice(0, 6).map((r, i) => {
                  const rev = reviewByMatch[Number(r.requestId)]
                  const caption = galleryCaption(r, rev)
                  const to = rev?.id != null ? `/mypage/reviews/${Number(rev.id)}` : `/mypage/scrapbook/${r.requestId}`
                  const aria =
                    rev?.id != null ? `리뷰 상세 보기: ${caption}` : `여행 기록 보기: ${caption}`
                  return (
                    <Link
                      key={`g-${r.requestId}`}
                      to={to}
                      className="mp-polaroid mp-polaroid--link"
                      style={{ transform: `rotate(${i % 2 === 0 ? -2 : 2}deg)` }}
                      aria-label={aria}
                    >
                      {caption}
                    </Link>
                  )
                })}
              </div>
            </>
          )}
        </>
      )}
    </div>
  )
}
