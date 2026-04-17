import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

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

function within24Hours(createdAtIso) {
  if (!createdAtIso) return false
  const t = new Date(createdAtIso).getTime()
  if (Number.isNaN(t)) return false
  return Date.now() - t < 24 * 60 * 60 * 1000
}

export function MypageReviewsPage() {
  const [page, setPage] = useState(0)
  const [rows, setRows] = useState([])
  const [first, setFirst] = useState(true)
  const [last, setLast] = useState(true)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState(/** @type {number | null} */ (null))
  const [editId, setEditId] = useState(/** @type {number | null} */ (null))
  const [editRating, setEditRating] = useState(5)
  const [editContent, setEditContent] = useState('')
  const [formErr, setFormErr] = useState('')

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
    setFormErr('')
    try {
      const res = await apiRequest(`/reviews/${id}`, { method: 'DELETE' })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(parseApiErrorMessage(text))
      }
      void load(page)
    } catch (e) {
      setFormErr(e instanceof Error ? e.message : '삭제 실패')
    } finally {
      setBusyId(null)
    }
  }

  const openEdit = (r) => {
    setFormErr('')
    setEditId(r.id)
    setEditRating(Number(r.rating ?? 5))
    setEditContent(String(r.content ?? ''))
  }

  const onSaveEdit = async (e) => {
    e.preventDefault()
    if (editId == null) return
    const content = editContent.trim()
    if (content.length < 10) {
      setFormErr('리뷰는 10자 이상 입력해 주세요.')
      return
    }
    setBusyId(editId)
    setFormErr('')
    try {
      const res = await apiRequest(`/reviews/${editId}/update`, {
        method: 'POST',
        json: { rating: editRating, content },
      })
      const text = await res.text()
      if (!res.ok) throw new Error(parseApiErrorMessage(text))
      setEditId(null)
      void load(page)
    } catch (e) {
      setFormErr(e instanceof Error ? e.message : '수정 실패')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="mp-member">
      <h1>⭐ 내 리뷰</h1>
      <p className="sub">
        <code>GET /reviews/me</code>로 내가 작성한 리뷰만 불러옵니다. 수정은 작성 후 24시간 이내, 삭제는 언제든 가능합니다(백엔드 정책과 동일).
      </p>

      {formErr && <p className="err">{formErr}</p>}
      {error && <p className="err">{error}</p>}

      {loading ? (
        <p className="sub">불러오는 중…</p>
      ) : rows.length === 0 ? (
        <p className="sub">아직 작성한 리뷰가 없습니다. 가이드 매칭 화면에서 후기를 남겨 보세요.</p>
      ) : (
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
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.45rem', width: '100%' }}>
                  {within24Hours(r.createdAt) ? (
                    <button type="button" className="mp-btn mp-btn--line" disabled={busyId != null} onClick={() => openEdit(r)}>
                      수정
                    </button>
                  ) : null}
                  <button
                    type="button"
                    className="mp-btn mp-btn--danger"
                    disabled={busyId != null}
                    onClick={() => void onDelete(r.id)}
                  >
                    {busyId === r.id ? '처리 중…' : '삭제'}
                  </button>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}

      <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem', flexWrap: 'wrap' }}>
        <button type="button" className="mp-btn mp-btn--line" disabled={first || loading} onClick={() => setPage((p) => Math.max(0, p - 1))}>
          이전
        </button>
        <button type="button" className="mp-btn mp-btn--line" disabled={last || loading} onClick={() => setPage((p) => p + 1)}>
          다음
        </button>
      </div>

      {editId != null && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="mp-rev-edit-title"
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(15,23,42,0.45)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 50,
            padding: '1rem',
          }}
        >
          <form
            onSubmit={(e) => void onSaveEdit(e)}
            style={{
              width: '100%',
              maxWidth: 420,
              background: '#fff',
              borderRadius: 14,
              padding: '1.1rem',
              border: '1px solid #e8eaed',
              boxShadow: '0 12px 40px rgba(15,23,42,0.12)',
            }}
          >
            <h2 id="mp-rev-edit-title" style={{ margin: '0 0 0.75rem', fontSize: '1rem', fontWeight: 800 }}>
              리뷰 수정
            </h2>
            <label className="sub" style={{ display: 'block', marginBottom: '0.35rem' }}>
              별점
            </label>
            <select
              className="mp-btn mp-btn--line"
              style={{ width: '100%', marginBottom: '0.75rem', padding: '0.5rem' }}
              value={editRating}
              onChange={(e) => setEditRating(Number(e.target.value))}
            >
              {[1, 2, 3, 4, 5].map((n) => (
                <option key={n} value={n}>
                  {n}점
                </option>
              ))}
            </select>
            <label className="sub" style={{ display: 'block', marginBottom: '0.35rem' }}>
              내용 (10~500자)
            </label>
            <textarea
              value={editContent}
              onChange={(e) => setEditContent(e.target.value)}
              rows={5}
              style={{
                width: '100%',
                boxSizing: 'border-box',
                borderRadius: 10,
                border: '1px solid #e8eaed',
                padding: '0.6rem',
                font: 'inherit',
                resize: 'vertical',
              }}
            />
            <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.85rem', justifyContent: 'flex-end' }}>
              <button type="button" className="mp-btn mp-btn--line" disabled={busyId != null} onClick={() => setEditId(null)}>
                취소
              </button>
              <button type="submit" className="mp-btn" disabled={busyId != null}>
                {busyId != null ? '저장 중…' : '저장'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  )
}
