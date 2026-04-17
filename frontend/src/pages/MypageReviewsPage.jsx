import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { MypageDevHint } from '../components/MypageDevHint.jsx'
import { PageEmpty, PageError, PageLoading } from '../components/PageStates.jsx'
import { apiRequest } from '../api/client.js'

import './MypageMemberPages.css'

function formatDt(iso) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString('ko-KR')
  } catch {
    return String(iso)
  }
}

function stars(rating) {
  const n = Math.max(1, Math.min(5, Math.round(Number(rating ?? 0))))
  return '★'.repeat(n)
}

function parseApiErrorMessage(text) {
  if (!text) return '요청 실패'
  try {
    const j = JSON.parse(text)
    return (j.message ?? text) || '요청 실패'
  } catch {
    return text || '요청 실패'
  }
}

export function MypageReviewsPage() {
  const [page, setPage] = useState(0)
  const [rows, setRows] = useState([])
  const [first, setFirst] = useState(true)
  const [last, setLast] = useState(true)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState(/** @type {number | null} */ (null))
  const [actionErr, setActionErr] = useState('')

  const load = useCallback(async (p) => {
    setLoading(true)
    setError('')
    try {
      const res = await apiRequest(`/reviews/me?page=${p}&size=10`, { method: 'GET' })
      const text = await res.text()
      if (!res.ok) throw new Error(parseApiErrorMessage(text))
      const data = text ? JSON.parse(text) : {}
      const content = Array.isArray(data.content) ? data.content : []
      setRows(content)
      setFirst(data.first === true)
      setLast(data.last === true)
    } catch (e) {
      setRows([])
      setError(e instanceof Error ? e.message : '목록을 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(page)
  }, [load, page])

  const onDelete = async (id) => {
    const ok = window.confirm('이 리뷰를 삭제할까요?')
    if (!ok) return
    setBusyId(id)
    setActionErr('')
    try {
      const res = await apiRequest(`/reviews/${id}`, { method: 'DELETE' })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(parseApiErrorMessage(text))
      }
      void load(page)
    } catch (e) {
      setActionErr(e instanceof Error ? e.message : '삭제 실패')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="mp-member">
      <h1>⭐ 내 리뷰</h1>
      <p className="sub">
        내가 작성한 리뷰만 모아 보여 줍니다. 팀 정책상 <strong>작성 후 수정은 불가</strong>이며, 잘못 올린 경우{' '}
        <strong>삭제</strong>만 가능합니다.
      </p>
      <MypageDevHint>
        <code>GET /reviews/me</code> (로그인 필요)
      </MypageDevHint>

      {actionErr && <p className="err">{actionErr}</p>}
      {loading && <PageLoading />}
      {!loading && error && <PageError message={error} onRetry={() => void load(page)} />}

      {!loading && !error && rows.length === 0 && (
        <PageEmpty title="작성한 리뷰가 없습니다">가이드 매칭 화면에서 후기를 남기면 여기에 표시됩니다.</PageEmpty>
      )}

      {!loading && !error && rows.length > 0 ? (
        <div className="mp-cards">
          {rows.map((r) => (
            <article key={r.id} className="mp-trip-card mp-trip-card--past">
              <div className="mp-trip-card__meta">
                <p className="mp-trip-detail" style={{ fontWeight: 700 }}>
                  {stars(r.rating)} <span style={{ color: '#6b7280', fontWeight: 600 }}>({r.rating}/5)</span>
                </p>
                <p className="mp-trip-detail" style={{ whiteSpace: 'pre-wrap' }}>
                  {r.content}
                </p>
                <p className="mp-trip-detail" style={{ fontSize: '0.8rem', color: '#6b7280' }}>
                  작성: {formatDt(r.createdAt)}
                  {r.guideId != null ? (
                    <>
                      {' · '}
                      <Link to={`/guides/${r.guideId}`}>가이드 프로필</Link>
                      {' · '}
                      <Link
                        to={`/guides/${r.guideId}/match`}
                        state={r.matchRequestId != null ? { requestId: r.matchRequestId } : undefined}
                      >
                        매칭·결제
                      </Link>
                    </>
                  ) : null}
                </p>
              </div>
              <div className="mp-trip-actions" style={{ alignSelf: 'stretch' }}>
                <button
                  type="button"
                  className="mp-btn mp-btn--danger"
                  disabled={busyId != null}
                  onClick={() => void onDelete(r.id)}
                >
                  {busyId === r.id ? '처리 중…' : '삭제'}
                </button>
              </div>
            </article>
          ))}
        </div>
      ) : null}

      {!error && (
        <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem', flexWrap: 'wrap' }}>
          <button type="button" className="mp-btn mp-btn--line" disabled={first || loading} onClick={() => setPage((p) => Math.max(0, p - 1))}>
            이전
          </button>
          <button type="button" className="mp-btn mp-btn--line" disabled={last || loading} onClick={() => setPage((p) => p + 1)}>
            다음
          </button>
        </div>
      )}
    </div>
  )
}
