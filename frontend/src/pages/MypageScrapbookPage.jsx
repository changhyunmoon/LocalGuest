import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import { apiRequest } from '../api/client.js'
import { daysUntil, fetchGuestMatchRequests } from '../lib/matchingGuest.js'

import './MypageMemberPages.css'

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

export function MypageScrapbookPage() {
  const [rows, setRows] = useState([])
  const [names, setNames] = useState({})
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        const all = await fetchGuestMatchRequests(apiRequest)
        const completed = (Array.isArray(all) ? all : []).filter((r) => r.status === 'COMPLETED')
        completed.sort((a, b) => String(b.desiredDate).localeCompare(String(a.desiredDate)))
        if (!cancelled) setRows(completed)
        const gids = completed.map((r) => r.guideId)
        const nm = await loadGuideNicknames(apiRequest, gids)
        if (!cancelled) setNames(nm)
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '불러오기 실패')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const count = rows.length

  const subtitle = useMemo(() => {
    if (count === 0) return '아직 완료된 로컬 투어 기록이 없습니다.'
    return `총 ${count}번의 로컬 여행을 다녀왔어요 ✈️`
  }, [count])

  return (
    <div className="mp-member">
      <h1>📒 나의 여행 기록 (스크랩북)</h1>
      <p className="sub">
        <code>GET /api/matching/requests/guest/list</code> 중 <strong>COMPLETED</strong> 만 표시합니다. 가이드 닉네임은{' '}
        <code>GET /api/guides/&#123;guideId&#125;</code> 로 보강합니다.
      </p>
      {error && <p className="err">{error}</p>}
      {loading && <p>불러오는 중…</p>}

      {!loading && !error && (
        <>
          <div className="mp-scrap-hero">
            <div>
              <h2>나의 스크랩북</h2>
              <p>{subtitle}</p>
            </div>
            <Link to="/mypage/profile" className="mp-btn mp-btn--line" style={{ textDecoration: 'none' }}>
              프로필 수정
            </Link>
          </div>

          {rows.length === 0 && <p className="sub">완료된 투어가 생기면 여기에 쌓입니다.</p>}

          {rows.length > 0 && (
            <div className="mp-cards">
              {rows.map((r) => {
                const d = daysUntil(r.desiredDate)
                const nick = names[r.guideId] ?? `가이드 #${r.guideId}`
                return (
                  <article key={r.requestId} className="mp-trip-card mp-trip-card--past">
                    <div className="mp-trip-card__meta">
                      <span className="mp-dday" style={{ color: d != null && d < 0 ? '#6b7280' : '#dc2626' }}>
                        {r.desiredDate} · {nick}
                      </span>
                      <h2 className="mp-trip-title">{r.destination}</h2>
                      <p className="mp-trip-detail">{r.conceptSummary ?? r.concept ?? '—'}</p>
                      <p className="mp-trip-detail">제시 일정: {r.proposedSchedule ?? '—'}</p>
                    </div>
                    <div className="mp-thumb" aria-hidden>
                      Trip
                    </div>
                  </article>
                )
              })}
            </div>
          )}

          {rows.length > 1 && (
            <>
              <h2 style={{ margin: '1.5rem 0 0.75rem', fontSize: '0.95rem', fontWeight: 800 }}>지난 여행 갤러리</h2>
              <div className="mp-gallery">
                {rows.slice(0, 6).map((r, i) => (
                  <div key={`g-${r.requestId}`} className="mp-polaroid" style={{ transform: `rotate(${i % 2 === 0 ? -2 : 2}deg)` }}>
                    {r.destination}
                  </div>
                ))}
              </div>
            </>
          )}
        </>
      )}
    </div>
  )
}
