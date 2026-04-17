import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { MypageDevHint } from '../components/MypageDevHint.jsx'
import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client.js'
import { useAuth } from '../context/useAuth.js'

import './MypageMemberPages.css'

function formatDateKo(v) {
  if (!v) return '—'
  const d = new Date(String(v))
  if (Number.isNaN(d.getTime())) return String(v)
  return d.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'short' })
}

function formatDateTimeKo(v) {
  if (!v) return '—'
  const d = new Date(String(v))
  if (Number.isNaN(d.getTime())) return String(v)
  return d.toLocaleString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

export function MyPageOverview() {
  const { email, isGuide } = useAuth()
  const navigate = useNavigate()

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [data, setData] = useState(null)

  const fetchDashboard = useCallback(async () => {
    const res = await apiRequest('/mypage/guest/dashboard', { method: 'GET' })
    const text = await res.text()
    if (!res.ok) {
      let msg = '대시보드를 불러오지 못했습니다.'
      try {
        const j = JSON.parse(text)
        if (typeof j?.message === 'string' && j.message.trim()) msg = j.message.trim()
      } catch {
        if (text) msg = text
      }
      throw new Error(msg)
    }
    return text ? JSON.parse(text) : null
  }, [])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        const json = await fetchDashboard()
        if (!cancelled) setData(json)
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '불러오기 실패')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [fetchDashboard])

  const onRetry = useCallback(() => {
    void (async () => {
      setLoading(true)
      setError('')
      try {
        const json = await fetchDashboard()
        setData(json)
      } catch (e) {
        setError(e instanceof Error ? e.message : '불러오기 실패')
      } finally {
        setLoading(false)
      }
    })()
  }, [fetchDashboard])

  const upcoming = useMemo(() => (Array.isArray(data?.upcomingMatches) ? data.upcomingMatches : []), [data])
  const scrapbooks = useMemo(() => (Array.isArray(data?.scrapbooks) ? data.scrapbooks : []), [data])

  const displayName = data?.guestName?.trim() ? String(data.guestName).trim() : email ?? '여행자'
  const displayEmail = data?.guestEmail?.trim() ? String(data.guestEmail).trim() : email ?? '—'

  const goPay = (m) => {
    const guideId = m?.guideId
    const requestId = m?.matchRequestedId ?? m?.requestId
    if (!guideId || !requestId) return
    navigate(`/guides/${guideId}/match`, { state: { requestId } })
  }

  return (
    <div className="mp-member">
      <h1>마이페이지</h1>
      <p className="sub">
        일정·기록·결제를 한곳에서 요약해 보여 드려요. 세부 내용은 왼쪽 메뉴나 아래 바로가기에서 확인할 수 있어요.
      </p>
      <MypageDevHint>
        <code>GET /api/mypage/guest/dashboard</code> 응답 기반 요약입니다.
      </MypageDevHint>
      <p className="sub mp-data-note">
        <strong>데이터 안내:</strong> 아래 「나의 여행 기록」은 서버에 저장된 <strong>스크랩북</strong>이고, 「기록 전체」
        메뉴는 <strong>완료된 매칭만</strong> 모은 목록이라 건수가 다를 수 있어요.
      </p>

      <div className="mp-scrap-hero" style={{ marginBottom: '1rem' }}>
        <div>
          <h2 style={{ margin: 0, fontSize: '1rem', fontWeight: 800 }}>{displayName}님, 환영해요</h2>
          <p style={{ margin: '0.35rem 0 0', color: '#4b5563', lineHeight: 1.55 }}>
            로그인 계정: <strong>{displayEmail}</strong>
            <br />
            JWT 역할: <strong>{isGuide ? 'GUIDE' : 'GUEST'}</strong>
          </p>
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.45rem', justifyContent: 'flex-end' }}>
          <Link to="/mypage/itinerary" className="mp-btn mp-btn--line" style={{ textDecoration: 'none' }}>
            일정 전체
          </Link>
          <Link to="/mypage/scrapbook" className="mp-btn mp-btn--line" style={{ textDecoration: 'none' }}>
            기록 전체
          </Link>
        </div>
      </div>

      {loading && <PageLoading />}
      {!loading && error && <PageError message={error} onRetry={onRetry} />}

      {!loading && !error && (
        <>
          <h2 style={{ margin: '0 0 0.75rem', fontSize: '0.95rem', fontWeight: 800 }}>다가오는 여행</h2>
          {upcoming.length === 0 ? (
            <PageEmpty title="예정된 여행이 없어요">일정 메뉴에서 매칭을 확인하거나 새 요청을 이어가 보세요.</PageEmpty>
          ) : (
            <div className="mp-cards">
              {upcoming.map((m) => (
                <article key={m.matchRequestedId ?? m.requestId ?? `${m.guideId}-${m.desiredDate}`} className="mp-trip-card">
                  <div className="mp-trip-card__meta">
                    <h2 className="mp-trip-title">{m.destination ?? '여행'}</h2>
                    <p className="mp-trip-detail">
                      {formatDateKo(m.desiredDate)} · 상태: {m.status ?? '—'}
                    </p>
                    <p className="mp-trip-detail">요청 #{m.matchRequestedId ?? m.requestId ?? '—'}</p>
                    <p className="mp-trip-detail">가이드: #{m.guideId ?? '—'}</p>
                  </div>
                  <div className="mp-trip-actions">
                    <span className="mp-thumb" style={{ minHeight: '4.5rem' }}>
                      Preview
                    </span>
                    {String(m.status).toUpperCase() === 'ACCEPTED' && (
                      <button type="button" className="mp-btn" onClick={() => goPay(m)}>
                        결제하고 확정
                      </button>
                    )}
                    <Link to="/mypage/itinerary" className="mp-btn mp-btn--line" style={{ textDecoration: 'none', textAlign: 'center' }}>
                      일정에서 관리
                    </Link>
                  </div>
                </article>
              ))}
            </div>
          )}

          <h2 style={{ margin: '1.5rem 0 0.75rem', fontSize: '0.95rem', fontWeight: 800 }}>나의 여행 기록</h2>
          {scrapbooks.length === 0 ? (
            <PageEmpty title="아직 스크랩북이 없어요">완료된 투어가 쌓이면 여기에 표시됩니다.</PageEmpty>
          ) : (
            <div className="mp-cards">
              {scrapbooks.slice(0, 3).map((s) => (
                <article key={s.scrapbookId} className="mp-trip-card mp-trip-card--past">
                  <div className="mp-trip-card__meta">
                    <h2 className="mp-trip-title">{s.title ?? '스크랩북'}</h2>
                    <p className="mp-trip-detail">작성: {formatDateTimeKo(s.createdAt)}</p>
                    {s.tags ? <p className="mp-trip-detail">태그: {String(s.tags)}</p> : null}
                    {s.matchRequestId != null ? (
                      <p className="mp-trip-detail">매칭 요청 #{s.matchRequestId}</p>
                    ) : null}
                  </div>
                  <div
                    className="mp-thumb"
                    style={
                      s.mainImageUrl
                        ? {
                            minHeight: '4.5rem',
                            backgroundImage: `url(${String(s.mainImageUrl)})`,
                            backgroundSize: 'cover',
                            backgroundPosition: 'center',
                          }
                        : { minHeight: '4.5rem' }
                    }
                  >
                    {!s.mainImageUrl ? 'Cover' : ''}
                  </div>
                </article>
              ))}
            </div>
          )}

          <h2 style={{ margin: '1.5rem 0 0.75rem', fontSize: '0.95rem', fontWeight: 800 }}>바로가기</h2>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.45rem' }}>
            <Link to="/mypage/payments" className="mp-btn mp-btn--line" style={{ textDecoration: 'none' }}>
              결제 내역
            </Link>
            <Link to="/mypage/tour" className="mp-btn mp-btn--line" style={{ textDecoration: 'none' }}>
              연장·환불
            </Link>
            <Link to="/mypage/privacy" className="mp-btn mp-btn--line" style={{ textDecoration: 'none' }}>
              알림/설정
            </Link>
            {!isGuide && (
              <Link to="/guide/register" className="mp-btn" style={{ textDecoration: 'none' }}>
                가이드 신청
              </Link>
            )}
          </div>
        </>
      )}
    </div>
  )
}
